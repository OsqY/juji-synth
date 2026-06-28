#ifndef JUJISYNTH_SYNTHENGINE_H
#define JUJISYNTH_SYNTHENGINE_H

#include "AudioEngine.h"
#include <oboe/Oboe.h>
#include <string>

/**
 * Result of an audio stream startup attempt.
 */
struct StartResult {
    bool ok = false;
    std::string message;
    int sampleRate = 0;
    int framesPerBurst = 0;
};

/**
 * SynthEngine wraps AudioEngine with Oboe audio stream management.
 *
 * - Creates and manages the Oboe audio callback
 * - Handles stream startup/teardown
 * - Provides JNI-friendly interface
 * - Singleton instance for C++ side
 */
class SynthEngine : public oboe::AudioStreamCallback {
public:
    static SynthEngine& getInstance();

    StartResult start();
    bool stop();
    bool isRunning() const { return isRunning_; }

    const std::string& getLastError() const { return lastError_; }
    int getSampleRate() const { return sampleRate_; }
    int getFramesPerBurst() const { return framesPerBurst_; }

    // AudioStreamCallback interface
    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream* stream,
        void* audioData,
        int32_t numFrames) override;

    void onErrorAfterClose(
        oboe::AudioStream* stream,
        oboe::Result error) override;

    AudioEngine& getAudioEngine() { return engine_; }

private:
    SynthEngine();
    ~SynthEngine() = default;

    // Prevent copy
    SynthEngine(const SynthEngine&) = delete;
    SynthEngine& operator=(const SynthEngine&) = delete;

    StartResult tryOpenStream(bool lowLatency, bool exclusive);

    AudioEngine engine_;
    std::shared_ptr<oboe::AudioStream> stream_;
    bool isRunning_ = false;
    std::string lastError_;
    int sampleRate_ = 0;
    int framesPerBurst_ = 0;
};

#endif // JUJISYNTH_SYNTHENGINE_H
