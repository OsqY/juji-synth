#ifndef JUJISYNTH_SYNTHENGINE_H
#define JUJISYNTH_SYNTHENGINE_H

#include "AudioEngine.h"
#include <oboe/Oboe.h>

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

    bool start();
    bool stop();
    bool isRunning() const { return isRunning_; }

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

    AudioEngine engine_;
    std::shared_ptr<oboe::AudioStream> stream_;
    bool isRunning_ = false;
};

#endif // JUJISYNTH_SYNTHENGINE_H
