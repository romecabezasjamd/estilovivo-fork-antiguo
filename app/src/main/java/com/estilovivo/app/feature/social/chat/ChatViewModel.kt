package com.estilovivo.app.feature.social.chat

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.ImageUpload
import com.estilovivo.app.core.data.model.Conversation
import com.estilovivo.app.core.data.model.Message
import com.estilovivo.app.core.data.repo.ChatRepository
import com.estilovivo.app.core.data.socket.SocketEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.UUID

enum class SendStatus { Sent, Sending, Failed }

/** Mensaje listo para pintar. [key] es estable aunque el mensaje pase de "enviando" a "enviado". */
@Immutable
data class ChatMessageUi(
    val key: String,
    val serverId: String?,
    val senderId: String,
    val mine: Boolean,
    val content: String,
    /** URL remota (String) o `Uri` local mientras se sube. */
    val image: Any?,
    val createdAtIso: String,
    val createdAtMillis: Long,
    val status: SendStatus,
    /** Cuándo apareció en este dispositivo (0 = venía en la carga inicial). Sirve para animar sólo lo nuevo. */
    val arrivedAt: Long,
)

@Immutable
data class ChatUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val conversation: Conversation? = null,
    val messages: List<ChatMessageUi> = emptyList(),
    val otherTyping: Boolean = false,
    /** Reacciones locales: clave del mensaje -> emoji. */
    val reactions: Map<String, String> = emptyMap(),
    val meId: String? = null,
)

sealed interface ChatEvent {
    data class Error(val message: String) : ChatEvent

    /** El envío falló: devuelve el texto al campo para no perderlo. */
    data class RestoreText(val text: String) : ChatEvent
}

class ChatViewModel(
    private val conversationId: String,
    private val chat: ChatRepository,
    private val socket: ChatSocket,
    private val outbox: ChatOutbox,
    private val meId: () -> String?,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private class Pending(
        val localId: String,
        val content: String,
        val preview: Any?,
        val upload: ImageUpload?,
        val createdAt: Long,
        var status: SendStatus = SendStatus.Sending,
    )

    private var confirmed: List<Message> = emptyList()
    private val pending = ArrayList<Pending>()
    private val arrivals = HashMap<String, Long>()
    private val keyAlias = HashMap<String, String>() // id del servidor -> clave local del mensaje optimista
    private var conversation: Conversation? = null
    private var loadedOnce = false
    private var loading = true
    private var error: String? = null
    private var otherTyping = false
    private var reactions: Map<String, String> = emptyMap()

    private val _state = MutableStateFlow(ChatUiState(meId = meId()))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private val eventChannel = Channel<ChatEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private val sendMutex = Mutex()
    private var reloadJob: Job? = null
    private var typingClearJob: Job? = null
    private var typingStopJob: Job? = null
    private var lastTypingSent = 0L
    private var typingActive = false

    init {
        socket.joinRoom(conversationId)
        viewModelScope.launch {
            socket.events.collect { event ->
                when (event) {
                    is SocketEvent.NewMessage -> if (event.message.conversationId == conversationId) onIncoming(event.message)
                    is SocketEvent.Typing -> if (event.roomId == conversationId && event.userId != meId()) onOtherTyping(event.isTyping)
                    else -> Unit
                }
            }
        }
        viewModelScope.launch {
            var wasDisconnected = false
            socket.connected.collect { up ->
                if (!up) wasDisconnected = true
                else if (wasDisconnected) { wasDisconnected = false; reloadMessages() } // recupera lo que llegó mientras no había conexión
            }
        }
        load()
    }

    fun retryLoad() = load()

    private fun load() {
        loading = true
        error = null
        publish()
        viewModelScope.launch {
            coroutineScope {
                val messages = async { chat.getMessages(conversationId) }
                val conversations = async { chat.getConversations() }
                conversations.await().getOrNull()?.firstOrNull { it.id == conversationId }?.let { conversation = it }
                when (val r = messages.await()) {
                    is ApiResult.Success -> {
                        applyServerMessages(r.value)
                        loadedOnce = true
                    }
                    is ApiResult.Failure -> error = r.error.message
                }
            }
            loading = false
            publish()
        }
    }

    /** Recarga silenciosa (reconexión, mensaje inicial sintético). */
    private fun reloadMessages() {
        if (reloadJob?.isActive == true) return
        reloadJob = viewModelScope.launch {
            val r = chat.getMessages(conversationId)
            if (r is ApiResult.Success) {
                applyServerMessages(r.value)
                publish()
            }
        }
    }

    private fun applyServerMessages(server: List<Message>) {
        val now = clock()
        val known = confirmed.mapTo(HashSet()) { it.id }
        // Los mensajes con id sintético ("init_…") se sustituyen por los reales que ya vienen de la base de datos
        val base = if (server.isNotEmpty()) confirmed.filterNot { it.isSyntheticInitial() } else confirmed
        server.forEach { if (loadedOnce && it.id !in known) arrivals[it.id] = now }
        confirmed = mergeMessages(base, server)
    }

    // ───────────────────────── Recepción ─────────────────────────

    private fun onIncoming(message: Message) {
        if (message.senderId != meId()) {
            otherTyping = false
            typingClearJob?.cancel()
        }
        if (confirmed.any { it.id == message.id }) { publish(); return } // duplicado: ya lo tenemos
        // Eco de un mensaje propio que ya mostramos de forma optimista (otro dispositivo o servidor que devuelva al emisor)
        if (message.senderId == meId() && pending.any { it.status == SendStatus.Sending && it.content == message.content && it.upload == null }) {
            return
        }
        arrivals[message.id] = clock()
        confirmed = mergeMessages(confirmed, listOf(message))
        if (message.isSyntheticInitial()) reloadMessages()
        publish()
    }

    private fun onOtherTyping(isTyping: Boolean) {
        typingClearJob?.cancel()
        otherTyping = isTyping
        publish()
        if (isTyping) {
            // Por si el aviso de "deja de escribir" se pierde
            typingClearJob = viewModelScope.launch {
                delay(TYPING_TIMEOUT_MS)
                otherTyping = false
                publish()
            }
        }
    }

    // ───────────────────────── Envío ─────────────────────────

    /** Devuelve `true` si el mensaje se aceptó (la UI puede vaciar el campo). */
    fun sendText(raw: String): Boolean {
        val text = raw.trim()
        if (text.isEmpty()) return false
        val content = text.take(MAX_LENGTH)
        stopTyping()
        val p = Pending(newLocalId(), content, null, null, clock())
        pending += p
        publish()
        viewModelScope.launch { deliver(p) }
        return true
    }

    fun sendImage(upload: ImageUpload, preview: Any?) {
        val p = Pending(newLocalId(), "", preview, upload, clock())
        pending += p
        publish()
        viewModelScope.launch { deliver(p) }
    }

    /** Reintenta una imagen que no se pudo enviar. */
    fun retry(key: String) {
        val p = pending.firstOrNull { it.localId == key && it.status == SendStatus.Failed } ?: return
        p.status = SendStatus.Sending
        publish()
        viewModelScope.launch { deliver(p) }
    }

    /** Descarta un envío fallido. */
    fun discard(key: String) {
        if (pending.removeAll { it.localId == key && it.status == SendStatus.Failed }) publish()
    }

    private suspend fun deliver(p: Pending) {
        // En orden: dos mensajes seguidos no deben cruzarse en el servidor
        sendMutex.withLock {
            val result = if (p.upload != null) chat.sendImage(conversationId, p.upload, p.content) else chat.sendMessage(conversationId, p.content)
            when (result) {
                is ApiResult.Success -> {
                    pending.remove(p)
                    val sent = result.value
                    keyAlias[sent.id] = p.localId
                    if (confirmed.none { it.id == sent.id }) arrivals[sent.id] = p.createdAt
                    confirmed = mergeMessages(confirmed, listOf(sent))
                    outbox.publish(sent)
                    publish()
                }
                is ApiResult.Failure -> {
                    if (p.upload != null) {
                        p.status = SendStatus.Failed
                    } else {
                        pending.remove(p) // rollback: el mensaje desaparece y el texto vuelve al campo
                        eventChannel.trySend(ChatEvent.RestoreText(p.content))
                    }
                    eventChannel.trySend(ChatEvent.Error(result.error.message))
                    publish()
                }
            }
        }
    }

    // ───────────────────────── Escribiendo… ─────────────────────────

    /** Llamar en cada cambio del campo de texto (con limitación para no saturar el socket). */
    fun onInputChanged(text: String) {
        if (text.isBlank()) { stopTyping(); return }
        val now = clock()
        if (!typingActive || now - lastTypingSent > TYPING_THROTTLE_MS) {
            lastTypingSent = now
            typingActive = true
            socket.typing(conversationId, true)
        }
        typingStopJob?.cancel()
        typingStopJob = viewModelScope.launch {
            delay(TYPING_IDLE_MS)
            stopTyping()
        }
    }

    private fun stopTyping() {
        typingStopJob?.cancel()
        if (typingActive) {
            typingActive = false
            socket.typing(conversationId, false)
        }
    }

    // ───────────────────────── Reacciones locales ─────────────────────────

    /** Pone, cambia o quita (si es el mismo emoji) la reacción de un mensaje. Sólo local, no se envía. */
    fun toggleReaction(key: String, emoji: String) {
        reactions = if (reactions[key] == emoji) reactions - key else reactions + (key to emoji)
        publish()
    }

    // ───────────────────────── Estado ─────────────────────────

    private fun publish() {
        val me = meId()
        val sent = confirmed.map { m ->
            val millis = parseMillis(m.createdAt)
            ChatMessageUi(
                key = keyAlias[m.id] ?: m.id,
                serverId = m.id,
                senderId = m.senderId,
                mine = m.senderId == me,
                content = m.content,
                image = m.imageUrl,
                createdAtIso = m.createdAt,
                createdAtMillis = millis,
                status = SendStatus.Sent,
                arrivedAt = arrivals[m.id] ?: 0L,
            )
        }
        val notSent = pending.map { p ->
            ChatMessageUi(
                key = p.localId,
                serverId = null,
                senderId = me.orEmpty(),
                mine = true,
                content = p.content,
                image = p.preview,
                createdAtIso = Instant.ofEpochMilli(p.createdAt).toString(),
                createdAtMillis = p.createdAt,
                status = p.status,
                arrivedAt = p.createdAt,
            )
        }
        _state.value = ChatUiState(
            loading = loading,
            error = if (loadedOnce) null else error,
            conversation = conversation,
            messages = sent + notSent, // los pendientes siempre al final, aunque el reloj del móvil vaya adelantado o atrasado
            otherTyping = otherTyping,
            reactions = reactions,
            meId = me,
        )
    }

    private fun newLocalId() = "local-" + UUID.randomUUID()

    override fun onCleared() {
        stopTyping()
        socket.leaveRoom(conversationId)
        super.onCleared()
    }

    companion object {
        const val MAX_LENGTH = 2000
        const val TYPING_THROTTLE_MS = 2_500L
        const val TYPING_IDLE_MS = 3_000L
        const val TYPING_TIMEOUT_MS = 5_000L
    }
}
