#include "Chorus.h"
#include <algorithm>
#include <cmath>

void Chorus::init(double sampleRate) {
    sampleRate_ = sampleRate;
    buffer_.fill(0.0f);
    writeIndex_ = 0;
    phase_ = 0.0;
}

void Chorus::setRate(double rate) {
    rate_ = 0.1 + 4.9 * rate * rate; // 0.1-5.0 Hz, exponential feel
}

void Chorus::setDepth(double depth) {
    depth_ = depth * depth * 20.0; // 0-20ms, quadratic feel
}

void Chorus::setMix(double mix) {
    mix_ = std::clamp(mix, 0.0, 1.0);
}

float Chorus::process(float input) {
    if (mix_ == 0.0f) return input;

    // Advance LFO phase
    phase_ += rate_ / sampleRate_;
    if (phase_ >= 1.0) phase_ -= 1.0;

    // Calculate modulated delay in samples
    double lfo = std::sin(2.0 * M_PI * phase_);
    double delayMs = depth_ * (1.0 + lfo) * 0.5; // 0 to depth_ ms
    double delaySamples = delayMs * sampleRate_ / 1000.0;
    delaySamples = std::clamp(delaySamples, 0.0, (double)(MAX_DELAY_SAMPLES - 1));

    // Write to delay buffer
    writeIndex_ = (writeIndex_ + 1) % MAX_DELAY_SAMPLES;
    buffer_[writeIndex_] = input;

    // Read from delay buffer with linear interpolation
    double readIndex = writeIndex_ - delaySamples;
    if (readIndex < 0) readIndex += MAX_DELAY_SAMPLES;

    int readIdx = (int)readIndex;
    double frac = readIndex - readIdx;
    int nextIdx = (readIdx + 1) % MAX_DELAY_SAMPLES;

    float delayed = buffer_[readIdx] * (1.0f - (float)frac) + buffer_[nextIdx] * (float)frac;

    // Dry/wet mix
    return input * (1.0f - (float)mix_) + delayed * (float)mix_;
}

void Chorus::reset() {
    buffer_.fill(0.0f);
    writeIndex_ = 0;
    phase_ = 0.0;
}
