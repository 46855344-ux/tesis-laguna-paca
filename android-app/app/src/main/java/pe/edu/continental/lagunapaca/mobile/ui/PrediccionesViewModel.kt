package pe.edu.continental.lagunapaca.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.edu.continental.lagunapaca.mobile.data.ApiException
import pe.edu.continental.lagunapaca.mobile.data.LagunaApi
import pe.edu.continental.lagunapaca.mobile.data.PhPrediction
import pe.edu.continental.lagunapaca.mobile.data.PrediccionesEstado
import pe.edu.continental.lagunapaca.mobile.data.SessionStore
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant

val horizontesValidos = listOf(1, 3, 6)

class PrediccionesViewModel(
    private val api: LagunaApi = LagunaApi()
) : ViewModel() {

    private val _state = MutableStateFlow(PrediccionesEstado())
    val state: StateFlow<PrediccionesEstado> = _state.asStateFlow()

    fun seleccionarHorizonte(horas: Int) {
        if (horas !in horizontesValidos) return
        _state.update { it.copy(horizonteHoras = horas) }
    }

    fun cargar() {
        val sesion = SessionStore.sesion.value ?: return
        if (_state.value.isLoading) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val filas = api.prediccionesRecientes(
                    baseUrl = sesion.baseUrl,
                    token = sesion.token
                )
                _state.value = PrediccionesEstado(
                    isLoading = false,
                    items = filas,
                    horizonteHoras = filas.firstOrNull()?.horizonHours
                        ?: _state.value.horizonteHoras,
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

    fun generar() {
        val sesion = SessionStore.sesion.value ?: return
        if (_state.value.isLoading) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val filas = api.generarPredicciones(
                    baseUrl = sesion.baseUrl,
                    token = sesion.token,
                    horizonHours = _state.value.horizonteHoras
                )
                _state.value = PrediccionesEstado(
                    isLoading = false,
                    items = filas,
                    horizonteHoras = _state.value.horizonteHoras,
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
            _state.value = PrediccionesEstado()
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
