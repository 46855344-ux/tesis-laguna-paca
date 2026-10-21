#include "lagunapaca/TelemetryPayload.h"

#include "lagunapaca/DecimalFormat.h"

namespace laguna {

namespace {
// Precision acorde a los sensores de la tesis: el DS18B20 y el TDS con un
// decimal, las sondas analogicas con dos.
constexpr int kPhDecimals = 2;
constexpr int kTurbidityDecimals = 2;
constexpr int kTemperatureDecimals = 1;
constexpr int kTdsDecimals = 1;
}  // namespace

std::string buildTelemetryPayload(const char* nodeId, const TelemetrySample& sample,
                                  const char* sampleId) {
    std::string payload;
    payload.reserve(sampleId == nullptr ? 96 : 160);

    payload += "{";
    if (sampleId != nullptr && sampleId[0] != '\0') {
        // Campo aditivo: el backend lo acepta opcionalmente y lo usa para que
        // un reintento no cree una segunda muestra.
        payload += "\"sample_id\": \"";
        payload += sampleId;
        payload += "\", ";
    }

    payload += "\"node_id\": \"";
    payload += (nodeId == nullptr ? "" : nodeId);
    payload += "\", \"ph\": ";
    payload += formatDecimal(sample.ph, kPhDecimals);
    payload += ", \"turbidity_ntu\": ";
    payload += formatDecimal(sample.turbidityNtu, kTurbidityDecimals);
    payload += ", \"temp_c\": ";
    payload += formatDecimal(sample.tempC, kTemperatureDecimals);
    payload += ", \"tds_ppm\": ";
    payload += formatDecimal(sample.tdsPpm, kTdsDecimals);
    payload += "}";

    return payload;
}

}  // namespace laguna
