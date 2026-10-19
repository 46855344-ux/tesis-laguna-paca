#pragma once

#include <cstdint>

namespace net {

/**
 * Conecta el ESP32 a la red WiFi en modo estacion.
 *
 * @return {@code true} si alcanzo el estado WL_CONNECTED dentro del tiempo dado.
 */
bool connect(const char* ssid, const char* password, std::uint32_t timeoutMillis);

}  // namespace net
