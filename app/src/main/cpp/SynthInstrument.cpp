#include "SynthInstrument.h"
#include "Envelope.h"
#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>
#include <cstring>

SynthInstrument::SynthInstrument() {
    for (int i = 0; i < MAX_VOICES; i++) {
        voiceActive_[i] = false;
    }
}

void SynthInstrument::init(double sampleRate) {
    sampleRate_ = sampleRate;

    for (auto& voice : voices_) {
        voice.init(sampleRate);
    }
    lfo1_.init(sampleRate);
    lfo2_.init(sampleRate);
    modLfo_.init(sampleRate);
    reverb_.init(sampleRate);
    delay_.init(sampleRate);
    chorus_.init(sampleRate);
    sequencer_.init(sampleRate);

    sequencer_.onNoteEvent = [this](int note, int velocity, bool noteOnEvt) {
        if (noteOnEvt) {
            this->noteOn(note, velocity);
        } else {
            this->noteOff(note);
        }
    };
}

float SynthInstrument::process() {
    float output = 0.0f;
    processToTracks(&output, 1);
    return output;
}

void SynthInstrument::processToTracks(float* outputs, int trackCount) {
    if (outputs == nullptr || trackCount <= 0) return;
    if (panicRequested_.exchange(false, std::memory_order_acq_rel)) {
        panic();
    }
    swapParamsIfNeeded();
    // Apply a new preset before handling a queued note so the first hit uses
    // the state the UI just selected rather than the previous synth state.
    processNoteQueue();

    (void)sequencer_.process(1);

    float lfo1Val = lfo1_.process();
    float lfo2Val = lfo2_.process();

    std::array<float, 6> modSources = {lfo1Val, lfo2Val, 0, 0, 0, 0};
    for (int v = 0; v < MAX_VOICES; v++) {
        if (voiceActive_[v]) {
            float cutoffMod = modMatrix_.getModulation(1, modSources);
            if (cutoffMod != 0.0f) {
                float base = currentParams_.filter.cutoff;
                voices_[v].setFilterCutoff(std::clamp(base + cutoffMod, 0.0f, 1.0f));
            }
            float resMod = modMatrix_.getModulation(2, modSources);
            if (resMod != 0.0f) {
                float base = currentParams_.filter.resonance;
                voices_[v].setFilterResonance(std::clamp(base + resMod, 0.0f, 1.0f));
            }
            float pitchMod = modMatrix_.getModulation(0, modSources);
            if (pitchMod != 0.0f) {
                voices_[v].setPitchBend(pitchBend_ + pitchMod);
            }
            float ampMod = modMatrix_.getModulation(3, modSources);
            if (ampMod != 0.0f) {
                float base = currentParams_.oscillators.osc1.level;
                float modded = std::clamp(base + ampMod, 0.0f, 1.0f);
                voices_[v].setOsc1Level(modded);
                voices_[v].setOsc2Level(modded);
            }
            float mixMod = modMatrix_.getModulation(4, modSources);
            if (mixMod != 0.0f) {
                float base = oscMix_;
                voices_[v].setOscMix(std::clamp(base + mixMod, 0.0f, 1.0f));
            }
        }
    }

    const int usableTrackCount = std::min(trackCount, MAX_ROUTING_TRACKS);
    std::array<float, MAX_ROUTING_TRACKS> dryOutputs{};
    bool hasActiveVoice = false;
    for (int v = 0; v < MAX_VOICES; v++) {
        if (!voiceActive_[v]) continue;
        float sample = voices_[v].process();
        int track = voices_[v].trackIndex;
        if (track >= 0 && track < usableTrackCount) {
            dryOutputs[track] += sample;
        }
        hasActiveVoice = true;
    }

    noiseSmooth_ += (((noiseLevel_ > 0.001f && hasActiveVoice) ? noiseLevel_ : 0.0f)
        - noiseSmooth_) * 0.02f;

    int effectsTrack = std::clamp(lastEffectsTrack_, 0, std::max(usableTrackCount - 1, 0));
    float largestDrySignal = 0.0f;
    for (int track = 0; track < usableTrackCount; ++track) {
        const float magnitude = std::fabs(dryOutputs[track]);
        if (magnitude > largestDrySignal) {
            largestDrySignal = magnitude;
            effectsTrack = track;
        }
    }
    lastEffectsTrack_ = effectsTrack;

    // The synth owns one shared effects chain. Preserve that behavior while
    // returning its wet signal to the timeline row producing the sound.
    dryOutputs[effectsTrack] += generateNoise() * noiseSmooth_;
    float drySum = 0.0f;
    for (int track = 0; track < usableTrackCount; ++track) {
        drySum += dryOutputs[track];
    }

    float effectedSum = drySum;
    if (!currentParams_.effects.bypass) {
        effectedSum = distortion_.process(effectedSum);
        effectedSum = chorus_.process(effectedSum);
        effectedSum = delay_.process(effectedSum);
        effectedSum = reverb_.process(effectedSum);
    }

    const float masterGain = static_cast<float>(masterVolume_);
    for (int track = 0; track < usableTrackCount; ++track) {
        outputs[track] += dryOutputs[track] * masterGain;
    }
    if (usableTrackCount > 0) {
        outputs[effectsTrack] += (effectedSum - drySum) * masterGain;
    }
}

float SynthInstrument::processSynthSample() {
    (void)sequencer_.process(1);

    float lfo1Val = lfo1_.process();
    float lfo2Val = lfo2_.process();

    std::array<float, 6> modSources = {lfo1Val, lfo2Val, 0, 0, 0, 0};
    for (int v = 0; v < MAX_VOICES; v++) {
        if (voiceActive_[v]) {
            float cutoffMod = modMatrix_.getModulation(1, modSources);
            if (cutoffMod != 0.0f) {
                float base = currentParams_.filter.cutoff;
                voices_[v].setFilterCutoff(std::clamp(base + cutoffMod, 0.0f, 1.0f));
            }
            float resMod = modMatrix_.getModulation(2, modSources);
            if (resMod != 0.0f) {
                float base = currentParams_.filter.resonance;
                voices_[v].setFilterResonance(std::clamp(base + resMod, 0.0f, 1.0f));
            }
            float pitchMod = modMatrix_.getModulation(0, modSources);
            if (pitchMod != 0.0f) {
                voices_[v].setPitchBend(pitchBend_ + pitchMod);
            }
            float ampMod = modMatrix_.getModulation(3, modSources);
            if (ampMod != 0.0f) {
                float base = currentParams_.oscillators.osc1.level;
                float modded = std::clamp(base + ampMod, 0.0f, 1.0f);
                voices_[v].setOsc1Level(modded);
                voices_[v].setOsc2Level(modded);
            }
            float mixMod = modMatrix_.getModulation(4, modSources);
            if (mixMod != 0.0f) {
                float base = oscMix_;
                voices_[v].setOscMix(std::clamp(base + mixMod, 0.0f, 1.0f));
            }
        }
    }

    float sample = 0.0f;
    for (int v = 0; v < MAX_VOICES; v++) {
        if (voiceActive_[v]) {
            sample += voices_[v].process();
        }
    }

    float noiseTarget = (noiseLevel_ > 0.001f && activeVoiceCount_.load(std::memory_order_relaxed) > 0)
        ? noiseLevel_ : 0.0f;
    noiseSmooth_ += (noiseTarget - noiseSmooth_) * 0.02f;
    if (noiseSmooth_ > 0.001f) {
        sample += generateNoise() * noiseSmooth_;
    }

    if (!currentParams_.effects.bypass) {
        sample = distortion_.process(sample);
        sample = chorus_.process(sample);
        sample = delay_.process(sample);
        sample = reverb_.process(sample);
    }

    sample *= static_cast<float>(masterVolume_);
    return sample;
}

void SynthInstrument::noteOn(int midiNote, int velocity) {
    noteOnForTrack(midiNote, velocity, 0);
}

void SynthInstrument::noteOnForTrack(int midiNote, int velocity, int trackIndex) {
    int tail = noteQueueTail_.load(std::memory_order_relaxed);
    int nextTail = (tail + 1) % NOTE_QUEUE_SIZE;
    if (nextTail != noteQueueHead_.load(std::memory_order_acquire)) {
        noteQueue_[tail] = {NoteEvent::NoteOn, midiNote, velocity, trackIndex};
        noteQueueTail_.store(nextTail, std::memory_order_release);
    }
}

void SynthInstrument::noteOff(int midiNote) {
    noteOffForTrack(midiNote, 0);
}

void SynthInstrument::noteOffForTrack(int midiNote, int trackIndex) {
    int tail = noteQueueTail_.load(std::memory_order_relaxed);
    int nextTail = (tail + 1) % NOTE_QUEUE_SIZE;
    if (nextTail != noteQueueHead_.load(std::memory_order_acquire)) {
        noteQueue_[tail] = {NoteEvent::NoteOff, midiNote, 0, trackIndex};
        noteQueueTail_.store(nextTail, std::memory_order_release);
    }
}

void SynthInstrument::noteOnFromAudioThread(int midiNote, int velocity, int trackIndex, uint64_t triggerId) {
    handleNoteOn(midiNote, velocity, trackIndex, triggerId);
}

void SynthInstrument::noteOffFromAudioThread(int midiNote, int trackIndex, uint64_t triggerId) {
    handleNoteOff(midiNote, trackIndex, triggerId);
}

void SynthInstrument::requestPanic() {
    panicRequested_.store(true, std::memory_order_release);
}

void SynthInstrument::handleNoteOn(int midiNote, int velocity, int trackIndex, uint64_t triggerId) {
    trackIndex = std::max(0, trackIndex);
    for (int i = 0; i < MAX_VOICES; i++) {
        if (triggerId == 0 && voiceActive_[i] && voices_[i].getNote() == midiNote &&
            voices_[i].trackIndex == trackIndex) {
            voices_[i].stopImmediately();
            voiceActive_[i] = false;
            activeVoiceCount_.fetch_sub(1, std::memory_order_relaxed);
        }
    }

    int voiceIdx = allocateVoice();
    if (voiceIdx >= 0) {
        voices_[voiceIdx].init(sampleRate_);
        voices_[voiceIdx].setAmpEnvelope(
            currentParams_.envelopes.attack,
            currentParams_.envelopes.decay,
            currentParams_.envelopes.sustain,
            currentParams_.envelopes.release);
        voices_[voiceIdx].setFilterEnvelope(
            currentParams_.envelopes.filterAttack,
            currentParams_.envelopes.filterDecay,
            currentParams_.envelopes.filterSustain,
            currentParams_.envelopes.filterRelease);

        voices_[voiceIdx].setOsc1Level(currentParams_.oscillators.osc1.level);
        voices_[voiceIdx].setOsc2Level(currentParams_.oscillators.osc2.level);
        voices_[voiceIdx].setOsc1Waveform(currentParams_.oscillators.osc1.waveform);
        voices_[voiceIdx].setOsc2Waveform(currentParams_.oscillators.osc2.waveform);
        voices_[voiceIdx].setOscDetune(currentParams_.oscillators.osc1.detune * 100.0f);
        voices_[voiceIdx].setFilterCutoff(currentParams_.filter.cutoff);
        voices_[voiceIdx].setFilterResonance(currentParams_.filter.resonance);
        voices_[voiceIdx].setFilterMode(currentParams_.filter.mode);
        voices_[voiceIdx].setOscMix(oscMix_);
        voices_[voiceIdx].setOscSyncEnabled(oscSync_);
        voices_[voiceIdx].setSubOscLevel(subOscLevel_);
        voices_[voiceIdx].setModWheel(modWheel_);
        voices_[voiceIdx].setPitchBend(pitchBend_);

        voices_[voiceIdx].noteOn(midiNote, velocity);
        voices_[voiceIdx].trackIndex = trackIndex;
        voices_[voiceIdx].triggerId = triggerId;
        voiceActive_[voiceIdx] = true;
        activeVoiceCount_.fetch_add(1, std::memory_order_relaxed);
    }
}

void SynthInstrument::handleNoteOff(int midiNote, int trackIndex, uint64_t triggerId) {
    for (int i = 0; i < MAX_VOICES; i++) {
        if (voiceActive_[i] && voices_[i].getNote() == midiNote &&
            voices_[i].trackIndex == trackIndex &&
            (triggerId == 0 || voices_[i].triggerId == triggerId)) {
            voices_[i].noteOff();
        }
    }
}

bool SynthInstrument::isActive() const {
    return activeVoiceCount_.load(std::memory_order_relaxed) > 0;
}

bool SynthInstrument::needsProcessing() const {
    return isActive() ||
        noteQueueHead_.load(std::memory_order_acquire) !=
            noteQueueTail_.load(std::memory_order_acquire);
}

void SynthInstrument::processNoteQueue() {
    while (true) {
        int head = noteQueueHead_.load(std::memory_order_acquire);
        int tail = noteQueueTail_.load(std::memory_order_acquire);
        if (head == tail) break;

        auto& event = noteQueue_[head];
        if (event.type == NoteEvent::NoteOn) {
            handleNoteOn(event.note, event.velocity, event.trackIndex);
        } else {
            handleNoteOff(event.note, event.trackIndex);
        }
        noteQueueHead_.store((head + 1) % NOTE_QUEUE_SIZE, std::memory_order_release);
    }
}

int SynthInstrument::allocateVoice() {
    for (int i = 0; i < MAX_VOICES; i++) {
        if (!voiceActive_[i]) return i;
    }
    int oldest = 0;
    uint64_t maxAge = voices_[0].getAge();
    for (int i = 1; i < MAX_VOICES; i++) {
        if (voices_[i].getAge() > maxAge) {
            maxAge = voices_[i].getAge();
            oldest = i;
        }
    }
    if (voiceActive_[oldest]) {
        voiceActive_[oldest] = false;
        activeVoiceCount_.fetch_sub(1, std::memory_order_relaxed);
    }
    voices_[oldest].stopImmediately();
    return oldest;
}

int SynthInstrument::findVoiceByNote(int midiNote) {
    for (int i = 0; i < MAX_VOICES; i++) {
        if (voiceActive_[i] && voices_[i].getNote() == midiNote) {
            return i;
        }
    }
    return -1;
}

void SynthInstrument::panic() {
    for (int i = 0; i < MAX_VOICES; i++) {
        if (voiceActive_[i]) {
            voices_[i].stopImmediately();
            voiceActive_[i] = false;
        }
    }
    activeVoiceCount_ = 0;
    sequencer_.setPlaying(false);
    currentParams_.sequencer.playing = false;
    pendingParams_.sequencer.playing = false;
    reverb_.reset();
    delay_.reset();
}

void SynthInstrument::setParams(const SynthParams& params) {
    pendingParams_ = params;
    paramsPending_ = true;
}

void SynthInstrument::setAllParamsFromArray(const float* values, int count) {
    static_assert(SYNTH_PARAM_COUNT == 39, "Synth parameter array size mismatch with Kotlin");
    if (count < SYNTH_PARAM_COUNT) return;

    pendingParams_.oscillators.osc1.level = values[0];
    pendingParams_.oscillators.osc2.level = values[1];
    pendingParams_.oscillators.osc1.waveform = static_cast<int>(values[2]);
    pendingParams_.oscillators.osc2.waveform = static_cast<int>(values[3]);
    pendingParams_.oscillators.osc1.detune = values[4];
    pendingParams_.oscillators.subOscLevel = values[5];
    pendingParams_.oscillators.noiseLevel = values[6];
    pendingParams_.oscillators.oscMix = values[7];
    pendingParams_.oscillators.syncEnabled = values[8] > 0.5f;

    pendingParams_.filter.cutoff = values[9];
    pendingParams_.filter.resonance = values[10];
    pendingParams_.filter.mode = static_cast<int>(values[11]);
    pendingParams_.filter.envelopeAmount = values[12];

    pendingParams_.envelopes.attack = values[13];
    pendingParams_.envelopes.decay = values[14];
    pendingParams_.envelopes.sustain = values[15];
    pendingParams_.envelopes.release = values[16];
    pendingParams_.envelopes.filterAttack = values[17];
    pendingParams_.envelopes.filterDecay = values[18];
    pendingParams_.envelopes.filterSustain = values[19];
    pendingParams_.envelopes.filterRelease = values[20];

    pendingParams_.lfos.lfo1.rate = values[21];
    pendingParams_.lfos.lfo1.depth = values[22];
    pendingParams_.lfos.lfo1.waveform = static_cast<int>(values[23]);
    pendingParams_.lfos.lfo2.rate = values[24];
    pendingParams_.lfos.lfo2.depth = values[25];
    pendingParams_.lfos.lfo2.waveform = static_cast<int>(values[26]);

    pendingParams_.effects.reverb.mix = values[27];
    pendingParams_.effects.reverb.decay = values[28];
    pendingParams_.effects.delay.mix = values[29];
    pendingParams_.effects.delay.time = values[30];
    pendingParams_.effects.delay.feedback = values[31];
    pendingParams_.effects.distortion.drive = values[32];
    pendingParams_.effects.distortion.mix = values[33];
    pendingParams_.effects.bypass = values[34] > 0.5f;

    pendingParams_.effects.chorus.rate = values[35];
    pendingParams_.effects.chorus.depth = values[36];
    pendingParams_.effects.chorus.mix = values[37];

    pendingParams_.master.volume = values[38];

    paramsPending_.store(true, std::memory_order_release);
}

float SynthInstrument::getParamByIndex(int index) const {
    if (index < 0 || index >= SYNTH_PARAM_COUNT) return 0.0f;
    // Mirror the index mapping from setAllParamsFromArray
    switch (index) {
case 0:  return currentParams_.oscillators.osc1.level;
case 1:  return currentParams_.oscillators.osc2.level;
case 2:  return static_cast<float>(currentParams_.oscillators.osc1.waveform);
case 3:  return static_cast<float>(currentParams_.oscillators.osc2.waveform);
case 4:  return currentParams_.oscillators.osc1.detune;
case 5:  return currentParams_.oscillators.subOscLevel;
case 6:  return currentParams_.oscillators.noiseLevel;
case 7:  return currentParams_.oscillators.oscMix;
case 8:  return currentParams_.oscillators.syncEnabled ? 1.0f : 0.0f;
case 9:  return currentParams_.filter.cutoff;
case 10: return currentParams_.filter.resonance;
case 11: return static_cast<float>(currentParams_.filter.mode);
case 12: return currentParams_.filter.envelopeAmount;
case 13: return currentParams_.envelopes.attack;
case 14: return currentParams_.envelopes.decay;
case 15: return currentParams_.envelopes.sustain;
case 16: return currentParams_.envelopes.release;
case 17: return currentParams_.envelopes.filterAttack;
case 18: return currentParams_.envelopes.filterDecay;
case 19: return currentParams_.envelopes.filterSustain;
case 20: return currentParams_.envelopes.filterRelease;
case 21: return currentParams_.lfos.lfo1.rate;
case 22: return currentParams_.lfos.lfo1.depth;
case 23: return static_cast<float>(currentParams_.lfos.lfo1.waveform);
case 24: return currentParams_.lfos.lfo2.rate;
case 25: return currentParams_.lfos.lfo2.depth;
case 26: return static_cast<float>(currentParams_.lfos.lfo2.waveform);
case 27: return currentParams_.effects.reverb.mix;
case 28: return currentParams_.effects.reverb.decay;
case 29: return currentParams_.effects.delay.mix;
case 30: return currentParams_.effects.delay.time;
case 31: return currentParams_.effects.delay.feedback;
case 32: return currentParams_.effects.distortion.drive;
case 33: return currentParams_.effects.distortion.mix;
case 34: return currentParams_.effects.bypass ? 1.0f : 0.0f;
case 35: return currentParams_.effects.chorus.rate;
case 36: return currentParams_.effects.chorus.depth;
case 37: return currentParams_.effects.chorus.mix;
case 38: return currentParams_.master.volume;
default: return 0.0f;
    }
}

void SynthInstrument::applyAutomationParam(int paramIndex, float value) {
    switch (paramIndex) {
        case 0:  pendingParams_.oscillators.osc1.level = value; break;
        case 1:  pendingParams_.oscillators.osc2.level = value; break;
        case 2:  pendingParams_.oscillators.osc1.waveform = static_cast<int>(value); break;
        case 3:  pendingParams_.oscillators.osc2.waveform = static_cast<int>(value); break;
        case 4:  pendingParams_.oscillators.osc1.detune = value; break;
        case 5:  pendingParams_.oscillators.subOscLevel = value; break;
        case 6:  pendingParams_.oscillators.noiseLevel = value; break;
        case 7:  pendingParams_.oscillators.oscMix = value; break;
        case 8:  pendingParams_.oscillators.syncEnabled = value > 0.5f; break;
        case 9:  pendingParams_.filter.cutoff = value; break;
        case 10: pendingParams_.filter.resonance = value; break;
        case 11: pendingParams_.filter.mode = static_cast<int>(value); break;
        case 12: pendingParams_.filter.envelopeAmount = value; break;
        case 13: pendingParams_.envelopes.attack = value; break;
        case 14: pendingParams_.envelopes.decay = value; break;
        case 15: pendingParams_.envelopes.sustain = value; break;
        case 16: pendingParams_.envelopes.release = value; break;
        case 17: pendingParams_.envelopes.filterAttack = value; break;
        case 18: pendingParams_.envelopes.filterDecay = value; break;
        case 19: pendingParams_.envelopes.filterSustain = value; break;
        case 20: pendingParams_.envelopes.filterRelease = value; break;
        case 21: pendingParams_.lfos.lfo1.rate = value; break;
        case 22: pendingParams_.lfos.lfo1.depth = value; break;
        case 23: pendingParams_.lfos.lfo1.waveform = static_cast<int>(value); break;
        case 24: pendingParams_.lfos.lfo2.rate = value; break;
        case 25: pendingParams_.lfos.lfo2.depth = value; break;
        case 26: pendingParams_.lfos.lfo2.waveform = static_cast<int>(value); break;
        case 27: pendingParams_.effects.reverb.mix = value; break;
        case 28: pendingParams_.effects.reverb.decay = value; break;
        case 29: pendingParams_.effects.delay.mix = value; break;
        case 30: pendingParams_.effects.delay.time = value; break;
        case 31: pendingParams_.effects.delay.feedback = value; break;
        case 32: pendingParams_.effects.distortion.drive = value; break;
        case 33: pendingParams_.effects.distortion.mix = value; break;
        case 34: pendingParams_.effects.bypass = value > 0.5f; break;
        case 35: pendingParams_.effects.chorus.rate = value; break;
        case 36: pendingParams_.effects.chorus.depth = value; break;
        case 37: pendingParams_.effects.chorus.mix = value; break;
        case 38: pendingParams_.master.volume = value; break;
        default: return;
    }
    paramsPending_ = true;
}

#define SET_AND_SYNC(field, value) \
    pendingParams_.field = value; \
    paramsPending_ = true;

void SynthInstrument::setOsc1Level(float v) { SET_AND_SYNC(oscillators.osc1.level, v); }
void SynthInstrument::setOsc2Level(float v) { SET_AND_SYNC(oscillators.osc2.level, v); }
void SynthInstrument::setOsc1Waveform(int w) { SET_AND_SYNC(oscillators.osc1.waveform, w); }
void SynthInstrument::setOsc2Waveform(int w) { SET_AND_SYNC(oscillators.osc2.waveform, w); }
void SynthInstrument::setOscDetune(float v) { SET_AND_SYNC(oscillators.osc1.detune, v); }
void SynthInstrument::setSubOscLevel(float v) { SET_AND_SYNC(oscillators.subOscLevel, v); }
void SynthInstrument::setNoiseLevel(float v) { SET_AND_SYNC(oscillators.noiseLevel, v); }
void SynthInstrument::setOscMix(float v) { SET_AND_SYNC(oscillators.oscMix, v); }
void SynthInstrument::setOscSync(bool s) { SET_AND_SYNC(oscillators.syncEnabled, s); }

void SynthInstrument::setFilterCutoff(float v) { SET_AND_SYNC(filter.cutoff, v); }
void SynthInstrument::setFilterResonance(float v) { SET_AND_SYNC(filter.resonance, v); }
void SynthInstrument::setFilterMode(int m) { SET_AND_SYNC(filter.mode, m); }
void SynthInstrument::setFilterEnvAmount(float v) { SET_AND_SYNC(filter.envelopeAmount, v); }

void SynthInstrument::setAmpAttack(float v) { SET_AND_SYNC(envelopes.attack, v); }
void SynthInstrument::setAmpDecay(float v) { SET_AND_SYNC(envelopes.decay, v); }
void SynthInstrument::setAmpSustain(float v) { SET_AND_SYNC(envelopes.sustain, v); }
void SynthInstrument::setAmpRelease(float v) { SET_AND_SYNC(envelopes.release, v); }
void SynthInstrument::setFilterAttack(float v) { SET_AND_SYNC(envelopes.filterAttack, v); }
void SynthInstrument::setFilterDecay(float v) { SET_AND_SYNC(envelopes.filterDecay, v); }
void SynthInstrument::setFilterSustain(float v) { SET_AND_SYNC(envelopes.filterSustain, v); }
void SynthInstrument::setFilterRelease(float v) { SET_AND_SYNC(envelopes.filterRelease, v); }

void SynthInstrument::setLfo1Rate(float v) { SET_AND_SYNC(lfos.lfo1.rate, v); }
void SynthInstrument::setLfo1Depth(float v) { SET_AND_SYNC(lfos.lfo1.depth, v); }
void SynthInstrument::setLfo1Waveform(int w) { SET_AND_SYNC(lfos.lfo1.waveform, w); }
void SynthInstrument::setLfo2Rate(float v) { SET_AND_SYNC(lfos.lfo2.rate, v); }
void SynthInstrument::setLfo2Depth(float v) { SET_AND_SYNC(lfos.lfo2.depth, v); }
void SynthInstrument::setLfo2Waveform(int w) { SET_AND_SYNC(lfos.lfo2.waveform, w); }

void SynthInstrument::setReverbMix(float v) { SET_AND_SYNC(effects.reverb.mix, v); }
void SynthInstrument::setReverbDecay(float v) { SET_AND_SYNC(effects.reverb.decay, v); }
void SynthInstrument::setDelayMix(float v) { SET_AND_SYNC(effects.delay.mix, v); }
void SynthInstrument::setDelayTime(float v) { SET_AND_SYNC(effects.delay.time, v); }
void SynthInstrument::setDelayFeedback(float v) { SET_AND_SYNC(effects.delay.feedback, v); }
void SynthInstrument::setDistortionDrive(float v) { SET_AND_SYNC(effects.distortion.drive, v); }
void SynthInstrument::setDistortionMix(float v) { SET_AND_SYNC(effects.distortion.mix, v); }
void SynthInstrument::setChorusRate(float v) { SET_AND_SYNC(effects.chorus.rate, v); }
void SynthInstrument::setChorusDepth(float v) { SET_AND_SYNC(effects.chorus.depth, v); }
void SynthInstrument::setChorusMix(float v) { SET_AND_SYNC(effects.chorus.mix, v); }
void SynthInstrument::setEffectsBypass(bool b) { SET_AND_SYNC(effects.bypass, b); }

void SynthInstrument::setModulationRoute(int index, const ModulationRoute& route) {
    if (index >= 0 && index < 8) {
        pendingParams_.modulation.routes[index] = route;
        paramsPending_ = true;
    }
}

void SynthInstrument::setSequencerSteps(const std::array<SequencerStep, SEQUENCER_STEPS>& steps) {
    pendingParams_.sequencer.steps = steps;
    paramsPending_ = true;
}

void SynthInstrument::setSequencerTempo(float bpm) {
    pendingParams_.sequencer.tempo = bpm;
    paramsPending_ = true;
}

void SynthInstrument::setSequencerPlaying(bool play) {
    pendingParams_.sequencer.playing = play;
    paramsPending_ = true;
}

void SynthInstrument::setMasterVolume(float v) { SET_AND_SYNC(master.volume, v); }
void SynthInstrument::setPitchBend(float v) { SET_AND_SYNC(master.pitchBend, v); }
void SynthInstrument::setModWheel(float v) { SET_AND_SYNC(master.modulationWheel, v); }

void SynthInstrument::applyModulationMatrix() {
    for (int i = 0; i < MAX_VOICES; i++) {
        if (voiceActive_[i]) {
            const auto& p = currentParams_;
            voices_[i].setOsc1Level(p.oscillators.osc1.level);
            voices_[i].setOsc2Level(p.oscillators.osc2.level);
            voices_[i].setOsc1Waveform(p.oscillators.osc1.waveform);
            voices_[i].setOsc2Waveform(p.oscillators.osc2.waveform);
            voices_[i].setOscDetune(p.oscillators.osc1.detune * 100.0f);
            voices_[i].setFilterCutoff(p.filter.cutoff);
            voices_[i].setFilterResonance(p.filter.resonance);
            voices_[i].setFilterMode(p.filter.mode);
            voices_[i].setOscMix(oscMix_);
            voices_[i].setOscSyncEnabled(oscSync_);
            voices_[i].setSubOscLevel(subOscLevel_);
            voices_[i].setModWheel(modWheel_);
        }
    }

    lfo1_.setRate(currentParams_.lfos.lfo1.rate);
    lfo1_.setDepth(currentParams_.lfos.lfo1.depth);
    lfo1_.setWaveform(currentParams_.lfos.lfo1.waveform);
    lfo2_.setRate(currentParams_.lfos.lfo2.rate);
    lfo2_.setDepth(currentParams_.lfos.lfo2.depth);
    lfo2_.setWaveform(currentParams_.lfos.lfo2.waveform);

    reverb_.setMix(currentParams_.effects.reverb.mix);
    reverb_.setDecay(currentParams_.effects.reverb.decay);
    delay_.setMix(currentParams_.effects.delay.mix);
    delay_.setTime(currentParams_.effects.delay.time);
    delay_.setFeedback(currentParams_.effects.delay.feedback);
    distortion_.setDrive(currentParams_.effects.distortion.drive);
    distortion_.setMix(currentParams_.effects.distortion.mix);
    chorus_.setRate(currentParams_.effects.chorus.rate);
    chorus_.setDepth(currentParams_.effects.chorus.depth);
    chorus_.setMix(currentParams_.effects.chorus.mix);

    sequencer_.setTempo(currentParams_.sequencer.tempo);
    sequencer_.setPlaying(currentParams_.sequencer.playing);

    noiseLevel_ = currentParams_.oscillators.noiseLevel;
    subOscLevel_ = currentParams_.oscillators.subOscLevel;
    oscMix_ = currentParams_.oscillators.oscMix;
    oscSync_ = currentParams_.oscillators.syncEnabled;
    masterVolume_ = currentParams_.master.volume;
    pitchBend_ = currentParams_.master.pitchBend;
    modWheel_ = currentParams_.master.modulationWheel;

    for (int v = 0; v < MAX_VOICES; v++) {
        voices_[v].setPitchBend(pitchBend_);
    }
}

void SynthInstrument::swapParamsIfNeeded() {
    if (paramsPending_.exchange(false)) {
        currentParams_ = pendingParams_;
        applyModulationMatrix();

        if (pendingEffectsReset_.exchange(false, std::memory_order_acquire)) {
            reverb_.reset();
            delay_.reset();
        }
    }
}

void SynthInstrument::syncVoiceActiveStates() {
    for (int v = 0; v < MAX_VOICES; v++) {
        if (voiceActive_[v] && !voices_[v].isActive()) {
            voiceActive_[v] = false;
            activeVoiceCount_.fetch_sub(1, std::memory_order_relaxed);
        }
    }
}

float SynthInstrument::generateNoise() {
    // xorshift64* with thread-local state — deterministic, re-entrant, no shared global.
    thread_local uint64_t state = 123456789;
    state ^= state << 13;
    state ^= state >> 7;
    state ^= state << 17;
    // Upper 23 bits of the state → float in [0, 1) → map to [-1, 1]
    const float val = static_cast<float>((state >> 8) & 0x7FFFFF) / 8388607.0f;
    return val * 2.0f - 1.0f;
}

namespace {

inline void addIfFinite(float& dst, float offset) {
    if (!std::isnan(offset)) dst += offset;
}

} // namespace

void SynthInstrument::applyBlockAutomation(const SynthAutomation& snapshot) {
    // Sequence lock: bump the generation before and after the copy so a
    // concurrent reader can detect torn reads.
    uint32_t gen = automationGen_.load(std::memory_order_relaxed);
    automationGen_.store(gen + 1, std::memory_order_release);
    blockAutomation_ = snapshot;
    automationPending_.store(true, std::memory_order_release);
    automationGen_.store(gen + 2, std::memory_order_release);
}

void SynthInstrument::processBlockAutomation() {
    if (!automationPending_.exchange(false, std::memory_order_acq_rel)) {
        return;
    }
    // Read snapshot with sequence lock. Retry if the writer was mid-update.
    SynthAutomation local;
    uint32_t gen0, gen1;
    do {
        gen0 = automationGen_.load(std::memory_order_acquire);
        if (gen0 & 1u) continue; // writer in progress
        std::memcpy(&local, &blockAutomation_, sizeof(SynthAutomation));
        gen1 = automationGen_.load(std::memory_order_acquire);
    } while (gen0 != gen1);

    // Apply offsets additively to currentParams_. We deliberately do not
    // touch pendingParams_ so a later param snapshot from the UI overrides
    // automation cleanly.
    addIfFinite(currentParams_.oscillators.osc1.level, local.osc1Level);
    addIfFinite(currentParams_.oscillators.osc2.level, local.osc2Level);
    addIfFinite(currentParams_.oscillators.osc1.detune, local.osc1Detune);
    addIfFinite(currentParams_.oscillators.subOscLevel, local.subOscLevel);
    addIfFinite(currentParams_.oscillators.noiseLevel, local.noiseLevel);
    addIfFinite(currentParams_.oscillators.oscMix, local.oscMix);

    addIfFinite(currentParams_.filter.cutoff, local.filterCutoff);
    addIfFinite(currentParams_.filter.resonance, local.filterResonance);
    if (!std::isnan(local.filterMode)) currentParams_.filter.mode = static_cast<int>(local.filterMode);
    addIfFinite(currentParams_.filter.envelopeAmount, local.filterEnvAmount);

    addIfFinite(currentParams_.envelopes.attack, local.ampAttack);
    addIfFinite(currentParams_.envelopes.decay, local.ampDecay);
    addIfFinite(currentParams_.envelopes.sustain, local.ampSustain);
    addIfFinite(currentParams_.envelopes.release, local.ampRelease);
    addIfFinite(currentParams_.envelopes.filterAttack, local.filterAttack);
    addIfFinite(currentParams_.envelopes.filterDecay, local.filterDecay);
    addIfFinite(currentParams_.envelopes.filterSustain, local.filterSustain);
    addIfFinite(currentParams_.envelopes.filterRelease, local.filterRelease);

    addIfFinite(currentParams_.lfos.lfo1.rate, local.lfo1Rate);
    addIfFinite(currentParams_.lfos.lfo1.depth, local.lfo1Depth);
    addIfFinite(currentParams_.lfos.lfo2.rate, local.lfo2Rate);
    addIfFinite(currentParams_.lfos.lfo2.depth, local.lfo2Depth);

    addIfFinite(currentParams_.effects.reverb.mix, local.reverbMix);
    addIfFinite(currentParams_.effects.delay.mix, local.delayMix);
    addIfFinite(currentParams_.effects.distortion.drive, local.distortionDrive);
    addIfFinite(currentParams_.effects.chorus.mix, local.chorusMix);

    addIfFinite(currentParams_.master.volume, local.masterVolume);
    addIfFinite(currentParams_.master.pitchBend, local.pitchBend);
    addIfFinite(currentParams_.master.modulationWheel, local.modWheel);

    // Push the merged params down to the live DSP graph.
    applyModulationMatrix();
}
