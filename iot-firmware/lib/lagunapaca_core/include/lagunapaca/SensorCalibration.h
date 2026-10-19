#pragma once

namespace laguna {

/**
 * Calibracion lineal del modulo de pH PH-4502C (tension -> pH).
 *
 * La plantilla de tesis calibra en laboratorio con soluciones buffer patron
 * 4.01, 7.00 y 10.01; @ref fromBuffers ajusta la recta por minimos cuadrados
 * sobre las tres lecturas, de modo que tolera sensores no ideales.
 */
class PhCalibration {
public:
    static constexpr double kBuffer4 = 4.01;
    static constexpr double kBuffer7 = 7.00;
    static constexpr double kBuffer10 = 10.01;

    PhCalibration(double slope, double intercept);

    /**
     * Ajusta la recta a partir de las tensiones medidas en los buffers 4.01,
     * 7.00 y 10.01 (minimos cuadrados). Si las tres tensiones coinciden la
     * recta queda indeterminada y se devuelve el neutro (pendiente 0, pH 7.00).
     */
    static PhCalibration fromBuffers(double voltageAt4, double voltageAt7, double voltageAt10);

    /**
     * Convierte una tension a pH. NO recorta el resultado: una lectura fuera de
     * rango suele significar electrodo desconectado o deteriorado, y debe
     * descartarse en la validacion en lugar de enviarse como un valor falso.
     */
    double toPh(double voltage) const;

    double slope() const { return slope_; }
    double intercept() const { return intercept_; }

private:
    double slope_;
    double intercept_;
};

/**
 * Calibracion cuadratica del sensor optico de turbidez TSW-20M / ST100:
 * {@code NTU = a*V^2 + b*V + c}. Devuelve el valor crudo del polinomio (puede
 * ser negativo si la tension esta fuera del rango util del sensor).
 */
class TurbidityCalibration {
public:
    TurbidityCalibration(double a, double b, double c);

    /** Coeficientes publicados para el TSW-20M / SEN0189 alimentado a 5 V. */
    static TurbidityCalibration tsw20m();

    /** Convierte la tension de salida a NTU (sin recortar; ver @ref PhCalibration::toPh). */
    double toNtu(double voltage) const;

private:
    double a_;
    double b_;
    double c_;
};

/**
 * Calibracion del sensor Gravity TDS V1.0 (tension -> ppm) con compensacion
 * por temperatura, segun la formula de DFRobot:
 *
 *   Vc  = V / (1 + 0.02 * (T - 25))
 *   TDS = a*Vc^3 + b*Vc^2 + c*Vc
 *
 * Los coeficientes son parametrizables para poder testear con una respuesta
 * lineal; @ref gravityV1 reproduce el preset del sensor real.
 */
class TdsCalibration {
public:
    TdsCalibration(double a, double b, double c, double temperatureCoefficient);

    /** Preset DFRobot Gravity TDS V1.0 (0-1000 ppm). */
    static TdsCalibration gravityV1();

    /** Convierte tension y temperatura a ppm (sin recortar; ver @ref PhCalibration::toPh). */
    double toPpm(double voltage, double temperatureC) const;

private:
    double a_;
    double b_;
    double c_;
    double temperatureCoefficient_;
};

}  // namespace laguna
