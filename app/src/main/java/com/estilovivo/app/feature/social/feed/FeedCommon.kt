package com.estilovivo.app.feature.social.feed

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.LocalEvToast
import com.estilovivo.app.core.design.components.EvButtonStyle
import com.estilovivo.app.core.design.components.EvImage
import com.estilovivo.app.core.design.components.EvPillButton
import com.estilovivo.app.core.design.evClickable
import java.io.File

// ─────────────────────────────────────────────────────────────────────────────
// Selección de fotos (cámara / galería) sin permisos de almacenamiento
// ─────────────────────────────────────────────────────────────────────────────

@Stable
class PhotoPicker internal constructor(val gallery: () -> Unit, val camera: () -> Unit)

/**
 * Cámara (con permiso en tiempo de ejecución, porque la app lo declara) y galería (selector del sistema).
 * El fichero de la cámara se guarda como ruta en `rememberSaveable`: si el proceso muere mientras la cámara
 * está abierta, la foto no se pierde. [onPicked] recibe un `Uri` legible con [com.estilovivo.app.core.data.ImageUpload.fromUri].
 */
@Composable
fun rememberPhotoPicker(onPicked: (Uri) -> Unit): PhotoPicker {
    val context = LocalContext.current
    val toast = LocalEvToast.current
    val current by rememberUpdatedState(onPicked)
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) current(uri)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = pendingPath
        pendingPath = null
        if (path != null) {
            val file = File(path)
            if (ok && file.length() > 0) current(file.toContentUri(context)) else file.delete()
        }
    }

    fun launchCamera() {
        val dir = File(context.cacheDir, "images").apply { mkdirs() }
        val file = runCatching { File.createTempFile("social_", ".jpg", dir) }.getOrNull()
        if (file == null) { toast.error("No se pudo preparar la foto. Revisa el espacio libre."); return }
        pendingPath = file.absolutePath
        try {
            cameraLauncher.launch(file.toContentUri(context))
        } catch (_: ActivityNotFoundException) {
            pendingPath = null
            file.delete()
            toast.error("No hay ninguna app de cámara. Elige una foto de la galería.")
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else toast.error("Sin permiso de cámara no puedo abrirla. Puedes elegir una foto de la galería.")
    }

    return remember {
        PhotoPicker(
            gallery = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            camera = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
                else permissionLauncher.launch(Manifest.permission.CAMERA)
            },
        )
    }
}

private fun File.toContentUri(context: Context): Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", this)

/** Dos botones grandes "Cámara" / "Galería". */
@Composable
fun PhotoSourceButtons(picker: PhotoPicker, modifier: Modifier = Modifier, height: Dp = 56.dp) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        EvPillButton("Cámara", picker.camera, Modifier.weight(1f), icon = Icons.Rounded.CameraAlt, height = height)
        EvPillButton("Galería", picker.gallery, Modifier.weight(1f), style = EvButtonStyle.Secondary, icon = Icons.Rounded.PhotoLibrary, height = height)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Piezas comunes
// ─────────────────────────────────────────────────────────────────────────────

/** Avatar redondo con inicial como alternativa. */
@Composable
fun EvAvatar(
    model: String?,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = name,
) {
    val c = EvTheme.colors
    Box(modifier.size(size).clip(CircleShape).background(c.surfaceAlt), contentAlignment = Alignment.Center) {
        if (model.isNullOrBlank()) {
            Text(
                name.trim().firstOrNull()?.uppercase() ?: "?",
                style = EvType.label.copy(fontSize = (size.value * 0.4f).sp), color = c.inkSoft,
            )
        } else {
            EvImage(model, contentDescription, Modifier.size(size))
        }
    }
}


/** Diálogo de confirmación con el estilo de la app. */
@Composable
fun EvConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = true,
    cancelText: String = "Cancelar",
) {
    val c = EvTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(EvShapes.card).background(c.surface).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = EvType.title, color = c.ink)
            Text(message, style = EvType.body, color = c.inkSoft)
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EvPillButton(confirmText, onConfirm, Modifier.fillMaxWidth(), style = if (danger) EvButtonStyle.Danger else EvButtonStyle.Primary, height = 50.dp)
                EvPillButton(cancelText, onDismiss, Modifier.fillMaxWidth(), style = EvButtonStyle.Ghost, height = 46.dp)
            }
        }
    }
}

/** Texto pequeño de sección ("HISTORIAS"). */
@Composable
fun EvOverline(text: String, modifier: Modifier = Modifier, color: Color = EvTheme.colors.inkMuted) {
    Text(text.uppercase(), style = EvType.caption.copy(letterSpacing = 1.4.sp), color = color, modifier = modifier)
}

/** Pastilla informativa (nivel, XP...). */
@Composable
fun EvPill(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = EvTheme.colors.surfaceAlt,
    content: Color = EvTheme.colors.inkSoft,
) {
    Text(
        text, style = EvType.caption, color = content, textAlign = TextAlign.Center,
        modifier = modifier.clip(CircleShape).background(container).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Fondo en degradado vertical oscuro para leer texto sobre fotos. */
fun scrimBrush(top: Boolean): Brush = if (top)
    Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent))
else
    Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000)))

/** Abre una URL http/https con el navegador. Devuelve `false` si no es segura o no hay app que la abra. */
fun openWebUrl(context: Context, url: String?): Boolean {
    if (!isSafeWebUrl(url)) return false
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url!!.trim())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try { context.startActivity(intent); true } catch (_: ActivityNotFoundException) { false }
}

fun shareText(context: Context, subject: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(Intent.createChooser(send, "Compartir").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
