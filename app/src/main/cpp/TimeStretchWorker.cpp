#include "TimeStretchWorker.h"
#include "SamplerInstrument.h"
#include "SampleBuffer.h"
#include <android/log.h>

#define LOG_TAG_TS "JujiDawTS"
#define LOGI_TS(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG_TS, __VA_ARGS__)
#define LOGE_TS(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG_TS, __VA_ARGS__)

TimeStretchWorker::TimeStretchWorker() = default;

TimeStretchWorker::~TimeStretchWorker() {
    stop();
}

void TimeStretchWorker::start() {
    if (running_.exchange(true)) return;
    thread_ = std::thread([this] { loop(); });
}

void TimeStretchWorker::stop() {
    if (!running_.exchange(false)) return;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        queue_.clear();
    }
    cv_.notify_all();
    if (thread_.joinable()) thread_.join();
}

void TimeStretchWorker::enqueue(Job job) {
    {
        std::lock_guard<std::mutex> lock(mutex_);
        queue_.push_back(job);
    }
    cv_.notify_one();
}

void TimeStretchWorker::loop() {
    LOGI_TS("TimeStretchWorker started");
    while (running_.load(std::memory_order_acquire)) {
        Job job;
        {
            std::unique_lock<std::mutex> lock(mutex_);
            cv_.wait(lock, [this] {
                return !queue_.empty() || !running_.load(std::memory_order_acquire);
            });
            if (!running_.load(std::memory_order_acquire) && queue_.empty()) break;
            if (queue_.empty()) continue;
            job = queue_.front();
            queue_.pop_front();
        }

        if (!sampler_) continue;
        busy_.fetch_add(1, std::memory_order_acq_rel);

        bool success = false;
        if (job.padIndex >= 0 && job.padIndex < NUM_PADS) {
            const auto& src = sampler_->getPad(job.padIndex).buffer;
            if (src && src->isLoaded()) {
                auto stretched = src->createTimeStretched(
                    job.tempoChangePercent, job.pitchSemiTones, job.rateChangePercent);
                if (stretched) {
                    sampler_->setPadBuffer(job.padIndex, std::move(stretched));
                    success = true;
                }
            }
        } else {
            LOGE_TS("Invalid pad index %d", job.padIndex);
        }

        busy_.fetch_sub(1, std::memory_order_acq_rel);

        if (completionCb_) {
            // Pass nullptr for env; the JNI callback layer will re-attach.
            completionCb_(nullptr, completionUserData_, job.padIndex, success ? 1 : 0);
        }
    }
    LOGI_TS("TimeStretchWorker stopped");
}
