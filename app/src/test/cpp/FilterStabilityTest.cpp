#include "Filter.h"

#include <array>
#include <cassert>
#include <cmath>
#include <iostream>

int main() {
    constexpr std::array<double, 6> values{0.0, 0.1, 0.25, 0.5, 0.8, 1.0};
    constexpr std::array<double, 3> envelopeAmounts{-1.0, 0.0, 1.0};

    for (double sampleRate : {44100.0, 48000.0}) {
        for (double cutoff : values) {
            for (double resonance : values) {
                for (double envelopeAmount : envelopeAmounts) {
                    for (int mode = 0; mode < 3; ++mode) {
                        Filter filter;
                        filter.init(sampleRate);
                        filter.setCutoff(cutoff);
                        filter.setResonance(resonance);
                        filter.setEnvelopeAmount(envelopeAmount);
                        filter.setMode(mode);

                        for (int frame = 0; frame < 4096; ++frame) {
                            const double phase = frame / sampleRate;
                            const float input = static_cast<float>(
                                0.5 * std::sin(2.0 * M_PI * 110.0 * phase) +
                                0.25 * std::sin(2.0 * M_PI * 997.0 * phase));
                            const double envelope = 0.5 + 0.5 * std::sin(2.0 * M_PI * frame / 4096.0);
                            filter.applyEnvelope(envelope);
                            const float output = filter.process(input);
                            assert(std::isfinite(output));
                            assert(std::abs(output) < 16.0f);
                        }
                    }
                }
            }
        }
    }

    Filter directFilter;
    directFilter.init(48000.0);
    directFilter.setResonance(0.0);
    directFilter.setCutoff(0.0);
    double closedEnergy = 0.0;
    for (int frame = 0; frame < 4096; ++frame) {
        const float input = static_cast<float>(std::sin(2.0 * M_PI * 997.0 * frame / 48000.0));
        const float output = directFilter.process(input);
        if (frame >= 1024) closedEnergy += output * output;
    }
    directFilter.setCutoff(1.0);
    double openEnergy = 0.0;
    for (int frame = 0; frame < 4096; ++frame) {
        const float input = static_cast<float>(std::sin(2.0 * M_PI * 997.0 * frame / 48000.0));
        const float output = directFilter.process(input);
        assert(std::isfinite(output));
        if (frame >= 1024) openEnergy += output * output;
    }
    assert(openEnergy > closedEnergy * 10.0);

    std::cout << "Filter stability sweep passed\n";
}
