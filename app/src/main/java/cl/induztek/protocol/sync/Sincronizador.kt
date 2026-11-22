package cl.induztek.protocol.sync

import android.content.Context
import androidx.room.withTransaction
import androidx.work.*
import cl.induztek.protocol.data.InduztekDatabase
import cl.induztek.protocol.domain.ErrorFormulario
import cl.induztek.protocol.domain.Validacion
import cl.induztek.protocol.model.Perfil
import com.google.gson.JsonElement
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.net.URI
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


interface PlanificadorSincronizacion {
    fun programar()

    fun cancelar()
}

class PlanificadorWorkManager(private val context: Context) : PlanificadorSincronizacion {
    override fun cancelar() {
        WorkManager.getInstance(context).cancelUniqueWork("induztek-sync-inmediata")
        WorkManager.getInstance(context).cancelUniqueWork("induztek-sync-periodica")
    }

    override fun programar() {
        val condicion = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val periodic =
            PeriodicWorkRequestBuilder<SincronizacionWorker>(15, TimeUnit.MINUTES)
                .setConstraints(condicion)
                .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                "induztek-sync-periodica",
                ExistingPeriodicWorkPolicy.KEEP,
                periodic,
            )
        val ahora =
            OneTimeWorkRequestBuilder<SincronizacionWorker>()
                .setConstraints(condicion)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("induztek-sync-inmediata", ExistingWorkPolicy.KEEP, ahora)
    }
}

class Sincronizador(
    private val context: Context,
    private val db: InduztekDatabase,
    private val planificador: PlanificadorSincronizacion = PlanificadorWorkManager(context),
) {
    private val prefs = context.getSharedPreferences("sincronizacion", Context.MODE_PRIVATE)
    private val almacen = AlmacenSincronizacion(db)
    private val mutex = Mutex()
    private val _estado =
        MutableStateFlow(
            prefs
                .getString(
                    "estado",
                    "Sin conexión configurada. Los datos se guardan en este dispositivo.",
                )
                .orEmpty()
        )
    val estado = _estado.asStateFlow()
    val configurado
        get() = !prefs.getString("token", null).isNullOrEmpty()

    fun urlIdentidad() = prefs.getString("identidad", "http://10.0.2.2:8080/").orEmpty()

    fun urlProtocolos() = prefs.getString("protocolos", "http://10.0.2.2:8080/").orEmpty()

    private fun comunicar(texto: String) {
        prefs.edit().putString("estado", texto).apply()
        _estado.value = texto
    }

    private inline fun <reified T> api(url: String): T =
        Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(T::class.java)

    fun validarUrl(valor: String): String {
        val uri =
            runCatching { URI(valor.trim()) }.getOrNull() ?: error("Dirección de servicio inválida")
        require(
            uri.userInfo == null &&
                uri.query == null &&
                uri.fragment == null &&
                !uri.host.isNullOrBlank()
        ) {
            "Usa una dirección de servicio sin credenciales"
        }
        require(
            uri.scheme == "https" ||
                uri.scheme == "http" && uri.host in listOf("10.0.2.2", "127.0.0.1", "localhost")
        ) {
            "Usa HTTPS. HTTP solo está habilitado para el servidor local del emulador"
        }
        return valor.trim().trimEnd('/') + "/"
    }

    private fun direccionDeFormulario(valor: String, campo: String): String =
        try {
            validarUrl(valor)
        } catch (_: Exception) {
            throw ErrorFormulario(
                mapOf(campo to "Usa una dirección HTTPS válida o el servidor local del emulador")
            )
        }

    suspend fun conectar(
        idUsuario: String,
        identidad: String,
        protocolos: String,
        correo: String,
        clave: String,
    ) {
        val datos = mapOf(
            "identidad" to identidad,
            "protocolos" to protocolos,
            "correo" to correo.trim(),
            "clave" to clave,
        )
        Validacion.requeridos(datos, "identidad", "protocolos", "correo", "clave")
        Validacion.correo(datos)
        val a = direccionDeFormulario(identidad, "identidad")
        val b = direccionDeFormulario(protocolos, "protocolos")
        val sesion = try {
            api<IdentidadApi>(a).ingresar(CredencialesRemotas(correo.trim(), clave))
        } catch (e: HttpException) {
            if (e.code() == 401)
                throw ErrorFormulario(
                    mapOf("clave" to "Correo o contraseña incorrectos, o cuenta inactiva")
                )
            throw e
        } catch (_: java.io.IOException) {
            throw ErrorFormulario(mapOf("identidad" to "No se pudo acceder al servicio de identidad"))
        }
        if (sesion.usuario.id != idUsuario) {
            throw ErrorFormulario(
                mapOf("correo" to "Conecta la misma cuenta que inició sesión en la aplicación")
            )
        }
        prefs
            .edit()
            .putString("identidad", a)
            .putString("protocolos", b)
            .putString("usuario", idUsuario)
            .putString("token", sesion.token)
            .apply()
        comunicar("Conexión configurada. Sincronización pendiente.")
        programar()
        sincronizar()
    }


    suspend fun ingresarDesdeServidor(identidad: String, correo: String, clave: String): UsuarioRemoto =
        mutex.withLock {
            val url = direccionDeFormulario(identidad, "identidad")
            val sesion = try {
                api<IdentidadApi>(url).ingresar(CredencialesRemotas(correo.trim(), clave))
            } catch (e: HttpException) {
                if (e.code() == 401)
                    throw ErrorFormulario(mapOf("clave" to "Correo o contraseña incorrectos, o cuenta inactiva"))
                throw e
            } catch (_: java.io.IOException) {
                throw ErrorFormulario(mapOf("identidad" to "No se pudo acceder al servidor. El primer acceso requiere conexión; las cuentas ya validadas pueden entrar en modo local"))
            }
            val semilla = context.assets.open("usuarios-demo.json").bufferedReader().use { r ->
                JsonParser.parseReader(r).asJsonArray.map { Gson().fromJson(it, UsuarioRemoto::class.java) }
            }
            almacen.usuarios(listOf(sesion.usuario), semilla)
            almacen.guardarAcceso(sesion.usuario, clave)
            prefs.edit()
                .putString("identidad", url)
                .putString("protocolos", url)
                .putString("usuario", sesion.usuario.id)
                .putString("token", sesion.token)
                .apply()
            comunicar("Cuenta validada. Sincronización pendiente.")
            sesion.usuario
        }

    suspend fun solicitarRecuperacion(direccion: String, correo: String) {
        Validacion.correo(mapOf("correo" to correo))
        val remoto = api<IdentidadApi>(direccionDeFormulario(direccion, "identidad"))
        if (!remoto.solicitarRecuperacion(SolicitudRecuperacion(correo.trim())).isSuccessful)
            error("La recuperación por correo no está disponible en este servidor")
    }

    suspend fun recuperarClave(direccion: String, correo: String, codigo: String, nueva: String) {
        Validacion.correo(mapOf("correo" to correo))
        Validacion.validarClaveNueva(nueva)
        if (!codigo.trim().matches(Regex("[A-Za-z0-9_-]{32}")))
            throw ErrorFormulario(mapOf("codigo" to "Ingresa el código completo de tu correo"))
        val url = direccionDeFormulario(direccion, "identidad")
        val remoto = api<IdentidadApi>(url)
        val respuesta = remoto.recuperarClave(ConfirmacionRecuperacion(correo.trim(), codigo.trim(), nueva))
        if (!respuesta.isSuccessful) {
            if (respuesta.code() == 400)
                throw ErrorFormulario(mapOf("codigo" to "Código inválido o vencido"))
            error("El servidor no pudo completar la recuperación")
        }
        prefs.edit().putString("identidad", url).putString("protocolos", url).apply()
    }

    suspend fun cuentaRemota(id: String): Boolean =
        prefs.getString("usuario", null) == id ||
            almacen.metas().any { it.tipo == "usuario" && it.registroId == id && it.version > 0 }

    suspend fun cambiarClaveRemota(id: String, correo: String, actual: String, nueva: String) =
        mutex.withLock {
            Validacion.validarCambioClave(actual, nueva)
            val identidad = api<IdentidadApi>(urlIdentidad())
            val sesion = try {
                val acceso = identidad.ingresar(CredencialesRemotas(correo, actual))
                check(acceso.usuario.id == id) { "La cuenta del servidor no coincide con tu sesión" }
                identidad.cambiarClave("Bearer " + acceso.token, CambioClaveRemoto(actual, nueva))
            } catch (e: HttpException) {
                when (e.code()) {
                    401, 403 -> throw ErrorFormulario(mapOf("actual" to "Contraseña actual incorrecta o cuenta inactiva"))
                    else -> error("El servidor no pudo cambiar la contraseña")
                }
            } catch (_: java.io.IOException) {
                error("No se pudo confirmar el cambio. Comprueba la conexión y vuelve a validar tu cuenta.")
            }
            almacen.confirmarClavePropia(sesion.usuario, nueva)
            if (prefs.getString("usuario", null) == id)
                prefs.edit().putString("token", sesion.token).apply()
            Unit
        }

    fun desconectar() {
        if (configurado) planificador.cancelar()
        prefs.edit().remove("token").remove("usuario").apply()
        comunicar("Sincronización desconectada. Los datos locales se conservan.")
    }

    fun programar() {
        if (configurado) planificador.programar()
    }

    suspend fun sincronizar() =
        mutex.withLock {
            if (!configurado) return@withLock
            val usuarioId = prefs.getString("usuario", null) ?: return@withLock
            val token = "Bearer " + prefs.getString("token", null)
            comunicar("Sincronizando… Los registros locales siguen conservados.")
            try {
                val usuario =
                    db.dao().usuario(usuarioId) ?: error("La cuenta local no está disponible")
                val identidad = api<IdentidadApi>(urlIdentidad())
                val protocolos = api<ProtocolosApi>(urlProtocolos())
                val semilla =
                    context.assets.open("registros-demo.json").bufferedReader().use { r ->
                        JsonParser.parseReader(r).asJsonArray.map { e ->
                            val o = e.asJsonObject
                            RegistroLocal(
                                o["tipo"].asString,
                                o["id"].asString,
                                o["datos"].asJsonObject,
                            )
                        }
                    }
                almacen.inicializar(semilla)
                val usuariosSemilla =
                    context.assets.open("usuarios-demo.json").bufferedReader().use { r ->
                        JsonParser.parseReader(r).asJsonArray.map {
                            Gson().fromJson(it, UsuarioRemoto::class.java)
                        }
                    }
                almacen.usuarios(identidad.usuarios(token), usuariosSemilla)

                val cuentas = almacen.cuentasPendientes()
                if (usuario.perfil != Perfil.CLIENTE) {
                    val clientes = cuentas.filter {
                        it["rolId"].asString == Perfil.CLIENTE.name &&
                            (usuario.perfil == Perfil.JEFE_TECNICO || !it["activo"].asBoolean)
                    }
                    if (clientes.isNotEmpty())
                        almacen.confirmarCuentas(clientes, identidad.clientes(token, clientes))
                }
                if (usuario.perfil == Perfil.JEFE_TECNICO) {
                    val personal = cuentas.filter { it["rolId"].asString != Perfil.CLIENTE.name }
                    if (personal.isNotEmpty())
                        almacen.confirmarCuentas(personal, identidad.personal(token, personal))
                }
                val clienteId = usuario.id.takeIf { usuario.perfil == Perfil.CLIENTE }
                descargar(protocolos.descargar(token), clienteId, semilla)
                if (clienteId != null) {
                    comunicar("Sincronización completada · ${LocalDateTime.now().withNano(0)}")
                    return@withLock
                }
                if (usuario.perfil != Perfil.CLIENTE) {
                    val metas = almacen.metas().associateBy { it.tipo to it.registroId }
                    val cambios =
                        almacen
                            .exportar()
                            .filter { r ->
                                !coincideHuella(r.datos, metas[r.tipo to r.id]?.huella)
                            }
                            .filter { r ->
                                val dato = r.datos.getAsJsonObject("registro")
                                when (r.tipo) {
                                    "instrumento" -> usuario.perfil == Perfil.JEFE_TECNICO
                                    "protocolo" ->
                                        dato["creadorId"].asString == usuario.id ||
                                            usuario.perfil.revisa &&
                                                r.datos.getAsJsonArray("historial").any {
                                                    it.asJsonObject["usuarioId"].asString ==
                                                        usuario.id
                                                }
                                    "visita" ->
                                        usuario.perfil.cierraVisita &&
                                            r.datos.getAsJsonArray("participantes").any {
                                                it.asJsonObject["usuarioId"].asString == usuario.id
                                            }
                                    else -> true
                                }
                            }
                            .map { r ->
                                val version = metas[r.tipo to r.id]?.version ?: 0
                                CambioRemoto(
                                    r.tipo,
                                    r.id,
                                    version,
                                    AlmacenSincronizacion.huella(
                                        Gson()
                                            .toJsonTree(
                                                listOf(
                                                    usuarioId,
                                                    r.tipo,
                                                    r.id,
                                                    version,
                                                    AlmacenSincronizacion.huella(r.datos),
                                                )
                                            )
                                    ),
                                    r.datos,
                                )
                            }
                    if (cambios.isNotEmpty()) {
                        val aceptados = protocolos.enviar(token, cambios)
                        db.withTransaction { aceptados.forEach { almacen.meta(it) } }
                    }
                }
                descargar(protocolos.descargar(token))
                try {
                    val registro = api<BitacoraApi>(urlProtocolos()).registrar(
                        token,
                        EventoSincronizacion(java.util.UUID.randomUUID().toString()),
                    )
                    registro.body()?.close()
                    registro.errorBody()?.close()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: java.io.IOException) {
                    Unit
                }
                comunicar("Sincronización completada · ${LocalDateTime.now().withNano(0)}")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val motivo =
                    if (e is HttpException) {
                        val json =
                            runCatching {
                                    JsonParser.parseString(e.response()?.errorBody()?.string())
                                        .asJsonObject
                                        .get("mensaje")
                                        ?.asString
                                }
                                .getOrNull()
                        when (e.code()) {
                            401 ->
                                "Vuelve a conectar la cuenta: la sesión remota no está disponible"
                            403 -> "El servidor rechazó una operación por permisos"
                            409 -> json ?: "Conflicto de datos: se conserva la copia local"
                            else -> "El servidor no pudo completar la sincronización (${e.code()})"
                        }
                    } else if (e is java.io.IOException)
                        "Sin acceso al servidor. Los datos siguen guardados aquí; se reintentará cuando haya conexión"
                    else e.message ?: "Sincronización pendiente"
                comunicar(motivo)
                throw e
            }
        }

    private fun coincideHuella(datos: JsonElement, huella: String?): Boolean =
        huella != null && (AlmacenSincronizacion.huella(datos) == huella ||
            AlmacenSincronizacion.huella(datos, omitirNulos = true) == huella)

    private suspend fun descargar(
        remotos: List<RegistroRemoto>,
        clienteId: String? = null,
        semilla: List<RegistroLocal> = emptyList(),
    ) =
        db.withTransaction {
            val locales = almacen.exportar().associateBy { it.tipo to it.id }
            val metas = almacen.metas().associateBy { it.tipo to it.registroId }
            val orden =
                mapOf(
                    "instalacion" to 0,
                    "equipo" to 1,
                    "instrumento" to 2,
                    "visita" to 3,
                    "protocolo" to 4,
                )
            remotos
                .sortedWith(
                    compareBy(
                        { orden[it.tipo] ?: 99 },
                        { it.datos.getAsJsonObject("registro").get("fechaHora")?.asString ?: "" },
                    )
                )
                .forEach { remoto ->
                    val key = remoto.tipo to remoto.id
                    val local = locales[key]
                    val meta = metas[key]
                    val huellaRemota = AlmacenSincronizacion.huella(remoto.datos, omitirNulos = true)
                    if (local != null && coincideHuella(local.datos, huellaRemota))
                        almacen.meta(remoto)
                    else if (local == null || coincideHuella(local.datos, meta?.huella))
                        almacen.aplicar(remoto)
                    else if (meta != null && meta.version == 0L && coincideHuella(remoto.datos, meta.huella))
                        almacen.meta(remoto)
                    else if (meta != null && remoto.version == meta.version) Unit
                    else
                        error(
                            "Conflicto en ${remoto.tipo} ${remoto.id}. Se conservaron tus datos locales; requiere revisión antes de enviar."
                        )
                }
            if (clienteId != null)
                almacen.reconciliarAccesoCliente(clienteId, remotos, semilla)
        }
}

class SincronizacionWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        try {
            (applicationContext as cl.induztek.protocol.InduztekApplication)
                .sincronizador
                .sincronizar()
            Result.success()
        } catch (e: HttpException) {
            if (e.code() in listOf(401, 403, 409)) Result.failure() else Result.retry()
        } catch (_: java.io.IOException) {
            Result.retry()
        } catch (_: Exception) {
            Result.failure()
        }
}
