#pragma once

#include <cstddef>

#include <string>

#include "lagunapaca/SensorCalibration.h"
#include "lagunapaca/TelemetryPorts.h"
#include "lagunapaca/TelemetrySample.h"

namespace laguna {

/**
 * Configuracion del nodo.
 *
 * Usa constructor explicito en lugar de inicializadores de miembro: el core
 * Arduino del ESP32 compila con gnu++11, donde una struct con inicializadores de
 * miembro deja de ser un agregado y no admite la inicializacion con llaves.
 */
struct TelemetryNodeConfig {
    /** Identificador del nodo, por ejemplo "N1". */
    const char* nodeId;

    /** Ventana w del filtro de media movil causal (tesis: 3). */
    std::size_t samplesPerReading;

    TelemetryNodeConfig(const char* nodeId = "N1", std::size_t samplesPerReading = 3)
            : nodeId(nodeId), samplesPerReading(samplesPerReading) {
    }
};

/**
 * Orquesta un ciclo de medicion: lee los canales aplicando el filtro de media
 * movil causal, convierte a unidades fisicas, valida la plausibilidad, arma el
 * payload y lo entrega al transporte.
 *
 * No conoce Arduino ni el Deep Sleep: es logica portable y testeable.
 */
class TelemetryNode {
public:
    enum class Outcome {
        Sent,
        InvalidSample,
        TransportFailed
    };

    TelemetryNode(const TelemetryNodeConfig& config,
                  AnalogChannel& phChannel,
                  AnalogChannel& turbidityChannel,
                  AnalogChannel& tdsChannel,
                  TemperatureSensor& temperatureSensor,
                  const PhCalibration& phCalibration,
                  const TurbidityCalibration& turbidityCalibration,
                  const TdsCalibration& tdsCalibration,
                  TelemetryTransport& transport);

    /** Ejecuta un ciclo completo de medicion y envio. */
    Outcome runOnce();

    const TelemetrySample& lastSample() const { return lastSample_; }
    const std::string& lastPayload() const { return lastPayload_; }
    SampleValidity lastValidity() const { return lastValidity_; }

private:
    /** Toma `samplesPerReading` lecturas consecutivas y devuelve su media movil. */
    double sampleChannel(AnalogChannel& channel);

    TelemetryNodeConfig config_;
    AnalogChannel& phChannel_;
    AnalogChannel& turbidityChannel_;
    AnalogChannel& tdsChannel_;
    TemperatureSensor& temperatureSensor_;
    PhCalibration phCalibration_;
    TurbidityCalibration turbidityCalibration_;
    TdsCalibration tdsCalibration_;
    TelemetryTransport& transport_;

    TelemetrySample lastSample_;
    std::string lastPayload_;
    SampleValidity lastValidity_ = SampleValidity::Valid;
};

}  // namespace laguna
