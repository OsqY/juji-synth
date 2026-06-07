#ifndef JUJISYNTH_ENVELOPE_H
#define JUJISYNTH_ENVELOPE_H

/**
 * ADSR Envelope Generator.
 * 
 * Generates the classic Attack-Decay-Sustain-Release envelope
 * shape used in subtractive synthesis.
 * 
 * All time values are internally scaled to samples.
 */
class Envelope {
public:
    enum class Stage {
        Idle,
        Attack,
        Decay,
        Sustain,
        Release
    };

    Envelope();
    ~Envelope() = default;

    /** Initialize with sample rate */
    void init(double sampleRate);

    /** Set attack time 0.0-1.0 (mapped 0-10000ms) */
    void setAttack(double time);

    /** Set decay time 0.0-1.0 (mapped 0-10000ms) */
    void setDecay(double time);

    /** Set sustain level 0.0-1.0 */
    void setSustain(double level);

    /** Set release time 0.0-1.0 (mapped 0-10000ms) */
    void setRelease(double time);

    /** Start the envelope (enter attack stage) */
    void noteOn();

    /** Release the envelope (enter release stage) */
    void noteOff();

    /** Process: get current envelope value (0.0-1.0) */
    float process();

    /** Returns true if envelope has completed its cycle */
    bool isIdle() const { return stage_ == Stage::Idle; }

    /** Reset to idle state */
    void reset();

    /** Get current stage */
    Stage getStage() const { return stage_; }

private:
    double sampleRate_ = 44100.0;

    // Time parameters (in seconds after mapping)
    double attackTime_ = 0.01;
    double decayTime_ = 0.3;
    double sustainLevel_ = 0.7;
    double releaseTime_ = 0.2;

    // Internal counters
    double level_ = 0.0;
    double attackRate_ = 0.0;
    double decayRate_ = 0.0;
    double releaseRate_ = 0.0;

    Stage stage_ = Stage::Idle;

    double mapTime(double t01, double maxMs) const;
};

#endif // JUJISYNTH_ENVELOPE_H
