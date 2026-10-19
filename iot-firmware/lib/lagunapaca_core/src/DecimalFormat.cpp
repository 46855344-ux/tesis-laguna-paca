#include "lagunapaca/DecimalFormat.h"

#include <cstdio>

namespace laguna {

namespace {
constexpr int kMinDecimals = 0;
constexpr int kMaxDecimals = 6;
}  // namespace

std::string formatDecimal(double value, int maxDecimals) {
    if (maxDecimals < kMinDecimals) {
        maxDecimals = kMinDecimals;
    }
    if (maxDecimals > kMaxDecimals) {
        maxDecimals = kMaxDecimals;
    }

    char buffer[48];
    std::snprintf(buffer, sizeof(buffer), "%.*f", maxDecimals, value);
    std::string text(buffer);

    // Descarta los ceros finales y el punto que quede huerfano: "320.0" -> "320".
    if (text.find('.') != std::string::npos) {
        const std::size_t lastMeaningful = text.find_last_not_of('0');
        text.erase(lastMeaningful + 1);
        if (!text.empty() && text.back() == '.') {
            text.pop_back();
        }
    }

    // Evita el "-0" que produce el redondeo de valores negativos muy pequenos.
    if (text.empty() || text == "-0") {
        text = "0";
    }
    return text;
}

}  // namespace laguna
