#include "lagunapaca/SensorCalibration.h"

#include <cmath>

namespace laguna {

namespace {
constexpr int kBufferCount = 3;
constexpr double kDegenerateSlopeTolerance = 1e-12;
}  // namespace

// -------------------------------------------------------------- pH (PH-4502C)

PhCalibration::PhCalibration(double slope, double intercept)
    : slope_(slope), intercept_(intercept) {
}

PhCalibration PhCalibration::fromBuffers(double voltageAt4, double voltageAt7, double voltageAt10) {
    const double voltages[kBufferCount] = {voltageAt4, voltageAt7, voltageAt10};
    const double referencePh[kBufferCount] = {kBuffer4, kBuffer7, kBuffer10};

    double sumVoltage = 0.0;
    double sumPh = 0.0;
    double sumProduct = 0.0;
    double sumVoltageSquared = 0.0;
    for (int i = 0; i < kBufferCount; ++i) {
        sumVoltage += voltages[i];
        sumPh += referencePh[i];
        sumProduct += voltages[i] * referencePh[i];
        sumVoltageSquared += voltages[i] * voltages[i];
    }

    const double n = static_cast<double>(kBufferCount);
    const double denominator = n * sumVoltageSquared - sumVoltage * sumVoltage;
    if (std::fabs(denominator) < kDegenerateSlopeTolerance) {
        // Las tres tensiones coinciden: la recta es indeterminada (electrodo
        // desconectado). Se devuelve el neutro para no propagar un pH absurdo.
        return PhCalibration(0.0, kBuffer7);
    }

    const double slope = (n * sumProduct - sumVoltage * sumPh) / denominator;
    const double intercept = (sumPh - slope * sumVoltage) / n;
    return PhCalibration(slope, intercept);
}

double PhCalibration::toPh(double voltage) const {
    return slope_ * voltage + intercept_;
}

// ---------------------------------------------------------- turbidez (TSW-20M)

TurbidityCalibration::TurbidityCalibration(double a, double b, double c)
    : a_(a), b_(b), c_(c) {
}

TurbidityCalibration TurbidityCalibration::tsw20m() {
    return TurbidityCalibration(-1120.4, 5742.3, -4352.9);
}

double TurbidityCalibration::toNtu(double voltage) const {
    return a_ * voltage * voltage + b_ * voltage + c_;
}

// ---------------------------------------------------------- TDS (Gravity V1.0)

TdsCalibration::TdsCalibration(double a, double b, double c, double temperatureCoefficient)
    : a_(a), b_(b), c_(c), temperatureCoefficient_(temperatureCoefficient) {
}

TdsCalibration TdsCalibration::gravityV1() {
    // Mitad de los coeficientes publicados por DFRobot (la formula original
    // termina multiplicando el polinomio por 0.5).
    return TdsCalibration(66.71, -127.93, 428.695, 0.02);
}

double TdsCalibration::toPpm(double voltage, double temperatureC) const {
    const double temperatureFactor = 1.0 + temperatureCoefficient_ * (temperatureC - 25.0);
    const double compensated = voltage / temperatureFactor;
    return a_ * compensated * compensated * compensated +
           b_ * compensated * compensated +
           c_ * compensated;
}

}  // namespace laguna
