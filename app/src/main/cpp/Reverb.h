#ifndef JUJISYNTH_REVERB_H
#define JUJISYNTH_REVERB_H

#include <array>

/**
 * Simple Schroeder/Moorer reverb using comb filters and all-pass filters.
 * Produces a diffuse, natural-sounding reverb tail.
 */
class Reverb {
public:
    Reverb();
    ~Reverb() = default;

    void init(double sampleRate);
    void setMix(double mix);       // 0.0-1.0
    void setDecay(double decay);   // 0.0-1.0
    void setDamping(double damp);  // 0.0-1.0
    float process(float input);
    void reset();

private:
    double sampleRate_ = 44100.0;
    double mix_ = 0.3;
    double decay_ = 0.5;
    double damping_ = 0.5;

    // Comb filter delay lines (lengths in samples)
    static constexpr int NUM_COMBS = 4;
    static constexpr int COMB_LENGTHS[NUM_COMBS] = {1116, 1188, 1277, 1356};
    static constexpr int NUM_ALLPASS = 2;
    static constexpr int ALLPASS_LENGTHS[NUM_ALLPASS] = {556, 441};

    std::array<std::array<float, 2000>, NUM_COMBS> combBuffers_{};
    std::array<int, NUM_COMBS> combIndices_{};
    std::array<float, NUM_COMBS> combFilterState_{};

    std::array<std::array<float, 1000>, NUM_ALLPASS> allpassBuffers_{};
    std::array<int, NUM_ALLPASS> allpassIndices_{};
};

#endif // JUJISYNTH_REVERB_H
