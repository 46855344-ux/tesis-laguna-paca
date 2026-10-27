package pe.edu.continental.lagunapaca.mobile.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Parseo puro (sin Android) de las respuestas de /api/v1/predictions.
 * Cada fila es una predicción que el backend ya persistió: la app no
 * inventa valores ni evalúa nada por su cuenta.
 */
object PredictionMapper {

    /** Acepta un array JSON o un objeto único; omite las filas incompletas. */
    fun desdeJson(texto: String): List<PhPrediction> {
        val filas: JSONArray = if (texto.trimStart().startsWith("[")) {
            JSONArray(texto)
        } else {
            JSONArray().put(JSONObject(texto))
        }
        return buildList {
            for (indice in 0 until filas.length()) {
                val fila = filas.optJSONObject(indice) ?: continue
                desdeFila(fila)?.let(::add)
            }
        }
    }

    internal fun desdeFila(json: JSONObject): PhPrediction? {
        val nodeId = json.optString("node_id")
        if (nodeId.isBlank()) return null
        // Un instante sin zona o mal formado no es una predicción válida.
        val generatedAt = instante(json.optString("generated_at")) ?: return null
        val targetAt = instante(json.optString("target_at")) ?: return null

        return PhPrediction(
            id = json.optLong("id"),
            modelRunId = json.optString("model_run_id"),
            nodeId = nodeId,
            variable = json.optString("variable", "ph"),
            horizonHours = json.optInt("horizon_hours"),
            generatedAt = generatedAt,
            targetAt = targetAt,
            predictedValue = json.optDouble("predicted_value", Double.NaN),
            modelVersion = json.optString("model_version"),
            dataSource = json.optString("data_source"),
            status = estado(json.optString("status", "PENDING")),
            observedValue = doble(json.opt("observed_value")),
            errorDiff = doble(json.opt("error")),
            evaluatedAt = instante(json.optString("evaluated_at"))
        )
    }

    /** Estado desconocido no rompe la app: se muestra como Pendiente. */
    internal fun estado(nombre: String): PredictionStatus =
        PredictionStatus.entries.firstOrNull { it.name == nombre } ?: PredictionStatus.PENDING

    private fun instante(valor: String): Instant? =
        if (valor.isBlank()) null else runCatching { Instant.parse(valor) }.getOrNull()

    /** Distingue "ausente", JSON null y NaN de un número real. */
    private fun doble(valor: Any?): Double? {
        if (valor == null || valor == JSONObject.NULL) return null
        val numero = (valor as? Number)?.toDouble() ?: return null
        return if (numero.isNaN()) null else numero
    }
}
