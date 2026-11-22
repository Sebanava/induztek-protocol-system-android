package cl.induztek.protocol.domain

import cl.induztek.protocol.model.*
import java.util.Locale

fun numero(valor: Double): String = String.format(Locale.ROOT, "%.2f", valor)

object Documentos {
    fun protocolo(data: DatosApp, id: String): String {
        val p = data.protocolos.firstOrNull { it.id == id } ?: error("Protocolo no autorizado")
        val e = data.equipos.first { it.id == p.equipoId }
        val i = data.instalaciones.first { it.id == e.instalacionId }
        return buildString {
            appendLine("INDUZTEK INGENIERÍA SpA")
            appendLine("DATOS FICTICIOS · MVP ACADÉMICO · NO ES UN CERTIFICADO REAL")
            appendLine("PROTOCOLO POR EQUIPO · ${p.numero}")
            appendLine(
                if (p.estado == Estado.FORMALIZADO) "Formalizado" else "Pendiente de formalización"
            )
            appendLine("Cliente: ${data.usuarios.first { it.id==i.clienteId }.nombre}")
            appendLine(
                "Instalación: ${i.nombre} · ${i.ubicacion} · ${i.comuna} · ${data.regionDe(i)}"
            )
            appendLine("Equipo: ${e.tag} · ${Catalogo.equipos[e.tipoId]} · Serie: ${e.serie}")
            appendLine("Marca/modelo: ${e.marca} ${e.modelo}")
            appendLine("Fecha de prueba: ${p.fechaHora} · Técnico: ${data.nombre(p.creadorId)}")
            appendLine("Temperatura: ${p.temperatura} °C · Humedad: ${p.humedad} %")
            appendLine(
                "Resultado global: ${data.resultado(id)?.titulo?:"Pendiente de criterio del ingeniero"}"
            )
            appendLine("Mantenimiento: ${p.mantenimiento.ifBlank { "No registrado" }}")
            data.protocolos
                .firstOrNull { it.reemplazaId == id && it.estado == Estado.FORMALIZADO }
                ?.let { appendLine("Reemplazado por nº ${it.numero}") }
            p.reemplazaId?.let { original ->
                appendLine(
                    "Reemplaza a ${data.protocolos.firstOrNull { it.id==original }?.numero?:original}"
                )
            }
            data
                .pruebasDe(id)
                .sortedWith(compareBy({ it.tipoId }, { it.elemento }, { it.momento }))
                .forEach { pr ->
                    appendLine("\n${Catalogo.pruebas[pr.tipoId]} · ${pr.elemento} · ${pr.momento}")
                    if (!pr.realizada) appendLine("No realizada: ${pr.motivo}")
                    else {
                        val instrumento = data.instrumentos.first { it.id == pr.instrumentoId }
                        appendLine(
                            "Instrumento: ${instrumento.nombre} · ${instrumento.marcaModelo} · Serie ${instrumento.serie}"
                        )
                        val cal =
                            data.calibraciones
                                .filter {
                                    it.instrumentoId == instrumento.id &&
                                        it.desde <= p.fechaHora.take(10) &&
                                        it.hasta >= p.fechaHora.take(10)
                                }
                                .maxByOrNull { it.desde }
                        appendLine(
                            "Calibración: ${cal?.desde?:"Histórico no disponible"} / vence ${cal?.hasta?:"Sin respaldo en precarga"}"
                        )
                        data.valores(pr.id).forEach { (k, v) ->
                            appendLine(
                                "${Catalogo.etiquetas[k]}: ${numero(v)} ${Catalogo.unidades[k]}"
                            )
                        }
                        val valores = data.valores(pr.id)
                        if (pr.tipoId == "AISLACION" && valores["LECTURA_10MIN"] != null)
                            appendLine(
                                "IP: ${numero(valores.getValue("LECTURA_10MIN")/valores.getValue("LECTURA_1MIN"))}"
                            )
                        appendLine(
                            "Resultado: ${pr.resultado?.titulo?:"Pendiente de revisión manual"}"
                        )
                        if (!pr.opera) appendLine("Relé no opera")
                        if (pr.comentario.isNotBlank()) appendLine("Comentario: ${pr.comentario}")
                    }
                }
            data
                .comparaciones(id)
                .filter { ReglasPrueba.significativa(it.porcentaje) }
                .forEach {
                    appendLine(
                        "Variación significativa · ${it.elemento} · ${Catalogo.etiquetas[it.parametro]}: ${it.porcentaje?.let(::numero)} %"
                    )
                }
            data.historial
                .filter { it.protocoloId == id }
                .forEach {
                    appendLine(
                        "Trazabilidad: ${it.fechaHora} · ${data.nombre(it.usuarioId)} · ${it.nuevo.titulo} · ${it.motivo}"
                    )
                }
        }
    }

    fun acta(
        data: DatosApp,
        visitaId: String,
        nombre: String,
        rut: String,
        cargo: String,
        fecha: String,
    ): String {
        val v = data.visitas.first { it.id == visitaId }
        val instalacion = data.instalaciones.first { it.id == v.instalacionId }
        val cliente = data.usuarios.first { it.id == instalacion.clienteId }
        val protocolos = data.protocolos.filter { it.visitaId == visitaId }
        return buildString {
            appendLine(
                "INDUZTEK INGENIERÍA SpA\nACTA DE PRUEBA EN TERRENO\nPendiente de formalización"
            )
            appendLine("Una acta por visita · OT ${v.ot} · Fecha ${v.fecha}")

            if (protocolos.all { it.estado == Estado.ANULADO }) {
                appendLine("Cliente: ${cliente.nombre} · RUT ${cliente.rut}")
                appendLine(
                    "Instalación: ${instalacion.nombre} · ${instalacion.ubicacion} · ${instalacion.comuna} · ${data.regionDe(instalacion)}"
                )
                appendLine("Tipo de visita: ${v.tipo.titulo}")
                appendLine("Ingeniero a cargo: ${data.nombre(v.ingenieroId)}")
                appendLine(
                    "Personal participante que registró protocolos: ${protocolos.map { data.nombre(it.creadorId) }.distinct().joinToString(", ")}"
                )
                appendLine("Cierre registrado por: ${data.usuario.nombre}")
            }
            appendLine("Recibe: $nombre · RUT $rut · Cargo $cargo · Recepción $fecha")
            appendLine("La recepción acredita entrega del respaldo, no aprobación técnica.")
            protocolos.forEach { p ->
                if (p.estado == Estado.ANULADO) {
                    val equipo = data.equipos.first { it.id == p.equipoId }
                    appendLine("\n${p.numero}: Anulado · ${p.motivo}")
                    appendLine(
                        "Equipo: ${equipo.tag} · ${Catalogo.equipos[equipo.tipoId]} · Serie: ${equipo.serie}"
                    )
                    appendLine("Marca/modelo: ${equipo.marca} ${equipo.modelo}")
                    appendLine("Fecha de prueba: ${p.fechaHora} · Técnico: ${data.nombre(p.creadorId)}")
                    appendLine("Protocolo anulado: no se presentan mediciones ni resultados vigentes.")
                } else appendLine("\n" + protocolo(data, p.id))
            }
        }
    }
}
