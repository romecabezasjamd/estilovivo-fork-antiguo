package com.estilovivo.app.core.data.net

import java.net.URI

/** Configuración de servidor derivada de `BuildConfig.API_BASE` (con o sin `/api` final). */
class ApiConfig(apiBase: String) {
    /** `https://host` sin barra final ni `/api`. */
    val origin: String = apiBase.trim().trimEnd('/').removeSuffix("/api").trimEnd('/')

    /** Base de Retrofit: `https://host/api/` (con barra final). */
    val apiBaseUrl: String = "$origin/api/"

    val host: String = runCatching { URI(origin).host }.getOrNull().orEmpty()
}

/** Convierte rutas relativas (`/api/uploads/...`) en URLs absolutas, igual que `resolveAssetUrl` de la web. */
class AssetUrls(val origin: String) {
    fun resolve(url: String?): String? {
        if (url.isNullOrEmpty()) return null
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("data:") || url.startsWith("blob:")) return url
        return if (url.startsWith("/")) "$origin$url" else "$origin/$url"
    }

    /** Inversa: quita el origen para enviar al servidor rutas relativas (p. ej. `imageUrl` de historias). */
    fun toRelative(url: String): String = if (url.startsWith(origin)) url.removePrefix(origin) else url

    val placeholder: String get() = "$origin/api/uploads/placeholder.png"
}
