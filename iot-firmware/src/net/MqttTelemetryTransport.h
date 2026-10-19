#pragma once

#include <cstdint>
#include <string>

#include <MQTT.h>
#include <WiFiClient.h>

#include "lagunapaca/TelemetryPorts.h"

namespace net {

/**
 * Transporte de telemetria por MQTT contra el broker.
 *
 * Publica en el mismo topico que consume el backend (`app.mqtt.topic`) y con
 * QoS 1, coherente con el `MQTT_QOS` del backend: el nodo solo da la muestra
 * por entregada cuando el broker confirma con PUBACK.
 *
 * No usa JWT: el canal de dispositivo se autentica con las credenciales del
 * broker, igual que la ingesta MQTT del backend.
 */
class MqttTelemetryTransport : public laguna::TelemetryTransport {
public:
    MqttTelemetryTransport(const char* host,
                           std::uint16_t port,
                           const char* username,
                           const char* password,
                           const char* topic,
                           const char* nodeId);

    /**
     * Calcula el client id a partir de la MAC de la placa y configura el broker.
     *
     * Se hace aqui y no en el constructor porque la MAC del ESP32 no esta
     * disponible con garantias durante la inicializacion estatica.
     */
    void begin();

    bool send(const std::string& payload) override;

    /** Identificador con el que el nodo aparece en el broker. */
    const std::string& clientId() const { return clientId_; }

private:
    /** Abre la sesion MQTT; @return {@code true} si el broker respondio CONNACK. */
    bool connect();

    /** Cierra la sesion y el socket, para no dejar el TCP colgado entre ciclos. */
    void disconnectQuietly();

    WiFiClient socket_;
    MQTTClient client_;
    std::string host_;
    std::uint16_t port_;
    std::string username_;
    std::string password_;
    std::string topic_;
    std::string nodeId_;
    std::string clientId_;
};

}  // namespace net
