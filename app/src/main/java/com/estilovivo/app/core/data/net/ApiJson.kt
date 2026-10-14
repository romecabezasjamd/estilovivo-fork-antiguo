package com.estilovivo.app.core.data.net

import kotlinx.serialization.json.Json

/** Configuración JSON única para Retrofit, sesión y sockets. */
internal val ApiJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    isLenient = true
    encodeDefaults = false
}
