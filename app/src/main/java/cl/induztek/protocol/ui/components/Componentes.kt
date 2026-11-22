package cl.induztek.protocol.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import cl.induztek.protocol.R
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(titulo, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                actions = {
                    Surface(
                        modifier = Modifier.padding(end = 16.dp).size(36.dp),
                        shape = MaterialTheme.shapes.small,
                        color = androidx.compose.ui.graphics.Color(0xFFE1F0EF),
                    ) {
                        Image(
                            painterResource(R.drawable.logo_induztek),
                            contentDescription = "Induztek",
                            modifier = Modifier.padding(4.dp),
                        )
                    }
                },
                navigationIcon = {
                    if (volver != null)
                        IconButton(onClick = volver) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                        }
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier.widthIn(max = 840.dp)
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 18.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = contenido,
            )
        }
    }
}

@Composable
fun TarjetaSeccion(titulo: String, contenido: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(
        Modifier.fillMaxWidth().animateContentSize(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = habilitado, shape = MaterialTheme.shapes.medium) {
        Icon(icono, null)
        Spacer(Modifier.width(8.dp))
        Text(texto)
    }
}

@Composable
fun BotonSecundario(texto: String, onClick: () -> Unit, habilitado: Boolean = true) {
    OutlinedButton(onClick, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = habilitado, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
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
        shape = MaterialTheme.shapes.medium,
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
            Resultado.OBSERVADO -> if (androidx.compose.foundation.isSystemInDarkTheme()) androidx.compose.ui.graphics.Color(0xFFFFC675) else cl.induztek.protocol.ui.theme.Alerta
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
