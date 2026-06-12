#include "Sequencer.h"
#include <algorithm>

Sequencer::Sequencer() {
    // Initialize all steps as rests
    for (auto& step : steps_) {
        step.note = -1;
        step.velocity = 100;
        step.gate = 0.8f;
        step.automation = 0.0f;
    }
}

void Sequencer::init(double sampleRate) {
    sampleRate_ = sampleRate;
    samplesPerTick_ = (60.0 / tempo_) * sampleRate_ / 4.0;
}

void Sequencer::setSteps(const std::array<SequencerStep, SEQUENCER_STEPS>& steps) {
    steps_ = steps;
}

void Sequencer::setStep(int index, const SequencerStep& step) {
    if (index >= 0 && index < SEQUENCER_STEPS) {
        steps_[index] = step;
    }
}

void Sequencer::start() {
    playing_ = true;
    currentStep_ = 0;
    tickCounter_ = 0.0;
    lastTriggerState_ = false;
}

void Sequencer::stop() {
    playing_ = false;
    // Release held note
    if (lastNote_ >= 0 && onNoteEvent) {
        onNoteEvent(lastNote_, 0, false);
        lastNote_ = -1;
    }
}

void Sequencer::reset() {
    currentStep_ = 0;
    tickCounter_ = 0.0;
    if (lastNote_ >= 0 && onNoteEvent) {
        onNoteEvent(lastNote_, 0, false);
        lastNote_ = -1;
    }
}

void Sequencer::setTempo(double bpm) {
    tempo_ = std::clamp(bpm, 30.0, 300.0);
    samplesPerTick_ = (60.0 / tempo_) * sampleRate_ / 4.0;
}

void Sequencer::setPlaying(bool playing) {
    if (playing && !playing_) start();
    else if (!playing && playing_) stop();
}

Sequencer::StepEvent Sequencer::process(int numSamples) {
    StepEvent event;
    event.triggerNote = false;

    if (!playing_) return event;

    tickCounter_ += numSamples;

    while (tickCounter_ >= samplesPerTick_) {
        tickCounter_ -= samplesPerTick_;

        // Release previous note
        if (lastNote_ >= 0 && onNoteEvent) {
            onNoteEvent(lastNote_, 0, false);
            lastNote_ = -1;
        }

        // Advance to next step
        currentStep_++;
        if (currentStep_ >= SEQUENCER_STEPS) {
            if (looping_) {
                currentStep_ = 0;
            } else {
                // Non-looping: stop at end of sequence
                currentStep_ = SEQUENCER_STEPS - 1;
                playing_ = false;
                tickCounter_ = 0.0;
                return event;
            }
        }
        const auto& step = steps_[currentStep_];

        if (step.note >= 0) {
            event.triggerNote = true;
            event.note = step.note;
            event.velocity = step.velocity;
            event.gate = step.gate;
            event.automation = step.automation;

            if (onNoteEvent) {
                onNoteEvent(step.note, step.velocity, true);
                lastNote_ = step.note;
            }
        }
    }

    return event;
}
