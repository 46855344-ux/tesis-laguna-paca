#pragma once

#include <cstdint>

namespace laguna {

/**
 * Calcula cuanto debe dormir el nodo para respetar el intervalo de muestreo.
 *
 * El ESP32 pasa de 150 mA a ~10 uA en Deep Sleep, que es lo que permite la
 * autonomia solar de la tesis. Se descuenta el tiempo activo del ciclo para que
 * el periodo sea estable, con un minimo de seguridad.
 */
class DeepSleepPolicy {
public:
    static constexpr std::uint32_t kMinimumSeconds = 10;

    DeepSleepPolicy(std::uint32_t intervalSeconds,
                    std::uint32_t minimumSeconds = kMinimumSeconds);

    std::uint32_t intervalSeconds() const { return intervalSeconds_; }

    /** Segundos a dormir, descontando el tiempo activo del ciclo. */
    std::uint32_t sleepSeconds(std::uint32_t activeMillis) const;

    /** El mismo valor en microsegundos, que es lo que consume esp_deep_sleep(). */
    std::uint64_t sleepMicros(std::uint32_t activeMillis) const;

private:
    std::uint32_t intervalSeconds_;
    std::uint32_t minimumSeconds_;
};

}  // namespace laguna
