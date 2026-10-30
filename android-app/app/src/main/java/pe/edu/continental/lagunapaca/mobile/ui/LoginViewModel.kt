package pe.edu.continental.lagunapaca.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.edu.continental.lagunapaca.mobile.BuildConfig
import pe.edu.continental.lagunapaca.mobile.data.ApiException
import pe.edu.continental.lagunapaca.mobile.data.AuthSession
import pe.edu.continental.lagunapaca.mobile.data.LagunaApi
import pe.edu.continental.lagunapaca.mobile.data.SessionStore
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

data class LoginState(
    val baseUrl: String = BuildConfig.DEFAULT_API_URL,
    val username: String = "",
    val password: String = "",
    val isLoggingIn: Boolean = false,
    val loginError: String? = null
)

class LoginViewModel(
    private val api: LagunaApi = LagunaApi()
) : ViewModel() {

    private val _state = MutableStateFlow(LoginState(baseUrl = SessionStore.baseUrl))
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun actualizarUrl(valor: String) {
        _state.update { it.copy(baseUrl = valor, loginError = null) }
    }

    fun actualizarUsuario(valor: String) {
        _state.update { it.copy(username = valor, loginError = null) }
    }

    fun actualizarPassword(valor: String) {
        _state.update { it.copy(password = valor, loginError = null) }
    }

    fun iniciarSesion() {
        val actual = _state.value
        if (actual.isLoggingIn) return

        val usuario = actual.username.trim()
        if (usuario.isBlank() || actual.password.isBlank()) {
            _state.update { it.copy(loginError = "Escribe el usuario y la contraseña.") }
            return
        }

        val url = try {
            normalizarBaseUrl(actual.baseUrl)
        } catch (e: IllegalArgumentException) {
            _state.update { it.copy(loginError = e.message) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoggingIn = true, loginError = null) }
            try {
                val sesion = api.login(url, usuario, actual.password)
                SessionStore.actualizarBaseUrl(url)
                SessionStore.iniciarSesion(url, usuario, sesion.token)
                _state.update {
                    it.copy(
                        isLoggingIn = false,
                        loginError = null,
                        password = ""
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoggingIn = false,
                        loginError = mensajeParaLogin(e)
                    )
                }
            }
        }
    }

    private fun mensajeParaLogin(e: Exception): String = when {
        e is ApiException && e.isUnauthorized -> "Usuario o contraseña incorrectos."
        else -> mensajeAmigable(e)
    }

    private fun mensajeAmigable(e: Exception): String = when {
        e is ApiException -> e.message
        e is UnknownHostException -> "No se encontró el servidor. Revisa la dirección y la conexión."
        e is ConnectException -> "No se pudo conectar con el backend. ¿Está arrancado?"
        e is SocketTimeoutException -> "El backend tardó demasiado en responder."
        e is IOException -> "Problema de red inesperado. Revisa la conexión."
        else -> e.message ?: "Ocurrió un error inesperado."
    }
}

fun normalizarBaseUrl(entrada: String): String {
    var valor = entrada.trim().trimEnd('/')
    require(valor.isNotBlank()) { "Escribe la dirección del backend." }
    if (!valor.startsWith("http://") && !valor.startsWith("https://")) {
        valor = "http://$valor"
    }
    return valor
}
