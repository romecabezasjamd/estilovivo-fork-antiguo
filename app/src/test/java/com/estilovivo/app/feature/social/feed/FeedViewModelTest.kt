package com.estilovivo.app.feature.social.feed

import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.Page
import com.estilovivo.app.core.data.model.LikeResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val looks = FakeLookRepository()
    private val social = FakeSocialRepository()
    private val me = FakeMe(user("me", "Yo"))

    private fun vm() = FeedViewModel(looks, social, me, pageSize = 2)

    private fun <T> TestScope.collect(flow: Flow<T>): MutableList<T> {
        val out = mutableListOf<T>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.toList(out) }
        return out
    }

    private fun page(vararg items: com.estilovivo.app.core.data.model.Look, next: String? = null) =
        ApiResult.Success(Page(items.toList(), next, hasMore = next != null))

    // ───────────── Paginación ─────────────

    @Test fun `la primera carga filtra las publicaciones de venta como la web`() = runTest {
        looks.feedHandler = { page(look("a"), look("v1", mood = "En venta"), look("v2", tags = listOf("venta")), look("b"), next = "c1") }
        val vm = vm()
        val s = vm.state.value
        assertEquals(FeedPhase.Content, s.phase)
        assertEquals(listOf("a", "b"), s.posts.map { it.id })
        assertTrue(s.hasMore)
    }

    @Test fun `loadMore pide la siguiente pagina con el cursor, sin duplicar, y se detiene al agotar`() = runTest {
        looks.feedHandler = { c ->
            when (c) {
                null -> page(look("a"), look("b"), next = "c1")
                "c1" -> page(look("b"), look("c"), next = null) // "b" repetida
                else -> error("no debería pedirse más")
            }
        }
        val vm = vm()
        vm.loadMore()
        assertEquals(listOf("a", "b", "c"), vm.state.value.posts.map { it.id })
        assertFalse(vm.state.value.hasMore)
        assertFalse(vm.state.value.loadingMore)
        assertEquals(listOf<String?>(null, "c1"), looks.feedCursors)
        vm.loadMore() // ya no hay más: no llama al servidor
        assertEquals(2, looks.feedCursors.size)
    }

    @Test fun `loadMore sigue buscando si una pagina entera son ventas`() = runTest {
        looks.feedHandler = { c ->
            when (c) {
                null -> page(look("a"), next = "c1")
                "c1" -> page(look("v1", mood = "venta"), look("v2", mood = "venta"), next = "c2")
                "c2" -> page(look("z"), next = null)
                else -> error("unexpected")
            }
        }
        val vm = vm()
        vm.loadMore()
        assertEquals(listOf("a", "z"), vm.state.value.posts.map { it.id })
        assertEquals(listOf<String?>(null, "c1", "c2"), looks.feedCursors)
    }

    @Test fun `un fallo al cargar mas conserva lo mostrado y permite reintentar`() = runTest {
        var fail = true
        looks.feedHandler = { c ->
            if (c == null) page(look("a"), next = "c1") else if (fail) failure("Sin conexión") else page(look("b"))
        }
        val vm = vm()
        vm.loadMore()
        assertEquals("Sin conexión", vm.state.value.loadMoreError)
        assertEquals(listOf("a"), vm.state.value.posts.map { it.id })
        assertTrue(vm.state.value.hasMore)
        fail = false
        vm.retryLoadMore()
        assertNull(vm.state.value.loadMoreError)
        assertEquals(listOf("a", "b"), vm.state.value.posts.map { it.id })
    }

    @Test fun `error inicial muestra el mensaje y reintentar recupera`() = runTest {
        var fail = true
        looks.feedHandler = { if (fail) failure("Sin conexión con el servidor") else page(look("a")) }
        val vm = vm()
        assertEquals(FeedPhase.Error, vm.state.value.phase)
        assertEquals("Sin conexión con el servidor", vm.state.value.errorMessage)
        fail = false
        vm.load()
        assertEquals(FeedPhase.Content, vm.state.value.phase)
        assertEquals(1, vm.state.value.posts.size)
    }

    @Test fun `refresh que falla mantiene las publicaciones y avisa`() = runTest {
        var fail = false
        looks.feedHandler = { if (fail) failure("Sin conexión") else page(look("a")) }
        val vm = vm()
        val events = collect(vm.events)
        fail = true
        vm.refresh()
        assertEquals(listOf("a"), vm.state.value.posts.map { it.id })
        assertFalse(vm.state.value.refreshing)
        assertTrue(events.any { it is FeedEvent.Toast && it.isError })
    }

    // ───────────── Me gusta ─────────────

    @Test fun `el me gusta es optimista, se concilia con el servidor y el doble toque se ignora`() = runTest {
        looks.feedHandler = { page(look("a", likes = 4)) }
        val gate = CompletableDeferred<ApiResult<LikeResult>>()
        social.likeHandler = { gate.await() }
        val vm = vm()
        vm.toggleLike("a")
        // Antes de que responda el servidor ya se ve el cambio
        assertTrue(vm.state.value.posts[0].isLiked)
        assertEquals(5, vm.state.value.posts[0].likesCount)
        vm.toggleLike("a") // doble toque mientras hay una petición en vuelo
        assertEquals(1, social.likeCalls)
        assertTrue(vm.state.value.posts[0].isLiked)
        gate.complete(ApiResult.Success(LikeResult(liked = true, likesCount = 7)))
        assertEquals(7, vm.state.value.posts[0].likesCount) // manda el contador real
        // Ya liberado, se puede quitar
        social.likeHandler = { ApiResult.Success(LikeResult(false, 6)) }
        vm.toggleLike("a")
        assertFalse(vm.state.value.posts[0].isLiked)
        assertEquals(6, vm.state.value.posts[0].likesCount)
    }

    @Test fun `si falla el me gusta se revierte y se avisa`() = runTest {
        looks.feedHandler = { page(look("a", likes = 3, liked = false)) }
        social.likeHandler = { failure("Sin conexión") }
        val vm = vm()
        val events = collect(vm.events)
        vm.toggleLike("a")
        val p = vm.state.value.posts[0]
        assertFalse(p.isLiked)
        assertEquals(3, p.likesCount)
        assertTrue(events.any { it is FeedEvent.Toast && it.isError && it.text == "Sin conexión" })
        // y se puede volver a intentar
        social.likeHandler = { ApiResult.Success(LikeResult(true, 4)) }
        vm.toggleLike("a")
        assertTrue(vm.state.value.posts[0].isLiked)
    }

    @Test fun `quitar el me gusta nunca deja el contador en negativo`() = runTest {
        looks.feedHandler = { page(look("a", likes = 0, liked = true)) }
        val gate = CompletableDeferred<ApiResult<LikeResult>>()
        social.likeHandler = { gate.await() }
        val vm = vm()
        vm.toggleLike("a")
        assertEquals(0, vm.state.value.posts[0].likesCount)
        gate.complete(ApiResult.Success(LikeResult(false, 0)))
    }

    @Test fun `el doble toque sobre la foto solo da me gusta, nunca lo quita`() = runTest {
        looks.feedHandler = { page(look("a", liked = true, likes = 1)) }
        val vm = vm()
        vm.likeOnly("a")
        assertEquals(0, social.likeCalls)
        assertTrue(vm.state.value.posts[0].isLiked)
    }

    @Test fun `favorito optimista con reversion`() = runTest {
        looks.feedHandler = { page(look("a")) }
        social.favoriteHandler = { failure("Error") }
        val vm = vm()
        vm.toggleFavorite("a")
        assertFalse(vm.state.value.posts[0].isFavorited)
        social.favoriteHandler = { ApiResult.Success(true) }
        vm.toggleFavorite("a")
        assertTrue(vm.state.value.posts[0].isFavorited)
    }

    // ───────────── Comentarios ─────────────

    @Test fun `responder anida bajo el comentario raiz y el contador del feed se sincroniza`() = runTest {
        looks.feedHandler = { page(look("a", userId = "u-other", comments = 1)) }
        social.commentsHandler = { ApiResult.Success(listOf(comment("r1", "u-x", replies = listOf(comment("p1", "u-y", "r1"))))) }
        val vm = vm()
        val events = collect(vm.events)
        vm.openComments("a")
        assertEquals(2, vm.state.value.posts[0].commentsCount) // raíz + respuesta
        vm.setReplyTo(ReplyTarget("r1", "User u-x"))
        vm.sendComment("  ¡Genial!  ")
        val c = vm.comments.value!!
        assertEquals(2, c.comments[0].replies.size)
        assertNull(c.replyTo)
        assertEquals(3, vm.state.value.posts[0].commentsCount)
        assertTrue(events.any { it is FeedEvent.CommentSent })
    }

    @Test fun `un comentario vacio o demasiado largo no se envia`() = runTest {
        looks.feedHandler = { page(look("a")) }
        var sent = 0
        social.addCommentHandler = { _, _, _ -> sent++; ApiResult.Success(comment("n")) }
        val vm = vm()
        val events = collect(vm.events)
        vm.openComments("a")
        vm.sendComment("   ")
        vm.sendComment("x".repeat(COMMENT_MAX + 1))
        assertEquals(0, sent)
        assertEquals(2, events.count { it is FeedEvent.Toast && it.isError })
    }

    @Test fun `solo se pueden borrar comentarios propios o los de mis publicaciones`() = runTest {
        looks.feedHandler = { page(look("mine", userId = "me"), look("other", userId = "u-other")) }
        val vm = vm()
        vm.openComments("other")
        assertTrue(vm.canDelete(comment("1", userId = "me")))
        assertFalse(vm.canDelete(comment("2", userId = "u-x")))
        vm.closeComments()
        vm.openComments("mine") // soy la dueña del look: puedo moderar
        assertTrue(vm.canDelete(comment("3", userId = "u-x")))
    }

    @Test fun `borrar un comentario raiz actualiza la lista y el contador`() = runTest {
        looks.feedHandler = { page(look("a", userId = "me", comments = 3)) }
        social.commentsHandler = { ApiResult.Success(listOf(comment("r1", "u-x", replies = listOf(comment("p1", "u-y", "r1"))), comment("r2", "u-z"))) }
        val vm = vm()
        vm.openComments("a")
        assertEquals(3, vm.state.value.posts[0].commentsCount)
        vm.deleteComment(vm.comments.value!!.comments[0])
        assertEquals(listOf("r2"), vm.comments.value!!.comments.map { it.id })
        assertEquals(1, vm.state.value.posts[0].commentsCount)
    }

    // ───────────── Publicar / borrar ─────────────

    @Test fun `publicar anade arriba, completa la autora y lanza confeti`() = runTest {
        looks.feedHandler = { page(look("a")) }
        looks.createHandler = { ApiResult.Success(look("new", userId = null).copy(userName = null)) }
        val vm = vm()
        val events = collect(vm.events)
        vm.publish(upload(), "  Mi look de hoy  ")
        assertEquals("Mi look de hoy", looks.lastCreatedName)
        assertEquals(listOf("new", "a"), vm.state.value.posts.map { it.id })
        assertEquals("me", vm.state.value.posts[0].userId)
        assertEquals("Yo", vm.state.value.posts[0].userName)
        assertFalse(vm.state.value.publishing)
        assertTrue(events.any { it is FeedEvent.Confetti })
        assertTrue(events.any { it is FeedEvent.Published })
        assertEquals(1, me.refreshCount)
    }

    @Test fun `publicar sin descripcion usa un titulo por defecto y limita a 100 caracteres`() = runTest {
        looks.feedHandler = { page() }
        looks.createHandler = { ApiResult.Success(look("n")) }
        val vm = vm()
        val events = collect(vm.events)
        vm.publish(upload(), "x".repeat(101))
        assertNull(looks.lastCreatedName)
        assertTrue(events.any { it is FeedEvent.Toast && it.isError })
        vm.publish(upload(), "")
        assertEquals("Publicación", looks.lastCreatedName)
    }

    @Test fun `si falla publicar no se anade nada y se puede reintentar`() = runTest {
        looks.feedHandler = { page(look("a")) }
        looks.createHandler = { failure("Error al publicar") }
        val vm = vm()
        vm.publish(upload(), "hola")
        assertEquals(listOf("a"), vm.state.value.posts.map { it.id })
        assertFalse(vm.state.value.publishing)
    }

    @Test fun `solo se puede borrar una publicacion propia`() = runTest {
        looks.feedHandler = { page(look("mine", userId = "me"), look("other", userId = "u-other")) }
        var deleted = mutableListOf<String>()
        looks.deleteHandler = { deleted += it; ApiResult.Success(Unit) }
        val vm = vm()
        vm.delete("other")
        assertTrue(deleted.isEmpty())
        vm.delete("mine")
        assertEquals(listOf("mine"), deleted)
        assertEquals(listOf("other"), vm.state.value.posts.map { it.id })
    }
}
