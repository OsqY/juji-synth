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

double Filter::cutoffCoefficient(double normalizedCutoff) const {
    double fc = 20.0 + (20000.0 - 20.0) * normalizedCutoff * normalizedCutoff;
    fc = std::clamp(fc, 20.0, std::min(20000.0, sampleRate_ * 0.45));
    return std::tan(M_PI * fc / sampleRate_);
}

void Filter::recalcCoefficients() {
    targetF_ = cutoffCoefficient(cutoff_);
    effectiveF_ = targetF_;
    const double q = 0.5 + 19.5 * resonance_;
    damping_ = 1.0 / q;
}

void Filter::applyEnvelope(double envValue) {
    // Apply envelope modulation to effectiveCutoff
    double mod = envValue * envAmount_;
    double modulatedCutoff = std::clamp(cutoff_ + mod, 0.0, 1.0);

    effectiveF_ = cutoffCoefficient(modulatedCutoff);
}

float Filter::process(float input) {
    f_ += (effectiveF_ - f_) * 0.1;
    const double a1 = 1.0 / (1.0 + f_ * (f_ + damping_));
    const double a2 = f_ * a1;
    const double a3 = f_ * a2;
    const double v3 = input - ic2eq_;
    const double band = a1 * ic1eq_ + a2 * v3;
    const double low = ic2eq_ + a2 * ic1eq_ + a3 * v3;
    const double high = input - damping_ * band - low;
    ic1eq_ = 2.0 * band - ic1eq_;
    ic2eq_ = 2.0 * low - ic2eq_;

    switch (mode_) {
        case 0: return static_cast<float>(low);     // LPF
        case 1: return static_cast<float>(high);    // HPF
        case 2: return static_cast<float>(band);    // BPF
        default: return static_cast<float>(low);
    }
}

void Filter::reset() {
    ic1eq_ = 0.0;
    ic2eq_ = 0.0;
    effectiveCutoff_ = cutoff_;
    effectiveF_ = targetF_;
    f_ = targetF_;
}
