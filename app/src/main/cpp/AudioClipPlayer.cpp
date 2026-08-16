#include "AudioClipPlayer.h"
#include <algorithm>
#include <cmath>

AudioClipPlayer::AudioClipPlayer() = default;

void AudioClipPlayer::init(double sampleRate) {
    sampleRate_ = sampleRate;
}

void AudioClipPlayer::setBuffer(std::shared_ptr<SampleBuffer> buffer) {
    std::atomic_store_explicit(&pendingBuffer_, std::move(buffer), std::memory_order_release);
}

void AudioClipPlayer::start(int64_t startOffsetInBuffer, int fadeInSamples, int fadeOutSamples) {
    stopPending_.store(false, std::memory_order_relaxed);
    pendingStartOffset_.store(startOffsetInBuffer, std::memory_order_relaxed);
    pendingFadeInSamples_.store(fadeInSamples, std::memory_order_relaxed);
    pendingFadeOutSamples_.store(fadeOutSamples, std::memory_order_relaxed);
    startPending_.store(true, std::memory_order_release);
}

void AudioClipPlayer::stop() {
    startPending_.store(false, std::memory_order_release);
    stopPending_.store(true, std::memory_order_release);
    active_.store(false, std::memory_order_release);
}

float AudioClipPlayer::process() {
    if (startPending_.exchange(false, std::memory_order_acquire)) {
        buffer_ = std::atomic_load_explicit(&pendingBuffer_, std::memory_order_acquire);
        if (!buffer_ || !buffer_->isLoaded()) {
            active_.store(false, std::memory_order_release);
            return 0.0f;
        }
        readPos_ = std::max(0.0, static_cast<double>(pendingStartOffset_.load(std::memory_order_relaxed)));
        speed_ = buffer_->getSampleRate() > 0 && sampleRate_ > 0.0
            ? static_cast<float>(buffer_->getSampleRate()) / static_cast<float>(sampleRate_)
            : 1.0f;
        fadeInSamples_ = std::max(0, pendingFadeInSamples_.load(std::memory_order_relaxed));
        fadeOutSamples_ = std::max(0, pendingFadeOutSamples_.load(std::memory_order_relaxed));
        samplesPlayed_ = 0;
        active_.store(true, std::memory_order_release);
    }
    if (stopPending_.exchange(false, std::memory_order_acquire)) {
        active_.store(false, std::memory_order_release);
    }
    if (!active_.load(std::memory_order_acquire) || !buffer_) return 0.0f;

    int frames = buffer_->getNumFrames();
    int channels = buffer_->getChannels();
    if (readPos_ >= frames) {
        active_.store(false, std::memory_order_release);
        return 0.0f;
    }

    int readIdx = static_cast<int>(readPos_);
    float sample = 0.0f;
    if (channels == 1) {
        sample = buffer_->getSampleInterpolated(static_cast<float>(readPos_), 0);
    } else {
        // Down-mix stereo to mono, using linear interpolation for pitch accuracy
        float left = buffer_->getSampleInterpolated(static_cast<float>(readPos_), 0);
        float right = buffer_->getSampleInterpolated(static_cast<float>(readPos_), 1);
        sample = (left + right) * 0.5f;
    }

    // Apply fade-in / fade-out ramps
    int remaining = frames - readIdx - 1;
    if (fadeInSamples_ > 0 && samplesPlayed_ < fadeInSamples_) {
        sample *= static_cast<float>(samplesPlayed_) / static_cast<float>(fadeInSamples_);
    }
    if (fadeOutSamples_ > 0 && remaining <= fadeOutSamples_) {
        sample *= static_cast<float>(remaining) / static_cast<float>(fadeOutSamples_);
    }

    sample *= gain_.load(std::memory_order_acquire);
    readPos_ += speed_;
    samplesPlayed_++;

    if (readIdx >= frames) {
        active_.store(false, std::memory_order_release);
    }

    return sample;
}
