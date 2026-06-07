#include "Oscillator.h"
#include <algorithm>
#include <cmath>

Oscillator::Oscillator() {
    wavetable_.resize(TABLE_SIZE);
}

void Oscillator::init(double sampleRate) {
    sampleRate_ = sampleRate;
    phase_ = 0.0;
    tableIndex_ = 0.0;
    rebuildWavetable();
}

void Oscillator::setFrequency(double freqHz) {
    frequency_ = std::clamp(freqHz, 0.1, 20000.0);
    tableIncrement_ = (frequency_ * TABLE_SIZE) / sampleRate_;
}

void Oscillator::setWaveform(int type) {
    waveformType_ = std::clamp(type, 0, 3);
    rebuildWavetable();
}

void Oscillator::setAmplitude(double amp) {
    amplitude_ = std::clamp(amp, 0.0, 1.0);
}

void Oscillator::reset() {
    phase_ = 0.0;
    tableIndex_ = 0.0;
}

void Oscillator::setPhaseOffset(double offset) {
    phaseOffset_ = std::clamp(offset, 0.0, 1.0);
}

void Oscillator::sync() {
    tableIndex_ = 0.0;
}

void Oscillator::rebuildWavetable() {
    for (int i = 0; i < TABLE_SIZE; i++) {
        double phase = (double)i / TABLE_SIZE;
        double value = 0.0;

        switch (waveformType_) {
            case 0: // Saw
                value = 2.0 * phase - 1.0;
                // MinBLEP-like smoothing for alias reduction
                value *= 0.99; // slight rolloff
                break;
            case 1: // Square
                value = (phase < 0.5) ? 1.0 : -1.0;
                break;
            case 2: // Triangle
                value = (phase < 0.5)
                    ? 4.0 * phase - 1.0
                    : 3.0 - 4.0 * phase;
                break;
            case 3: // Sine
                value = std::sin(2.0 * M_PI * phase);
                break;
        }

        wavetable_[i] = static_cast<float>(value);
    }
}

float Oscillator::process() {
    if (amplitude_ == 0.0) return 0.0f;

    float sample = interpolate();

    tableIndex_ += tableIncrement_;
    if (tableIndex_ >= TABLE_SIZE) {
        tableIndex_ -= TABLE_SIZE;
    }

    return sample * static_cast<float>(amplitude_);
}

float Oscillator::interpolate() {
    int index = static_cast<int>(tableIndex_);
    double frac = tableIndex_ - index;

    int next = (index + 1) % TABLE_SIZE;

    // Linear interpolation
    return wavetable_[index] * (1.0f - static_cast<float>(frac))
         + wavetable_[next] * static_cast<float>(frac);
}
