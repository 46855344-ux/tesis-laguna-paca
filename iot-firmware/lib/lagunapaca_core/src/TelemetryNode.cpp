#include "lagunapaca/TelemetryNode.h"

#include "lagunapaca/MovingAverageFilter.h"
#include "lagunapaca/TelemetryPayload.h"

namespace laguna {

TelemetryNode::TelemetryNode(const TelemetryNodeConfig& config,
                             AnalogChannel& phChannel,
                             AnalogChannel& turbidityChannel,
                             AnalogChannel& tdsChannel,
                             TemperatureSensor& temperatureSensor,
                             const PhCalibration& phCalibration,
                             const TurbidityCalibration& turbidityCalibration,
                             const TdsCalibration& tdsCalibration,
                             TelemetryTransport& transport)
    : config_(config),
      phChannel_(phChannel),
      turbidityChannel_(turbidityChannel),
      tdsChannel_(tdsChannel),
      temperatureSensor_(temperatureSensor),
      phCalibration_(phCalibration),
      turbidityCalibration_(turbidityCalibration),
      tdsCalibration_(tdsCalibration),
      transport_(transport),
      lastSample_{0.0, 0.0, 0.0, 0.0} {
}

double TelemetryNode::sampleChannel(AnalogChannel& channel) {
    // La ventana se abre y se cierra dentro del ciclo: el Deep Sleep reinicia la
    // ESP32 y con el se perderia cualquier promedio entre ciclos.
    MovingAverageFilter filter(config_.samplesPerReading);

    double average = 0.0;
    for (std::size_t i = 0; i < filter.windowSize(); ++i) {
        average = filter.add(channel.readVoltage());
    }
    return average;
}

TelemetryNode::Outcome TelemetryNode::runOnce(const char* sampleId) {
    const double ph = phCalibration_.toPh(sampleChannel(phChannel_));
    const double turbidity = turbidityCalibration_.toNtu(sampleChannel(turbidityChannel_));
    const double temperature = temperatureSensor_.readCelsius();
    const double tds = tdsCalibration_.toPpm(sampleChannel(tdsChannel_), temperature);

    lastSample_ = TelemetrySample{ph, turbidity, temperature, tds};
    lastValidity_ = validateSample(config_.nodeId, lastSample_);

    if (lastValidity_ != SampleValidity::Valid) {
        lastPayload_.clear();
        return Outcome::InvalidSample;
    }

    lastPayload_ = buildTelemetryPayload(config_.nodeId, lastSample_, sampleId);
    return transport_.send(lastPayload_) ? Outcome::Sent : Outcome::TransportFailed;
}

}  // namespace laguna
