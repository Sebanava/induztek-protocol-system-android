package cl.induztek.protocol.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.induztek.protocol.domain.*
import cl.induztek.protocol.model.*
import cl.induztek.protocol.native.FotoEquipo
import cl.induztek.protocol.ui.components.*
import cl.induztek.protocol.viewmodel.*
import java.time.LocalDate

@Composable
fun EquipoScreen(
    id: String,
    data: DatosApp,
    app: AppViewModel,
    volver: () -> Unit,
    protocolo: (String) -> Unit,
) {
    val estadoApp by app.estado.collectAsStateWithLifecycle()
    val e = data.equipos.firstOrNull { it.id == id }
    var aviso by remember { mutableStateOf("") }
    var serie by rememberSaveable { mutableStateOf("") }
    Pantalla("Equipo e historial", volver) {
        if (e == null) {
            Text("Equipo no disponible para esta cuenta")
            return@Pantalla
        }
        val i = data.instalaciones.first { it.id == e.instalacionId }
        TarjetaSeccion(e.tag) {
            Text(
                "${Catalogo.equipos[e.tipoId]} · ${if(e.tipoId=="RELE")"Secundario" else "Primario"}"
            )
            Text("${i.nombre} · ${i.comuna} · ${data.regionDe(i)}")
            Text("${e.marca} ${e.modelo}\nSerie ${e.serie} · Año ${e.anio?:"Sin dato"}")
            Text(
                "Potencia: ${e.potencia?:"—"} kVA · Tensiones: ${e.tensiones}\nCorriente: ${e.corriente?:"—"} A · Máx. contactos: ${e.maxContactos?:"—"} µΩ"
            )
            if (e.observaciones.isNotBlank()) Text(e.observaciones)
            FotoEquipo(e.foto, data.usuario.perfil.registra && !estadoApp.ocupado) { bytes ->
                app.foto(id, bytes) { aviso = it }
            }
            if (aviso.isNotBlank()) Text(aviso)
        }
        val protocolos =
            data.protocolos.filter { it.equipoId == id }.sortedByDescending { it.fechaHora }
        val series =
            protocolos
                .filter { it.estado !in listOf(Estado.BORRADOR, Estado.ANULADO) }
                .flatMap { p ->
                    data
                        .vigentes(p.id)
                        .filter { it.realizada }
                        .flatMap { pr ->
                            data
                                .valores(pr.id)
                                .keys
                                .filter { it !in listOf("POSICION_TAP", "TENSION_PRUEBA") }
                                .map { k -> "${pr.tipoId}|${pr.elemento}|$k|${data.contexto(pr)}" }
                        }
                }
                .distinct()
        if (series.isNotEmpty())
            TarjetaSeccion("Evolución de mediciones compatibles") {
                val seleccion = serie.takeIf { it in series } ?: series.first()
                Selector(
                    seleccion,
                    series.map { s ->
                        val trozos = s.split('|')
                        s to
                            "${Catalogo.pruebas[trozos[0]]} · ${trozos[1]} · ${Catalogo.etiquetas[trozos[2]]} · ${trozos[3]}"
                    },
                    { serie = it },
                    "Serie y unidad base",
                )
                val partes = seleccion.split('|')
                val puntos =
                    protocolos
                        .sortedBy { it.fechaHora }
                        .filter { it.estado !in listOf(Estado.BORRADOR, Estado.ANULADO) }
                        .mapNotNull { p ->
                            data
                                .vigentes(p.id)
                                .firstOrNull {
                                    it.tipoId == partes[0] &&
                                        it.elemento == partes[1] &&
                                        data.contexto(it) == partes[3] &&
                                        it.realizada
                                }
                                ?.let { pr ->
                                    data.valores(pr.id)[partes[2]]?.let {
                                        DatoGrafico(p.fechaHora.take(10), it)
                                    }
                                }
                        }
                GraficoBarras(puntos, Catalogo.unidades[partes[2]].orEmpty())
                Text(
                    "Valores en unidad base; la comparación no reemplaza la revisión del ingeniero.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        if (protocolos.isEmpty()) Text("Sin historial previo para este equipo.")
        protocolos.forEach { p ->
            TarjetaSeccion(p.fechaHora) {
                Text(p.numero)
                EtiquetaEstado(p.estado.etiqueta(data.usuario.perfil))
                EtiquetaEstado(
                    data.resultado(p.id)?.titulo ?: "Resultado manual pendiente",
                    data.resultado(p.id),
                )
                BotonSecundario("Ver pruebas y comparación", { protocolo(p.id) })
            }
        }
    }
}

@Composable
fun VisitaScreen(
    id: String,
    data: DatosApp,
    volver: () -> Unit,
    protocolo: (String) -> Unit,
    formulario: (Accion, String, String) -> Unit,
    compartir: (String, Boolean) -> Unit,
) {
    val v = data.visitas.firstOrNull { it.id == id }
    Pantalla("Detalle de visita", volver) {
        if (v == null) {
            Text("Visita no disponible")
            return@Pantalla
        }
        TarjetaSeccion(v.ot) {
            Text("${data.instalaciones.first{it.id==v.instalacionId}.nombre} · ${v.fecha}")
            Text(v.tipo.titulo)
            Text("Ingeniero a cargo: ${data.nombre(v.ingenieroId)}")
            EtiquetaEstado(if (v.cierre == null) "Abierta" else "Cerrada ${v.cierre}")
        }
        val protocolos = data.protocolos.filter { it.visitaId == id }
        TarjetaSeccion("Resumen de visita") {
            Text(
                "Equipos: ${protocolos.size} · Observados: ${protocolos.count{data.resultado(it.id)==Resultado.OBSERVADO}} · Rechazados: ${protocolos.count{data.resultado(it.id)==Resultado.RECHAZADO}}"
            )
        }
        protocolos.forEach { p ->
            TarjetaSeccion(data.equipos.first { it.id == p.equipoId }.tag) {
                Text(p.numero)
                EtiquetaEstado(p.estado.etiqueta(data.usuario.perfil))
                BotonSecundario("Ver protocolo", { protocolo(p.id) })
            }
        }
        if (v.cierre == null && data.usuario.perfil.registra)
            BotonPrincipal("Iniciar protocolo por equipo", { formulario(Accion.PROTOCOLO, id, "") })
        if (v.cierre == null && data.usuario.perfil.cierraVisita)
            BotonSecundario(
                "Cerrar visita y registrar recepción",
                { formulario(Accion.CERRAR, id, "") },
            )
        data.recepciones
            .firstOrNull { it.visitaId == id }
            ?.let { r ->
                TarjetaSeccion("Acta preliminar · Pendiente de formalización") {
                    Text("Recibió ${r.nombre} · ${r.rut}\n${r.cargo} · ${r.fechaHora}")
                    Text("El acta conserva los valores emitidos al cerrar la visita.")
                    BotonPrincipal("Compartir acta PDF", { compartir(id, true) })
                    Text(
                        "Abrir el selector de compartir no confirma la entrega. Verifica recepción efectiva con el cliente el mismo día.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
    }
}

@Composable
fun ProtocoloScreen(
    id: String,
    data: DatosApp,
    volver: () -> Unit,
    formulario: (Accion, String, String) -> Unit,
    compartir: (String, Boolean) -> Unit,
) {
    val p = data.protocolos.firstOrNull { it.id == id }
    var expandido by rememberSaveable { mutableStateOf(false) }
    Pantalla("Protocolo por equipo", volver) {
        if (p == null) {
            Text("Protocolo no disponible para esta cuenta")
            return@Pantalla
        }
        val e = data.equipos.first { it.id == p.equipoId }
        val editable = Permisos.editar(data.usuario.perfil, data.usuario.id, p.creadorId, p.estado)
        TarjetaSeccion(e.tag) {
            Text(p.numero, style = MaterialTheme.typography.labelLarge)
            Text(
                "${data.instalaciones.first{it.id==e.instalacionId}.nombre}\n${p.fechaHora}\nTécnico: ${data.nombre(p.creadorId)}"
            )
            Text("Temperatura: ${p.temperatura} °C · Humedad: ${p.humedad} %")
            EtiquetaEstado(p.estado.etiqueta(data.usuario.perfil))
            EtiquetaEstado(
                data.resultado(id)?.titulo ?: "Resultado pendiente de criterio manual",
                data.resultado(id),
            )
            if (p.motivo.isNotBlank()) Text("Motivo/comentario: ${p.motivo}")
            if (p.mantenimiento.isNotBlank()) Text("Mantenimiento: ${p.mantenimiento}")
            data.protocolos
                .firstOrNull { it.reemplazaId == id && it.estado == Estado.FORMALIZADO }
                ?.let { Text("Reemplazado por nº ${it.numero}") }
            p.reemplazaId?.let { original ->
                Text(
                    "Reemplaza a ${data.protocolos.firstOrNull{it.id==original}?.numero?:original}"
                )
            }
            if (
                p.estado !in listOf(Estado.FORMALIZADO, Estado.ANULADO) &&
                    ReglasPrueba.atrasado(LocalDate.parse(p.fechaHora.take(10)), LocalDate.now())
            )
                EtiquetaEstado("Atrasado: más de 2 días hábiles", Resultado.OBSERVADO)
        }
        if (editable) {
            BotonSecundario(
                "Corregir ambiente o mantenimiento",
                { formulario(Accion.CORREGIR, id, "") },
            )
            BotonPrincipal("Agregar o corregir prueba", { formulario(Accion.PRUEBA, id, "") })
            val pendientes =
                Catalogo.tipos(e.tipoId).flatMap { tipo ->
                    Catalogo.elementos(e.tipoId, tipo)
                        .filter { elemento ->
                            data.pruebasDe(id).none { it.tipoId == tipo && it.elemento == elemento }
                        }
                        .map { tipo to it }
                }
            if (pendientes.isNotEmpty())
                TarjetaSeccion("Pruebas pendientes de registro o justificación") {
                    pendientes.forEach { (tipo, elemento) ->
                        BotonSecundario(
                            "${Catalogo.pruebas[tipo]} · $elemento",
                            { formulario(Accion.PRUEBA, id, "$tipo|$elemento") },
                        )
                    }
                }
            BotonPrincipal(
                "Pasar a Registrado en terreno",
                { formulario(Accion.REGISTRAR, id, "") },
            )
        }
        data
            .pruebasDe(id)
            .sortedWith(compareBy({ it.tipoId }, { it.elemento }, { it.momento }))
            .forEach { pr ->
                TarjetaSeccion("${Catalogo.pruebas[pr.tipoId]} · ${pr.elemento}") {
                    Text("Momento: ${pr.momento}")
                    if (!pr.realizada) Text("No realizada · ${pr.motivo}")
                    else {
                        data.valores(pr.id).forEach { (k, v) ->
                            Text("${Catalogo.etiquetas[k]}: ${numero(v)} ${Catalogo.unidades[k]}")
                        }
                        val valores = data.valores(pr.id)
                        if (pr.tipoId == "AISLACION" && valores["LECTURA_10MIN"] != null)
                            Text(
                                "IP: ${numero(valores.getValue("LECTURA_10MIN")/valores.getValue("LECTURA_1MIN"))}"
                            )
                        if (!pr.opera) Text("El relé no operó")
                        EtiquetaEstado(
                            pr.resultado?.titulo ?: "Criterio del ingeniero pendiente",
                            pr.resultado,
                        )
                        val ins = data.instrumentos.firstOrNull { it.id == pr.instrumentoId }
                        Text("Instrumento: ${ins?.nombre} · ${ins?.serie}")
                        if (pr.comentario.isNotBlank()) Text(pr.comentario)
                        if (
                            data.usuario.perfil.revisa &&
                                p.estado == Estado.REGISTRADO_TERRENO &&
                                (pr.tipoId == "DEVANADOS" ||
                                    pr.tipoId == "AISLACION" &&
                                        e.tipoId in listOf("CELDA", "INTERRUPTOR"))
                        )
                            BotonSecundario(
                                "Indicar resultado manual",
                                { formulario(Accion.MANUAL, pr.id, "") },
                            )
                    }
                    if (editable)
                        BotonSecundario(
                            "Corregir esta prueba",
                            { formulario(Accion.PRUEBA, id, pr.id) },
                        )
                }
            }
        val comparaciones = data.comparaciones(id)
        TarjetaSeccion("Comparación con prueba anterior compatible") {
            if (comparaciones.isEmpty()) Text("Sin historial previo compatible.")
            comparaciones.forEach { c ->
                Text(
                    "${c.elemento} · ${Catalogo.etiquetas[c.parametro]}\nAnterior ${numero(c.anterior)} ${c.unidad} → actual ${numero(c.actual)} ${c.unidad}"
                )
                EtiquetaEstado(
                    if (c.porcentaje == null) "No calculable: valor anterior cero"
                    else
                        "${numero(c.porcentaje)} %${if(ReglasPrueba.significativa(c.porcentaje))" · Variación significativa" else ""}",
                    if (ReglasPrueba.significativa(c.porcentaje)) Resultado.OBSERVADO else null,
                )
            }
        }
        if (data.usuario.perfil.revisa && p.estado == Estado.REGISTRADO_TERRENO) {
            BotonPrincipal(
                "Formalizar como documento oficial",
                { formulario(Accion.FORMALIZAR, id, "") },
            )
            BotonSecundario("Devolver con observaciones", { formulario(Accion.OBSERVAR, id, "") })
        }
        if (data.usuario.perfil.revisa && p.estado !in listOf(Estado.FORMALIZADO, Estado.ANULADO))
            BotonSecundario("Anular con motivo", { formulario(Accion.ANULAR, id, "") })
        if (p.estado != Estado.ANULADO)
            BotonSecundario("Compartir protocolo PDF", { compartir(id, false) })
        TextButton(onClick = { expandido = !expandido }) {
            Text(if (expandido) "Ocultar trazabilidad" else "Ver trazabilidad y formalización")
        }
        AnimatedVisibility(expandido) {
            Column {
                data.historial
                    .filter { it.protocoloId == id }
                    .forEach { h ->
                        Text(
                            "${h.fechaHora}\n${data.nombre(h.usuarioId)} · ${h.nuevo.titulo}\n${h.motivo}\n"
                        )
                    }
            }
        }
    }
}
