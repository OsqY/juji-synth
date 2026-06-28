#ifndef JUJISYNTH_SEQUENCER_H
#define JUJISYNTH_SEQUENCER_H

#include "SynthParams.h"
#include <functional>

/**
 * 16-step sequencer with per-step note, velocity, gate, and automation.
 * 
 * Drives note events and parameter automation for the synth.
 */
class Sequencer {
public:
    Sequencer();
    ~Sequencer() = default;

    void init(double sampleRate);

    /** Set all steps from external data */
    void setSteps(const std::array<SequencerStep, SEQUENCER_STEPS>& steps);

    /** Set single step */
    void setStep(int index, const SequencerStep& step);

    /** Start/stop/reset */
    void start();
    void stop();
    void reset();

    void setTempo(double bpm);         // 30-300
    void setPlaying(bool playing);
    void setLooping(bool loop) { looping_ = loop; }
    bool isLooping() const { return looping_; }
    bool isPlaying() const { return playing_; }

    // When disabled, process() is a no-op. Used when the DAW transport is
    // driving notes from Kotlin instead of the internal step sequencer.
    void setEnabled(bool enabled) { enabled_ = enabled; }
    bool isEnabled() const { return enabled_; }

    /** Process a block of samples, returns current step events */
    struct StepEvent {
        bool triggerNote = false;
        int note = -1;
        int velocity = 0;
        float automation = 0.0f;
        float gate = 0.0f;
    };

    StepEvent process(int numSamples);

    /** Get current step index (0-15) */
    int getCurrentStep() const { return currentStep_; }

    /** Callback for note events */
    std::function<void(int note, int velocity, bool noteOn)> onNoteEvent;

private:
    double sampleRate_ = 44100.0;
    std::array<SequencerStep, SEQUENCER_STEPS> steps_{};
    bool playing_ = false;
    bool looping_ = true;
    int currentStep_ = 0;
    double tempo_ = 120.0;

    double samplesPerTick_ = 0.0;
    double tickCounter_ = 0.0;
    bool lastTriggerState_ = false;
    int lastNote_ = -1;
    bool enabled_ = true;
};

#endif // JUJISYNTH_SEQUENCER_H
