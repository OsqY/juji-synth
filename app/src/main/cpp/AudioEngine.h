#ifndef JUJISYNTH_AUDIOENGINE_H
#define JUJISYNTH_AUDIOENGINE_H

#include "SynthParams.h"
#include "SynthVoice.h"
#include "LFO.h"
#include "ModulationMatrix.h"
#include "Sequencer.h"
#include "Reverb.h"
#include "Delay.h"
#include "Distortion.h"
#include <array>
#include <memory>
#include <atomic>

/**
 * Main audio engine that orchestrates the entire synthesizer.
 * 
 * - Manages 4 voices of polyphony
 * - Applies modulation matrix routings
 * - Routes through effects chain
 * - Handles sequencer playback
 * - Runs on the audio thread via Oboe callback
 * 
 * All parameter updates are thread-safe via atomic flags.
 */
class AudioEngine {
public:
    AudioEngine();
    ~AudioEngine() = default;

    /** Initialize engine with sample rate */
    bool init(double sampleRate);

    /** Process audio callback: fill buffer with samples */
    int processAudio(float* outputBuffer, int numFrames);

    // ---- Parameter Updates (called from UI thread via JNI) ----

    /** Set all parameters at once (atomic snapshot) */
    void setParams(const SynthParams& params);

    // Note events
    void noteOn(int midiNote, int velocity);
    void noteOff(int midiNote);

    // Individual parameter setters
    void setOsc1Level(float v);
    void setOsc2Level(float v);
    void setOsc1Waveform(int w);
    void setOsc2Waveform(int w);
    void setOscDetune(float v);
    void setSubOscLevel(float v);
    void setNoiseLevel(float v);
    void setOscMix(float v);
    void setOscSync(bool s);

    void setFilterCutoff(float v);
    void setFilterResonance(float v);
    void setFilterMode(int m);
    void setFilterEnvAmount(float v);

    void setAmpAttack(float v);
    void setAmpDecay(float v);
    void setAmpSustain(float v);
    void setAmpRelease(float v);
    void setFilterAttack(float v);
    void setFilterDecay(float v);
    void setFilterSustain(float v);
    void setFilterRelease(float v);

    void setLfo1Rate(float v);
    void setLfo1Depth(float v);
    void setLfo1Waveform(int w);
    void setLfo2Rate(float v);
    void setLfo2Depth(float v);
    void setLfo2Waveform(int w);

    void setReverbMix(float v);
    void setReverbDecay(float v);
    void setDelayMix(float v);
    void setDelayTime(float v);
    void setDelayFeedback(float v);
    void setDistortionDrive(float v);
    void setDistortionMix(float v);
    void setEffectsBypass(bool b);

    void setModulationRoute(int index, const ModulationRoute& route);

    void setSequencerSteps(const std::array<SequencerStep, SEQUENCER_STEPS>& steps);
    void setSequencerTempo(float bpm);
    void setSequencerPlaying(bool play);

    void setMasterVolume(float v);
    void setPitchBend(float v);
    void setModWheel(float v);

    /** Get current parameters (for UI state sync) */
    SynthParams getCurrentParams() const;

private:
    std::atomic<double> sampleRate_{44100.0};
    std::atomic<bool> paramsDirty_{false};

    // Synth state (protected by atomic swap or mutex)
    SynthParams currentParams_;
    SynthParams pendingParams_;
    std::atomic<bool> paramsPending_{false};

    // Voices
    static constexpr int MAX_VOICES = 4;
    std::array<SynthVoice, MAX_VOICES> voices_;
    bool voiceActive_[MAX_VOICES] = {false, false, false, false};

    // Modulation
    LFO lfo1_;
    LFO lfo2_;
    LFO modLfo_; // internal modulation LFO
    ModulationMatrix modMatrix_;

    // Effects
    Reverb reverb_;
    Delay delay_;
    Distortion distortion_;

    // Sequencer
    Sequencer sequencer_;

    // Internal state
    float noiseState_ = 0.0f;
    float subOscLevel_ = 0.0f;
    float noiseLevel_ = 0.0f;
    float oscMix_ = 0.5f;
    bool oscSync_ = false;
    float masterVolume_ = 0.8f;
    float pitchBend_ = 0.0f;
    float modWheel_ = 0.0f;

    // Polyphony
    int allocateVoice();
    void releaseVoice(int index);
    int findVoiceByNote(int midiNote);
    float generateNoise();
    float generateSubOsc(double freq);

    void applyModulationMatrix();
    void swapParamsIfNeeded();
};

#endif // JUJISYNTH_AUDIOENGINE_H
