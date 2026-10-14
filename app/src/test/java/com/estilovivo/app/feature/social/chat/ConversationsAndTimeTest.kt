package com.estilovivo.app.feature.social.chat

import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.model.Conversation
import com.estilovivo.app.core.data.model.LastMessage
import com.estilovivo.app.core.data.model.UserSummary
import com.estilovivo.app.core.data.socket.SocketEvent
import com.estilovivo.app.feature.social.FakeChatRepository
import com.estilovivo.app.feature.social.FakeChatSocket
import com.estilovivo.app.feature.social.MainDispatcherRule
import com.estilovivo.app.feature.social.failure
import com.estilovivo.app.feature.social.message
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationsAndTimeTest {
    @get:Rule val main = MainDispatcherRule()

    private val chat = FakeChatRepository()
    private val socket = FakeChatSocket()
    private val outbox = ChatOutbox()

    private fun conv(id: String, lastAt: String, text: String = "hola") = Conversation(
        id = id,
        updatedAt = lastAt,
        lastMessage = LastMessage("l-$id", text, lastAt, UserSummary("other", "Otra")),
        otherUser = UserSummary("u-$id", "Persona $id"),
    )

    private fun vm() = ConversationsViewModel(chat, socket, outbox) { "me" }

    @Test fun `ordena por actividad reciente`() = runTest {
        chat.conversations = ApiResult.Success(
            listOf(conv("a", "2026-05-10T10:00:00.000Z"), conv("b", "2026-05-12T10:00:00.000Z"), conv("c", "2026-05-11T10:00:00.000Z")),
        )
        assertEquals(listOf("b", "c", "a"), vm().state.value.conversations.map { it.id })
    }

    @Test fun `un mensaje en tiempo real actualiza el ultimo mensaje y sube la conversacion`() = runTest {
        chat.conversations = ApiResult.Success(listOf(conv("a", "2026-05-12T10:00:00.000Z"), conv("b", "2026-05-11T10:00:00.000Z")))
        val vm = vm()
        socket.flow.emit(SocketEvent.NewMessage(message("m9", "2026-05-12T11:00:00.000Z", "¿Sigue disponible?", conversation = "b")))
        val list = vm.state.value.conversations
        assertEquals(listOf("b", "a"), list.map { it.id })
        assertEquals("¿Sigue disponible?", list.first().lastMessage?.content)
    }

    @Test fun `un mensaje antiguo no pisa uno mas reciente`() = runTest {
        chat.conversations = ApiResult.Success(listOf(conv("a", "2026-05-12T10:00:00.000Z", "nuevo")))
        val vm = vm()
        socket.flow.emit(SocketEvent.NewMessage(message("m0", "2026-05-12T09:00:00.000Z", "viejo", conversation = "a")))
        assertEquals("nuevo", vm.state.value.conversations.single().lastMessage?.content)
    }

    @Test fun `mensaje propio publicado en el outbox actualiza la fila`() = runTest {
        chat.conversations = ApiResult.Success(listOf(conv("a", "2026-05-12T10:00:00.000Z"), conv("b", "2026-05-12T10:30:00.000Z")))
        val vm = vm()
        outbox.publish(message("m1", "2026-05-12T12:00:00.000Z", "Gracias", sender = "me", conversation = "a"))
        assertEquals(listOf("a", "b"), vm.state.value.conversations.map { it.id })
    }

    @Test fun `conversacion desconocida provoca recarga`() = runTest {
        chat.conversations = ApiResult.Success(listOf(conv("a", "2026-05-12T10:00:00.000Z")))
        val vm = vm()
        val before = chat.getConversationsCalls
        chat.conversations = ApiResult.Success(listOf(conv("n", "2026-05-12T12:00:00.000Z"), conv("a", "2026-05-12T10:00:00.000Z")))
        socket.flow.emit(SocketEvent.NewMessage(message("m1", "2026-05-12T12:00:00.000Z", conversation = "n")))
        advanceUntilIdle()
        assertEquals(before + 1, chat.getConversationsCalls)
        assertEquals(listOf("n", "a"), vm.state.value.conversations.map { it.id })
    }

    @Test fun `error sin datos y reintento`() = runTest {
        chat.conversations = failure("Sin conexión")
        val vm = vm()
        assertEquals("Sin conexión", vm.state.value.error)
        chat.conversations = ApiResult.Success(listOf(conv("a", "2026-05-12T10:00:00.000Z")))
        vm.refresh(silent = false)
        assertNull(vm.state.value.error)
        assertNotNull(vm.state.value.conversations.singleOrNull())
    }

    // ───────── tiempo ─────────

    private val madrid = ZoneId.of("Europe/Madrid")
    private fun millis(iso: String) = ZonedDateTime.parse(iso).toInstant().toEpochMilli()

    @Test fun `hora relativa para la lista`() {
        val now = millis("2026-05-12T14:00:00+02:00")
        fun rel(iso: String) = ChatTime.relative(iso, now, madrid)
        assertEquals("ahora", rel("2026-05-12T11:59:40Z"))
        assertEquals("15 min", rel("2026-05-12T11:45:00Z"))
        assertEquals("09:30", rel("2026-05-12T07:30:00Z"))
        assertEquals("Ayer", rel("2026-05-11T20:00:00Z"))
        assertEquals("sáb", rel("2026-05-09T10:00:00Z"))
        assertEquals("1 may", rel("2026-05-01T10:00:00Z"))
        assertEquals("", rel("no es una fecha"))
    }

    @Test fun `etiquetas de dia`() {
        val today = LocalDate.of(2026, 5, 12)
        assertEquals("Hoy", ChatTime.dayLabel(today, today))
        assertEquals("Ayer", ChatTime.dayLabel(today.minusDays(1), today))
        assertEquals("Lunes 4 de mayo", ChatTime.dayLabel(LocalDate.of(2026, 5, 4), today))
        assertEquals("3 de enero de 2025", ChatTime.dayLabel(LocalDate.of(2025, 1, 3), today))
    }
}
