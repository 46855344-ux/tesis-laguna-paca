#include "hardware/HttpTelemetryTransport.h"

#include <Arduino.h>
#include <ArduinoJson.h>
#include <HTTPClient.h>

#include <cstring>

namespace hardware {

namespace {

/** Margen con el que se renueva el token antes de que caduque de verdad. */
constexpr std::uint32_t kTokenSafetyMarginSeconds = 300;

constexpr std::uint16_t kHttpTimeoutMillis = 5000;

/** Los JWT HS256 con subject rondan los 200-300 caracteres. */
constexpr std::size_t kMaxTokenLength = 512;

/**
 * Cache del JWT en memoria RTC.
 *
 * RTC_DATA_ATTR hace que el bloque sobreviva al Deep Sleep (que borra la RAM
 * normal), asi que el nodo no re-autentica en cada ciclo.
 *
 * No hay reloj de pared: el envejecimiento se lleva de forma monotona, sumando
 * el tiempo dormido y el activo.
 */
struct TokenCache {
    char accessToken[kMaxTokenLength];
    std::uint32_t lifetimeSeconds;
    std::uint32_t consumedSeconds;
};

RTC_DATA_ATTR TokenCache g_tokenCache;

}  // namespace

HttpTelemetryTransport::HttpTelemetryTransport(const char* baseUrl,
                                               const char* username,
                                               const char* password)
    : baseUrl_(baseUrl),
      telemetryUrl_(std::string(baseUrl) + "/api/v1/telemetry"),
      tokenUrl_(std::string(baseUrl) + "/api/v1/auth/token"),
      username_(username),
      password_(password) {
}

void HttpTelemetryTransport::noteElapsedSeconds(std::uint32_t seconds) {
    if (g_tokenCache.accessToken[0] == '\0') {
        return;
    }
    g_tokenCache.consumedSeconds += seconds;
}

void HttpTelemetryTransport::invalidateToken() {
    g_tokenCache.accessToken[0] = '\0';
    g_tokenCache.lifetimeSeconds = 0;
    g_tokenCache.consumedSeconds = 0;
}

bool HttpTelemetryTransport::hasCachedToken() const {
    return g_tokenCache.accessToken[0] != '\0'
            && g_tokenCache.consumedSeconds + kTokenSafetyMarginSeconds < g_tokenCache.lifetimeSeconds;
}

bool HttpTelemetryTransport::send(const std::string& payload) {
    if (!ensureToken()) {
        Serial.println("[http] sin token: no se envio la muestra");
        return false;
    }

    HTTPClient http;
    if (!http.begin(String(telemetryUrl_.c_str()))) {
        return false;
    }
    http.setTimeout(kHttpTimeoutMillis);
    http.addHeader("Content-Type", "application/json");
    http.addHeader("Authorization", String("Bearer ") + g_tokenCache.accessToken);

    const int status = http.POST(String(payload.c_str()));
    http.end();

    if (status == 401) {
        // El token caduco antes de lo previsto: se invalida para el proximo ciclo.
        invalidateToken();
    }
    return status == 200 || status == 201;
}

bool HttpTelemetryTransport::ensureToken() {
    if (hasCachedToken()) {
        return true;
    }
    return authenticate();
}

bool HttpTelemetryTransport::authenticate() {
    HTTPClient http;
    if (!http.begin(String(tokenUrl_.c_str()))) {
        return false;
    }
    http.setTimeout(kHttpTimeoutMillis);
    http.addHeader("Content-Type", "application/json");

    const String body = String("{\"username\":\"") + username_.c_str()
            + "\",\"password\":\"" + password_.c_str() + "\"}";
    const int status = http.POST(body);
    if (status != 200) {
        http.end();
        Serial.printf("[http] autenticacion fallida (HTTP %d)\n", status);
        return false;
    }

    const String response = http.getString();
    http.end();

    JsonDocument document;
    if (deserializeJson(document, response) != DeserializationError::Ok) {
        Serial.println("[http] respuesta de autenticacion ilegible");
        return false;
    }

    const char* token = document["access_token"] | "";
    const std::uint32_t lifetime = document["expires_in"] | 0u;
    if (token[0] == '\0' || lifetime == 0) {
        Serial.println("[http] respuesta de autenticacion sin token");
        return false;
    }

    std::strncpy(g_tokenCache.accessToken, token, kMaxTokenLength - 1);
    g_tokenCache.accessToken[kMaxTokenLength - 1] = '\0';
    g_tokenCache.lifetimeSeconds = lifetime;
    g_tokenCache.consumedSeconds = 0;
    return true;
}

}  // namespace hardware
