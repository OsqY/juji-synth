#include "SynthVoice.h"
#include <algorithm>
#include <cstdint>
#include <cmath>

SynthVoice::SynthVoice() = default;

void SynthVoice::init(double sampleRate) {
    sampleRate_ = sampleRate;
    osc1_.init(sampleRate);
    osc2_.init(sampleRate);
    filter_.init(sampleRate);
    ampEnv_.init(sampleRate);
    filterEnv_.init(sampleRate);
    subPhase_ = 0.0;
}

void SynthVoice::noteOn(int midiNote, int velocity) {
    midiNote_ = midiNote;
    velocity_ = velocity;
    active_ = true;
    age_++;

    double freq = midiNoteToFrequency(midiNote);
    osc1_.setFrequency(freq);
    osc2_.setFrequency(freq * std::pow(2.0, detune_ / 1200.0)); // detune in cents

    // Velocity affects amplitude
    double velScale = velocity / 127.0;
    osc1_.setAmplitude(velScale);
    osc2_.setAmplitude(velScale);

    osc1_.reset();
    osc2_.reset();
    ampEnv_.noteOn();
    filterEnv_.noteOn();
}

void SynthVoice::noteOff() {
    ampEnv_.noteOff();
    filterEnv_.noteOff();
    // Voice stays active until envelope finishes
}

float SynthVoice::process() {
    if (!active_) return 0.0f;

    // Apply pitch bend and mod wheel
    double pitchBendSemitones = pitchBend_ * 2.0 + modWheel_ * 0.5; // ±2 + mod wheel up to 0.5 semitones
    double bendFactor = std::pow(2.0, pitchBendSemitones / 12.0);
    double freq = midiNoteToFrequency(midiNote_) * bendFactor;

    osc1_.setFrequency(freq);
    double osc2Freq = freq * std::pow(2.0, detune_ / 1200.0);
    osc2_.setFrequency(osc2Freq);

    // Process oscillators
    float osc1Out = osc1_.process();
    float osc2Out = osc2_.process();

    // Oscillator sync: if OSC1 phase wrapped, sync OSC2
    if (oscSyncEnabled_ && osc1_.didPhaseWrap()) {
        osc2_.sync();
    }

    // Mix oscillators using oscMix parameter
    float mixedOut = osc1Out * (1.0f - oscMix_) + osc2Out * oscMix_;

    // Add sub-oscillator (square wave one octave below)
    double subFreq = midiNoteToFrequency(midiNote_) * 0.5 * bendFactor;
    subPhase_ += subFreq / sampleRate_;
    if (subPhase_ >= 1.0) subPhase_ -= 1.0;
    float subSample = (subPhase_ < 0.5f) ? 1.0f : -1.0f;
    mixedOut += subSample * subOscLevel_;

    // Process envelope
    float ampEnv = ampEnv_.process();
    float filterEnv = filterEnv_.process();

    // Apply filter envelope to filter
    filter_.applyEnvelope(filterEnv);
    float filteredOut = filter_.process(mixedOut);

    // Apply amplitude envelope
    float output = filteredOut * ampEnv;

    // Check if voice is done
    if (ampEnv_.isIdle()) {
        active_ = false;
    }

    return output;
}

void SynthVoice::setAmpEnvelope(float a, float d, float s, float r) {
    ampEnv_.setAttack(a);
    ampEnv_.setDecay(d);
    ampEnv_.setSustain(s);
    ampEnv_.setRelease(r);
}

void SynthVoice::setFilterEnvelope(float a, float d, float s, float r) {
    filterEnv_.setAttack(a);
    filterEnv_.setDecay(d);
    filterEnv_.setSustain(s);
    filterEnv_.setRelease(r);
}

bool SynthVoice::isActive() const {
    return active_;
}

void SynthVoice::stopImmediately() {
    active_ = false;
    ampEnv_.reset();
    filterEnv_.reset();
}

double SynthVoice::midiNoteToFrequency(int midiNote) const {
    return 440.0 * std::pow(2.0, (midiNote - 69) / 12.0);
}
