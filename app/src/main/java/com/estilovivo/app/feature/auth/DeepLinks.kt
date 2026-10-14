package com.estilovivo.app.feature.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URI
import java.net.URLDecoder

/** Enlaces entrantes que la app sabe tratar. */
sealed interface AppLink {
    data class VerifyEmail(val token: String) : AppLink
    data class ResetPassword(val token: String) : AppLink

    /** `/auth?error=invalid_token` (el servidor redirige así un enlace de recuperación caducado). */
    data class AuthError(val serverError: Boolean) : AppLink
    data class Look(val id: String) : AppLink
    data class User(val id: String) : AppLink
}

/**
 * Valida y convierte URLs en [AppLink]. Sólo se aceptan `https://estilovivo.xyoncloud.win` (puerto estándar, sin
 * credenciales) y el esquema `estilovivo://`. Los tokens/ids se validan por formato y longitud; nada se registra.
 */
object DeepLinkParser {
    const val HOST = "estilovivo.xyoncloud.win"
    private const val MAX_URL = 2048
    private val TOKEN = Regex("^[A-Za-z0-9_-]{16,256}$")
    private val ID = Regex("^[A-Za-z0-9_-]{1,64}$")

    fun parse(url: String?): AppLink? {
        if (url.isNullOrBlank() || url.length > MAX_URL) return null
        val uri = try { URI(url.trim()) } catch (_: Exception) { return null }
        val scheme = uri.scheme?.lowercase() ?: return null
        val segments: List<String>
        when (scheme) {
            "https" -> {
                if (uri.host?.lowercase() != HOST || uri.userInfo != null || (uri.port != -1 && uri.port != 443)) return null
                segments = pathSegments(uri) ?: return null
            }
            // estilovivo://auth?..., estilovivo://reset-password/TOKEN, estilovivo:///auth...
            "estilovivo" -> {
                val head = uri.host?.lowercase()?.let { listOf(it) }.orEmpty()
                segments = head + (pathSegments(uri) ?: return null)
            }
            else -> return null
        }
        val query = queryOf(uri)
        return when (segments.firstOrNull()?.lowercase()) {
            "auth" -> if (segments.size == 1) authLink(query) else null
            "reset-password" -> segments.getOrNull(1)?.takeIf { segments.size == 2 && TOKEN.matches(it) }?.let(AppLink::ResetPassword)
            "look" -> segments.getOrNull(1)?.takeIf { segments.size == 2 && ID.matches(it) }?.let(AppLink::Look)
            "user" -> segments.getOrNull(1)?.takeIf { segments.size == 2 && ID.matches(it) }?.let(AppLink::User)
            else -> null
        }
    }

    private fun authLink(q: Map<String, String>): AppLink? {
        val verifyToken = q["verifyToken"]
        val token = q["token"]
        val type = q["type"]
        return when {
            token != null && (type == "reset" || (type == null && "reset" in q)) ->
                token.takeIf { TOKEN.matches(it) }?.let(AppLink::ResetPassword)
            verifyToken != null -> verifyToken.takeIf { TOKEN.matches(it) }?.let(AppLink::VerifyEmail)
            token != null && (type == "verify" || (type == null && "verify" in q)) ->
                token.takeIf { TOKEN.matches(it) }?.let(AppLink::VerifyEmail)
            q["error"] == "invalid_token" -> AppLink.AuthError(serverError = false)
            q["error"] == "server_error" -> AppLink.AuthError(serverError = true)
            else -> null
        }
    }

    private fun pathSegments(uri: URI): List<String>? {
        val raw = uri.rawPath.orEmpty()
        return try {
            raw.split('/').filter { it.isNotEmpty() }.map { URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }
        } catch (_: Exception) { null }
    }

    private fun queryOf(uri: URI): Map<String, String> {
        val raw = uri.rawQuery ?: return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (pair in raw.split('&')) {
            if (pair.isEmpty()) continue
            val i = pair.indexOf('=')
            val k = if (i < 0) pair else pair.substring(0, i)
            val v = if (i < 0) "" else pair.substring(i + 1)
            try {
                val key = URLDecoder.decode(k, "UTF-8")
                if (key !in out) out[key] = URLDecoder.decode(v, "UTF-8")
            } catch (_: Exception) { /* par malformado: se ignora */ }
        }
        return out
    }
}

/** Enlace pendiente de tratar; [seq] distingue dos entregas del mismo enlace. */
data class PendingLink(val seq: Long, val link: AppLink)

/** Puente entre `MainActivity` (intents) y la UI. Un único enlace pendiente; el último gana. */
class DeepLinkHub {
    private var counter = 0L
    private val _pending = MutableStateFlow<PendingLink?>(null)
    val pending: StateFlow<PendingLink?> = _pending.asStateFlow()

    /** @return true si la URL era un enlace válido. */
    @Synchronized
    fun submit(url: String?): Boolean {
        val link = DeepLinkParser.parse(url) ?: return false
        _pending.value = PendingLink(++counter, link)
        return true
    }

    /** Marca como tratado (sólo si sigue siendo el mismo). */
    @Synchronized
    fun consume(seq: Long) {
        if (_pending.value?.seq == seq) _pending.value = null
    }
}
