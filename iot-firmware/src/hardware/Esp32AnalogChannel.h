#pragma once

#include <cstdint>

#include "lagunapaca/TelemetryPorts.h"

namespace hardware {

/**
 * Canal analogico sobre el ADC de 12 bits del ESP32.
 *
 * Hace tres cosas que el nucleo portable no puede hacer porque son del
 * hardware:
 *  - sobre-muestreo para reducir el ruido del ADC,
 *  - correccion por el divisor de tension externo,
 *  - espaciado entre lecturas, para que el filtro de media movil del nodo vea
 *    ruido independiente en lugar de copias del mismo sample.
 */
class Esp32AnalogChannel : public laguna::AnalogChannel {
public:
    Esp32AnalogChannel(std::uint8_t pin,
                       double referenceVolts,
                       double dividerRatio,
                       std::uint8_t oversampling);

    /** Configura resolucion y atenuacion del ADC. Llamar desde setup(). */
    void begin();

    double readVoltage() override;

private:
    std::uint8_t pin_;
    double referenceVolts_;
    double dividerRatio_;
    std::uint8_t oversampling_;
};

}  // namespace hardware
