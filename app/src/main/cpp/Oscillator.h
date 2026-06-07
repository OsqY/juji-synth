#ifndef JUJISYNTH_OSCILLATOR_H
#define JUJISYNTH_OSCILLATOR_H

#include <cmath>
#include <vector>
#include <cstdint>

/**
 * Oscillator generates audio waveforms at a given frequency.
 * Supports saw, square, triangle, and sine waveforms.
 * Uses wavetable synthesis for efficient, alias-free generation.
 */
class Oscillator {
public:
    Oscillator();
    ~Oscillator() = default;

    /** Initialize with sample rate */
    void init(double sampleRate);

    /** Set frequency in Hz */
    void setFrequency(double freqHz);

    /** Set waveform type: 0=saw, 1=square, 2=triangle, 3=sine */
    void setWaveform(int type);

    /** Set amplitude 0.0-1.0 */
    void setAmplitude(double amp);

    /** Get next sample */
    float process();

    /** Reset phase to beginning */
    void reset();

    /** Set phase offset 0.0-1.0 */
    void setPhaseOffset(double offset);

    /** Sync: reset this oscillator's phase */
    void sync();

private:
    void rebuildWavetable();
    float interpolate();

    double sampleRate_ = 44100.0;
    double frequency_ = 440.0;
    double amplitude_ = 1.0;
    double phase_ = 0.0;
    double phaseOffset_ = 0.0;
    int waveformType_ = 0; // 0=saw

    std::vector<float> wavetable_;
    static constexpr int TABLE_SIZE = 4096;
    double tableIncrement_ = 0.0;
    double tableIndex_ = 0.0;
};

#endif // JUJISYNTH_OSCILLATOR_H
