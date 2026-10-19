#include "lagunapaca/DeepSleepPolicy.h"

namespace laguna {

DeepSleepPolicy::DeepSleepPolicy(std::uint32_t intervalSeconds, std::uint32_t minimumSeconds)
    : intervalSeconds_(intervalSeconds), minimumSeconds_(minimumSeconds) {
}

std::uint32_t DeepSleepPolicy::sleepSeconds(std::uint32_t activeMillis) const {
    const std::uint32_t activeSeconds = activeMillis / 1000u;

    // Un ciclo activo mas largo que el intervalo no puede dormir "negativo".
    if (activeSeconds >= intervalSeconds_) {
        return minimumSeconds_;
    }

    const std::uint32_t remaining = intervalSeconds_ - activeSeconds;
    return remaining < minimumSeconds_ ? minimumSeconds_ : remaining;
}

std::uint64_t DeepSleepPolicy::sleepMicros(std::uint32_t activeMillis) const {
    return static_cast<std::uint64_t>(sleepSeconds(activeMillis)) * 1000000ull;
}

}  // namespace laguna
