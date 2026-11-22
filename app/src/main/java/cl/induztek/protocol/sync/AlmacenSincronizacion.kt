package cl.induztek.protocol.sync

import androidx.room.withTransaction
import cl.induztek.protocol.data.*
import cl.induztek.protocol.domain.Claves
import com.google.gson.*
import java.security.MessageDigest
import java.util.UUID


class AlmacenSincronizacion(private val db: InduztekDatabase) {
    private val dao = db.dao()
    private val gson = GsonBuilder().serializeNulls().create()

    private fun objeto(valor: Any) = gson.toJsonTree(valor).asJsonObject

    private fun grupo(registro: Any, vararg hijos: Pair<String, Any>) =
        JsonObject().apply {
            add("registro", gson.toJsonTree(registro))
            hijos.forEach { (k, v) -> add(k, gson.toJsonTree(v)) }
        }

    suspend fun exportar(): List<RegistroLocal> =
        db.withTransaction {
            val pruebas = dao.pruebas()
            val mediciones = dao.medicions()
            val historia = dao.historialEstados()
            val participantes = dao.participantes()
            val recepciones = dao.recepcions()
            val calibraciones = dao.calibracions()
            dao.instalacions().map { RegistroLocal("instalacion", it.id, grupo(it)) } +
                dao.equipos().map { RegistroLocal("equipo", it.id, grupo(it)) } +
                dao.instrumentos().map { ins ->
                    RegistroLocal(
                        "instrumento",
                        ins.id,
                        grupo(
                            ins,
                            "calibraciones" to calibraciones.filter { it.instrumentoId == ins.id },
                        ),
                    )
                } +
                dao.visitas().map { v ->
                    RegistroLocal(
                        "visita",
                        v.id,
                        grupo(
                            v,
                            "participantes" to participantes.filter { it.visitaId == v.id },
                            "recepciones" to recepciones.filter { it.visitaId == v.id },
                        ),
                    )
                } +
                dao.protocolos().map { p ->
                    val ps = pruebas.filter { it.protocoloId == p.id }
                    val ids = ps.map { it.id }.toSet()
                    RegistroLocal(
                        "protocolo",
                        p.id,
                        grupo(
                            p,
                            "pruebas" to ps,
                            "mediciones" to mediciones.filter { it.pruebaId in ids },
                            "historial" to historia.filter { it.protocoloId == p.id },
                        ),
                    )
                }
        }

    private fun publico(u: Usuario) =
        UsuarioRemoto(u.id, u.rolId, u.nombre, u.rut, u.correo, u.activo,
            u.contacto, u.cargo, u.giro, u.telefono)

    private fun huellaUsuario(u: UsuarioRemoto): String {
        val datos = gson.toJsonTree(u).asJsonObject
        datos.remove("version")
        return huella(datos)
    }


    suspend fun cuentasPendientes(): List<JsonObject> {
        val metas = dao.metas().associateBy { it.tipo to it.registroId }
        return dao.usuarios().mapNotNull { u ->
            val base = metas["usuario" to u.id]
            val clavePendiente = metas["clave-pendiente" to u.id]?.version == 0L
            if (base != null && base.huella == huellaUsuario(publico(u)) && !clavePendiente)
                null
            else objeto(u).apply {
                addProperty("version", base?.version ?: 0L)
                if (base != null && !clavePendiente) remove("claveHash")
            }
        }
    }

    suspend fun confirmarCuentas(enviadas: List<JsonObject>, aceptadas: List<UsuarioRemoto>) =
        db.withTransaction {
            val originales = enviadas.associateBy { it["id"].asString }
            aceptadas.forEach { r ->
                val enviada = originales[r.id] ?: error("Acuse de cuenta desconocida")
                dao.guardarMeta(SincronizacionMeta("usuario", r.id, r.version, huellaUsuario(r)))
                val hashEnviado = enviada["claveHash"]?.asString
                if (hashEnviado != null && dao.usuario(r.id)?.claveHash == hashEnviado)
                    dao.guardarMeta(SincronizacionMeta("clave-pendiente", r.id, 1, "confirmada"))
            }
        }


    suspend fun guardarAcceso(r: UsuarioRemoto, clave: String) = db.withTransaction {
        require(r.activo && r.version > 0) { "La cuenta remota no está disponible" }
        val anterior = dao.usuario(r.id)
        require(anterior == null || huellaUsuario(publico(anterior)) == huellaUsuario(r)) {
            "La cuenta tiene cambios locales pendientes; sincronízalos desde jefatura antes de validar el acceso"
        }
        val pendiente = dao.metas().any {
            it.tipo == "clave-pendiente" && it.registroId == r.id && it.version == 0L
        }
        require(!pendiente || anterior != null && Claves.verificar(clave, anterior.claveHash)) {
            "Hay una contraseña local pendiente de envío; sincroniza primero desde jefatura"
        }
        dao.importar(Usuario(r.id, r.rolId, r.nombre, r.rut, r.correo,
            Claves.crear(clave), r.activo, r.contacto, r.cargo, r.giro, r.telefono))
        dao.guardarMeta(SincronizacionMeta("usuario", r.id, r.version, huellaUsuario(r)))
        dao.guardarMeta(SincronizacionMeta("clave-pendiente", r.id, 1, "acceso validado"))
    }

    suspend fun usuarios(remotos: List<UsuarioRemoto>, semilla: List<UsuarioRemoto>) =
        db.withTransaction {
            val metas = dao.metas().associateBy { it.tipo to it.registroId }
            val baselines = semilla.associateBy { it.id }
            remotos.forEach { r ->
                require(r.version > 0) { "Actualiza el servicio de identidad para sincronizar cuentas" }
                val anterior = dao.usuario(r.id)
                val local = anterior?.let { huellaUsuario(publico(it)) }
                val remota = huellaUsuario(r)
                val meta = metas["usuario" to r.id]
                val base =
                    meta?.huella ?: baselines[r.id]?.let(::huellaUsuario)
                val versionBase = meta?.version ?: if (baselines.containsKey(r.id)) 1L else 0L
                val clavePendiente = metas["clave-pendiente" to r.id]?.version == 0L
                val cambiado = anterior != null && (local != base || clavePendiente)
                when {
                    anterior != null && meta == null && !baselines.containsKey(r.id) && local == remota -> Unit
                    clavePendiente && local == remota -> {

                        if (meta == null && base != null)
                            dao.guardarMeta(SincronizacionMeta("usuario", r.id, versionBase, base))
                    }
                    cambiado && r.version != versionBase && local != remota ->
                        error("Conflicto en la cuenta ${r.id}: se conserva la información local")
                    anterior == null || !cambiado || local == remota && !clavePendiente -> {

                        val hash = if (anterior != null && r.version > versionBase)
                            Claves.crear(UUID.randomUUID().toString())
                        else anterior?.claveHash ?: Claves.crear(UUID.randomUUID().toString())
                        dao.importar(
                            Usuario(
                                r.id,
                                r.rolId,
                                r.nombre,
                                r.rut,
                                r.correo,
                                hash,
                                r.activo,
                                r.contacto,
                                r.cargo,
                                r.giro,
                                r.telefono,
                            )
                        )
                        dao.guardarMeta(SincronizacionMeta("usuario", r.id, r.version, remota))
                    }
                    remota == base && r.version == versionBase -> {

                        if (meta == null)
                            dao.guardarMeta(SincronizacionMeta("usuario", r.id, r.version, remota))
                    }
                    else ->
                        error("Conflicto en la cuenta ${r.id}: se conserva la información local")
                }
            }
        }

    suspend fun confirmarClavePropia(r: UsuarioRemoto, nueva: String) = db.withTransaction {
        val local = requireNotNull(dao.usuario(r.id))
        val base = dao.metas().firstOrNull { it.tipo == "usuario" && it.registroId == r.id }
        dao.actualizar(local.copy(claveHash = Claves.crear(nueva)))
        val remoto = huellaUsuario(r)
        if (remoto == huellaUsuario(publico(local)) || remoto == base?.huella)
            dao.guardarMeta(SincronizacionMeta("usuario", r.id, r.version, remoto))
        dao.guardarMeta(SincronizacionMeta("clave-pendiente", r.id, 1, "confirmada"))
    }

    suspend fun metas() = dao.metas()

    suspend fun meta(r: RegistroRemoto) =
        dao.guardarMeta(SincronizacionMeta(r.tipo, r.id, r.version, huella(r.datos)))

    suspend fun inicializar(semilla: List<RegistroLocal>) =
        db.withTransaction {
            val presentes = dao.metas().map { it.tipo to it.registroId }.toSet()
            semilla
                .filter { (it.tipo to it.id) !in presentes }
                .forEach {
                    dao.guardarMeta(SincronizacionMeta(it.tipo, it.id, 0, huella(it.datos)))
                }
        }


    suspend fun reconciliarAccesoCliente(
        clienteId: String,
        remotos: List<RegistroRemoto>,
        semilla: List<RegistroLocal>,
    ) = db.withTransaction {
        val instalaciones = dao.instalacions().filter { it.clienteId == clienteId }.map { it.id }.toSet()
        val equipos = dao.equipos().filter { it.instalacionId in instalaciones }.map { it.id }.toSet()
        val idsPorTipo = mapOf(
            "instalacion" to instalaciones,
            "equipo" to equipos,
            "visita" to dao.visitas().filter { it.instalacionId in instalaciones }.map { it.id }.toSet(),
            "protocolo" to dao.protocolos().filter { it.equipoId in equipos }.map { it.id }.toSet(),
        )
        val recibidos = remotos.map { it.tipo to it.id }.toSet()
        val confirmados = dao.metas().filter { it.version > 0 && it.tipo in idsPorTipo }
            .map { it.tipo to it.registroId }.toSet()
        val iniciales = semilla.map { it.tipo to it.id }.toSet()
        idsPorTipo.forEach { (tipo, ids) ->
            ids.forEach { id ->
                val clave = tipo to id

                if (clave in recibidos || clave in confirmados || clave in iniciales) {
                    val visible = clave in recibidos
                    dao.guardarMeta(
                        SincronizacionMeta(
                            "acceso:$clienteId:$tipo", id,
                            if (visible) 1L else 0L,
                            if (visible) "autorizado" else "no incluido en snapshot autorizado",
                        )
                    )
                }
            }
        }
    }

    suspend fun aplicar(r: RegistroRemoto) =
        db.withTransaction {
            val d = r.datos
            val registro = d.getAsJsonObject("registro")
            fun <T> lista(nombre: String, tipo: Class<T>): List<T> =
                d.getAsJsonArray(nombre)?.map { gson.fromJson(it, tipo) } ?: emptyList()
            when (r.tipo) {
                "instalacion" -> dao.importar(gson.fromJson(registro, Instalacion::class.java))
                "equipo" -> dao.importar(gson.fromJson(registro, Equipo::class.java))
                "instrumento" -> {
                    dao.importar(gson.fromJson(registro, Instrumento::class.java))
                    lista("calibraciones", Calibracion::class.java).forEach { dao.importar(it) }
                }
                "visita" -> {
                    dao.importar(gson.fromJson(registro, Visita::class.java))
                    dao.borrarParticipantes(r.id)
                    dao.borrarRecepcion(r.id)
                    lista("participantes", Participante::class.java).forEach { dao.importar(it) }
                    lista("recepciones", Recepcion::class.java).forEach { dao.importar(it) }
                }
                "protocolo" -> {

                    dao.importar(gson.fromJson(registro, Protocolo::class.java))
                    dao.borrarMedicionesProtocolo(r.id)
                    dao.borrarPruebas(r.id)
                    dao.borrarHistorial(r.id)
                    lista("pruebas", Prueba::class.java).forEach { dao.importar(it) }
                    lista("mediciones", Medicion::class.java).forEach { dao.importar(it) }
                    lista("historial", HistorialEstado::class.java).forEach { dao.importar(it) }
                }
                else -> error("Tipo remoto desconocido")
            }
            meta(r)
        }

    companion object {

        fun canonico(n: JsonElement, omitirNulos: Boolean = false): String =
            when {
                n.isJsonObject ->
                    n.asJsonObject
                        .entrySet()
                        .filterNot { omitirNulos && it.value.isJsonNull }
                        .sortedBy { it.key }
                        .joinToString(",", "{", "}") {
                            Gson().toJson(it.key) + ":" + canonico(it.value, omitirNulos)
                        }
                n.isJsonArray -> {
                    val elementos = n.asJsonArray.toList()
                    val ordenados =
                        if (elementos.all { it.isJsonObject && it.asJsonObject.has("id") })
                            elementos.sortedBy { it.asJsonObject["id"].asString }
                        else elementos
                    ordenados.map { canonico(it, omitirNulos) }.joinToString(",", "[", "]")
                }
                n.isJsonPrimitive && n.asJsonPrimitive.isNumber ->
                    n.asBigDecimal.stripTrailingZeros().toPlainString()
                else -> n.toString()
            }

        fun huella(n: JsonElement, omitirNulos: Boolean = false): String =
            MessageDigest.getInstance("SHA-256").digest(canonico(n, omitirNulos).toByteArray()).joinToString(
                ""
            ) {
                "%02x".format(it)
            }
    }
}
