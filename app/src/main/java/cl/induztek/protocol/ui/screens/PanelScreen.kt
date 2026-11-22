package cl.induztek.protocol.ui.screens

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import cl.induztek.protocol.domain.ReglasPrueba
import cl.induztek.protocol.model.*
import cl.induztek.protocol.ui.components.*
import cl.induztek.protocol.viewmodel.Accion
import java.time.*

@Composable
fun PanelScreen(
    data: DatosApp,
    navegar: (String) -> Unit,
    formulario: (Accion, String, String) -> Unit,
    salir: () -> Unit,
) {
    val u = data.usuario
    Pantalla("Induztek · Inicio") {
        TarjetaSeccion(u.contacto.ifBlank { u.nombre }) {
            Text(u.perfil.titulo, style = MaterialTheme.typography.titleLarge)
            Text(if (u.perfil == Perfil.CLIENTE) u.nombre else "Registro eléctrico en terreno")
            EtiquetaEstado("Datos disponibles sin conexión")
        }
        TarjetaEstadistica(data.equipos.size, "Equipos disponibles")
        TarjetaEstadistica(
            data.protocolos.count {
                it.estado != Estado.FORMALIZADO && it.estado != Estado.ANULADO
            },
            "Protocolos pendientes",
        )
        if (u.perfil != Perfil.CLIENTE)
            TarjetaEstadistica(
                data.protocolos.count {
                    it.estado !in listOf(Estado.FORMALIZADO, Estado.ANULADO) &&
                        ReglasPrueba.atrasado(
                            LocalDate.parse(it.fechaHora.take(10)),
                            LocalDate.now(),
                        )
                },
                "Atrasados: más de 2 días hábiles",
            )
        BotonPrincipal("Equipos e historial", { navegar("equipos") })
        BotonSecundario(
            if (u.perfil == Perfil.CLIENTE) "Actas de visitas cerradas" else "Visitas y actas",
            { navegar("visitas") },
        )
        BotonSecundario("Protocolos y resultados", { navegar("protocolos") })
        if (u.perfil.registra) {
            BotonSecundario("Clientes e instalaciones", { navegar("clientes") })
            BotonSecundario("Instrumentos y calibraciones", { navegar("instrumentos") })
        }
        if (u.perfil == Perfil.JEFE_TECNICO) {
            val duraciones =
                data.protocolos
                    .filter { it.estado == Estado.FORMALIZADO }
                    .mapNotNull { p ->
                        data.historial
                            .firstOrNull {
                                it.protocoloId == p.id && it.nuevo == Estado.FORMALIZADO
                            }
                            ?.let {
                                Duration.between(
                                        LocalDateTime.parse(p.fechaHora),
                                        LocalDateTime.parse(it.fechaHora),
                                    )
                                    .toHours()
                            }
                    }
            TarjetaSeccion("Supervisión") {
                Text(
                    "Promedio de formalización: ${if(duraciones.isEmpty())"Sin datos" else "${duraciones.average().toInt()} h (precarga incluida)"}"
                )
                Text(
                    "El plazo considera de lunes a viernes y no descuenta feriados."
                )
            }
            BotonSecundario("Gestionar personal", { formulario(Accion.PERSONAL, "nuevo", "") })
            BotonSecundario(
                "Otorgar o renovar acceso cliente",
                { formulario(Accion.CUENTA, "nuevo", "") },
            )
        }
        BotonSecundario("Conexión y sincronización", { navegar("sincronizacion") })
        BotonSecundario("Cerrar sesión", salir)
    }
}
