#ifndef JUJISYNTH_CHORUS_H
#define JUJISYNTH_CHORUS_H

#include <array>
#include <cmath>

class Chorus {
public:
    Chorus() = default;
    ~Chorus() = default;

    void init(double sampleRate);
    void setRate(double rate);    // 0.0-1.0 mapped to 0.1-5.0 Hz
    void setDepth(double depth);  // 0.0-1.0 mapped to 0-20ms
    void setMix(double mix);      // 0.0-1.0 dry/wet
    float process(float input);
    void reset();

private:
    double sampleRate_ = 44100.0;
    double rate_ = 0.5;
    double depth_ = 0.0;
    double mix_ = 0.0;
    double phase_ = 0.0;

    static constexpr int MAX_DELAY_SAMPLES = 4410; // 100ms at 44.1kHz
    std::array<float, MAX_DELAY_SAMPLES> buffer_{};
    int writeIndex_ = 0;
};

#endif
