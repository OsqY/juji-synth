#include "Envelope.h"
#include <algorithm>
#include <cmath>

Envelope::Envelope() = default;

void Envelope::init(double sampleRate) {
    sampleRate_ = sampleRate;
    reset();
}

void Envelope::setAttack(double time) {
    attackTime_ = mapTime(time, 10000.0);
    attackRate_ = (attackTime_ > 0.0) ? (1.0 / (attackTime_ * sampleRate_)) : 1.0;
}

void Envelope::setDecay(double time) {
    decayTime_ = mapTime(time, 10000.0);
    decayRate_ = (decayTime_ > 0.0)
        ? ((1.0 - sustainLevel_) / (decayTime_ * sampleRate_))
        : (1.0 - sustainLevel_);
}

void Envelope::setSustain(double level) {
    sustainLevel_ = std::clamp(level, 0.0, 1.0);
    // Recalculate decay rate since sustain changed
    if (decayTime_ > 0.0) {
        decayRate_ = (1.0 - sustainLevel_) / (decayTime_ * sampleRate_);
    }
}

void Envelope::setRelease(double time) {
    releaseTime_ = mapTime(time, 10000.0);
    releaseRate_ = (releaseTime_ > 0.0)
        ? (level_ / (releaseTime_ * sampleRate_))
        : level_;
}

void Envelope::noteOn() {
    stage_ = Stage::Attack;
    attackRate_ = (attackTime_ > 0.0) ? (1.0 / (attackTime_ * sampleRate_)) : 1.0;
}

void Envelope::noteOff() {
    if (stage_ != Stage::Idle) {
        stage_ = Stage::Release;
        releaseRate_ = (releaseTime_ > 0.0)
            ? (level_ / (releaseTime_ * sampleRate_))
            : level_;
    }
}

float Envelope::process() {
    switch (stage_) {
        case Stage::Idle:
            level_ = 0.0;
            break;

        case Stage::Attack:
            level_ += attackRate_;
            if (level_ >= 1.0) {
                level_ = 1.0;
                stage_ = Stage::Decay;
                // Recalc decay rate for current sustain
                decayRate_ = (decayTime_ > 0.0)
                    ? ((1.0 - sustainLevel_) / (decayTime_ * sampleRate_))
                    : (1.0 - sustainLevel_);
            }
            break;

        case Stage::Decay:
            level_ -= decayRate_;
            if (level_ <= sustainLevel_) {
                level_ = sustainLevel_;
                stage_ = Stage::Sustain;
            }
            break;

        case Stage::Sustain:
            level_ = sustainLevel_;
            break;

        case Stage::Release:
            level_ -= releaseRate_;
            if (level_ <= 0.0) {
                level_ = 0.0;
                stage_ = Stage::Idle;
            }
            break;
    }

    return static_cast<float>(std::clamp(level_, 0.0, 1.0));
}

void Envelope::reset() {
    stage_ = Stage::Idle;
    level_ = 0.0;
    attackRate_ = 0.0;
    decayRate_ = 0.0;
    releaseRate_ = 0.0;
}

double Envelope::mapTime(double t01, double maxMs) const {
    // Exponential mapping for natural-feeling control
    if (t01 <= 0.0) return 0.001; // minimum 1ms
    double ms = maxMs * t01 * t01; // quadratic mapping
    double seconds = ms / 1000.0;
    return std::clamp(seconds, 0.001, maxMs / 1000.0);
}
