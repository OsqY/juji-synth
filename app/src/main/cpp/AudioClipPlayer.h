#ifndef JUJIDAW_AUDIO_CLIP_PLAYER_H
#define JUJIDAW_AUDIO_CLIP_PLAYER_H

#include "Instrument.h"
#include "SampleBuffer.h"
#include <memory>
#include <atomic>

/**
 * In-memory audio clip player assigned to a mixer channel.
 *
 * Reads from a shared SampleBuffer starting at a configurable sample offset.
 * Supports fade-in/out and gain. Output is mono: stereo buffers are
 * down-mixed to mono.
 */
class AudioClipPlayer : public Instrument {
public:
    AudioClipPlayer();

    void init(double sampleRate) override;
    float process() override;
    void noteOn(int midiNote, int velocity) override { (void)midiNote; (void)velocity; }
    void noteOff(int midiNote) override { (void)midiNote; }
    void panic() override { stop(); }
    bool isActive() const override { return active_.load(std::memory_order_acquire); }

    void setBuffer(std::shared_ptr<SampleBuffer> buffer);
    void start(int64_t startOffsetInBuffer, int fadeInSamples = 0, int fadeOutSamples = 0);
    void stop();

    void setGain(float gain) { gain_ = gain; }

private:
    double sampleRate_ = 48000.0;
    std::shared_ptr<SampleBuffer> buffer_;
    std::atomic<bool> active_{false};
    double readPos_ = 0.0;
    float speed_ = 1.0f;
    int fadeInSamples_ = 0;
    int fadeOutSamples_ = 0;
    int samplesPlayed_ = 0;
    float gain_ = 1.0f;
};

#endif // JUJIDAW_AUDIO_CLIP_PLAYER_H
