#include "LFO.h"
#include <algorithm>
#include <cmath>

LFO::LFO() = default;

void LFO::init(double sampleRate) {
    sampleRate_ = sampleRate;
    phase_ = 0.0;
    phaseIncrement_ = mapRate(rate_) / sampleRate_;
}

void LFO::setRate(double rate) {
    rate_ = std::clamp(rate, 0.0, 1.0);
    phaseIncrement_ = mapRate(rate_) / sampleRate_;
}

void LFO::setDepth(double depth) {
    depth_ = std::clamp(depth, 0.0, 1.0);
}

void LFO::setWaveform(int type) {
    waveformType_ = std::clamp(type, 0, 4);
}

void LFO::setKeySync(bool sync) {
    keySync_ = sync;
}

void LFO::reset() {
    phase_ = 0.0;
    currentRandom_ = randDist_(rng_);
}

float LFO::process() {
    phase_ += phaseIncrement_;
    if (phase_ >= 1.0) {
        phase_ -= 1.0;
        if (waveformType_ == 4) { // random: new value each cycle
            currentRandom_ = randDist_(rng_);
        }
    }

    double phase = phase_;

    switch (waveformType_) {
        case 0: // Sine
            currentValue_ = std::sin(2.0 * M_PI * phase);
            break;
        case 1: // Square
            currentValue_ = (phase < 0.5) ? 1.0 : -1.0;
            break;
        case 2: // Saw
            currentValue_ = 2.0 * phase - 1.0;
            break;
        case 3: // Triangle
            currentValue_ = (phase < 0.5)
                ? 4.0 * phase - 1.0
                : 3.0 - 4.0 * phase;
            break;
        case 4: // Random (S&H)
            currentValue_ = currentRandom_;
            break;
        default:
            currentValue_ = 0.0;
    }

    return static_cast<float>(currentValue_ * depth_);
}

float LFO::getModulationValue() {
    return static_cast<float>(currentValue_ * depth_);
}

double LFO::mapRate(double rate01) const {
    // Map 0.0-1.0 to 0.01-50 Hz with exponential feel
    if (rate01 <= 0.0) return 0.01;
    return 0.01 + 49.99 * rate01 * rate01;
}
