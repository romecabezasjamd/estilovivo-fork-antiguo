package com.estilovivo.app.core.data.socket

import androidx.compose.runtime.Immutable
import com.estilovivo.app.core.data.AppScope
import com.estilovivo.app.core.data.model.AppNotification
import com.estilovivo.app.core.data.model.Message
import com.estilovivo.app.core.data.net.ApiConfig
import com.estilovivo.app.core.data.net.ApiJson
import com.estilovivo.app.core.data.net.ApiService
import com.estilovivo.app.core.data.net.Mappers
import com.estilovivo.app.core.data.net.MessageDto
import com.estilovivo.app.core.data.net.NotificationDto
import com.estilovivo.app.core.data.session.SessionManager
import com.estilovivo.app.core.data.session.SessionState
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Eventos en tiempo real del servidor. */
sealed interface SocketEvent {
    /** Mensaje nuevo de otra persona (los propios no se reciben: la UI usa envío optimista). */
    @Immutable
    data class NewMessage(val message: Message) : SocketEvent

    @Immutable
    data class Notification(val notification: AppNotification) : SocketEvent

    @Immutable
    data class LevelUp(val level: Int?, val experiencePoints: Int?) : SocketEvent

    @Immutable
    data class AchievementUnlocked(
        val key: String?,
        val title: String?,
        val description: String?,
        val icon: String?,
        val xpReward: Int?,
    ) : SocketEvent

    /** [isTyping] = false cuando la otra persona deja de escribir (`stop_typing`). */
    @Immutable
    data class Typing(val userId: String, val roomId: String, val isTyping: Boolean) : SocketEvent
}

/**
 * Conexión Socket.IO autenticada con el token vigente. Se conecta sola al iniciar sesión y se cierra al salir
 * (observa [SessionManager.state]); reconecta automáticamente y vuelve a entrar en las salas abiertas.
 * Si el servidor rechaza el token, renueva la sesión (una llamada autenticada dispara el refresh) y reconecta.
 */
class SocketManager internal constructor(
    private val config: ApiConfig,
    private val session: SessionManager,
    private val mappers: Mappers,
    private val api: ApiService,
    appScope: AppScope,
) {
    private val scope = appScope.scope
    private val lock = Any()
    private var socket: Socket? = null
    private val rooms = LinkedHashSet<String>()
    private val renewing = AtomicBoolean(false)
    @Volatile private var authFailures = 0

    private val _events = MutableSharedFlow<SocketEvent>(extraBufferCapacity = 64)

    /** Flujo de eventos (`new_message`, `notification`, `level_up`, `achievement_unlocked`, `typing`). */
    val events: SharedFlow<SocketEvent> = _events.asSharedFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val ioClient: OkHttpClient by lazy {
        OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).pingInterval(25, TimeUnit.SECONDS).build()
    }

    init {
        scope.launch {
            session.state.collect { state -> if (state is SessionState.LoggedIn) connect() else disconnect() }
        }
    }

    /** Abre la conexión si hay sesión (idempotente). Normalmente no hace falta llamarlo a mano. */
    fun connect() {
        synchronized(lock) {
            if (socket != null) return
            val token = session.accessToken ?: return
            val options = IO.Options().apply {
                forceNew = true
                reconnection = true
                reconnectionDelay = 1_000
                reconnectionDelayMax = 15_000
                timeout = 20_000
                auth = mapOf("token" to token)
                callFactory = ioClient
                webSocketFactory = ioClient
            }
            val s = IO.socket(URI.create(config.origin), options)
            wire(s)
            socket = s
            s.connect()
        }
    }

    /** Cierra la conexión y olvida las salas. */
    fun disconnect() {
        synchronized(lock) {
            socket?.let {
                it.off()
                it.disconnect()
                it.close()
            }
            socket = null
            rooms.clear()
        }
        authFailures = 0
        _connected.value = false
    }

    /** Entra en la sala de una conversación (necesario para recibir `typing`). Se repite solo tras reconectar. */
    fun joinRoom(conversationId: String) {
        synchronized(lock) {
            rooms += conversationId
            socket?.takeIf { it.connected() }?.emit("join_room", conversationId)
        }
    }

    fun leaveRoom(conversationId: String) {
        synchronized(lock) {
            rooms -= conversationId
            socket?.takeIf { it.connected() }?.emit("leave_room", conversationId)
        }
    }

    /** Avisa de que estás escribiendo ([isTyping] = true) o has parado. Requiere haber hecho [joinRoom]. */
    fun typing(conversationId: String, isTyping: Boolean = true) {
        val s = synchronized(lock) { socket }?.takeIf { it.connected() } ?: return
        s.emit(if (isTyping) "typing" else "stop_typing", JSONObject().put("roomId", conversationId))
    }

    private fun wire(s: Socket) {
        s.on(Socket.EVENT_CONNECT) {
            authFailures = 0
            _connected.value = true
            synchronized(lock) { rooms.forEach { room -> s.emit("join_room", room) } }
        }
        s.on(Socket.EVENT_DISCONNECT) { _connected.value = false }
        s.on(Socket.EVENT_CONNECT_ERROR) { args ->
            _connected.value = false
            val reason = args.firstOrNull()?.toString().orEmpty()
            if (reason.contains("token", ignoreCase = true) || reason.contains("Authentication", ignoreCase = true)) onAuthRejected()
        }
        s.on("new_message") { args ->
            parse(args) { json ->
                val dto = ApiJson.decodeFromString(MessageDto.serializer(), json)
                SocketEvent.NewMessage(mappers.message(dto))
            }
        }
        s.on("notification") { args ->
            parse(args) { json ->
                SocketEvent.Notification(mappers.notification(ApiJson.decodeFromString(NotificationDto.serializer(), json)))
            }
        }
        s.on("level_up") { args ->
            parse(args) { json ->
                val o = obj(json)
                SocketEvent.LevelUp(o.int("level"), o.int("experiencePoints") ?: o.int("xp"))
            }
        }
        s.on("achievement_unlocked") { args ->
            parse(args) { json ->
                val o = obj(json)
                val inner = (o["achievement"] as? JsonObject) ?: o
                SocketEvent.AchievementUnlocked(inner.str("key"), inner.str("title"), inner.str("description"), inner.str("icon"), inner.int("xpReward"))
            }
        }
        s.on("typing") { args -> parse(args) { json -> typingEvent(json, true) } }
        s.on("stop_typing") { args -> parse(args) { json -> typingEvent(json, false) } }
    }

    private fun typingEvent(json: String, isTyping: Boolean): SocketEvent? {
        val o = obj(json)
        val user = o.str("userId") ?: return null
        val room = o.str("roomId") ?: return null
        return SocketEvent.Typing(user, room, isTyping)
    }

    private inline fun parse(args: Array<Any?>, build: (String) -> SocketEvent?) {
        val raw = args.firstOrNull()?.toString() ?: return
        val event = runCatching { build(raw) }.getOrNull() ?: return
        _events.tryEmit(event)
    }

    private fun obj(json: String): JsonObject = ApiJson.parseToJsonElement(json).jsonObject
    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

    /** El servidor rechazó el token: se renueva con una llamada autenticada (el Authenticator hace el refresh) y se reconecta. */
    private fun onAuthRejected() {
        if (authFailures >= MAX_AUTH_RETRIES || !renewing.compareAndSet(false, true)) return
        authFailures++
        scope.launch {
            try {
                delay(1_500L * authFailures)
                runCatching { api.me() }
                if (session.state.value is SessionState.LoggedIn) {
                    val failures = authFailures
                    disconnect()
                    authFailures = failures
                    connect()
                }
            } finally {
                renewing.set(false)
            }
        }
    }

    private companion object {
        const val MAX_AUTH_RETRIES = 4
    }
}
