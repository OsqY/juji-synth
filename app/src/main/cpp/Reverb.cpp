#include "Reverb.h"
#include <algorithm>
#include <cmath>

Reverb::Reverb() = default;

void Reverb::init(double sampleRate) {
    sampleRate_ = sampleRate;
    reset();
}

void Reverb::setMix(double mix) {
    mix_ = std::clamp(mix, 0.0, 1.0);
}

void Reverb::setDecay(double decay) {
    decay_ = std::clamp(decay, 0.0, 1.0);
}

void Reverb::setDamping(double damp) {
    damping_ = std::clamp(damp, 0.0, 1.0);
}

float Reverb::process(float input) {
    if (mix_ == 0.0f) return input;

    float wet = 0.0f;
    float feedback_factor = 0.5f + 0.5f * static_cast<float>(decay_);
    float damp_factor = 0.1f + 0.9f * static_cast<float>(damping_);

    // Comb filters in parallel
    for (int i = 0; i < NUM_COMBS; i++) {
        int idx = combIndices_[i];
        float out = combBuffers_[i][idx];

        // Low-pass damping
        combFilterState_[i] = out * (1.0f - damp_factor) + combFilterState_[i] * damp_factor;
        float filtered = combFilterState_[i];

        // Feedback
        combBuffers_[i][idx] = input + filtered * feedback_factor;

        combIndices_[i] = (idx + 1) % COMB_LENGTHS[i];
        wet += out;
    }

    // All-pass filters in series
    for (int i = 0; i < NUM_ALLPASS; i++) {
        int idx = allpassIndices_[i];
        float buf = allpassBuffers_[i][idx];
        float apOut = -buf + wet;
        allpassBuffers_[i][idx] = wet + buf * 0.5f;
        wet = apOut;
        allpassIndices_[i] = (idx + 1) % ALLPASS_LENGTHS[i];
    }

    wet *= 0.5f; // Normalize

    // Dry/wet mix
    return input * (1.0f - static_cast<float>(mix_))
         + wet * static_cast<float>(mix_);
}

void Reverb::reset() {
    for (auto& buf : combBuffers_) buf.fill(0.0f);
    for (auto& buf : allpassBuffers_) buf.fill(0.0f);
    for (auto& idx : combIndices_) idx = 0;
    for (auto& idx : allpassIndices_) idx = 0;
    for (auto& state : combFilterState_) state = 0.0f;
}
