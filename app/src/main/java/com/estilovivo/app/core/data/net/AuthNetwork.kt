package com.estilovivo.app.core.data.net

import com.estilovivo.app.core.data.session.AuthSession
import okhttp3.Authenticator
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import java.io.IOException

/** Rutas que nunca deben disparar un refresco (evita bucles y falsos "sesión caducada"). Igual que la web. */
internal val NO_REFRESH_PATHS = listOf(
    "/auth/login", "/auth/register", "/auth/refresh", "/auth/forgot-password",
    "/auth/reset-password", "/auth/verify-email", "/auth/resend-verification", "/auth/change-password",
)

private fun HttpUrl.isNoRefresh(): Boolean = NO_REFRESH_PATHS.any { encodedPath.endsWith(it) }

/** Sólo se manda el token a nuestro propio servidor. */
private fun HttpUrl.isOwn(config: ApiConfig): Boolean = host.equals(config.host, ignoreCase = true)

private fun Request.withBearer(token: String): Request =
    newBuilder().header("Authorization", "Bearer $token").build()

/** Añade `Authorization: Bearer <access>` a las peticiones a nuestra API. */
internal class AuthHeaderInterceptor(private val session: AuthSession, private val config: ApiConfig) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = session.accessToken
        if (token == null || request.header("Authorization") != null || !request.url.isOwn(config)) return chain.proceed(request)
        return chain.proceed(request.withBearer(token))
    }
}

/**
 * El backend responde **403** `Invalid or expired token` (no 401) cuando el JWT caduca, y [Authenticator] sólo
 * se invoca con 401. Este interceptor *de red* (anterior al seguimiento de [Authenticator]) reescribe ese 403 a
 * 401 conservando el cuerpo, para que el flujo de refresco estándar de OkHttp se encargue.
 */
internal class TokenErrorNormalizer(private val config: ApiConfig) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code != 403 || !chain.request().url.isOwn(config) || chain.request().url.isNoRefresh()) return response
        val body = response.peekBody(2048).string()
        val isTokenError = body.contains("Invalid or expired token") || body.contains("Token required")
        return if (isTokenError) response.newBuilder().code(401).message("Unauthorized").build() else response
    }
}

internal sealed interface RefreshResult {
    data class Refreshed(val accessToken: String, val refreshToken: String?) : RefreshResult

    /** El servidor rechazó el refresh token: la sesión no es recuperable. */
    data object Rejected : RefreshResult

    /** Sin red / servidor caído: no se pierde la sesión. */
    data object Unavailable : RefreshResult
}

/** Llama a `POST /auth/refresh` con un cliente aparte (sin interceptores de auth). */
internal class TokenRefresher(private val client: OkHttpClient, private val config: ApiConfig) {
    fun refresh(refreshToken: String): RefreshResult {
        val body = ApiJson.encodeToString(RefreshRequest.serializer(), RefreshRequest(refreshToken))
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(config.apiBaseUrl + "auth/refresh").post(body).build()
        return try {
            client.newCall(request).execute().use { res ->
                when {
                    res.isSuccessful -> {
                        val dto = runCatching { ApiJson.decodeFromString(AuthResponseDto.serializer(), res.body.string()) }.getOrNull()
                        if (dto?.token != null) RefreshResult.Refreshed(dto.token, dto.refreshToken) else RefreshResult.Unavailable
                    }
                    res.code in 400..499 -> RefreshResult.Rejected
                    else -> RefreshResult.Unavailable
                }
            }
        } catch (_: IOException) {
            RefreshResult.Unavailable
        }
    }
}

/**
 * Ante un 401 en una petición autenticada: renueva el par de tokens (el refresh token rota) y reintenta UNA vez.
 * Si el refresh es rechazado, o la petición reintentada vuelve a dar 401, limpia la sesión y emite
 * [com.estilovivo.app.core.data.session.SessionEvent.SessionExpired].
 * Las peticiones concurrentes se serializan: la segunda reutiliza el token ya renovado por la primera.
 */
internal class TokenAuthenticator(
    private val session: AuthSession,
    private val config: ApiConfig,
    private val refresher: TokenRefresher,
) : Authenticator {
    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        val request = response.request
        if (!request.url.isOwn(config) || request.url.isNoRefresh()) return null
        val sentToken = request.header("Authorization")?.removePrefix("Bearer ")?.trim()
        if (sentToken.isNullOrEmpty()) return null // petición anónima: no hay sesión que renovar

        // Ya se reintentó una vez y sigue sin autorización: sesión perdida
        if (generateSequence(response.priorResponse) { it.priorResponse }.any { it.code == 401 }) {
            session.expire()
            return null
        }

        synchronized(lock) {
            val current = session.accessToken
            // Otra petición ya renovó el token mientras esperábamos
            if (current != null && current != sentToken) return request.withBearer(current)

            val refreshToken = session.refreshToken
            if (refreshToken == null) {
                session.expire()
                return null
            }
            return when (val result = refresher.refresh(refreshToken)) {
                is RefreshResult.Refreshed -> {
                    session.saveTokens(result.accessToken, result.refreshToken)
                    request.withBearer(result.accessToken)
                }
                RefreshResult.Rejected -> {
                    session.expire()
                    null
                }
                RefreshResult.Unavailable -> null
            }
        }
    }
}
