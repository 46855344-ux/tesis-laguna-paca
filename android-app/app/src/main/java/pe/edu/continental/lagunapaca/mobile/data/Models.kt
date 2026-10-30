package pe.edu.continental.lagunapaca.mobile.data

import java.time.Instant

/** Sesión emitida por POST /api/v1/auth/token. */
data class AuthSession(
    val token: String,
    val expiresInSeconds: Long
)

/** Motivo redactado por el backend; la app no evalúa el pH. */
data class AlertReason(
    val code: String,
    val message: String
)

/** Muestra tal como la publica GET /api/v1/telemetry. */
data class WaterSample(
    val id: Long,
    val nodeId: String,
    val ph: Double,
    val turbidityNtu: Double,
    val tempC: Double,
    val tdsPpm: Double,
    val alert: Boolean,
    val alertReasons: List<AlertReason>,
    val recordedAt: Instant
)

enum class StationStatus(val label: String) {
    DENTRO("Dentro de la ECA"),
    ALERTA("Alerta ECA-Agua"),
    SIN_DATOS("Sin datos")
}

data class StationSummary(
    val nodeId: String,
    val latest: WaterSample?,
    val sampleCount: Int,
    val alertCount: Int
) {
    val status: StationStatus
        get() = when {
            latest == null -> StationStatus.SIN_DATOS
            latest.alert -> StationStatus.ALERTA
            else -> StationStatus.DENTRO
        }
}

data class DashboardSummary(
    val samplesInWindow: Int,
    val alertsInWindow: Int,
    val stationsWithData: Int,
    val totalStations: Int,
    val stations: List<StationSummary>
) {
    /** Porcentaje de muestras dentro de la ECA; null si no hay muestras. */
    val compliancePercent: Int?
        get() = if (samplesInWindow == 0) {
            null
        } else {
            ((samplesInWindow - alertsInWindow) * 100.0 / samplesInWindow).toInt()
        }
}

/** Estado persistido de la predicción; la app no la evalúa por su cuenta. */
enum class PredictionStatus(val label: String) {
    PENDING("Pendiente"),
    EVALUATED("Evaluada")
}

/** Estado del tablero. */
data class DashboardState(
    val isLoading: Boolean = false,
    val summary: DashboardSummary? = null,
    val recent: List<WaterSample> = emptyList(),
    val error: String? = null,
    val lastUpdated: Instant? = null
)

/** Estado de la lista de alertas. */
data class AlertsState(
    val isLoading: Boolean = false,
    val items: List<WaterSample> = emptyList(),
    val error: String? = null,
    val lastUpdated: Instant? = null
)

/** Estado del mapa. */
data class MapaEstado(
    val isLoading: Boolean = false,
    val map: LagunaMapaPintado? = null,
    val error: String? = null,
    val lastUpdated: Instant? = null
)

/** Estado de las predicciones. */
data class PrediccionesEstado(
    val isLoading: Boolean = false,
    val items: List<PhPrediction> = emptyList(),
    val horizonteHoras: Int = 6,
    val error: String? = null,
    val lastUpdated: Instant? = null
)
data class PhPrediction(
    val id: Long,
    val modelRunId: String,
    val nodeId: String,
    val variable: String,
    val horizonHours: Int,
    val generatedAt: Instant,
    val targetAt: Instant,
    val predictedValue: Double,
    val modelVersion: String,
    val dataSource: String,
    val status: PredictionStatus,
    val observedValue: Double?,
    /** predicted_value - observed_value en unidades de pH; solo si ya evaluó. */
    val errorDiff: Double?,
    val evaluatedAt: Instant?
)
