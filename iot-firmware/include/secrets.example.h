#pragma once

// ---------------------------------------------------------------------------
// PLANTILLA de credenciales.
//
// Copia este archivo como `include/secrets.h` y completa los valores reales.
// `include/secrets.h` esta en .gitignore: nunca se versiona.
// ---------------------------------------------------------------------------

#define WIFI_SSID "MI_RED_WIFI"
#define WIFI_PASSWORD "MI_CLAVE_WIFI"

/** URL base del backend, sin barra final. Ej.: "http://192.168.1.100:8080". */
#define BACKEND_BASE_URL "http://192.168.1.100:8080"

/** Deben coincidir con APP_USER / APP_PASSWORD del backend. */
#define BACKEND_USER "admin"
#define BACKEND_PASSWORD "admin123"

// --- Broker MQTT (solo si se compila el canal MQTT) ---

/** Host o IP del broker, sin esquema ni puerto. */
#define MQTT_BROKER_HOST "192.168.1.100"
#define MQTT_USERNAME "nodo-laguna"
#define MQTT_PASSWORD "MI_CLAVE_BROKER"
