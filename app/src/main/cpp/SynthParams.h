#ifndef JUJISYNTH_PARAMS_H
#define JUJISYNTH_PARAMS_H

#include <cstdint>
#include <array>

// ---- Oscillator Parameters ----
struct OscillatorParams {
    float level = 1.0f;          // 0.0 - 1.0
    float detune = 0.0f;         // -1.0 - 1.0 (cents scaled)
    int waveform = 0;            // 0=saw, 1=square, 2=triangle, 3=sine
    bool active = true;
};

struct SynthOscillatorSection {
    OscillatorParams osc1;
    OscillatorParams osc2;
    float subOscLevel = 0.0f;    // 0.0 - 1.0 (1 octave below, square)
    float noiseLevel = 0.0f;     // 0.0 - 1.0
    bool syncEnabled = false;    // OSC2 sync to OSC1
    float oscMix = 0.5f;         // 0.0 = only OSC1, 1.0 = only OSC2
};

// ---- Filter Parameters ----
struct SynthFilterSection {
    float cutoff = 0.8f;         // 0.0 - 1.0 (mapped 20Hz-20kHz)
    float resonance = 0.0f;      // 0.0 - 1.0
    int mode = 0;                // 0=LPF, 1=HPF, 2=BPF
    float envelopeAmount = 0.0f; // -1.0 - 1.0
    float keyTracking = 0.0f;    // 0.0 - 1.0
    bool active = true;
};

// ---- Envelope Parameters ----
struct SynthEnvelopeSection {
    // AMP ENV
    float attack = 0.01f;        // 0.0 - 1.0 (mapped 0-10000ms)
    float decay = 0.3f;          // 0.0 - 1.0
    float sustain = 0.7f;        // 0.0 - 1.0
    float release = 0.2f;        // 0.0 - 1.0
    // FILTER ENV (independent)
    float filterAttack = 0.01f;
    float filterDecay = 0.3f;
    float filterSustain = 0.7f;
    float filterRelease = 0.2f;
};

// ---- LFO Parameters ----
struct LfoParams {
    float rate = 1.0f;           // 0.0 - 1.0 (mapped 0.01-50Hz)
    float depth = 0.0f;          // 0.0 - 1.0
    int waveform = 0;            // 0=sine, 1=square, 2=saw, 3=triangle, 4=random
    bool keySync = false;
    int destination = 0;         // 0=pitch, 1=filter, 2=amp, 3=oscMix
};

struct SynthLfoSection {
    LfoParams lfo1;
    LfoParams lfo2;
};

// ---- Effects Parameters ----
struct ReverbParams {
    float mix = 0.0f;            // 0.0 - 1.0
    float decay = 0.5f;          // 0.0 - 1.0
    float damping = 0.5f;        // 0.0 - 1.0
    bool active = false;
};

struct DelayParams {
    float mix = 0.0f;            // 0.0 - 1.0
    float time = 0.5f;           // 0.0 - 1.0 (mapped 20-2000ms)
    float feedback = 0.3f;       // 0.0 - 1.0
    bool tempoSync = false;
    bool active = false;
};

struct DistortionParams {
    float drive = 0.0f;          // 0.0 - 1.0
    float mix = 0.0f;            // 0.0 - 1.0
    bool active = false;
};

struct ChorusParams {
    float rate = 0.3f;     // 0.0-1.0
    float depth = 0.0f;    // 0.0-1.0
    float mix = 0.0f;      // 0.0-1.0
    bool active = false;
};

struct SynthEffectsSection {
    ReverbParams reverb;
    DelayParams delay;
    DistortionParams distortion;
    ChorusParams chorus;
    bool bypass = false;
};

// ---- Modulation Matrix ----
struct ModulationRoute {
    int source = 0;              // 0=LFO1, 1=LFO2, 2=ENV1, 3=ENV2, 4=velocity, 5=aftertouch
    int destination = 0;         // 0=pitch, 1=filter_cutoff, 2=filter_res, 3=amp, 4=osc_mix, 5=LFO_rate
    float amount = 0.0f;         // -1.0 - 1.0
    bool active = false;
};

struct SynthModulationSection {
    std::array<ModulationRoute, 8> routes; // Up to 8 simultaneous routings
};

// ---- Sequencer Parameters ----
constexpr int SEQUENCER_STEPS = 16;

struct SequencerStep {
    int note = -1;               // -1 = rest, 0-127 = MIDI note
    int velocity = 100;          // 0-127
    float gate = 0.8f;           // 0.0 - 1.0
    float automation = 0.0f;     // 0.0 - 1.0 (generic parameter automation)
};

struct SynthSequencerSection {
    std::array<SequencerStep, SEQUENCER_STEPS> steps{};
    bool playing = false;
    int currentStep = 0;
    float tempo = 120.0f;        // 30-300 BPM
    int resolution = 0;          // 0=1/4, 1=1/8, 2=1/16
};

// ---- Master ----
struct SynthMasterState {
    float volume = 0.8f;         // 0.0 - 1.0
    int polyphonyMode = 0;       // 0=poly (4-voice), 1=mono, 2=unison
    float pitchBend = 0.0f;      // -1.0 - 1.0
    float modulationWheel = 0.0f;// 0.0 - 1.0
};

// Complete synth parameter bundle
struct SynthParams {
    SynthOscillatorSection oscillators;
    SynthFilterSection filter;
    SynthEnvelopeSection envelopes;
    SynthLfoSection lfos;
    SynthEffectsSection effects;
    SynthModulationSection modulation;
    SynthSequencerSection sequencer;
    SynthMasterState master;
};

#endif // JUJISYNTH_PARAMS_H
