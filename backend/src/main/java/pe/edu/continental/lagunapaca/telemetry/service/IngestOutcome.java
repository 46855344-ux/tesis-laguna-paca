package pe.edu.continental.lagunapaca.telemetry.service;

import pe.edu.continental.lagunapaca.telemetry.api.TelemetryResponse;

/**
 * Resultado de una ingesta.
 *
 * <p>{@code created=false} significa que la muestra ya existía para el
 * {@code sample_id} recibido. El controlador lo traduce a 200 en lugar de 201,
 * para que el cliente sepa que su reintento fue idempotente y no creó una fila
 * nueva.</p>
 */
public record IngestOutcome(TelemetryResponse response, boolean created) {

    public static IngestOutcome created(TelemetryResponse response) {
        return new IngestOutcome(response, true);
    }

    public static IngestOutcome duplicate(TelemetryResponse response) {
        return new IngestOutcome(response, false);
    }
}
