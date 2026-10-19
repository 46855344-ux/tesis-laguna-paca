package pe.edu.continental.lagunapaca.telemetry.service;

import pe.edu.continental.lagunapaca.telemetry.domain.AlertCode;

import java.time.Instant;

/**
 * Filtros de la consulta histórica. Todos son opcionales ({@code null} = sin filtro).
 *
 * @param nodeId nodo sensor
 * @param from   límite inferior inclusivo sobre {@code recordedAt}
 * @param to     límite superior inclusivo sobre {@code recordedAt}
 * @param alert  si la muestra tiene alerta
 * @param reason texto buscado dentro del mensaje de los motivos
 * @param code   código exacto del motivo
 */
public record TelemetryQuery(String nodeId, Instant from, Instant to,
                             Boolean alert, String reason, AlertCode code) {
}
