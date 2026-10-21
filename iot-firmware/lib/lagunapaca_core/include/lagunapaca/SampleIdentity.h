#pragma once

#include <cstdint>
#include <string>

namespace laguna {

/**
 * Identificador de muestra que viaja en el payload (campo opcional
 * ``sample_id`` del backend).
 *
 * Formato: ``<nodeId>-<bootId en 8 hex>-<secuencia>``, por ejemplo
 * ``N1-3FA4C21B-17``.
 *
 * Por que no basta un contador:
 * - El contador de secuencia vive en RTC y sobrevive al Deep Sleep, pero se
 *   pierde en un arranque en frio. Si el identificador fuera solo
 *   ``<nodeId>-<secuencia>``, tras cada corte de energia la primera muestra
 *   reutilizaria un identificador ya usado y el backend la descartaria como
 *   duplicada (perderia una medicion real).
 * - ``bootId`` se genera con el RNG del ESP32 en cada arranque en frio y se
 *   conserva en RTC durante los despertares por temporizador. La probabilidad de
 *   repetirlo es despreciable.
 *
 * La secuencia solo avanza cuando el transporte confirma la entrega: si se cae
 * la red, el reintento reutiliza el mismo identificador, que es justo lo que
 * hace idempotente al reintento.
 *
 * La funcion es portable (sin Arduino) para poder probarla en el host.
 */
std::string formatSampleId(const char* nodeId, std::uint32_t bootId,
                           std::uint32_t sequence);

}  // namespace laguna
