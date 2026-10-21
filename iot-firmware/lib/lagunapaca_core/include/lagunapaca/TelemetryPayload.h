#pragma once

#include <string>

#include "lagunapaca/TelemetrySample.h"

namespace laguna {

/**
 * Arma el payload JSON exacto que espera el backend (contrato de la tesis):
 *
 * {"sample_id": "N1-3FA4C21B-17", "node_id": "N1", "ph": 7.2,
 *  "turbidity_ntu": 15.4, "temp_c": 18.5, "tds_ppm": 320}
 *
 * ``sample_id`` es opcional en el contrato del backend. Se omite del JSON si el
 * puntero es nulo o vacio; el nodo real siempre lo envia (ver
 * :func:`formatSampleId`). Los espacios tras los dos puntos son parte del
 * formato documentado; el servidor los ignora. Los decimales se recortan segun
 * la precision fisica de cada sonda.
 */
std::string buildTelemetryPayload(const char* nodeId, const TelemetrySample& sample,
                                  const char* sampleId = nullptr);

}  // namespace laguna
