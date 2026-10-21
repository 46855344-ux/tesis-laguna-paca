// ---------------------------------------------------------------------------
// Nodo sensor IoT de la Laguna de Paca - capa de Arduino.
//
// Este archivo es solo glue: inicializa el hardware, conecta el WiFi, delega el
// ciclo de medicion al nucleo portable (lib/lagunapaca_core) y vuelve a dormir.
// Toda la logica verificable vive en el nucleo; aqui no hay reglas de negocio
// (las alertas ECA-Agua son responsabilidad exclusiva del backend).
//
// Las credenciales NO viven en este archivo: copiar include/secrets.example.h
// como include/secrets.h (esta en .gitignore).
// ---------------------------------------------------------------------------

#include <Arduino.h>
#include <WiFi.h>
#include <esp_random.h>

#include <string>

#include "config.h"

#if __has_include("secrets.h")
#include "secrets.h"
#else
#warning "Falta include/secrets.h: se usan credenciales de ejemplo (copia secrets.example.h)."
#define WIFI_SSID "MI_RED_WIFI"
#define WIFI_PASSWORD "MI_CLAVE_WIFI"
#define BACKEND_BASE_URL "http://192.168.1.100:8080"
#define BACKEND_USER "admin"
#define BACKEND_PASSWORD "admin123"
#define MQTT_BROKER_HOST "192.168.1.100"
#define MQTT_USERNAME "nodo-laguna"
#define MQTT_PASSWORD "MI_CLAVE_BROKER"
#endif

#include "hardware/Ds18b20TemperatureSensor.h"
#include "hardware/Esp32AnalogChannel.h"
#include "net/WifiConnector.h"

#if defined(LAGUNA_TELEMETRY_MQTT)
#include "net/MqttTelemetryTransport.h"
#else
#include "hardware/HttpTelemetryTransport.h"
#endif

#include "lagunapaca/DeepSleepPolicy.h"
#include "lagunapaca/SampleIdentity.h"
#include "lagunapaca/TelemetryNode.h"

using laguna::DeepSleepPolicy;
using laguna::PhCalibration;
using laguna::TdsCalibration;
using laguna::TelemetryNode;
using laguna::TelemetryNodeConfig;
using laguna::TurbidityCalibration;

namespace {

// --- Sensores y transporte -------------------------------------------------

hardware::Esp32AnalogChannel g_phChannel(
        PIN_PH, ADC_REFERENCE_VOLTS, PH_DIVIDER_RATIO, ADC_OVERSAMPLING);
hardware::Esp32AnalogChannel g_turbidityChannel(
        PIN_TURBIDITY, ADC_REFERENCE_VOLTS, TURBIDITY_DIVIDER_RATIO, ADC_OVERSAMPLING);
hardware::Esp32AnalogChannel g_tdsChannel(
        PIN_TDS, ADC_REFERENCE_VOLTS, TDS_DIVIDER_RATIO, ADC_OVERSAMPLING);
hardware::Ds18b20TemperatureSensor g_temperatureSensor(PIN_DS18B20);

// Canal de telemetria: HTTP POST (por defecto) o MQTT, mismo contrato de payload.
#if defined(LAGUNA_TELEMETRY_MQTT)
net::MqttTelemetryTransport g_transport(MQTT_BROKER_HOST, MQTT_BROKER_PORT, MQTT_USERNAME,
                                        MQTT_PASSWORD, MQTT_TOPIC, NODE_ID);
#else
hardware::HttpTelemetryTransport g_transport(BACKEND_BASE_URL, BACKEND_USER, BACKEND_PASSWORD);
#endif

// --- Calibraciones (ajustar con el laboratorio; ver config.h) ---------------

const PhCalibration g_phCalibration =
        PhCalibration::fromBuffers(PH_VOLTAGE_AT_4, PH_VOLTAGE_AT_7, PH_VOLTAGE_AT_10);
const TurbidityCalibration g_turbidityCalibration = TurbidityCalibration::tsw20m();
const TdsCalibration g_tdsCalibration = TdsCalibration::gravityV1();

// --- Nodo ------------------------------------------------------------------

const TelemetryNodeConfig g_nodeConfig{NODE_ID, SAMPLES_PER_READING};
const DeepSleepPolicy g_sleepPolicy(SAMPLE_INTERVAL_SECONDS, MINIMUM_SLEEP_SECONDS);

TelemetryNode g_node(g_nodeConfig,
                     g_phChannel,
                     g_turbidityChannel,
                     g_tdsChannel,
                     g_temperatureSensor,
                     g_phCalibration,
                     g_turbidityCalibration,
                     g_tdsCalibration,
                     g_transport);

/** Contador de ciclos, en RTC para que sobreviva al Deep Sleep. */
RTC_DATA_ATTR std::uint32_t g_cycle = 0;

/**
 * Identidad de las muestras.
 *
 * ``g_bootId`` se renueva solo en arranque en frio (cuando la magia de RTC no
 * esta puesta) y ``g_sequence`` avanza solo cuando el transporte confirma la
 * entrega. Asi un reintento reutiliza el mismo ``sample_id`` y el backend no
 * duplica la muestra; tras un corte de energia la secuencia vuelve a cero pero
 * el ``bootId`` es nuevo, de modo que no choca con identificadores anteriores.
 */
RTC_DATA_ATTR std::uint32_t g_bootId = 0;
RTC_DATA_ATTR std::uint32_t g_sequence = 0;
RTC_DATA_ATTR std::uint32_t g_bootMagic = 0;
constexpr std::uint32_t kBootMagic = 0x4C504143;  // "LPAC"

void ensureSampleIdentity() {
    if (g_bootMagic == kBootMagic) {
        return;
    }
    g_bootMagic = kBootMagic;
    g_bootId = esp_random();
    g_sequence = 0;
}

const char* describeWakeCause() {
    switch (esp_sleep_get_wakeup_cause()) {
        case ESP_SLEEP_WAKEUP_TIMER:
            return "temporizador";
        case ESP_SLEEP_WAKEUP_UNDEFINED:
            return "arranque en frio";
        default:
            return "otra causa";
    }
}

void initializeHardware() {
    g_phChannel.begin();
    g_turbidityChannel.begin();
    g_tdsChannel.begin();
    g_temperatureSensor.begin();
}

void logOutcome(TelemetryNode::Outcome outcome) {
    switch (outcome) {
        case TelemetryNode::Outcome::Sent:
            Serial.println("[nodo] muestra enviada");
            break;
        case TelemetryNode::Outcome::InvalidSample:
            Serial.printf("[nodo] muestra descartada por implausible: %s\n",
                          laguna::describeValidity(g_node.lastValidity()));
            break;
        case TelemetryNode::Outcome::TransportFailed:
            Serial.println("[nodo] fallo el transporte: no se pudo entregar");
            break;
    }
}

}  // namespace

void setup() {
    Serial.begin(SERIAL_BAUD);
    delay(SERIAL_WARMUP_MILLIS);  // espera al adaptador USB-serie

    ++g_cycle;
    ensureSampleIdentity();
    const std::uint32_t activeStart = millis();

    Serial.printf("\n=== Nodo %s | ciclo #%lu | despertar por %s ===\n",
                  NODE_ID,
                  static_cast<unsigned long>(g_cycle),
                  describeWakeCause());

    // El token JWT se cachea en RTC: se le descuenta el tiempo dormido del ciclo
    // anterior para saber si sigue vigente sin consultar un reloj. En MQTT no hay
    // token: la sesion se autentica con las credenciales del broker.
#if defined(LAGUNA_TELEMETRY_MQTT)
    g_transport.begin();
    Serial.printf("[mqtt] client id %s | topico %s\n",
                  g_transport.clientId().c_str(), MQTT_TOPIC);
#else
    g_transport.noteElapsedSeconds(g_sleepPolicy.intervalSeconds());
#endif

    initializeHardware();

    const bool online = net::connect(WIFI_SSID, WIFI_PASSWORD, WIFI_TIMEOUT_MILLIS);
    if (!online) {
        Serial.println("[wifi] sin conexion: se omite el envio, la muestra se descarta");
    } else {
        const std::string sampleId = laguna::formatSampleId(NODE_ID, g_bootId, g_sequence);
        Serial.printf("[nodo] muestra %s\n", sampleId.c_str());
        const TelemetryNode::Outcome outcome = g_node.runOnce(sampleId.c_str());
        logOutcome(outcome);
        if (outcome == TelemetryNode::Outcome::Sent) {
            // Solo avanza cuando el servidor confirmo: el reintento reutiliza el id.
            ++g_sequence;
        }
        WiFi.disconnect(true);
    }

    const std::uint32_t activeMillis = millis() - activeStart;
    if (!g_node.lastPayload().empty()) {
        Serial.printf("[nodo] payload: %s\n", g_node.lastPayload().c_str());
    }
    Serial.printf("[nodo] tiempo activo %lu ms; durmiendo %lu s\n",
                  static_cast<unsigned long>(activeMillis),
                  static_cast<unsigned long>(g_sleepPolicy.sleepSeconds(activeMillis)));
    Serial.flush();

    esp_sleep_enable_timer_wakeup(g_sleepPolicy.sleepMicros(activeMillis));
    esp_deep_sleep_start();
}

/**
 * Nunca se ejecuta: el Deep Sleep reinicia el MCU y vuelve a entrar por setup().
 * Se deja vacio porque el framework de Arduino lo exige.
 */
void loop() {
}
