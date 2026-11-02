package pe.edu.continental.lagunapaca.mobile.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import pe.edu.continental.lagunapaca.mobile.data.LagunaMapaPintado

/**
 * Mapa de Google opcional para la pestaña Mapa.
 *
 * Es la misma información del contorno embebido (polígono del lago y las cuatro
 * estaciones), pero sobre teselas reales. Solo se compone cuando hay clave,
 * internet y Google Play Services; en cualquier otro caso la pantalla usa el
 * `Canvas` local, que no depende de la red.
 */
@Composable
internal fun MapaGoogle(
    pintado: LagunaMapaPintado,
    coloresPorNodo: Map<String, Color>
) {
    val camara = rememberCameraPositionState()
    val contorno = remember(pintado) {
        pintado.contornoGeografico.map { punto -> LatLng(punto.lat, punto.lon) }
    }
    val estaciones = remember(pintado) {
        pintado.estaciones.map { estacion -> estacion to LatLng(estacion.lat, estacion.lon) }
    }

    val relleno = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    val borde = MaterialTheme.colorScheme.primary
    val bordeMarca = MaterialTheme.colorScheme.surface
    val sinDatos = MaterialTheme.colorScheme.outline

    // Encuadra el lago completo la primera vez (y si cambia el contorno).
    LaunchedEffect(pintado) {
        if (contorno.size >= 3) {
            val limites = LatLngBounds.builder()
                .apply { contorno.forEach { include(it) } }
                .build()
            camara.animate(CameraUpdateFactory.newLatLngBounds(limites, 64))
        }
    }

    Card(shape = RoundedCornerShape(16.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(pintado.aspecto.toFloat())
                .semantics {
                    contentDescription =
                        "Mapa de Google de la Laguna de Paca con las cuatro estaciones de monitoreo."
                }
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = camara,
                properties = MapProperties(
                    isBuildingEnabled = false,
                    isIndoorEnabled = false
                ),
                uiSettings = MapUiSettings(mapToolbarEnabled = false)
            ) {
                if (contorno.size >= 3) {
                    Polygon(
                        points = contorno,
                        strokeColor = borde,
                        fillColor = relleno,
                        strokeWidth = 4f
                    )
                }

                estaciones.forEach { (estacion, posicion) ->
                    MarkerComposable(
                        state = rememberMarkerState(key = estacion.id, position = posicion),
                        title = estacion.id,
                        snippet = estacion.nombre,
                        anchor = Offset(0.5f, 0.5f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(coloresPorNodo[estacion.id] ?: sinDatos)
                                .border(2.dp, bordeMarca, CircleShape)
                        )
                    }
                }
            }
        }
    }
}

/**
 * `true` mientras el dispositivo tenga una red con internet validada.
 *
 * `NET_CAPABILITY_VALIDATED` evita el caso de un WiFi conectado a un portal
 * cautivo: ahí Google Maps tampoco cargaría y es mejor el contorno local.
 */
@Composable
internal fun recuerdaHayInternet(): Boolean {
    val contexto = LocalContext.current
    var hay by remember { mutableStateOf(hayInternetAhora(contexto)) }

    DisposableEffect(contexto) {
        val gestor = contexto.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                hay = hayInternetAhora(contexto)
            }

            override fun onLost(network: Network) {
                hay = hayInternetAhora(contexto)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                hay = hayInternetAhora(contexto)
            }
        }
        runCatching { gestor.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { gestor.unregisterNetworkCallback(callback) } }
    }
    return hay
}

internal fun hayInternetAhora(contexto: Context): Boolean {
    val gestor = contexto.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val red = gestor.activeNetwork ?: return false
    val capacidades = gestor.getNetworkCapabilities(red) ?: return false
    return capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

/**
 * Los emuladores sin imagen "Google APIs/Play" no traen Play Services; sin él
 * el SDK de Maps aborta, así que se pregunta antes de componerlo.
 */
@Composable
internal fun recuerdaPlayServices(): Boolean {
    val contexto = LocalContext.current
    return remember(contexto) {
        GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(contexto) == ConnectionResult.SUCCESS
    }
}
