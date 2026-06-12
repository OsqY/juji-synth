#include "AudioEngine.h"
#include "SynthEngine.h"
#include "Chorus.h"
#include <android/log.h>
#include <algorithm>
#include <cmath>
#include <cstdlib>

#define LOG_TAG "JujiSynth"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

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
    chorus_.init(sampleRate);
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
    processNoteQueue();  // drain pending note events FIRST
    swapParamsIfNeeded();

    // Process sequencer
    (void)sequencer_.process(numFrames);

    // Process LFOs
    float lfo1Val = lfo1_.process();
    float lfo2Val = lfo2_.process();

    // Get modulation source values and apply to active voices (block-rate)
    std::array<float, 6> modSources = {lfo1Val, lfo2Val, 0, 0, 0, 0};
    // ENV1 and ENV2 aren't easily available here - they're per-voice.
    // For global mod sources, we use the first active voice's env.
    for (int v = 0; v < MAX_VOICES; v++) {
        if (voiceActive_[v]) {
            // Filter cutoff modulation
            float cutoffMod = modMatrix_.getModulation(1, modSources);
            if (cutoffMod != 0.0f) {
                float base = currentParams_.filter.cutoff;
                voices_[v].setFilterCutoff(std::clamp(base + cutoffMod, 0.0f, 1.0f));
            }
            // Filter resonance modulation
            float resMod = modMatrix_.getModulation(2, modSources);
            if (resMod != 0.0f) {
                float base = currentParams_.filter.resonance;
                voices_[v].setFilterResonance(std::clamp(base + resMod, 0.0f, 1.0f));
            }
            // Pitch modulation (semitones offset)
            float pitchMod = modMatrix_.getModulation(0, modSources);
            if (pitchMod != 0.0f) {
                voices_[v].setPitchBend(pitchBend_ + pitchMod);
            }
            // Amp modulation (osc level offset)
            float ampMod = modMatrix_.getModulation(3, modSources);
            if (ampMod != 0.0f) {
                float base = currentParams_.oscillators.osc1.level;
                float modded = std::clamp(base + ampMod, 0.0f, 1.0f);
                voices_[v].setOsc1Level(modded);
                voices_[v].setOsc2Level(modded);
            }
            // Osc mix modulation
            float mixMod = modMatrix_.getModulation(4, modSources);
            if (mixMod != 0.0f) {
                float base = oscMix_;
                voices_[v].setOscMix(std::clamp(base + mixMod, 0.0f, 1.0f));
            }
        }
    }

    for (int i = 0; i < numFrames; i++) {
        float sample = 0.0f;

        // Sum all active voices
        for (int v = 0; v < MAX_VOICES; v++) {
            if (voiceActive_[v]) {
                sample += voices_[v].process();
            }
        }

        // Add noise (gated by both knob value AND voice activity)
        // Noise responds immediately to knob changes — if noiseLevel_ is 0, output 0.
        float noiseTarget = (noiseLevel_ > 0.001f && activeVoiceCount_.load(std::memory_order_relaxed) > 0)
            ? noiseLevel_ : 0.0f;
        noiseSmooth_ += (noiseTarget - noiseSmooth_) * 0.02f; // faster smoothing (was 0.005)
        if (noiseSmooth_ > 0.001f) {
            sample += generateNoise() * noiseSmooth_;
        }

        // --- Effects chain ---
        if (!currentParams_.effects.bypass) {
            sample = distortion_.process(sample);
            sample = chorus_.process(sample);
            sample = delay_.process(sample);
            sample = reverb_.process(sample);
        }

        // Master volume
        sample *= static_cast<float>(masterVolume_);

        // Clamp
        sample = std::clamp(sample, -1.0f, 1.0f);

        outputBuffer[i] = sample;

        // Feed oscilloscope buffer
        scopeBuffer_[scopeWriteIndex_] = sample;
        scopeWriteIndex_ = (scopeWriteIndex_ + 1) % SCOPE_SIZE;
    }

    // Sync voiceActive_ with each voice's internal active state
    for (int v = 0; v < MAX_VOICES; v++) {
        if (voiceActive_[v] && !voices_[v].isActive()) {
            voiceActive_[v] = false;
            activeVoiceCount_.fetch_sub(1, std::memory_order_relaxed);
        }
    }

    return numFrames;
}

// ---- Note Handling ----

void AudioEngine::noteOn(int midiNote, int velocity) {
    // Push to lock-free queue instead of directly modifying voices
    int tail = noteQueueTail_.load(std::memory_order_relaxed);
    int nextTail = (tail + 1) % NOTE_QUEUE_SIZE;
    if (nextTail != noteQueueHead_.load(std::memory_order_acquire)) {
        noteQueue_[tail] = {NoteEvent::NoteOn, midiNote, velocity};
        noteQueueTail_.store(nextTail, std::memory_order_release);
    }
}

void AudioEngine::noteOff(int midiNote) {
    // Push to lock-free queue instead of directly modifying voices
    int tail = noteQueueTail_.load(std::memory_order_relaxed);
    int nextTail = (tail + 1) % NOTE_QUEUE_SIZE;
    if (nextTail != noteQueueHead_.load(std::memory_order_acquire)) {
        noteQueue_[tail] = {NoteEvent::NoteOff, midiNote, 0};
        noteQueueTail_.store(nextTail, std::memory_order_release);
    }
}

void AudioEngine::handleNoteOn(int midiNote, int velocity) {
    // Re-trigger: silence any existing voice already playing this note
    for (int i = 0; i < MAX_VOICES; i++) {
        if (voiceActive_[i] && voices_[i].getNote() == midiNote) {
            voices_[i].stopImmediately();
            voiceActive_[i] = false;
            activeVoiceCount_.fetch_sub(1, std::memory_order_relaxed);
        }
    }

    int voiceIdx = allocateVoice();
    if (voiceIdx >= 0) {
        voices_[voiceIdx].init(sampleRate_);
        // Apply current params to voice (osc, filter, LFO, effects, etc.)
        // applyModulationMatrix is NOT called here — it runs from swapParamsIfNeeded
        // on the audio thread already. Envelopes are set directly:
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

        // Apply per-voice params so the new voice starts with the correct state
        // (applyModulationMatrix only updates currently-active voices)
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
        voiceActive_[voiceIdx] = true;
        activeVoiceCount_.fetch_add(1, std::memory_order_relaxed);
    }
}

void AudioEngine::handleNoteOff(int midiNote) {
    // Release ALL voices playing this MIDI note
    for (int i = 0; i < MAX_VOICES; i++) {
        if (voiceActive_[i] && voices_[i].getNote() == midiNote) {
            voices_[i].noteOff();
        }
    }
}

void AudioEngine::processNoteQueue() {
    while (true) {
        int head = noteQueueHead_.load(std::memory_order_acquire);
        int tail = noteQueueTail_.load(std::memory_order_acquire);
        if (head == tail) break;  // Queue is empty

        auto& event = noteQueue_[head];
        if (event.type == NoteEvent::NoteOn) {
            handleNoteOn(event.note, event.velocity);
        } else {
            handleNoteOff(event.note);
        }
        noteQueueHead_.store((head + 1) % NOTE_QUEUE_SIZE, std::memory_order_release);
    }
}

int AudioEngine::allocateVoice() {
    // Look for free voice
    for (int i = 0; i < MAX_VOICES; i++) {
        if (!voiceActive_[i]) return i;
    }
    // Voice stealing: find oldest voice (highest age counter)
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
}

void AudioEngine::setAllParamsFromArray(const float* values, int count) {
    if (count < SYNTH_PARAM_COUNT) return;

    // Array layout (must match Kotlin side in applySynthStateToEngine):
    // 0-8:  osc params
    // 9-12: filter params
    // 13-20: amp + filter envelopes
    // 21-26: LFO1 + LFO2
    // 27-38: effects + master
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
    LOGI("setAllParamsFromArray: chorus_rate=%f chorus_depth=%f chorus_mix=%f filter_res=%f filter_envAmt=%f",
         values[35], values[36], values[37], values[10], values[12]);

    // Signal audio thread after ALL fields are written (atomic snapshot)
    paramsPending_.store(true, std::memory_order_release);
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
void AudioEngine::setChorusRate(float v) { SET_AND_SYNC(effects.chorus.rate, v); }
void AudioEngine::setChorusDepth(float v) { SET_AND_SYNC(effects.chorus.depth, v); }
void AudioEngine::setChorusMix(float v) { SET_AND_SYNC(effects.chorus.mix, v); }
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

void AudioEngine::panic() {
    // Stop all voices immediately
    for (int i = 0; i < MAX_VOICES; i++) {
        if (voiceActive_[i]) {
            voices_[i].stopImmediately();
            voiceActive_[i] = false;
        }
    }
    activeVoiceCount_ = 0;
    // Stop sequencer
    sequencer_.setPlaying(false);
    currentParams_.sequencer.playing = false;
    pendingParams_.sequencer.playing = false;
    // Reset effects
    reverb_.reset();
    delay_.reset();
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
            voices_[i].setOscMix(oscMix_);
            voices_[i].setOscSyncEnabled(oscSync_);
            voices_[i].setSubOscLevel(subOscLevel_);
            voices_[i].setModWheel(modWheel_);
            // NOTE: ADSR envelopes intentionally NOT set here for active voices.
            // Applying envelope changes to held voices causes audible stutter/cutout.
            // Envelopes are set in noteOn() for new voices only.
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

void AudioEngine::swapParamsIfNeeded() {
    if (paramsPending_.exchange(false)) {
        LOGI("swapParamsIfNeeded: BEFORE swap chorus_r=%f chorus_d=%f chorus_m=%f filter_res=%f filter_envAmt=%f",
             pendingParams_.effects.chorus.rate, pendingParams_.effects.chorus.depth,
             pendingParams_.effects.chorus.mix, pendingParams_.filter.resonance,
             pendingParams_.filter.envelopeAmount);
        currentParams_ = pendingParams_;
        applyModulationMatrix();

        // Reset effects if flagged (e.g. on preset load).
        // Acquire ensures we see all param writes from the UI thread
        // that happened before the release-store in setPendingEffectsReset().
        if (pendingEffectsReset_.exchange(false, std::memory_order_acquire)) {
            reverb_.reset();
            delay_.reset();
        }
    }
}

 float AudioEngine::generateNoise() {
    return static_cast<float>(rand()) / static_cast<float>(RAND_MAX) * 2.0f - 1.0f;
}

void AudioEngine::getWaveform(float* out, int maxSize) const {
    int n = std::min(maxSize, SCOPE_SIZE);
    // Copy in order from oldest to newest
    int start = scopeWriteIndex_;
    for (int i = 0; i < n; i++) {
        out[i] = scopeBuffer_[(start + i) % SCOPE_SIZE];
    }
}

SynthParams AudioEngine::getCurrentParams() const {
    return currentParams_;
}
