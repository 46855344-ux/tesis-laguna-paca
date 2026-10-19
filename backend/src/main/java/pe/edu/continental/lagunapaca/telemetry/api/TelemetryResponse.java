package pe.edu.continental.lagunapaca.telemetry.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * Respuesta con la muestra persistida y el resultado de la evaluación ECA-Agua.
 */
public record TelemetryResponse(

        Long id,

        @JsonProperty("node_id")
        String nodeId,

        Double ph,

        @JsonProperty("turbidity_ntu")
        Double turbidityNtu,

        @JsonProperty("temp_c")
        Double tempC,

        @JsonProperty("tds_ppm")
        Double tdsPpm,

        boolean alert,

        @JsonProperty("alert_reasons")
        List<AlertReasonDto> alertReasons,

        @JsonProperty("recorded_at")
        Instant recordedAt
) {
}
