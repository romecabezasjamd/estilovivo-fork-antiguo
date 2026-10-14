package com.estilovivo.app.feature.home

import com.estilovivo.app.core.data.ApiError
import com.estilovivo.app.core.data.ApiErrorKind
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.model.DailyLoginResult
import com.estilovivo.app.core.data.model.GamificationProgress
import com.estilovivo.app.core.data.model.Garment
import com.estilovivo.app.core.data.model.Look
import com.estilovivo.app.core.data.model.PlannerEntry
import com.estilovivo.app.core.data.model.ProfileUpdate
import com.estilovivo.app.core.data.model.User
import com.estilovivo.app.core.data.model.UserStreak
import com.estilovivo.app.core.data.repo.AuthRepository
import com.estilovivo.app.core.data.repo.GamificationRepository
import com.estilovivo.app.core.data.repo.GarmentRepository
import com.estilovivo.app.core.data.repo.LookRepository
import com.estilovivo.app.core.data.repo.PlannerRepository
import com.estilovivo.app.core.data.repo.SocialRepository
import com.estilovivo.app.core.data.repo.TripRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
import java.lang.reflect.Proxy
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Crea un doble de una interfaz: cada método llama a [handler] con su nombre y argumentos (sin la Continuation). */
private inline fun <reified T : Any> fake(crossinline handler: (name: String, args: List<Any?>) -> Any?): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { proxy, method, args ->
        when (method.name) {
            "toString" -> "fake<${T::class.simpleName}>"
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            else -> handler(method.name, args?.toList().orEmpty())
        }
    } as T

private fun fail(msg: String) = ApiResult.Failure(ApiError(msg, ApiErrorKind.Server))

private fun look(id: String, name: String = "Look $id", garments: List<Garment> = emptyList()) =
    Look(id, name, garments.map { it.id }, garments, emptyList())

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    // 22:30 UTC del 12 de octubre = 00:30 del 13 en Madrid
    private val clock = Clock.fixed(Instant.parse("2026-10-12T22:30:00Z"), ZoneId.of("Europe/Madrid"))

    private val user = MutableStateFlow<User?>(User(id = "u1", name = "Lucía Pérez", gender = "female", cycleTracking = true, mood = "casual"))
    private val garments = MutableStateFlow(emptyList<Garment>())
    private val looks = MutableStateFlow(emptyList<Look>())
    private val server = listOf(
        garment("t1", "top", "blanco"), garment("b1", "bottom", "negro"), garment("s1", "shoes", "blanco"),
    )
    private var plannerEntries = emptyList<PlannerEntry>()
    private var plannerSaves = mutableListOf<Triple<String, String?, String>>()
    private var createdLooks = mutableListOf<List<String>>()
    private var profileResult: ApiResult<User>? = null
    private var moodSeenDuringRequest: String? = null
    private var dailyLoginCalls = 0
    private var progressCalls = 0
    private var failProgress = false
    private lateinit var vm: HomeViewModel

    private val auth = fake<AuthRepository> { name, args ->
        when (name) {
            "getCurrentUser" -> user
            "updateProfile" -> {
                moodSeenDuringRequest = vm.state.value.mood
                val res = profileResult ?: ApiResult.Success(user.value!!.copy(mood = (args[0] as ProfileUpdate).mood))
                (res as? ApiResult.Success)?.let { user.value = it.value }
                res
            }
            else -> error("auth.$name no esperado")
        }
    }
    private val garmentRepo = fake<GarmentRepository> { name, _ ->
        when (name) {
            "getGarments" -> garments
            "getAll" -> { garments.value = server; ApiResult.Success(server) }
            else -> error("garments.$name no esperado")
        }
    }
    private val lookRepo = fake<LookRepository> { name, args ->
        when (name) {
            "getMyLooks" -> looks
            "getMine" -> ApiResult.Success(looks.value)
            "create" -> {
                @Suppress("UNCHECKED_CAST") createdLooks += (args[1] as List<String>)
                val l = look("L${createdLooks.size}", args[0] as String, server.filter { it.id in (args[1] as List<*>) })
                looks.value = looks.value + l
                ApiResult.Success(l)
            }
            else -> error("looks.$name no esperado")
        }
    }
    private val planner = fake<PlannerRepository> { name, args ->
        when (name) {
            "getAll" -> ApiResult.Success(plannerEntries)
            "save" -> {
                plannerSaves += Triple(args[0] as String, args[1] as String?, args[2] as String)
                ApiResult.Success(PlannerEntry(args[0] as String, args[1] as String?, null, args[2] as String))
            }
            else -> error("planner.$name no esperado")
        }
    }
    private val gamification = fake<GamificationRepository> { name, _ ->
        when (name) {
            "dailyLogin" -> { dailyLoginCalls++; ApiResult.Success(DailyLoginResult(3, 10, emptyList())) }
            "getProgress" -> {
                progressCalls++
                if (failProgress) fail("sin red")
                else ApiResult.Success(GamificationProgress(250, 2, 150, 200, 75, UserStreak(3, null), emptyList(), emptyList(), 0))
            }
            else -> error("gamification.$name no esperado")
        }
    }
    private val social = fake<SocialRepository> { name, _ ->
        when (name) {
            "getFavorites" -> ApiResult.Success(emptyList<Any>())
            else -> error("social.$name no esperado")
        }
    }
    private val trips = fake<TripRepository> { name, _ ->
        when (name) {
            "getAll" -> ApiResult.Success(emptyList<Any>())
            else -> error("trips.$name no esperado")
        }
    }
    private val weatherApi = object : WeatherApi {
        override suspend fun geocode(name: String) = null
        override suspend fun forecast(coords: Coordinates) = RawWeather(18.0, 50, 0, 3.0)
    }
    private val location = object : DeviceLocationProvider {
        override fun hasPermission() = false
        override suspend fun current(): Coordinates? = null
    }
    private val cycle = object : CycleInfo {
        override val changes = flowOf(0)
        override fun isInCycle(userId: String?, date: LocalDate) = date in LocalDate.of(2026, 10, 13)..LocalDate.of(2026, 10, 15)
        override fun messageFor(userId: String?, date: LocalDate) = if (isInCycle(userId, date)) "Respira, brilla a tu ritmo" else null
    }

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createVm(): HomeViewModel {
        vm = HomeViewModel(
            auth, garmentRepo, lookRepo, planner, gamification, social, trips, cycle,
            WeatherRepository(weatherApi), location, clock, dispatcher,
        )
        return vm
    }

    @Test fun todayIsTheLocalDayNotUtc() = runTest {
        createVm()
        val s = vm.state.value
        assertEquals("2026-10-13", s.todayIso)
        val week = (s.week as Section.Ready).value
        assertEquals("2026-10-13", week.single { it.info.isToday }.info.iso)
        assertEquals("2026-10-12", week.first().info.iso)
        assertEquals("Lucía", s.name)
    }

    @Test fun cycleDropsOnlyWhenFemaleWithTrackingEnabled() = runTest {
        createVm()
        val week = (vm.state.value.week as Section.Ready).value
        assertEquals(listOf(false, true, true, true, false, false, false), week.map { it.isCycle })
        assertEquals("Respira, brilla a tu ritmo", vm.state.value.cycleMessage)

        user.value = user.value!!.copy(cycleTracking = false)
        assertFalse(vm.state.value.showCycle)
        assertTrue((vm.state.value.week as Section.Ready).value.none { it.isCycle })
        assertNull(vm.state.value.cycleMessage)

        user.value = user.value!!.copy(cycleTracking = true, gender = "male")
        assertFalse(vm.state.value.showCycle)
        assertTrue((vm.state.value.week as Section.Ready).value.none { it.isCycle })
    }

    @Test fun levelUsesServerProgressAndDailyLoginRunsOnce() = runTest {
        createVm()
        val level = (vm.state.value.level as Section.Ready).value
        assertEquals(2, level.progress.level)
        assertEquals(150, level.progress.current)
        assertEquals(200, level.progress.needed)
        assertEquals("Principiante", level.title)
        assertEquals(3, level.streak)
        vm.refresh()
        vm.refresh()
        assertEquals(1, dailyLoginCalls)
        assertTrue(progressCalls >= 3)
    }

    @Test fun levelFallsBackToSessionDataWhenProgressFails() = runTest {
        failProgress = true
        user.value = user.value!!.copy(experiencePoints = 40, level = 1)
        createVm()
        val level = (vm.state.value.level as Section.Ready).value
        assertEquals(40, level.progress.current)
    }

    @Test fun moodIsOptimisticAndConfirmed() = runTest {
        createVm()
        vm.selectMood("elegant")
        assertEquals("elegant", moodSeenDuringRequest)
        assertEquals("elegant", vm.state.value.mood)
        assertFalse(vm.state.value.moodSaving)
    }

    @Test fun moodRollsBackWhenServerFails() = runTest {
        createVm()
        profileResult = fail("No se pudo guardar")
        vm.selectMood("elegant")
        assertEquals("elegant", moodSeenDuringRequest)
        assertEquals("casual", vm.state.value.mood)
        val event = kotlinx.coroutines.withTimeout(1000) { vm.eventFlow.first { it is HomeEvent.Message && it.error } }
        assertEquals("No se pudo guardar", (event as HomeEvent.Message).text)
    }

    @Test fun proposalAppearsAndNextProposalChangesIt() = runTest {
        createVm()
        val reco = vm.state.value.reco
        assertTrue(reco is RecoUi.Ready)
        val first = (reco as RecoUi.Ready).outfit.key
        assertEquals(3, reco.outfit.garments.size)
        assertEquals(1, reco.position)
        assertNotNull(first)
    }

    @Test fun planTodaySavesLookThenPlansWithLocalDate() = runTest {
        createVm()
        vm.planToday()
        assertEquals(listOf(listOf("t1", "b1", "s1").sorted()), createdLooks.map { it.sorted() })
        assertEquals(1, plannerSaves.size)
        assertEquals("2026-10-13", plannerSaves.single().first)
        assertEquals("L1", plannerSaves.single().second)
        val s = vm.state.value
        assertEquals("L1", s.todayLook?.id)
        val ready = s.reco as RecoUi.Ready
        assertTrue(ready.plannedToday)
        assertEquals("L1", ready.savedLookId)
    }

    @Test fun savingTwiceDoesNotDuplicateTheLook() = runTest {
        createVm()
        vm.saveLook()
        vm.saveLook()
        vm.planToday()
        assertEquals(1, createdLooks.size)
        assertEquals("L1", plannerSaves.single().second)
    }

    @Test fun replacingAnExistingPlanAsksForConfirmation() = runTest {
        plannerEntries = listOf(PlannerEntry("2026-10-13", "OLD", look("OLD", "Look viejo")))
        createVm()
        vm.planToday()
        assertEquals("Look viejo", vm.state.value.confirmReplace)
        assertTrue(plannerSaves.isEmpty())
        vm.planToday(confirmed = true)
        assertEquals(1, plannerSaves.size)
        assertNull(vm.state.value.confirmReplace)
    }
}
