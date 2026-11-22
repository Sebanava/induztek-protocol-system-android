package cl.induztek.protocol.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.induztek.protocol.sync.Sincronizador
import cl.induztek.protocol.ui.components.*
import cl.induztek.protocol.viewmodel.FormularioViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SincronizacionScreen(
    servicio: Sincronizador,
    usuarioId: String,
    correo: String,
    volver: () -> Unit,
    actualizar: () -> Unit,
) {
    val vm: FormularioViewModel = viewModel(key = "conexion-$usuarioId")
    val formulario by vm.estado.collectAsStateWithLifecycle()
    val estado by servicio.estado.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var ocupado by remember { mutableStateOf(false) }
    LaunchedEffect(usuarioId) {
        vm.iniciar(
            mapOf(
                "identidad" to servicio.urlIdentidad(),
                "correo" to correo,
            )
        )
    }
    Pantalla("Conexión y sincronización", volver) {
        TarjetaSeccion("Tus registros están disponibles sin Internet") {
            Text(estado)
            Text(
                "La conexión es opcional para registrar y consultar aquí. Sirve para compartir datos con otros dispositivos autorizados."
            )
            Text(
                "En el emulador puedes usar el Gateway local. En otros dispositivos, usa la dirección HTTPS del Gateway."
            )
        }
        TarjetaSeccion("Conectar mi cuenta al servidor") {
            listOf(
                    "identidad" to "Dirección del API Gateway",
                    "correo" to "Correo de mi cuenta",
                )
                .forEach { (k, t) ->
                    CampoTexto(
                        formulario.valores[k].orEmpty(),
                        { vm.cambiar(k, it) },
                        t,
                        error = formulario.errores[k],
                        teclado = if (k == "correo") KeyboardType.Email else KeyboardType.Uri,
                        icono = if (k == "correo") Icons.Default.Email else Icons.Default.Cloud,
                    )
                }
            CampoTexto(
                formulario.valores["clave"].orEmpty(),
                { vm.cambiar("clave", it) },
                "Contraseña",
                error = formulario.errores["clave"],
                contrasena = true,
            )
            formulario.errores["general"]?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            BotonPrincipal(
                if (ocupado) "Conectando…" else "Conectar y sincronizar",
                {
                    if (!ocupado)
                        scope.launch {
                            ocupado = true
                            try {
                                withContext(Dispatchers.IO) {
                                    servicio.conectar(
                                        usuarioId,
                                        formulario.valores["identidad"].orEmpty(),
                                        formulario.valores["identidad"].orEmpty(),
                                        formulario.valores["correo"].orEmpty(),
                                        formulario.valores["clave"].orEmpty(),
                                    )
                                }
                                actualizar()
                            } catch (e: Exception) {
                                vm.error(e)
                            } finally {
                                vm.limpiarClave()
                                ocupado = false
                            }
                        }
                },
                !ocupado,
            )
            if (servicio.configurado) {
                BotonSecundario(
                    "Sincronizar ahora",
                    {
                        if (!ocupado)
                            scope.launch {
                                ocupado = true
                                try {
                                    withContext(Dispatchers.IO) { servicio.sincronizar() }
                                    actualizar()
                                } catch (e: Exception) {
                                    vm.error(e)
                                } finally {
                                    ocupado = false
                                }
                            }
                    },
                    !ocupado,
                )
                BotonSecundario(
                    "Desconectar sin borrar mis datos",
                    { servicio.desconectar() },
                    !ocupado,
                )
            }
        }
    }
}
