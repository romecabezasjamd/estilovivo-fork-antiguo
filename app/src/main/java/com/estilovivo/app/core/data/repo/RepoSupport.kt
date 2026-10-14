package com.estilovivo.app.core.data.repo

import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.net.PageDto
import com.estilovivo.app.core.data.net.toApiError
import com.estilovivo.app.core.data.session.SessionManager
import com.estilovivo.app.core.data.session.SessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/** Ejecuta [block] convirtiendo cualquier error en [ApiResult.Failure] (la cancelación se propaga). */
internal suspend inline fun <T> apiCall(crossinline block: suspend () -> T): ApiResult<T> =
    try {
        ApiResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        ApiResult.Failure(e.toApiError())
    }

/** Respuesta sin un campo imprescindible. */
internal fun missing(what: String): Nothing = throw SerializationException("Falta $what en la respuesta")

private const val PAGE_LIMIT = 100
private const val MAX_PAGES = 50

/** Recorre un endpoint paginado por cursor hasta agotarlo (`limit=100`, como la web). */
internal suspend fun <D> fetchAllPages(fetch: suspend (limit: Int, cursor: String?) -> PageDto<D>): List<D> {
    val all = ArrayList<D>()
    var cursor: String? = null
    repeat(MAX_PAGES) {
        val page = fetch(PAGE_LIMIT, cursor)
        all += page.items
        if (!page.hasMore || page.nextCursor == null) return all
        cursor = page.nextCursor
    }
    return all
}

private val TEXT = "text/plain; charset=utf-8".toMediaType()

internal fun String.asTextPart(): RequestBody = toRequestBody(TEXT)

/** Construye el mapa de campos de texto de un multipart ignorando los nulos. */
internal fun textParts(vararg pairs: Pair<String, Any?>): Map<String, RequestBody> =
    buildMap { pairs.forEach { (k, v) -> if (v != null) put(k, v.toString().asTextPart()) } }

/** Vacía cachés cuando la sesión deja de estar activa. */
internal fun CoroutineScope.onSessionEnded(session: SessionManager, block: () -> Unit) {
    session.state.onEach { if (it !is SessionState.LoggedIn) block() }.launchIn(this)
}
