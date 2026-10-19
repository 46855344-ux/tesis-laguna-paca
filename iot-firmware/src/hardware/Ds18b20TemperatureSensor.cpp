#include "hardware/Ds18b20TemperatureSensor.h"

#include <Arduino.h>

namespace hardware {

namespace {
/** Resolucion de 12 bits: 0.0625 C por paso (el datasheet da +-0.5 C). */
constexpr std::uint8_t kResolutionBits = 12;
/** DallasTemperature usa -127 C para "sensor desconectado". */
constexpr float kDisconnectedCelsius = -100.0f;
}  // namespace

Ds18b20TemperatureSensor::Ds18b20TemperatureSensor(std::uint8_t pin)
    : wire_(pin), sensors_(&wire_) {
}

void Ds18b20TemperatureSensor::begin() {
    sensors_.begin();
    sensors_.setResolution(kResolutionBits);
}

double Ds18b20TemperatureSensor::readCelsius() {
    sensors_.requestTemperatures();
    const float measured = sensors_.getTempCByIndex(0);

    if (measured <= kDisconnectedCelsius) {
        // NaN en lugar de un valor falso: validateSample() lo rechaza.
        return NAN;
    }
    return static_cast<double>(measured);
}

}  // namespace hardware
