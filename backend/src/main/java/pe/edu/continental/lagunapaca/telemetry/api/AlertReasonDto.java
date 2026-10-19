package pe.edu.continental.lagunapaca.telemetry.api;

import pe.edu.continental.lagunapaca.telemetry.domain.AlertCode;

/**
 * Vista de un motivo de alerta para la API:
 * {@code {"code": "PH_HIGH", "message": "pH 9.70 supera ..."}}.
 */
public record AlertReasonDto(AlertCode code, String message) {
}
