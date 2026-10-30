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
import pe.edu.continental.lagunapaca.mobile.data.LagunaMapa
import pe.edu.continental.lagunapaca.mobile.data.LagunaMapaPintado
import pe.edu.continental.lagunapaca.mobile.data.MapaEstado
import pe.edu.continental.lagunapaca.mobile.data.SessionStore
import pe.edu.continental.lagunapaca.mobile.data.proyectar
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant

class MapaViewModel : ViewModel() {

    private var lectorDeMapa: (suspend () -> String)? = null

    private val _state = MutableStateFlow(MapaEstado())
    val state: StateFlow<MapaEstado> = _state.asStateFlow()

    fun prepararMapa(leer: suspend () -> String) {
        lectorDeMapa = leer
        cargar()
    }

    fun cargar() {
        val lector = lectorDeMapa ?: return
        if (_state.value.isLoading) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val texto = lector()
                val pintado = LagunaMapa.desdeJson(texto).proyectar()
                _state.value = MapaEstado(
                    isLoading = false,
                    map = pintado,
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
            _state.value = MapaEstado()
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
        e is IOException -> "No se pudo leer el mapa embebido."
        else -> e.message ?: "Ocurrió un error inesperado."
    }
}
