package cl.induztek.protocol.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import cl.induztek.protocol.domain.ErrorFormulario
import cl.induztek.protocol.ui.components.BotonPrincipal
import cl.induztek.protocol.ui.components.CampoTexto
import cl.induztek.protocol.ui.components.Pantalla
import cl.induztek.protocol.ui.components.TarjetaSeccion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun CambiarClaveScreen(
    volver: () -> Unit,
    cambiar: suspend (String, String, String) -> String,
) {
    var actual by remember { mutableStateOf("") }
    var nueva by remember { mutableStateOf("") }
    var confirmar by remember { mutableStateOf("") }
    var errores by remember { mutableStateOf(emptyMap<String, String>()) }
    var mensaje by remember { mutableStateOf("") }
    var guardando by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Pantalla("Cambiar contraseña", if (guardando) null else volver) {
        TarjetaSeccion("Mi cuenta") {
            CampoTexto(actual, { if (!guardando) actual = it }, "Contraseña actual", error = errores["actual"], contrasena = true)
            CampoTexto(nueva, { if (!guardando) nueva = it }, "Nueva contraseña", error = errores["nueva"], contrasena = true)
            CampoTexto(confirmar, { if (!guardando) confirmar = it }, "Confirmar nueva contraseña", error = errores["confirmar"], contrasena = true)
            errores["general"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (mensaje.isNotEmpty()) Text(mensaje)
            BotonPrincipal(if (guardando) "Guardando…" else "Guardar contraseña", {
                if (!guardando) {
                    guardando = true
                    mensaje = ""
                    errores = emptyMap()
                    scope.launch {
                        try {
                            mensaje = cambiar(actual, nueva, confirmar)
                            actual = ""
                            nueva = ""
                            confirmar = ""
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: ErrorFormulario) {
                            errores = e.campos
                        } catch (e: Exception) {
                            errores = mapOf("general" to (e.message ?: "No se pudo cambiar la contraseña"))
                        } finally {
                            guardando = false
                        }
                    }
                }
            }, !guardando)
        }
    }
}