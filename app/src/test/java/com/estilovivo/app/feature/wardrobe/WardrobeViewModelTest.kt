package com.estilovivo.app.feature.wardrobe

import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.ImageUpload
import com.estilovivo.app.core.data.model.Look
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WardrobeViewModelTest {
    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private val photo = ImageUpload("a.jpg", "image/jpeg", ByteArray(4))

    private fun vm(
        garments: FakeGarmentRepo = FakeGarmentRepo(),
        looks: FakeLookRepo = FakeLookRepo(),
    ) = WardrobeViewModel(garments, looks, FakeTripRepo(), GarmentActions(garments))

    private fun sampleRepo() = FakeGarmentRepo(
        listOf(
            garment("1", type = "top", color = "azul", usage = 3),
            garment("2", type = "bottom", color = "negro", usage = 8),
            garment("3", type = "shoes", color = "negro", usage = 1, washing = true),
            garment("4", type = "top", color = "rojo", usage = 0, forSale = true, price = 20.0),
        ),
    )

    // ── filtros, orden y contadores ──────────────────────────────────────────────────────────

    @Test fun `carga inicial expone armario sin lavadora, contadores y ventas`() = runTest {
        val vm = vm(sampleRepo())
        val s = vm.state.value
        assertFalse(s.loading)
        assertEquals(listOf("1", "2", "4"), s.closet.map { it.id })
        assertEquals(1, s.washingCount)
        assertEquals(3, s.categoryCounts["all"])
        assertEquals(2, s.categoryCounts["top"])
        assertEquals(listOf("4"), s.sales.map { it.id })
        assertEquals(20.0, s.salesTotal, 0.0)
    }

    @Test fun `cambiar filtros y orden actualiza la lista y los contadores`() = runTest {
        val vm = vm(sampleRepo())
        vm.updateFilters { it.copy(category = "top") }
        assertEquals(listOf("1", "4"), vm.state.value.closet.map { it.id })
        vm.updateFilters { it.copy(sort = SortOrder.LeastUsed) }
        assertEquals(listOf("4", "1"), vm.state.value.closet.map { it.id })
        vm.updateFilters { it.copy(color = "negro") }
        assertTrue(vm.state.value.closet.isEmpty())
        assertEquals(1, vm.state.value.categoryCounts["bottom"])
        vm.clearAdvancedFilters()
        assertEquals(listOf("1", "4"), vm.state.value.closet.map { it.id }.sortedBy { it })
        assertEquals("top", vm.state.value.filters.category)
    }

    @Test fun `error de carga sin datos muestra error y con datos solo avisa`() = runTest {
        val empty = FakeGarmentRepo().apply { getAllResult = failure("Sin conexión") }
        val vmEmpty = vm(empty)
        assertEquals("Sin conexión", vmEmpty.state.value.error)

        val withData = sampleRepo().apply { getAllResult = failure("Sin conexión") }
        val vm2 = vm(withData)
        assertNull(vm2.state.value.error)
        val e = vm2.events.first()
        assertTrue(e is WardrobeEvent.Message && e.error)
    }

    // ── alta optimista ───────────────────────────────────────────────────────────────────────

    @Test fun `el alta aparece al instante y se sustituye por la del servidor`() = runTest {
        val repo = FakeGarmentRepo().apply { addGate = CompletableDeferred() }
        val vm = vm(repo)
        vm.add(AddRequest(AddKind.Closet, GarmentForm(name = "Falda", category = "bottom", photoUri = "content://p"), photo))

        val during = vm.state.value
        assertEquals(1, during.closet.size)
        assertEquals("Falda", during.closet[0].name)
        assertEquals(1, during.pendingIds.size)
        assertEquals(1, repo.addCalls)

        repo.addGate!!.complete(ApiResult.Success(garment("srv", name = "Falda", type = "bottom")))
        val after = vm.state.value
        assertEquals(listOf("srv"), after.closet.map { it.id })
        assertTrue(after.pendingIds.isEmpty())
        assertTrue(vm.events.first() is WardrobeEvent.Message)
    }

    @Test fun `si el servidor rechaza el alta se deshace y se devuelve el formulario`() = runTest {
        val repo = FakeGarmentRepo().apply { addGate = CompletableDeferred() }
        val vm = vm(repo)
        val form = GarmentForm(name = "Vestido", category = "dress", price = "30", forSale = true, photoUri = "content://p")
        val request = AddRequest(AddKind.Sale, form, photo)
        vm.add(request)
        assertEquals(1, vm.state.value.sales.size)

        repo.addGate!!.complete(failure("Los usuarios gratuitos pueden tener un máximo de 5 prendas a la venta."))
        val s = vm.state.value
        assertTrue(s.all.isEmpty())
        assertTrue(s.pendingIds.isEmpty())
        val e = vm.events.first()
        assertTrue(e is WardrobeEvent.AddRolledBack)
        e as WardrobeEvent.AddRolledBack
        assertEquals(form, e.request.form)
        assertTrue(e.message.contains("máximo de 5"))
    }

    @Test fun `alta de venta envia el precio validado y el estado`() = runTest {
        val repo = FakeGarmentRepo()
        val vm = vm(repo)
        vm.add(AddRequest(AddKind.Sale, GarmentForm(name = "Abrigo", price = "45,5", condition = "fair", photoUri = "content://p"), photo))
        val d = repo.addedDrafts.single()
        assertEquals(true, d.forSale)
        assertEquals(45.5, d.price!!, 0.0)
        assertEquals("fair", d.condition)
    }

    // ── operaciones ──────────────────────────────────────────────────────────────────────────

    @Test fun `toques dobles no repiten la operacion en curso`() = runTest {
        val repo = sampleRepo().apply { updateGate = CompletableDeferred() }
        val vm = vm(repo)
        vm.setWashing("1", true)
        vm.setWashing("1", true)
        assertEquals(1, repo.updates.size)
        assertTrue("1" in vm.state.value.busy)
        repo.updateGate!!.complete(Unit)
        assertTrue(vm.state.value.busy.isEmpty())
        assertEquals(2, vm.state.value.washingCount) // la 3 ya estaba + la 1
    }

    @Test fun `una prenda en venta no entra en la lavadora`() = runTest {
        val repo = sampleRepo()
        val vm = vm(repo)
        vm.setWashing("4", true)
        assertTrue(repo.updates.isEmpty())
        val msg = vm.events.first()
        assertTrue(msg is WardrobeEvent.Message && msg.error)
    }

    @Test fun `vender con precio invalido no llama al servidor`() = runTest {
        val repo = sampleRepo()
        val vm = vm(repo)
        vm.sell("1", GarmentForm(price = "0"))
        assertTrue(repo.updates.isEmpty())
        vm.sell("1", GarmentForm(price = "100001"))
        assertTrue(repo.updates.isEmpty())
        vm.sell("1", GarmentForm(price = "15,5", condition = "good"))
        assertEquals(1, repo.updates.size)
        assertEquals(15.5, repo.updates[0].second.price!!, 0.0)
        assertEquals(true, repo.updates[0].second.forSale)
    }

    @Test fun `el limite de ventas del servidor llega tal cual al usuario`() = runTest {
        val limit = "Los usuarios gratuitos pueden tener un máximo de 5 prendas a la venta. Actualiza a Premium para ventas ilimitadas."
        val repo = sampleRepo().apply { updateResult = { _, _ -> failure(limit) } }
        val vm = vm(repo)
        vm.sell("1", GarmentForm(price = "20"))
        val events = mutableListOf<WardrobeEvent>()
        repeat(2) { events += vm.events.first() }
        assertTrue(events.any { it is WardrobeEvent.Message && it.text == limit && it.error })
        assertTrue(events.any { it is WardrobeEvent.OperationResult && !it.success && it.message == limit })
        assertTrue(vm.state.value.busy.isEmpty())
    }

    @Test fun `retirar de la venta borra el precio`() = runTest {
        val repo = sampleRepo()
        val vm = vm(repo)
        vm.cancelSale("4")
        val d = repo.updates.single().second
        assertEquals(false, d.forSale)
        assertNull(d.price)
        assertTrue(vm.state.value.sales.isEmpty())
    }

    @Test fun `publicar y borrar looks`() = runTest {
        val look = Look("l1", "Brunch", listOf("1"), emptyList(), emptyList(), isPublic = false)
        val looks = FakeLookRepo(listOf(look))
        val vm = vm(sampleRepo(), looks)
        vm.toggleLookPublic("l1")
        assertEquals(true, looks.updates.single().second.isPublic)
        assertEquals(true, vm.state.value.looks.single().isPublic)
        vm.deleteLook("l1")
        assertTrue(vm.state.value.looks.isEmpty())
    }
}
