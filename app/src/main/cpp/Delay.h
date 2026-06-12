#ifndef JUJISYNTH_DELAY_H
#define JUJISYNTH_DELAY_H

#include <array>
#include "Smoother.h"

/**
 * Digital delay effect with feedback and optional tempo sync.
 * Uses linear interpolation on reads and parameter smoothing
 * to prevent zipper noise when tweaking controls.
 */
class Delay {
public:
    Delay();
    ~Delay() = default;

    void init(double sampleRate);
    void setMix(double mix);         // 0.0-1.0
    void setTime(double time);       // 0.0-1.0 (mapped 20-2000ms)
    void setFeedback(double fb);     // 0.0-1.0
    void setTempoSync(bool sync);
    float process(float input);
    void reset();

private:
    double sampleRate_ = 44100.0;
    double mix_ = 0.0;
    double delayTimeMs_ = 300.0;
    double feedback_ = 0.3;
    bool tempoSync_ = false;

    static constexpr int MAX_DELAY_SAMPLES = 88200; // 2 seconds at 44.1k
    std::array<float, MAX_DELAY_SAMPLES> buffer_{};
    int writeIndex_ = 0;
    int delaySamples_ = 0;
    float smoothDelaySamples_ = 0.0f; // smoothed read position for linear interp
    Smoother mixSmoother_;
    float lastOutput_ = 0.0f;
};

#endif // JUJISYNTH_DELAY_H
