package cl.induztek.protocol.model

import cl.induztek.protocol.data.*
import cl.induztek.protocol.domain.ReglasPrueba

data class Comparacion(
    val pruebaId: String,
    val parametro: String,
    val elemento: String,
    val actual: Double,
    val anterior: Double,
    val unidad: String,
    val porcentaje: Double?,
)

data class DatoGrafico(val etiqueta: String, val valor: Double)

data class DatosApp(
    val usuario: Usuario,
    val usuarios: List<Usuario>,
    val instalaciones: List<Instalacion>,
    val equipos: List<Equipo>,
    val visitas: List<Visita>,
    val protocolos: List<Protocolo>,
    val pruebas: List<Prueba>,
    val mediciones: List<Medicion>,
    val parametros: List<Parametro>,
    val instrumentos: List<Instrumento>,
    val calibraciones: List<Calibracion>,
    val historial: List<HistorialEstado>,
    val recepciones: List<Recepcion>,
    val regiones: List<Region>,
    val comunas: List<Comuna>,
) {
    fun regionDe(instalacion: Instalacion): String =
        regiones
            .first { region -> region.id == comunas.first { it.id == instalacion.comuna }.regionId }
            .nombre

    fun pruebasDe(id: String) = pruebas.filter { it.protocoloId == id }

    fun vigentes(id: String) =
        pruebasDe(id)
            .groupBy { it.tipoId to it.elemento }
            .values
            .map { grupo ->
                val realizadas = grupo.filter { it.realizada }
                (realizadas.ifEmpty { grupo }).maxBy { it.momento.ordinal }
            }

    fun resultado(id: String): Resultado? =
        ReglasPrueba.global(vigentes(id).filter { it.realizada }.map { it.resultado })

    fun valores(id: String): Map<String, Double> =
        mediciones
            .filter { it.pruebaId == id }
            .associate { m -> parametros.first { it.id == m.parametroId }.codigo to m.valor }

    fun nombre(id: String) =
        usuarios.firstOrNull { it.id == id }?.let { it.contacto.ifBlank { it.nombre } }
            ?: "Usuario no disponible"


    fun contexto(prueba: Prueba): String =
        valores(prueba.id)
            .filterKeys {
                it in
                    listOf("TENSION_PRUEBA", "POSICION_TAP", "CORRIENTE_AJUSTE", "TIEMPO_ESPERADO")
            }
            .toSortedMap()
            .entries
            .joinToString(", ") { (codigo, valor) ->
                "${Catalogo.etiquetas[codigo]} $valor ${Catalogo.unidades[codigo]}"
            }

    fun comparaciones(id: String): List<Comparacion> {
        val p = protocolos.first { it.id == id }
        val anteriores =
            protocolos
                .filter {
                    it.equipoId == p.equipoId &&
                        it.fechaHora < p.fechaHora &&
                        it.estado !in listOf(Estado.ANULADO, Estado.BORRADOR)
                }
                .sortedByDescending { it.fechaHora }
        return vigentes(id)
            .filter { it.realizada }
            .flatMap { actual ->
                val anterior =
                    anteriores.firstNotNullOfOrNull { previo ->
                        vigentes(previo.id).firstOrNull {
                            it.tipoId == actual.tipoId &&
                                it.elemento == actual.elemento &&
                                contexto(it) == contexto(actual) &&
                                it.realizada
                        }
                    } ?: return@flatMap emptyList()
                val va = valores(actual.id)
                val vp = valores(anterior.id)
                va.filterKeys { it !in listOf("POSICION_TAP", "TENSION_PRUEBA") }
                    .mapNotNull { (codigo, valor) ->
                        vp[codigo]?.let { ant ->
                            Comparacion(
                                actual.id,
                                codigo,
                                actual.elemento,
                                valor,
                                ant,
                                Catalogo.unidades[codigo].orEmpty(),
                                ReglasPrueba.variacion(valor, ant),
                            )
                        }
                    }
            }
    }
}
