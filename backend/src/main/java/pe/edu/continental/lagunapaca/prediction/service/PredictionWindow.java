package pe.edu.continental.lagunapaca.prediction.service;

import java.time.Instant;
import java.util.List;

/**
 * Ventana temporal lista para el servicio de IA.
 *
 * @param estaciones orden de las estaciones en el eje de nodos
 * @param inputLen   horas (o muestras) de historia que ve el modelo
 * @param baseTime   instante de la última observación; el objetivo se calcula desde aquí
 * @param valores    tensor {@code [estaciones][tiempo][4 variables]} en unidades reales
 */
public record PredictionWindow(
        List<String> estaciones,
        int inputLen,
        Instant baseTime,
        double[][][] valores
) {
}
