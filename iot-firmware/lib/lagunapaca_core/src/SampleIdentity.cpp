#include "lagunapaca/SampleIdentity.h"

#include <cstdio>

namespace laguna {

std::string formatSampleId(const char* nodeId, std::uint32_t bootId,
                           std::uint32_t sequence) {
    char buffer[64];
    std::snprintf(buffer, sizeof(buffer), "%s-%08lX-%lu",
                  nodeId == nullptr ? "" : nodeId,
                  static_cast<unsigned long>(bootId),
                  static_cast<unsigned long>(sequence));
    return std::string(buffer);
}

}  // namespace laguna
