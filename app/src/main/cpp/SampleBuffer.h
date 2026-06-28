#ifndef JUJIDAW_SAMPLE_BUFFER_H
#define JUJIDAW_SAMPLE_BUFFER_H

#include <vector>
#include <cstdint>
#include <memory>

// Holds decoded PCM audio data for sampler playback.
// Samples are stored as interleaved float frames (-1..1).
class SampleBuffer {
public:
    SampleBuffer() = default;
    SampleBuffer(std::vector<float> samples, int sampleRate, int channels);

    bool loadFromWav(const char* path);

    bool isLoaded() const { return !samples_.empty(); }
    int getSampleRate() const { return sampleRate_; }
    int getChannels() const { return channels_; }
    int getNumFrames() const;

    // Read a frame (one sample per channel) at integer position.
    // Returns zero if out of bounds.
    float getSample(int frame, int channel) const;

    // Read with linear interpolation for fractional positions.
    float getSampleInterpolated(float frame, int channel) const;

    const std::vector<float>& getSamples() const { return samples_; }

    // Offline time-stretch using SoundTouch. Values are relative changes:
    // tempoChangePercent: -50.0 .. +100.0 (0 = no change)
    // pitchSemiTones: -12.0 .. +12.0 (0 = no change)
    // rateChangePercent: -50.0 .. +100.0 (0 = no change)
    std::shared_ptr<SampleBuffer> createTimeStretched(
        double tempoChangePercent,
        double pitchSemiTones,
        double rateChangePercent) const;

private:
    std::vector<float> samples_;
    int sampleRate_ = 44100;
    int channels_ = 1;
};

#endif // JUJIDAW_SAMPLE_BUFFER_H
