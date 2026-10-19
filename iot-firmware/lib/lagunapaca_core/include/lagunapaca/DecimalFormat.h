#pragma once

#include <string>

namespace laguna {

/**
 * Formatea un decimal con como maximo `maxDecimals` decimales, descartando los
 * ceros finales y el punto sobrante: 7.20 -> "7.2", 320.0 -> "320", 0.0 -> "0".
 *
 * Se usa para armar el payload JSON con valores compactos, evitando el ruido de
 * la aritmetica de punto flotante (por ejemplo 0.32 * 1000.0).
 */
std::string formatDecimal(double value, int maxDecimals);

}  // namespace laguna
