package com.estilovivo.app.core.data.net

import com.estilovivo.app.core.data.ApiError
import com.estilovivo.app.core.data.ApiErrorKind
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import retrofit2.HttpException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

private const val MSG_SESSION = "Tu sesión ha caducado. Vuelve a iniciar sesión."
private const val MSG_LINK = "El enlace no es válido o ha caducado."

private val KNOWN_MESSAGES: Map<String, String> = mapOf(
    "invalid credentials" to "Email o contraseña incorrectos.",
    "email already registered" to "Ya existe una cuenta con este email.",
    "token required" to "Necesitas iniciar sesión.",
    "invalid refresh token" to MSG_SESSION,
    "refresh token expired" to MSG_SESSION,
    "refresh token required" to MSG_SESSION,
    "not authorized" to "No tienes permiso para hacer esto.",
    "user not found" to "No hemos encontrado a ese usuario.",
    "invalidcurrentpassword" to "La contraseña actual no es correcta.",
    "samepassword" to "La nueva contraseña debe ser distinta de la actual.",
    "invalid or expired verification token" to "El enlace de verificación no es válido o ha caducado.",
    "smtp not configured" to "El servidor no puede enviar correos ahora mismo. Inténtalo más tarde.",
    "cannot follow yourself" to "No puedes seguirte a ti mismo.",
    "cannot chat with yourself" to "No puedes chatear contigo mismo.",
    "item not found" to "No se ha encontrado el elemento.",
    "label required" to "Escribe un nombre para el elemento.",
    "admin only" to "Solo disponible para administradores.",
    "no image provided" to "Selecciona una imagen.",
    "error de validación" to "Revisa los datos e inténtalo de nuevo.",
    "invalid productids" to "Los datos enviados no son válidos.",
    "invalid productids json" to "Los datos enviados no son válidos.",
    "invalid tags" to "Los datos enviados no son válidos.",
    "invalid layers" to "Los datos enviados no son válidos.",
    "invalid thumbnail" to "La miniatura no es válida.",
    "invalid imageurl" to "La imagen no es válida.",
    "internal server error" to "Ha ocurrido un error en el servidor. Inténtalo de nuevo en un momento.",
    "request failed" to "No se pudo completar la acción. Inténtalo de nuevo.",
)

private fun genericForStatus(status: Int): String = when {
    status == 401 -> MSG_SESSION
    status == 403 -> "No tienes permiso para hacer esto."
    status == 404 -> "No hemos encontrado lo que buscas."
    status == 408 -> "La conexión está tardando demasiado. Inténtalo de nuevo."
    status == 413 -> "El archivo es demasiado grande."
    status == 429 -> "Demasiadas peticiones. Espera un momento e inténtalo de nuevo."
    status >= 500 -> "Ha ocurrido un error en el servidor. Inténtalo de nuevo en un momento."
    else -> "No se pudo completar la acción. Inténtalo de nuevo."
}

private val GENERIC_STATUS_MESSAGE = Regex("^Error \\d{3}(:.*)?$")

private fun JsonObject.stringField(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

/**
 * Extrae un mensaje legible (en español) de una respuesta de error. Réplica de `parseApiErrorMessage` de la web:
 * primero `details[0].message` (validación zod), luego `error`, `message` y `detail`.
 */
fun parseApiErrorMessage(status: Int, contentType: String?, body: String?): String {
    val looksJson = contentType?.contains("json", ignoreCase = true) == true ||
        body?.trimStart()?.startsWith("{") == true
    val raw: String = if (looksJson) {
        val obj: JsonObject = runCatching { ApiJson.parseToJsonElement(body.orEmpty()).jsonObject }
            .getOrDefault(JsonObject(emptyMap()))
        val details = obj["details"] as? JsonArray
        val detailMsg = (details?.firstOrNull() as? JsonObject)?.stringField("message")
        detailMsg
            ?: obj.stringField("error")
            ?: obj.stringField("message")
            ?: obj.stringField("detail")
            ?: "Error $status"
    } else {
        val text = body.orEmpty().trim()
        if (text.isEmpty() || text.length > 200) "Error $status" else text
    }
    return localize(raw, status)
}

internal fun localize(raw: String, status: Int): String {
    val key = raw.trim().lowercase()
    if (key == "invalid or expired token") return if (status == 403 || status == 401) MSG_SESSION else MSG_LINK
    KNOWN_MESSAGES[key]?.let { return it }
    if (raw.isBlank() || GENERIC_STATUS_MESSAGE.matches(raw.trim())) return genericForStatus(status)
    return raw.trim()
}

private fun kindFor(status: Int): ApiErrorKind = when {
    status == 401 -> ApiErrorKind.Unauthorized
    status == 403 -> ApiErrorKind.Forbidden
    status == 404 -> ApiErrorKind.NotFound
    status == 400 || status == 422 -> ApiErrorKind.Validation
    status == 429 -> ApiErrorKind.RateLimited
    status >= 500 -> ApiErrorKind.Server
    else -> ApiErrorKind.Unknown
}

/** Traduce cualquier excepción de red/HTTP/serialización a un [ApiError] legible. */
internal fun Throwable.toApiError(): ApiError = when (this) {
    is HttpException -> {
        val errBody = response()?.errorBody()
        val text = runCatching { errBody?.string() }.getOrNull()
        val type = errBody?.contentType()?.toString()
        ApiError(parseApiErrorMessage(code(), type, text), kindFor(code()), code())
    }
    is SocketTimeoutException, is InterruptedIOException ->
        ApiError("La conexión está tardando demasiado. Inténtalo de nuevo.", ApiErrorKind.Timeout)
    is SerializationException, is IllegalArgumentException ->
        ApiError("Respuesta inválida del servidor. Inténtalo de nuevo.", ApiErrorKind.Server)
    is IOException ->
        ApiError("No hay conexión con el servidor. Revisa tu internet e inténtalo de nuevo.", ApiErrorKind.Network)
    else -> ApiError("Algo no ha ido bien. Inténtalo de nuevo.", ApiErrorKind.Unknown)
}
