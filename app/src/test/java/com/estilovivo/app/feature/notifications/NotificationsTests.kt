package com.estilovivo.app.feature.notifications

import com.estilovivo.app.app.MainTab
import com.estilovivo.app.core.data.ApiError
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.AppScope
import com.estilovivo.app.core.data.model.AppNotification
import com.estilovivo.app.core.data.repo.NotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

private val MADRID = ZoneId.of("Europe/Madrid")

private fun n(
    id: String, type: String = "LIKE", content: String = "hola", related: String? = "abc123",
    read: Boolean = false, at: String = "2026-10-13T10:00:00Z",
) = AppNotification(id, type, content, related, read, at)

class NotificationLogicTest {
    @Test fun badgeCapsAtNinePlus() {
        assertNull(badgeText(0)); assertNull(badgeText(-3))
        assertEquals("1", badgeText(1)); assertEquals("9", badgeText(9))
        assertEquals("9+", badgeText(10)); assertEquals("9+", badgeText(250))
    }

    @Test fun destinationsByType() {
        assertEquals(NotificationDestination.Chat("c1"), destinationFor("CHAT", "x", "c1"))
        assertEquals(NotificationDestination.Chat("c1"), destinationFor("STORY_REACTION", "x", "c1"))
        assertEquals(NotificationDestination.Tab(MainTab.Social), destinationFor("LIKE", "x", "look"))
        assertEquals(NotificationDestination.Tab(MainTab.Social), destinationFor("COMMENT", "x", null))
        assertEquals(NotificationDestination.UserProfile("u1"), destinationFor("FOLLOW", "Ana te siguió", "u1"))
        assertEquals(NotificationDestination.FollowRequests, destinationFor("FOLLOW", "Ana quiere seguirte", "u1"))
        assertEquals(NotificationDestination.Tab(MainTab.Profile), destinationFor("FOLLOW", "Ana te siguió", null))
        assertEquals(NotificationDestination.Washing, destinationFor("WASHING", "x", null))
    }

    @Test fun unknownAndInvalidDataNeverCrash() {
        assertEquals(NotificationDestination.None, destinationFor("ALGO_NUEVO", "x", "id"))
        assertEquals(NotificationDestination.None, destinationFor("", "", null))
        // id nulo o con caracteres peligrosos: se cae a Social en vez de navegar a una ruta inválida
        assertEquals(NotificationDestination.Tab(MainTab.Social), destinationFor("CHAT", "x", null))
        assertEquals(NotificationDestination.Tab(MainTab.Social), destinationFor("CHAT", "x", "../../etc"))
        assertEquals(NotificationDestination.Tab(MainTab.Social), destinationFor("CHAT", "x", "a".repeat(200)))
    }

    @Test fun groupsByDayInMadridAcrossMidnight() {
        // 13/10 22:30Z = 14/10 00:30 en Madrid (CEST, UTC+2)
        val now = Instant.parse("2026-10-14T09:00:00Z")
        val list = listOf(
            n("a", at = "2026-10-13T22:30:00Z"), // 14/10 00:30 Madrid -> Hoy
            n("b", at = "2026-10-13T21:59:00Z"), // 13/10 23:59 Madrid -> Ayer
            n("c", at = "2026-10-10T08:00:00Z"),
            n("d", at = "2025-12-31T08:00:00Z"),
        )
        val sections = groupByDay(list, now, MADRID)
        assertEquals(listOf("Hoy", "Ayer", "10 de octubre", "31 de diciembre de 2025"), sections.map { it.label })
        assertEquals(listOf("a"), sections[0].items.map { it.id })
        assertEquals(listOf("b"), sections[1].items.map { it.id })
    }

    @Test fun groupingSortsNewestFirstAndHandlesBadDatesAndFuture() {
        val now = Instant.parse("2026-10-13T12:00:00Z")
        val sections = groupByDay(
            listOf(n("old", at = "2026-10-13T08:00:00Z"), n("new", at = "2026-10-13T11:00:00Z"), n("bad", at = "no-es-fecha"), n("future", at = "2026-10-20T00:00:00Z")),
            now, MADRID,
        )
        assertEquals(listOf("Hoy", "Anteriores"), sections.map { it.label })
        assertEquals(listOf("future", "new", "old"), sections[0].items.map { it.id })
        assertEquals(listOf("bad"), sections[1].items.map { it.id })
        assertTrue(groupByDay(emptyList(), now, MADRID).isEmpty())
    }

    @Test fun timeLabels() {
        val now = Instant.parse("2026-10-13T12:00:00Z")
        assertEquals("Ahora", timeLabel("2026-10-13T11:59:30Z", now, MADRID))
        assertEquals("Hace 5 min", timeLabel("2026-10-13T11:55:00Z", now, MADRID))
        assertEquals("11:00", timeLabel("2026-10-13T09:00:00Z", now, MADRID))
        assertEquals("", timeLabel("???", now, MADRID))
    }

    @Test fun systemNotificationOnlyInBackgroundAndNeverTwice() {
        assertEquals(AlertAction.InAppBanner, alertAction(isForeground = true, alreadySeen = false))
        assertEquals(AlertAction.SystemNotification, alertAction(isForeground = false, alreadySeen = false))
        assertEquals(AlertAction.None, alertAction(isForeground = true, alreadySeen = true))
        assertEquals(AlertAction.None, alertAction(isForeground = false, alreadySeen = true))
    }

    @Test fun seenIdsIsBounded() {
        val seen = SeenIds(capacity = 2)
        assertTrue(seen.firstTime("1")); assertTrue(seen.firstTime("2"))
        assertTrue(!seen.firstTime("2"))
        assertTrue(seen.firstTime("3")) // expulsa "1"
        assertTrue(seen.firstTime("1"))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationAlertsTest {
    private val shown = mutableListOf<String>()
    private var foreground = true

    private fun alerts(flow: MutableSharedFlow<AppNotification>, scope: CoroutineScope) =
        NotificationAlerts(flow, { foreground }, { shown += it.id }, scope)

    @Test fun routesByAppStateWithoutDuplicates() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val flow = MutableSharedFlow<AppNotification>()
        val alerts = alerts(flow, scope)
        val banners = mutableListOf<String>()
        val job = scope.launchCollect(alerts, banners)

        flow.emit(n("1")); flow.emit(n("1")) // repetida
        foreground = false
        flow.emit(n("2")); flow.emit(n("2"))
        flow.emit(n("3", read = true)) // ya leída: no avisa
        assertEquals(listOf("1"), banners)
        assertEquals(listOf("2"), shown)
        job.cancel()
    }

    private fun CoroutineScope.launchCollect(a: NotificationAlerts, out: MutableList<String>) =
        launch { a.inApp.collect { out += it.id } }
}

private class FakeRepo(initial: List<AppNotification> = emptyList(), var fail: String? = null) : NotificationRepository {
    val list = MutableStateFlow(initial)
    override val notifications: StateFlow<List<AppNotification>> = list
    override val unreadCount: StateFlow<Int> = MutableStateFlow(0)
    override suspend fun refresh(): ApiResult<List<AppNotification>> =
        fail?.let { ApiResult.Failure(ApiError(it)) } ?: ApiResult.Success(list.value)
    override suspend fun markRead(id: String): ApiResult<Unit> {
        list.value = list.value.map { if (it.id == id) it.copy(isRead = true) else it }
        return ApiResult.Success(Unit)
    }
    override suspend fun markAllRead(): ApiResult<Unit> {
        list.value = list.value.map { it.copy(isRead = true) }
        return ApiResult.Success(Unit)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val now = Instant.parse("2026-10-13T12:00:00Z")

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(repo: FakeRepo) =
        NotificationsViewModel(repo, AppScope(CoroutineScope(dispatcher)), { now }, MADRID, tickMs = 0)

    @Test fun loadsAndCountsUnread() = runTest {
        val vm = vm(FakeRepo(listOf(n("1"), n("2", read = true), n("3"))))
        val s = vm.state.value
        assertTrue(!s.loading)
        assertEquals(2, s.unread)
        assertEquals(1, s.sections.size)
    }

    @Test fun errorOnEmptyListShowsRetryState() = runTest {
        val repo = FakeRepo(fail = "Sin conexión")
        val vm = vm(repo)
        assertEquals("Sin conexión", vm.state.value.error)
        repo.fail = null
        repo.list.value = listOf(n("1"))
        vm.retry()
        assertNull(vm.state.value.error)
        assertEquals(1, vm.state.value.unread)
    }

    @Test fun openMarksReadAndEmitsDestination() = runTest {
        val repo = FakeRepo(listOf(n("1", type = "CHAT", related = "conv1")))
        val vm = vm(repo)
        vm.open(repo.list.value.first())
        val e = vm.events.first()
        assertEquals(NotificationsEvent.Open(NotificationDestination.Chat("conv1")), e)
        assertTrue(repo.list.value.first().isRead)
        assertEquals(0, vm.state.value.unread)
    }

    @Test fun markAllRead() = runTest {
        val repo = FakeRepo(listOf(n("1"), n("2")))
        val vm = vm(repo)
        vm.markAllRead()
        assertEquals(0, vm.state.value.unread)
        assertTrue(!vm.state.value.markingAll)
    }
}
