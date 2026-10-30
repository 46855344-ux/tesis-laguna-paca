package pe.edu.continental.lagunapaca.mobile.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Sesión activa compartida por todos los ViewModels (MVVM). */
data class SesionActiva(
    val baseUrl: String,
    val token: String,
    val username: String
)

/**
 * Almacén de sesión de la app. Es la única fuente de verdad del estado de
 * autenticación: los ViewModels leen de aquí y la UI observa `sesion`.
 */
object SessionStore {
    private val _sesion = MutableStateFlow<SesionActiva?>(null)
    val sesion: StateFlow<SesionActiva?> = _sesion.asStateFlow()

    val isAuthenticated: Boolean get() = _sesion.value != null

    @Volatile
    var baseUrl: String = pe.edu.continental.lagunapaca.mobile.BuildConfig.DEFAULT_API_URL
        private set

    fun actualizarBaseUrl(valor: String) {
        baseUrl = valor
    }

    fun iniciarSesion(baseUrl: String, username: String, token: String) {
        this.baseUrl = baseUrl
        _sesion.value = SesionActiva(baseUrl, token, username)
    }

    fun cerrarSesion() {
        _sesion.value = null
    }
}
