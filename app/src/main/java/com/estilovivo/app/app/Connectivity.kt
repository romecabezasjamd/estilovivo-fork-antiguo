package com.estilovivo.app.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Se incrementa cada vez que vuelve la conexión tras una caída. Las pantallas que quieran recargar solas pueden
 * hacer `LaunchedEffect(LocalNetworkRestoredTick.current) { ... }` (el valor inicial es 0: no recargar en ese caso).
 */
val LocalNetworkRestoredTick = compositionLocalOf { 0 }

/** `true` mientras hay una red con salida a Internet validada. Emite el valor actual al suscribirse. */
fun Context.connectivityFlow(): Flow<Boolean> = callbackFlow {
    val cm = getSystemService(ConnectivityManager::class.java)
    if (cm == null) { trySend(true); close(); return@callbackFlow }
    fun ok(caps: NetworkCapabilities?) = caps != null &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    trySend(ok(cm.getNetworkCapabilities(cm.activeNetwork)))
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { trySend(ok(caps)) }
        override fun onLost(network: Network) { trySend(ok(cm.getNetworkCapabilities(cm.activeNetwork))) }
    }
    try {
        cm.registerDefaultNetworkCallback(callback)
    } catch (_: SecurityException) {
        trySend(true) // sin permiso de estado de red no se molesta con falsos avisos
    }
    awaitClose { runCatching { cm.unregisterNetworkCallback(callback) } }
}.distinctUntilChanged()

val OfflineBannerHeight = 30.dp

/** Franja superior animada: "Sin conexión" (oscura) y brevemente "Conexión recuperada" (verde). */
@Composable
fun OfflineBanner(offline: Boolean, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    val bg by animateColorAsState(if (offline) Color(0xFF1F2937) else c.success, tween(250), label = "offlineBg")
    Row(
        modifier.fillMaxWidth().background(bg).statusBarsPadding().height(OfflineBannerHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        AnimatedContent(
            targetState = offline,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
            label = "offlineText",
        ) { off ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(if (off) Icons.Rounded.CloudOff else Icons.Rounded.Wifi, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text(
                    if (off) "Sin conexión · reintentaremos al volver la red" else "Conexión recuperada",
                    style = EvType.caption, color = Color.White,
                )
            }
        }
    }
}
