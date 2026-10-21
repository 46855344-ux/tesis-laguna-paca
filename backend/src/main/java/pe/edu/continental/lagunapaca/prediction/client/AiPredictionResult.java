package pe.edu.continental.lagunapaca.prediction.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/** Resultado de una inferencia, ya validado por {@link AiClient}. */
public record AiPredictionResult(
        String modelVersion,
        String dataSource,
        int horizonHours,
        Instant generatedAt,
        Instant targetAt,
        List<StationPrediction> predictions
) {

    public record StationPrediction(String station, double ph) {
    }

    /**
     * Respuesta cruda de FastAPI. Se valida antes de devolverla al dominio: un
     * contrato roto no puede convertirse en una predicción persistida.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Payload(
            @JsonProperty("model_version") String modelVersion,
            @JsonProperty("data_source") String dataSource,
            @JsonProperty("horizon_hours") Integer horizonHours,
            @JsonProperty("generated_at") Instant generatedAt,
            @JsonProperty("target_at") Instant targetAt,
            List<Station> predictions
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Station(String station, Double ph) {
    }

    /** Salud del servicio tal como la devuelve FastAPI. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record HealthPayload(
            String status,
            @JsonProperty("model_loaded") Boolean modelLoaded,
            @JsonProperty("model_version") String modelVersion,
            List<Integer> horizons,
            @JsonProperty("input_len") Integer inputLen,
            @JsonProperty("data_source") String dataSource,
            String detail
    ) {
    }

    /** Cuerpo de la petición de inferencia. */
    record PredictPayload(
            @JsonProperty("horizon_hours") int horizonHours,
            @JsonProperty("base_time") Instant baseTime,
            double[][][] input
    ) {
    }
}
