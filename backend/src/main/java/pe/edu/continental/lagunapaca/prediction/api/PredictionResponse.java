package pe.edu.continental.lagunapaca.prediction.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import pe.edu.continental.lagunapaca.prediction.domain.Prediction;
import pe.edu.continental.lagunapaca.prediction.domain.PredictionStatus;

import java.time.Instant;
import java.util.UUID;

/** Predicción lista para la API y el panel. */
public record PredictionResponse(

        Long id,

        @JsonProperty("model_run_id")
        UUID modelRunId,

        @JsonProperty("node_id")
        String nodeId,

        String variable,

        @JsonProperty("horizon_hours")
        int horizonHours,

        @JsonProperty("generated_at")
        Instant generatedAt,

        @JsonProperty("target_at")
        Instant targetAt,

        @JsonProperty("predicted_value")
        double predictedValue,

        @JsonProperty("model_version")
        String modelVersion,

        @JsonProperty("data_source")
        String dataSource,

        PredictionStatus status,

        @JsonProperty("observed_value")
        Double observedValue,

        @JsonProperty("error")
        Double error,

        @JsonProperty("evaluated_at")
        Instant evaluatedAt
) {

    public static PredictionResponse from(Prediction prediction) {
        return new PredictionResponse(
                prediction.getId(),
                prediction.getModelRunId(),
                prediction.getNodeId(),
                prediction.getVariable(),
                prediction.getHorizonHours(),
                prediction.getGeneratedAt(),
                prediction.getTargetAt(),
                prediction.getPredictedValue(),
                prediction.getModelVersion(),
                prediction.getDataSource(),
                prediction.getStatus(),
                prediction.getObservedValue(),
                prediction.getError(),
                prediction.getEvaluatedAt());
    }
}
