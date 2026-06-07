#ifndef JUJISYNTH_MODULATIONMATRIX_H
#define JUJISYNTH_MODULATIONMATRIX_H

#include "SynthParams.h"
#include <array>

/**
 * Modulation matrix routes modulation sources to destinations.
 * 
 * Sources: LFO1, LFO2, ENV1, ENV2, Velocity, Aftertouch
 * Destinations: Pitch, Filter Cutoff, Filter Res, Amp, OscMix, LFO Rate
 * 
 * Supports up to 8 simultaneous routings with per-route amount.
 */
class ModulationMatrix {
public:
    ModulationMatrix() = default;
    ~ModulationMatrix() = default;

    void setRoute(int index, const ModulationRoute& route);
    void clearRoute(int index);
    void clearAll();

    /** Get modulation value for a specific destination */
    float getModulation(int destination,
                        const std::array<float, 6>& sourceValues) const;

private:
    std::array<ModulationRoute, 8> routes_;
};

#endif // JUJISYNTH_MODULATIONMATRIX_H
