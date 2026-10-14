package com.estilovivo.app.feature.wardrobe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.estilovivo.app.core.image.CutoutModelStatus
import com.estilovivo.app.core.image.FallbackGarmentCutout
import com.estilovivo.app.core.image.FloodFillGarmentCutout
import com.estilovivo.app.core.image.MlKitGarmentCutout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

/**
 * Recorte de la foto de una prenda (ML Kit con respaldo flood-fill). Singleton de Koin.
 * Guarda el PNG recortado en `cacheDir/images/cutout_<hash>.png`; la misma foto no se recorta dos veces.
 */
class AndroidPhotoCutout(context: Context) {
    private val app = context.applicationContext
    private val mlKit = MlKitGarmentCutout(app)
    private val dir = File(app.cacheDir, "images")

    /** `Preparing` mientras se descarga el módulo de ML Kit ("Preparando el recorte…"). */
    val status: StateFlow<CutoutModelStatus> = mlKit.status

    val decider = PhotoCutoutDecider(
        load = ::load,
        cutout = FallbackGarmentCutout(mlKit, FloodFillGarmentCutout()),
        store = ::store,
        cached = ::cachedFor,
    )

    private fun fileFor(sourceUri: String): File {
        val h = MessageDigest.getInstance("SHA-1").digest(sourceUri.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "${PreparedPhoto.PREFIX}$h.png")
    }

    private fun cachedFor(sourceUri: String): String? =
        fileFor(sourceUri).takeIf { it.isFile && it.length() > 0 }?.let { Uri.fromFile(it).toString() }

    /** Decodifica la foto a ≤ 1600 px de lado, con la orientación EXIF aplicada. */
    private suspend fun load(sourceUri: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = app.contentResolver
            val uri = Uri.parse(sourceUri)
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
            val orientation = runCatching {
                ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= MAX_SIDE || bounds.outHeight / (sample * 2) >= MAX_SIDE) sample *= 2
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return@runCatching null
            val m = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                }
            }
            if (m.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
        }.getOrNull()
    }

    /** Guarda el PNG con alfa; si pasa de ~1,9 MB lo reescala (hasta 4 intentos). null si no cabe. */
    private suspend fun store(sourceUri: String, bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        runCatching {
            dir.mkdirs()
            var bmp = bitmap
            repeat(MAX_ATTEMPTS) {
                val out = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                val scale = CutoutSize.scaleFor(out.size())
                if (scale == null) {
                    val target = fileFor(sourceUri)
                    val tmp = File(dir, target.name + ".tmp")
                    tmp.writeBytes(out.toByteArray())
                    if (!tmp.renameTo(target)) { tmp.delete(); return@runCatching null }
                    trimOld()
                    return@runCatching Uri.fromFile(target).toString()
                }
                bmp = Bitmap.createScaledBitmap(
                    bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true,
                )
            }
            null
        }.getOrNull()
    }

    private fun trimOld() {
        dir.listFiles { f -> f.name.startsWith(PreparedPhoto.PREFIX) && f.name.endsWith(".png") }
            .orEmpty().sortedByDescending { it.lastModified() }.drop(MAX_FILES).forEach { it.delete() }
    }

    private companion object {
        const val MAX_SIDE = 1600
        const val MAX_ATTEMPTS = 4
        const val MAX_FILES = 30
    }
}
