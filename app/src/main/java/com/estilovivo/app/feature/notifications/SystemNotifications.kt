package com.estilovivo.app.feature.notifications

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.estilovivo.app.R
import com.estilovivo.app.core.data.model.AppNotification
import com.estilovivo.app.core.data.model.NotificationTypes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Sabe si hay alguna actividad de la app visible (primer plano). Sin librerías extra: cuenta actividades "started". */
class AppForeground(app: Application) {
    @Volatile private var started = 0
    private val _state = MutableStateFlow(false)
    val state: StateFlow<Boolean> = _state.asStateFlow()
    val isForeground: Boolean get() = _state.value

    init {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: android.app.Activity) { started++; _state.value = true }
            override fun onActivityStopped(activity: android.app.Activity) { started = (started - 1).coerceAtLeast(0); _state.value = started > 0 }
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: android.app.Activity) = Unit
            override fun onActivityPaused(activity: android.app.Activity) = Unit
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: android.app.Activity) = Unit
        })
    }
}

/** ¿Se pueden mostrar notificaciones del sistema? (Android 13+ exige el permiso en tiempo de ejecución.) */
fun canPostNotifications(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return false
    return NotificationManagerCompat.from(context).areNotificationsEnabled()
}

/**
 * Notificaciones locales del sistema. Se llama sólo con la app en segundo plano.
 * Al tocarlas se abre la app con un Intent que lleva `tipo + relatedId` (validados al recibirlos, ver [NotificationLinks]).
 */
class AndroidSystemNotifier(private val context: Context) : SystemNotifier {
    init {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Actividad", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Mensajes, me gusta, comentarios, seguidores y avisos de tu armario."
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun show(notification: AppNotification) {
        if (!canPostNotifications(context)) return
        val open = NotificationLinks.buildIntent(context, notification) ?: return
        val pending = PendingIntent.getActivity(
            context,
            notification.id.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = notification.content.take(MAX_TEXT)
        val built = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(titleFor(notification.type))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(if (notification.type == NotificationTypes.CHAT) NotificationCompat.CATEGORY_MESSAGE else NotificationCompat.CATEGORY_SOCIAL)
            .setColor(0xFF0F172A.toInt())
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        // El id de notificación se deriva del id remoto: la misma notificación nunca se muestra dos veces.
        runCatching {
            NotificationManagerCompat.from(context).notify(notification.id, notification.id.hashCode(), built)
        } // SecurityException si el usuario retira el permiso a mitad: se ignora
    }

    private fun titleFor(type: String) = when (type) {
        NotificationTypes.CHAT -> "Nuevo mensaje"
        NotificationTypes.STORY_REACTION -> "Reacción a tu historia"
        NotificationTypes.LIKE -> "Nuevo me gusta"
        NotificationTypes.COMMENT -> "Nuevo comentario"
        NotificationTypes.FOLLOW -> "Seguidores"
        NotificationTypes.WASHING -> "Tu armario"
        else -> "EstiloVivo"
    }

    companion object {
        const val CHANNEL_ID = "estilovivo_activity"
        private const val MAX_TEXT = 240
    }
}

/**
 * Puente entre la notificación del sistema (u otro enlace `estilovivo://notification?...`) y la navegación.
 * `MainActivity` entrega el Intent con [offer]; `EvApp` consume el destino con [pending] cuando hay sesión.
 */
object NotificationLinks {
    private const val ACTION = "com.estilovivo.app.OPEN_NOTIFICATION"
    private const val EXTRA_TYPE = "ev_n_type"
    private const val EXTRA_RELATED = "ev_n_related"
    private const val EXTRA_CONTENT = "ev_n_content"
    private const val SCHEME = "estilovivo"
    private const val HOST = "notification"

    private val _pending = MutableStateFlow<NotificationDestination?>(null)
    val pending: StateFlow<NotificationDestination?> = _pending.asStateFlow()

    fun consume() { _pending.value = null }

    /** Intent para abrir la app en la notificación [n] (explícito: sólo lo puede resolver esta app). */
    fun buildIntent(context: Context, n: AppNotification): Intent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        val uri = Uri.Builder().scheme(SCHEME).authority(HOST).appendQueryParameter("id", n.id).build()
        return launch.apply {
            action = ACTION
            data = uri // distingue los PendingIntent entre sí
            putExtra(EXTRA_TYPE, n.type)
            putExtra(EXTRA_RELATED, n.relatedId)
            putExtra(EXTRA_CONTENT, n.content.take(200))
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }

    /** Lee un Intent entrante; si es una notificación válida, deja el destino en [pending]. Devuelve `true` si lo era. */
    fun offer(intent: Intent?): Boolean {
        intent ?: return false
        val data = intent.data
        val fromLink = data != null && data.scheme == SCHEME && data.host == HOST
        if (intent.action != ACTION && !fromLink) return false
        val type = intent.getStringExtra(EXTRA_TYPE) ?: data?.getQueryParameter("type")
        val related = intent.getStringExtra(EXTRA_RELATED) ?: data?.getQueryParameter("related")
        val content = intent.getStringExtra(EXTRA_CONTENT).orEmpty()
        intent.removeExtra(EXTRA_TYPE); intent.removeExtra(EXTRA_RELATED); intent.removeExtra(EXTRA_CONTENT)
        if (type == null || type.length > 32) return false
        val destination = destinationFor(type, content, related)
        if (destination == NotificationDestination.None) return false
        _pending.value = destination
        return true
    }
}
