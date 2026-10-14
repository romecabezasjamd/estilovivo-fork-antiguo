package com.estilovivo.app.feature.social.shop

import com.estilovivo.app.core.data.ImageUpload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Descarga la foto de una prenda para volver a subirla (p. ej. al copiar un favorito al armario). */
interface ImageFetcher {
    suspend fun fetch(url: String): ImageUpload?
}

/** Las fotos de las prendas son públicas: se descargan sin credenciales. */
class OkHttpImageFetcher(private val client: OkHttpClient) : ImageFetcher {
    override suspend fun fetch(url: String): ImageUpload? = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://") && !url.startsWith("http://")) return@withContext null
        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val mime = response.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase().orEmpty()
                if (!mime.startsWith("image/")) return@use null
                val bytes = response.body.bytes()
                if (bytes.isEmpty() || bytes.size > MAX_BYTES) return@use null
                val ext = when (mime) {
                    "image/png" -> "png"
                    "image/webp" -> "webp"
                    else -> "jpg"
                }
                ImageUpload("garment-${System.currentTimeMillis()}.$ext", mime, bytes)
            }
        }.getOrNull()
    }

    private companion object {
        const val MAX_BYTES = 12 * 1024 * 1024
    }
}
