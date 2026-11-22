package cl.induztek.protocol.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import cl.induztek.protocol.domain.ReglasPrueba
import cl.induztek.protocol.domain.Validacion
import cl.induztek.protocol.model.*
import cl.induztek.protocol.ui.components.*
import cl.induztek.protocol.viewmodel.Accion
import java.time.Duration
import java.time.LocalDateTime

@Composable
fun PanelScreen(
    data: DatosApp,
    navegar: (String) -> Unit,
    formulario: (Accion, String, String) -> Unit,
    salir: () -> Unit,
) {
    val usuario = data.usuario
    val pendientes = data.protocolos.filter { it.estado !in listOf(Estado.FORMALIZADO, Estado.ANULADO) }
    Pantalla("Induztek") {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(usuario.perfil.titulo, style = MaterialTheme.typography.labelLarge)
                Text(usuario.contacto.ifBlank { usuario.nombre }, style = MaterialTheme.typography.headlineSmall)
                if (usuario.perfil == Perfil.CLIENTE && usuario.contacto.isNotBlank()) {
                    Text(usuario.nombre, style = MaterialTheme.typography.bodyMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudOff, null, Modifier.size(16.dp))
                    Text("Datos disponibles sin conexión", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Indicador("Equipos", data.equipos.size, Modifier.weight(1f))
            Indicador("Pendientes", pendientes.size, Modifier.weight(1f))
            if (usuario.perfil != Perfil.CLIENTE) {
                val hoy = Validacion.hoy()
                Indicador("Atrasados", pendientes.count {
                    ReglasPrueba.atrasado(LocalDateTime.parse(it.fechaHora).toLocalDate(), hoy)
                }, Modifier.weight(1f))
            }
        }

        TarjetaSeccion(if (usuario.perfil == Perfil.CLIENTE) "Consultas" else "Trabajo en terreno") {
            AccesoPanel("Equipos", "Ficha e historial de pruebas", Icons.Default.Build) { navegar("equipos") }
            AccesoPanel(if (usuario.perfil == Perfil.CLIENTE) "Actas de visitas cerradas" else "Visitas y actas",
                "Órdenes de trabajo y documentos", Icons.Default.Event) { navegar("visitas") }
            AccesoPanel("Protocolos", "Estados y resultados", Icons.Default.Assignment) { navegar("protocolos") }
            if (usuario.perfil.registra) {
                AccesoPanel("Clientes e instalaciones", null, Icons.Default.Business) { navegar("clientes") }
                AccesoPanel("Instrumentos y calibraciones", null, Icons.Default.Speed) { navegar("instrumentos") }
            }
        }

        if (usuario.perfil == Perfil.JEFE_TECNICO) {
            val duraciones = data.protocolos.filter { it.estado == Estado.FORMALIZADO }.mapNotNull { protocolo ->
                data.historial.firstOrNull { it.protocoloId == protocolo.id && it.nuevo == Estado.FORMALIZADO }
                    ?.let { Duration.between(LocalDateTime.parse(protocolo.fechaHora), LocalDateTime.parse(it.fechaHora)).toHours() }
            }
            TarjetaSeccion("Supervisión") {
                Text(if (duraciones.isEmpty()) "Sin protocolos formalizados" else
                    "Promedio de formalización: ${duraciones.average().toInt()} h (precarga incluida)")
                Text("Los atrasos se calculan de lunes a viernes, sin descontar feriados.",
                    style = MaterialTheme.typography.bodySmall)
                AccesoPanel("Gestionar personal", null, Icons.Default.People) { formulario(Accion.PERSONAL, "nuevo", "") }
                AccesoPanel("Otorgar o renovar acceso cliente", null, Icons.Default.ManageAccounts) { formulario(Accion.CUENTA, "nuevo", "") }
            }
        }

        TarjetaSeccion("Mi cuenta") {
            AccesoPanel("Conexión y sincronización", null, Icons.Default.Sync) { navegar("sincronizacion") }
            AccesoPanel("Cambiar contraseña", null, Icons.Default.Lock) { navegar("cambiar-clave") }
            AccesoPanel("Cerrar sesión", null, Icons.Default.Logout, salir)
        }
    }
}

@Composable
private fun Indicador(etiqueta: String, valor: Int, modifier: Modifier) {
    OutlinedCard(modifier, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 11.dp)) {
            Text(valor.toString(), style = MaterialTheme.typography.headlineMedium)
            Text(etiqueta, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AccesoPanel(titulo: String, detalle: String?, icono: ImageVector, abrir: () -> Unit) {
    Surface(onClick = abrir, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.heightIn(min = 52.dp).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer, shape = MaterialTheme.shapes.small) {
                Icon(icono, null, Modifier.padding(8.dp).size(18.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(titulo, style = MaterialTheme.typography.titleSmall)
                if (detalle != null) Text(detalle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}