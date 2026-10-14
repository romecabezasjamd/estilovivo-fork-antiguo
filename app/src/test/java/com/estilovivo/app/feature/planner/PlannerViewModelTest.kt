package com.estilovivo.app.feature.planner

import androidx.lifecycle.SavedStateHandle
import com.estilovivo.app.core.data.ApiError
import com.estilovivo.app.core.data.ApiErrorKind
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.ImageUpload
import com.estilovivo.app.core.data.Page
import com.estilovivo.app.core.data.model.Garment
import com.estilovivo.app.core.data.model.GarmentDraft
import com.estilovivo.app.core.data.model.Look
import com.estilovivo.app.core.data.model.LookChanges
import com.estilovivo.app.core.data.model.PlannerEntry
import com.estilovivo.app.core.data.model.ShopItem
import com.estilovivo.app.core.data.model.User
import com.estilovivo.app.core.data.repo.GarmentRepository
import com.estilovivo.app.core.data.repo.LookRepository
import com.estilovivo.app.core.data.repo.PlannerRepository
import com.estilovivo.app.core.util.CyclePeriod
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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
import java.time.LocalDate

// ── Dobles de prueba ─────────────────────────────────────────────────────────

private fun fail(message: String = "Sin conexión", kind: ApiErrorKind = ApiErrorKind.Network) =
    ApiResult.Failure(ApiError(message, kind))

private fun look(id: String, name: String = "Look $id") =
    Look(id = id, name = name, garmentIds = listOf("g1"), garments = emptyList(), tags = emptyList())

private class FakePlanner : PlannerRepository {
    var all: ApiResult<List<PlannerEntry>> = ApiResult.Success(emptyList())
    var saveHandler: suspend (String, String?, String) -> ApiResult<PlannerEntry> = { date, lookId, note ->
        ApiResult.Success(PlannerEntry(date, lookId, null, note.ifEmpty { null }))
    }
    var deleteHandler: suspend (String) -> ApiResult<Unit> = { ApiResult.Success(Unit) }
    val saved = mutableListOf<Triple<String, String?, String>>()
    val deleted = mutableListOf<String>()
    var getAllCalls = 0

    var getAllGate: CompletableDeferred<Unit>? = null
    override suspend fun getAll(): ApiResult<List<PlannerEntry>> { getAllCalls++; getAllGate?.await(); return all }
    override suspend fun save(date: String, lookId: String?, eventNote: String): ApiResult<PlannerEntry> {
        saved += Triple(date, lookId, eventNote)
        return saveHandler(date, lookId, eventNote)
    }
    override suspend fun delete(date: String): ApiResult<Unit> { deleted += date; return deleteHandler(date) }
}

private class FakeLooks(initial: List<Look> = emptyList()) : LookRepository {
    private val _mine = MutableStateFlow(initial)
    override val myLooks: StateFlow<List<Look>> = _mine.asStateFlow()
    override suspend fun getMine(): ApiResult<List<Look>> = ApiResult.Success(_mine.value)
    override suspend fun feed(cursor: String?, limit: Int): ApiResult<Page<Look>> = error("no usado")
    override suspend fun create(name: String, garmentIds: List<String>, isPublic: Boolean, mood: String?, tags: List<String>): ApiResult<Look> = error("no usado")
    override suspend fun createWithImage(name: String, garmentIds: List<String>, image: ImageUpload, isPublic: Boolean, mood: String?, tags: List<String>): ApiResult<Look> = error("no usado")
    override suspend fun update(id: String, changes: LookChanges): ApiResult<Look> = error("no usado")
    override suspend fun delete(id: String): ApiResult<Unit> = error("no usado")
}

private class FakeGarments : GarmentRepository {
    private val _g = MutableStateFlow<List<Garment>>(emptyList())
    override val garments: StateFlow<List<Garment>> = _g.asStateFlow()
    var getAllCalls = 0
    override suspend fun getAll(): ApiResult<List<Garment>> { getAllCalls++; return ApiResult.Success(emptyList()) }
    override suspend fun add(draft: GarmentDraft, image: ImageUpload?): ApiResult<Garment> = error("no usado")
    override suspend fun update(id: String, draft: GarmentDraft, image: ImageUpload?): ApiResult<Garment> = error("no usado")
    override suspend fun delete(id: String): ApiResult<Unit> = error("no usado")
    override suspend fun markWorn(id: String): ApiResult<Garment> = error("no usado")
    override suspend fun shop(search: String?, category: String?): ApiResult<List<ShopItem>> = error("no usado")
    override suspend fun toggleFavorite(garmentId: String): ApiResult<Boolean> = error("no usado")
}

private class FakeCycle : CycleGateway {
    private val _changes = MutableStateFlow(0)
    override val changes: StateFlow<Int> = _changes.asStateFlow()
    val data = mutableMapOf<String, CyclePeriod>()
    override fun get(userId: String?): CyclePeriod? = userId?.let { data[it] }
    override fun save(userId: String, period: CyclePeriod?): Boolean {
        if (period == null) data.remove(userId) else data[userId] = period
        _changes.value++
        return true
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlannerViewModelTest {
    private val today = LocalDate.parse("2026-10-13")

    private val planner = FakePlanner()
    private val looks = FakeLooks(listOf(look("a", "Oficina"), look("b", "Playa")))
    private val garments = FakeGarments()
    private val cycle = FakeCycle()
    private val user = MutableStateFlow<User?>(User(id = "u1", name = "Ana", gender = "female", cycleTracking = true))
    private lateinit var scope: TestScope

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(handle: SavedStateHandle = SavedStateHandle()): PlannerViewModel {
        scope = TestScope(UnconfinedTestDispatcher())
        return PlannerViewModel(planner, looks, garments, user, cycle, scope, handle) { today }
    }

    private fun d(s: String) = LocalDate.parse(s)

    // ── Carga ────────────────────────────────────────────────────────────────

    @Test fun `carga las entradas por fecha y deja de cargar`() = runTest {
        planner.all = ApiResult.Success(listOf(PlannerEntry("2026-10-13", "a", null, "cena"), PlannerEntry("2026-10-14T00:00:00.000Z", null, null, "dentista")))
        val vm = vm()
        val s = vm.state.value
        assertTrue(s.loaded); assertFalse(s.loading); assertNull(s.error)
        assertEquals(setOf("2026-10-13", "2026-10-14"), s.entries.keys)
        assertEquals("Oficina", s.lookOf(s.entry(d("2026-10-13")))?.name)
        assertNull(s.lookOf(s.entry(d("2026-10-14"))))
    }

    @Test fun `un fallo en la primera carga muestra error con reintento y despues carga`() = runTest {
        planner.all = fail("No hay conexión")
        val vm = vm()
        assertEquals("No hay conexión", vm.state.value.error)
        assertFalse(vm.state.value.loaded)
        planner.all = ApiResult.Success(listOf(PlannerEntry("2026-10-13", "a")))
        vm.refresh()
        assertTrue(vm.state.value.loaded); assertNull(vm.state.value.error)
        assertEquals(1, vm.state.value.entries.size)
    }

    @Test fun `un fallo al refrescar con datos conserva lo cargado y avisa por evento`() = runTest {
        planner.all = ApiResult.Success(listOf(PlannerEntry("2026-10-13", "a")))
        val vm = vm()
        val events = mutableListOf<PlannerEvent>()
        val job = launchCollect(vm, events)
        planner.all = fail("Sin red")
        vm.refresh()
        assertEquals(1, vm.state.value.entries.size)
        assertNull(vm.state.value.error)
        assertEquals(listOf(PlannerEvent("Sin red", true)), events)
        job.cancel()
    }

    private fun TestScope.launchCollect(vm: PlannerViewModel, into: MutableList<PlannerEvent>) =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { into += it } }

    // ── Asignar / quitar / nota ──────────────────────────────────────────────

    @Test fun `asignar un look guarda con la nota existente, refresca prendas y avisa`() = runTest {
        planner.all = ApiResult.Success(listOf(PlannerEntry("2026-10-13", null, null, "cena")))
        val vm = vm()
        val events = mutableListOf<PlannerEvent>()
        val job = launchCollect(vm, events)
        vm.assignLook(today, "a")
        assertEquals(Triple("2026-10-13", "a", "cena"), planner.saved.single())
        assertEquals("a", vm.state.value.entry(today)?.lookId)
        assertEquals("cena", vm.state.value.entry(today)?.eventNote)
        assertEquals(1, garments.getAllCalls)
        assertFalse(events.single().isError)
        assertTrue(vm.state.value.busyDates.isEmpty())
        job.cancel()
    }

    @Test fun `un error al asignar hace rollback y muestra el mensaje del servidor`() = runTest {
        planner.all = ApiResult.Success(listOf(PlannerEntry("2026-10-13", "a", null, "cena")))
        val vm = vm()
        val events = mutableListOf<PlannerEvent>()
        val job = launchCollect(vm, events)
        planner.saveHandler = { _, _, _ -> fail("Look no válido", ApiErrorKind.Validation) }
        vm.assignLook(today, "b")
        assertEquals("a", vm.state.value.entry(today)?.lookId) // volvió al estado anterior
        assertEquals("cena", vm.state.value.entry(today)?.eventNote)
        assertEquals(listOf(PlannerEvent("Look no válido", true)), events)
        assertEquals(0, garments.getAllCalls)
        assertTrue(vm.state.value.busyDates.isEmpty())
        job.cancel()
    }

    @Test fun `un error al asignar en un dia nuevo lo deja sin entrada`() = runTest {
        val vm = vm()
        planner.saveHandler = { _, _, _ -> fail() }
        vm.assignLook(today, "a")
        assertNull(vm.state.value.entry(today))
    }

    @Test fun `el optimismo se ve antes de que responda el servidor`() = runTest {
        val vm = vm()
        val gate = CompletableDeferred<Unit>()
        planner.saveHandler = { date, lookId, note -> gate.await(); ApiResult.Success(PlannerEntry(date, lookId, null, note.ifEmpty { null })) }
        vm.assignLook(today, "a")
        assertEquals("a", vm.state.value.entry(today)?.lookId)
        assertTrue(PlannerCalendar.iso(today) in vm.state.value.busyDates)
        gate.complete(Unit)
        assertFalse(PlannerCalendar.iso(today) in vm.state.value.busyDates)
    }

    @Test fun `quitar el look conservando la nota guarda la entrada sin look`() = runTest {
        planner.all = ApiResult.Success(listOf(PlannerEntry("2026-10-13", "a", null, "cena")))
        val vm = vm()
        vm.clearLook(today)
        assertEquals(Triple("2026-10-13", null, "cena"), planner.saved.single())
        assertTrue(planner.deleted.isEmpty())
        assertNull(vm.state.value.entry(today)?.lookId)
        assertEquals(1, garments.getAllCalls)
    }

    @Test fun `quitar el look de un dia sin nota elimina la entrada y no guarda vacios`() = runTest {
        planner.all = ApiResult.Success(listOf(PlannerEntry("2026-10-13", "a", null, null)))
        val vm = vm()
        vm.clearLook(today)
        assertEquals(listOf("2026-10-13"), planner.deleted)
        assertTrue(planner.saved.isEmpty())
        assertNull(vm.state.value.entry(today))
    }

    @Test fun `borrar la nota de un dia sin look elimina la entrada`() = runTest {
        planner.all = ApiResult.Success(listOf(PlannerEntry("2026-10-13", null, null, "cena")))
        val vm = vm()
        vm.saveNote(today, "   ")
        assertEquals(listOf("2026-10-13"), planner.deleted)
        assertNull(vm.state.value.entry(today))
    }

    @Test fun `guardar nota vacia en un dia que no existe no llama al servidor`() = runTest {
        val vm = vm()
        vm.saveNote(today, "")
        assertTrue(planner.saved.isEmpty()); assertTrue(planner.deleted.isEmpty())
    }

    @Test fun `editar solo la nota no refresca prendas ni cambia el look`() = runTest {
        planner.all = ApiResult.Success(listOf(PlannerEntry("2026-10-13", "a", null, null)))
        val vm = vm()
        vm.saveNote(today, "  reunión  ")
        assertEquals(Triple("2026-10-13", "a", "reunión"), planner.saved.single())
        assertEquals(0, garments.getAllCalls)
    }

    @Test fun `si el servidor dice que el dia ya no existe al borrar se da por bueno`() = runTest {
        planner.all = ApiResult.Success(listOf(PlannerEntry("2026-10-13", "a", null, null)))
        val vm = vm()
        val events = mutableListOf<PlannerEvent>()
        val job = launchCollect(vm, events)
        planner.deleteHandler = { fail("Entrada no encontrada", ApiErrorKind.NotFound) }
        vm.clearLook(today)
        assertNull(vm.state.value.entry(today))
        assertTrue(events.none { it.isError })
        job.cancel()
    }

    @Test fun `un error al borrar restaura la entrada`() = runTest {
        planner.all = ApiResult.Success(listOf(PlannerEntry("2026-10-13", "a", null, null)))
        val vm = vm()
        planner.deleteHandler = { fail("Error del servidor", ApiErrorKind.Server) }
        vm.clearLook(today)
        assertEquals("a", vm.state.value.entry(today)?.lookId)
    }

    @Test fun `lo editado durante una descarga no se pisa con la respuesta antigua`() = runTest {
        val vm = vm()
        // La respuesta (sin el día) sale antes de la edición y llega después de ella.
        val gate = CompletableDeferred<Unit>()
        planner.getAllGate = gate
        vm.refresh()
        vm.assignLook(today, "a")
        gate.complete(Unit)
        assertEquals("a", vm.state.value.entry(today)?.lookId)
    }

    // ── Navegación ───────────────────────────────────────────────────────────

    @Test fun `arranca en semanal con hoy y navega por semanas meses y dias`() = runTest {
        val vm = vm()
        assertEquals(PlannerMode.Weekly, vm.state.value.mode)
        assertEquals(today, vm.state.value.selected)
        vm.next(); assertEquals(d("2026-10-20"), vm.state.value.selected); assertEquals(1, vm.state.value.direction)
        vm.previous(); vm.previous(); assertEquals(d("2026-10-06"), vm.state.value.selected); assertEquals(-1, vm.state.value.direction)
        vm.goToday(); assertEquals(today, vm.state.value.selected)
        vm.selectMode(PlannerMode.Monthly); vm.next()
        assertEquals(d("2026-11-13"), vm.state.value.selected)
        vm.selectMode(PlannerMode.Daily); vm.next()
        assertEquals(d("2026-11-14"), vm.state.value.selected)
    }

    @Test fun `en mensual el primer toque selecciona y el segundo abre el dia`() = runTest {
        val vm = vm()
        vm.selectMode(PlannerMode.Monthly)
        vm.tapDay(d("2026-10-20"))
        assertEquals(PlannerMode.Monthly, vm.state.value.mode)
        assertEquals(d("2026-10-20"), vm.state.value.selected)
        vm.tapDay(d("2026-10-20"))
        assertEquals(PlannerMode.Daily, vm.state.value.mode)
    }

    @Test fun `en semanal tocar un dia abre la vista diaria`() = runTest {
        val vm = vm()
        vm.tapDay(d("2026-10-15"))
        assertEquals(PlannerMode.Daily, vm.state.value.mode)
        assertEquals(d("2026-10-15"), vm.state.value.selected)
    }

    @Test fun `la fecha inicial de la ruta abre el diario una sola vez`() = runTest {
        val handle = SavedStateHandle()
        val vm = vm(handle)
        vm.openInitialDate("2026-12-25")
        assertEquals(PlannerMode.Daily, vm.state.value.mode)
        assertEquals(d("2026-12-25"), vm.state.value.selected)
        vm.next()
        vm.openInitialDate("2026-12-25") // p. ej. tras rotar: no debe volver a mover
        assertEquals(d("2026-12-26"), vm.state.value.selected)
    }

    @Test fun `una fecha inicial invalida se ignora`() = runTest {
        val vm = vm()
        vm.openInitialDate("no-es-fecha")
        assertEquals(PlannerMode.Weekly, vm.state.value.mode)
        assertEquals(today, vm.state.value.selected)
    }

    @Test fun `el estado de navegacion sobrevive a la recreacion del ViewModel`() = runTest {
        val handle = SavedStateHandle()
        val first = vm(handle)
        first.openDay(d("2026-11-02"))
        val second = vm(handle)
        assertEquals(PlannerMode.Daily, second.state.value.mode)
        assertEquals(d("2026-11-02"), second.state.value.selected)
    }

    // ── Ciclo ────────────────────────────────────────────────────────────────

    @Test fun `el ciclo solo se activa en perfil femenino con seguimiento`() = runTest {
        val vm = vm()
        assertTrue(vm.state.value.cycleEnabled)
        user.value = User(id = "u1", name = "Ana", gender = "female", cycleTracking = false)
        assertFalse(vm.state.value.cycleEnabled)
        user.value = User(id = "u1", name = "Ana", gender = "male", cycleTracking = true)
        assertFalse(vm.state.value.cycleEnabled)
    }

    @Test fun `guardar el periodo valida fin mayor o igual que inicio y marca los dias`() = runTest {
        val vm = vm()
        assertNotNull(vm.saveCycle(d("2026-10-14"), d("2026-10-10")))
        assertNull(vm.state.value.cyclePeriod)
        assertNull(vm.saveCycle(d("2026-10-10"), d("2026-10-14")))
        assertEquals(CyclePeriod("2026-10-10", "2026-10-14"), vm.state.value.cyclePeriod)
        assertTrue(vm.state.value.isCycleDay(d("2026-10-12")))
        assertFalse(vm.state.value.isCycleDay(d("2026-10-15")))
        assertNull(vm.saveCycle(d("2026-10-20"), d("2026-10-20"))) // un solo día es válido
    }

    @Test fun `borrar el periodo deja de marcar dias y sin ciclo activo no se guarda`() = runTest {
        val vm = vm()
        vm.saveCycle(d("2026-10-10"), d("2026-10-14"))
        vm.clearCycle()
        assertNull(vm.state.value.cyclePeriod)
        assertFalse(vm.state.value.isCycleDay(d("2026-10-12")))
        user.value = User(id = "u1", name = "Ana", gender = "male", cycleTracking = true)
        assertNotNull(vm.saveCycle(d("2026-10-10"), d("2026-10-14")))
    }
}
