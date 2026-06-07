#include "Filter.h"
#include <algorithm>
#include <cmath>

Filter::Filter() = default;

void Filter::init(double sampleRate) {
    sampleRate_ = sampleRate;
    reset();
}

void Filter::setCutoff(double cutoff) {
    cutoff_ = std::clamp(cutoff, 0.0, 1.0);
    effectiveCutoff_ = cutoff_;
}

void Filter::setResonance(double res) {
    resonance_ = std::clamp(res, 0.0, 1.0);
}

void Filter::setMode(int mode) {
    mode_ = std::clamp(mode, 0, 2);
}

void Filter::setEnvelopeAmount(double amount) {
    envAmount_ = std::clamp(amount, -1.0, 1.0);
}

void Filter::applyEnvelope(double envValue) {
    double mod = envValue * envAmount_;
    effectiveCutoff_ = std::clamp(cutoff_ + mod, 0.0, 1.0);

    // Map effective cutoff to frequency parameter (Hz)
    double fc = 20.0 + (20000.0 - 20.0) * effectiveCutoff_ * effectiveCutoff_;
    // Clamp to avoid numerical issues
    fc = std::clamp(fc, 20.0, 20000.0);
    f_ = 2.0 * std::sin(M_PI * fc / sampleRate_);
    // Limit f to prevent instability
    f_ = std::min(f_, 1.0);

    // Map resonance to Q (0.5 - 20)
    q_ = 0.5 + 19.5 * resonance_;
    // Self-oscillation at max resonance
    if (resonance_ > 0.95) {
        q_ = 0.5 + 25.0 * (resonance_ - 0.95) / 0.05;
    }
}

float Filter::process(float input) {
    if (!f_) {
        f_ = 0.01;
        q_ = 0.5;
    }

    // SVF algorithm
    high_ = input - low_ - q_ * band_;
    band_ = band_ + f_ * high_;
    low_ = low_ + f_ * band_;
    notch_ = high_ + low_;

    switch (mode_) {
        case 0: return static_cast<float>(low_);     // LPF
        case 1: return static_cast<float>(high_);    // HPF
        case 2: return static_cast<float>(band_);    // BPF
        default: return static_cast<float>(low_);
    }
}

void Filter::reset() {
    low_ = 0.0;
    high_ = 0.0;
    band_ = 0.0;
    notch_ = 0.0;
}
