#include "AudioEngine.h"
#include "SynthEngine.h"
#include <algorithm>
#include <cmath>
#include <cstdlib>

AudioEngine::AudioEngine() {
    for (int i = 0; i < MAX_VOICES; i++) {
        voiceActive_[i] = false;
    }
}

bool AudioEngine::init(double sampleRate) {
    sampleRate_ = sampleRate;

    // Initialize all components
    for (auto& voice : voices_) {
        voice.init(sampleRate);
    }
    lfo1_.init(sampleRate);
    lfo2_.init(sampleRate);
    reverb_.init(sampleRate);
    delay_.init(sampleRate);
    sequencer_.init(sampleRate);

    // Connect sequencer note events to the engine
    sequencer_.onNoteEvent = [this](int note, int velocity, bool noteOnEvt) {
        if (noteOnEvt) {
            this->noteOn(note, velocity);
        } else {
            this->noteOff(note);
        }
    };

    return true;
}

int AudioEngine::processAudio(float* outputBuffer, int numFrames) {
    swapParamsIfNeeded();

    // Process sequencer
    auto seqEvent = sequencer_.process(numFrames);

    // Process LFOs
    float lfo1Val = lfo1_.process();
    float lfo2Val = lfo2_.process();

    // Get modulation source values
    std::array<float, 6> modSources = {lfo1Val, lfo2Val, 0, 0, 0, 0};
    // ENV1 and ENV2 aren't easily available here - they're per-voice.
    // For global mod sources, we use the first active voice's env.

    for (int i = 0; i < numFrames; i++) {
        float sample = 0.0f;

        // Sum all active voices
        for (int v = 0; v < MAX_VOICES; v++) {
            if (voiceActive_[v]) {
                sample += voices_[v].process();
            }
        }

        // Add noise
        if (noiseLevel_ > 0.0f) {
            sample += generateNoise() * noiseLevel_;
        }

        // --- Effects chain ---
        if (!currentParams_.effects.bypass) {
            sample = distortion_.process(sample);
            sample = delay_.process(sample);
            sample = reverb_.process(sample);
        }

        // Master volume
        sample *= static_cast<float>(masterVolume_);

        // Clamp
        sample = std::clamp(sample, -1.0f, 1.0f);

        outputBuffer[i] = sample;
    }

    // Sync voiceActive_ with each voice's internal active state
    for (int v = 0; v < MAX_VOICES; v++) {
        if (voiceActive_[v] && !voices_[v].isActive()) {
            voiceActive_[v] = false;
        }
    }

    return numFrames;
}

// ---- Note Handling ----

void AudioEngine::noteOn(int midiNote, int velocity) {
    int voiceIdx = allocateVoice();
    if (voiceIdx >= 0) {
        voices_[voiceIdx].init(sampleRate_);
        // Apply current params to voice
        applyModulationMatrix();
        voices_[voiceIdx].noteOn(midiNote, velocity);
        voiceActive_[voiceIdx] = true;
    }
}

void AudioEngine::noteOff(int midiNote) {
    int voiceIdx = findVoiceByNote(midiNote);
    if (voiceIdx >= 0) {
        voices_[voiceIdx].noteOff();
    }
}

int AudioEngine::allocateVoice() {
    // Look for free voice
    for (int i = 0; i < MAX_VOICES; i++) {
        if (!voiceActive_[i]) return i;
    }
    // Voice stealing: find oldest (voice 0)
    // In a real synth you'd track note age; for simplicity, steal first
    voices_[0].stopImmediately();
    return 0;
}

void AudioEngine::releaseVoice(int index) {
    if (index >= 0 && index < MAX_VOICES) {
        voiceActive_[index] = false;
    }
}

int AudioEngine::findVoiceByNote(int midiNote) {
    for (int i = 0; i < MAX_VOICES; i++) {
        if (voiceActive_[i] && voices_[i].getNote() == midiNote) {
            return i;
        }
    }
    return -1;
}

// ---- Parameter Setters ----

void AudioEngine::setParams(const SynthParams& params) {
    pendingParams_ = params;
    paramsPending_ = true;
    paramsDirty_ = true;
}

#define SET_AND_SYNC(field, value) \
    pendingParams_.field = value; \
    paramsPending_ = true;

void AudioEngine::setOsc1Level(float v) { SET_AND_SYNC(oscillators.osc1.level, v); }
void AudioEngine::setOsc2Level(float v) { SET_AND_SYNC(oscillators.osc2.level, v); }
void AudioEngine::setOsc1Waveform(int w) { SET_AND_SYNC(oscillators.osc1.waveform, w); }
void AudioEngine::setOsc2Waveform(int w) { SET_AND_SYNC(oscillators.osc2.waveform, w); }
void AudioEngine::setOscDetune(float v) { SET_AND_SYNC(oscillators.osc1.detune, v); }
void AudioEngine::setSubOscLevel(float v) { SET_AND_SYNC(oscillators.subOscLevel, v); }
void AudioEngine::setNoiseLevel(float v) { SET_AND_SYNC(oscillators.noiseLevel, v); }
void AudioEngine::setOscMix(float v) { SET_AND_SYNC(oscillators.oscMix, v); }
void AudioEngine::setOscSync(bool s) { SET_AND_SYNC(oscillators.syncEnabled, s); }

void AudioEngine::setFilterCutoff(float v) { SET_AND_SYNC(filter.cutoff, v); }
void AudioEngine::setFilterResonance(float v) { SET_AND_SYNC(filter.resonance, v); }
void AudioEngine::setFilterMode(int m) { SET_AND_SYNC(filter.mode, m); }
void AudioEngine::setFilterEnvAmount(float v) { SET_AND_SYNC(filter.envelopeAmount, v); }

void AudioEngine::setAmpAttack(float v) { SET_AND_SYNC(envelopes.attack, v); }
void AudioEngine::setAmpDecay(float v) { SET_AND_SYNC(envelopes.decay, v); }
void AudioEngine::setAmpSustain(float v) { SET_AND_SYNC(envelopes.sustain, v); }
void AudioEngine::setAmpRelease(float v) { SET_AND_SYNC(envelopes.release, v); }
void AudioEngine::setFilterAttack(float v) { SET_AND_SYNC(envelopes.filterAttack, v); }
void AudioEngine::setFilterDecay(float v) { SET_AND_SYNC(envelopes.filterDecay, v); }
void AudioEngine::setFilterSustain(float v) { SET_AND_SYNC(envelopes.filterSustain, v); }
void AudioEngine::setFilterRelease(float v) { SET_AND_SYNC(envelopes.filterRelease, v); }

void AudioEngine::setLfo1Rate(float v) { SET_AND_SYNC(lfos.lfo1.rate, v); }
void AudioEngine::setLfo1Depth(float v) { SET_AND_SYNC(lfos.lfo1.depth, v); }
void AudioEngine::setLfo1Waveform(int w) { SET_AND_SYNC(lfos.lfo1.waveform, w); }
void AudioEngine::setLfo2Rate(float v) { SET_AND_SYNC(lfos.lfo2.rate, v); }
void AudioEngine::setLfo2Depth(float v) { SET_AND_SYNC(lfos.lfo2.depth, v); }
void AudioEngine::setLfo2Waveform(int w) { SET_AND_SYNC(lfos.lfo2.waveform, w); }

void AudioEngine::setReverbMix(float v) { SET_AND_SYNC(effects.reverb.mix, v); }
void AudioEngine::setReverbDecay(float v) { SET_AND_SYNC(effects.reverb.decay, v); }
void AudioEngine::setDelayMix(float v) { SET_AND_SYNC(effects.delay.mix, v); }
void AudioEngine::setDelayTime(float v) { SET_AND_SYNC(effects.delay.time, v); }
void AudioEngine::setDelayFeedback(float v) { SET_AND_SYNC(effects.delay.feedback, v); }
void AudioEngine::setDistortionDrive(float v) { SET_AND_SYNC(effects.distortion.drive, v); }
void AudioEngine::setDistortionMix(float v) { SET_AND_SYNC(effects.distortion.mix, v); }
void AudioEngine::setEffectsBypass(bool b) { SET_AND_SYNC(effects.bypass, b); }

void AudioEngine::setModulationRoute(int index, const ModulationRoute& route) {
    if (index >= 0 && index < 8) {
        pendingParams_.modulation.routes[index] = route;
        paramsPending_ = true;
    }
}

void AudioEngine::setSequencerSteps(const std::array<SequencerStep, SEQUENCER_STEPS>& steps) {
    pendingParams_.sequencer.steps = steps;
    paramsPending_ = true;
}

void AudioEngine::setSequencerTempo(float bpm) {
    pendingParams_.sequencer.tempo = bpm;
    paramsPending_ = true;
}

void AudioEngine::setSequencerPlaying(bool play) {
    pendingParams_.sequencer.playing = play;
    paramsPending_ = true;
}

void AudioEngine::setMasterVolume(float v) { SET_AND_SYNC(master.volume, v); }
void AudioEngine::setPitchBend(float v) { SET_AND_SYNC(master.pitchBend, v); }
void AudioEngine::setModWheel(float v) { SET_AND_SYNC(master.modulationWheel, v); }

// ---- Internal ----

void AudioEngine::applyModulationMatrix() {
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

            voices_[i].setAmpEnvelope(
                p.envelopes.attack, p.envelopes.decay,
                p.envelopes.sustain, p.envelopes.release);
            voices_[i].setFilterEnvelope(
                p.envelopes.filterAttack, p.envelopes.filterDecay,
                p.envelopes.filterSustain, p.envelopes.filterRelease);
        }
    }

    // Sync global components
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

void AudioEngine::swapParamsIfNeeded() {
    if (paramsPending_.exchange(false)) {
        currentParams_ = pendingParams_;
        applyModulationMatrix();
    }
}

float AudioEngine::generateNoise() {
    return static_cast<float>(rand()) / static_cast<float>(RAND_MAX) * 2.0f - 1.0f;
}

float AudioEngine::generateSubOsc(double freq) {
    static double subPhase = 0.0;
    subPhase += freq / sampleRate_;
    if (subPhase >= 1.0) subPhase -= 1.0;
    return (subPhase < 0.5) ? 1.0f : -1.0f;
}

SynthParams AudioEngine::getCurrentParams() const {
    return currentParams_;
}
