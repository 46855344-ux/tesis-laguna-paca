package pe.edu.continental.lagunapaca.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pe.edu.continental.lagunapaca.mobile.BuildConfig
import pe.edu.continental.lagunapaca.mobile.data.AlertReason
import pe.edu.continental.lagunapaca.mobile.data.DashboardSummary
import pe.edu.continental.lagunapaca.mobile.data.StationStatus
import pe.edu.continental.lagunapaca.mobile.data.StationSummary
import pe.edu.continental.lagunapaca.mobile.data.WaterSample
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val zonaLima = ZoneId.of("America/Lima")
private val formatoFecha = DateTimeFormatter
    .ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-PE"))
    .withZone(zonaLima)
private val formatoHora = DateTimeFormatter
    .ofPattern("HH:mm", Locale.forLanguageTag("es-PE"))
    .withZone(zonaLima)

@Composable
fun LagunaPacaApp(state: AppState, viewModel: MainViewModel) {
    if (state.isAuthenticated) {
        MainScaffold(state = state, viewModel = viewModel)
    } else {
        LoginScreen(
            state = state,
            onUrl = viewModel::actualizarUrl,
            onUser = viewModel::actualizarUsuario,
            onPassword = viewModel::actualizarPassword,
            onLogin = viewModel::iniciarSesion
        )
    }
}

@Composable
private fun LoginScreen(
    state: AppState,
    onUrl: (String) -> Unit,
    onUser: (String) -> Unit,
    onPassword: (String) -> Unit,
    onLogin: () -> Unit
) {
    val scroll = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "LP",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Laguna Paca",
                color = Color.White,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Monitoreo móvil de la calidad del agua",
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(28.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(Modifier.padding(22.dp)) {
                    Text(
                        text = "Iniciar sesión",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Usa las mismas credenciales de la API y del panel.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(18.dp))

                    OutlinedTextField(
                        value = state.baseUrl,
                        onValueChange = onUrl,
                        label = { Text("Servidor") },
                        placeholder = { Text("http://10.0.2.2:8080") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isLoggingIn
                    )

                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = state.username,
                        onValueChange = onUser,
                        label = { Text("Usuario") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isLoggingIn
                    )

                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = state.password,
                        onValueChange = onPassword,
                        label = { Text("Contraseña") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isLoggingIn
                    )

                    state.loginError?.let { error ->
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    Button(
                        onClick = onLogin,
                        enabled = !state.isLoggingIn,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        if (state.isLoggingIn) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Iniciar sesión")
                        }
                    }

                    if (BuildConfig.DEBUG) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "Demo local: admin / admin123. En el emulador, " +
                                "10.0.2.2 es la PC que ejecuta el backend.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(state: AppState, viewModel: MainViewModel) {
    val cargando = when (state.screen) {
        MobileScreen.TABLERO -> state.dashboard.isLoading
        MobileScreen.ALERTAS -> state.alerts.isLoading
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Laguna de Paca",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = when (state.screen) {
                                MobileScreen.TABLERO -> "Tablero de monitoreo"
                                MobileScreen.ALERTAS -> "Alertas ECA-Agua"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = viewModel::refrescar,
                        enabled = !cargando
                    ) {
                        Text("Actualizar")
                    }
                    TextButton(onClick = viewModel::cerrarSesion) {
                        Text("Salir")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(
                selectedTabIndex = when (state.screen) {
                    MobileScreen.TABLERO -> 0
                    MobileScreen.ALERTAS -> 1
                }
            ) {
                Tab(
                    selected = state.screen == MobileScreen.TABLERO,
                    onClick = { viewModel.seleccionarPantalla(MobileScreen.TABLERO) },
                    text = { Text("Tablero") }
                )
                Tab(
                    selected = state.screen == MobileScreen.ALERTAS,
                    onClick = { viewModel.seleccionarPantalla(MobileScreen.ALERTAS) },
                    text = { Text("Alertas") }
                )
            }

            when (state.screen) {
                MobileScreen.TABLERO -> DashboardScreen(
                    state = state,
                    onRetry = viewModel::cargarTablero
                )
                MobileScreen.ALERTAS -> AlertsScreen(
                    state = state,
                    onRetry = viewModel::cargarAlertas
                )
            }
        }
    }
}

@Composable
private fun DashboardScreen(state: AppState, onRetry: () -> Unit) {
    val dashboard = state.dashboard
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Estado de la laguna",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Ventana de 24 h · 4 estaciones",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                dashboard.lastUpdated?.let { instante ->
                    Text(
                        text = "Actualizado ${formatoHora.format(instante)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        dashboard.error?.let { error ->
            item { ErrorBanner(message = error, onRetry = onRetry) }
        }

        if (dashboard.isLoading && dashboard.summary == null) {
            item { LoadingCard() }
        }

        dashboard.summary?.let { resumen ->
            item { KpiGrid(resumen) }

            if (resumen.samplesInWindow == 0) {
                item {
                    EmptyCard(
                        title = "Sin muestras recientes",
                        message = "No hay lecturas en las últimas 24 h. " +
                            "Ejecuta el simulador y pulsa Actualizar."
                    )
                }
            }

            item {
                Text(
                    text = "Estaciones",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            items(resumen.stations, key = { it.nodeId }) { estacion ->
                StationCard(estacion)
            }

            if (dashboard.recent.isNotEmpty()) {
                item {
                    Text(
                        text = "Últimas muestras",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(dashboard.recent, key = { it.id }) { muestra ->
                    RecentSampleRow(muestra)
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun KpiGrid(summary: DashboardSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KpiCard(
                label = "Muestras 24 h",
                value = summary.samplesInWindow.toString(),
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                label = "Alertas",
                value = summary.alertsInWindow.toString(),
                modifier = Modifier.weight(1f),
                destacado = summary.alertsInWindow > 0
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KpiCard(
                label = "Estaciones",
                value = "${summary.stationsWithData}/${summary.totalStations}",
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                label = "Cumplimiento",
                value = summary.compliancePercent?.let { "$it %" } ?: "—",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun KpiCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    destacado: Boolean = false
) {
    Card(
        modifier = modifier.heightIn(min = 86.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (destacado) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = if (destacado) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (destacado) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

@Composable
private fun StationCard(station: StationSummary) {
    val colorEstado = colorDeEstado(station.status)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = station.nodeId,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                EstadoChip(status = station.status)
            }

            Spacer(Modifier.height(8.dp))

            val ultima = station.latest
            if (ultima == null) {
                Text(
                    text = "Sin lecturas en las últimas 24 h.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "pH ${ph(ultima.ph)}  ·  ${decimal(ultima.tempC)} °C  ·  " +
                        "${entero(ultima.tdsPpm)} ppm",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Turbidez ${decimal(ultima.turbidityNtu)} NTU · ${hace(ultima.recordedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ultima.alertReasons.firstOrNull()?.let { motivo ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = motivo.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorEstado
                    )
                }
            }
        }
    }
}

@Composable
private fun EstadoChip(status: StationStatus) {
    val color = colorDeEstado(status)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = status.label,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun colorDeEstado(status: StationStatus): Color = when (status) {
    StationStatus.DENTRO -> MaterialTheme.colorScheme.secondary
    StationStatus.ALERTA -> MaterialTheme.colorScheme.error
    StationStatus.SIN_DATOS -> MaterialTheme.colorScheme.outline
}

@Composable
private fun RecentSampleRow(sample: WaterSample) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = sample.nodeId,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(38.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = "pH ${ph(sample.ph)} · ${decimal(sample.tempC)} °C",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${formatoFecha.format(sample.recordedAt)} · ${hace(sample.recordedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (sample.alert) {
                Text(
                    text = "ALERTA",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun AlertsScreen(state: AppState, onRetry: () -> Unit) {
    val alerts = state.alerts
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Alertas registradas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "El veredicto lo decide el backend; la app solo lo muestra.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        alerts.error?.let { error ->
            item { ErrorBanner(message = error, onRetry = onRetry) }
        }

        if (alerts.isLoading && alerts.items.isEmpty()) {
            item { LoadingCard() }
        }

        if (!alerts.isLoading && alerts.error == null && alerts.items.isEmpty()) {
            item {
                EmptyCard(
                    title = "Sin alertas",
                    message = "No hay muestras con alerta ECA-Agua registradas."
                )
            }
        }

        items(alerts.items, key = { it.id }) { alerta ->
            AlertCard(alerta)
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun AlertCard(sample: WaterSample) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = sample.nodeId,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "pH ${ph(sample.ph)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatoFecha.format(sample.recordedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = motivos(sample.alertReasons),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 10.dp, end = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRetry) {
                Text("Reintentar")
            }
        }
    }
}

@Composable
private fun LoadingCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyCard(title: String, message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun motivos(reasons: List<AlertReason>): String {
    if (reasons.isEmpty()) return "pH fuera del rango ECA-Agua (6.5 – 9.0)."
    return reasons.joinToString(" · ") { it.message }
}

private fun decimal(valor: Double): String =
    String.format(Locale.US, "%.1f", valor)

/** Conserva 6.15 en lugar de redondearlo a 6.2 y evita mostrar "6.". */
private fun ph(valor: Double): String =
    String.format(Locale.US, "%.2f", valor)
        .trimEnd('0')
        .trimEnd('.')

private fun entero(valor: Double): String =
    String.format(Locale.US, "%.0f", valor)

private fun hace(instante: Instant, ahora: Instant = Instant.now()): String {
    val minutos = Duration.between(instante, ahora).toMinutes()
    return when {
        minutos <= 0 -> "ahora"
        minutos < 60 -> "hace $minutos min"
        minutos < 24 * 60 -> "hace ${minutos / 60} h"
        else -> formatoFecha.format(instante)
    }
}
