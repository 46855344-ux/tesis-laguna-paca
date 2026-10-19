#pragma once

#include <string>

namespace laguna {

/**
 * Puertos (interfaces) que el nodo usa para hablar con el mundo exterior.
 *
 * Vivien aparte de @c TelemetryNode para que los drivers de hardware puedan
 * implementarlos sin arrastrar toda la logica del nodo.
 */

/** Canal analogico (ADC) del ESP32. */
class AnalogChannel {
public:
    virtual ~AnalogChannel() = default;

    /** Tension medida en voltios, ya corregida por el divisor de tension. */
    virtual double readVoltage() = 0;
};

/** Sonda digital de temperatura DS18B20 (protocolo 1-Wire). */
class TemperatureSensor {
public:
    virtual ~TemperatureSensor() = default;

    /** Temperatura en grados Celsius, o NaN si la sonda no responde. */
    virtual double readCelsius() = 0;
};

/** Salida de telemetria (HTTP POST o MQTT). */
class TelemetryTransport {
public:
    virtual ~TelemetryTransport() = default;

    /** @return {@code true} si el mensaje se entrego. */
    virtual bool send(const std::string& payload) = 0;
};

}  // namespace laguna
