package com.estilovivo.app.core.data

import androidx.compose.runtime.Immutable

/** Categoría del error para que la UI decida cómo reaccionar (reintentar, ir a login, etc.). */
enum class ApiErrorKind { Network, Timeout, Unauthorized, Forbidden, NotFound, Validation, RateLimited, Server, Unknown }

/** Error de API ya legible: [message] está en español y se puede mostrar tal cual en un toast. */
@Immutable
data class ApiError(
    val message: String,
    val kind: ApiErrorKind = ApiErrorKind.Unknown,
    val httpStatus: Int? = null,
)

/** Resultado de cualquier llamada a un repositorio. Nunca lanza excepciones (salvo cancelación de corrutina). */
sealed interface ApiResult<out T> {
    @Immutable
    data class Success<out T>(val value: T) : ApiResult<T>

    @Immutable
    data class Failure(val error: ApiError) : ApiResult<Nothing>

    val isSuccess: Boolean get() = this is Success

    fun getOrNull(): T? = (this as? Success)?.value

    fun errorOrNull(): ApiError? = (this as? Failure)?.error
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(value))
    is ApiResult.Failure -> this
}

inline fun <T, R> ApiResult<T>.fold(onSuccess: (T) -> R, onFailure: (ApiError) -> R): R = when (this) {
    is ApiResult.Success -> onSuccess(value)
    is ApiResult.Failure -> onFailure(error)
}

inline fun <T> ApiResult<T>.onSuccess(block: (T) -> Unit): ApiResult<T> {
    if (this is ApiResult.Success) block(value)
    return this
}

inline fun <T> ApiResult<T>.onFailure(block: (ApiError) -> Unit): ApiResult<T> {
    if (this is ApiResult.Failure) block(error)
    return this
}

fun <T> ApiResult<T>.getOrDefault(default: T): T = getOrNull() ?: default

/** Página de resultados de un endpoint paginado por cursor. */
@Immutable
data class Page<out T>(val items: List<T>, val nextCursor: String? = null, val hasMore: Boolean = false)
