package pe.edu.continental.lagunapaca.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.edu.continental.lagunapaca.mobile.data.AlertsState
import pe.edu.continental.lagunapaca.mobile.data.ApiException
import pe.edu.continental.lagunapaca.mobile.data.LagunaApi
import pe.edu.continental.lagunapaca.mobile.data.SessionStore
import pe.edu.continental.lagunapaca.mobile.data.WaterSample
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant

class AlertsViewModel(
    private val api: LagunaApi = LagunaApi()
) : ViewModel() {

    private val _state = MutableStateFlow(AlertsState())
    val state: StateFlow<AlertsState> = _state.asStateFlow()

    fun cargar() {
        val sesion = SessionStore.sesion.value ?: return
        if (_state.value.isLoading) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val muestras = api.telemetry(
                    baseUrl = sesion.baseUrl,
                    token = sesion.token,
                    alert = true,
                    size = 50
                )
                _state.value = AlertsState(
                    isLoading = false,
                    items = muestras,
                    error = null,
                    lastUpdated = Instant.now()
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                manejarError(e)
            }
        }
    }

    fun refrescar() = cargar()

    private fun manejarError(e: Exception) {
        if (e is ApiException && e.isUnauthorized) {
            SessionStore.cerrarSesion()
            _state.value = AlertsState()
            return
        }
        val mensaje = mensajeAmigable(e)
        _state.update { it.copy(isLoading = false, error = mensaje) }
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
