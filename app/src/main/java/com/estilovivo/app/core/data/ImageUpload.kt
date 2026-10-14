package com.estilovivo.app.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Imagen lista para subir (en memoria). Créala con [fromUri] (galería/cámara) o [fromFile].
 * Las fotos grandes se reducen (máx. 2048 px) y se corrige la orientación EXIF antes de subirlas.
 */
@Immutable
class ImageUpload(val fileName: String, val mimeType: String, val bytes: ByteArray) {

    internal fun toPart(field: String): MultipartBody.Part =
        MultipartBody.Part.createFormData(field, fileName, bytes.toRequestBody(mimeType.toMediaType()))

    companion object {
        private const val MAX_RAW_BYTES = 2 * 1024 * 1024
        private const val MAX_DIMENSION = 2048
        private val PASS_THROUGH = setOf("image/jpeg", "image/png", "image/webp")

        suspend fun fromUri(context: Context, uri: Uri, maxDimension: Int = MAX_DIMENSION): ImageUpload? =
            withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = context.applicationContext.contentResolver
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
                    val mime = (resolver.getType(uri) ?: "image/jpeg").lowercase()
                    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    } ?: "image.jpg"
                    prepare(name, mime, bytes, maxDimension)
                }.getOrNull()
            }

        suspend fun fromFile(file: File, maxDimension: Int = MAX_DIMENSION): ImageUpload? =
            withContext(Dispatchers.IO) {
                runCatching {
                    val mime = when (file.extension.lowercase()) {
                        "png" -> "image/png"
                        "webp" -> "image/webp"
                        else -> "image/jpeg"
                    }
                    prepare(file.name, mime, file.readBytes(), maxDimension)
                }.getOrNull()
            }

        private fun prepare(name: String, mime: String, bytes: ByteArray, maxDimension: Int): ImageUpload {
            val orientation = runCatching {
                ExifInterface(ByteArrayInputStream(bytes))
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            if (bytes.size <= MAX_RAW_BYTES && mime in PASS_THROUGH && orientation == ExifInterface.ORIENTATION_NORMAL) {
                return ImageUpload(name, mime, bytes)
            }
            // Re-codifica: reduce tamaño y aplica la rotación EXIF (al re-codificar se pierde el EXIF).
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxDimension || bounds.outHeight / (sample * 2) >= maxDimension) sample *= 2
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return ImageUpload(name, mime, bytes)
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                }
                val longest = maxOf(decoded.width, decoded.height)
                if (longest > maxDimension) {
                    val s = maxDimension.toFloat() / longest
                    postScale(s, s)
                }
            }
            val out = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            val keepPng = mime == "image/png"
            val stream = ByteArrayOutputStream()
            out.compress(if (keepPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 88, stream)
            if (out !== decoded) decoded.recycle()
            out.recycle()
            val ext = if (keepPng) "png" else "jpg"
            return ImageUpload(name.substringBeforeLast('.', name) + ".$ext", if (keepPng) "image/png" else "image/jpeg", stream.toByteArray())
        }
    }
}
