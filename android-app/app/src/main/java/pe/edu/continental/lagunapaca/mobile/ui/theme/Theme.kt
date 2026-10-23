package pe.edu.continental.lagunapaca.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AzulProfundo = Color(0xFF0F5C8C)
private val AzulClaro = Color(0xFF8EC9E8)
private val VerdeAgua = Color(0xFF178C7F)
private val FondoClaro = Color(0xFFF6F8FA)
private val SuperficieClara = Color(0xFFFFFFFF)
private val TextoClaro = Color(0xFF16212B)
private val TextoSecundarioClaro = Color(0xFF526174)
private val Alerta = Color(0xFFB3261E)
private val FondoOscuro = Color(0xFF0E1620)
private val SuperficieOscura = Color(0xFF162230)
private val TextoOscuro = Color(0xFFE8EDF2)
private val TextoSecundarioOscuro = Color(0xFFCBD5E1)

private val EsquemaClaro = lightColorScheme(
    primary = AzulProfundo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7ECF8),
    onPrimaryContainer = Color(0xFF07324D),
    secondary = VerdeAgua,
    onSecondary = Color.White,
    background = FondoClaro,
    onBackground = TextoClaro,
    surface = SuperficieClara,
    onSurface = TextoClaro,
    surfaceVariant = Color(0xFFE8EEF3),
    onSurfaceVariant = TextoSecundarioClaro,
    error = Alerta,
    onError = Color.White,
    outline = Color(0xFF8A99A8)
)

private val EsquemaOscuro = darkColorScheme(
    primary = AzulClaro,
    onPrimary = Color(0xFF00344F),
    primaryContainer = Color(0xFF0B4668),
    onPrimaryContainer = Color(0xFFD7ECF8),
    secondary = Color(0xFF7DD8CB),
    onSecondary = Color(0xFF003731),
    background = FondoOscuro,
    onBackground = TextoOscuro,
    surface = SuperficieOscura,
    onSurface = TextoOscuro,
    surfaceVariant = Color(0xFF243345),
    onSurfaceVariant = TextoSecundarioOscuro,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    outline = Color(0xFF9AAABA)
)

@Composable
fun LagunaPacaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) EsquemaOscuro else EsquemaClaro,
        content = content
    )
}
