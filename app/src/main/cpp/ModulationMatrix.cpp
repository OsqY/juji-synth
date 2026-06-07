#include "ModulationMatrix.h"

void ModulationMatrix::setRoute(int index, const ModulationRoute& route) {
    if (index >= 0 && index < 8) {
        routes_[index] = route;
    }
}

void ModulationMatrix::clearRoute(int index) {
    if (index >= 0 && index < 8) {
        routes_[index] = ModulationRoute{};
    }
}

void ModulationMatrix::clearAll() {
    for (auto& route : routes_) {
        route = ModulationRoute{};
        route.active = false;
    }
}

float ModulationMatrix::getModulation(
    int destination,
    const std::array<float, 6>& sourceValues) const
{
    float total = 0.0f;

    for (const auto& route : routes_) {
        if (!route.active) continue;
        if (route.destination != destination) continue;

        if (route.source >= 0 && route.source < 6) {
            total += sourceValues[route.source] * route.amount;
        }
    }

    return total;
}
