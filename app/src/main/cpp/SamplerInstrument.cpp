#include "SamplerInstrument.h"
#include "AudioEngine.h"
#include "SynthInstrument.h"
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
    float sum = 0.0f;
    int active = 0;
    for (auto& voice : voices_) {
        if (voice.isActive()) {
            sum += voice.process();
            active++;
        }
    }
    // Mix in per-pad synth output (synth-mode pads).
    if (audioEngine_) {
        for (int p = 0; p < AudioEngine::PAD_SYNTH_COUNT; p++) {
            auto* padSynth = audioEngine_->getPadSynth(p);
            if (padSynth && padSynth->isActive()) {
sum += padSynth->process();
active++;
            }
        }
    }
    activeVoiceCount_.store(active, std::memory_order_relaxed);
    return sum * masterVolume_;
}

void SamplerInstrument::noteOn(int midiNote, int velocity) {
    int padIndex = padIndexFromNote(midiNote);
    if (padIndex < 0 || padIndex >= NUM_PADS) return;

    const auto& pad = pads_[padIndex];

    // Synth-pad mode: forward to the per-pad synth via AudioEngine pool.
    if (pad.synthMode) {
        if (audioEngine_) {
            auto* padSynth = audioEngine_->getPadSynth(padIndex % 16);
            if (padSynth) {
                int targetNote = pad.synthRootNote + (midiNote - (activeBank_ * 16 + (padIndex % 16)));
                padSynth->noteOn(targetNote, velocity);
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
}

void SamplerInstrument::noteOff(int midiNote) {
    for (auto& voice : voices_) {
        if (voice.isActive() && voice.note == midiNote) {
            voice.stop();
        }
    }
}

void SamplerInstrument::releasePad(int padIndex) {
    int base = activeBank_ * 16;
    int note = base + (padIndex % 16);
    noteOff(note);
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
    int base = activeBank_ * 16;
    int note = base + (padIndex % 16);
    noteOn(note, velocity);
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
