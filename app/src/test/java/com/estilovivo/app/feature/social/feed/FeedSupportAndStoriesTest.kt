package com.estilovivo.app.feature.social.feed

import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.model.StoryReactionResult
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
import java.time.Instant

class FeedSupportTest {
    private val now = Instant.parse("2026-10-13T12:00:00Z")

    @Test fun `tiempo relativo`() {
        assertEquals("ahora", relativeTime("2026-10-13T11:59:50Z", now))
        assertEquals("hace 5 min", relativeTime("2026-10-13T11:55:00Z", now))
        assertEquals("hace 3 h", relativeTime("2026-10-13T09:00:00Z", now))
        assertEquals("hace 2 d", relativeTime("2026-10-11T12:00:00.000Z", now))
        assertTrue(relativeTime("2026-01-05T12:00:00Z", now).contains("2026"))
        assertEquals("", relativeTime("no-es-fecha", now))
        assertEquals("", relativeTime(null, now))
    }

    @Test fun `las publicaciones de venta se filtran por prenda, humor o etiqueta`() {
        assertFalse(isSaleLook(look("a")))
        assertTrue(isSaleLook(look("a", mood = "Ropa en VENTA")))
        assertTrue(isSaleLook(look("a", tags = listOf("outfit", "venta"))))
        val sale = look("a").copy(garments = listOf(com.estilovivo.app.core.data.model.Garment("g", "u", "n", "top", "red", "all", 0, forSale = true)))
        assertTrue(isSaleLook(sale))
    }

    @Test fun `solo se abren enlaces http y https`() {
        assertTrue(isSafeWebUrl("https://vogue.es/articulo"))
        assertTrue(isSafeWebUrl(" http://example.com/x "))
        assertFalse(isSafeWebUrl("javascript:alert(1)"))
        assertFalse(isSafeWebUrl("file:///etc/passwd"))
        assertFalse(isSafeWebUrl("intent://scan/#Intent;scheme=zxing;end"))
        assertFalse(isSafeWebUrl("https://"))
        assertFalse(isSafeWebUrl(""))
        assertFalse(isSafeWebUrl(null))
    }

    @Test fun `progreso de XP replica la formula del servidor`() {
        // nivel 1: 0..100 XP ; nivel 2: 100..300
        assertEquals(XpProgress(0, 100, 0, 100), xpProgress(0, 1))
        assertEquals(XpProgress(50, 100, 50, 50), xpProgress(50, 1))
        assertEquals(XpProgress(50, 200, 25, 150), xpProgress(150, 2))
        assertEquals(XpProgress(100, 100, 100, 0), xpProgress(9999, 1)) // nunca se pasa
    }

    @Test fun `enlace de compartir y texto`() {
        assertEquals("https://estilovivo.xyoncloud.win/look/abc", lookShareUrl("abc"))
        assertTrue(lookShareText(look("abc")).endsWith("/look/abc"))
    }

    @Test fun `recuento de comentarios incluye respuestas`() {
        assertEquals(3, countAllComments(listOf(comment("1", replies = listOf(comment("2"))), comment("3"))))
        assertEquals(0, countAllComments(emptyList()))
    }

    @Test fun `plazo del reto`() {
        assertEquals("Termina hoy", challengeTimeLeft("2026-10-13T20:00:00Z", now))
        assertEquals("Termina en 3 días", challengeTimeLeft("2026-10-15T12:30:00Z", now))
        assertEquals("Reto cerrado", challengeTimeLeft("2026-10-12T00:00:00Z", now))
        assertNull(challengeTimeLeft("", now))
    }

    @Test fun `consejos por reto con alternativa generica`() {
        assertEquals(4, challengeTips("Monocromo").size)
        assertEquals(3, challengeTips("Desconocido").size)
        assertEquals(3, challengeTips(null).size)
    }

    // ───────────── Historias ─────────────

    @Test fun `las historias se agrupan por persona con la propia primero y las no vistas despues`() {
        val s = listOf(
            story("1", "ana", created = "2026-10-13T08:00:00Z"),
            story("2", "bea", created = "2026-10-13T10:00:00Z"),
            story("3", "me", own = true, created = "2026-10-13T07:00:00Z"),
            story("4", "ana", created = "2026-10-13T09:00:00Z"),
        )
        val groups = groupStories(s, viewed = setOf("2"), now = now)
        assertEquals(listOf("me", "ana", "bea"), groups.map { it.userId }) // bea ya vista => al final
        assertEquals(listOf("1", "4"), groups[1].stories.map { it.id }) // la más antigua primero
        assertTrue(groups[2].allViewed)
        assertTrue(groups[0].allViewed) // las propias no cuentan como "nuevas"
    }

    @Test fun `las historias caducadas no se muestran`() {
        val groups = groupStories(listOf(story("1", expires = "2026-10-13T11:00:00Z"), story("2", "x")), emptySet(), now)
        assertEquals(listOf("x"), groups.map { it.userId })
    }

    @Test fun `entrada del visor es la primera sin ver`() {
        val g = groupStories(listOf(story("1", "ana"), story("2", "ana", created = "2026-10-13T11:00:00Z")), setOf("1"), now)[0]
        assertEquals("2", g.entryStoryId(setOf("1")))
        assertEquals("1", g.entryStoryId(setOf("1", "2")))
    }

    @Test fun `navegacion del visor entre historias y personas`() {
        val groups = groupStories(
            listOf(
                story("a1", "ana", created = "2026-10-13T08:00:00Z"), story("a2", "ana", created = "2026-10-13T09:00:00Z"),
                story("b1", "bea", created = "2026-10-13T07:00:00Z"),
            ),
            emptySet(), now,
        ) // ana (más reciente) primero, luego bea
        val a1 = locateStory(groups, "a1")!!
        val a2 = nextStory(groups, a1)!!
        assertEquals("a2", a2.storyIn(groups)!!.id)
        val b1 = nextStory(groups, a2)!!
        assertEquals("b1", b1.storyIn(groups)!!.id)
        assertNull(nextStory(groups, b1)) // fin => cerrar
        assertEquals("a2", prevStory(groups, b1).storyIn(groups)!!.id) // vuelve a la última de la anterior
        assertEquals(a1, prevStory(groups, a1)) // en la primera se queda
        assertNull(locateStory(groups, "zzz"))
    }

    @Test fun `el fondo de las historias de texto es estable por id`() {
        assertEquals(storyGradient("abc"), storyGradient("abc"))
        assertEquals(2, storyGradient("abc").size)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class StoryViewModelsTest {
    @get:Rule val main = MainDispatcherRule()

    private val stories = FakeStoryRepository()
    private val viewed = InMemoryViewedStories()

    private fun <T> TestScope.collect(flow: Flow<T>): MutableList<T> {
        val out = mutableListOf<T>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.toList(out) }
        return out
    }

    @Test fun `la visualizacion se cuenta una sola vez y las propias no cuentan`() = runTest {
        stories.stories = listOf(story("1", "ana"), story("2", "me", own = true))
        val vm = StoryViewerViewModel(stories, viewed)
        val s1 = vm.state.value.groups.flatMap { it.stories }.first { it.id == "1" }
        val s2 = vm.state.value.groups.flatMap { it.stories }.first { it.id == "2" }
        vm.onShown(s1); vm.onShown(s1); vm.onShown(s2)
        assertEquals(listOf("1"), stories.viewed)
        assertTrue("1" in viewed.ids.value)
        // Si ya estaba vista de una sesión anterior tampoco se vuelve a contar
        val vm2 = StoryViewerViewModel(stories, viewed)
        vm2.onShown(s1)
        assertEquals(listOf("1"), stories.viewed)
    }

    @Test fun `reaccionar envia el emoji, bloquea el doble toque y no se puede en la propia`() = runTest {
        stories.stories = listOf(story("1", "ana"), story("2", "me", own = true))
        val gate = CompletableDeferred<ApiResult<StoryReactionResult>>()
        var calls = 0
        stories.reactHandler = { _, _ -> calls++; gate.await() }
        val vm = StoryViewerViewModel(stories, viewed)
        val events = collect(vm.events)
        val others = vm.state.value.groups.flatMap { it.stories }
        vm.react(others.first { it.isOwn }, "❤️")
        assertEquals(0, calls)
        vm.react(others.first { !it.isOwn }, "🔥")
        vm.react(others.first { !it.isOwn }, "🔥")
        assertEquals(1, calls)
        gate.complete(ApiResult.Success(StoryReactionResult(null, "conv")))
        assertTrue(events.any { it is ViewerEvent.Reacted && it.emoji == "🔥" })
        assertFalse(vm.state.value.reacting)
    }

    @Test fun `una reaccion fallida avisa`() = runTest {
        stories.stories = listOf(story("1", "ana"))
        stories.reactHandler = { _, _ -> failure("Sin conexión") }
        val vm = StoryViewerViewModel(stories, viewed)
        val events = collect(vm.events)
        vm.react(vm.state.value.groups[0].stories[0], "😍")
        assertTrue(events.any { it is ViewerEvent.Toast && it.isError })
    }

    @Test fun `borrar una historia propia indica la siguiente y solo la quita al confirmarlo la UI`() = runTest {
        stories.stories = listOf(
            story("m1", "me", own = true, created = "2026-10-13T08:00:00Z"),
            story("m2", "me", own = true, created = "2026-10-13T09:00:00Z"),
            story("a1", "ana"),
        )
        val vm = StoryViewerViewModel(stories, viewed)
        val events = collect(vm.events)
        val m1 = vm.state.value.groups[0].stories[0]
        vm.delete(m1)
        val deleted = events.filterIsInstance<ViewerEvent.Deleted>().single()
        assertEquals("m1", deleted.storyId)
        assertEquals("m2", deleted.nextStoryId)
        vm.removeStory("m1")
        assertEquals(listOf("m2"), vm.state.value.groups[0].stories.map { it.id })
    }

    @Test fun `no se puede borrar la historia de otra persona`() = runTest {
        stories.stories = listOf(story("a1", "ana"))
        var deleted = false
        stories.deleteHandler = { deleted = true; ApiResult.Success(Unit) }
        val vm = StoryViewerViewModel(stories, viewed)
        vm.delete(vm.state.value.groups[0].stories[0])
        assertFalse(deleted)
    }

    @Test fun `crear historia valida texto y foto antes de enviar`() = runTest {
        var created = 0
        stories.createHandler = { created++; ApiResult.Success(story("n", "me", own = true)) }
        val garments: com.estilovivo.app.core.data.repo.GarmentRepository = NoGarments
        val vm = StoriesViewModel(stories, garments, viewed, FakeMe())
        val events = collect(vm.events)
        vm.create("text", "   ", null)
        vm.create("image", "algo", null, null)
        vm.create("text", "x".repeat(STORY_TEXT_MAX + 1), null)
        assertEquals(0, created)
        assertEquals(3, events.count { it is FeedEvent.Toast && it.isError })
        vm.create("text", "Hola mundo", null)
        assertEquals(1, created)
        assertEquals("n", vm.state.value.stories.first().id)
        assertTrue(events.any { it is FeedEvent.Confetti })
    }
}

/** GarmentRepository inerte para los tests de historias (nunca se usa su contenido). */
private object NoGarments : com.estilovivo.app.core.data.repo.GarmentRepository by throwingGarmentRepository()

private fun throwingGarmentRepository(): com.estilovivo.app.core.data.repo.GarmentRepository =
    java.lang.reflect.Proxy.newProxyInstance(
        com.estilovivo.app.core.data.repo.GarmentRepository::class.java.classLoader,
        arrayOf(com.estilovivo.app.core.data.repo.GarmentRepository::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "getGarments" -> kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.estilovivo.app.core.data.model.Garment>())
            else -> error("unused ${method.name}")
        }
    } as com.estilovivo.app.core.data.repo.GarmentRepository
