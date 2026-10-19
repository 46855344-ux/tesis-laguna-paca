#pragma once

#include <cstddef>
#include <cstdint>
#include <string>

namespace laguna {

/**
 * Longitud maxima del identificador de cliente MQTT.
 *
 * El estandar MQTT 3.1.1 obliga al broker a aceptar client ids de entre 1 y 23
 * bytes; los mas largos son opcionales y varios brokers reales los rechazan.
 * Se reserva ese limite para no depender del broker elegido.
 */
constexpr std::size_t kMaxClientIdLength = 23;

/**
 * Construye el identificador de cliente MQTT de una placa.
 *
 * Formato: {@code lp-<8 hex de la MAC>-<nodeId>}, recortado a
 * @ref kMaxClientIdLength caracteres.
 *
 * Decisiones:
 *  - El id debe ser **unico por placa**: el broker expulsa al cliente anterior
 *    cuando se conecta otro con el mismo id, asi que dos nodos con el mismo
 *    identificador se desconectarian en bucle.
 *  - Por eso el hex de la MAC se reserva antes de recortar y el node id es lo
 *    primero que se sacrifica: la unicidad no es negociable, lo legible si.
 *  - Los caracteres no alfanumericos del node id se reemplazan por guiones y se
 *    recortan los guiones finales, para que el id sea seguro en cualquier broker.
 *
 * @param nodeId identificador del nodo, por ejemplo "N1" (puede ser nullptr).
 * @param chipId MAC del ESP32 tal como la reporta @c ESP.getEfuseMac().
 * @return un id determinista, nunca vacio y de a lo sumo 23 caracteres.
 */
std::string buildMqttClientId(const char* nodeId, std::uint64_t chipId);

}  // namespace laguna
