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
#include "Chorus.h"
#include <array>
#include <memory>
#include <atomic>

struct NoteEvent {
    enum Type : uint8_t { NoteOn, NoteOff } type;
    int note = 0;
    int velocity = 0;
};

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

    /** Set all parameters from a float array (for bulk JNI apply).
     *  Array must have exactly SYNTH_PARAM_COUNT floats in the defined order.
     *  The entire pending state is written before paramsPending_ is set,
     *  guaranteeing the audio thread sees a fully consistent state. */
    static constexpr int SYNTH_PARAM_COUNT = 39;
    void setAllParamsFromArray(const float* values, int count);

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
    void setChorusRate(float v);
    void setChorusDepth(float v);
    void setChorusMix(float v);
    void setEffectsBypass(bool b);

    void setModulationRoute(int index, const ModulationRoute& route);

    void setSequencerSteps(const std::array<SequencerStep, SEQUENCER_STEPS>& steps);
    void setSequencerTempo(float bpm);
    void setSequencerPlaying(bool play);
    int getSequencerStep() const { return sequencer_.getCurrentStep(); }
    void setSequencerLooping(bool loop) { sequencer_.setLooping(loop); }
    bool getSequencerLooping() const { return sequencer_.isLooping(); }

    void setMasterVolume(float v);
    void setPitchBend(float v);
    void setModWheel(float v);

    /** Emergency panic: stop all sound immediately */
    void panic();

    /** Get current parameters (for UI state sync) */
    SynthParams getCurrentParams() const;

    /** Copy latest N output samples into buffer for oscilloscope display.
     *  Thread-safe: called from UI thread, written from audio thread. */
    static constexpr int SCOPE_SIZE = 512;
    void getWaveform(float* out, int maxSize) const;

    /** Reset delay and reverb buffers (called from JNI on preset load).
     *  Uses memory_order_release to ensure the audio thread sees the flag
     *  after any preceding preset param writes. */
    void setPendingEffectsReset() {
        pendingEffectsReset_.store(true, std::memory_order_release);
    }

private:
    std::atomic<double> sampleRate_{44100.0};

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
    Chorus chorus_;

    // Sequencer
    Sequencer sequencer_;

    // Internal state
    float subOscLevel_ = 0.0f;
    float noiseLevel_ = 0.0f;
    float noiseSmooth_ = 0.0f;      // one-pole smoother state for noise gate
    float oscMix_ = 0.5f;
    bool oscSync_ = false;
    float masterVolume_ = 0.8f;
    float pitchBend_ = 0.0f;
    float modWheel_ = 0.0f;

    // Active voice count for noise gating
    std::atomic<int> activeVoiceCount_{0};

    // Lazy-effect-reset flag (set when preset is loaded)
    std::atomic<bool> pendingEffectsReset_{false};

    // Oscilloscope circular buffer
    float scopeBuffer_[SCOPE_SIZE]{};
    int scopeWriteIndex_ = 0;

    // Polyphony
    int allocateVoice();
    void releaseVoice(int index);
    int findVoiceByNote(int midiNote);
    float generateNoise();

    void applyModulationMatrix();
    void swapParamsIfNeeded();

    // Lock-free note queue (SPSC: UI thread → audio thread)
    static constexpr int NOTE_QUEUE_SIZE = 64;
    std::array<NoteEvent, NOTE_QUEUE_SIZE> noteQueue_;
    std::atomic<int> noteQueueHead_{0};
    std::atomic<int> noteQueueTail_{0};

    void processNoteQueue();
    void handleNoteOn(int midiNote, int velocity);
    void handleNoteOff(int midiNote);
};

#endif // JUJISYNTH_AUDIOENGINE_H
