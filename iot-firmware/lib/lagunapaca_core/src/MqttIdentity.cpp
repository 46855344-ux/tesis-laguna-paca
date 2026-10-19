#include "lagunapaca/MqttIdentity.h"

namespace laguna {

namespace {

constexpr char kHexDigits[] = "0123456789abcdef";

/** Prefijo del proyecto (Laguna de Paca). */
constexpr const char* kPrefix = "lp-";

/** Longitud del hex de la placa: 8 caracteres. */
constexpr std::size_t kFingerprintLength = 8;

/**
 * Huella de la placa: los 4 bytes bajos de la MAC en hex.
 *
 * Se usan los bytes bajos y no los altos porque los tres primeros bytes de una
 * MAC Espressif son el OUI del fabricante, identico en todas las placas.
 */
std::string chipFingerprint(std::uint64_t chipId) {
    std::uint32_t low = static_cast<std::uint32_t>(chipId & 0xFFFFFFFFull);

    std::string fingerprint(kFingerprintLength, '0');
    for (std::size_t index = kFingerprintLength; index > 0; --index) {
        fingerprint[index - 1] = kHexDigits[low & 0x0Fu];
        low >>= 4;
    }
    return fingerprint;
}

/** Deja solo caracteres seguros para un client id, sin guiones finales. */
std::string sanitizeNodeId(const char* nodeId) {
    std::string sanitized;
    if (nodeId != nullptr) {
        for (const char* cursor = nodeId; *cursor != '\0'; ++cursor) {
            const char character = *cursor;
            const bool safe = (character >= 'a' && character <= 'z')
                    || (character >= 'A' && character <= 'Z')
                    || (character >= '0' && character <= '9');
            sanitized.push_back(safe ? character : '-');
        }
    }

    while (!sanitized.empty() && sanitized.back() == '-') {
        sanitized.pop_back();
    }
    return sanitized;
}

}  // namespace

std::string buildMqttClientId(const char* nodeId, std::uint64_t chipId) {
    // El hex de la placa se coloca antes del node id para que el recorte por
    // longitud nunca sacrifique la unicidad: solo se pierde legibilidad.
    std::string clientId = std::string(kPrefix) + chipFingerprint(chipId);

    const std::string safeNodeId = sanitizeNodeId(nodeId);
    if (!safeNodeId.empty()) {
        const std::size_t separatorLength = 1;
        const std::size_t fixedLength = clientId.size() + separatorLength;
        if (fixedLength < kMaxClientIdLength) {
            clientId.push_back('-');
            clientId.append(safeNodeId, 0, kMaxClientIdLength - fixedLength);
        }
    }
    return clientId;
}

}  // namespace laguna
