#include "net/WifiConnector.h"

#include <Arduino.h>
#include <WiFi.h>

namespace net {

bool connect(const char* ssid, const char* password, std::uint32_t timeoutMillis) {
    // Sin persistencia: evita reescribir la NVS de la flash en cada despertar
    // (los ciclos son frecuentes y la flash tiene un numero finito de escrituras).
    WiFi.persistent(false);
    WiFi.mode(WIFI_STA);
    WiFi.begin(ssid, password);

    const std::uint32_t start = millis();
    while (WiFi.status() != WL_CONNECTED && (millis() - start) < timeoutMillis) {
        delay(200);
        Serial.print('.');
    }
    Serial.println();

    return WiFi.status() == WL_CONNECTED;
}

}  // namespace net
