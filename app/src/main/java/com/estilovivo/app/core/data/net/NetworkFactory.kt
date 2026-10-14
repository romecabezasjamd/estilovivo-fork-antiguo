package com.estilovivo.app.core.data.net

import com.estilovivo.app.core.data.session.AuthSession
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/** Construcción de OkHttp/Retrofit. Separado de Koin para poder probarlo con MockWebServer. */
internal object NetworkFactory {

    /** Cliente sin interceptores de auth: sólo para `/auth/refresh`. */
    fun refreshClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Cliente principal. Orden: cabecera Bearer (app) -> [TokenErrorNormalizer] (red, 403 -> 401) -> [TokenAuthenticator].
     * El logging (sólo si [debug]) registra método/URL/código, nunca cabeceras `Authorization` ni cuerpos.
     */
    fun apiClient(session: AuthSession, config: ApiConfig, debug: Boolean, refreshClient: OkHttpClient = refreshClient()): OkHttpClient {
        val refresher = TokenRefresher(refreshClient, config)
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS) // subidas de fotos
            .callTimeout(120, TimeUnit.SECONDS)
            .addInterceptor(AuthHeaderInterceptor(session, config))
            .addNetworkInterceptor(TokenErrorNormalizer(config))
            .authenticator(TokenAuthenticator(session, config, refresher))
            .apply {
                if (debug) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                            redactHeader("Authorization")
                            redactHeader("Cookie")
                            redactHeader("Set-Cookie")
                        },
                    )
                }
            }
            .build()
    }

    fun retrofit(client: OkHttpClient, config: ApiConfig): Retrofit = Retrofit.Builder()
        .baseUrl(config.apiBaseUrl)
        .client(client)
        .addConverterFactory(ApiJson.asConverterFactory("application/json; charset=UTF-8".toMediaType()))
        .build()

    fun service(retrofit: Retrofit): ApiService = retrofit.create(ApiService::class.java)
}
