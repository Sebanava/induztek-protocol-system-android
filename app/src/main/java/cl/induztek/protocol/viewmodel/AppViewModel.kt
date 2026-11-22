package cl.induztek.protocol.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cl.induztek.protocol.InduztekApplication
import cl.induztek.protocol.data.DatosSemilla
import cl.induztek.protocol.domain.Validacion
import cl.induztek.protocol.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class Accion {
    LOGIN,
    CLIENTE,
    CUENTA,
    INSTALACION,
    EQUIPO,
    VISITA,
    PROTOCOLO,
    CORREGIR,
    PRUEBA,
    MANUAL,
    REGISTRAR,
    FORMALIZAR,
    OBSERVAR,
    ANULAR,
    CERRAR,
    INSTRUMENTO,
    PERSONAL,
}

data class AppUiState(
    val cargando: Boolean = true,
    val ocupado: Boolean = false,
    val datos: DatosApp? = null,
    val errorInicio: String? = null,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as InduztekApplication
    private val repo = app.repository
    private val preferencias = application.getSharedPreferences("sesion", 0)
    private val _estado = MutableStateFlow(AppUiState())
    val estado = _estado.asStateFlow()

    init {
        iniciar()
        viewModelScope.launch {
            app.sincronizador.estado
                .filter { it.startsWith("Sincronización completada") }
                .collect { recargar() }
        }
    }

    fun iniciar() {
        viewModelScope.launch {
            try {
                val datos =
                    withContext(Dispatchers.IO) {
                        DatosSemilla.preparar(app.database)
                        repo.restaurar(preferencias.getString("usuarioId", null))
                        runCatching { repo.datos() }.getOrNull()
                    }
                _estado.value = AppUiState(cargando = false, datos = datos)
                if (datos != null) app.sincronizador.programar()
            } catch (e: Exception) {
                _estado.value =
                    AppUiState(
                        cargando = false,
                        errorInicio = e.message ?: "Error al abrir la base de datos",
                    )
            }
        }
    }

    val sincronizador
        get() = app.sincronizador

    suspend fun cambiarClave(actual: String, nueva: String, confirmar: String): String =
        withContext(Dispatchers.IO) {
            Validacion.validarCambioClave(actual, nueva)
            if (confirmar != nueva)
                throw cl.induztek.protocol.domain.ErrorFormulario(mapOf("confirmar" to "Las contraseñas no coinciden"))
            val usuario = repo.datos().usuario
            if (sincronizador.cuentaRemota(usuario.id)) {
                sincronizador.cambiarClaveRemota(usuario.id, usuario.correo, actual, nueva)
                "Contraseña cambiada. Usa la nueva al volver a iniciar sesión."
            } else {
                repo.cambiarClaveLocal(actual, nueva)
                "Contraseña cambiada en este dispositivo."
            }
        }

    fun recargar() {
        viewModelScope.launch {
            try {
                val datos = withContext(Dispatchers.IO) { repo.datos() }
                _estado.update { it.copy(datos = datos) }
            } catch (_: Exception) {}
        }
    }

    fun salir() {
        if (_estado.value.ocupado) return
        app.sincronizador.desconectar()
        repo.salir()
        preferencias.edit().remove("usuarioId").apply()
        _estado.value = AppUiState(cargando = false)
    }

    fun ejecutar(
        accion: Accion,
        contexto: String,
        campos: Map<String, String>,
        error: (Throwable) -> Unit,
        exito: (String) -> Unit,
    ) {
        if (_estado.value.ocupado) return
        _estado.update { it.copy(ocupado = true) }
        viewModelScope.launch {
            try {
                val resultado =
                    withContext(Dispatchers.IO) {
                        var resultado = ""
                        when (accion) {
                            Accion.LOGIN -> {
                                Validacion.requeridos(campos, "correo", "clave")
                                val usuario = try {
                                    repo.ingresar(campos)
                                } catch (e: cl.induztek.protocol.domain.ErrorFormulario) {
                                    if (e.campos.keys != setOf("clave")) throw e
                                    val servicio = app.sincronizador
                                    try {
                                        servicio.ingresarDesdeServidor(
                                            servicio.urlIdentidad(),
                                            campos.getValue("correo"),
                                            campos.getValue("clave"),
                                        )
                                    } catch (remoto: cl.induztek.protocol.domain.ErrorFormulario) {
                                        if ("identidad" !in remoto.campos) throw remoto
                                        throw cl.induztek.protocol.domain.ErrorFormulario(
                                            mapOf("clave" to "Revisa el correo y la contraseña. El primer acceso requiere conexión al servidor.")
                                        )
                                    }
                                    repo.ingresar(campos)
                                }
                                preferencias.edit().putString("usuarioId", usuario.id).apply()
                            }
                            Accion.CLIENTE -> repo.guardarCliente(campos)
                            Accion.CUENTA -> repo.habilitarCliente(campos)
                            Accion.INSTALACION -> repo.guardarInstalacion(campos)
                            Accion.EQUIPO -> repo.guardarEquipo(campos)
                            Accion.VISITA -> repo.guardarVisita(campos)
                            Accion.PROTOCOLO -> resultado = repo.guardarProtocolo(campos)
                            Accion.CORREGIR -> repo.corregirProtocolo(contexto, campos)
                            Accion.PRUEBA -> repo.guardarPrueba(contexto, campos)
                            Accion.MANUAL ->
                                repo.revisarPrueba(campos.getValue("protocoloId"), contexto, campos)
                            Accion.REGISTRAR ->
                                repo.cambiarEstado(contexto, Estado.REGISTRADO_TERRENO, campos)
                            Accion.FORMALIZAR ->
                                repo.cambiarEstado(contexto, Estado.FORMALIZADO, campos)
                            Accion.OBSERVAR ->
                                repo.cambiarEstado(contexto, Estado.OBSERVADO, campos)
                            Accion.ANULAR -> repo.cambiarEstado(contexto, Estado.ANULADO, campos)
                            Accion.CERRAR -> repo.cerrarVisita(contexto, campos)
                            Accion.INSTRUMENTO -> repo.guardarInstrumento(campos)
                            Accion.PERSONAL -> repo.guardarPersonal(campos)
                        }
                        resultado to repo.datos()
                    }
                _estado.update { it.copy(ocupado = false, datos = resultado.second) }
                app.sincronizador.programar()
                exito(resultado.first)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update { it.copy(ocupado = false) }
                error(e)
            }
        }
    }

    fun foto(equipo: String, bytes: ByteArray, terminar: (String) -> Unit) {
        if (_estado.value.ocupado) return
        _estado.update { it.copy(ocupado = true) }
        viewModelScope.launch {
            try {
                val data =
                    withContext(Dispatchers.IO) {
                        repo.fotoEquipo(equipo, bytes)
                        repo.datos()
                    }
                _estado.update { it.copy(datos = data, ocupado = false) }
                app.sincronizador.programar()
                terminar("Fotografía guardada")
            } catch (e: Exception) {
                _estado.update { it.copy(ocupado = false) }
                terminar(e.message ?: "No se pudo guardar")
            }
        }
    }

    fun documento(id: String, acta: Boolean, terminar: (String?, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val texto =
                    withContext(Dispatchers.IO) {
                        if (acta) repo.documentoActa(id) else repo.documentoProtocolo(id)
                    }
                terminar(texto, null)
            } catch (e: Exception) {
                terminar(null, e.message ?: "Documento no disponible")
            }
        }
    }
}
