package pe.edu.continental.lagunapaca.prediction.domain;

/** Estado de una predicción respecto a su evaluación posterior (RN-07). */
public enum PredictionStatus {
    /** Esperando la medición real del instante objetivo. */
    PENDING,
    /** Ya existe una muestra real en el objetivo y se calculó el error. */
    EVALUATED
}
