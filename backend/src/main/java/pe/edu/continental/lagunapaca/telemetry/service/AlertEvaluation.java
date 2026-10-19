package pe.edu.continental.lagunapaca.telemetry.service;

import pe.edu.continental.lagunapaca.telemetry.domain.AlertReason;

import java.util.List;

/**
 * Resultado de evaluar una muestra contra los umbrales ECA-Agua.
 *
 * @param alert   {@code true} si la muestra excede algún límite
 * @param reasons motivos estructurados (vacío cuando {@code alert} es {@code false})
 */
public record AlertEvaluation(boolean alert, List<AlertReason> reasons) {

    private static final AlertEvaluation NONE = new AlertEvaluation(false, List.of());

    public static AlertEvaluation none() {
        return NONE;
    }

    public static AlertEvaluation of(List<AlertReason> reasons) {
        return new AlertEvaluation(!reasons.isEmpty(), List.copyOf(reasons));
    }
}
