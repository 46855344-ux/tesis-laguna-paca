#pragma once

namespace laguna {

/**
 * Limites de plausibilidad, espejo de las restricciones que el backend aplica a
 * `TelemetryRequest`. Fuera de estos rangos la lectura se descarta.
 */
constexpr double kMinPh = 0.0;
constexpr double kMaxPh = 14.0;
constexpr double kMinTempC = -5.0;
constexpr double kMaxTempC = 60.0;
constexpr double kMinTurbidityNtu = 0.0;
constexpr double kMinTdsPpm = 0.0;

/**
 * Vector de parametros fisicoquimicos de una muestra, en las unidades que
 * publica el nodo: pH, NTU, grados Celsius y ppm.
 */
struct TelemetrySample {
    double ph;
    double turbidityNtu;
    double tempC;
    double tdsPpm;
};

/**
 * Resultado de validar la plausibilidad de una muestra.
 *
 * Refleja las mismas restricciones que el backend aplica a `TelemetryRequest`,
 * para no enviar por MQTT/HTTP datos que el servidor rechazaria. NO evalua los
 * umbrales ECA-Agua: esa regla de negocio vive en el backend.
 */
enum class SampleValidity {
    Valid,
    NodeIdInvalid,
    PhOutOfRange,
    TurbidityNegative,
    TemperatureOutOfRange,
    TdsNegative
};

/** Mensaje legible del resultado, util para el log serie. */
const char* describeValidity(SampleValidity validity);

/** Valida un identificador de nodo (no vacio, <= 32 caracteres). */
bool isValidNodeId(const char* nodeId);

/**
 * Valida la muestra completa contra los rangos del backend:
 * pH 0-14, turbidez >= 0, temperatura -5..60 y TDS >= 0.
 */
SampleValidity validateSample(const char* nodeId, const TelemetrySample& sample);

}  // namespace laguna
