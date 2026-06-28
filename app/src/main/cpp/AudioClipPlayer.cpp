#include "AudioClipPlayer.h"
#include <algorithm>
#include <cmath>

AudioClipPlayer::AudioClipPlayer() = default;

void AudioClipPlayer::init(double sampleRate) {
    sampleRate_ = sampleRate;
}

void AudioClipPlayer::setBuffer(std::shared_ptr<SampleBuffer> buffer) {
    buffer_ = std::move(buffer);
}

void AudioClipPlayer::start(int64_t startOffsetInBuffer, int fadeInSamples, int fadeOutSamples) {
    if (!buffer_ || !buffer_->isLoaded()) return;
    readPos_ = startOffsetInBuffer;
    if (readPos_ < 0) readPos_ = 0;
    fadeInSamples_ = std::max(0, fadeInSamples);
    fadeOutSamples_ = std::max(0, fadeOutSamples);
    samplesPlayed_ = 0;
    active_.store(true, std::memory_order_release);
}

void AudioClipPlayer::stop() {
    active_.store(false, std::memory_order_release);
}

float AudioClipPlayer::process() {
    if (!active_.load(std::memory_order_acquire) || !buffer_) return 0.0f;

    int frames = buffer_->getNumFrames();
    int channels = buffer_->getChannels();
    if (readPos_ >= frames) {
        active_.store(false, std::memory_order_release);
        return 0.0f;
    }

    float sample = 0.0f;
    if (channels == 1) {
        sample = buffer_->getSample(static_cast<int>(readPos_), 0);
    } else {
        // Down-mix stereo to mono
        float left = buffer_->getSample(static_cast<int>(readPos_), 0);
        float right = buffer_->getSample(static_cast<int>(readPos_), 1);
        sample = (left + right) * 0.5f;
    }

    // Apply fade-in / fade-out ramps
    int remaining = frames - static_cast<int>(readPos_) - 1;
    if (fadeInSamples_ > 0 && samplesPlayed_ < fadeInSamples_) {
        sample *= static_cast<float>(samplesPlayed_) / static_cast<float>(fadeInSamples_);
    }
    if (fadeOutSamples_ > 0 && remaining <= fadeOutSamples_) {
        sample *= static_cast<float>(remaining) / static_cast<float>(fadeOutSamples_);
    }

    sample *= gain_;
    readPos_++;
    samplesPlayed_++;

    if (readPos_ >= frames) {
        active_.store(false, std::memory_order_release);
    }

    return sample;
}
