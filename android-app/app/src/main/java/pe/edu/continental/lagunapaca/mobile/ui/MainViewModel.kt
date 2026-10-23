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
import pe.edu.continental.lagunapaca.mobile.data.DashboardMapper
import pe.edu.continental.lagunapaca.mobile.data.DashboardSummary
import pe.edu.continental.lagunapaca.mobile.data.LagunaApi
import pe.edu.continental.lagunapaca.mobile.data.WaterSample
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant
import java.time.temporal.ChronoUnit

enum class MobileScreen {
    TABLERO,
    ALERTAS
}

data class DashboardState(
    val isLoading: Boolean = false,
    val summary: DashboardSummary? = null,
    val recent: List<WaterSample> = emptyList(),
    val error: String? = null,
    val lastUpdated: Instant? = null
)

data class AlertsState(
    val isLoading: Boolean = false,
    val items: List<WaterSample> = emptyList(),
    val error: String? = null,
    val lastUpdated: Instant? = null
)

data class AppState(
    val baseUrl: String = BuildConfig.DEFAULT_API_URL,
    val username: String = "",
    val password: String = "",
    val isLoggingIn: Boolean = false,
    val loginError: String? = null,
    val session: AuthSession? = null,
    val screen: MobileScreen = MobileScreen.TABLERO,
    val dashboard: DashboardState = DashboardState(),
    val alerts: AlertsState = AlertsState()
) {
    val isAuthenticated: Boolean get() = session != null
}

fun normalizarBaseUrl(entrada: String): String {
    var valor = entrada.trim().trimEnd('/')
    require(valor.isNotBlank()) { "Escribe la dirección del backend." }
    if (!valor.startsWith("http://") && !valor.startsWith("https://")) {
        valor = "http://$valor"
    }
    return valor
}

class MainViewModel(
    private val api: LagunaApi = LagunaApi()
) : ViewModel() {

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

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
            _state.update {
                it.copy(loginError = "Escribe el usuario y la contraseña.")
            }
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
                _state.update {
                    it.copy(
                        isLoggingIn = false,
                        loginError = null,
                        session = sesion,
                        baseUrl = url,
                        password = "",
                        screen = MobileScreen.TABLERO,
                        dashboard = DashboardState(isLoading = true),
                        alerts = AlertsState()
                    )
                }
                cargarTablero()
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

    fun cargarTablero() {
        val actual = _state.value
        val sesion = actual.session ?: return

        viewModelScope.launch {
            _state.update {
                it.copy(dashboard = it.dashboard.copy(isLoading = true, error = null))
            }
            try {
                val muestras = api.telemetry(
                    baseUrl = actual.baseUrl,
                    token = sesion.token,
                    from = Instant.now().minus(24, ChronoUnit.HOURS),
                    size = 100
                )
                val resumen = DashboardMapper.summarize(muestras)
                _state.update {
                    it.copy(
                        dashboard = DashboardState(
                            isLoading = false,
                            summary = resumen,
                            recent = muestras
                                .sortedByDescending { muestra -> muestra.recordedAt }
                                .take(6),
                            error = null,
                            lastUpdated = Instant.now()
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                manejarError(e, destino = MobileScreen.TABLERO)
            }
        }
    }

    fun cargarAlertas() {
        val actual = _state.value
        val sesion = actual.session ?: return

        viewModelScope.launch {
            _state.update {
                it.copy(alerts = it.alerts.copy(isLoading = true, error = null))
            }
            try {
                val muestras = api.telemetry(
                    baseUrl = actual.baseUrl,
                    token = sesion.token,
                    alert = true,
                    size = 50
                )
                _state.update {
                    it.copy(
                        alerts = AlertsState(
                            isLoading = false,
                            items = muestras,
                            error = null,
                            lastUpdated = Instant.now()
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                manejarError(e, destino = MobileScreen.ALERTAS)
            }
        }
    }

    fun seleccionarPantalla(pantalla: MobileScreen) {
        _state.update { it.copy(screen = pantalla) }
        when (pantalla) {
            MobileScreen.TABLERO -> if (_state.value.dashboard.summary == null) cargarTablero()
            MobileScreen.ALERTAS -> cargarAlertas()
        }
    }

    fun refrescar() {
        when (_state.value.screen) {
            MobileScreen.TABLERO -> cargarTablero()
            MobileScreen.ALERTAS -> cargarAlertas()
        }
    }

    fun cerrarSesion() {
        _state.update {
            it.copy(
                session = null,
                password = "",
                screen = MobileScreen.TABLERO,
                dashboard = DashboardState(),
                alerts = AlertsState(),
                loginError = null
            )
        }
    }

    private fun manejarError(e: Exception, destino: MobileScreen) {
        if (e is ApiException && e.isUnauthorized) {
            _state.update {
                it.copy(
                    session = null,
                    password = "",
                    screen = MobileScreen.TABLERO,
                    dashboard = DashboardState(),
                    alerts = AlertsState(),
                    loginError = "La sesión venció. Vuelve a iniciar sesión."
                )
            }
            return
        }

        val mensaje = mensajeAmigable(e)
        _state.update {
            when (destino) {
                MobileScreen.TABLERO -> it.copy(
                    dashboard = it.dashboard.copy(isLoading = false, error = mensaje)
                )
                MobileScreen.ALERTAS -> it.copy(
                    alerts = it.alerts.copy(isLoading = false, error = mensaje)
                )
            }
        }
    }

    private fun mensajeParaLogin(e: Exception): String = when {
        e is ApiException && e.isUnauthorized -> "Usuario o contraseña incorrectos."
        else -> mensajeAmigable(e)
    }

    private fun mensajeAmigable(e: Exception): String = when (e) {
        is ApiException -> e.message
        is UnknownHostException -> "No se encontró el servidor. Revisa la dirección y la conexión."
        is ConnectException -> "No se pudo conectar con el backend. ¿Está arrancado?"
        is SocketTimeoutException -> "El backend tardó demasiado en responder."
        else -> e.message ?: "Ocurrió un error inesperado."
    }
}
