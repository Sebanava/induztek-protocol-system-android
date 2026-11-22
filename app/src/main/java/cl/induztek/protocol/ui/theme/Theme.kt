package cl.induztek.protocol.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Azul = Color(0xFF142D4E)
val Exito = Color(0xFF176443)
val Alerta = Color(0xFF855200)
private val Claro =
    lightColorScheme(
        primary = Azul,
        secondary = Color(0xFF35648A),
        tertiary = Exito,
        background = Color(0xFFF3F5F8),
        surface = Color.White,
        error = Color(0xFFAF2837),
    )
private val Oscuro =
    darkColorScheme(
        primary = Color(0xFFACC9EE),
        secondary = Color(0xFFA9C9E5),
        tertiary = Color(0xFF8BD7B1),
        background = Color(0xFF0D1826),
        surface = Color(0xFF172437),
    )

@Composable
fun InduztekTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Oscuro else Claro, content = content)
}
