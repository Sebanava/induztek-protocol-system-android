package cl.induztek.protocol.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.KeyboardType
import cl.induztek.protocol.domain.ErrorFormulario
import cl.induztek.protocol.sync.Sincronizador
import cl.induztek.protocol.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RecuperarClaveScreen(servicio: Sincronizador, volver: () -> Unit) {
    var direccion by remember { mutableStateOf(servicio.urlIdentidad()) }
    var correo by remember { mutableStateOf("") }
    var codigo by remember { mutableStateOf("") }
    var nueva by remember { mutableStateOf("") }
    var confirmar by remember { mutableStateOf("") }
    var errores by remember { mutableStateOf(emptyMap<String, String>()) }
    var pasoCodigo by remember { mutableStateOf(false) }
    var completado by remember { mutableStateOf(false) }
    var ocupado by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Pantalla("Recuperar contraseña", if (ocupado) null else volver) {
        TarjetaSeccion("Acceso a mi cuenta") {
            if (completado) {
                Text("Contraseña actualizada. Vuelve al ingreso, activa «Validar cuenta con servidor» e ingresa con tu nueva contraseña.")
                BotonPrincipal("Volver al acceso", volver)
            } else {
                CampoTexto(direccion, { if (!ocupado && !pasoCodigo) direccion = it }, "Dirección del API Gateway", error = errores["identidad"], teclado = KeyboardType.Uri)
                CampoTexto(correo, { if (!ocupado && !pasoCodigo) correo = it }, "Correo registrado", error = errores["correo"], teclado = KeyboardType.Email)
                if (pasoCodigo) {
                    Text("Si la cuenta está habilitada, recibirás un código que caduca en 15 minutos.")
                    CampoTexto(codigo, { if (!ocupado) codigo = it }, "Código recibido", error = errores["codigo"])
                    CampoTexto(nueva, { if (!ocupado) nueva = it }, "Nueva contraseña", error = errores["nueva"], contrasena = true)
                    CampoTexto(confirmar, { if (!ocupado) confirmar = it }, "Confirmar nueva contraseña", error = errores["confirmar"], contrasena = true)
                    BotonSecundario("Solicitar otro código", {
                        pasoCodigo = false
                        codigo = ""
                        nueva = ""
                        confirmar = ""
                        errores = emptyMap()
                    }, !ocupado)
                }
                errores["general"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                BotonPrincipal(if (pasoCodigo) "Restablecer contraseña" else "Enviar código", {
                    ocupado = true
                    errores = emptyMap()
                    scope.launch {
                        try {
                            if (pasoCodigo && nueva != confirmar)
                                throw ErrorFormulario(mapOf("confirmar" to "Las contraseñas no coinciden"))
                            withContext(Dispatchers.IO) {
                                if (pasoCodigo) servicio.recuperarClave(direccion, correo, codigo, nueva)
                                else servicio.solicitarRecuperacion(direccion, correo)
                            }
                            if (pasoCodigo) {
                                codigo = ""
                                nueva = ""
                                confirmar = ""
                                completado = true
                            } else pasoCodigo = true
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: ErrorFormulario) {
                            errores = e.campos
                        } catch (_: java.io.IOException) {
                            errores = mapOf("general" to "No se pudo conectar al servidor")
                        } catch (e: Exception) {
                            errores = mapOf("general" to (e.message ?: "No se pudo recuperar el acceso"))
                        } finally {
                            ocupado = false
                        }
                    }
                }, !ocupado)
            }
        }
    }
}