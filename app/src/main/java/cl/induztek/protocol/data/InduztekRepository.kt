package cl.induztek.protocol.data

import androidx.room.withTransaction
import cl.induztek.protocol.domain.*
import cl.induztek.protocol.model.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID


class InduztekRepository(private val db: InduztekDatabase) {
    private val dao = db.dao()
    private var sesionId: String? = null

    private fun id() = UUID.randomUUID().toString()

    private fun ahora() = Validacion.ahora().withNano(0).toString()

    private fun fallo(clave: String, mensaje: String): Nothing =
        throw ErrorFormulario(mapOf(clave to mensaje))

    private suspend fun actor(): Usuario =
        sesionId?.let { dao.usuario(it) }?.takeIf { it.activo } ?: error("Inicia sesión nuevamente")

    suspend fun restaurar(id: String?) {
        sesionId = id?.let { dao.usuario(it) }?.takeIf { it.activo }?.id
    }

    fun salir() {
        sesionId = null
    }

    suspend fun ingresar(datos: Map<String, String>): Usuario {
        Validacion.requeridos(datos, "correo", "clave")
        Validacion.correo(datos)
        val u = dao.usuarioPorCorreo(datos.getValue("correo").trim())
        if (u == null || !u.activo || !Claves.verificar(datos.getValue("clave"), u.claveHash))
            fallo("clave", "Correo o contraseña incorrectos, o cuenta inactiva")
        sesionId = u.id
        return u.copy(claveHash = "")
    }

    suspend fun cambiarClaveLocal(actual: String, nueva: String) = db.withTransaction {
        Validacion.validarCambioClave(actual, nueva)
        val usuario = actor()
        if (dao.metas().any { it.tipo == "usuario" && it.registroId == usuario.id && it.version > 0 })
            error("Conéctate al servidor para cambiar la contraseña de esta cuenta")
        if (!Claves.verificar(actual, usuario.claveHash)) fallo("actual", "Contraseña actual incorrecta")
        dao.actualizar(usuario.copy(claveHash = Claves.crear(nueva)))
    }

    suspend fun datos(): DatosApp =
        db.withTransaction {
            val u = actor()
            val cliente = u.id.takeIf { u.perfil == Perfil.CLIENTE }
            val protocolos = dao.protocolosVisibles(cliente)
            val visitas = dao.visitasVisibles(cliente)
            val pruebas = dao.pruebasVisibles(protocolos.map { it.id })
            DatosApp(
                u.copy(claveHash = ""),
                dao.usuarios()
                    .filter { cliente == null || it.perfil != Perfil.CLIENTE || it.id == cliente }
                    .map { it.copy(claveHash = "") },
                dao.instalacionesVisibles(cliente),
                dao.equiposVisibles(cliente),
                visitas,
                protocolos,
                pruebas,
                dao.medicionesVisibles(pruebas.map { it.id }),
                dao.parametros(),
                dao.instrumentos(),
                dao.calibracions(),
                dao.historialVisible(protocolos.map { it.id }),
                dao.recepcionesVisibles(visitas.map { it.id }),
                dao.regiones(),
                dao.comunas(),
            )
        }

    private suspend fun personal(): Usuario =
        actor().also { check(it.perfil.registra) { "El cliente tiene acceso de solo consulta" } }

    private suspend fun editable(p: Protocolo): Usuario =
        personal().also {
            check(Permisos.editar(it.perfil, it.id, p.creadorId, p.estado)) {
                "Solo el creador puede editar un Borrador u Observado"
            }
        }

    private suspend fun protocolo(id: String) =
        datos().protocolos.firstOrNull { it.id == id } ?: error("Protocolo no disponible")

    private suspend fun trazar(p: Protocolo, usuario: Usuario, siguiente: Estado, motivo: String) {
        val fecha = ahora()
        dao.actualizar(p.copy(estado = siguiente, motivo = motivo))
        dao.insertar(HistorialEstado(id(), p.id, usuario.id, p.estado, siguiente, fecha, motivo))
    }

    suspend fun guardarCliente(d: Map<String, String>) =
        db.withTransaction {
            personal()
            Validacion.requeridos(d, "nombre", "rut", "correo", "contacto", "cargo")
            Validacion.validarRut(d)
            Validacion.correo(d)
            val rut = Validacion.normalizarRut(d.getValue("rut"))
            if (dao.usuarios().any { Validacion.normalizarRut(it.rut) == rut })
                fallo("rut", "Ya existe ese RUT")
            if (dao.usuarioPorCorreo(d.getValue("correo").trim()) != null)
                fallo("correo", "El correo ya está registrado")
            dao.insertar(
                Usuario(
                    id(),
                    Perfil.CLIENTE.name,
                    d.getValue("nombre").trim(),
                    d.getValue("rut").trim(),
                    d.getValue("correo").trim().lowercase(),
                    Claves.crear(UUID.randomUUID().toString()),
                    activo = false,
                    contacto = d.getValue("contacto"),
                    cargo = d.getValue("cargo"),
                    giro = d["giro"].orEmpty(),
                    telefono = Validacion.telefonoChileno(d["telefono"].orEmpty()),
                )
            )
        }

    suspend fun habilitarCliente(d: Map<String, String>) =
        db.withTransaction {
            check(actor().perfil == Perfil.JEFE_TECNICO) { "Solo el jefe técnico otorga cuentas" }
            Validacion.requeridos(d, "clienteId", "clave")
            if (d.getValue("clave").length < 10) fallo("clave", "Usa al menos 10 caracteres")
            val u = dao.usuario(d.getValue("clienteId")) ?: error("Cliente inexistente")
            check(u.perfil == Perfil.CLIENTE)
            dao.actualizar(u.copy(activo = true, claveHash = Claves.crear(d.getValue("clave"))))
            dao.guardarMeta(SincronizacionMeta("clave-pendiente", u.id, 0, "cambio explícito"))
        }

    suspend fun guardarPersonal(d: Map<String, String>) =
        db.withTransaction {
            val jefe = actor()
            check(jefe.perfil == Perfil.JEFE_TECNICO) { "Solo el jefe técnico gestiona personal" }
            Validacion.requeridos(d, "nombre", "rut", "correo", "rolId", "activo")
            Validacion.validarRut(d)
            Validacion.correo(d)
            val rol = Perfil.valueOf(d.getValue("rolId"))
            check(rol != Perfil.CLIENTE)
            val existente = d["personalId"]?.takeIf { it.isNotBlank() }?.let { dao.usuario(it) }
            check(existente == null || existente.perfil != Perfil.CLIENTE)
            val activo = d["activo"] == "SI"
            if (existente?.id == jefe.id && rol != Perfil.JEFE_TECNICO)
                throw ErrorFormulario(
                    mapOf("rolId" to "No puedes quitar tu propio perfil de jefatura")
                )
            if (existente?.id == jefe.id && !activo)
                throw ErrorFormulario(
                    mapOf("activo" to "No puedes desactivar tu propia sesión de jefatura")
                )
            val otros = dao.usuarios().filter { it.id != existente?.id }
            val rut = Validacion.normalizarRut(d.getValue("rut"))
            if (otros.any { Validacion.normalizarRut(it.rut) == rut })
                throw ErrorFormulario(mapOf("rut" to "El RUT ya existe"))
            if (otros.any { it.correo.equals(d["correo"]?.trim(), true) })
                throw ErrorFormulario(mapOf("correo" to "El correo ya existe"))
            val clave = d["clave"].orEmpty()
            if ((existente == null || clave.isNotBlank()) && clave.length < 10)
                throw ErrorFormulario(mapOf("clave" to "Usa al menos 10 caracteres"))
            val hash = if (clave.isBlank()) existente!!.claveHash else Claves.crear(clave)
            val u =
                Usuario(
                    existente?.id ?: id(),
                    rol.name,
                    d.getValue("nombre").trim(),
                    d.getValue("rut").trim(),
                    d.getValue("correo").trim().lowercase(),
                    hash,
                    activo,
                )
            if (existente == null) dao.insertar(u) else dao.actualizar(u)
            if (existente == null || clave.isNotBlank())
                dao.guardarMeta(SincronizacionMeta("clave-pendiente", u.id, 0, "cambio explícito"))
        }

    suspend fun guardarInstalacion(d: Map<String, String>) =
        db.withTransaction {
            personal()
            Validacion.requeridos(d, "clienteId", "nombre", "ubicacion", "comuna")
            check(dao.usuario(d.getValue("clienteId"))?.perfil == Perfil.CLIENTE) {
                "La instalación debe pertenecer a una empresa cliente"
            }
            check(dao.comunas().any { it.id == d["comuna"] }) {
                "Selecciona una comuna del catálogo del caso"
            }
            dao.insertar(
                Instalacion(
                    id(),
                    d.getValue("clienteId"),
                    d.getValue("nombre").trim(),
                    d.getValue("ubicacion"),
                    d.getValue("comuna"),
                    d["acceso"].orEmpty(),
                )
            )
        }

    suspend fun guardarEquipo(d: Map<String, String>) =
        db.withTransaction {
            personal()
            Validacion.requeridos(d, "tag", "tipoId", "instalacionId", "marca", "modelo", "serie")
            check(d["tipoId"] in Catalogo.equipos)
            check(dao.instalacions().any { it.id == d["instalacionId"] })
            val tag = d.getValue("tag").trim().uppercase()
            if (
                dao.equipos().any {
                    it.instalacionId == d["instalacionId"] && it.tag.equals(tag, true)
                }
            )
                fallo(
                    "tag",
                    "TAG ya registrado en esta instalación. Usa el equipo existente desde Equipos",
                )
            val anio =
                d["anio"]
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        it.toIntOrNull()?.takeIf { y -> Validacion.fabricacionValida(y) }
                            ?: fallo("anio", "Año inválido")
                    }
            val max =
                if (d["tipoId"] in listOf("CELDA", "INTERRUPTOR"))
                    Validacion.numero(d, "maxContactos")
                else null
            dao.insertar(
                Equipo(
                    id(),
                    d.getValue("instalacionId"),
                    d.getValue("tipoId"),
                    tag,
                    d.getValue("marca"),
                    d.getValue("modelo"),
                    d.getValue("serie"),
                    anio,
                    Validacion.decimalOpcional(d, "potencia"),
                    d["tensiones"].orEmpty(),
                    Validacion.decimalOpcional(d, "corriente"),
                    max,
                    d["observaciones"].orEmpty(),
                )
            )
        }

    suspend fun fotoEquipo(equipoId: String, foto: ByteArray) =
        db.withTransaction {
            personal()
            check(foto.size <= 500_000) { "Imagen demasiado grande" }
            val e = dao.equipos().first { it.id == equipoId }
            dao.actualizar(e.copy(foto = foto))
        }

    suspend fun guardarVisita(d: Map<String, String>) =
        db.withTransaction {
            val u = personal()
            check(u.perfil.cierraVisita) {
                "El jefe técnico supervisa; la visita la inicia el personal de terreno"
            }
            Validacion.requeridos(d, "ot", "instalacionId", "ingenieroId", "fecha", "tipo")
            val fecha = Validacion.fecha(d, "fecha")
            if (fecha.isAfter(Validacion.hoy()))
                fallo("fecha", "Registra una visita ejecutada, no una planificación futura")
            check(dao.instalacions().any { it.id == d["instalacionId"] })
            check(
                dao.usuario(d.getValue("ingenieroId"))?.let {
                    it.activo && it.perfil == Perfil.INGENIERO
                } == true
            ) {
                "Asigna un ingeniero de pruebas activo"
            }
            if (dao.visitas().any { it.ot.equals(d["ot"]?.trim(), true) })
                fallo("ot", "La OT ya está registrada")
            val v =
                Visita(
                    id(),
                    d.getValue("instalacionId"),
                    d.getValue("ingenieroId"),
                    d.getValue("ot").trim(),
                    fecha.toString(),
                    TipoVisita.valueOf(d.getValue("tipo")),
                )
            dao.insertar(v)
            dao.insertar(Participante(id(), v.id, u.id))
        }

    private fun ambiente(d: Map<String, String>): Pair<Double, Double> {
        val t = d["temperatura"]?.replace(',', '.')?.toDoubleOrNull()
        if (t == null || !t.isFinite() || t < -273.15) fallo("temperatura", "Ingresa una temperatura válida en °C")
        val h = Validacion.numero(d, "humedad", false)
        if (h > 100) fallo("humedad", "La humedad debe estar entre 0 y 100 %")
        return t to h
    }

    suspend fun guardarProtocolo(d: Map<String, String>): String =
        db.withTransaction {
            val u = personal()
            Validacion.requeridos(d, "visitaId", "equipoId", "fechaHora", "temperatura", "humedad")
            val v = dao.visitas().first { it.id == d["visitaId"] }
            check(v.cierre == null) { "La visita ya está cerrada" }
            val e = dao.equipos().first { it.id == d["equipoId"] }
            check(e.instalacionId == v.instalacionId) {
                "El equipo debe pertenecer a la instalación visitada"
            }
            if (dao.protocolos().any { it.visitaId == v.id && it.equipoId == e.id })
                fallo("equipoId", "Ya existe un protocolo para este equipo en la visita")
            val fecha =
                try {
                    LocalDateTime.parse(d.getValue("fechaHora"))
                } catch (_: Exception) {
                    fallo("fechaHora", "Usa AAAA-MM-DDTHH:MM")
                }
            if (
                fecha.toLocalDate() != LocalDate.parse(v.fecha) ||
                    fecha.isAfter(Validacion.ahora())
            )
                fallo("fechaHora", "La fecha debe corresponder a la visita y no ser futura")
            if (e.anio?.let { !Validacion.fabricacionValida(it, fecha.toLocalDate()) } == true)
                fallo("fechaHora", "La prueba no puede ser anterior al año de fabricación")
            val (t, h) = ambiente(d)
            val reemplaza = d["reemplazaId"]?.takeIf { it.isNotBlank() }
            reemplaza?.let { original ->
                check(
                    dao.protocolos().none {
                        it.reemplazaId == original && it.estado != Estado.ANULADO
                    }
                ) {
                    "El original ya tiene un reemplazo en proceso o formalizado"
                }
                check(
                    dao.protocolos().any {
                        it.id == original && it.equipoId == e.id && it.estado == Estado.FORMALIZADO
                    }
                ) {
                    "Solo puede reemplazar un formalizado del mismo equipo"
                }
            }
            val nuevoId = id()
            val p =
                Protocolo(
                    nuevoId,
                    "PT-${fecha.year}-$nuevoId",
                    v.id,
                    e.id,
                    u.id,
                    fecha.toString(),
                    t,
                    h,
                    d["mantenimiento"].orEmpty(),
                    reemplazaId = reemplaza,
                )
            dao.insertar(p)
            dao.insertar(
                HistorialEstado(id(), p.id, u.id, null, Estado.BORRADOR, ahora(), "Creación")
            )
            if (dao.participantes().none { it.visitaId == v.id && it.usuarioId == u.id })
                dao.insertar(Participante(id(), v.id, u.id))
            p.id
        }

    suspend fun corregirProtocolo(protocoloId: String, d: Map<String, String>) =
        db.withTransaction {
            val p = protocolo(protocoloId)
            val u = editable(p)
            val (t, h) = ambiente(d)
            dao.actualizar(
                p.copy(temperatura = t, humedad = h, mantenimiento = d["mantenimiento"].orEmpty())
            )
            dao.insertar(
                HistorialEstado(
                    id(),
                    p.id,
                    u.id,
                    p.estado,
                    p.estado,
                    ahora(),
                    "Corrección de ambiente/mantenimiento",
                )
            )
        }

    suspend fun guardarPrueba(protocoloId: String, d: Map<String, String>) =
        db.withTransaction {
            val p = protocolo(protocoloId)
            val u = editable(p)
            Validacion.requeridos(d, "tipoId", "elemento", "momento", "realizada")
            val e = dao.equipos().first { it.id == p.equipoId }
            val tipo = d.getValue("tipoId")
            check(tipo in Catalogo.tipos(e.tipoId)) { "Prueba incompatible con el equipo" }
            check(d["elemento"] in Catalogo.elementos(e.tipoId, tipo))
            val momento = Momento.valueOf(d.getValue("momento"))
            val visita = dao.visitas().first { it.id == p.visitaId }
            if (visita.tipo == TipoVisita.SOLO_PRUEBAS && momento != Momento.UNICA)
                fallo("momento", "Visita de solo pruebas: usa Única")
            val anteriores =
                dao.pruebas().filter {
                    it.protocoloId == p.id && it.tipoId == tipo && it.elemento == d["elemento"]
                }
            if (momento == Momento.UNICA && anteriores.any { it.momento != Momento.UNICA })
                fallo("momento", "Ya hay mediciones de mantenimiento; usa Previa o Posterior")
            if (momento != Momento.UNICA && anteriores.any { it.momento == Momento.UNICA })
                fallo(
                    "momento",
                    "Ya existe una medición Única; corrígela antes de crear otro ciclo",
                )
            if (
                momento == Momento.POSTERIOR &&
                    anteriores.none { it.momento == Momento.PREVIA && it.realizada }
            )
                fallo("momento", "Primero registra la medición Previa realizada")
            if (momento == Momento.POSTERIOR && p.mantenimiento.isBlank())
                fallo("momento", "Agrega la descripción del mantenimiento al protocolo")
            val realizada = d["realizada"] == "SI"
            var instrumento: Instrumento? = null
            var resultado: Resultado? = null
            val valores = mutableMapOf<String, Double>()
            val opera = d["opera"] != "NO"
            if (!realizada) Validacion.requeridos(d, "motivo")
            else {
                Validacion.requeridos(d, "instrumentoId")
                instrumento =
                    dao.instrumentos().firstOrNull {
                        it.id == d["instrumentoId"] && it.tipoPruebaId == tipo
                    } ?: fallo("instrumentoId", "Selecciona un instrumento compatible")
                val fecha = LocalDateTime.parse(p.fechaHora).toLocalDate().toString()
                if (
                    dao.calibracions().none {
                        it.instrumentoId == instrumento.id && it.desde <= fecha && it.hasta >= fecha
                    }
                )
                    fallo(
                        "instrumentoId",
                        "No hay calibración vigente a la fecha de la prueba ($fecha)",
                    )
                Catalogo.parametros(e.tipoId, tipo).forEach { codigo ->


                    if (
                        tipo != "OPERACION_RELE" ||
                            opera ||
                            codigo !in listOf("CORRIENTE_OPERACION", "TIEMPO_MEDIDO")
                    ) {
                        val numero =
                            Validacion.numero(
                                d,
                                codigo,
                                codigo !in
                                    listOf(
                                        "POSICION_TAP",
                                        "RESISTENCIA",
                                        "RESISTENCIA_CONTACTO",
                                        "TIEMPO_MEDIDO",
                                    ),
                            )
                        if (codigo == "POSICION_TAP" && numero % 1.0 != 0.0)
                            fallo(codigo, "El tap debe ser entero")
                        val valorBase =
                            if (
                                codigo in listOf("LECTURA_1MIN", "LECTURA_10MIN") &&
                                    d["unidad"] == "GΩ"
                            )
                                numero * 1000
                            else numero
                        if (!valorBase.isFinite())
                            fallo(
                                codigo,
                                "La medición es demasiado grande para convertirla a MΩ. Revisa el valor y la unidad",
                            )
                        valores[codigo] = valorBase
                    }
                }
                resultado = ReglasPrueba.resultado(e.tipoId, tipo, valores, e.maxContactos, opera)
            }
            val anterior = anteriores.firstOrNull { it.momento == momento }
            val prueba =
                Prueba(
                    anterior?.id ?: id(),
                    p.id,
                    tipo,
                    d.getValue("elemento"),
                    momento,
                    realizada,
                    if (realizada) "" else d.getValue("motivo"),
                    instrumento?.id,
                    resultado,
                    d["comentario"].orEmpty(),
                    opera,
                )
            if (anterior == null) dao.insertar(prueba)
            else {
                dao.actualizar(prueba)
                dao.borrarMediciones(anterior.id)
            }
            valores.forEach { (codigo, valor) ->
                dao.insertar(Medicion(id(), prueba.id, "$tipo:$codigo", valor))
            }
            dao.insertar(
                HistorialEstado(
                    id(),
                    p.id,
                    u.id,
                    p.estado,
                    p.estado,
                    ahora(),
                    "${if(anterior==null)"Registro" else "Corrección"} $tipo · ${prueba.elemento} · $momento",
                )
            )
        }

    suspend fun revisarPrueba(protocoloId: String, pruebaId: String, d: Map<String, String>) =
        db.withTransaction {
            val u = actor()
            check(u.perfil.revisa)
            val p = protocolo(protocoloId)
            check(p.estado == Estado.REGISTRADO_TERRENO)
            Validacion.requeridos(d, "resultado", "comentario")
            val pr =
                dao.pruebas().first { it.id == pruebaId && it.protocoloId == p.id && it.realizada }
            val e = dao.equipos().first { it.id == p.equipoId }
            check(
                pr.tipoId == "DEVANADOS" ||
                    (pr.tipoId == "AISLACION" && e.tipoId in listOf("CELDA", "INTERRUPTOR"))
            ) {
                "Esta prueba tiene criterio automático"
            }
            dao.actualizar(
                pr.copy(
                    resultado = Resultado.valueOf(d.getValue("resultado")),
                    comentario = d.getValue("comentario"),
                )
            )
            dao.insertar(
                HistorialEstado(
                    id(),
                    p.id,
                    u.id,
                    p.estado,
                    p.estado,
                    ahora(),
                    "Criterio manual ${pr.tipoId} · ${pr.elemento}: ${d.getValue("resultado")} · ${d.getValue("comentario")}",
                )
            )
        }

    suspend fun cambiarEstado(protocoloId: String, siguiente: Estado, d: Map<String, String>) =
        db.withTransaction {
            val u = actor()
            val p = protocolo(protocoloId)
            check(Permisos.transicion(u.perfil, p.estado, siguiente)) { "Transición no autorizada" }
            if (siguiente == Estado.REGISTRADO_TERRENO) {
                editable(p)
                check(p.temperatura != null && p.humedad != null) {
                    "Faltan condiciones ambientales"
                }
                val e = dao.equipos().first { it.id == p.equipoId }
                val pruebas = dao.pruebas().filter { it.protocoloId == p.id }
                val faltan =
                    Catalogo.tipos(e.tipoId).flatMap { tipo ->
                        Catalogo.elementos(e.tipoId, tipo)
                            .filter { elemento ->
                                pruebas.none { it.tipoId == tipo && it.elemento == elemento }
                            }
                            .map { "$tipo · $it" }
                    }
                check(faltan.isEmpty()) {
                    "Registra o justifica cada prueba pendiente: ${faltan.joinToString()}"
                }
                check(pruebas.any { it.realizada }) { "Se requiere al menos una prueba realizada" }
            }
            if (siguiente == Estado.FORMALIZADO) {
                check(dao.visitas().first { it.id == p.visitaId }.cierre != null) {
                    "Primero cierra la visita y emite el acta preliminar"
                }
                val data = datos()
                check(data.vigentes(p.id).filter { it.realizada }.all { it.resultado != null }) {
                    "El ingeniero debe resolver las pruebas con resultado manual pendiente"
                }
                if (data.comparaciones(p.id).any { ReglasPrueba.significativa(it.porcentaje) })
                    Validacion.requeridos(d, "motivo")
            }
            if (siguiente in listOf(Estado.OBSERVADO, Estado.ANULADO))
                Validacion.requeridos(d, "motivo")
            trazar(p, u, siguiente, d["motivo"].orEmpty())
        }

    suspend fun cerrarVisita(visitaId: String, d: Map<String, String>) =
        db.withTransaction {
            val u = actor()
            check(u.perfil.cierraVisita) { "Solo técnico o ingeniero cierra visitas" }
            val data = datos()
            val v = data.visitas.first { it.id == visitaId }
            check(v.cierre == null) { "Visita ya cerrada" }
            check(dao.participantes().any { it.visitaId == v.id && it.usuarioId == u.id }) {
                "Solo un participante de la visita puede cerrarla"
            }
            val protocolos = data.protocolos.filter { it.visitaId == v.id }
            check(protocolos.isNotEmpty()) { "La visita no tiene protocolos" }
            check(
                protocolos.all { it.estado in listOf(Estado.REGISTRADO_TERRENO, Estado.ANULADO) }
            ) {
                "Todos los protocolos deben estar Registrados en terreno o Anulados"
            }
            Validacion.requeridos(d, "nombre", "rut", "cargo")
            Validacion.validarRut(d)
            val rut = d.getValue("rut").trim()
            val fecha = ahora()
            val documento =
                Documentos.acta(
                    data,
                    v.id,
                    d.getValue("nombre"),
                    rut,
                    d.getValue("cargo"),
                    fecha,
                )
            dao.actualizar(v.copy(cierre = fecha))
            dao.insertar(
                Recepcion(
                    id(),
                    v.id,
                    d.getValue("nombre"),
                    rut,
                    d.getValue("cargo"),
                    fecha,
                    documento,
                )
            )
        }

    suspend fun guardarInstrumento(d: Map<String, String>) =
        db.withTransaction {
            check(actor().perfil == Perfil.JEFE_TECNICO) {
                "Solo el jefe técnico gestiona instrumentos y calibraciones"
            }
            Validacion.requeridos(
                d,
                "nombre",
                "marcaModelo",
                "serie",
                "tipoPruebaId",
                "desde",
                "hasta",
            )
            check(d["tipoPruebaId"] in Catalogo.pruebas)
            val desde = Validacion.fecha(d, "desde")
            val hasta = Validacion.fecha(d, "hasta")
            if (desde.isAfter(Validacion.hoy()))
                fallo("desde", "La calibración realizada no puede tener fecha futura")
            if (hasta < desde)
                fallo("hasta", "El vencimiento no puede ser anterior a la calibración")
            val existente =
                dao.instrumentos().firstOrNull { it.serie.equals(d["serie"]?.trim(), true) }
            if (existente != null && existente.tipoPruebaId != d["tipoPruebaId"])
                fallo("serie", "La serie pertenece a otro tipo de instrumento")
            val instrumento =
                existente
                    ?: Instrumento(
                            id(),
                            d.getValue("tipoPruebaId"),
                            d.getValue("nombre"),
                            d.getValue("marcaModelo"),
                            d.getValue("serie").trim(),
                        )
                        .also { dao.insertar(it) }
            if (existente != null) {
                dao.actualizar(
                    instrumento.copy(
                        nombre = d.getValue("nombre"),
                        marcaModelo = d.getValue("marcaModelo"),
                    )
                )
            }
            dao.insertar(Calibracion(id(), instrumento.id, desde.toString(), hasta.toString()))
        }

    suspend fun documentoProtocolo(id: String): String = Documentos.protocolo(datos(), id)

    suspend fun documentoActa(id: String): String =
        datos().recepciones.firstOrNull { it.visitaId == id }?.documento
            ?: error("El acta aún no se ha emitido o no está autorizada")
}
