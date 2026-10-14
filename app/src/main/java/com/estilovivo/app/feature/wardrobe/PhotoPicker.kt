package com.estilovivo.app.feature.wardrobe

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

/** Cámara y galería listas para usar desde cualquier formulario. */
@Stable
class PhotoPicker(val camera: () -> Unit, val gallery: () -> Unit)

/**
 * Cámara (`TakePicture` + FileProvider, con permiso CAMERA) y galería (`PickVisualMedia`).
 * Valida tipo (JPEG/PNG/WebP) y tamaño (≤ 10 MB) antes de entregar la foto; si no vale, [onProblem] lleva el motivo.
 * La URI de la foto de cámara se guarda con `rememberSaveable`: sobrevive si el sistema mata la app mientras se hace la foto.
 */
@Composable
fun rememberPhotoPicker(onPicked: (Uri) -> Unit, onProblem: (String) -> Unit): PhotoPicker {
    val context = LocalContext.current
    val picked = rememberUpdatedState(onPicked)
    val problem = rememberUpdatedState(onProblem)
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }

    fun deliver(uri: Uri) {
        val (mime, size) = inspectImage(context, uri)
        val msg = GarmentFormRules.imageProblem(mime, size)
        if (msg != null) problem.value(msg) else picked.value(uri)
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = cameraUri
        cameraUri = null
        if (u != null) {
            if (ok) deliver(Uri.parse(u)) else discardTempFile(context, Uri.parse(u))
        }
    }

    fun startCamera() {
        runCatching {
            val dir = File(context.cacheDir, "images").apply { mkdirs() }
            val file = File.createTempFile("garment_", ".jpg", dir)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            cameraUri = uri.toString()
            takePicture.launch(uri)
        }.onFailure { problem.value("No se pudo abrir la cámara. Prueba con la galería.") }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else problem.value("Necesitamos permiso de cámara para hacer la foto. Puedes activarlo en Ajustes o elegir una foto de la galería.")
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) deliver(uri)
    }

    return remember {
        PhotoPicker(
            camera = {
                if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
                    problem.value("Este dispositivo no tiene cámara. Elige una foto de la galería.")
                } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    startCamera()
                } else {
                    permission.launch(Manifest.permission.CAMERA)
                }
            },
            gallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        )
    }
}

/** (tipo MIME, tamaño en bytes) de una URI; null si no se sabe. */
internal fun inspectImage(context: Context, uri: Uri): Pair<String?, Long?> {
    val resolver = context.contentResolver
    val mime = runCatching { resolver.getType(uri) }.getOrNull()
    var size: Long? = null
    runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) size = c.getLong(0)
        }
    }
    return mime to size
}

private fun discardTempFile(context: Context, uri: Uri) {
    runCatching { context.contentResolver.delete(uri, null, null) }
}
