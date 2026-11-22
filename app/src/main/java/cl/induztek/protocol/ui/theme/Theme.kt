package cl.induztek.protocol.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Azul = Color(0xFF152A40)
val Exito = Color(0xFF176443)
val Alerta = Color(0xFF855200)
private val Claro =
    lightColorScheme(
        primary = Azul,
        primaryContainer = Azul,
        onPrimaryContainer = Color(0xFFF5FAFC),
        secondary = Color(0xFF006A62),
        tertiary = Exito,
        background = Color(0xFFF2F6F8),
        surface = Color.White,
        surfaceVariant = Color(0xFFE8EFF2),
        outlineVariant = Color(0xFFD9E3E9),
        secondaryContainer = Color(0xFFCFEDE6),
        onSecondaryContainer = Color(0xFF164C44),
        error = Color(0xFFAF2837),
    )
private val Oscuro =
    darkColorScheme(
        primary = Color(0xFFACC9EE),
        primaryContainer = Color(0xFF203A50),
        onPrimaryContainer = Color(0xFFF5FAFC),
        secondary = Color(0xFFA9C9E5),
        tertiary = Color(0xFF8BD7B1),
        background = Color(0xFF0D1826),
        surface = Color(0xFF172437),
        surfaceVariant = Color(0xFF25384B),
        outlineVariant = Color(0xFF3F5264),
        secondaryContainer = Color(0xFF1F4B47),
        onSecondaryContainer = Color(0xFFD0F2E9),
    )

private val Forma = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
)

private val Base = Typography()
private val Tipografia = Typography(
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.Bold),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

@Composable
fun InduztekTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Oscuro else Claro,
        typography = Tipografia,
        shapes = Forma,
        content = content,
    )
}
