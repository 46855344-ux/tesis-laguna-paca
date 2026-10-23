package pe.edu.continental.lagunapaca.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class DashboardMapperTest {

    private val ahora = Instant.parse("2026-10-21T12:00:00Z")

    @Test
    fun `resume la ventana y conserva las cuatro estaciones`() {
        val muestras = listOf(
            muestra("N1", "2026-10-21T11:00:00Z", ph = 7.2),
            muestra("N1", "2026-10-21T10:00:00Z", ph = 6.2, alerta = true),
            muestra("N2", "2026-10-21T11:30:00Z", ph = 9.4, alerta = true)
        )

        val resumen = DashboardMapper.summarize(muestras, ahora)

        assertEquals(3, resumen.samplesInWindow)
        assertEquals(2, resumen.alertsInWindow)
        assertEquals(2, resumen.stationsWithData)
        assertEquals(4, resumen.totalStations)
        assertEquals(33, resumen.compliancePercent)

        val n1 = resumen.stations.first { it.nodeId == "N1" }
        assertEquals(2, n1.sampleCount)
        assertEquals(1, n1.alertCount)
        assertEquals(StationStatus.DENTRO, n1.status)
        assertEquals(7.2, n1.latest?.ph ?: 0.0, 0.001)

        val n2 = resumen.stations.first { it.nodeId == "N2" }
        assertEquals(StationStatus.ALERTA, n2.status)

        val n4 = resumen.stations.first { it.nodeId == "N4" }
        assertEquals(StationStatus.SIN_DATOS, n4.status)
    }

    @Test
    fun `ignora muestras fuera de la ventana de 24 horas`() {
        val resumen = DashboardMapper.summarize(
            listOf(
                muestra("N1", "2026-10-19T11:00:00Z"),
                muestra("N2", "2026-10-21T11:00:00Z")
            ),
            ahora
        )

        assertEquals(1, resumen.samplesInWindow)
        assertEquals(1, resumen.stationsWithData)
        assertNull(resumen.stations.first { it.nodeId == "N1" }.latest)
    }

    @Test
    fun `sin muestras el cumplimiento es nulo y no se inventa un cien por ciento`() {
        val resumen = DashboardMapper.summarize(emptyList(), ahora)

        assertEquals(0, resumen.samplesInWindow)
        assertEquals(0, resumen.stationsWithData)
        assertNull(resumen.compliancePercent)
        assertEquals(4, resumen.stations.size)
    }

    private fun muestra(
        nodo: String,
        instante: String,
        ph: Double = 7.0,
        alerta: Boolean = false
    ) = WaterSample(
        id = instante.hashCode().toLong(),
        nodeId = nodo,
        ph = ph,
        turbidityNtu = 12.0,
        tempC = 18.0,
        tdsPpm = 320.0,
        alert = alerta,
        alertReasons = if (alerta) {
            listOf(AlertReason("PH_LOW", "pH menor que 6.5"))
        } else {
            emptyList()
        },
        recordedAt = Instant.parse(instante)
    )
}
