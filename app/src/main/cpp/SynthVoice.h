#ifndef JUJISYNTH_SYNTHVOICE_H
#define JUJISYNTH_SYNTHVOICE_H

#include "Oscillator.h"
#include "Filter.h"
#include "Envelope.h"
#include "LFO.h"

/**
 * SynthVoice represents a single monophonic voice in a polyphonic synth.
 * 
 * Each voice contains its own oscillators, filter, envelopes, and LFO
 * for independent note articulation.
 */
class SynthVoice {
public:
    SynthVoice();
    ~SynthVoice() = default;

    void init(double sampleRate);

    /** Trigger note on with MIDI note (0-127) and velocity (0-127) */
    void noteOn(int midiNote, int velocity);

    /** Trigger note off */
    void noteOff();

    /** Process: get next audio sample */
    float process();

    /** Set oscillator parameters */
    void setOsc1Level(float level) { osc1_.setAmplitude(level); }
    void setOsc2Level(float level) { osc2_.setAmplitude(level); }
    void setOsc1Waveform(int wf) { osc1_.setWaveform(wf); }
    void setOsc2Waveform(int wf) { osc2_.setWaveform(wf); }
    void setOscDetune(float det) { detune_ = det; }

    // Filter
    void setFilterCutoff(float c) { filter_.setCutoff(c); }
    void setFilterResonance(float r) { filter_.setResonance(r); }
    void setFilterMode(int m) { filter_.setMode(m); }

    // Envelopes
    void setAmpEnvelope(float a, float d, float s, float r);
    void setFilterEnvelope(float a, float d, float s, float r);

    /** Is voice currently sounding or releasing? */
    bool isActive() const;

    /** Get MIDI note */
    int getNote() const { return midiNote_; }

    /** Get velocity */
    int getVelocity() const { return velocity_; }

    /** Get voice age (incremented on each noteOn) */
    uint64_t getAge() const { return age_; }

    /** Mark voice as free (force idle) */
    void stopImmediately();

    /** Set pitch bend */
    void setPitchBend(float bend) { pitchBend_ = bend; }

private:
    Oscillator osc1_;
    Oscillator osc2_;
    Filter filter_;
    Envelope ampEnv_;
    Envelope filterEnv_;

    int midiNote_ = 69;
    int velocity_ = 100;
    bool active_ = false;

    uint64_t age_ = 0;

    double sampleRate_ = 44100.0;
    float detune_ = 0.0f;
    float pitchBend_ = 0.0f;

    double midiNoteToFrequency(int midiNote) const;
};

#endif // JUJISYNTH_SYNTHVOICE_H
