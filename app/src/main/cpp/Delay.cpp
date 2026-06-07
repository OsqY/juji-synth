#include "Delay.h"
#include <algorithm>
#include <cmath>

Delay::Delay() = default;

void Delay::init(double sampleRate) {
    sampleRate_ = sampleRate;
    buffer_.fill(0.0f);
    writeIndex_ = 0;
    delaySamples_ = static_cast<int>(delayTimeMs_ * sampleRate_ / 1000.0);
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
    if (mix_ == 0.0f) return input;

    // Read from delay line
    int readIndex = writeIndex_ - delaySamples_;
    if (readIndex < 0) readIndex += MAX_DELAY_SAMPLES;
    readIndex %= MAX_DELAY_SAMPLES;

    float delayed = buffer_[readIndex];

    // Write with feedback
    buffer_[writeIndex_] = input + delayed * static_cast<float>(feedback_);

    writeIndex_ = (writeIndex_ + 1) % MAX_DELAY_SAMPLES;

    lastOutput_ = delayed;

    // Dry/wet mix
    return input * (1.0f - static_cast<float>(mix_))
         + delayed * static_cast<float>(mix_);
}

void Delay::reset() {
    buffer_.fill(0.0f);
    writeIndex_ = 0;
    lastOutput_ = 0.0f;
}
