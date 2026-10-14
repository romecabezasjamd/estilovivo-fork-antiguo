package com.estilovivo.app.feature.social.chat

import androidx.compose.runtime.Immutable
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.model.Message
import com.estilovivo.app.core.data.repo.ChatRepository
import com.estilovivo.app.core.data.socket.SocketEvent
import com.estilovivo.app.core.data.socket.SocketManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Lo que el chat necesita del socket (interfaz para poder probarlo con un falso). */
interface ChatSocket {
    val events: Flow<SocketEvent>
    val connected: StateFlow<Boolean>
    fun joinRoom(conversationId: String)
    fun leaveRoom(conversationId: String)
    fun typing(conversationId: String, isTyping: Boolean)
}

internal class SocketManagerChatSocket(private val socket: SocketManager) : ChatSocket {
    override val events: Flow<SocketEvent> get() = socket.events
    override val connected: StateFlow<Boolean> get() = socket.connected
    override fun joinRoom(conversationId: String) = socket.joinRoom(conversationId)
    override fun leaveRoom(conversationId: String) = socket.leaveRoom(conversationId)
    override fun typing(conversationId: String, isTyping: Boolean) = socket.typing(conversationId, isTyping)
}

/**
 * Mensajes propios ya confirmados por el servidor: el socket no los devuelve al emisor, así que la lista de
 * conversaciones los recibe por aquí para reordenarse y mostrar el último mensaje sin recargar.
 */
class ChatOutbox {
    private val _sent = MutableSharedFlow<Message>(extraBufferCapacity = 16)
    val sent: SharedFlow<Message> = _sent.asSharedFlow()
    fun publish(message: Message) {
        _sent.tryEmit(message)
    }
}

/**
 * Crea o recupera una conversación 1 a 1 (opcionalmente ligada a un artículo) y devuelve su id.
 * Lo usan la tienda, el perfil de otra persona y las historias.
 */
class ChatLauncher(private val chatRepo: ChatRepository) {
    suspend fun conversationWith(
        userId: String,
        itemId: String? = null,
        itemTitle: String? = null,
        itemImage: String? = null,
        initialMessage: String? = null,
    ): String? = open(userId, itemId, itemTitle, itemImage, initialMessage).getOrNull()

    /** Igual que [conversationWith] pero conservando el mensaje de error del servidor. */
    suspend fun open(
        userId: String,
        itemId: String? = null,
        itemTitle: String? = null,
        itemImage: String? = null,
        initialMessage: String? = null,
    ): ApiResult<String> = when (
        val r = chatRepo.createConversation(
            userId, itemId, itemTitle?.take(200), itemImage,
            initialMessage?.take(1000),
        )
    ) {
        is ApiResult.Success -> ApiResult.Success(r.value.id)
        is ApiResult.Failure -> r
    }
}

// ───────────────────────── Mensajes: orden y deduplicación ─────────────────────────

/** Hora del servidor (ISO-8601) en milisegundos; 0 si no se puede interpretar. */
internal fun parseMillis(iso: String): Long =
    runCatching { Instant.parse(iso).toEpochMilli() }
        .recoverCatching { ZonedDateTime.parse(iso).toInstant().toEpochMilli() }
        .getOrDefault(0L)

/**
 * Une [incoming] a [existing] sin duplicados (por id; gana la versión más reciente) y ordena cronológicamente.
 * El orden es estable: a igual hora se conserva el orden de llegada.
 */
internal fun mergeMessages(existing: List<Message>, incoming: Collection<Message>): List<Message> {
    val byId = LinkedHashMap<String, Message>(existing.size + incoming.size)
    existing.forEach { byId[it.id] = it }
    incoming.forEach { byId[it.id] = it }
    return byId.values.sortedBy { parseMillis(it.createdAt) }
}

/** El servidor emite el mensaje inicial de una conversación con un id sintético (`init_…`) que no existe en la base de datos. */
internal fun Message.isSyntheticInitial(): Boolean = id.startsWith("init_")

// ───────────────────────── Texto con enlaces ─────────────────────────

private val URL_REGEX = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
private const val TRAILING = ".,;:!?)]}»”"

@Immutable
data class TextSpanInfo(val start: Int, val end: Int, val url: String)

/** Enlaces http/https de un texto, sin la puntuación final. Cualquier otro esquema se deja como texto plano. */
fun findLinks(text: String): List<TextSpanInfo> =
    URL_REGEX.findAll(text).mapNotNull { m ->
        var end = m.range.last + 1
        while (end > m.range.first && text[end - 1] in TRAILING) end--
        val url = text.substring(m.range.first, end)
        if (url.substringAfter("://").isBlank()) null
        else TextSpanInfo(m.range.first, end, url)
    }.toList()

// ───────────────────────── Fechas ─────────────────────────

private val ES = Locale.forLanguageTag("es-ES")
private val HOUR = DateTimeFormatter.ofPattern("HH:mm", ES)
private val DAY_SHORT = DateTimeFormatter.ofPattern("d MMM", ES)
private val DAY_LONG = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES)
private val DAY_LONG_YEAR = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", ES)

object ChatTime {
    private fun zoned(iso: String, zone: ZoneId): ZonedDateTime? =
        parseMillis(iso).takeIf { it > 0L }?.let { Instant.ofEpochMilli(it).atZone(zone) }

    fun hour(iso: String, zone: ZoneId = ZoneId.systemDefault()): String = zoned(iso, zone)?.format(HOUR).orEmpty()

    /** Día natural del mensaje (para agrupar). */
    fun day(iso: String, zone: ZoneId = ZoneId.systemDefault()): LocalDate? = zoned(iso, zone)?.toLocalDate()

    /** Etiqueta del separador de día: "Hoy", "Ayer", "lunes 12 de mayo". */
    fun dayLabel(day: LocalDate, today: LocalDate = LocalDate.now()): String = when {
        day == today -> "Hoy"
        day == today.minusDays(1) -> "Ayer"
        day.year == today.year -> day.format(DAY_LONG).replaceFirstChar { it.uppercase() }
        else -> day.format(DAY_LONG_YEAR)
    }

    /** Hora relativa para la lista: "ahora", "12 min", "14:32", "Ayer", "lun", "12 may". */
    fun relative(iso: String, nowMillis: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
        val t = zoned(iso, zone) ?: return ""
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val minutes = ChronoUnit.MINUTES.between(t, now)
        if (minutes < 1) return "ahora"
        val dayDiff = ChronoUnit.DAYS.between(t.toLocalDate(), now.toLocalDate())
        return when {
            dayDiff <= 0L && minutes < 60 -> "$minutes min"
            dayDiff <= 0L -> t.format(HOUR)
            dayDiff == 1L -> "Ayer"
            dayDiff < 7 -> t.format(DateTimeFormatter.ofPattern("EEE", ES)).trimEnd('.')
            else -> t.format(DAY_SHORT).replace(".", "")
        }
    }
}
