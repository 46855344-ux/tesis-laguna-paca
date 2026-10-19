#include "net/MqttTelemetryTransport.h"

#include <Arduino.h>

#include "lagunapaca/MqttIdentity.h"

namespace net {

namespace {

/**
 * Topico + payload del nodo ocupan ~150 B; el buffer de escritura debe superar
 * el paquete completo o el publish falla en silencio.
 */
constexpr int kReadBufferSize = 256;
constexpr int kWriteBufferSize = 512;

/** El nodo publica y se duerme: la sesion es corta y no necesita latido largo. */
constexpr int kKeepAliveSeconds = 15;

/** Igual que `app.mqtt.completion-timeout-ms` del backend: 5 s. */
constexpr int kTimeoutMillis = 5000;

/** Sin sesion persistente: el nodo se reconecta con clean session cada ciclo. */
constexpr bool kCleanSession = true;

/** QoS 1: al menos una entrega, coherente con MQTT_QOS=1 del backend. */
constexpr int kPublishQos = 1;

/**
 * Retenido en falso a proposito: un mensaje retenido se reproduce a cualquier
 * suscriptor nuevo como si fuera la lectura actual, y en una serie temporal eso
 * es un dato enganoso.
 */
constexpr bool kRetain = false;

}  // namespace

MqttTelemetryTransport::MqttTelemetryTransport(const char* host,
                                               std::uint16_t port,
                                               const char* username,
                                               const char* password,
                                               const char* topic,
                                               const char* nodeId)
    : client_(kReadBufferSize, kWriteBufferSize),
      host_(host == nullptr ? "" : host),
      port_(port),
      username_(username == nullptr ? "" : username),
      password_(password == nullptr ? "" : password),
      topic_(topic == nullptr ? "" : topic),
      nodeId_(nodeId == nullptr ? "" : nodeId) {
}

void MqttTelemetryTransport::begin() {
    clientId_ = laguna::buildMqttClientId(nodeId_.c_str(), ESP.getEfuseMac());
    client_.begin(host_.c_str(), port_, socket_);
    client_.setOptions(kKeepAliveSeconds, kCleanSession, kTimeoutMillis);
}

bool MqttTelemetryTransport::send(const std::string& payload) {
    if (!connect()) {
        return false;
    }

    // Con QoS 1 la libreria espera el PUBACK: el retorno ya significa entrega.
    const bool published = client_.publish(topic_.c_str(),
                                           payload.c_str(),
                                           static_cast<int>(payload.size()),
                                           kRetain,
                                           kPublishQos);

    disconnectQuietly();
    return published;
}

bool MqttTelemetryTransport::connect() {
    if (client_.connected()) {
        return true;
    }

    // Sin credenciales se omite el flag de usuario, en lugar de mandar un usuario
    // vacio (algunos brokers lo rechazan).
    if (username_.empty()) {
        return client_.connect(clientId_.c_str());
    }
    return client_.connect(clientId_.c_str(), username_.c_str(), password_.c_str());
}

void MqttTelemetryTransport::disconnectQuietly() {
    if (client_.connected()) {
        client_.disconnect();
    }
    socket_.stop();
}

}  // namespace net
