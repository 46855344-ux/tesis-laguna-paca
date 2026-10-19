package pe.edu.continental.lagunapaca.telemetry.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Muestra de calidad de agua emitida por un nodo sensor (ESP32).
 *
 * <p>Mapea la tabla {@code water_quality_sample} y su tabla hija
 * {@code alert_reason} (migraciones V1, V2 y V3).</p>
 */
@Entity
@Table(name = "water_quality_sample")
public class WaterQualitySample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "node_id", nullable = false, length = 32)
    private String nodeId;

    @Column(name = "ph", nullable = false)
    private double ph;

    @Column(name = "turbidity_ntu", nullable = false)
    private double turbidityNtu;

    @Column(name = "temp_c", nullable = false)
    private double tempC;

    @Column(name = "tds_ppm", nullable = false)
    private double tdsPpm;

    /**
     * Resumen booleano de {@link #alertReasons}, denormalizado a propósito para
     * poder filtrar "muestras con alerta" sin un JOIN.
     */
    @Column(name = "alert", nullable = false)
    private boolean alert;

    /** Motivos estructurados (código + mensaje), normalizados en {@code alert_reason}. */
    @ElementCollection
    @CollectionTable(
            name = "alert_reason",
            joinColumns = @JoinColumn(name = "sample_id", nullable = false))
    private List<AlertReason> alertReasons = new ArrayList<>();

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected WaterQualitySample() {
        // Requerido por JPA
    }

    public WaterQualitySample(String nodeId, double ph, double turbidityNtu,
                              double tempC, double tdsPpm, Instant recordedAt) {
        this.nodeId = nodeId;
        this.ph = ph;
        this.turbidityNtu = turbidityNtu;
        this.tempC = tempC;
        this.tdsPpm = tdsPpm;
        this.recordedAt = recordedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNodeId() {
        return nodeId;
    }

    public double getPh() {
        return ph;
    }

    public double getTurbidityNtu() {
        return turbidityNtu;
    }

    public double getTempC() {
        return tempC;
    }

    public double getTdsPpm() {
        return tdsPpm;
    }

    public boolean isAlert() {
        return alert;
    }

    public void setAlert(boolean alert) {
        this.alert = alert;
    }

    public List<AlertReason> getAlertReasons() {
        return List.copyOf(alertReasons);
    }

    public void setAlertReasons(List<AlertReason> alertReasons) {
        this.alertReasons = new ArrayList<>(alertReasons);
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Instant recordedAt) {
        this.recordedAt = recordedAt;
    }
}
