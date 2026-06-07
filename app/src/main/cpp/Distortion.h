#ifndef JUJISYNTH_DISTORTION_H
#define JUJISYNTH_DISTORTION_H

/**
 * Soft-clip distortion effect.
 * 
 * Uses a tanh waveshaper for smooth, musical saturation.
 * Low drive = subtle warmth, high drive = aggressive distortion.
 */
class Distortion {
public:
    Distortion();
    ~Distortion() = default;

    void setDrive(double drive); // 0.0-1.0
    void setMix(double mix);     // 0.0-1.0
    float process(float input);
    void reset();

private:
    double drive_ = 0.0;
    double mix_ = 0.0;
    double driveFactor_ = 1.0;
};

#endif // JUJISYNTH_DISTORTION_H
