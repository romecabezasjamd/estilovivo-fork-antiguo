package com.estilovivo.app.feature.social.chat

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.model.Conversation
import com.estilovivo.app.core.data.model.LastMessage
import com.estilovivo.app.core.data.model.Message
import com.estilovivo.app.core.data.repo.ChatRepository
import com.estilovivo.app.core.data.socket.SocketEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class ConversationsUiState(
    val conversations: List<Conversation> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    /** Error a pantalla completa (sólo si no hay nada que enseñar). */
    val error: String? = null,
    val meId: String? = null,
)

class ConversationsViewModel(
    private val chat: ChatRepository,
    private val socket: ChatSocket,
    outbox: ChatOutbox,
    private val meId: () -> String?,
) : ViewModel() {

    private val _state = MutableStateFlow(ConversationsUiState(meId = meId()))
    val state: StateFlow<ConversationsUiState> = _state.asStateFlow()

    private val errorChannel = Channel<String>(Channel.BUFFERED)
    val errors = errorChannel.receiveAsFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            socket.events.collect { event ->
                if (event is SocketEvent.NewMessage) onMessage(event.message)
            }
        }
        viewModelScope.launch { outbox.sent.collect { onMessage(it) } }
        viewModelScope.launch {
            var wasDisconnected = false
            socket.connected.collect { up ->
                if (!up) wasDisconnected = true else if (wasDisconnected) { wasDisconnected = false; refresh(silent = true) }
            }
        }
        refresh(silent = false)
    }

    /** [silent] = no muestra el indicador (al volver a la pestaña o al reconectar). */
    fun refresh(silent: Boolean = true, userInitiated: Boolean = false) {
        loadJob?.cancel()
        _state.update {
            it.copy(
                loading = !silent && it.conversations.isEmpty(),
                refreshing = userInitiated,
                error = if (silent) it.error else null,
                meId = meId(),
            )
        }
        loadJob = viewModelScope.launch {
            when (val r = chat.getConversations()) {
                is ApiResult.Success -> _state.update {
                    it.copy(conversations = sortedByActivity(r.value), loading = false, refreshing = false, error = null)
                }
                is ApiResult.Failure -> {
                    val hadItems = _state.value.conversations.isNotEmpty()
                    _state.update { it.copy(loading = false, refreshing = false, error = if (hadItems) it.error else r.error.message) }
                    if (hadItems && !silent) errorChannel.trySend(r.error.message)
                }
            }
        }
    }

    /** Mensaje nuevo (recibido o enviado por mí): actualiza la fila y reordena sin recargar. */
    private fun onMessage(m: Message) {
        val current = _state.value.conversations
        val index = current.indexOfFirst { it.id == m.conversationId }
        if (index < 0) {
            refresh(silent = true) // conversación nueva que aún no conocemos (p. ej. alguien nos escribe por primera vez)
            return
        }
        val old = current[index]
        // No retroceder: un mensaje antiguo (reenvío del socket, carga tardía) no debe pisar uno más reciente
        val newer = parseMillis(m.createdAt) >= parseMillis(old.lastMessage?.createdAt ?: old.updatedAt)
        if (!newer && old.lastMessage != null) return
        val updated = old.copy(
            updatedAt = m.createdAt,
            lastMessage = LastMessage(m.id, m.content.ifEmpty { if (m.imageUrl != null) IMAGE_LABEL else "" }, m.createdAt, m.sender),
        )
        _state.update { s ->
            s.copy(conversations = sortedByActivity(s.conversations.map { if (it.id == old.id) updated else it }))
        }
    }

    private fun sortedByActivity(list: List<Conversation>) =
        list.sortedByDescending { maxOf(parseMillis(it.lastMessage?.createdAt.orEmpty()), parseMillis(it.updatedAt)) }

    companion object {
        const val IMAGE_LABEL = "Foto"
    }
}
