package cl.induztek.protocol.native

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import cl.induztek.protocol.ui.components.BotonSecundario
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*

object ConvertidorImagen {
    fun aBytes(bitmap: Bitmap): ByteArray {
        val escala = minOf(1.0, 400.0 / maxOf(bitmap.width, bitmap.height))
        val chica =
            Bitmap.createScaledBitmap(
                bitmap,
                maxOf(1, (bitmap.width * escala).toInt()),
                maxOf(1, (bitmap.height * escala).toInt()),
                true,
            )
        return ByteArrayOutputStream().use {
            chica.compress(Bitmap.CompressFormat.JPEG, 80, it)
            it.toByteArray()
        }
    }
}

@Composable
fun FotoEquipo(bytes: ByteArray?, editable: Boolean, guardar: (ByteArray) -> Unit) {
    val contexto = LocalContext.current
    var aviso by remember { mutableStateOf("") }
    val foto =
        remember(bytes) {
            bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
        }
    val scope = rememberCoroutineScope()
    val camara =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            if (bitmap == null) aviso = "Captura cancelada: se conserva la fotografía anterior"
            else
                scope.launch {
                    try {
                        val datos =
                            withContext(Dispatchers.Default) { ConvertidorImagen.aBytes(bitmap) }
                        aviso = ""
                        guardar(datos)
                    } catch (_: Exception) {
                        aviso = "No se pudo procesar la fotografía"
                    }
                }
        }
    fun abrir() {
        aviso = ""
        try {
            camara.launch(null)
        } catch (_: ActivityNotFoundException) {
            aviso = "Este dispositivo no tiene una aplicación de cámara"
        } catch (_: SecurityException) {
            aviso = "No se pudo acceder a la cámara. Revisa el permiso en Ajustes"
        }
    }
    val permiso =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido
            ->
            if (concedido) abrir()
            else
                aviso =
                    "Permiso de cámara denegado. Puedes habilitarlo en Ajustes; el registro de pruebas sigue disponible"
        }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        foto?.let {
            Image(
                it,
                "Fotografía de identificación del equipo",
                Modifier.fillMaxWidth().height(180.dp),
                contentScale = ContentScale.Fit,
            )
        }
        if (editable)
            BotonSecundario(
                "Fotografiar equipo",
                onClick = {
                    if (
                        ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) ==
                            PackageManager.PERMISSION_GRANTED
                    )
                        abrir()
                    else permiso.launch(Manifest.permission.CAMERA)
                },
            )
        if (aviso.isNotBlank()) Text(aviso, style = MaterialTheme.typography.bodySmall)
        if (editable && aviso.contains("denegado"))
            TextButton(
                onClick = {
                    try {
                        contexto.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${contexto.packageName}"),
                            )
                        )
                    } catch (_: ActivityNotFoundException) {
                        aviso = "No se pudieron abrir los ajustes"
                    }
                }
            ) {
                Text("Abrir ajustes de permisos")
            }
    }
}

object CompartirDocumento {

    fun crearPdf(contexto: Context, texto: String): Uri {
        val carpeta = File(contexto.cacheDir, "documentos").apply { mkdirs() }
        carpeta
            .listFiles()
            ?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }
            ?.forEach { it.delete() }
        val destino = File(carpeta, "induztek-${UUID.randomUUID()}.pdf")
        val logo = android.graphics.BitmapFactory.decodeResource(contexto.resources, cl.induztek.protocol.R.drawable.logo_induztek)
        val pdf = PdfDocument()
        try {
            val pintura =
                Paint().apply {
                    typeface = Typeface.MONOSPACE
                    textSize = 10f
                    color = Color.rgb(20, 45, 78)
                }
            val lineas =
                texto.lines().flatMap { linea ->
                    if (linea.isEmpty()) listOf("") else linea.chunked(86)
                }
            lineas.chunked(46).forEachIndexed { indice, grupo ->
                val pagina =
                    pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, indice + 1).create())
                pagina.canvas.drawBitmap(logo, null, android.graphics.RectF(33f, 24f, 77f, 68f), null)
                grupo.forEachIndexed { fila, linea ->
                    pagina.canvas.drawText(linea, 35f, 83f + fila * 15f, pintura)
                }
                pagina.canvas.drawText(
                    "Induztek · Documento académico · Página ${indice+1}",
                    35f,
                    810f,
                    pintura,
                )
                pdf.finishPage(pagina)
            }
            destino.outputStream().use { pdf.writeTo(it) }
        } finally {
            pdf.close()
            logo.recycle()
        }
        return FileProvider.getUriForFile(contexto, "${contexto.packageName}.files", destino)
    }

    fun abrir(contexto: Context, uri: Uri) {
        val envio =
            Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("Documento Induztek", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        contexto.startActivity(Intent.createChooser(envio, "Compartir respaldo Induztek"))
    }
}
