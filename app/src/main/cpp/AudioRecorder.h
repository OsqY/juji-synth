#ifndef JUJIDAW_AUDIO_RECORDER_H
#define JUJIDAW_AUDIO_RECORDER_H

#include <oboe/Oboe.h>
#include <vector>
#include <atomic>
#include <memory>
#include <mutex>

class SampleBuffer;

/**
 * Audio input recorder using Oboe.
 *
 * Captures mono PCM float samples at the engine's sample rate and stores them
 * in an internal ring buffer. On stop, the captured data is converted to a
 * shared_ptr<SampleBuffer> ready to be assigned to a sampler pad.
 *
 * Not thread-safe to start while a previous recording is in progress; check
 * isRecording() first.
 */
class AudioRecorder : public oboe::AudioStreamCallback {
public:
    AudioRecorder();
    ~AudioRecorder() override;

    /** Begin recording. Returns true on success. */
    bool start(int sampleRate);
    /** Stop recording and freeze the captured buffer. */
    void stop();
    /** Discard captured audio without producing a buffer. */
    void clear();

    bool isRecording() const { return recording_.load(std::memory_order_acquire); }

    /**
     * Enable/disable punch-in recording. When enabled, only samples captured
     * within [punchInSample, punchOutSample) are stored; samples outside the
     * range are monitored (passed through to the live path) but not recorded.
     */
    void setPunchEnabled(bool enabled) { punchEnabled_.store(enabled, std::memory_order_release); }
    void setPunchRange(int64_t inSample, int64_t outSample);

    /**
     * Write the current recorded buffer to a 16-bit PCM WAV file.
     * Returns true on success.
     */
    bool writeToWav(const std::string& path);

    /**
     * Take ownership of the most recent captured buffer.
     * Returns nullptr if no buffer is available.
     */
    std::shared_ptr<SampleBuffer> takeBuffer();

    // oboe::AudioStreamCallback
    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream* stream,
        void* audioData,
        int32_t numFrames) override;
    void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) override;

private:
    std::shared_ptr<oboe::AudioStream> stream_;

    std::mutex bufferMutex_;
    std::vector<float> samples_; // interleaved float frames
    int sampleRate_ = 48000;
    int channels_ = 1;

    std::atomic<bool> recording_{false};
    std::atomic<bool> stopRequested_{false};
    std::atomic<bool> punchEnabled_{false};
    std::atomic<int64_t> punchInSample_{0};
    std::atomic<int64_t> punchOutSample_{0};
    std::atomic<int64_t> samplesRecorded_{0};
};

#endif // JUJIDAW_AUDIO_RECORDER_H
