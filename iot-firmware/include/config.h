#pragma once

#include <cstddef>
#include <cstdint>

// ---------------------------------------------------------------------------
// Configuracion del nodo sensor.
//
// ATENCION: los valores marcados como "ajustar" dependen del cableado real y de
// la calibracion en laboratorio con soluciones buffer (pH 4.01 / 7.00 / 10.01).
// No son constantes fisicas: hay que medirlos con el montaje definitivo.
// ---------------------------------------------------------------------------

/** Identificador del nodo, el mismo que registra el backend. */
#define NODE_ID "N1"

// --- Pines (ESP32 DevKit v1, 34 pines GPIO) ---
// Los tres canales analogicos DEBEN ir a ADC1 (GPIO 32-39): ADC2 deja de
// funcionar mientras el WiFi esta activo.
constexpr std::uint8_t PIN_PH = 34;         // ADC1_CH6
constexpr std::uint8_t PIN_TURBIDITY = 35;  // ADC1_CH7
constexpr std::uint8_t PIN_TDS = 32;        // ADC1_CH4
constexpr std::uint8_t PIN_DS18B20 = 4;     // 1-Wire (resistencia de 4.7k a 3V3)

// --- ADC de 12 bits ---
constexpr double ADC_REFERENCE_VOLTS = 3.3;
constexpr std::uint8_t ADC_OVERSAMPLING = 8;

// Divisores de tension (factor por el que se multiplica la tension medida).
// Ajustar a los valores reales de las resistencias. El TSW-20M se alimenta a
// 5 V y su salida alcanza ~4.5 V, por encima del maximo del ADC.
constexpr double PH_DIVIDER_RATIO = 1.0;
constexpr double TURBIDITY_DIVIDER_RATIO = 1.56;
constexpr double TDS_DIVIDER_RATIO = 1.0;

// --- Canal de telemetria ---
// HTTP POST contra el backend propio es el canal por defecto. Para publicar por
// MQTT en su lugar:
//   - con PlatformIO:  pio run -e esp32dev-mqtt
//   - con Arduino IDE: descomentar la linea siguiente
// #define LAGUNA_TELEMETRY_MQTT

/** Puerto del broker MQTT. */
constexpr std::uint16_t MQTT_BROKER_PORT = 1883;

/** Debe coincidir con `app.mqtt.topic` del backend (MQTT_TOPIC). */
constexpr const char* MQTT_TOPIC = "laguna-paca/telemetry";

// --- Muestreo y ahorro de energia ---
constexpr std::size_t SAMPLES_PER_READING = 3;               // w del filtro de media movil
constexpr std::uint32_t SAMPLE_INTERVAL_SECONDS = 900;       // 15 min (ajustar a la autonomia solar)
constexpr std::uint32_t MINIMUM_SLEEP_SECONDS = 10;
constexpr std::uint32_t WIFI_TIMEOUT_MILLIS = 15000;

// --- Puerto serie ---
constexpr std::uint32_t SERIAL_BAUD = 115200;
constexpr std::uint32_t SERIAL_WARMUP_MILLIS = 200;

// --- Calibracion del pH (ajustar con las soluciones buffer) ---
constexpr double PH_VOLTAGE_AT_4 = 3.0;
constexpr double PH_VOLTAGE_AT_7 = 2.5;
constexpr double PH_VOLTAGE_AT_10 = 2.0;
