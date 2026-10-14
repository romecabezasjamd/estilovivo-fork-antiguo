package com.estilovivo.app.feature.social.chat

import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.ImageUpload
import com.estilovivo.app.core.data.model.Conversation
import com.estilovivo.app.core.data.model.UserSummary
import com.estilovivo.app.core.data.socket.SocketEvent
import com.estilovivo.app.feature.social.FakeChatRepository
import com.estilovivo.app.feature.social.FakeChatSocket
import com.estilovivo.app.feature.social.MainDispatcherRule
import com.estilovivo.app.feature.social.failure
import com.estilovivo.app.feature.social.message
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val chat = FakeChatRepository()
    private val socket = FakeChatSocket()
    private val outbox = ChatOutbox()
    private var now = 1_000_000L

    private fun vm() = ChatViewModel("c1", chat, socket, outbox, meId = { "me" }, clock = { now })

    private fun ChatViewModel.ids() = state.value.messages.map { it.serverId ?: it.key }

    @Test fun `entra en la sala y sale al cerrar`() = runTest {
        val vm = vm()
        assertEquals(listOf("c1"), socket.joined)
        // onCleared es protegido: se invoca por reflexión como hace el framework
        ChatViewModel::class.java.getDeclaredMethod("onCleared").apply { isAccessible = true }.invoke(vm)
        assertEquals(listOf("c1"), socket.left)
    }

    @Test fun `los mensajes de la carga se ordenan por hora aunque lleguen desordenados`() = runTest {
        chat.messages = ApiResult.Success(
            listOf(
                message("m3", "2026-05-12T10:03:00.000Z"),
                message("m1", "2026-05-12T10:01:00.000Z"),
                message("m2", "2026-05-12T10:02:00.000Z", sender = "me"),
            ),
        )
        val vm = vm()
        assertEquals(listOf("m1", "m2", "m3"), vm.ids())
        assertTrue(vm.state.value.messages[1].mine)
        assertFalse(vm.state.value.loading)
    }

    @Test fun `un mensaje en tiempo real se inserta en su sitio cronologico`() = runTest {
        chat.messages = ApiResult.Success(listOf(message("m1", "2026-05-12T10:01:00.000Z"), message("m3", "2026-05-12T10:03:00.000Z")))
        val vm = vm()
        socket.flow.emit(SocketEvent.NewMessage(message("m2", "2026-05-12T10:02:00.000Z")))
        assertEquals(listOf("m1", "m2", "m3"), vm.ids())
    }

    @Test fun `un mensaje repetido por el socket no se duplica`() = runTest {
        chat.messages = ApiResult.Success(listOf(message("m1", "2026-05-12T10:01:00.000Z")))
        val vm = vm()
        val dup = message("m1", "2026-05-12T10:01:00.000Z")
        repeat(3) { socket.flow.emit(SocketEvent.NewMessage(dup)) }
        socket.flow.emit(SocketEvent.NewMessage(message("m2", "2026-05-12T10:02:00.000Z")))
        socket.flow.emit(SocketEvent.NewMessage(message("m2", "2026-05-12T10:02:00.000Z")))
        assertEquals(listOf("m1", "m2"), vm.ids())
    }

    @Test fun `mensajes de otra conversacion se ignoran`() = runTest {
        val vm = vm()
        socket.flow.emit(SocketEvent.NewMessage(message("x", "2026-05-12T10:01:00.000Z", conversation = "otra")))
        assertTrue(vm.state.value.messages.isEmpty())
    }

    @Test fun `el mensaje inicial sintetico se sustituye por el real tras recargar`() = runTest {
        val vm = vm()
        assertTrue(vm.state.value.messages.isEmpty())
        // El servidor emite un id sintético; en la base de datos el mensaje tiene otro id
        chat.messages = ApiResult.Success(listOf(message("real1", "2026-05-12T10:00:00.000Z", "Hola, me interesa")))
        socket.flow.emit(SocketEvent.NewMessage(message("init_c1_999", "2026-05-12T10:00:00.000Z", "Hola, me interesa")))
        advanceUntilIdle()
        assertEquals(listOf("real1"), vm.ids())
    }

    @Test fun `envio optimista aparece enseguida y se reemplaza por el del servidor conservando la clave`() = runTest {
        val vm = vm()
        val gate = CompletableDeferred<ApiResult<com.estilovivo.app.core.data.model.Message>>()
        chat.sendHandler = { _, _ -> gate.await() }
        val published = mutableListOf<String>()
        val job = launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) { outbox.sent.collect { published += it.id } }
        assertTrue(vm.sendText("  Hola 👋  "))
        val pending = vm.state.value.messages.single()
        assertEquals("Hola 👋", pending.content)
        assertEquals(SendStatus.Sending, pending.status)
        assertTrue(pending.mine)

        gate.complete(ApiResult.Success(message("srv1", "2026-05-12T10:00:05.000Z", "Hola 👋", sender = "me")))
        advanceUntilIdle()
        val sent = vm.state.value.messages.single()
        assertEquals(SendStatus.Sent, sent.status)
        assertEquals("srv1", sent.serverId)
        assertEquals("la clave no cambia para no recrear la burbuja", pending.key, sent.key)
        assertEquals(listOf("srv1"), published)
        job.cancel()
    }

    @Test fun `envio con fallo hace rollback devuelve el texto y avisa`() = runTest {
        val vm = vm()
        chat.sendHandler = { _, _ -> failure("Sin conexión. Revisa tu red.") }
        val events = mutableListOf<ChatEvent>()
        val job = launch { vm.events.toList(events) }
        assertTrue(vm.sendText("Hola"))
        advanceUntilIdle()
        assertTrue("la burbuja desaparece", vm.state.value.messages.isEmpty())
        assertEquals(listOf(ChatEvent.RestoreText("Hola"), ChatEvent.Error("Sin conexión. Revisa tu red.")), events)
        job.cancel()
    }

    @Test fun `texto vacio o solo espacios no se envia`() = runTest {
        val vm = vm()
        assertFalse(vm.sendText("   \n "))
        assertTrue(chat.sent.isEmpty())
    }

    @Test fun `dos mensajes seguidos se envian en orden`() = runTest {
        val vm = vm()
        val first = CompletableDeferred<ApiResult<com.estilovivo.app.core.data.model.Message>>()
        chat.sendHandler = { conv, content ->
            if (content == "uno") first.await()
            else ApiResult.Success(message("srv-$content", "2026-05-12T10:00:02.000Z", content, sender = "me", conversation = conv))
        }
        vm.sendText("uno")
        vm.sendText("dos")
        assertEquals("el segundo espera al primero", listOf("uno"), chat.sent)
        first.complete(ApiResult.Success(message("srv-uno", "2026-05-12T10:00:01.000Z", "uno", sender = "me")))
        advanceUntilIdle()
        assertEquals(listOf("uno", "dos"), chat.sent)
        assertEquals(listOf("srv-uno", "srv-dos"), vm.ids())
    }

    @Test fun `una imagen que falla se queda para reintentar y luego se envia`() = runTest {
        val vm = vm()
        chat.sendImageHandler = { failure("No se pudo subir la imagen.") }
        vm.sendImage(ImageUpload("a.jpg", "image/jpeg", byteArrayOf(1)), preview = "content://foto")
        advanceUntilIdle()
        val failed = vm.state.value.messages.single()
        assertEquals(SendStatus.Failed, failed.status)

        chat.sendImageHandler = { conv ->
            ApiResult.Success(message("srv-img", "2026-05-12T10:00:00.000Z", "", sender = "me", conversation = conv, imageUrl = "https://x/i.jpg"))
        }
        vm.retry(failed.key)
        advanceUntilIdle()
        val ok = vm.state.value.messages.single()
        assertEquals(SendStatus.Sent, ok.status)
        assertEquals(failed.key, ok.key)
    }

    @Test fun `descartar elimina el envio fallido`() = runTest {
        val vm = vm()
        chat.sendImageHandler = { failure("x") }
        vm.sendImage(ImageUpload("a.jpg", "image/jpeg", byteArrayOf(1)), preview = null)
        advanceUntilIdle()
        vm.discard(vm.state.value.messages.single().key)
        assertTrue(vm.state.value.messages.isEmpty())
    }

    @Test fun `escribiendo aparece y desaparece solo por tiempo`() = runTest {
        val vm = vm()
        socket.flow.emit(SocketEvent.Typing("other", "c1", true))
        assertTrue(vm.state.value.otherTyping)
        main.dispatcher.scheduler.advanceTimeBy(ChatViewModel.TYPING_TIMEOUT_MS + 100)
        assertFalse(vm.state.value.otherTyping)
        // aviso de otra sala o propio: se ignora
        socket.flow.emit(SocketEvent.Typing("other", "otra", true))
        socket.flow.emit(SocketEvent.Typing("me", "c1", true))
        assertFalse(vm.state.value.otherTyping)
    }

    @Test fun `escribiendo se limpia al recibir el mensaje`() = runTest {
        val vm = vm()
        socket.flow.emit(SocketEvent.Typing("other", "c1", true))
        socket.flow.emit(SocketEvent.NewMessage(message("m1", "2026-05-12T10:01:00.000Z")))
        assertFalse(vm.state.value.otherTyping)
    }

    @Test fun `avisa de que escribes con limitacion y lo apaga al enviar`() = runTest {
        val vm = vm()
        vm.onInputChanged("H")
        vm.onInputChanged("Ho")
        vm.onInputChanged("Hol")
        assertEquals(listOf("c1" to true), socket.typingCalls)
        vm.sendText("Hola")
        assertEquals("c1" to false, socket.typingCalls.last())
    }

    @Test fun `reacciones locales se ponen cambian y quitan`() = runTest {
        chat.messages = ApiResult.Success(listOf(message("m1", "2026-05-12T10:01:00.000Z")))
        val vm = vm()
        vm.toggleReaction("m1", "❤️")
        assertEquals("❤️", vm.state.value.reactions["m1"])
        vm.toggleReaction("m1", "🔥")
        assertEquals("🔥", vm.state.value.reactions["m1"])
        vm.toggleReaction("m1", "🔥")
        assertNull(vm.state.value.reactions["m1"])
    }

    @Test fun `error al cargar se muestra y reintentar recupera`() = runTest {
        chat.messages = failure("No se pudo cargar.")
        val vm = vm()
        assertEquals("No se pudo cargar.", vm.state.value.error)
        chat.messages = ApiResult.Success(listOf(message("m1", "2026-05-12T10:01:00.000Z")))
        vm.retryLoad()
        assertNull(vm.state.value.error)
        assertEquals(listOf("m1"), vm.ids())
    }

    @Test fun `al reconectar el socket se recargan los mensajes perdidos`() = runTest {
        chat.messages = ApiResult.Success(listOf(message("m1", "2026-05-12T10:01:00.000Z")))
        val vm = vm()
        socket.setConnected(false)
        chat.messages = ApiResult.Success(listOf(message("m1", "2026-05-12T10:01:00.000Z"), message("m2", "2026-05-12T10:02:00.000Z")))
        socket.setConnected(true)
        advanceUntilIdle()
        assertEquals(listOf("m1", "m2"), vm.ids())
    }

    @Test fun `la cabecera sale de la conversacion correspondiente`() = runTest {
        chat.conversations = ApiResult.Success(
            listOf(
                Conversation("zzz", otherUser = UserSummary("u9", "Otra")),
                Conversation("c1", itemId = "p1", itemTitle = "Vestido", otherUser = UserSummary("u1", "Marta")),
            ),
        )
        val vm = vm()
        assertEquals("Marta", vm.state.value.conversation?.otherUser?.name)
        assertNotNull(vm.state.value.conversation?.itemTitle)
    }

    // ───────── utilidades puras ─────────

    @Test fun `mergeMessages deduplica por id y ordena de forma estable`() {
        val a = message("a", "2026-05-12T10:00:00.000Z")
        val b = message("b", "2026-05-12T10:00:00.000Z")
        val c = message("c", "2026-05-12T09:00:00.000Z")
        val merged = mergeMessages(listOf(a, b), listOf(c, b.copy(content = "editado")))
        assertEquals(listOf("c", "a", "b"), merged.map { it.id })
        assertEquals("editado", merged.last().content)
    }

    @Test fun `enlaces http y https se detectan sin la puntuacion final`() {
        val text = "Mira https://estilovivo.xyoncloud.win/?product=1, y http://a.co/x). Pero no javascript:alert(1) ni www.sin-esquema.com"
        val links = findLinks(text).map { it.url }
        assertEquals(listOf("https://estilovivo.xyoncloud.win/?product=1", "http://a.co/x"), links)
        assertTrue(findLinks("https://").isEmpty())
    }
}
