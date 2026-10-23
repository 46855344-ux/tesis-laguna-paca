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
