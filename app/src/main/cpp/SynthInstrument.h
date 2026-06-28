#ifndef JUJIDAW_SYNTH_INSTRUMENT_H
#define JUJIDAW_SYNTH_INSTRUMENT_H

#include "Instrument.h"
#include "SynthParams.h"
#include "SynthVoice.h"
#include "LFO.h"
#include "ModulationMatrix.h"
#include "Reverb.h"
#include "Delay.h"
#include "Distortion.h"
#include "Chorus.h"
#include "Sequencer.h"
#include <array>
#include <atomic>
#include <cstdint>

/**
 * Subtractive synthesizer instrument.
 *
 * Self-contained 4-voice polyphonic synth with:
 *   - Two oscillators + sub + noise, classic ADSR envelopes
 *   - Multi-mode resonant filter
 *   - Two LFOs and a modulation matrix (8 routes, 6 sources, 5 destinations)
 *   - Insert effect chain: distortion -> chorus -> delay -> reverb
 *   - Internal 16-step sequencer that fires note events back into the synth
 *   - Lock-free SPSC note queue for UI-thread note submissions
 *
 * Extracted from the legacy AudioEngine so the synth can be hosted on any
 * mixer channel alongside other instruments (sampler, future samplers/synths).
 */

/**
 * Per-block automation offsets for the synth. Each non-NaN field is applied
 * additively to the corresponding parameter for the next audio block. NaN
 * fields are ignored (i.e. "no automation for this parameter").
 *
 * Set once per audio buffer from the host (UI thread or timeline scheduler)
 * via `SynthInstrument::applyBlockAutomation`. Offsets are read on the audio
 * thread without locks.
 */
struct SynthAutomation {
    // Oscillators
    float osc1Level = std::nanf("");
    float osc2Level = std::nanf("");
    float osc1Detune = std::nanf("");
    float subOscLevel = std::nanf("");
    float noiseLevel = std::nanf("");
    float oscMix = std::nanf("");

    // Filter
    float filterCutoff = std::nanf("");
    float filterResonance = std::nanf("");
    float filterMode = std::nanf("");
    float filterEnvAmount = std::nanf("");

    // Envelopes
    float ampAttack = std::nanf("");
    float ampDecay = std::nanf("");
    float ampSustain = std::nanf("");
    float ampRelease = std::nanf("");
    float filterAttack = std::nanf("");
    float filterDecay = std::nanf("");
    float filterSustain = std::nanf("");
    float filterRelease = std::nanf("");

    // LFOs
    float lfo1Rate = std::nanf("");
    float lfo1Depth = std::nanf("");
    float lfo2Rate = std::nanf("");
    float lfo2Depth = std::nanf("");

    // Effects
    float reverbMix = std::nanf("");
    float delayMix = std::nanf("");
    float distortionDrive = std::nanf("");
    float chorusMix = std::nanf("");

    // Master
    float masterVolume = std::nanf("");
    float pitchBend = std::nanf("");
    float modWheel = std::nanf("");

    // Block length in samples the offsets apply over. Used for ramp / fade.
    int numFrames = 0;
};

class SynthInstrument : public Instrument {
public:
    SynthInstrument();
    ~SynthInstrument() override = default;

    // ---- Instrument interface ----
    void init(double sampleRate) override;
    float process() override;
    void noteOn(int midiNote, int velocity) override;
    void noteOff(int midiNote) override;
    void panic() override;
    bool isActive() const override;

    // ---- Bulk / parameter setters (UI thread; atomic swap on audio thread) ----
    void setParams(const SynthParams& params);
    void setAllParamsFromArray(const float* values, int count);
    static constexpr int SYNTH_PARAM_COUNT = 39;

    // Oscillators
    void setOsc1Level(float v);
    void setOsc2Level(float v);
    void setOsc1Waveform(int w);
    void setOsc2Waveform(int w);
    void setOscDetune(float v);
    void setSubOscLevel(float v);
    void setNoiseLevel(float v);
    void setOscMix(float v);
    void setOscSync(bool s);

    // Filter
    void setFilterCutoff(float v);
    void setFilterResonance(float v);
    void setFilterMode(int m);
    void setFilterEnvAmount(float v);

    // Envelopes
    void setAmpAttack(float v);
    void setAmpDecay(float v);
    void setAmpSustain(float v);
    void setAmpRelease(float v);
    void setFilterAttack(float v);
    void setFilterDecay(float v);
    void setFilterSustain(float v);
    void setFilterRelease(float v);

    // LFOs
    void setLfo1Rate(float v);
    void setLfo1Depth(float v);
    void setLfo1Waveform(int w);
    void setLfo2Rate(float v);
    void setLfo2Depth(float v);
    void setLfo2Waveform(int w);

    // Effects
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

    // Modulation
    void setModulationRoute(int index, const ModulationRoute& route);

    // Sequencer
    void setSequencerSteps(const std::array<SequencerStep, SEQUENCER_STEPS>& steps);
    void setSequencerTempo(float bpm);
    void setSequencerPlaying(bool play);
    void setSequencerLooping(bool loop) { sequencer_.setLooping(loop); }
    bool getSequencerLooping() const { return sequencer_.isLooping(); }
    int getSequencerStep() const { return sequencer_.getCurrentStep(); }

    // Master
    void setMasterVolume(float v);
    void setPitchBend(float v);
    void setModWheel(float v);

    // Snapshot for UI state sync.
    SynthParams getCurrentParams() const { return currentParams_; }

    // Disable the internal step sequencer when the DAW transport drives notes.
    void setSequencerEnabled(bool enabled) { sequencer_.setEnabled(enabled); }

    // Set a flag to clear effect buffers on the next audio thread tick.
    void setPendingEffectsReset() { pendingEffectsReset_.store(true, std::memory_order_release); }

    // Apply a per-block automation snapshot. The snapshot is copied into the
    // instrument; the caller may free it immediately. NaN fields are ignored.
    // The audio thread reads the snapshot in `process()` and applies offsets
    // additively to the corresponding params for the next numFrames samples.
    void applyBlockAutomation(const SynthAutomation& snapshot);

    // Called once per audio buffer from the host to consume the latest
    // automation snapshot and propagate it to the live DSP graph. Idempotent
    // and lock-free with respect to `applyBlockAutomation`.
    void processBlockAutomation();

    // Called once per audio buffer from the host to swap pending param state
    // and apply it to the live DSP graph.
    void swapParamsIfNeeded();

    // Voice bookkeeping: called from the host's audio thread to keep
    // `voiceActive_[]` in sync with each voice's internal active state.
    void syncVoiceActiveStates();

private:
    static constexpr int MAX_VOICES = 4;
    static constexpr int NOTE_QUEUE_SIZE = 64;

    struct NoteEvent {
        enum Type : uint8_t { NoteOn, NoteOff } type;
        int note = 0;
        int velocity = 0;
    };

    double sampleRate_ = 44100.0;

    // Param snapshot (double-buffered, atomic swap).
    SynthParams currentParams_;
    SynthParams pendingParams_;
    std::atomic<bool> paramsPending_{false};
    std::atomic<bool> pendingEffectsReset_{false};

    // Per-block automation offsets (read by process(), written by host).
    // Uses a sequence lock pattern: writer increments a generation counter
    // before/after a memcpy so the audio thread can read consistently.
    mutable std::atomic<uint32_t> automationGen_{0};
    SynthAutomation blockAutomation_{};
    std::atomic<bool> automationPending_{false};

    // Voices
    std::array<SynthVoice, MAX_VOICES> voices_;
    bool voiceActive_[MAX_VOICES] = {false, false, false, false};
    std::atomic<int> activeVoiceCount_{0};

    // Modulation
    LFO lfo1_;
    LFO lfo2_;
    LFO modLfo_;
    ModulationMatrix modMatrix_;

    // Effects
    Reverb reverb_;
    Delay delay_;
    Distortion distortion_;
    Chorus chorus_;

    // Sequencer
    Sequencer sequencer_;

    // Internal scalar copies of recent param values for the per-sample path.
    float subOscLevel_ = 0.0f;
    float noiseLevel_ = 0.0f;
    float noiseSmooth_ = 0.0f;
    float oscMix_ = 0.5f;
    bool oscSync_ = false;
    float masterVolume_ = 0.8f;
    float pitchBend_ = 0.0f;
    float modWheel_ = 0.0f;

    // SPSC note queue (UI thread -> audio thread)
    std::array<NoteEvent, NOTE_QUEUE_SIZE> noteQueue_;
    std::atomic<int> noteQueueHead_{0};
    std::atomic<int> noteQueueTail_{0};

    int allocateVoice();
    int findVoiceByNote(int midiNote);
    float generateNoise();
    void applyModulationMatrix();
    float processSynthSample();
    void processNoteQueue();
    void handleNoteOn(int midiNote, int velocity);
    void handleNoteOff(int midiNote);
};

#endif // JUJIDAW_SYNTH_INSTRUMENT_H
