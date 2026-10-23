package pe.edu.continental.lagunapaca.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MainViewModelTest {

    @Test
    fun `agrega http cuando falta el esquema`() {
        assertEquals(
            "http://10.0.2.2:8080",
            normalizarBaseUrl(" 10.0.2.2:8080/ ")
        )
    }

    @Test
    fun `conserva https y quita la barra final`() {
        assertEquals(
            "https://api.example.com",
            normalizarBaseUrl("https://api.example.com/")
        )
    }

    @Test
    fun `rechaza una direccion vacia`() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizarBaseUrl("   ")
        }
    }
}
