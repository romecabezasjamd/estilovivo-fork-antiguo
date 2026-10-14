package com.estilovivo.app.feature.wardrobe

import com.estilovivo.app.core.data.model.Look
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class CreateLookAndWashingTest {
    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun wardrobe() = FakeGarmentRepo(
        listOf(
            garment("t", type = "top"), garment("b", type = "bottom"), garment("s", type = "shoes"), garment("o", type = "outerwear"),
        ),
    )

    private fun vm(looks: FakeLookRepo = FakeLookRepo(), repo: FakeGarmentRepo = wardrobe(), lookId: String? = null) =
        CreateLookViewModel(lookId, repo, looks, Random(1))

    // ── CreateLook ───────────────────────────────────────────────────────────────────────────

    @Test fun `seleccionar y quitar prendas respeta el orden de seleccion`() = runTest {
        val vm = vm()
        vm.toggle("b"); vm.toggle("t"); vm.toggle("s")
        assertEquals(listOf("b", "t", "s"), vm.state.value.draft.selectedIds)
        vm.toggle("t")
        assertEquals(listOf("b", "s"), vm.state.value.draft.selectedIds)
        vm.clear()
        assertTrue(vm.state.value.draft.selectedIds.isEmpty())
    }

    @Test fun `sorprendeme elige una prenda por categoria`() = runTest {
        val vm = vm()
        vm.surprise()
        val picks = vm.state.value.selected
        assertTrue(picks.isNotEmpty())
        assertEquals(picks.size, picks.map { WardrobeCatalog.categoryOf(it.type) }.toSet().size)
        assertTrue(picks.any { it.type == "shoes" })
    }

    @Test fun `sorprendeme con armario vacio avisa`() = runTest {
        val vm = vm(repo = FakeGarmentRepo())
        vm.surprise()
        val e = vm.events.first()
        assertTrue(e is WardrobeEvent.Message && e.error)
        assertTrue(vm.state.value.draft.selectedIds.isEmpty())
    }

    @Test fun `etiquetas normalizadas, sin repetir y maximo cinco`() = runTest {
        val vm = vm()
        assertTrue(vm.addTag("  #Verano "))
        assertFalse(vm.addTag("verano"))
        assertFalse(vm.addTag("   "))
        listOf("a", "b", "c", "d").forEach { assertTrue(vm.addTag(it)) }
        assertFalse(vm.addTag("sexta"))
        assertEquals(listOf("verano", "a", "b", "c", "d"), vm.state.value.draft.tags)
        vm.removeTag("a")
        assertTrue(vm.addTag("sexta"))
    }

    @Test fun `no se guarda un look sin prendas`() = runTest {
        val looks = FakeLookRepo()
        val vm = vm(looks)
        vm.save()
        assertTrue(looks.createCalls.isEmpty())
        assertNotNull(vm.state.value.saveError)
    }

    @Test fun `guardar crea el look y avisa para el confeti`() = runTest {
        val looks = FakeLookRepo()
        val vm = vm(looks)
        vm.toggle("t"); vm.toggle("b")
        vm.setName("  Oficina "); vm.setMood("elegant"); vm.setPublic(true); vm.addTag("trabajo")
        vm.save()
        assertEquals(listOf(listOf("t", "b")), looks.createCalls)
        assertEquals("elegant", looks.lastMood)
        assertEquals(listOf("trabajo"), looks.lastTags)
        assertEquals(1, vm.state.value.savedCount)
        assertFalse(vm.state.value.saving)
        assertEquals("Oficina", looks.state.value.single().name)
    }

    @Test fun `error del servidor al guardar conserva el borrador`() = runTest {
        val looks = FakeLookRepo().apply { createResult = failure("Título demasiado largo") }
        val vm = vm(looks)
        vm.toggle("t")
        vm.save()
        assertEquals("Título demasiado largo", vm.state.value.saveError)
        assertEquals(0, vm.state.value.savedCount)
        assertEquals(listOf("t"), vm.state.value.draft.selectedIds)
        assertFalse(vm.state.value.saving)
    }

    @Test fun `editar separa el mood de las etiquetas, ignora prendas que ya no existen y puede quitar el mood`() = runTest {
        val look = Look("l1", "Brunch", listOf("t", "ghost"), emptyList(), listOf("casual", "domingo"), mood = "casual")
        val looks = FakeLookRepo(listOf(look))
        val vm = vm(looks, lookId = "l1")
        val d = vm.state.value.draft
        assertEquals(listOf("t"), d.selectedIds)
        assertEquals(listOf("domingo"), d.tags)
        assertEquals("casual", d.mood)
        vm.setMood("casual") // tocar el mismo estado lo desmarca
        vm.save()
        val change = looks.updates.single().second
        assertEquals("", change.mood)
        assertEquals(listOf("t"), change.garmentIds)
        assertEquals(listOf("domingo"), change.tags)
    }

    @Test fun `editar un look inexistente lo indica`() = runTest {
        val vm = vm(lookId = "nope")
        assertTrue(vm.state.value.notFound)
    }

    @Test fun `el borrador se codifica y se restaura`() {
        val d = LookDraft(listOf("a", "b"), "Mi look", "bold", listOf("x", "y"), true)
        assertEquals(d, LookDraft.decode(d.encode()))
        assertNull(LookDraft.decode(listOf("solo uno")))
    }

    @Test fun `las posiciones del lienzo caben y estan centradas`() {
        for (n in 1..9) {
            val slots = boardSlots(n, 600f, 800f)
            assertEquals(n, slots.size)
            slots.forEach {
                assertTrue(it.cx in 0f..600f)
                assertTrue(it.cy in 0f..800f)
                assertTrue(it.size > 0f)
            }
        }
        // 3 prendas en 2 columnas: la última queda centrada
        val three = boardSlots(3, 600f, 800f)
        assertEquals(300f, three[2].cx, 0.01f)
    }

    // ── Lavadora ─────────────────────────────────────────────────────────────────────────────

    @Test fun `devolver todas resume el resultado aunque alguna falle`() = runTest {
        val repo = FakeGarmentRepo(listOf(garment("1", washing = true), garment("2", washing = true), garment("3", washing = true)))
        repo.updateResult = { id, _ ->
            if (id == "2") failure("Sin conexión")
            else ApiResultOk(repo.state.value.first { it.id == id }.copy(isWashing = false))
        }
        val vm = WashingViewModel(repo, GarmentActions(repo))
        assertEquals(3, vm.state.value.items.size)
        vm.returnAll()
        assertEquals(listOf("2"), vm.state.value.items.map { it.id })
        assertFalse(vm.state.value.returningAll)
        assertTrue(vm.state.value.busy.isEmpty())
        val e = vm.events.first()
        assertTrue(e is WardrobeEvent.Message && e.error && e.text.startsWith("2 devueltas"))
    }

    @Test fun `devolver una prenda y evitar toques dobles`() = runTest {
        val repo = FakeGarmentRepo(listOf(garment("1", washing = true))).apply { updateGate = kotlinx.coroutines.CompletableDeferred() }
        val vm = WashingViewModel(repo, GarmentActions(repo))
        vm.returnToCloset("1")
        vm.returnToCloset("1")
        assertEquals(1, repo.updates.size)
        repo.updateGate!!.complete(Unit)
        assertTrue(vm.state.value.items.isEmpty())
    }
}

private fun ApiResultOk(g: com.estilovivo.app.core.data.model.Garment) = com.estilovivo.app.core.data.ApiResult.Success(g)
