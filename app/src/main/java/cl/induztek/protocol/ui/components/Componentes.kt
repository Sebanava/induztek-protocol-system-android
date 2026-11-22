package cl.induztek.protocol.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import cl.induztek.protocol.model.Resultado

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Pantalla(
    titulo: String,
    volver: (() -> Unit)? = null,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titulo) },
                navigationIcon = {
                    if (volver != null)
                        IconButton(onClick = volver) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                        }
                },
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier.widthIn(max = 840.dp)
                    .fillMaxWidth()
                    .align(androidx.compose.ui.Alignment.TopCenter)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = contenido,
            )
        }
    }
}

@Composable
fun TarjetaSeccion(titulo: String, contenido: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                titulo,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            contenido()
        }
    }
}

@Composable
fun BotonPrincipal(
    texto: String,
    onClick: () -> Unit,
    habilitado: Boolean = true,
    icono: ImageVector = Icons.Default.Check,
) {
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = habilitado) {
        Icon(icono, null)
        Spacer(Modifier.width(8.dp))
        Text(texto)
    }
}

@Composable
fun BotonSecundario(texto: String, onClick: () -> Unit, habilitado: Boolean = true) {
    OutlinedButton(onClick, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = habilitado) {
        Text(texto)
    }
}

@Composable
fun CampoTexto(
    valor: String,
    cambio: (String) -> Unit,
    etiqueta: String,
    error: String? = null,
    teclado: KeyboardType = KeyboardType.Text,
    icono: ImageVector = Icons.Default.Edit,
    contrasena: Boolean = false,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = valor,
        onValueChange = cambio,
        label = { Text(etiqueta) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = error != null,
        leadingIcon = { Icon(if (contrasena) Icons.Default.Lock else icono, null) },
        trailingIcon = {
            if (contrasena)
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        if (visible) "Ocultar contraseña" else "Mostrar contraseña",
                    )
                }
        },
        visualTransformation =
            if (contrasena && !visible) PasswordVisualTransformation()
            else VisualTransformation.None,
        keyboardOptions =
            KeyboardOptions(keyboardType = if (contrasena) KeyboardType.Password else teclado),
        supportingText = { if (error != null) Text(error) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Selector(
    valor: String,
    opciones: List<Pair<String, String>>,
    cambio: (String) -> Unit,
    etiqueta: String,
    error: String? = null,
) {
    var abierto by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = abierto, onExpandedChange = { abierto = it }) {
        OutlinedTextField(
            value = opciones.firstOrNull { it.first == valor }?.second.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(etiqueta) },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(abierto) },
            isError = error != null,
            supportingText = { if (error != null) Text(error) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            opciones.forEach { (id, nombre) ->
                DropdownMenuItem(
                    text = { Text(nombre) },
                    onClick = {
                        cambio(id)
                        abierto = false
                    },
                )
            }
        }
    }
}

@Composable
fun EtiquetaEstado(texto: String, resultado: Resultado? = null) {
    val color =
        when (resultado) {
            Resultado.RECHAZADO -> MaterialTheme.colorScheme.error
            Resultado.OBSERVADO -> MaterialTheme.colorScheme.secondary
            Resultado.APROBADO -> MaterialTheme.colorScheme.tertiary
            null -> MaterialTheme.colorScheme.primary
        }
    Surface(
        color = color.copy(alpha = .12f),
        contentColor = color,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            texto,
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun TarjetaEstadistica(numero: Int, texto: String) {
    TarjetaSeccion(texto) {
        Text(numero.toString(), style = MaterialTheme.typography.headlineLarge)
    }
}
