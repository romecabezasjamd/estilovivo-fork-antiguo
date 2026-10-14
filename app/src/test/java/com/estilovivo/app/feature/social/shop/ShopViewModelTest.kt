package com.estilovivo.app.feature.social.shop

import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.model.Favorite
import com.estilovivo.app.core.data.model.User
import com.estilovivo.app.feature.social.FakeGarmentRepository
import com.estilovivo.app.feature.social.FakeSocialRepository
import com.estilovivo.app.feature.social.MainDispatcherRule
import com.estilovivo.app.feature.social.failure
import com.estilovivo.app.feature.social.garment
import com.estilovivo.app.feature.social.shopItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
class ShopViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val garments = FakeGarmentRepository()
    private val social = FakeSocialRepository()
    private val me = MutableStateFlow<User?>(User(id = "me", name = "Ana López"))

    private val catalogItems = listOf(
        shopItem("a", "Vestido rojo", price = 45.0, size = "S", condition = "new"),
        shopItem("b", "Jersey azul", price = 20.0, size = "M", condition = "good"),
        shopItem("c", "Abrigo negro", price = 120.0, size = "L", condition = "worn"),
        shopItem("free", "Regalo sin precio", price = 0.0),
    )

    private fun vm(): ShopViewModel {
        garments.shopHandler = { _, _ -> ApiResult.Success(catalogItems) }
        return ShopViewModel(ShopCatalog(garments, social), me)
    }

    @Test fun `carga inicial muestra solo artículos con precio y saluda por el nombre`() = runTest {
        val vm = vm()
        val s = vm.state.value
        assertEquals(listOf("a", "b", "c"), s.visible.map { it.id })
        assertFalse(s.loading)
        assertEquals("Ana", s.greetingName)
    }

    @Test fun `la búsqueda espera al debounce y solo pide lo último tecleado`() = runTest {
        val vm = vm()
        garments.shopCalls.clear()
        vm.onQueryChange("ves")
        vm.onQueryChange("vest")
        vm.onQueryChange("vestido")
        main.dispatcher.scheduler.advanceTimeBy(300)
        assertTrue("no debe pedir antes del debounce", garments.shopCalls.isEmpty())
        main.dispatcher.scheduler.advanceTimeBy(100)
        assertEquals(listOf<Pair<String?, String?>>("vestido" to null), garments.shopCalls)
    }

    @Test fun `cambiar de categoría pide al servidor esa categoría`() = runTest {
        val vm = vm()
        garments.shopCalls.clear()
        vm.onCategory("dress")
        assertEquals(listOf<Pair<String?, String?>>(null to "dress"), garments.shopCalls)
        vm.onCategory("all")
        assertEquals(null to null, garments.shopCalls.last())
    }

    @Test fun `una respuesta tardía de una búsqueda anterior no pisa la actual`() = runTest {
        val vm = vm()
        val slow = CompletableDeferred<ApiResult<List<com.estilovivo.app.core.data.model.ShopItem>>>()
        garments.shopHandler = { _, cat -> if (cat == "top") slow.await() else ApiResult.Success(listOf(shopItem("z", "Zapato"))) }
        vm.onCategory("top")      // queda pendiente
        vm.onCategory("shoes")    // responde antes
        assertEquals(listOf("z"), vm.state.value.visible.map { it.id })
        slow.complete(ApiResult.Success(listOf(shopItem("old", "Antiguo"))))
        advanceUntilIdle()
        assertEquals(listOf("z"), vm.state.value.visible.map { it.id })
    }

    @Test fun `filtros de precio estado y talla`() = runTest {
        val vm = vm()
        vm.onFilters(ShopFilters(minPrice = 30.0))
        assertEquals(listOf("a", "c"), vm.state.value.visible.map { it.id })
        vm.onFilters(ShopFilters(minPrice = 30.0, maxPrice = 100.0))
        assertEquals(listOf("a"), vm.state.value.visible.map { it.id })
        vm.onFilters(ShopFilters(condition = "good"))
        assertEquals(listOf("b"), vm.state.value.visible.map { it.id })
        vm.onFilters(ShopFilters(size = "l"))
        assertEquals(listOf("c"), vm.state.value.visible.map { it.id })
        assertEquals(1, vm.state.value.filters.activeCount)
        vm.onFilters(ShopFilters())
        assertEquals(3, vm.state.value.visible.size)
        assertEquals(listOf("S", "M", "L"), vm.state.value.availableSizes)
    }

    @Test fun `filtro favoritos solo deja los guardados`() = runTest {
        social.favorites = ApiResult.Success(listOf(Favorite("f1", null, garment("b"), "")))
        val vm = vm()
        vm.onFavoritesOnly(true)
        assertEquals(listOf("b"), vm.state.value.visible.map { it.id })
        vm.onFavoritesOnly(false)
        assertEquals(3, vm.state.value.visible.size)
    }

    @Test fun `favorito es optimista y se confirma con la respuesta del servidor`() = runTest {
        val vm = vm()
        val gate = CompletableDeferred<ApiResult<Boolean>>()
        garments.toggleHandler = { gate.await() }
        vm.onToggleFavorite("a")
        assertTrue("optimista antes de responder", "a" in vm.state.value.favoriteIds)
        gate.complete(ApiResult.Success(true))
        advanceUntilIdle()
        assertTrue("a" in vm.state.value.favoriteIds)
    }

    @Test fun `favorito con fallo hace rollback y avisa del error`() = runTest {
        val vm = vm()
        garments.toggleHandler = { failure("Sin conexión. Revisa tu red.") }
        val errors = mutableListOf<String>()
        val job = launch { vm.errors.toList(errors) }
        vm.onToggleFavorite("a")
        advanceUntilIdle()
        assertFalse("a" in vm.state.value.favoriteIds)
        assertEquals(listOf("Sin conexión. Revisa tu red."), errors)
        job.cancel()
    }

    @Test fun `doble toque rapido solo envia un cambio`() = runTest {
        val vm = vm()
        var calls = 0
        val gate = CompletableDeferred<ApiResult<Boolean>>()
        garments.toggleHandler = { calls++; gate.await() }
        vm.onToggleFavorite("a")
        vm.onToggleFavorite("a")
        gate.complete(ApiResult.Success(true))
        advanceUntilIdle()
        assertEquals(1, calls)
        assertTrue("a" in vm.state.value.favoriteIds)
    }

    @Test fun `error sin datos muestra error a pantalla completa y reintentar recupera`() = runTest {
        garments.shopHandler = { _, _ -> failure("No se pudo conectar con el servidor.") }
        val vm = ShopViewModel(ShopCatalog(garments, social), me)
        assertEquals("No se pudo conectar con el servidor.", vm.state.value.error)
        assertTrue(vm.state.value.visible.isEmpty())
        garments.shopHandler = { _, _ -> ApiResult.Success(catalogItems) }
        vm.refresh()
        assertNull(vm.state.value.error)
        assertEquals(3, vm.state.value.visible.size)
    }

    @Test fun `un error al refrescar con datos en pantalla los conserva y avisa por toast`() = runTest {
        val vm = vm()
        garments.shopHandler = { _, _ -> failure("Tiempo de espera agotado.") }
        val errors = mutableListOf<String>()
        val job = launch { vm.errors.toList(errors) }
        vm.refresh()
        advanceUntilIdle()
        assertEquals(3, vm.state.value.visible.size)
        assertNull(vm.state.value.error)
        assertEquals(listOf("Tiempo de espera agotado."), errors)
        job.cancel()
    }

    @Test fun `fallo al cambiar de categoria no deja datos de la anterior`() = runTest {
        val vm = vm()
        garments.shopHandler = { _, _ -> failure("Sin conexión") }
        vm.onCategory("shoes")
        assertTrue(vm.state.value.visible.isEmpty())
        assertNotNull(vm.state.value.error)
    }

    @Test fun `formato de precio en euros`() {
        assertEquals("45 €", formatEuro(45.0))
        assertEquals("45,50 €", formatEuro(45.5))
        assertEquals("9,99 €", formatEuro(9.99))
        assertEquals("Buen estado", conditionLabel("good"))
        assertEquals("Como nuevo", conditionLabel("fair"))
    }
}
