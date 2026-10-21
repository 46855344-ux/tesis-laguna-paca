package pe.edu.continental.lagunapaca.prediction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Predicción del ST-GNN para una estación y un horizonte.
 *
 * <p>Vive en su propia tabla, nunca en {@code water_quality_sample}: una
 * predicción no es una medición (RN-06). El veredicto ambiental de una muestra
 * solo puede salir de un sensor real.</p>
 *
 * <p>Cuando llega una medición real cerca del instante objetivo, la predicción
 * se evalúa y guarda el valor observado y el error <em>sin modificarse a sí
 * misma</em>: el valor previsto queda intacto para poder auditar (RN-07).</p>
 */
@Entity
@Table(name = "prediction")
public class Prediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Agrupa las cuatro estaciones de una misma ejecución de inferencia. */
    @Column(name = "model_run_id", nullable = false)
    private UUID modelRunId;

    @Column(name = "node_id", nullable = false, length = 32)
    private String nodeId;

    /** Variable predicha; hoy solo {@code ph}, la que dispara alertas ECA-Agua. */
    @Column(name = "variable", nullable = false, length = 16)
    private String variable;

    @Column(name = "horizon_hours", nullable = false)
    private int horizonHours;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "target_at", nullable = false)
    private Instant targetAt;

    @Column(name = "predicted_value", nullable = false)
    private double predictedValue;

    @Column(name = "model_version", nullable = false, length = 64)
    private String modelVersion;

    /** {@code synthetic} o {@code field}: nunca se presenta uno como el otro. */
    @Column(name = "data_source", nullable = false, length = 16)
    private String dataSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PredictionStatus status;

    @Column(name = "observed_value")
    private Double observedValue;

    /** {@code predicho - observado}, en unidades de pH (RN-07). */
    @Column(name = "prediction_error")
    private Double error;

    @Column(name = "evaluated_at")
    private Instant evaluatedAt;

    protected Prediction() {
        // Requerido por JPA
    }

    public Prediction(UUID modelRunId, String nodeId, String variable, int horizonHours,
                      Instant generatedAt, Instant targetAt, double predictedValue,
                      String modelVersion, String dataSource) {
        this.modelRunId = modelRunId;
        this.nodeId = nodeId;
        this.variable = variable;
        this.horizonHours = horizonHours;
        this.generatedAt = generatedAt;
        this.targetAt = targetAt;
        this.predictedValue = predictedValue;
        this.modelVersion = modelVersion;
        this.dataSource = dataSource;
        this.status = PredictionStatus.PENDING;
    }

    /**
     * Registra la medición real del instante objetivo y el error cometido.
     *
     * <p>No toca {@link #predictedValue}: el valor previsto es evidencia y no se
     * reescribe.</p>
     */
    public void evaluate(double observedValue, Instant when) {
        this.observedValue = observedValue;
        this.error = predictedValue - observedValue;
        this.evaluatedAt = when;
        this.status = PredictionStatus.EVALUATED;
    }

    public Long getId() {
        return id;
    }

    public UUID getModelRunId() {
        return modelRunId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getVariable() {
        return variable;
    }

    public int getHorizonHours() {
        return horizonHours;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public Instant getTargetAt() {
        return targetAt;
    }

    public double getPredictedValue() {
        return predictedValue;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public String getDataSource() {
        return dataSource;
    }

    public PredictionStatus getStatus() {
        return status;
    }

    public Double getObservedValue() {
        return observedValue;
    }

    public Double getError() {
        return error;
    }

    public Instant getEvaluatedAt() {
        return evaluatedAt;
    }
}
