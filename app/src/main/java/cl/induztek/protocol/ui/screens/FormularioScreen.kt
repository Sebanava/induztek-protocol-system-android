package cl.induztek.protocol.ui.screens

import androidx.compose.ui.unit.dp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.induztek.protocol.data.*
import cl.induztek.protocol.model.*
import cl.induztek.protocol.ui.components.*
import cl.induztek.protocol.viewmodel.*

@Composable
fun FormularioScreen(
    accion: Accion,
    contexto: String,
    item: String,
    data: DatosApp?,
    app: AppViewModel,
    ocupado: Boolean,
    volver: (() -> Unit)?,
    recuperar: (() -> Unit)? = null,
    exito: (String) -> Unit,
) {
    val vm: FormularioViewModel = viewModel(key = "${accion.name}:$contexto:$item")
    val estado by vm.estado.collectAsStateWithLifecycle()
    val d = estado.valores
    val protocolo = data?.protocolos?.firstOrNull { it.id == contexto }
    val equipo = protocolo?.let { p -> data.equipos.first { it.id == p.equipoId } }
    val prueba =
        data?.pruebas?.firstOrNull {
            it.id == item || (accion == Accion.MANUAL && it.id == contexto)
        }
    LaunchedEffect(accion, contexto, item) {
        val iniciales = mutableMapOf<String, String>()
        iniciales["fecha"] = cl.induztek.protocol.domain.Validacion.hoy().toString()
        iniciales["fechaHora"] = cl.induztek.protocol.domain.Validacion.ahora().withSecond(0).withNano(0).toString()
        iniciales["activo"] = "SI"
        iniciales["rolId"] = Perfil.TECNICO.name
        iniciales["tipo"] = TipoVisita.SOLO_PRUEBAS.name
        iniciales["momento"] = Momento.UNICA.name
        iniciales["realizada"] = "SI"
        iniciales["opera"] = "SI"
        iniciales["unidad"] = "MΩ"
        if (accion == Accion.LOGIN) {
            iniciales["remoto"] = "NO"
            iniciales["identidad"] = app.sincronizador.urlIdentidad()
        }
        if (accion == Accion.PROTOCOLO && data != null) {
            val visita = data.visitas.firstOrNull { it.id == contexto }
            visita?.let {
                iniciales["visitaId"] = it.id
                iniciales["fechaHora"] = "${it.fecha}T10:00"
            }
        }
        if (accion == Accion.PRUEBA && item.contains('|')) {
            iniciales["tipoId"] = item.substringBefore('|')
            iniciales["elemento"] = item.substringAfter('|')
        }
        if (accion == Accion.PRUEBA && prueba != null) {
            iniciales.putAll(
                mapOf(
                    "tipoId" to prueba.tipoId,
                    "elemento" to prueba.elemento,
                    "momento" to prueba.momento.name,
                    "realizada" to if (prueba.realizada) "SI" else "NO",
                    "motivo" to prueba.motivo,
                    "instrumentoId" to prueba.instrumentoId.orEmpty(),
                    "comentario" to prueba.comentario,
                    "opera" to if (prueba.opera) "SI" else "NO",
                )
            )
            iniciales.putAll(data.valores(prueba.id).mapValues { it.value.toString() })
        }
        if (accion == Accion.CORREGIR && protocolo != null)
            iniciales.putAll(
                mapOf(
                    "temperatura" to protocolo.temperatura?.toString().orEmpty(),
                    "humedad" to protocolo.humedad?.toString().orEmpty(),
                    "mantenimiento" to protocolo.mantenimiento,
                )
            )
        if (accion == Accion.MANUAL && prueba != null) iniciales["protocoloId"] = prueba.protocoloId
        vm.iniciar(iniciales)
    }
    fun cambiar(clave: String, valor: String) {
        vm.cambiar(clave, valor)
        if (clave == "personalId") {
            val u = data?.usuarios?.firstOrNull { it.id == valor && it.perfil != Perfil.CLIENTE }
            listOf("nombre", "rut", "correo", "rolId").forEach { k ->
                vm.cambiar(
                    k,
                    when (k) {
                        "nombre" -> u?.nombre
                        "rut" -> u?.rut
                        "correo" -> u?.correo
                        else -> u?.rolId ?: Perfil.TECNICO.name
                    }.orEmpty(),
                )
            }
            vm.cambiar("activo", if (u?.activo != false) "SI" else "NO")
            vm.cambiar("clave", "")
        }
        when (clave) {
            "tipoId" ->
                if (accion == Accion.PRUEBA) {
                    vm.cambiar("elemento", "")
                    vm.cambiar("instrumentoId", "")
                    Catalogo.unidades.keys.forEach { vm.cambiar(it, "") }
                }
            "visitaId" -> {
                vm.cambiar("equipoId", "")
                vm.cambiar("reemplazaId", "")
            }
            "equipoId" -> vm.cambiar("reemplazaId", "")
        }
    }
    val titulo =
        when (accion) {
            Accion.PERSONAL -> "Gestionar personal"
            Accion.LOGIN -> "Acceso"
            Accion.CLIENTE -> "Registrar empresa cliente"
            Accion.CUENTA -> "Habilitar cuenta cliente"
            Accion.INSTALACION -> "Registrar instalación"
            Accion.EQUIPO -> "Registrar equipo"
            Accion.VISITA -> "Registrar visita"
            Accion.PROTOCOLO -> "Iniciar protocolo"
            Accion.CORREGIR -> "Corregir ambiente y mantenimiento"
            Accion.PRUEBA -> "Registrar o corregir prueba"
            Accion.MANUAL -> "Criterio del ingeniero"
            Accion.REGISTRAR -> "Terminar registro de terreno"
            Accion.FORMALIZAR -> "Formalizar protocolo"
            Accion.OBSERVAR -> "Devolver con observaciones"
            Accion.ANULAR -> "Anular protocolo"
            Accion.CERRAR -> "Cerrar visita y emitir acta"
            Accion.INSTRUMENTO -> "Instrumento y calibración"
        }
    Pantalla(titulo, volver) {
        if (accion == Accion.LOGIN)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Induztek\nProtocol System", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Gestión de protocolos eléctricos",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        TarjetaSeccion(if (accion == Accion.LOGIN) "Iniciar sesión" else "Datos del registro") {
            @Composable
            fun campo(
                clave: String,
                titulo: String,
                numerico: Boolean = false,
                secreto: Boolean = false,
                icono: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Edit,
            ) {
                CampoTexto(
                    d[clave].orEmpty(),
                    { cambiar(clave, it) },
                    titulo,
                    estado.errores[clave],
                    if (numerico) KeyboardType.Decimal
                    else when (clave) {
                        "correo" -> KeyboardType.Email
                        "telefono" -> KeyboardType.Phone
                        else -> KeyboardType.Text
                    },
                    icono,
                    secreto,
                )
            }
            @Composable
            fun selector(clave: String, titulo: String, opciones: List<Pair<String, String>>) {
                Selector(
                    d[clave].orEmpty(),
                    opciones,
                    { cambiar(clave, it) },
                    titulo,
                    estado.errores[clave],
                )
                if (opciones.isEmpty())
                    Text(
                        "No hay opciones disponibles. Registra primero los datos relacionados.",
                        style = MaterialTheme.typography.bodySmall,
                    )
            }
            @Composable
            fun ambiente() {
                campo(
                    "temperatura",
                    "Temperatura ambiente (°C)",
                    true,
                    icono = Icons.Default.Thermostat,
                )
                campo("humedad", "Humedad relativa (%)", true)
                campo("mantenimiento", "Descripción del mantenimiento por equipo (si aplica)")
            }
            when (accion) {
                Accion.PERSONAL -> {
                    selector(
                        "personalId",
                        "Persona (o agregar nueva)",
                        listOf("" to "Agregar personal") +
                            data!!
                                .usuarios
                                .filter { it.perfil != Perfil.CLIENTE }
                                .map {
                                    it.id to
                                        "${it.nombre} · ${if(it.activo)"Activo" else "Desactivado"}"
                                },
                    )
                    campo("nombre", "Nombre completo")
                    campo("rut", "RUT")
                    campo("correo", "Correo")
                    selector(
                        "rolId",
                        "Perfil",
                        Perfil.entries.filter { it != Perfil.CLIENTE }.map { it.name to it.titulo },
                    )
                    selector(
                        "activo",
                        "Estado de acceso",
                        listOf("SI" to "Activo", "NO" to "Desactivado"),
                    )
                    campo(
                        "clave",
                        "Contraseña (en edición, dejar vacía para conservar)",
                        secreto = true,
                    )
                    Text(
                        "La desactivación conserva las pruebas y el nombre del usuario en el historial."
                    )
                }
                Accion.LOGIN -> {
                    campo("correo", "Correo de usuario", icono = Icons.Default.Email)
                    campo("clave", "Contraseña", secreto = true)
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(
                            checked = d["remoto"] == "SI",
                            onCheckedChange = {
                                cambiar("remoto", if (it) "SI" else "NO")
                            },
                            enabled = !ocupado,
                        )
                        Text("Validar cuenta con servidor")
                    }
                    AnimatedVisibility(d["remoto"] == "SI") {
                        Column {
                            campo(
                                "identidad",
                                "Dirección del API Gateway",
                                icono = Icons.Default.Cloud,
                            )
                            Text(
                                "Úsalo para el primer acceso en este dispositivo o después de un cambio de contraseña. Requiere conexión con tu servidor. Después puedes ingresar sin conexión con la cuenta validada.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    if (d["remoto"] != "SI")
                        Text(
                            "Acceso local: no necesita Internet. Para una cuenta creada en otro dispositivo, valida primero con el servidor.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                }
                Accion.CLIENTE -> {
                    campo("nombre", "Razón social")
                    campo("rut", "RUT de empresa")
                    campo("correo", "Correo de la cuenta")
                    campo("contacto", "Nombre del contacto")
                    campo("cargo", "Cargo del contacto")
                    campo("giro", "Giro (opcional)")
                    campo("telefono", "Teléfono chileno · 9 dígitos (opcional)")
                    Text("El acceso queda inactivo hasta que el jefe técnico habilite la cuenta.")
                }
                Accion.CUENTA -> {
                    selector(
                        "clienteId",
                        "Empresa cliente",
                        data!!
                            .usuarios
                            .filter { it.perfil == Perfil.CLIENTE }
                            .map {
                                it.id to "${it.nombre} · ${if(it.activo)"Activa" else "Inactiva"}"
                            },
                    )
                    campo("clave", "Nueva contraseña", secreto = true)
                    Text(
                        "Esta acción establece o reemplaza la contraseña de la cuenta seleccionada."
                    )
                }
                Accion.INSTALACION -> {
                    selector(
                        "clienteId",
                        "Empresa cliente",
                        data!!
                            .usuarios
                            .filter { it.perfil == Perfil.CLIENTE }
                            .map { it.id to it.nombre },
                    )
                    campo("nombre", "Nombre de la instalación")
                    campo("ubicacion", "Ubicación")
                    selector(
                        "comuna",
                        "Comuna / región",
                        data.comunas.map { comuna ->
                            comuna.id to
                                "${comuna.id} · ${data.regiones.first { it.id == comuna.regionId }.nombre}"
                        },
                    )
                    campo("acceso", "Observaciones de acceso (opcional)")
                }
                Accion.EQUIPO -> {
                    selector(
                        "instalacionId",
                        "Instalación / cliente",
                        data!!.instalaciones.map { i ->
                            i.id to
                                "${i.nombre} · ${data.usuarios.first{it.id==i.clienteId}.nombre}"
                        },
                    )
                    selector("tipoId", "Tipo de equipo", Catalogo.equipos.toList())
                    campo("tag", "Identificador TAG", icono = Icons.Default.QrCode)
                    campo("marca", "Marca")
                    campo("modelo", "Modelo")
                    campo("serie", "Número de serie")
                    campo("anio", "Año de fabricación (opcional)", true)
                    campo("potencia", "Potencia nominal (kVA, si aplica)", true)
                    campo("tensiones", "Tensiones nominales y unidad (si aplica)")
                    campo("corriente", "Corriente nominal (A, si aplica)", true)
                    if (d["tipoId"] in listOf("INTERRUPTOR", "CELDA"))
                        campo("maxContactos", "Máximo de contactos de placa (µΩ)", true)
                    campo("observaciones", "Observaciones (opcional)")
                }
                Accion.VISITA -> {
                    campo("ot", "Número de orden de trabajo (OT)")
                    selector(
                        "instalacionId",
                        "Instalación",
                        data!!.instalaciones.map { it.id to it.nombre },
                    )
                    selector(
                        "ingenieroId",
                        "Ingeniero a cargo (asista o no)",
                        data.usuarios
                            .filter { it.perfil == Perfil.INGENIERO && it.activo }
                            .map { it.id to it.nombre },
                    )
                    campo("fecha", "Fecha ejecutada (AAAA-MM-DD)")
                    selector(
                        "tipo",
                        "Tipo de visita",
                        TipoVisita.entries.map { it.name to it.titulo },
                    )
                }
                Accion.PROTOCOLO -> {
                    selector(
                        "visitaId",
                        "Visita abierta",
                        data!!
                            .visitas
                            .filter { it.cierre == null }
                            .map { it.id to "${it.ot} · ${it.fecha}" },
                    )
                    val visita = data.visitas.firstOrNull { it.id == d["visitaId"] }
                    selector(
                        "equipoId",
                        "Equipo de la instalación",
                        data.equipos
                            .filter { it.instalacionId == visita?.instalacionId }
                            .map { it.id to "${it.tag} · ${Catalogo.equipos[it.tipoId]}" },
                    )
                    campo("fechaHora", "Fecha y hora (AAAA-MM-DDTHH:MM)")
                    ambiente()
                    selector(
                        "reemplazaId",
                        "Reemplaza protocolo formalizado (opcional)",
                        listOf("" to "No reemplaza") +
                            data.protocolos
                                .filter {
                                    it.equipoId == d["equipoId"] && it.estado == Estado.FORMALIZADO
                                }
                                .map { it.id to it.numero },
                    )
                }
                Accion.CORREGIR -> ambiente()
                Accion.PRUEBA -> {
                    if (equipo != null) {
                        Text("${equipo.tag} · ${protocolo?.fechaHora}")
                        selector(
                            "tipoId",
                            "Tipo de prueba",
                            Catalogo.tipos(equipo.tipoId).map {
                                it to Catalogo.pruebas.getValue(it)
                            },
                        )
                        val tipo = d["tipoId"].orEmpty()
                        selector(
                            "elemento",
                            "Devanado / fase / polo",
                            Catalogo.elementos(equipo.tipoId, tipo).map { it to it },
                        )
                        selector(
                            "momento",
                            "Momento",
                            Momento.entries.map {
                                it.name to
                                    when (it) {
                                        Momento.UNICA -> "Única"
                                        Momento.PREVIA -> "Previa al mantenimiento"
                                        Momento.POSTERIOR -> "Posterior al mantenimiento"
                                    }
                            },
                        )
                        selector(
                            "realizada",
                            "¿Se realizó la prueba?",
                            listOf("SI" to "Realizada", "NO" to "No realizada"),
                        )
                        if (d["realizada"] == "NO")
                            campo("motivo", "Motivo obligatorio de no realización")
                        else {
                            selector(
                                "instrumentoId",
                                "Instrumento y calibración",
                                data!!
                                    .instrumentos
                                    .filter { it.tipoPruebaId == tipo }
                                    .map { ins ->
                                        ins.id to
                                            "${ins.nombre} · ${ins.serie} · vence ${data.calibraciones.filter{it.instrumentoId==ins.id}.maxByOrNull{it.hasta}?.hasta?:"sin calibración"}"
                                    },
                            )
                            if (tipo == "AISLACION")
                                selector(
                                    "unidad",
                                    "Unidad de ingreso de lecturas",
                                    listOf("MΩ" to "MΩ", "GΩ" to "GΩ (se convierte a MΩ)"),
                                )
                            if (tipo == "OPERACION_RELE")
                                selector(
                                    "opera",
                                    "¿El relé operó?",
                                    listOf("SI" to "Sí", "NO" to "No operó (Rechazado)"),
                                )
                            Catalogo.parametros(equipo.tipoId, tipo)
                                .filter {
                                    tipo != "OPERACION_RELE" ||
                                        d["opera"] != "NO" ||
                                        it !in listOf("CORRIENTE_OPERACION", "TIEMPO_MEDIDO")
                                }
                                .forEach { codigo ->
                                    val unidad =
                                        if (codigo in listOf("LECTURA_1MIN", "LECTURA_10MIN"))
                                            d["unidad"] ?: "MΩ"
                                        else Catalogo.unidades[codigo]
                                    campo(codigo, "${Catalogo.etiquetas[codigo]} ($unidad)", true)
                                }
                            if (
                                tipo == "DEVANADOS" ||
                                    tipo == "AISLACION" &&
                                        equipo.tipoId in listOf("CELDA", "INTERRUPTOR")
                            )
                                Text(
                                    "El ingeniero asigna el resultado durante la revisión."
                                )
                        }
                        campo("comentario", "Observaciones de la prueba (opcional)")
                    }
                }
                Accion.MANUAL -> {
                    Text("${prueba?.tipoId} · ${prueba?.elemento}")
                    selector(
                        "resultado",
                        "Resultado técnico manual",
                        Resultado.entries.map { it.name to it.titulo },
                    )
                    campo("comentario", "Justificación del criterio")
                }
                Accion.REGISTRAR ->
                    Text(
                        "Se verificará que cada prueba requerida esté realizada o justificada, con al menos una realizada. Al registrar, se bloquea la edición hasta una devolución del ingeniero."
                    )
                Accion.FORMALIZAR -> {
                    Text(
                        "La formalización es irreversible. El protocolo quedará bloqueado para edición y anulación."
                    )
                    campo(
                        "motivo",
                        "Comentario del ingeniero (obligatorio si hay variación > 25 %)",
                    )
                }
                Accion.OBSERVAR,
                Accion.ANULAR -> campo("motivo", "Motivo obligatorio")
                Accion.CERRAR -> {
                    Text(
                        "Se guardará una única acta preliminar con mediciones, resultados y receptor. Luego podrás compartirla por PDF. Registrar la recepción no confirma un envío digital."
                    )
                    campo("nombre", "Nombre de quien recibe", icono = Icons.Default.Person)
                    campo("rut", "RUT de quien recibe")
                    campo("cargo", "Cargo de quien recibe")
                }
                Accion.INSTRUMENTO -> {
                    campo("nombre", "Nombre / tipo de instrumento")
                    selector("tipoPruebaId", "Prueba compatible", Catalogo.pruebas.toList())
                    campo("marcaModelo", "Marca y modelo")
                    campo("serie", "Número de serie")
                    campo("desde", "Fecha calibración (AAAA-MM-DD)")
                    campo("hasta", "Vence (AAAA-MM-DD)")
                    Text(
                        "Si la serie ya existe se agrega una calibración sin borrar el historial anterior."
                    )
                }
            }
            AnimatedVisibility(estado.errores.isNotEmpty()) {
                Column {
                    estado.errores["general"]?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    Text(
                        "Revisa los campos señalados antes de continuar.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            BotonPrincipal(
                if (ocupado && accion == Accion.LOGIN) "Ingresando…"
                else if (ocupado) "Guardando…"
                else if (accion == Accion.LOGIN) "Ingresar" else "Confirmar",
                {
                    app.ejecutar(accion, contexto, estado.valores, vm::error) { nuevo ->
                        if (accion == Accion.LOGIN) vm.cambiar("clave", "")
                        exito(nuevo)
                    }
                },
                !ocupado,
            )
            if (recuperar != null && accion == Accion.LOGIN)
                BotonSecundario("Olvidé mi contraseña", recuperar, !ocupado)
        }
    }
}
