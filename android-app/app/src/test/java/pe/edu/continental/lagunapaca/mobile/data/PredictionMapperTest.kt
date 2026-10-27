package pe.edu.continental.lagunapaca.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * El parseo de /api/v1/predictions es la única lógica nueva con datos que
 * llegan por red: se prueba en la JVM con el org.json real (el android.jar
 * de tests trae stubs).
 */
class PredictionMapperTest {

    @Test
    fun `lee el lote de predicciones con una fila por estacion`() {
        val texto = """
            [
              {
                "id": 11,
                "model_run_id": "bdf8958f-8188-4cd8-a5a5-0350103690b2",
                "node_id": "N1",
                "variable": "ph",
                "horizon_hours": 6,
                "generated_at": "2026-10-26T03:11:20.425982Z",
                "target_at": "2026-10-26T09:11:58Z",
                "predicted_value": 5.85603064018187,
                "model_version": "stgnn-synthetic-s42-i24-e150",
                "data_source": "synthetic",
                "status": "PENDING",
                "observed_value": null,
                "error": null,
                "evaluated_at": null
              },
              {
                "id": 12,
                "model_run_id": "bdf8958f-8188-4cd8-a5a5-0350103690b2",
                "node_id": "N2",
                "variable": "ph",
                "horizon_hours": 6,
                "generated_at": "2026-10-26T03:11:20.425982Z",
                "target_at": "2026-10-26T09:11:58Z",
                "predicted_value": 5.9,
                "model_version": "stgnn-synthetic-s42-i24-e150",
                "data_source": "synthetic",
                "status": "PENDING",
                "observed_value": null,
                "error": null,
                "evaluated_at": null
              }
            ]
        """.trimIndent()

        val filas = PredictionMapper.desdeJson(texto)

        assertEquals(2, filas.size)
        val n1 = filas.first { it.nodeId == "N1" }
        assertEquals(11L, n1.id)
        assertEquals("bdf8958f", n1.modelRunId.take(8))
        assertEquals(6, n1.horizonHours)
        assertEquals(5.856, n1.predictedValue, 0.001)
        assertEquals("stgnn-synthetic-s42-i24-e150", n1.modelVersion)
        assertEquals("synthetic", n1.dataSource)
        assertEquals(PredictionStatus.PENDING, n1.status)
        assertEquals(null, n1.observedValue)
        assertEquals(null, n1.errorDiff)
        assertEquals(null, n1.evaluatedAt)
        assertEquals(
            Instant.parse("2026-10-26T09:11:58Z"),
            n1.targetAt
        )
    }

    @Test
    fun `una prediccion evaluada trae el observado y la desviacion`() {
        val texto = """
            {
              "id": 31,
              "model_run_id": "9f0d1111-2222-4333-8444-555566667777",
              "node_id": "N3",
              "variable": "ph",
              "horizon_hours": 1,
              "generated_at": "2026-10-25T18:00:00Z",
              "target_at": "2026-10-25T19:00:00Z",
              "predicted_value": 7.10,
              "model_version": "stgnn-synthetic-s42-i24-e150",
              "data_source": "field",
              "status": "EVALUATED",
              "observed_value": 7.32,
              "error": 0.22,
              "evaluated_at": "2026-10-25T19:02:00Z"
            }
        """.trimIndent()

        val filas = PredictionMapper.desdeJson(texto)

        assertEquals(1, filas.size)
        val evaluada = filas.single()
        assertEquals(PredictionStatus.EVALUATED, evaluada.status)
        assertEquals(7.32, evaluada.observedValue ?: 0.0, 0.001)
        assertEquals(0.22, evaluada.errorDiff ?: 0.0, 0.001)
        assertEquals(Instant.parse("2026-10-25T19:02:00Z"), evaluada.evaluatedAt)
    }

    @Test
    fun `omite las filas incompletas sin romper el lote`() {
        val texto = """
            [
              { "node_id": "N1", "target_at": "2026-10-26T09:11:58Z", "generated_at": "2026-10-26T03:11:20Z" },
              { "node_id": "", "generated_at": "2026-10-26T03:11:20Z", "target_at": "2026-10-26T09:11:58Z" },
              { "node_id": "N3", "target_at": "no-es-instante", "generated_at": "2026-10-26T03:11:20Z" }
            ]
        """.trimIndent()

        val filas = PredictionMapper.desdeJson(texto)

        assertEquals(listOf("N1"), filas.map { it.nodeId })
        assertEquals("ph", filas.single().variable)
    }

    @Test
    fun `un estado desconocido se muestra como pendiente`() {
        assertEquals(PredictionStatus.PENDING, PredictionMapper.estado("PENDING"))
        assertEquals(PredictionStatus.EVALUATED, PredictionMapper.estado("EVALUATED"))
        assertEquals(PredictionStatus.PENDING, PredictionMapper.estado("SIN_SENTIDO"))
    }
}
