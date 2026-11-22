package cl.induztek.protocol.navigation

import android.content.ActivityNotFoundException
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import cl.induztek.protocol.model.Perfil
import cl.induztek.protocol.native.CompartirDocumento
import cl.induztek.protocol.ui.components.*
import cl.induztek.protocol.ui.screens.*
import cl.induztek.protocol.viewmodel.*
import kotlinx.coroutines.*

@Composable
fun AppNavegacion(app: AppViewModel = viewModel()) {
    val estado by app.estado.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    var aviso by remember { mutableStateOf("") }
    var recuperando by remember { mutableStateOf(false) }
    val data = estado.datos
    when {
        estado.cargando ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        estado.errorInicio != null ->
            Pantalla("No se pudo iniciar") {
                Text(estado.errorInicio.orEmpty())
                BotonPrincipal("Reintentar", app::iniciar)
            }
        data == null && recuperando ->
            RecuperarClaveScreen(app.sincronizador, { recuperando = false })
        data == null ->
            FormularioScreen(
                Accion.LOGIN, "login", "", null, app, estado.ocupado, null,
                recuperar = { recuperando = true }, exito = {},
            )
        else ->
            key(data.usuario.id) {
                val nav = rememberNavController()
                val form: (Accion, String, String) -> Unit = { accion, id, item ->
                    nav.navigate(Ruta.Formulario.crear(accion, id, item))
                }
                val volver: () -> Unit = {
                    nav.navigateUp()
                    Unit
                }
                val verProtocolo: (String) -> Unit = { nav.navigate("protocolo/$it") }
                val compartir: (String, Boolean) -> Unit = { id, acta ->
                    app.documento(id, acta) { texto, error ->
                        if (texto == null) aviso = error.orEmpty()
                        else
                            scope.launch {
                                try {
                                    val uri =
                                        withContext(Dispatchers.IO) {
                                            CompartirDocumento.crearPdf(contexto, texto)
                                        }
                                    CompartirDocumento.abrir(contexto, uri)
                                } catch (_: ActivityNotFoundException) {
                                    aviso = "No hay aplicaciones disponibles para compartir PDF"
                                } catch (_: Exception) {
                                    aviso = "No se pudo preparar o compartir el documento"
                                }
                            }
                    }
                }
                NavHost(
                    nav,
                    startDestination = Ruta.Panel.ruta,
                    enterTransition = { fadeIn() },
                    exitTransition = { fadeOut() },
                ) {
                    composable("cambiar-clave") {
                        CambiarClaveScreen(volver, app::cambiarClave)
                    }
                    composable("sincronizacion") {
                        SincronizacionScreen(
                            app.sincronizador,
                            data.usuario.id,
                            data.usuario.correo,
                            volver,
                            app::recargar,
                        )
                    }
                    composable(Ruta.Panel.ruta) {
                        PanelScreen(data, { nav.navigate(it) }, form, app::salir)
                    }
                    composable(Ruta.Equipos.ruta) {
                        EquiposScreen(data, volver, { nav.navigate("equipo/$it") }, form)
                    }
                    composable(
                        Ruta.Equipo.ruta,
                        listOf(navArgument("id") { type = NavType.StringType }),
                    ) { e ->
                        EquipoScreen(
                            e.arguments?.getString("id").orEmpty(),
                            data,
                            app,
                            volver,
                            verProtocolo,
                        )
                    }
                    composable(Ruta.Protocolos.ruta) {
                        ProtocolosScreen(data, volver, verProtocolo)
                    }
                    composable(
                        Ruta.Protocolo.ruta,
                        listOf(navArgument("id") { type = NavType.StringType }),
                    ) { e ->
                        ProtocoloScreen(
                            e.arguments?.getString("id").orEmpty(),
                            data,
                            volver,
                            form,
                            compartir,
                        )
                    }
                    composable(Ruta.Visitas.ruta) {
                        VisitasScreen(data, volver, { nav.navigate("visita/$it") }, form)
                    }
                    composable(
                        Ruta.Visita.ruta,
                        listOf(navArgument("id") { type = NavType.StringType }),
                    ) { e ->
                        VisitaScreen(
                            e.arguments?.getString("id").orEmpty(),
                            data,
                            volver,
                            verProtocolo,
                            form,
                            compartir,
                        )
                    }
                    composable(Ruta.Clientes.ruta) {
                        if (data.usuario.perfil.registra) ClientesScreen(data, volver, form)
                        else
                            Pantalla("Acceso restringido", volver) {
                                Text("Tu cuenta es de solo consulta")
                            }
                    }
                    composable(Ruta.Instrumentos.ruta) {
                        if (data.usuario.perfil.registra) InstrumentosScreen(data, volver, form)
                        else
                            Pantalla("Acceso restringido", volver) {
                                Text("Tu cuenta es de solo consulta")
                            }
                    }
                    composable(
                        Ruta.Formulario.ruta,
                        arguments =
                            listOf(
                                navArgument("accion") { type = NavType.StringType },
                                navArgument("contexto") { type = NavType.StringType },
                                navArgument("item") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                },
                            ),
                    ) { entrada ->
                        val accion =
                            runCatching {
                                    Accion.valueOf(entrada.arguments?.getString("accion").orEmpty())
                                }
                                .getOrNull()
                        val id = entrada.arguments?.getString("contexto").orEmpty()
                        val item = entrada.arguments?.getString("item").orEmpty()
                        val autorizado =
                            accion != null &&
                                data.usuario.perfil.registra &&
                                when (accion) {
                                    Accion.MANUAL,
                                    Accion.FORMALIZAR,
                                    Accion.OBSERVAR,
                                    Accion.ANULAR -> data.usuario.perfil.revisa
                                    Accion.CERRAR,
                                    Accion.VISITA -> data.usuario.perfil.cierraVisita
                                    Accion.PERSONAL,
                                    Accion.CUENTA,
                                    Accion.INSTRUMENTO -> data.usuario.perfil == Perfil.JEFE_TECNICO
                                    Accion.LOGIN -> false
                                    else -> true
                                }
                        if (!autorizado)
                            Pantalla("Acceso restringido", volver) {
                                Text("Esta acción no está disponible para tu perfil")
                            }
                        else
                            FormularioScreen(
                                accion!!,
                                id,
                                item,
                                data,
                                app,
                                estado.ocupado,
                                volver,
                            ) { nuevo ->
                                nav.popBackStack()
                                if (accion == Accion.PROTOCOLO && nuevo.isNotBlank())
                                    nav.navigate("protocolo/$nuevo")
                                aviso = "Cambios guardados en el dispositivo"
                            }
                    }
                }
            }
    }
    if (aviso.isNotBlank())
        AlertDialog(
            onDismissRequest = { aviso = "" },
            title = { Text("Induztek") },
            text = { Text(aviso) },
            confirmButton = { TextButton(onClick = { aviso = "" }) { Text("Aceptar") } },
        )
}
