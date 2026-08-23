#ifndef JUJISYNTH_FILTER_H
#define JUJISYNTH_FILTER_H

/**
 * Multi-mode state variable filter (SVF).
 * Provides low-pass, high-pass, and band-pass modes
 * with resonance control.
 * 
 * Uses a topology-preserving state-variable filter for stable modulation.
 */
class Filter {
public:
    Filter();
    ~Filter() = default;

    /** Initialize with sample rate */
    void init(double sampleRate);

    /** Set cutoff frequency 0.0-1.0 (mapped 20Hz-20kHz) */
    void setCutoff(double cutoff);

    /** Set resonance 0.0-1.0 */
    void setResonance(double res);

    /** Set filter mode: 0=LPF, 1=HPF, 2=BPF */
    void setMode(int mode);

    /** Set envelope modulation amount (-1.0 to 1.0) */
    void setEnvelopeAmount(double amount);

    /** Apply envelope value (0.0-1.0) */
    void applyEnvelope(double envValue);

    /** Process one sample, return filtered output */
    float process(float input);

    /** Reset internal state */
    void reset();

private:
    double sampleRate_ = 44100.0;
    double cutoff_ = 0.8;
    double resonance_ = 0.0;
    double envAmount_ = 0.0;
    double effectiveCutoff_ = 0.8;
    int mode_ = 0;

    // Topology-preserving SVF integrator state.
    double ic1eq_ = 0.0;
    double ic2eq_ = 0.0;

    double f_ = 0.0; // current (smoothed) frequency coefficient
    double damping_ = 2.0; // inverse Q
    double targetF_ = 0.01;
    double effectiveF_ = 0.01;

    double cutoffCoefficient(double normalizedCutoff) const;
    void recalcCoefficients();
};

#endif // JUJISYNTH_FILTER_H
