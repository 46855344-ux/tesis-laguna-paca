package pe.edu.continental.lagunapaca.mobile.data

import java.time.Instant

/**
 * Resume las muestras de una ventana de 24 h para el tablero.
 *
 * No recalcula la regla ECA-Agua: usa el campo `alert` que ya persistió el
 * backend. Las estaciones se muestran siempre, aunque no tengan datos.
 */
object DashboardMapper {

    private val estacionesBase = listOf("N1", "N2", "N3", "N4")

    fun summarize(samples: List<WaterSample>, now: Instant = Instant.now()): DashboardSummary {
        val recientes = samples
            .filter { !it.recordedAt.isBefore(now.minusSeconds(24 * 60 * 60)) }
            .sortedByDescending { it.recordedAt }

        val porNodo = recientes.groupBy { it.nodeId }
        val ids = (estacionesBase + porNodo.keys.filterNot { it in estacionesBase }.sorted())

        val estaciones = ids.map { id ->
            val delNodo = porNodo[id].orEmpty()
            StationSummary(
                nodeId = id,
                latest = delNodo.firstOrNull(),
                sampleCount = delNodo.size,
                alertCount = delNodo.count { it.alert }
            )
        }

        return DashboardSummary(
            samplesInWindow = recientes.size,
            alertsInWindow = recientes.count { it.alert },
            stationsWithData = estaciones.count { it.latest != null },
            totalStations = estaciones.size,
            stations = estaciones
        )
    }
}
