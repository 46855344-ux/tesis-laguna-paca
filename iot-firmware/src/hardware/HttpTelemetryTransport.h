#pragma once

#include <cstdint>
#include <string>

#include "lagunapaca/TelemetryPorts.h"

namespace hardware {

/**
 * Transporte de telemetria por HTTP POST contra el backend propio.
 *
 * `POST /api/v1/telemetry` exige JWT, asi que el nodo primero se autentica en
 * `POST /api/v1/auth/token` con sus credenciales. El token se guarda en memoria
 * RTC: sobrevive al Deep Sleep, de modo que no se paga un viaje de red extra en
 * cada despertar (el token dura 2 h).
 */
class HttpTelemetryTransport : public laguna::TelemetryTransport {
public:
    HttpTelemetryTransport(const char* baseUrl, const char* username, const char* password);

    bool send(const std::string& payload) override;

    /**
     * Suma al token cacheado el tiempo que el nodo estuvo dormido.
     *
     * Llamar justo tras despertar: sin reloj de pared (NTP) no hay otra forma de
     * saber cuanto ha envejecido el token.
     */
    void noteElapsedSeconds(std::uint32_t seconds);

    /** Descarta el token cacheado (por ejemplo, para forzar la re-autenticacion). */
    void invalidateToken();

    /** @return {@code true} si el token cacheado sigue siendo utilizable. */
    bool hasCachedToken() const;

private:
    /** Garantiza un token valido, autenticandose si hace falta. */
    bool ensureToken();

    /** Pide un token nuevo al backend y lo deja en la cache RTC. */
    bool authenticate();

    std::string baseUrl_;
    std::string telemetryUrl_;
    std::string tokenUrl_;
    std::string username_;
    std::string password_;
};

}  // namespace hardware
