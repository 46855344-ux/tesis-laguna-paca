package pe.edu.continental.lagunapaca.telemetry.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload emitido por el firmware ESP32:
 * <pre>
 * {"sample_id": "N1-3FA4C21B-17", "node_id": "N1", "ph": 7.2,
 *  "turbidity_ntu": 15.4, "temp_c": 18.5, "tds_ppm": 320}
 * </pre>
 *
 * <p>{@code sample_id} es <strong>opcional</strong> y aditivo: los clientes que
 * no lo envían siguen funcionando. Cuando está presente, la ingesta es
 * idempotente (RN-09): un reintento HTTP o un duplicado de MQTT QoS 1 devuelve
 * la muestra ya guardada en lugar de crear otra.</p>
 */
public record TelemetryRequest(

        @JsonProperty("node_id")
        @NotBlank(message = "node_id es obligatorio")
        @Size(max = 32, message = "node_id no debe exceder 32 caracteres")
        String nodeId,

        @JsonProperty("ph")
        @NotNull(message = "ph es obligatorio")
        @DecimalMin(value = "0.0", message = "ph debe ser >= 0.0")
        @DecimalMax(value = "14.0", message = "ph debe ser <= 14.0")
        Double ph,

        @JsonProperty("turbidity_ntu")
        @NotNull(message = "turbidity_ntu es obligatorio")
        @DecimalMin(value = "0.0", message = "turbidity_ntu debe ser >= 0.0")
        Double turbidityNtu,

        @JsonProperty("temp_c")
        @NotNull(message = "temp_c es obligatorio")
        @DecimalMin(value = "-5.0", message = "temp_c debe ser >= -5.0")
        @DecimalMax(value = "60.0", message = "temp_c debe ser <= 60.0")
        Double tempC,

        @JsonProperty("tds_ppm")
        @NotNull(message = "tds_ppm es obligatorio")
        @DecimalMin(value = "0.0", message = "tds_ppm debe ser >= 0.0")
        Double tdsPpm,

        @JsonProperty("sample_id")
        @Size(max = 64, message = "sample_id no debe exceder 64 caracteres")
        String sampleId
) {

    /**
     * Constructor compatible con el payload histórico (sin {@code sample_id}).
     * Lo usan los tests y cualquier llamada interna que no necesite idempotencia.
     */
    public TelemetryRequest(String nodeId, Double ph, Double turbidityNtu, Double tempC, Double tdsPpm) {
        this(nodeId, ph, turbidityNtu, tempC, tdsPpm, null);
    }
}
