#include "Filter.h"
#include <algorithm>
#include <cmath>

Filter::Filter() = default;

void Filter::init(double sampleRate) {
    sampleRate_ = sampleRate;
    reset();
    recalcCoefficients();
    effectiveF_ = targetF_;
    f_ = targetF_;
}

void Filter::setCutoff(double cutoff) {
    cutoff_ = std::clamp(cutoff, 0.0, 1.0);
    recalcCoefficients();
}

void Filter::setResonance(double res) {
    resonance_ = std::clamp(res, 0.0, 1.0);
    recalcCoefficients();
}

void Filter::setMode(int mode) {
    mode_ = std::clamp(mode, 0, 2);
}

void Filter::setEnvelopeAmount(double amount) {
    envAmount_ = std::clamp(amount, -1.0, 1.0);
}

void Filter::recalcCoefficients() {
    // Compute target frequency coefficient from cutoff
    // Map to Hz, then to SVF coefficient f = 2*sin(pi*fc/fs)
    double fc = 20.0 + (20000.0 - 20.0) * effectiveCutoff_ * effectiveCutoff_;
    fc = std::clamp(fc, 20.0, 20000.0);
    targetF_ = 2.0 * std::sin(M_PI * fc / sampleRate_);
    // Cap to prevent instability (was min(f_, 1.0), now 0.95)
    targetF_ = std::min(targetF_, 0.95);

    // Map resonance to Q (0.5 - 20.0)
    q_ = 0.5 + 19.5 * resonance_;
    // Self-oscillation at max resonance, but cap to prevent stutter
    if (resonance_ > 0.95) {
        q_ = std::min(20.0, 0.5 + 25.0 * (resonance_ - 0.95) / 0.05);
    }
}

void Filter::applyEnvelope(double envValue) {
    // Quick one-pole smooth of f_ toward target
    f_ += (effectiveF_ - f_) * 0.1;

    // Apply envelope modulation to effectiveCutoff
    double mod = envValue * envAmount_;
    double modulatedCutoff = std::clamp(cutoff_ + mod, 0.0, 1.0);

    // Recompute effectiveF_ from modulated cutoff
    double fc = 20.0 + (20000.0 - 20.0) * modulatedCutoff * modulatedCutoff;
    fc = std::clamp(fc, 20.0, 20000.0);
    effectiveF_ = 2.0 * std::sin(M_PI * fc / sampleRate_);
    effectiveF_ = std::min(effectiveF_, 0.95);
}

float Filter::process(float input) {
    if (f_ < 0.001) {
        f_ = 0.01;
        q_ = 0.5;
    }

    // SVF algorithm using current smoothed f_
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
    effectiveCutoff_ = cutoff_;
    effectiveF_ = targetF_;
    f_ = targetF_;
}
