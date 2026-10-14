package com.estilovivo.app.feature.social.shop

import com.estilovivo.app.core.data.ApiError
import com.estilovivo.app.core.data.ApiErrorKind
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.model.Conversation
import com.estilovivo.app.core.data.model.Favorite
import com.estilovivo.app.feature.social.FakeChatRepository
import com.estilovivo.app.feature.social.FakeGarmentRepository
import com.estilovivo.app.feature.social.FakeImageFetcher
import com.estilovivo.app.feature.social.FakeSocialRepository
import com.estilovivo.app.feature.social.MainDispatcherRule
import com.estilovivo.app.feature.social.chat.ChatLauncher
import com.estilovivo.app.feature.social.failure
import com.estilovivo.app.feature.social.garment
import com.estilovivo.app.feature.social.shopItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProductAndFavoritesTest {
    @get:Rule val main = MainDispatcherRule()

    private val garments = FakeGarmentRepository()
    private val social = FakeSocialRepository()
    private val chat = FakeChatRepository()
    private val launcher = ChatLauncher(chat)
    private val catalog = ShopCatalog(garments, social)

    // ───────── detalle ─────────

    @Test fun `detalle encuentra el articulo en el catalogo`() = runTest {
        garments.shopHandler = { _, _ -> ApiResult.Success(listOf(shopItem("p1", "Vestido"), shopItem("p2"))) }
        val vm = ProductDetailViewModel("p2", catalog, launcher) { "me" }
        assertEquals("p2", vm.state.value.item?.id)
        assertFalse(vm.state.value.loading)
    }

    @Test fun `detalle usa los favoritos si el articulo ya no sale en el catalogo`() = runTest {
        social.favorites = ApiResult.Success(listOf(Favorite("f", null, garment("old"), "")))
        val vm = ProductDetailViewModel("old", catalog, launcher) { "me" }
        assertEquals("old", vm.state.value.item?.id)
        assertTrue("old" in vm.state.value.let { catalog.favoriteIds.value })
    }

    @Test fun `detalle de articulo inexistente muestra mensaje util`() = runTest {
        val vm = ProductDetailViewModel("nope", catalog, launcher) { "me" }
        assertNull(vm.state.value.item)
        assertEquals("Este artículo ya no está disponible.", vm.state.value.error)
    }

    @Test fun `contactar crea la conversacion con contexto del articulo y navega`() = runTest {
        garments.shopHandler = { _, _ -> ApiResult.Success(listOf(shopItem("p1", "Vestido «rojo»", userId = "seller"))) }
        chat.createResult = ApiResult.Success(Conversation(id = "conv-77"))
        val vm = ProductDetailViewModel("p1", catalog, launcher) { "me" }
        val events = mutableListOf<ProductEvent>()
        val job = launch { vm.events.toList(events) }
        vm.contactSeller()
        advanceUntilIdle()
        assertEquals(listOf<ProductEvent>(ProductEvent.OpenChat("conv-77")), events)
        val call = chat.created.single()
        assertEquals("seller", call[0])
        assertEquals("p1", call[1])
        assertEquals("Vestido «rojo»", call[2])
        assertEquals("https://x/p1.jpg", call[3])
        assertEquals("Hola, me interesa «Vestido «rojo»». ¿Sigue disponible?", call[4])
        job.cancel()
    }

    @Test fun `contactar dos veces seguidas solo crea una conversacion`() = runTest {
        garments.shopHandler = { _, _ -> ApiResult.Success(listOf(shopItem("p1"))) }
        val vm = ProductDetailViewModel("p1", catalog, launcher) { "me" }
        val gate = CompletableDeferred<Unit>()
        val repo = object : com.estilovivo.app.core.data.repo.ChatRepository by chat {
            override suspend fun createConversation(
                targetUserId: String, itemId: String?, itemTitle: String?, itemImage: String?, initialMessage: String?,
            ): ApiResult<Conversation> {
                gate.await()
                return chat.createConversation(targetUserId, itemId, itemTitle, itemImage, initialMessage)
            }
        }
        val slowVm = ProductDetailViewModel("p1", catalog, ChatLauncher(repo)) { "me" }
        slowVm.contactSeller()
        assertTrue(slowVm.state.value.contacting)
        slowVm.contactSeller()
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, chat.created.size)
        assertFalse(slowVm.state.value.contacting)
        assertTrue(vm.state.value.item != null)
    }

    @Test fun `contactar con error avisa y no navega`() = runTest {
        garments.shopHandler = { _, _ -> ApiResult.Success(listOf(shopItem("p1"))) }
        chat.createResult = failure("No se pudo crear la conversación.")
        val vm = ProductDetailViewModel("p1", catalog, launcher) { "me" }
        val events = mutableListOf<ProductEvent>()
        val job = launch { vm.events.toList(events) }
        vm.contactSeller()
        advanceUntilIdle()
        assertEquals(listOf<ProductEvent>(ProductEvent.Error("No se pudo crear la conversación.")), events)
        assertFalse(vm.state.value.contacting)
        job.cancel()
    }

    @Test fun `en un articulo propio no se puede contactar`() = runTest {
        garments.shopHandler = { _, _ -> ApiResult.Success(listOf(shopItem("p1", userId = "me"))) }
        val vm = ProductDetailViewModel("p1", catalog, launcher) { "me" }
        assertTrue(vm.state.value.isOwn)
        vm.contactSeller()
        advanceUntilIdle()
        assertTrue(chat.created.isEmpty())
    }

    @Test fun `favorito del detalle con rollback`() = runTest {
        garments.shopHandler = { _, _ -> ApiResult.Success(listOf(shopItem("p1"))) }
        val vm = ProductDetailViewModel("p1", catalog, launcher) { "me" }
        garments.toggleHandler = { failure("Error") }
        vm.toggleFavorite()
        advanceUntilIdle()
        assertFalse(vm.state.value.liked)
        garments.toggleHandler = { ApiResult.Success(true) }
        vm.toggleFavorite()
        assertTrue(vm.state.value.liked)
    }

    @Test fun `ChatLauncher recorta titulo y mensaje al limite del servidor y devuelve null si falla`() = runTest {
        chat.createResult = ApiResult.Success(Conversation(id = "x"))
        assertEquals("x", launcher.conversationWith("u", "i", "t".repeat(300), null, "m".repeat(2000)))
        assertEquals(200, chat.created.single()[2]!!.length)
        assertEquals(1000, chat.created.single()[4]!!.length)
        chat.createResult = ApiResult.Failure(ApiError("fallo", ApiErrorKind.Server))
        assertNull(launcher.conversationWith("u"))
    }

    // ───────── favoritos ─────────

    private fun favVm(
        fetcher: FakeImageFetcher = FakeImageFetcher(),
    ) = FavoritesViewModel(catalog, social, garments, launcher, fetcher) { "me" }

    @Test fun `favoritos lista articulos y looks`() = runTest {
        social.favorites = ApiResult.Success(
            listOf(
                Favorite("f1", null, garment("g1"), ""),
                Favorite("f2", com.estilovivo.app.core.data.model.Look("l1", "Look noche", emptyList(), emptyList(), emptyList()), null, ""),
                Favorite("f3", null, null, ""), // vacío: se ignora
            ),
        )
        val vm = favVm()
        assertEquals(listOf("f1", "f2"), vm.state.value.entries.map { it.id })
        assertTrue("g1" in catalog.favoriteIds.value)
    }

    @Test fun `quitar un favorito es optimista y con fallo vuelve a su sitio`() = runTest {
        social.favorites = ApiResult.Success(listOf(Favorite("f1", null, garment("g1"), ""), Favorite("f2", null, garment("g2"), "")))
        val vm = favVm()
        garments.toggleHandler = { failure("No se pudo quitar.") }
        val events = mutableListOf<FavoritesEvent>()
        val job = launch { vm.events.toList(events) }
        vm.remove("f1")
        advanceUntilIdle()
        assertEquals(listOf("f1", "f2"), vm.state.value.entries.map { it.id })
        assertEquals(listOf<FavoritesEvent>(FavoritesEvent.Error("No se pudo quitar.")), events)
        assertTrue(vm.state.value.busy.isEmpty())
        // y funcionando
        garments.toggleHandler = { ApiResult.Success(false) }
        vm.remove("f1")
        advanceUntilIdle()
        assertEquals(listOf("f2"), vm.state.value.entries.map { it.id })
        assertFalse("g1" in catalog.favoriteIds.value)
        job.cancel()
    }

    @Test fun `quitar un look usa el endpoint de looks`() = runTest {
        social.favorites = ApiResult.Success(
            listOf(Favorite("f2", com.estilovivo.app.core.data.model.Look("l1", "Look", emptyList(), emptyList(), emptyList()), null, "")),
        )
        val vm = favVm()
        vm.remove("f2")
        advanceUntilIdle()
        assertEquals(listOf("l1"), social.toggledLooks)
        assertTrue(vm.state.value.entries.isEmpty())
    }

    @Test fun `mover al armario copia la prenda con su foto y la quita de favoritos`() = runTest {
        social.favorites = ApiResult.Success(listOf(Favorite("f1", null, garment("g1"), "")))
        val fetcher = FakeImageFetcher()
        val vm = favVm(fetcher)
        garments.toggleHandler = { ApiResult.Success(false) }
        vm.moveToWardrobe("f1")
        advanceUntilIdle()
        val (draft, image) = garments.added.single()
        assertEquals(listOf("https://x/g1.jpg"), fetcher.urls)
        assertTrue("la foto viaja con la prenda", image != null)
        assertEquals("outerwear", draft.type)
        assertEquals("Chaqueta g1", draft.name)
        assertEquals(false, draft.forSale)
        assertTrue(vm.state.value.entries.isEmpty())
    }

    @Test fun `mover al armario sin poder bajar la foto avisa y no crea la prenda`() = runTest {
        social.favorites = ApiResult.Success(listOf(Favorite("f1", null, garment("g1"), "")))
        val vm = favVm(FakeImageFetcher(result = null))
        val events = mutableListOf<FavoritesEvent>()
        val job = launch { vm.events.toList(events) }
        vm.moveToWardrobe("f1")
        advanceUntilIdle()
        assertTrue(garments.added.isEmpty())
        assertEquals(1, vm.state.value.entries.size)
        assertTrue(events.single() is FavoritesEvent.Error)
        assertTrue(vm.state.value.busy.isEmpty())
        job.cancel()
    }

    @Test fun `contactar desde favoritos abre el chat con el articulo`() = runTest {
        social.favorites = ApiResult.Success(listOf(Favorite("f1", null, garment("g1", userId = "seller"), "")))
        chat.createResult = ApiResult.Success(Conversation(id = "conv-1"))
        val vm = favVm()
        val events = mutableListOf<FavoritesEvent>()
        val job = launch { vm.events.toList(events) }
        vm.contact("f1")
        advanceUntilIdle()
        assertEquals(listOf<FavoritesEvent>(FavoritesEvent.OpenChat("conv-1")), events)
        assertEquals("g1", chat.created.single()[1])
        job.cancel()
    }

    @Test fun `etiqueta de compatibilidad con el armario`() {
        val g = garment("x")
        assertEquals("Sin combinaciones claras", compatibilityLabel(g, emptyList()))
        val wardrobe = List(3) { garment("w$it") }
        assertEquals("Combina con 6+ prendas", compatibilityLabel(g, wardrobe))
    }
}
