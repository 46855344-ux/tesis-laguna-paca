package pe.edu.continental.lagunapaca.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.cos
import android.content.Context

/**
 * Mapa de la laguna embebido en la app (assets/geo/laguna-paca.json).
 *
 * Es una copia del mismo GeoJSON que sirve el panel
 * (backend/src/main/resources/geo/laguna-paca.json, generado con
 * ia-model/lagunapaca_gnn/stations.py): la app no descarga teselas ni
 * pide nada a Google.
 */
data class LagunaMapa(
    val fuente: String,
    val aviso: String?,
    /** Contorno del lago en grados. */
    val contorno: List<PuntoGeografico>,
    /** Estaciones del grafo; mismas coordenadas del modelo de IA. */
    val estaciones: List<EstacionGeografica>
) {
    companion object {
        const val RUTA_GEOJSON: String = "geo/laguna-paca.json"

        /**
         * El contorno viaja como pares [lon, lat] y las estaciones como campos
         * lat/lon separados; es la forma que ya usa el panel web.
         */
        fun desdeJson(texto: String): LagunaMapa {
            val raiz = JSONObject(texto)
            val arregloContorno = raiz.getJSONArray("laguna")
            require(arregloContorno.length() >= 3) {
                "El contorno de la laguna no tiene suficientes puntos."
            }
            val contorno = List(arregloContorno.length()) { indice ->
                val par = arregloContorno.getJSONArray(indice)
                PuntoGeografico(lon = par.getDouble(0), lat = par.getDouble(1))
            }
            val estaciones = if (raiz.has("estaciones")) {
                val arreglo = raiz.getJSONArray("estaciones")
                List(arreglo.length()) { indice ->
                    val estacion = arreglo.getJSONObject(indice)
                    EstacionGeografica(
                        id = estacion.getString("id"),
                        nombre = estacion.optString("nombre", estacion.getString("id")),
                        lat = estacion.getDouble("lat"),
                        lon = estacion.getDouble("lon")
                    )
                }
            } else {
                emptyList()
            }
            return LagunaMapa(
                fuente = raiz.optString("fuente"),
                aviso = raiz.optString("aviso").takeIf { it.isNotBlank() },
                contorno = contorno,
                estaciones = estaciones
            )
        }
    }
}

/** Punto del contorno en grados (longitud, latitud). */
data class PuntoGeografico(val lon: Double, val lat: Double)

/** Estación marcada sobre el mapa. */
data class EstacionGeografica(val id: String, val nombre: String, val lat: Double, val lon: Double)

/** Coordenadas normalizadas de pantalla: x, y en [0, 1] y el norte arriba. */
data class PuntoPantalla(val x: Double, val y: Double)

/** Estación ya proyectada, lista para el Canvas del mapa. */
data class EstacionPintada(val id: String, val nombre: String, val x: Double, val y: Double)

/** Mapa listo para dibujar: el Canvas solo multiplica por su tamaño. */
data class LagunaMapaPintado(
    val fuente: String,
    val aviso: String?,
    /** Proporción ancho/alto de la caja; el Canvas la respeta con aspectRatio. */
    val aspecto: Double,
    val contorno: List<PuntoPantalla>,
    val estaciones: List<EstacionPintada>
)

/**
 * Lee el GeoJSON embebido sin bloquear el hilo principal.
 */
suspend fun leerGeoJsonDeAssets(contexto: Context): String = withContext(Dispatchers.IO) {
    contexto.assets.open(LagunaMapa.RUTA_GEOJSON).bufferedReader().use { it.readText() }
}

/**
 * Proyección equirectangular corregida por cos(latitud), la misma del panel web.
 * La caja incluye el contorno y las estaciones; la corrección evita que el lago
 * se estire en el eje este-oeste. Devuelve x, y en [0, 1] con el norte arriba.
 */
fun LagunaMapa.proyectar(): LagunaMapaPintado {
    val longitudes = contorno.map { it.lon } + estaciones.map { it.lon }
    val latitudes = contorno.map { it.lat } + estaciones.map { it.lat }
    require(longitudes.isNotEmpty() && latitudes.isNotEmpty()) {
        "El GeoJSON del mapa no trae puntos."
    }
    val lonMin = longitudes.min()
    val lonMax = longitudes.max()
    val latMin = latitudes.min()
    val latMax = latitudes.max()

    // Corrección cos(latitud): los grados de longitud encogen hacia los polos.
    val cosCentro = cos(Math.toRadians((latMin + latMax) / 2.0))
    val anchoGeo = (lonMax - lonMin) * cosCentro
    val altoGeo = latMax - latMin
    val lado = maxOf(anchoGeo, altoGeo)
    require(lado > 0.0) { "La caja del mapa no tiene tamaño." }

    val margen = lado * 0.03
    val totalAncho = anchoGeo + 2 * margen
    val totalAlto = altoGeo + 2 * margen

    fun x(lon: Double): Double = ((lon - lonMin) * cosCentro + margen) / totalAncho
    // Norte arriba: a mayor latitud, menor y (la y del Canvas crece hacia abajo).
    fun y(lat: Double): Double = ((latMax - lat) + margen) / totalAlto

    return LagunaMapaPintado(
        fuente = fuente,
        aviso = aviso,
        aspecto = totalAncho / totalAlto,
        contorno = contorno.map { punto -> PuntoPantalla(x = x(punto.lon), y = y(punto.lat)) },
        estaciones = estaciones.map { estacion ->
            EstacionPintada(
                id = estacion.id,
                nombre = estacion.nombre,
                x = x(estacion.lon),
                y = y(estacion.lat)
            )
        }
    )
}
