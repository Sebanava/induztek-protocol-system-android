package cl.induztek.protocol.sync

import com.google.gson.JsonObject
import retrofit2.http.*

data class SolicitudRecuperacion(val correo: String)
data class ConfirmacionRecuperacion(val correo: String, val codigo: String, val nueva: String)
data class CambioClaveRemoto(val actual: String, val nueva: String)

data class CredencialesRemotas(val correo: String, val clave: String)

data class UsuarioRemoto(
    val id: String,
    val rolId: String,
    val nombre: String,
    val rut: String,
    val correo: String,
    val activo: Boolean = true,
    val contacto: String = "",
    val cargo: String = "",
    val giro: String = "",
    val telefono: String = "",
    val version: Long = 0,
)

data class SesionRemota(val token: String, val usuario: UsuarioRemoto)

data class RegistroLocal(val tipo: String, val id: String, val datos: JsonObject)

data class RegistroRemoto(
    val tipo: String,
    val id: String,
    val version: Long,
    val datos: JsonObject,
)

data class CambioRemoto(
    val tipo: String,
    val id: String,
    val version: Long,
    val operacionId: String,
    val datos: JsonObject,
)

interface IdentidadApi {
    @POST("api/sesiones")
    suspend fun ingresar(@Body credenciales: CredencialesRemotas): SesionRemota

    @POST("api/recuperacion/solicitar")
    suspend fun solicitarRecuperacion(@Body solicitud: SolicitudRecuperacion): retrofit2.Response<Void>

    @POST("api/recuperacion/confirmar")
    suspend fun recuperarClave(@Body solicitud: ConfirmacionRecuperacion): retrofit2.Response<Void>

    @POST("api/mi-contrasena")
    suspend fun cambiarClave(
        @Header("Authorization") token: String,
        @Body cambio: CambioClaveRemoto,
    ): SesionRemota

    @GET("api/usuarios")
    suspend fun usuarios(@Header("Authorization") token: String): List<UsuarioRemoto>

    @POST("api/personal")
    suspend fun personal(
        @Header("Authorization") token: String,
        @Body usuarios: List<JsonObject>,
    ): List<UsuarioRemoto>

    @POST("api/clientes")
    suspend fun clientes(
        @Header("Authorization") token: String,
        @Body usuarios: List<JsonObject>,
    ): List<UsuarioRemoto>
}

interface ProtocolosApi {
    @GET("api/registros")
    suspend fun descargar(@Header("Authorization") token: String): List<RegistroRemoto>

    @POST("api/sincronizacion")
    suspend fun enviar(
        @Header("Authorization") token: String,
        @Body cambios: List<CambioRemoto>,
    ): List<RegistroRemoto>
}

interface BitacoraApi {
    @POST("api/bitacora")
    suspend fun registrar(
        @retrofit2.http.Header("Authorization") token: String,
        @retrofit2.http.Body evento: EventoSincronizacion,
    ): retrofit2.Response<okhttp3.ResponseBody>
}

data class EventoSincronizacion(val id: String)