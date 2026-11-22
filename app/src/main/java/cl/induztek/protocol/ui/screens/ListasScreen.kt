package cl.induztek.protocol.ui.screens

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import cl.induztek.protocol.model.*
import cl.induztek.protocol.ui.components.*
import cl.induztek.protocol.viewmodel.Accion
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun EquiposScreen(
    data: DatosApp,
    volver: () -> Unit,
    detalle: (String) -> Unit,
    formulario: (Accion, String, String) -> Unit,
) {
    var filtro by rememberSaveable { mutableStateOf("") }
    var clase by rememberSaveable { mutableStateOf("") }
    Pantalla("Equipos e historial", volver) {
        CampoTexto(filtro, { filtro = it }, "Buscar TAG, serie o instalación")
        Selector(
            clase,
            listOf("" to "Todos", "PRIMARIO" to "Primarios", "SECUNDARIO" to "Secundarios"),
            { clase = it },
            "Clasificación",
        )
        if (data.usuario.perfil.registra)
            BotonPrincipal("Registrar equipo", { formulario(Accion.EQUIPO, "nuevo", "") })
        val equipos =
            data.equipos.filter { e ->
                val instalacion = data.instalaciones.first { it.id == e.instalacionId }.nombre
                listOf(e.tag, e.serie, instalacion).any { it.contains(filtro, true) } &&
                    (clase.isEmpty() ||
                        (if (e.tipoId == "RELE") "SECUNDARIO" else "PRIMARIO") == clase)
            }
        if (equipos.isEmpty()) Text("No hay equipos que coincidan con la búsqueda.")
        equipos.forEach { e ->
            TarjetaSeccion(e.tag) {
                Text(
                    "${Catalogo.equipos[e.tipoId]} · ${data.instalaciones.first{it.id==e.instalacionId}.nombre}"
                )
                Text("Serie ${e.serie}")
                BotonSecundario("Ver equipo e historial", { detalle(e.id) })
            }
        }
    }
}

@Composable
fun ProtocolosScreen(data: DatosApp, volver: () -> Unit, detalle: (String) -> Unit) {
    var filtro by rememberSaveable { mutableStateOf("") }
    var estado by rememberSaveable { mutableStateOf("") }
    Pantalla("Protocolos", volver) {
        CampoTexto(filtro, { filtro = it }, "Buscar número, TAG, cliente o técnico")
        val opciones =
            if (data.usuario.perfil == Perfil.CLIENTE)
                listOf(
                    "" to "Todos",
                    "PENDIENTE" to "Pendiente de formalización",
                    Estado.FORMALIZADO.name to "Formalizado",
                )
            else listOf("" to "Todos") + Estado.entries.map { it.name to it.titulo }
        Selector(estado, opciones, { estado = it }, "Estado")
        val lista =
            data.protocolos.filter { p ->
                val e = data.equipos.first { it.id == p.equipoId }
                val i = data.instalaciones.first { it.id == e.instalacionId }
                val coincide =
                    listOf(
                            p.numero,
                            e.tag,
                            data.nombre(p.creadorId),
                            data.usuarios.first { it.id == i.clienteId }.nombre,
                        )
                        .any { it.contains(filtro, true) }
                coincide &&
                    (estado.isEmpty() ||
                        p.estado.name == estado ||
                        estado == "PENDIENTE" && p.estado != Estado.FORMALIZADO)
            }
        if (lista.isEmpty()) Text("No hay protocolos disponibles para este filtro.")
        lista.forEach { p ->
            TarjetaSeccion(data.equipos.first { it.id == p.equipoId }.tag) {
                Text(p.numero)
                Text(p.fechaHora)
                EtiquetaEstado(p.estado.etiqueta(data.usuario.perfil))
                EtiquetaEstado(
                    data.resultado(p.id)?.titulo ?: "Criterio manual pendiente",
                    data.resultado(p.id),
                )
                BotonSecundario("Consultar protocolo", { detalle(p.id) })
            }
        }
    }
}

@Composable
fun VisitasScreen(
    data: DatosApp,
    volver: () -> Unit,
    detalle: (String) -> Unit,
    formulario: (Accion, String, String) -> Unit,
) {
    Pantalla(if (data.usuario.perfil == Perfil.CLIENTE) "Actas de terreno" else "Visitas", volver) {
        if (data.usuario.perfil.cierraVisita)
            BotonPrincipal("Registrar visita ejecutada", { formulario(Accion.VISITA, "nuevo", "") })
        if (data.visitas.isEmpty()) Text("No hay visitas disponibles para tu cuenta.")
        data.visitas
            .sortedByDescending { it.fecha }
            .forEach { v ->
                TarjetaSeccion(v.ot) {
                    Text(data.instalaciones.first { it.id == v.instalacionId }.nombre)
                    Text("${v.fecha} · ${v.tipo.titulo}")
                    EtiquetaEstado(if (v.cierre == null) "Abierta" else "Cerrada · acta emitida")
                    BotonSecundario("Ver visita y acta", { detalle(v.id) })
                }
            }
    }
}

@Composable
fun ClientesScreen(
    data: DatosApp,
    volver: () -> Unit,
    formulario: (Accion, String, String) -> Unit,
) {
    Pantalla("Clientes e instalaciones", volver) {
        if (data.usuario.perfil.registra) {
            BotonPrincipal("Registrar empresa cliente", { formulario(Accion.CLIENTE, "nuevo", "") })
            BotonSecundario(
                "Registrar instalación",
                { formulario(Accion.INSTALACION, "nuevo", "") },
            )
        }
        data.usuarios
            .filter { it.perfil == Perfil.CLIENTE }
            .forEach { u ->
                TarjetaSeccion(u.nombre) {
                    Text("RUT ${u.rut} · ${u.contacto}")
                    EtiquetaEstado(
                        if (u.activo) "Cuenta activa" else "Acceso pendiente del jefe técnico"
                    )
                    data.instalaciones
                        .filter { it.clienteId == u.id }
                        .forEach { i ->
                            Text("${i.nombre} · ${i.comuna} · ${data.regionDe(i)}\n${i.acceso}")
                        }
                }
            }
    }
}

@Composable
fun InstrumentosScreen(
    data: DatosApp,
    volver: () -> Unit,
    formulario: (Accion, String, String) -> Unit,
) {
    Pantalla("Instrumentos y calibraciones", volver) {
        if (data.usuario.perfil == Perfil.JEFE_TECNICO)
            BotonPrincipal(
                "Registrar instrumento o calibración",
                { formulario(Accion.INSTRUMENTO, "nuevo", "") },
            )
        data.instrumentos.forEach { ins ->
            TarjetaSeccion(ins.nombre) {
                Text("${ins.marcaModelo} · Serie ${ins.serie}")
                val calibraciones =
                    data.calibraciones
                        .filter { it.instrumentoId == ins.id }
                        .sortedByDescending { it.hasta }
                calibraciones.forEach { Text("Calibrado ${it.desde} · vence ${it.hasta}") }
                val vigente =
                    calibraciones
                        .filter { it.desde <= LocalDate.now().toString() }
                        .maxByOrNull { it.hasta }
                val dias =
                    vigente?.let {
                        ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(it.hasta))
                    }
                EtiquetaEstado(
                    when {
                        dias == null -> "Sin calibración"
                        dias < 0 -> "Calibración vencida"
                        dias <= 30 -> "Próximo a vencer: $dias días"
                        else -> "Vigente: $dias días"
                    },
                    if (dias == null || dias < 0) Resultado.RECHAZADO
                    else if (dias <= 30) Resultado.OBSERVADO else Resultado.APROBADO,
                )
            }
        }
    }
}
