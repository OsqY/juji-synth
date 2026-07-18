#ifndef JUJIDAW_SAMPLER_INSTRUMENT_H
#define JUJIDAW_SAMPLER_INSTRUMENT_H

#include "Instrument.h"
#include "SampleBuffer.h"
#include "SamplerVoice.h"
#include <array>
#include <memory>
#include <atomic>
#include <cstdint>

class SynthInstrument;
class AudioEngine;

constexpr int NUM_PADS = 32;     // 2 banks x 16 pads
constexpr int SAMPLER_POLYPHONY = 16;

// Configuration for one sampler pad.
struct PadConfig {
    std::shared_ptr<SampleBuffer> buffer;
    float pitch = 0.0f;          // semitones
    float pan = 0.0f;
    float volume = 1.0f;
    float attack = 0.0f;
    float release = 0.0f;
    float filterCutoff = 1.0f;
    float filterResonance = 0.0f;
    bool reverse = false;
    bool loop = false;
    bool oneShot = true;
    bool useFilter = false;
    std::atomic<bool> synthMode{false}; // if true, pad triggers synth instead
    int synthRootNote = 60;      // C4
};

class SamplerInstrument : public Instrument {
public:
    SamplerInstrument();

    void init(double sampleRate) override;
    float process() override;
    void noteOn(int midiNote, int velocity) override;
    void noteOff(int midiNote) override;
    void releasePad(int padIndex);
    void panic() override;
    bool isActive() const override;

    // Pad management
    PadConfig& getPad(int index);
    const PadConfig& getPad(int index) const;
    void setPadBuffer(int index, std::shared_ptr<SampleBuffer> buffer);
    void clearPad(int index);

    // Apply SoundTouch time-stretch / pitch shift to a pad's buffer in place.
    // Returns true if the operation succeeded.
    bool timeStretchPad(int padIndex, double tempoChangePercent,
                        double pitchSemiTones, double rateChangePercent);

    // Chop a buffer into equal slices assigned to sequential pads starting at startPad.
    void chopSample(int sourcePad, int startPad, int numSlices);

    // Global controls
    void setMasterVolume(float volume);
    void setActiveBank(int bank); // 0 or 1
    int getActiveBank() const { return activeBank_; }

    // Forward synth-pad triggers to the per-pad synth pool via AudioEngine.
    // nullptr means synth-pad mode falls back to no-op.
    void setAudioEngine(AudioEngine* engine) { audioEngine_ = engine; }

    // Trigger a pad directly (used by the transport scheduler).
    void triggerPad(int padIndex, int velocity);
    /** Trigger from the audio callback without writing to the UI SPSC queue. */
    void triggerPadFromAudioThread(int padIndex, int velocity);

private:
    double sampleRate_ = 48000.0;
    std::array<PadConfig, NUM_PADS> pads_;
    std::array<SamplerVoice, SAMPLER_POLYPHONY> voices_;
    float masterVolume_ = 1.0f;
    int activeBank_ = 0;
    std::atomic<int> activeVoiceCount_{0};
    AudioEngine* audioEngine_ = nullptr;  // for per-pad synth pool access
    uint64_t voiceAgeCounter_ = 0;

    int allocateVoice();
    int padIndexFromNote(int midiNote) const;
    void triggerPadInternal(int padIndex, int velocity, bool fromAudioThread);
};

#endif // JUJIDAW_SAMPLER_INSTRUMENT_H
