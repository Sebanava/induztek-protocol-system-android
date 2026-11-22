package cl.induztek.protocol.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import cl.induztek.protocol.domain.numero
import cl.induztek.protocol.model.DatoGrafico


@Composable
fun GraficoBarras(datos: List<DatoGrafico>, unidad: String) {
    val progreso = remember(datos) { Animatable(0f) }
    LaunchedEffect(datos) { progreso.animateTo(1f, tween(600)) }
    val color = MaterialTheme.colorScheme.primary
    val descripcion = datos.joinToString { "${it.etiqueta}: ${numero(it.valor)} $unidad" }
    Canvas(Modifier.fillMaxWidth().height(160.dp).semantics { contentDescription = descripcion }) {
        val maximo = datos.maxOfOrNull { it.valor }?.coerceAtLeast(1.0) ?: 1.0
        if (datos.isEmpty()) return@Canvas
        val columna = size.width / datos.size
        datos.forEachIndexed { indice, dato ->
            val alto = (size.height * dato.valor / maximo * progreso.value).toFloat()
            drawRoundRect(
                color,
                Offset(indice * columna + columna * .2f, size.height - alto),
                Size(columna * .6f, alto),
                CornerRadius(5.dp.toPx()),
            )
        }
    }
    datos.forEach {
        Text(
            "${it.etiqueta}: ${numero(it.valor)} $unidad",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
