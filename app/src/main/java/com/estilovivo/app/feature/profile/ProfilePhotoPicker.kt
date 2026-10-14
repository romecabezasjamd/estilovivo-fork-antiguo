package com.estilovivo.app.feature.profile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

@Stable
class ProfilePhotoPicker(val camera: () -> Unit, val gallery: () -> Unit)

internal object PhotoRules {
    const val MAX_BYTES = 10L * 1024 * 1024
    private val OK = setOf("image/jpeg", "image/jpg", "image/png", "image/webp")

    /** Motivo por el que una imagen no vale, o null si es válida. */
    fun problem(mime: String?, size: Long?): String? = when {
        mime != null && mime.lowercase() !in OK -> "Usa una foto JPG, PNG o WebP."
        size != null && size > MAX_BYTES -> "La foto pesa demasiado (máximo 10 MB)."
        else -> null
    }
}

/** Cámara (con permiso) y galería. La URI de la foto de cámara sobrevive a la muerte del proceso. */
@Composable
fun rememberProfilePhotoPicker(onPicked: (Uri) -> Unit, onProblem: (String) -> Unit): ProfilePhotoPicker {
    val context = LocalContext.current
    val picked = rememberUpdatedState(onPicked)
    val problem = rememberUpdatedState(onProblem)
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }

    fun deliver(uri: Uri) {
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        val size = sizeOf(context, uri)
        val msg = PhotoRules.problem(mime, size)
        if (msg != null) problem.value(msg) else picked.value(uri)
    }

    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = cameraUri
        cameraUri = null
        if (u != null && ok) deliver(Uri.parse(u))
    }

    fun startCamera() {
        runCatching {
            val dir = File(context.cacheDir, "images").apply { mkdirs() }
            val file = File.createTempFile("profile_", ".jpg", dir)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            cameraUri = uri.toString()
            take.launch(uri)
        }.onFailure { problem.value("No se pudo abrir la cámara. Prueba con la galería.") }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera()
        else problem.value("Necesitamos permiso de cámara para hacer la foto. Puedes activarlo en los ajustes del móvil o elegir una de la galería.")
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) deliver(uri) }

    return remember {
        ProfilePhotoPicker(
            camera = {
                when {
                    !context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) ->
                        problem.value("Este dispositivo no tiene cámara. Elige una foto de la galería.")
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> startCamera()
                    else -> permission.launch(Manifest.permission.CAMERA)
                }
            },
            gallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        )
    }
}

private fun sizeOf(context: Context, uri: Uri): Long? {
    var size: Long? = null
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) size = c.getLong(0)
        }
    }
    return size
}
