#include "Delay.h"
#include <algorithm>
#include <cmath>

Delay::Delay() = default;

void Delay::init(double sampleRate) {
    sampleRate_ = sampleRate;
    buffer_.fill(0.0f);
    writeIndex_ = 0;
    delaySamples_ = static_cast<int>(delayTimeMs_ * sampleRate_ / 1000.0);
    smoothDelaySamples_ = static_cast<float>(delaySamples_);
    mixSmoother_.reset(0.0f);
    mixSmoother_.setCoefficient(0.1f); // ~10ms at 44.1kHz
}

void Delay::setMix(double mix) {
    mix_ = std::clamp(mix, 0.0, 1.0);
}

void Delay::setTime(double time) {
    delayTimeMs_ = std::clamp(time, 0.0, 1.0);
    // Map 0.0-1.0 to 20-2000ms with exponential curve
    delayTimeMs_ = 20.0 + 1980.0 * time * time;
    delaySamples_ = static_cast<int>(delayTimeMs_ * sampleRate_ / 1000.0);
    delaySamples_ = std::clamp(delaySamples_, 1, MAX_DELAY_SAMPLES - 1);
}

void Delay::setFeedback(double fb) {
    feedback_ = std::clamp(fb, 0.0, 1.0);
}

void Delay::setTempoSync(bool sync) {
    tempoSync_ = sync;
}

float Delay::process(float input) {
    // Smooth the mix parameter to avoid zipper noise
    float smoothMix = mixSmoother_.process(static_cast<float>(mix_));

    // Smooth the delay time read position with linear interpolation
    smoothDelaySamples_ += (static_cast<float>(delaySamples_) - smoothDelaySamples_) * 0.1f;
    if (smoothDelaySamples_ < 1.0f) smoothDelaySamples_ = 1.0f;

    if (smoothMix == 0.0f) return input;

    // Read from delay line with linear interpolation
    float readIndexFloat = static_cast<float>(writeIndex_) - smoothDelaySamples_;
    if (readIndexFloat < 0) readIndexFloat += MAX_DELAY_SAMPLES;
    // Wrap modulo
    int readIdx = static_cast<int>(readIndexFloat) % MAX_DELAY_SAMPLES;
    float frac = readIndexFloat - static_cast<float>(static_cast<int>(readIndexFloat));
    int nextIdx = (readIdx + 1) % MAX_DELAY_SAMPLES;

    float delayed = buffer_[readIdx] * (1.0f - frac) + buffer_[nextIdx] * frac;

    // Write with feedback
    buffer_[writeIndex_] = input + delayed * static_cast<float>(feedback_);

    writeIndex_ = (writeIndex_ + 1) % MAX_DELAY_SAMPLES;

    lastOutput_ = delayed;

    // Dry/wet mix with smoothed mix
    return input * (1.0f - smoothMix) + delayed * smoothMix;
}

void Delay::reset() {
    buffer_.fill(0.0f);
    writeIndex_ = 0;
    smoothDelaySamples_ = 0.0f;
    lastOutput_ = 0.0f;
    mixSmoother_.reset(0.0f);
}
