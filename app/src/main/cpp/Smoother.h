#ifndef JUJISYNTH_SMOOTHER_H
#define JUJISYNTH_SMOOTHER_H

/**
 * One-pole low-pass smoother for parameter changes.
 * Prevents zipper noise by smoothing abrupt value changes.
 * Time constant is determined by the coefficient (0.0-1.0).
 * Lower coefficient = slower smoothing (more lag).
 * Typical coefficient: 0.1-0.3 for audio parameters (~2-10ms at 44.1kHz).
 */
class Smoother {
public:
    Smoother() = default;
    ~Smoother() = default;

    /** Set smoothing coefficient (0.0-1.0). Higher = faster response. */
    void setCoefficient(float coeff) {
        coeff_ = coeff;
    }

    /** Set target value and get smoothed output */
    float process(float target) {
        current_ += (target - current_) * coeff_;
        return current_;
    }

    /** Reset to a specific value */
    void reset(float value = 0.0f) {
        current_ = value;
    }

    /** Get current smoothed value without advancing */
    float getCurrent() const { return current_; }

private:
    float coeff_ = 0.3f;  // default: ~3ms at 44.1kHz
    float current_ = 0.0f;
};

#endif // JUJISYNTH_SMOOTHER_H
