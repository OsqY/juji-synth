#include "Distortion.h"
#include <algorithm>
#include <cmath>

Distortion::Distortion() = default;

void Distortion::setDrive(double drive) {
    drive_ = std::clamp(drive, 0.0, 1.0);
    // Map drive to gain factor (1x to 20x)
    driveFactor_ = 1.0 + 19.0 * drive_ * drive_;
}

void Distortion::setMix(double mix) {
    mix_ = std::clamp(mix, 0.0, 1.0);
}

float Distortion::process(float input) {
    if (drive_ == 0.0 || mix_ == 0.0f) return input;

    // Waveshape with tanh for soft clipping
    float shaped = std::tanh(input * static_cast<float>(driveFactor_));

    // Normalize to maintain perceived level
    if (driveFactor_ > 1.0f) {
        shaped /= std::tanh(static_cast<float>(driveFactor_));
    }

    // Dry/wet mix
    return input * (1.0f - static_cast<float>(mix_))
         + shaped * static_cast<float>(mix_);
}

void Distortion::reset() {
    // No state to reset
}
