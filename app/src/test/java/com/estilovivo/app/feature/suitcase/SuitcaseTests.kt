package com.estilovivo.app.feature.suitcase

import com.estilovivo.app.core.data.ApiError
import com.estilovivo.app.core.data.ApiErrorKind
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.model.Favorite
import com.estilovivo.app.core.data.model.Garment
import com.estilovivo.app.core.data.model.NewTripItem
import com.estilovivo.app.core.data.model.Trip
import com.estilovivo.app.core.data.model.TripItem
import com.estilovivo.app.core.data.repo.TripRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

private fun fail(msg: String = "boom", kind: ApiErrorKind = ApiErrorKind.Server) = ApiResult.Failure(ApiError(msg, kind))

private fun trip(id: String, start: String, end: String, items: List<TripItem> = emptyList()) =
    Trip(id, "Dest $id", start, end, items, emptyList())

private fun item(id: String, checked: Boolean = false) = TripItem(id, "Item $id", checked, false)

class FakeTrips(var trips: List<Trip> = emptyList()) : TripRepository {
    var createResult: ApiResult<Trip>? = null
    var deleteResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var updateItemResult: ApiResult<TripItem>? = null
    var updateItemGate: CompletableDeferred<Unit>? = null
    val updateItemCalls = mutableListOf<Triple<String, Boolean?, Boolean?>>()
    var addItemResult: ApiResult<TripItem>? = null
    var deleteItemResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var metaResult: ApiResult<Trip>? = null

    override suspend fun getAll() = ApiResult.Success(trips)
    override suspend fun create(destination: String, dateStart: String, dateEnd: String, items: List<NewTripItem>, garmentIds: List<String>) =
        createResult ?: ApiResult.Success(Trip("srv-1", destination, dateStart, dateEnd, items.mapIndexed { i, n -> TripItem("i$i", n.label, n.checked, n.isEssential) }, emptyList()))
    override suspend fun updateMeta(tripId: String, destination: String, dateStart: String, dateEnd: String, garmentIds: List<String>) =
        metaResult ?: ApiResult.Success(Trip(tripId, destination, dateStart, dateEnd, emptyList(), emptyList()))
    override suspend fun delete(tripId: String) = deleteResult
    override suspend fun addItem(tripId: String, label: String, isEssential: Boolean) =
        addItemResult ?: ApiResult.Success(TripItem("srv-item", label, false, isEssential))
    override suspend fun updateItem(tripId: String, itemId: String, checked: Boolean?, label: String?, isEssential: Boolean?): ApiResult<TripItem> {
        updateItemCalls += Triple(itemId, checked, isEssential)
        updateItemGate?.await()
        return updateItemResult ?: ApiResult.Success(TripItem(itemId, "", checked ?: false, isEssential ?: false))
    }
    override suspend fun deleteItem(tripId: String, itemId: String) = deleteItemResult
}

@OptIn(ExperimentalCoroutinesApi::class)
class SuitcaseLogicTest {
    private val today = LocalDate.of(2026, 10, 13)

    @Test fun progress() {
        assertEquals(0, Checklist.percent(emptyList()))
        assertEquals(33, Checklist.percent(listOf(item("a", true), item("b"), item("c"))))
        assertEquals(67, Checklist.percent(listOf(item("a", true), item("b", true), item("c"))))
        assertEquals(100, Checklist.percent(listOf(item("a", true))))
    }

    @Test fun countdownLocalDays() {
        assertEquals("En 5 días", TripTiming.label(TripTiming.phase("2026-10-18", "2026-10-20", today)))
        assertEquals("Mañana", TripTiming.label(TripTiming.phase("2026-10-14", "2026-10-20", today)))
        assertEquals("En curso", TripTiming.label(TripTiming.phase("2026-10-13", "2026-10-13", today)))
        assertEquals("En curso", TripTiming.label(TripTiming.phase("2026-10-10", "2026-10-13", today)))
        assertEquals("Terminado", TripTiming.label(TripTiming.phase("2026-10-10", "2026-10-12", today)))
        assertNull(TripTiming.phase("13 oct", "15 oct", today))
        // fin anterior al inicio: viaje de un día
        assertEquals("En 2 días", TripTiming.label(TripTiming.phase("2026-10-15", "2026-10-10", today)))
    }

    @Test fun formatting() {
        assertEquals("13 oct – 15 oct", TripDates.range("2026-10-13", "2026-10-15", today))
        assertEquals("13 oct", TripDates.range("2026-10-13", "2026-10-13", today))
        assertEquals("13 oct 2027 – 15 oct 2027", TripDates.range("2027-10-13", "2027-10-15", today))
        assertEquals("13 oct", TripDates.format("13 oct", today))
        assertNull(TripDates.parse("2026-02-30"))
    }

    @Test fun sortByDate() {
        val list = listOf(
            trip("old", "2026-01-01", "2026-01-05"), trip("later", "2026-12-01", "2026-12-05"),
            trip("legacy", "13 oct", "15 oct"), trip("now", "2026-10-12", "2026-10-14"), trip("soon", "2026-10-20", "2026-10-22"),
            trip("old2", "2026-03-01", "2026-03-05"),
        )
        assertEquals(listOf("now", "soon", "later", "old2", "old", "legacy"), TripTiming.sort(list, today).map { it.id })
    }

    @Test fun formValidation() {
        assertTrue(TripFormRules.validate(TripForm()).destination != null)
        assertTrue(TripFormRules.validate(TripForm(destination = "París")).dates != null)
        assertTrue(TripFormRules.validate(TripForm("París", "2026-10-15", "2026-10-10")).dates != null)
        assertFalse(TripFormRules.validate(TripForm("París", "2026-10-15", "")).any)
        assertEquals("2026-10-15", TripFormRules.normalizedEnd(TripForm("P", "2026-10-15", "")))
        assertTrue(TripFormRules.validate(TripForm("x".repeat(101), "2026-10-15", "2026-10-16")).destination != null)
        val (f, e) = TripFormRules.addItem(TripForm(), "  documentación ")
        assertTrue(e != null && f.items == TripFormRules.suggested)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TripStoreTest {
    private fun TestScope.store(repo: FakeTrips) = TripStore(repo, kotlinx.coroutines.CoroutineScope(StandardTestDispatcher(testScheduler)))

    @Test fun createReturnsRealId() = runTest(StandardTestDispatcher()) {
        val repo = FakeTrips()
        val s = store(repo)
        val r = s.create("Roma", "2026-11-01", "2026-11-03", listOf(NewTripItem("Pasaporte", false, true)), emptyList())
        assertEquals("srv-1", (r as ApiResult.Success).value.id)
        assertEquals(listOf("srv-1"), s.trips.value.map { it.id })
    }

    @Test fun createFailureLeavesListUntouched() = runTest(StandardTestDispatcher()) {
        val repo = FakeTrips().apply { createResult = fail("sin red") }
        val s = store(repo)
        assertTrue(s.create("Roma", "a", "b", emptyList(), emptyList()) is ApiResult.Failure)
        assertTrue(s.trips.value.isEmpty())
    }

    @Test fun deleteRollsBackOnFailure() = runTest(StandardTestDispatcher()) {
        val repo = FakeTrips(listOf(trip("a", "2026-01-01", "2026-01-02"), trip("b", "2026-02-01", "2026-02-02"))).apply { deleteResult = fail() }
        val s = store(repo)
        s.refresh()
        assertTrue(s.delete("a") is ApiResult.Failure)
        assertEquals(listOf("a", "b"), s.trips.value.map { it.id })
    }

    @Test fun deleteSucceeds() = runTest(StandardTestDispatcher()) {
        val repo = FakeTrips(listOf(trip("a", "x", "y")))
        val s = store(repo)
        s.refresh()
        assertTrue(s.delete("a") is ApiResult.Success)
        assertTrue(s.trips.value.isEmpty())
    }

    @Test fun toggleRollsBackAndNotifies() = runTest(StandardTestDispatcher()) {
        val repo = FakeTrips(listOf(trip("t", "x", "y", listOf(item("i1"))))).apply { updateItemResult = fail("no") }
        val s = store(repo)
        s.refresh()
        s.setItem("t", "i1", checked = true)
        assertTrue(s.trips.value[0].items[0].checked) // optimista
        advanceUntilIdle()
        assertFalse(s.trips.value[0].items[0].checked)
    }

    @Test fun doubleTapCoalescesToFinalState() = runTest(StandardTestDispatcher()) {
        val gate = CompletableDeferred<Unit>()
        val repo = FakeTrips(listOf(trip("t", "x", "y", listOf(item("i1"))))).apply { updateItemGate = gate }
        val s = store(repo)
        s.refresh()
        s.setItem("t", "i1", checked = true)
        advanceUntilIdle()
        s.setItem("t", "i1", checked = false) // vuelve al original mientras el primero vuela
        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(s.trips.value[0].items[0].checked)
        assertEquals(listOf(true, false), repo.updateItemCalls.map { it.second })
    }

    @Test fun addItemReplacesTempAndFailureRemoves() = runTest(StandardTestDispatcher()) {
        val repo = FakeTrips(listOf(trip("t", "x", "y")))
        val s = store(repo)
        s.refresh()
        s.addItem("t", "Cámara")
        assertTrue(TripStore.isTemp(s.trips.value[0].items[0].id))
        advanceUntilIdle()
        assertEquals("srv-item", s.trips.value[0].items[0].id)
        repo.addItemResult = fail()
        s.addItem("t", "Otro")
        advanceUntilIdle()
        assertEquals(1, s.trips.value[0].items.size)
    }

    @Test fun deleteItemRollsBack() = runTest(StandardTestDispatcher()) {
        val repo = FakeTrips(listOf(trip("t", "x", "y", listOf(item("a"), item("b"), item("c"))))).apply { deleteItemResult = fail() }
        val s = store(repo)
        s.refresh()
        s.deleteItem("t", "b")
        assertEquals(listOf("a", "c"), s.trips.value[0].items.map { it.id })
        advanceUntilIdle()
        assertEquals(listOf("a", "b", "c"), s.trips.value[0].items.map { it.id })
    }

    @Test fun metaRollbackKeepsItems() = runTest(StandardTestDispatcher()) {
        val repo = FakeTrips(listOf(trip("t", "2026-01-01", "2026-01-02", listOf(item("a"))))).apply { metaResult = fail() }
        val s = store(repo)
        s.refresh()
        val r = s.updateMeta("t", "Nuevo", "2026-02-01", "2026-02-02", emptyList(), emptyList())
        assertTrue(r is ApiResult.Failure)
        assertEquals("Dest t", s.trips.value[0].destination)
        assertEquals(1, s.trips.value[0].items.size)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class WishlistLogicTest {
    private fun g(id: String, type: String, color: String) =
        Garment(id, "u", id, type, color, "all", 0)

    @Test fun compatibilityMatchesWeb() {
        val wardrobe = List(3) { g("w$it", "top", "negro") } + List(3) { g("x$it", "shoes", "negro") }
        assertEquals("Combina con 6+ prendas", WishlistLogic.compatibilityLabel(g("a", "top", "Negro"), wardrobe)) // 3 + 6
        assertEquals("Combina con 3+ prendas", WishlistLogic.compatibilityLabel(g("a", "bottom", "negro"), wardrobe.take(3)))
        assertEquals("Combina con tu armario", WishlistLogic.compatibilityLabel(g("a", "bottom", "rojo"), wardrobe.take(1) + g("z", "bottom", "azul")))
        assertEquals("Sin combinaciones claras", WishlistLogic.compatibilityLabel(g("a", "dress", "rojo"), wardrobe))
    }
}
