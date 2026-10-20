package pe.edu.continental.lagunapaca.telemetry.service;

import pe.edu.continental.lagunapaca.telemetry.domain.AlertCode;

import java.time.Instant;

/**
 * Filtros de la consulta histórica. Todos son opcionales ({@code null} = sin filtro).
 *
 * @param nodeId       nodo sensor
 * @param from         límite inferior inclusivo sobre {@code recordedAt}
 * @param to           límite superior inclusivo sobre {@code recordedAt}
 * @param alert        si la muestra tiene alerta
 * @param reason       texto buscado dentro del mensaje de los motivos
 * @param code         código exacto del motivo
 * @param acknowledged si la alerta ya fue atendida desde el panel; {@code null}
 *                     en la API REST, que no expone la gestión de alertas
 */
public record TelemetryQuery(String nodeId, Instant from, Instant to,
                             Boolean alert, String reason, AlertCode code,
                             Boolean acknowledged) {

    /** Consulta sin filtro de atención, para los llamadores que no lo necesitan. */
    public TelemetryQuery(String nodeId, Instant from, Instant to,
                          Boolean alert, String reason, AlertCode code) {
        this(nodeId, from, to, alert, reason, code, null);
    }
}
