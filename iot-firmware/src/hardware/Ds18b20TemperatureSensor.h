#pragma once

#include <cstdint>

#include <DallasTemperature.h>
#include <OneWire.h>

#include "lagunapaca/TelemetryPorts.h"

namespace hardware {

/**
 * Sonda DS18B20 sumergible por 1-Wire.
 *
 * Devuelve NaN cuando la sonda no responde, de modo que la validacion del
 * nucleo descarte la muestra en lugar de publicar un -127 C ficticio.
 */
class Ds18b20TemperatureSensor : public laguna::TemperatureSensor {
public:
    explicit Ds18b20TemperatureSensor(std::uint8_t pin);

    /** Inicia el bus y fija la resolucion de conversion. Llamar desde setup(). */
    void begin();

    double readCelsius() override;

private:
    OneWire wire_;
    DallasTemperature sensors_;
};

}  // namespace hardware
