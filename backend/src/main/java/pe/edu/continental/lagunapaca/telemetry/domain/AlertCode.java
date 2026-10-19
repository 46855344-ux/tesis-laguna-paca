package pe.edu.continental.lagunapaca.telemetry.domain;

/**
 * Código estable de un motivo de alerta. Estable para lógica (iconos, reglas),
 * mientras que el texto legible vive en {@link AlertReason#getMessage()}.
 */
public enum AlertCode {
    /** pH por encima del límite superior ECA-Agua. */
    PH_HIGH,
    /** pH por debajo del límite inferior ECA-Agua. */
    PH_LOW
}
