#include "lagunapaca/TelemetrySample.h"

#include <cmath>
#include <cstring>

namespace laguna {

namespace {
/** Espejo de la restriccion @Size(max = 32) del backend. */
constexpr std::size_t kMaxNodeIdLength = 32;
}  // namespace

const char* describeValidity(SampleValidity validity) {
    switch (validity) {
        case SampleValidity::Valid:
            return "muestra valida";
        case SampleValidity::NodeIdInvalid:
            return "node_id vacio o de mas de 32 caracteres";
        case SampleValidity::PhOutOfRange:
            return "pH fuera del rango 0-14 o no finito";
        case SampleValidity::TurbidityNegative:
            return "turbidez negativa o no finita";
        case SampleValidity::TemperatureOutOfRange:
            return "temperatura fuera del rango -5 a 60 C o no finita";
        case SampleValidity::TdsNegative:
            return "TDS negativo o no finito";
    }
    return "estado desconocido";
}

bool isValidNodeId(const char* nodeId) {
    if (nodeId == nullptr) {
        return false;
    }
    const std::size_t length = std::strlen(nodeId);
    return length > 0 && length <= kMaxNodeIdLength;
}

SampleValidity validateSample(const char* nodeId, const TelemetrySample& sample) {
    if (!isValidNodeId(nodeId)) {
        return SampleValidity::NodeIdInvalid;
    }
    if (!std::isfinite(sample.ph) || sample.ph < kMinPh || sample.ph > kMaxPh) {
        return SampleValidity::PhOutOfRange;
    }
    if (!std::isfinite(sample.turbidityNtu) || sample.turbidityNtu < kMinTurbidityNtu) {
        return SampleValidity::TurbidityNegative;
    }
    if (!std::isfinite(sample.tempC) || sample.tempC < kMinTempC || sample.tempC > kMaxTempC) {
        return SampleValidity::TemperatureOutOfRange;
    }
    if (!std::isfinite(sample.tdsPpm) || sample.tdsPpm < kMinTdsPpm) {
        return SampleValidity::TdsNegative;
    }
    return SampleValidity::Valid;
}

}  // namespace laguna
