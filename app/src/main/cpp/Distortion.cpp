#include "Distortion.h"
#include <algorithm>
#include <cmath>

Distortion::Distortion() {
    driveSmoother_.reset(1.0f);
    driveSmoother_.setCoefficient(0.3f); // ~3ms at 44.1kHz
}

void Distortion::setDrive(double drive) {
    drive_ = std::clamp(drive, 0.0, 1.0);
    // Map drive to gain factor (1x to 20x)
    driveFactor_ = 1.0 + 19.0 * drive_ * drive_;
}

void Distortion::setMix(double mix) {
    mix_ = std::clamp(mix, 0.0, 1.0);
}

float Distortion::process(float input) {
    // Smooth the drive factor to avoid zipper noise
    float smoothDrive = driveSmoother_.process(static_cast<float>(driveFactor_));

    if (drive_ == 0.0 || mix_ == 0.0f) return input;

    // Waveshape with tanh for soft clipping
    float shaped = std::tanh(input * smoothDrive);

    // Normalize to maintain perceived level
    if (smoothDrive > 1.0f) {
        shaped /= std::tanh(smoothDrive);
    }

    // Dry/wet mix
    return input * (1.0f - static_cast<float>(mix_))
         + shaped * static_cast<float>(mix_);
}

void Distortion::reset() {
    // No state to reset
}
