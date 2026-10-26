package pe.edu.continental.lagunapaca.mobile.data

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

/** Prueba el GeoJSON embebido y la proyección sin Android, como el panel web. */
class LagunaMapaTest {

    /** Caja casi real de la laguna: lon −75.52..−75.49, lat −11.746..−11.717. */
    private val cajaDePrueba = """
        {"fuente":"prueba","aviso":"Coordenadas provisionales",
         "laguna":[[-75.52,-11.717],[-75.49,-11.717],[-75.49,-11.746],[-75.52,-11.746],[-75.52,-11.717]],
         "estaciones":[
           {"id":"N1","nombre":"Norte","lat":-11.7170,"lon":-75.5070},
           {"id":"N2","nombre":"Este","lat":-11.7370,"lon":-75.4900},
           {"id":"N3","nombre":"Sur","lat":-11.7460,"lon":-75.4980},
           {"id":"N4","nombre":"Oeste","lat":-11.7240,"lon":-75.5200}]}
    """.trimIndent()

    /** Caja cuadrada de 3 grados por lado con estaciones N/S/E/O en los bordes. */
    private fun plantilla(latMin: Double, latMax: Double): String {
        fun n(valor: Double) = String.format(Locale.US, "%.5f", valor)
        val lonMin = 0.0
        val lonMax = 0.03
        val centro = (latMin + latMax) / 2.0
        val lonMitad = (lonMin + lonMax) / 2.0
        return """
        {"fuente":"prueba","aviso":"Coordenadas provisionales",
         "laguna":[[${n(lonMin)},${n(latMax)}],[${n(lonMax)},${n(latMax)}],[${n(lonMax)},${n(latMin)}],[${n(lonMin)},${n(latMin)}],[${n(lonMin)},${n(latMax)}]],
         "estaciones":[
           {"id":"N","nombre":"Norte","lat":${n(latMax)},"lon":${n(lonMitad)}},
           {"id":"S","nombre":"Sur","lat":${n(latMin)},"lon":${n(lonMitad)}},
           {"id":"E","nombre":"Este","lat":${n(centro)},"lon":${n(lonMax)}},
           {"id":"O","nombre":"Oeste","lat":${n(centro)},"lon":${n(lonMin)}}]}
        """.trimIndent()
    }

    @Test
    fun `parsea los pares lon lat del contorno y los campos separados de la estacion`() {
        val mapa = LagunaMapa.desdeJson(cajaDePrueba)
        assertEquals(5, mapa.contorno.size)
        assertEquals(-75.52, mapa.contorno.first().lon, 1e-9)
        assertEquals(-11.717, mapa.contorno.first().lat, 1e-9)
        assertEquals(listOf("N1", "N2", "N3", "N4"), mapa.estaciones.map { it.id })
        // Ojo: la estación NO usa pares; usa los campos lat y lon por separado.
        assertEquals(-11.7370, mapa.estaciones[1].lat, 1e-9)
        assertEquals(-75.4900, mapa.estaciones[1].lon, 1e-9)
        assertEquals("prueba", mapa.fuente)
        assertEquals("Coordenadas provisionales", mapa.aviso)
    }

    @Test
    fun `todo el contorno y las estaciones quedan dentro del cuadro`() {
        val pintado = LagunaMapa.desdeJson(cajaDePrueba).proyectar()
        pintado.contorno.forEach { punto ->
            assertTrue(punto.x in 0.0..1.0)
            assertTrue(punto.y in 0.0..1.0)
        }
        pintado.estaciones.forEach { estacion ->
            assertTrue(estacion.x in 0.0..1.0)
            assertTrue(estacion.y in 0.0..1.0)
        }
        assertTrue(pintado.aspecto in 0.3..3.0)
    }

    @Test
    fun `el norte queda arriba del sur y el este a la derecha del oeste`() {
        val pintado = LagunaMapa.desdeJson(cajaDePrueba).proyectar()
        val norte = pintado.estaciones.first { it.id == "N1" }
        val sur = pintado.estaciones.first { it.id == "N3" }
        val este = pintado.estaciones.first { it.id == "N2" }
        val oeste = pintado.estaciones.first { it.id == "N4" }
        assertTrue(norte.y < sur.y)
        assertTrue(este.x > oeste.x)
    }

    @Test
    fun `el eje este oeste se corrige con cos de la latitud central`() {
        // Dos cajas con los mismos grados de extensión: una en el ecuador y
        // otra en latitud alta (~57°). La separación física este-oeste respecto
        // de la norte-sur debe ser cos(latitud central); las latitudes van en
        // grados y la corrección las convierte a radianes, igual que el panel.
        val ecuador = LagunaMapa.desdeJson(plantilla(latMin = 0.0, latMax = 0.03)).proyectar()
        val andino = LagunaMapa.desdeJson(plantilla(latMin = 57.0, latMax = 57.03)).proyectar()

        fun proporcion(pintado: LagunaMapaPintado): Double {
            val este = pintado.estaciones.first { it.id == "E" }
            val oeste = pintado.estaciones.first { it.id == "O" }
            val norte = pintado.estaciones.first { it.id == "N" }
            val sur = pintado.estaciones.first { it.id == "S" }
            // El Canvas respeta el aspecto con aspectRatio; multiplicar el delta
            // en x por el aspecto lo pasa a la misma escala física que el delta en y.
            val brechaEsteOeste = (este.x - oeste.x) * pintado.aspecto
            val brechaNorteSur = sur.y - norte.y
            return brechaEsteOeste / brechaNorteSur
        }

        assertEquals(cos(Math.toRadians(0.015)), proporcion(ecuador), 1e-9)
        assertEquals(cos(Math.toRadians(57.015)), proporcion(andino), 1e-9)
    }
}
