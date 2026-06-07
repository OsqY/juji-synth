#ifndef JUJISYNTH_LFO_H
#define JUJISYNTH_LFO_H

#include <random>

/**
 * Low Frequency Oscillator for modulation.
 * 
 * Generates cyclic modulation signals at sub-audio rates.
 * Supports sine, square, saw, triangle, and S&H random waveforms.
 */
class LFO {
public:
    LFO();
    ~LFO() = default;

    /** Initialize with sample rate */
    void init(double sampleRate);

    /** Set LFO rate 0.0-1.0 (mapped 0.01-50Hz) */
    void setRate(double rate);

    /** Set modulation depth 0.0-1.0 */
    void setDepth(double depth);

    /** Set waveform type: 0=sine, 1=square, 2=saw, 3=triangle, 4=random */
    void setWaveform(int type);

    /** Enable key sync (restart on note-on) */
    void setKeySync(bool sync);

    /** Restart LFO phase */
    void reset();

    /** Process: get current LFO output value (-1.0 to 1.0) */
    float process();

    /** Get LFO output scaled by depth as modulation value (-depth to +depth) */
    float getModulationValue();

private:
    double sampleRate_ = 44100.0;
    double rate_ = 1.0;           // 0.0-1.0 mapped
    double depth_ = 0.0;          // 0.0-1.0
    int waveformType_ = 0;
    bool keySync_ = false;

    double phase_ = 0.0;
    double phaseIncrement_ = 0.0;
    double currentValue_ = 0.0;

    // Random state
    std::mt19937 rng_{42};
    std::uniform_real_distribution<float> randDist_{-1.0f, 1.0f};
    float currentRandom_ = 0.0f;

    double mapRate(double rate01) const;
};

#endif // JUJISYNTH_LFO_H
