#include "hardware/Esp32AnalogChannel.h"

#include <Arduino.h>

namespace hardware {

namespace {

/** Escala del ADC de 12 bits: 2^12 - 1. */
constexpr double kAdcFullScale = 4095.0;

/** Espera antes de muestrear, para separar esta lectura de la anterior. */
constexpr std::uint32_t kReadSpacingMicros = 200;

/** Espera entre muestras del sobre-muestreo. */
constexpr std::uint32_t kOversampleSpacingMicros = 100;

}  // namespace

Esp32AnalogChannel::Esp32AnalogChannel(std::uint8_t pin,
                                       double referenceVolts,
                                       double dividerRatio,
                                       std::uint8_t oversampling)
    : pin_(pin),
      referenceVolts_(referenceVolts),
      dividerRatio_(dividerRatio),
      oversampling_(oversampling < 1 ? 1 : oversampling) {
}

void Esp32AnalogChannel::begin() {
    analogReadResolution(12);
    // 11 dB de atenuacion: es lo que permite leer hasta ~3.3 V.
    analogSetPinAttenuation(pin_, ADC_11db);
}

double Esp32AnalogChannel::readVoltage() {
    delayMicroseconds(kReadSpacingMicros);

    std::uint32_t accumulator = 0;
    for (std::uint8_t i = 0; i < oversampling_; ++i) {
        accumulator += analogRead(pin_);
        delayMicroseconds(kOversampleSpacingMicros);
    }

    const double averageRaw = static_cast<double>(accumulator) / static_cast<double>(oversampling_);
    const double measured = averageRaw / kAdcFullScale * referenceVolts_;
    return measured * dividerRatio_;
}

}  // namespace hardware
