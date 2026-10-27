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
import pe.edu.continental.lagunapaca.mobile.data.LagunaMapa
import pe.edu.continental.lagunapaca.mobile.data.LagunaMapaPintado
import pe.edu.continental.lagunapaca.mobile.data.PhPrediction
import pe.edu.continental.lagunapaca.mobile.data.WaterSample
import pe.edu.continental.lagunapaca.mobile.data.proyectar
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant
import java.time.temporal.ChronoUnit

enum class MobileScreen {
    TABLERO,
    ALERTAS,
    MAPA,
    PREDICCIONES
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

data class MapaEstado(
    val isLoading: Boolean = false,
    val map: LagunaMapaPintado? = null,
    val error: String? = null,
    val lastUpdated: Instant? = null
)

/** Horizontes que el artefacto actual tiene entrenados; otro valor responde 400. */
val horizontesValidos = listOf(1, 3, 6)

data class PrediccionesEstado(
    val isLoading: Boolean = false,
    val items: List<PhPrediction> = emptyList(),
    /** Horizonte elegido para la próxima generación (por defecto 6 h). */
    val horizonteHoras: Int = 6,
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
    val alerts: AlertsState = AlertsState(),
    val mapa: MapaEstado = MapaEstado(),
    val predicciones: PrediccionesEstado = PrediccionesEstado()
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

    private var lectorDeMapa: (suspend () -> String)? = null

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
                        alerts = AlertsState(),
                        predicciones = PrediccionesEstado()
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

    /**
     * El mapa es estático: la UI aporta cómo leer el GeoJSON embebido
     * (necesita un Context de Android) y el ViewModel parsea, proyecta
     * y guarda el resultado. Sin sesión ni llamadas al backend.
     */
    fun prepararMapa(leer: suspend () -> String) {
        lectorDeMapa = leer
        cargarMapa()
    }

    fun cargarMapa() {
        val lector = lectorDeMapa ?: return
        if (_state.value.mapa.isLoading) return

        viewModelScope.launch {
            _state.update {
                it.copy(mapa = it.mapa.copy(isLoading = true, error = null))
            }
            try {
                val texto = lector()
                val pintado = LagunaMapa.desdeJson(texto).proyectar()
                _state.update {
                    it.copy(
                        mapa = MapaEstado(
                            isLoading = false,
                            map = pintado,
                            error = null,
                            lastUpdated = Instant.now()
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                manejarError(e, destino = MobileScreen.MAPA)
            }
        }
    }

    /** Cambia el horizonte de la próxima generación; no consulta nada. */
    fun seleccionarHorizonte(horas: Int) {
        if (horas !in horizontesValidos) return
        _state.update {
            it.copy(predicciones = it.predicciones.copy(horizonteHoras = horas))
        }
    }

    /** Consulta GET /api/v1/predictions/latest; no inventa filas si no las hay. */
    fun cargarPredicciones() {
        val actual = _state.value
        val sesion = actual.session ?: return
        if (actual.predicciones.isLoading) return

        viewModelScope.launch {
            _state.update {
                it.copy(predicciones = it.predicciones.copy(isLoading = true, error = null))
            }
            try {
                val filas = api.prediccionesRecientes(
                    baseUrl = actual.baseUrl,
                    token = sesion.token
                )
                _state.update {
                    it.copy(
                        predicciones = PrediccionesEstado(
                            isLoading = false,
                            items = filas,
                            horizonteHoras = filas.firstOrNull()?.horizonHours
                                ?: it.predicciones.horizonteHoras,
                            error = null,
                            lastUpdated = Instant.now()
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                manejarError(e, destino = MobileScreen.PREDICCIONES)
            }
        }
    }

    /**
     * Genera una predicción nueva para las cuatro estaciones. El resultado
     * del POST reemplaza la lista: son las cuatro filas del lote recién
     * creado, con el mismo model_run_id.
     */
    fun generarPredicciones() {
        val actual = _state.value
        val sesion = actual.session ?: return
        if (actual.predicciones.isLoading) return

        viewModelScope.launch {
            _state.update {
                it.copy(predicciones = it.predicciones.copy(isLoading = true, error = null))
            }
            try {
                val filas = api.generarPredicciones(
                    baseUrl = actual.baseUrl,
                    token = sesion.token,
                    horizonHours = actual.predicciones.horizonteHoras
                )
                _state.update {
                    it.copy(
                        predicciones = PrediccionesEstado(
                            isLoading = false,
                            items = filas,
                            horizonteHoras = it.predicciones.horizonteHoras,
                            error = null,
                            lastUpdated = Instant.now()
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                manejarError(e, destino = MobileScreen.PREDICCIONES)
            }
        }
    }

    fun seleccionarPantalla(pantalla: MobileScreen) {
        _state.update { it.copy(screen = pantalla) }
        when (pantalla) {
            MobileScreen.TABLERO -> if (_state.value.dashboard.summary == null) cargarTablero()
            MobileScreen.ALERTAS -> cargarAlertas()
            MobileScreen.MAPA -> if (_state.value.dashboard.summary == null) cargarTablero()
            MobileScreen.PREDICCIONES -> cargarPredicciones()
        }
    }

    fun refrescar() {
        when (_state.value.screen) {
            MobileScreen.TABLERO -> cargarTablero()
            MobileScreen.ALERTAS -> cargarAlertas()
            MobileScreen.MAPA -> cargarMapa()
            MobileScreen.PREDICCIONES -> cargarPredicciones()
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
                mapa = MapaEstado(),
                predicciones = PrediccionesEstado(),
                loginError = null
            )
        }
        lectorDeMapa = null
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
                    predicciones = PrediccionesEstado(),
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
                MobileScreen.MAPA -> it.copy(
                    mapa = it.mapa.copy(isLoading = false, error = mensaje)
                )
                MobileScreen.PREDICCIONES -> it.copy(
                    predicciones = it.predicciones.copy(isLoading = false, error = mensaje)
                )
            }
        }
    }

    private fun mensajeParaLogin(e: Exception): String = when {
        e is ApiException && e.isUnauthorized -> "Usuario o contraseña incorrectos."
        else -> mensajeAmigable(e)
    }

    // Los subtipos de IOException van primero: si no, el genérico
    // los taparía (UnknownHostException también es IOException).
    private fun mensajeAmigable(e: Exception): String = when {
        e is ApiException -> e.message
        e is UnknownHostException ->
            "No se encontró el servidor. Revisa la dirección y la conexión."
        e is ConnectException ->
            "No se pudo conectar con el backend. ¿Está arrancado?"
        e is SocketTimeoutException ->
            "El backend tardó demasiado en responder."
        e is IOException -> "Problema de red inesperado. Revisa la conexión."
        else -> e.message ?: "Ocurrió un error inesperado."
    }
}
