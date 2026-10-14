package com.estilovivo.app.feature.notifications

import com.estilovivo.app.core.data.model.AppNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** Muestra una notificación del sistema (barra de estado). La implementación Android está en [AndroidSystemNotifier]. */
fun interface SystemNotifier {
    fun show(notification: AppNotification)
}

/**
 * Decide qué hacer con cada notificación que llega por el socket:
 * banner dentro de la app (primer plano) o notificación del sistema (segundo plano), sin duplicados.
 */
class NotificationAlerts(
    incoming: Flow<AppNotification>,
    private val isForeground: () -> Boolean,
    private val system: SystemNotifier,
    scope: CoroutineScope,
) {
    private val seen = SeenIds()
    private val _inApp = MutableSharedFlow<AppNotification>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Notificaciones para mostrar como banner en la app (sólo se emiten con la app en primer plano). */
    val inApp: SharedFlow<AppNotification> = _inApp.asSharedFlow()

    init {
        incoming.onEach(::handle).launchIn(scope)
    }

    internal fun handle(n: AppNotification) {
        if (n.isRead) return
        when (alertAction(isForeground(), alreadySeen = !seen.firstTime(n.id))) {
            AlertAction.None -> Unit
            AlertAction.InAppBanner -> _inApp.tryEmit(n)
            AlertAction.SystemNotification -> runCatching { system.show(n) }
        }
    }
}

/**
 * PUSH REMOTO (FCM) — NO IMPLEMENTADO.
 *
 * El servidor todavía no envía push y falta `google-services.json`. Cuando existan, una implementación de esta
 * interfaz debería:
 *  1. Obtener el token de FCM (`FirebaseMessaging.getInstance().token`) tras iniciar sesión y en `onNewToken`.
 *  2. Enviarlo al backend (endpoint por definir, p. ej. `POST /api/push/register { token, platform: "android" }`).
 *  3. Llamar a [unregister] al cerrar sesión para dejar de recibir avisos de la cuenta anterior.
 *  4. En `FirebaseMessagingService.onMessageReceived` reutilizar [SystemNotifier.show] (mismo canal e Intent con
 *     tipo + relatedId) para que tocar el aviso lleve a la misma pantalla que las notificaciones locales.
 */
interface PushRegistrar {
    /** Registra este dispositivo para recibir push de la cuenta actual. */
    suspend fun register()

    /** Anula el registro (cierre de sesión). */
    suspend fun unregister()
}
