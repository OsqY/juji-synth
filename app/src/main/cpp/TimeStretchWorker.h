#ifndef JUJIDAW_TIME_STRETCH_WORKER_H
#define JUJIDAW_TIME_STRETCH_WORKER_H

#include <atomic>
#include <condition_variable>
#include <deque>
#include <mutex>
#include <thread>

class SamplerInstrument;

/**
 * Background worker that runs SoundTouch time-stretch off the audio and UI
 * threads. JNI enqueues jobs; the worker thread applies them and posts a
 * completion callback back to the JVM.
 */
class TimeStretchWorker {
public:
    TimeStretchWorker();
    ~TimeStretchWorker();

    // Start the worker thread. Safe to call multiple times.
    void start();

    // Stop and join the worker thread. Drops any pending jobs.
    void stop();

    struct Job {
        int padIndex = 0;
        double tempoChangePercent = 0.0;
        double pitchSemiTones = 0.0;
        double rateChangePercent = 0.0;
    };

    // Enqueue a job. The worker will copy the Job, so callers can free it
    // immediately. The sampler pointer must remain valid until the worker is
    // stopped.
    void enqueue(Job job);

    bool isBusy() const { return busy_.load(std::memory_order_acquire) > 0; }

    // Set the target sampler. Required.
    void setSampler(SamplerInstrument* sampler) { sampler_ = sampler; }

    // Set the JVM listener to be notified when a job completes. The listener
    // object is a global reference owned by the worker; pass a global ref
    // from JNI.
    using CompletionCallback = void (*)(void* env, void* userData,
                                        int padIndex, int success);
    void setCompletionCallback(CompletionCallback cb, void* userData) {
        completionCb_ = cb;
        completionUserData_ = userData;
    }

private:
    void loop();

    SamplerInstrument* sampler_ = nullptr;
    CompletionCallback completionCb_ = nullptr;
    void* completionUserData_ = nullptr;

    std::thread thread_;
    std::mutex mutex_;
    std::condition_variable cv_;
    std::deque<Job> queue_;
    std::atomic<bool> running_{false};
    std::atomic<int> busy_{0};
};

#endif // JUJIDAW_TIME_STRETCH_WORKER_H
