#include "SamplerInstrument.h"
#include "AudioEngine.h"
#include "SynthInstrument.h"
#include <algorithm>
#include <cmath>

SamplerInstrument::SamplerInstrument() {
    for (int i = 0; i < NUM_PADS; i++) {
        pads_[i].buffer = nullptr;
    }
}

void SamplerInstrument::init(double sampleRate) {
    sampleRate_ = sampleRate;
    for (auto& voice : voices_) {
        voice.init(sampleRate);
    }
}

float SamplerInstrument::process() {
    std::array<float, AudioEngine::MAX_TRACKS> outputs{};
    processToTracks(outputs.data(), static_cast<int>(outputs.size()));
    float sum = 0.0f;
    for (float output : outputs) sum += output;
    return sum;
}

void SamplerInstrument::processToTracks(float* outputs, int trackCount) {
    if (outputs == nullptr || trackCount <= 0) return;
    int active = 0;
    for (auto& voice : voices_) {
        if (voice.isActive()) {
            float sample = voice.process() * masterVolume_;
            if (voice.trackIndex >= 0 && voice.trackIndex < trackCount) {
                outputs[voice.trackIndex] += sample;
            }
            active++;
        }
    }
    // Mix in per-pad synth output (synth-mode pads).
    if (audioEngine_) {
        for (int p = 0; p < AudioEngine::PAD_SYNTH_COUNT; p++) {
            // process() runs on the real-time audio thread: never cause a
            // lazy allocation here. Only pads explicitly put into synth mode
            // create an instrument.
            auto* padSynth = audioEngine_->getExistingPadSynth(p);
            // A pad synth receives note events from the UI thread. It must be
            // processed once to consume its queue before it can report an
            // active voice; checking only isActive() left new synth-pad notes
            // permanently silent.
            if (padSynth && padSynth->needsProcessing()) {
                std::array<float, AudioEngine::MAX_TRACKS> padOutputs{};
                padSynth->processToTracks(padOutputs.data(), trackCount);
                for (int track = 0; track < trackCount; ++track) {
                    outputs[track] += padOutputs[track] * masterVolume_;
                }
                active++;
            }
        }
    }
    activeVoiceCount_.store(active, std::memory_order_relaxed);
}

void SamplerInstrument::noteOn(int midiNote, int velocity) {
    int padIndex = padIndexFromNote(midiNote);
    if (padIndex < 0 || padIndex >= NUM_PADS) return;

    const auto& pad = pads_[padIndex];

    // Synth-pad mode: forward to the per-pad synth via AudioEngine pool.
    if (pad.synthMode.load(std::memory_order_acquire)) {
        if (audioEngine_) {
            auto* padSynth = audioEngine_->getPadSynth(padIndex);
            if (padSynth) {
                int targetNote = pad.synthRootNote + (midiNote - padIndex);
                padSynth->noteOnForTrack(targetNote, velocity, 1);
            }
        }
        return;
    }

    if (!pad.buffer || !pad.buffer->isLoaded()) return;

    int voiceIdx = allocateVoice();
    if (voiceIdx < 0) return;

    auto& voice = voices_[voiceIdx];
    voice.pitch = pad.pitch;
    voice.pan = pad.pan;
    voice.volume = pad.volume;
    voice.attack = pad.attack;
    voice.release = pad.release;
    voice.reverse = pad.reverse;
    voice.loop = pad.loop;
    voice.oneShot = pad.oneShot;
    voice.useFilter = pad.useFilter;
    voice.filter.setCutoff(static_cast<double>(pad.filterCutoff));
    voice.filter.setResonance(static_cast<double>(pad.filterResonance));
    voice.start(pad.buffer.get(), midiNote, velocity);
    voice.trackIndex = 1;
}

void SamplerInstrument::noteOff(int midiNote) {
    for (auto& voice : voices_) {
        if (voice.isActive() && voice.note == midiNote) {
            voice.stop();
        }
    }
}

void SamplerInstrument::releasePad(int padIndex) {
    releasePadInternal(padIndex, 1, false);
}

void SamplerInstrument::releasePadFromAudioThread(int padIndex, int trackIndex, uint64_t triggerId) {
    releasePadInternal(padIndex, trackIndex, true, triggerId);
}

void SamplerInstrument::releasePadInternal(int padIndex, int trackIndex, bool fromAudioThread,
                                           uint64_t triggerId) {
    // Scheduler and project state use global pad indices. Keep the release
    // operation independent of whichever bank the UI currently displays.
    int globalPadIndex = padIndex;
    if (globalPadIndex < 0 || globalPadIndex >= NUM_PADS) return;
    int note = globalPadIndex;
    if (audioEngine_ && pads_[globalPadIndex].synthMode.load(std::memory_order_acquire)) {
        if (auto* padSynth = audioEngine_->getExistingPadSynth(globalPadIndex)) {
            if (fromAudioThread) {
                padSynth->noteOffFromAudioThread(pads_[globalPadIndex].synthRootNote, trackIndex, triggerId);
            } else {
                padSynth->noteOffForTrack(pads_[globalPadIndex].synthRootNote, trackIndex);
            }
        }
    }
    for (auto& voice : voices_) {
        if (voice.isActive() && voice.note == note && voice.trackIndex == trackIndex &&
            (triggerId == 0 || voice.triggerId == triggerId)) {
            voice.stop();
        }
    }
}

void SamplerInstrument::panic() {
    for (auto& voice : voices_) {
        voice.reset();
    }
    activeVoiceCount_.store(0, std::memory_order_relaxed);
}

bool SamplerInstrument::isActive() const {
    return activeVoiceCount_.load(std::memory_order_relaxed) > 0;
}

PadConfig& SamplerInstrument::getPad(int index) {
    return pads_[index];
}

const PadConfig& SamplerInstrument::getPad(int index) const {
    return pads_[index];
}

void SamplerInstrument::setPadBuffer(int index, std::shared_ptr<SampleBuffer> buffer) {
    if (index >= 0 && index < NUM_PADS) {
        pads_[index].buffer = std::move(buffer);
    }
}

void SamplerInstrument::clearPad(int index) {
    if (index >= 0 && index < NUM_PADS) {
        pads_[index].buffer = nullptr;
    }
}

bool SamplerInstrument::timeStretchPad(int padIndex, double tempoChangePercent,
                                       double pitchSemiTones, double rateChangePercent) {
    if (padIndex < 0 || padIndex >= NUM_PADS) return false;
    const auto& src = pads_[padIndex].buffer;
    if (!src || !src->isLoaded()) return false;

    auto stretched = src->createTimeStretched(tempoChangePercent, pitchSemiTones, rateChangePercent);
    if (!stretched) return false;

    pads_[padIndex].buffer = std::move(stretched);
    return true;
}

void SamplerInstrument::chopSample(int sourcePad, int startPad, int numSlices) {
    if (sourcePad < 0 || sourcePad >= NUM_PADS) return;
    if (startPad < 0 || startPad >= NUM_PADS) return;
    if (numSlices <= 0) return;

    const auto& source = pads_[sourcePad].buffer;
    if (!source || !source->isLoaded()) return;

    int totalFrames = source->getNumFrames();
    int channels = source->getChannels();
    int sliceFrames = totalFrames / numSlices;
    if (sliceFrames <= 0) return;

    const auto& srcSamples = source->getSamples();

    for (int i = 0; i < numSlices && (startPad + i) < NUM_PADS; i++) {
        int startFrame = i * sliceFrames;
        int endFrame = (i == numSlices - 1) ? totalFrames : (startFrame + sliceFrames);
        int frames = endFrame - startFrame;

        std::vector<float> slice;
        slice.resize(frames * channels);
        std::copy(
            srcSamples.begin() + startFrame * channels,
            srcSamples.begin() + endFrame * channels,
            slice.begin()
        );

        auto buffer = std::make_shared<SampleBuffer>(
            std::move(slice), source->getSampleRate(), channels);
        pads_[startPad + i].buffer = buffer;
    }
}

void SamplerInstrument::setMasterVolume(float volume) {
    masterVolume_ = volume;
}

void SamplerInstrument::setActiveBank(int bank) {
    activeBank_ = bank == 1 ? 1 : 0;
}

void SamplerInstrument::triggerPad(int padIndex, int velocity) {
    triggerPadInternal(padIndex, velocity, false, 1);
}

void SamplerInstrument::triggerPadFromAudioThread(int padIndex, int velocity) {
    triggerPadInternal(padIndex, velocity, true, 1);
}

void SamplerInstrument::triggerPadFromAudioThread(int padIndex, int velocity, int trackIndex,
                                                   uint64_t triggerId) {
    triggerPadInternal(padIndex, velocity, true, trackIndex, triggerId);
}

void SamplerInstrument::triggerPadInternal(int padIndex, int velocity, bool fromAudioThread,
                                           int trackIndex, uint64_t triggerId) {
    if (padIndex < 0 || padIndex >= NUM_PADS) return;
    trackIndex = std::clamp(trackIndex, 0, AudioEngine::MAX_TRACKS - 1);

    const auto& pad = pads_[padIndex];
    if (pad.synthMode.load(std::memory_order_acquire)) {
        if (audioEngine_) {
            if (auto* padSynth = audioEngine_->getExistingPadSynth(padIndex)) {
                if (fromAudioThread) {
                    padSynth->noteOnFromAudioThread(pad.synthRootNote, velocity, trackIndex, triggerId);
                } else {
                    padSynth->noteOnForTrack(pad.synthRootNote, velocity, trackIndex);
                }
            }
        }
        return;
    }

    if (!pad.buffer || !pad.buffer->isLoaded()) return;
    int voiceIdx = allocateVoice();
    if (voiceIdx < 0) return;

    auto& voice = voices_[voiceIdx];
    voice.pitch = pad.pitch;
    voice.pan = pad.pan;
    voice.volume = pad.volume;
    voice.attack = pad.attack;
    voice.release = pad.release;
    voice.reverse = pad.reverse;
    voice.loop = pad.loop;
    voice.oneShot = pad.oneShot;
    voice.useFilter = pad.useFilter;
    voice.filter.setCutoff(static_cast<double>(pad.filterCutoff));
    voice.filter.setResonance(static_cast<double>(pad.filterResonance));
    // Keep the global pad index as the voice note so release/voice tracking
    // remains deterministic regardless of the currently selected UI bank.
    voice.start(pad.buffer.get(), padIndex, velocity);
    voice.trackIndex = trackIndex;
    voice.triggerId = triggerId;
}

int SamplerInstrument::allocateVoice() {
    for (int i = 0; i < SAMPLER_POLYPHONY; i++) {
        if (!voices_[i].isActive()) {
            voices_[i].age = voiceAgeCounter_++;
            return i;
        }
    }
    // Steal oldest voice (smallest age value — least recently used)
    int oldest = 0;
    uint64_t minAge = voices_[0].age;
    for (int i = 1; i < SAMPLER_POLYPHONY; i++) {
        if (voices_[i].age < minAge) {
            minAge = voices_[i].age;
            oldest = i;
        }
    }
    voices_[oldest].reset();
    voices_[oldest].age = voiceAgeCounter_++;
    return oldest;
}

int SamplerInstrument::padIndexFromNote(int midiNote) const {
    // Map notes to pads within the active bank.
    // Bank 0: pads 0-15, Bank 1: pads 16-31
    int base = activeBank_ * 16;
    int offset = midiNote % 16;
    return base + offset;
}
