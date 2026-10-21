package pe.edu.continental.lagunapaca.prediction.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Petición de una ejecución de inferencia.
 *
 * <p>El modelo predice las cuatro estaciones a la vez (su tensor de entrada las
 * incluye todas), así que basta con indicar el horizonte.</p>
 */
public record PredictionRequest(

        @JsonProperty("horizon_hours")
        @NotNull(message = "horizon_hours es obligatorio")
        @Min(value = 1, message = "horizon_hours debe ser >= 1")
        @Max(value = 24, message = "horizon_hours debe ser <= 24")
        Integer horizonHours
) {
}
