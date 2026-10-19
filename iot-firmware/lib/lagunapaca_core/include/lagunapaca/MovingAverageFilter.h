#pragma once

#include <cstddef>

namespace laguna {

/**
 * Filtro de media movil causal (la muestra actual y las anteriores).
 *
 * La plantilla de tesis fija w = 3 para eliminar el ruido de alta frecuencia de
 * los canales ADC del ESP32. El buffer es estatico para no reservar memoria
 * dinamica en el microcontrolador.
 *
 * Nota sobre el Deep Sleep: al reiniciarse la ESP32 el estado del filtro se
 * pierde, por eso el nodo reinicia la ventana en cada ciclo de despertar. Con
 * RTC memory (futuro) se podria suavizar entre ciclos.
 */
class MovingAverageFilter {
public:
    /** Tamano maximo de ventana soportado (buffer estatico). */
    static constexpr std::size_t kMaxWindow = 8;

    explicit MovingAverageFilter(std::size_t windowSize = 3);

    /** Agrega una muestra y devuelve la media de la ventana actual. */
    double add(double value);

    /** Media de las muestras disponibles ({@code 0.0} si aun no hay ninguna). */
    double average() const;

    /** Descarta las muestras acumuladas. */
    void reset();

    std::size_t windowSize() const { return window_; }

    /** Cantidad de muestras actualmente en la ventana. */
    std::size_t sampleCount() const { return count_; }

private:
    std::size_t window_;
    std::size_t next_;
    std::size_t count_;
    double buffer_[kMaxWindow];
    double sum_;
};

}  // namespace laguna
