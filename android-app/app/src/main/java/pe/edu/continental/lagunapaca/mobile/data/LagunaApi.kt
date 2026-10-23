package pe.edu.continental.lagunapaca.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant

class ApiException(
    val statusCode: Int,
    override val message: String
) : Exception(message) {
    val isUnauthorized: Boolean get() = statusCode == 401
}

/**
 * Cliente mínimo de la API pública. Usa JWT y no las rutas del panel:
 * el panel está protegido por sesión + CSRF y no es un contrato para móviles.
 */
class LagunaApi {

    suspend fun login(baseUrl: String, username: String, password: String): AuthSession {
        val body = JSONObject()
            .put("username", username)
            .put("password", password)
            .toString()

        val text = request(
            url = URL("${baseUrl.trimEnd('/')}/api/v1/auth/token"),
            method = "POST",
            body = body
        )
        val json = JSONObject(text)
        val token = json.optString("access_token")
        if (token.isBlank()) {
            throw ApiException(500, "El backend no devolvió un token válido.")
        }
        return AuthSession(
            token = token,
            expiresInSeconds = json.optLong("expires_in", 7200L)
        )
    }

    suspend fun telemetry(
        baseUrl: String,
        token: String,
        from: Instant? = null,
        alert: Boolean? = null,
        size: Int = 100
    ): List<WaterSample> {
        val parametros = buildList {
            from?.let { add("from=${URLEncoder.encode(it.toString(), "UTF-8")}") }
            alert?.let { add("alert=$it") }
            add("page=0")
            add("size=${size.coerceIn(1, 100)}")
            add("sort=recordedAt,desc")
        }
        val text = request(
            url = URL("${baseUrl.trimEnd('/')}/api/v1/telemetry?${parametros.joinToString("&")}"),
            method = "GET",
            token = token
        )
        val content = JSONObject(text).optJSONArray("content") ?: JSONArray()
        return buildList {
            for (indice in 0 until content.length()) {
                parseSample(content.optJSONObject(indice) ?: continue)?.let(::add)
            }
        }
    }

    private fun parseSample(json: JSONObject): WaterSample? {
        val nodeId = json.optString("node_id")
        val recordedAt = json.optString("recorded_at")
        if (nodeId.isBlank() || recordedAt.isBlank()) return null

        val instante = runCatching { Instant.parse(recordedAt) }.getOrNull() ?: return null
        val motivos = mutableListOf<AlertReason>()
        val reasons = json.optJSONArray("alert_reasons") ?: JSONArray()
        for (i in 0 until reasons.length()) {
            val motivo = reasons.optJSONObject(i) ?: continue
            val message = motivo.optString("message")
            if (message.isNotBlank()) {
                motivos += AlertReason(
                    code = motivo.optString("code"),
                    message = message
                )
            }
        }

        return WaterSample(
            id = json.optLong("id"),
            nodeId = nodeId,
            ph = json.optDouble("ph", 0.0),
            turbidityNtu = json.optDouble("turbidity_ntu", 0.0),
            tempC = json.optDouble("temp_c", 0.0),
            tdsPpm = json.optDouble("tds_ppm", 0.0),
            alert = json.optBoolean("alert", false),
            alertReasons = motivos,
            recordedAt = instante
        )
    }

    private suspend fun request(
        url: URL,
        method: String,
        token: String? = null,
        body: String? = null
    ): String = withContext(Dispatchers.IO) {
        val conexion = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            token?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

        try {
            if (body != null) {
                conexion.outputStream.use { salida ->
                    salida.write(body.toByteArray(Charsets.UTF_8))
                }
            }

            val codigo = conexion.responseCode
            val flujo = if (codigo in 200..299) conexion.inputStream else conexion.errorStream
            val texto = flujo?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (codigo !in 200..299) {
                throw ApiException(codigo, mensajeDeError(texto, codigo))
            }
            texto
        } finally {
            conexion.disconnect()
        }
    }

    private fun mensajeDeError(texto: String, codigo: Int): String {
        if (texto.isBlank()) return "Error HTTP $codigo"
        val mensaje = runCatching {
            JSONObject(texto).optString("message")
        }.getOrDefault("")
        return mensaje.ifBlank { "Error HTTP $codigo" }
    }
}
