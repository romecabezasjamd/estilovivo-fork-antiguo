package com.estilovivo.app.feature.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.model.Garment
import com.estilovivo.app.core.data.model.GamificationProgress
import com.estilovivo.app.core.data.model.Look
import com.estilovivo.app.core.data.model.PlannerEntry
import com.estilovivo.app.core.data.model.ProfileUpdate
import com.estilovivo.app.core.data.model.User
import com.estilovivo.app.core.data.repo.AuthRepository
import com.estilovivo.app.core.data.repo.GamificationRepository
import com.estilovivo.app.core.data.repo.GarmentRepository
import com.estilovivo.app.core.data.repo.LookRepository
import com.estilovivo.app.core.data.repo.PlannerRepository
import com.estilovivo.app.core.data.repo.SocialRepository
import com.estilovivo.app.core.data.repo.TripRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random

/** Seguimiento del ciclo (adaptador de `CycleStore` para poder probar sin Android). */
interface CycleInfo {
    val changes: Flow<Int>
    fun isInCycle(userId: String?, date: LocalDate): Boolean
    fun messageFor(userId: String?, date: LocalDate): String?
}

@Immutable
sealed interface Section<out T> {
    data object Loading : Section<Nothing>
    data class Ready<T>(val value: T) : Section<T>
    data class Failed(val message: String) : Section<Nothing>
}

@Immutable
data class LevelUi(val progress: LevelMath.Progress, val title: String, val totalXp: Int, val streak: Int, val hasXp: Boolean)

@Immutable
data class WeekDayUi(
    val info: WeekDayInfo,
    val look: Look?,
    val hasEntry: Boolean,
    val isCycle: Boolean,
) {
    val image: String? get() = look?.imageUrl ?: look?.garments?.firstOrNull()?.imageUrl
}

@Immutable
data class WeatherUi(
    val section: Section<WeatherData>,
    val tips: List<OutfitTip> = emptyList(),
    val picks: List<Garment> = emptyList(),
    val advice: String? = null,
    val canAskLocation: Boolean = false,
)

@Immutable
sealed interface RecoUi {
    data object Loading : RecoUi
    data object NotEnoughGarments : RecoUi
    data class NoCombination(val message: String) : RecoUi
    data class Failed(val message: String) : RecoUi
    data class Ready(
        val outfit: Outfit,
        val position: Int,
        val total: Int,
        val savedLookId: String?,
        val saving: Boolean,
        val planning: Boolean,
        val plannedToday: Boolean,
        val shuffleTick: Int,
    ) : RecoUi
}

@Immutable
data class QuickCounts(val garments: Int, val looks: Int, val favorites: Int?, val trips: Int?)

@Immutable
data class HomeUiState(
    val greeting: String = "",
    val todayIso: String = "",
    val name: String = "",
    val gender: String? = null,
    val mood: String? = null,
    val moodSaving: Boolean = false,
    val level: Section<LevelUi> = Section.Loading,
    val showCycle: Boolean = false,
    val cycleMessage: String? = null,
    val cycleToday: Boolean = false,
    val weather: WeatherUi = WeatherUi(Section.Loading),
    val week: Section<List<WeekDayUi>> = Section.Loading,
    val todayLook: Look? = null,
    val todayNote: String? = null,
    val reco: RecoUi = RecoUi.Loading,
    val counts: QuickCounts = QuickCounts(0, 0, null, null),
    val countsReady: Boolean = false,
    val refreshing: Boolean = false,
    /** Look que ya ocupa hoy y se sustituiría al planear (pide confirmación). */
    val confirmReplace: String? = null,
)

sealed interface HomeEvent {
    data class Message(val text: String, val error: Boolean = false) : HomeEvent
    data object Celebrate : HomeEvent
}

class HomeViewModel(
    private val auth: AuthRepository,
    private val garmentRepo: GarmentRepository,
    private val lookRepo: LookRepository,
    private val planner: PlannerRepository,
    private val gamification: GamificationRepository,
    private val social: SocialRepository,
    private val trips: TripRepository,
    private val cycle: CycleInfo,
    private val weatherRepo: WeatherRepository,
    private val locationProvider: DeviceLocationProvider,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private sealed interface Status {
        data object Loading : Status
        data object Ready : Status
        data class Failed(val message: String) : Status
    }

    private data class RecoCore(val result: RecommendResult? = null, val index: Int = 0, val seed: Int = 0, val tick: Int = 0)

    private data class Raw(
        val user: User? = null,
        val now: LocalDateTime,
        val garments: List<Garment> = emptyList(),
        val garmentsStatus: Status = Status.Loading,
        val looks: List<Look> = emptyList(),
        val entries: List<PlannerEntry> = emptyList(),
        val plannerStatus: Status = Status.Loading,
        val progress: GamificationProgress? = null,
        val progressStatus: Status = Status.Loading,
        val favorites: Int? = null,
        val trips: Int? = null,
        val weather: WeatherData? = null,
        val weatherStatus: Status = Status.Loading,
        val locationGranted: Boolean = false,
        val locationDenied: Boolean = false,
        val pendingMood: String? = null,
        val reco: RecoCore = RecoCore(),
        val savedLookByKey: Map<String, String> = emptyMap(),
        val saving: Boolean = false,
        val planning: Boolean = false,
        val refreshing: Boolean = false,
        val weatherGrace: Boolean = true,
        val cycleVersion: Int = 0,
        val confirmReplace: String? = null,
    )

    private val raw = MutableStateFlow(Raw(now = LocalDateTime.now(clock)))
    private val events = Channel<HomeEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    val state: StateFlow<HomeUiState> = raw
        .map { it.toUi() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, raw.value.toUi())

    private val moodMutex = Mutex()
    private var lastKnownLevel: Int? = null
    private var dailyLoginDone = false
    private var lastRefreshAt = 0L
    private var lastShownKey: String? = null
    private var weatherJob: Job? = null

    init {
        raw.update { it.copy(user = auth.currentUser.value, garments = garmentRepo.garments.value, looks = lookRepo.myLooks.value) }
        viewModelScope.launch { auth.currentUser.collect { u -> raw.update { it.copy(user = u, pendingMood = if (u?.mood == it.pendingMood) null else it.pendingMood) } } }
        viewModelScope.launch { garmentRepo.garments.collect { g -> raw.update { it.copy(garments = g) } } }
        viewModelScope.launch { lookRepo.myLooks.collect { l -> raw.update { it.copy(looks = l) } } }
        viewModelScope.launch { cycle.changes.collect { raw.update { r -> r.copy(cycleVersion = r.cycleVersion + 1) } } }
        // Si cambia la ciudad del perfil, se recarga el tiempo.
        viewModelScope.launch {
            auth.currentUser.map { it?.locationName?.trim().orEmpty() }.distinctUntilChanged().drop(1).collect { loadWeather(force = false) }
        }
        viewModelScope.launch { delay(WEATHER_GRACE_MS); raw.update { it.copy(weatherGrace = false) } }
        observeRecommendations()
        viewModelScope.launch { loadAll(force = false) }
    }

    // ───────────────────────── Carga ─────────────────────────

    fun refresh() {
        viewModelScope.launch {
            raw.update { it.copy(refreshing = true, now = LocalDateTime.now(clock)) }
            try { loadAll(force = true) } finally { raw.update { it.copy(refreshing = false) } }
        }
    }

    /** Al volver a la pantalla: recalcula "hoy" (el día puede haber cambiado) y refresca en silencio si hace falta. */
    fun onResume() {
        val now = LocalDateTime.now(clock)
        raw.update { it.copy(now = now) }
        val elapsed = clock.millis() - lastRefreshAt
        if (elapsed > RESUME_REFRESH_MS) {
            viewModelScope.launch {
                lastRefreshAt = clock.millis()
                coroutineScope {
                    launch { loadGarments() }
                    launch { loadPlanner() }
                    launch { loadExtras() }
                    launch { loadWeather(force = false) }
                }
            }
        }
    }

    private suspend fun loadAll(force: Boolean) {
        lastRefreshAt = clock.millis()
        coroutineScope {
            launch { loadProgress() }
            launch { loadGarments() }
            launch { loadPlanner() }
            launch { loadExtras() }
            launch { loadWeather(force) }
        }
    }

    fun retryProgress() { viewModelScope.launch { raw.update { it.copy(progressStatus = Status.Loading) }; loadProgress() } }
    fun retryPlanner() { viewModelScope.launch { raw.update { it.copy(plannerStatus = Status.Loading) }; loadPlanner() } }
    fun retryGarments() { viewModelScope.launch { raw.update { it.copy(garmentsStatus = Status.Loading) }; loadGarments() } }
    fun retryWeather() { viewModelScope.launch { raw.update { it.copy(weatherStatus = Status.Loading) }; loadWeather(force = true) } }

    private suspend fun loadProgress() {
        if (!dailyLoginDone && auth.currentUser.value != null) {
            dailyLoginDone = true
            when (val login = gamification.dailyLogin()) {
                is ApiResult.Success -> {
                    val r = login.value
                    val parts = buildList {
                        if (r.streakCount > 1) add("Racha de ${r.streakCount} días")
                        if (r.xpAwarded > 0) add("+${r.xpAwarded} XP")
                    }
                    if (r.newBadges.isNotEmpty()) {
                        events.trySend(HomeEvent.Message("Nueva insignia: ${r.newBadges.joinToString { it.title }}"))
                        events.trySend(HomeEvent.Celebrate)
                    } else if (parts.isNotEmpty()) {
                        events.trySend(HomeEvent.Message(parts.joinToString(" · ")))
                    }
                }
                is ApiResult.Failure -> Unit // el inicio de sesión diario no es crítico
            }
        }
        when (val res = gamification.getProgress()) {
            is ApiResult.Success -> {
                val p = res.value
                val previous = lastKnownLevel
                lastKnownLevel = p.level
                raw.update { it.copy(progress = p, progressStatus = Status.Ready) }
                if (previous != null && p.level > previous) {
                    events.trySend(HomeEvent.Message("¡Subes al nivel ${p.level}! ${LevelMath.title(p.level)}"))
                    events.trySend(HomeEvent.Celebrate)
                }
            }
            is ApiResult.Failure -> raw.update {
                // Con datos previos (o los del perfil) la tarjeta sigue útil: sólo se marca error si no hay nada.
                it.copy(progressStatus = Status.Failed(res.error.message))
            }
        }
    }

    private suspend fun loadGarments() {
        when (val res = garmentRepo.getAll()) {
            is ApiResult.Success -> raw.update { it.copy(garments = res.value, garmentsStatus = Status.Ready) }
            is ApiResult.Failure -> raw.update {
                it.copy(garmentsStatus = if (it.garments.isNotEmpty()) Status.Ready else Status.Failed(res.error.message))
            }
        }
    }

    private suspend fun loadPlanner() {
        coroutineScope {
            val looksJob = async { lookRepo.getMine() }
            when (val res = planner.getAll()) {
                is ApiResult.Success -> raw.update { it.copy(entries = res.value, plannerStatus = Status.Ready) }
                is ApiResult.Failure -> raw.update {
                    it.copy(plannerStatus = if (it.entries.isNotEmpty()) Status.Ready else Status.Failed(res.error.message))
                }
            }
            looksJob.await() // alimenta la caché `myLooks` (contador y búsqueda de looks)
        }
    }

    private suspend fun loadExtras() {
        coroutineScope {
            val fav = async { social.getFavorites() }
            val tr = async { trips.getAll() }
            fav.await().let { r -> if (r is ApiResult.Success) raw.update { it.copy(favorites = r.value.size) } }
            tr.await().let { r -> if (r is ApiResult.Success) raw.update { it.copy(trips = r.value.size) } }
        }
    }

    private suspend fun loadWeather(force: Boolean) {
        val job = viewModelScope.launch {
            val user = raw.value.user
            val city = user?.locationName?.trim().orEmpty()
            val location = when {
                city.isNotEmpty() -> WeatherLocation.City(city)
                raw.value.locationGranted -> locationProvider.current()?.let { WeatherLocation.Device(it) } ?: WeatherLocation.Default
                else -> WeatherLocation.Default
            }
            val data = weatherRepo.load(location, force)
            raw.update {
                if (data != null) it.copy(weather = data, weatherStatus = Status.Ready)
                else it.copy(weatherStatus = if (it.weather != null) Status.Ready else Status.Failed("Sin datos del tiempo"))
            }
        }
        weatherJob?.cancel()
        weatherJob = job
        job.join()
    }

    // ───────────────────────── Ubicación ─────────────────────────

    fun onLocationPermission(granted: Boolean, userAction: Boolean) {
        val changed = raw.value.locationGranted != granted
        raw.update { it.copy(locationGranted = granted, locationDenied = if (userAction && !granted) true else it.locationDenied && !granted) }
        if (userAction && !granted) {
            events.trySend(HomeEvent.Message("Sin permiso de ubicación. Usamos Madrid; puedes fijar tu ciudad en el perfil.", error = false))
        }
        if (changed && granted) viewModelScope.launch { loadWeather(force = true) }
    }

    // ───────────────────────── Mood ─────────────────────────

    fun selectMood(moodId: String) {
        val current = raw.value.pendingMood ?: raw.value.user?.mood
        if (current == moodId) return
        raw.update { it.copy(pendingMood = moodId) }
        viewModelScope.launch {
            moodMutex.withLock {
                when (val res = auth.updateProfile(ProfileUpdate(mood = moodId))) {
                    is ApiResult.Success -> raw.update { if (it.pendingMood == moodId) it.copy(pendingMood = null) else it }
                    is ApiResult.Failure -> {
                        // Rollback: vuelve al mood confirmado por el servidor.
                        raw.update { if (it.pendingMood == moodId) it.copy(pendingMood = null) else it }
                        events.trySend(HomeEvent.Message(res.error.message, error = true))
                    }
                }
            }
        }
    }

    // ───────────────────────── Recomendador ─────────────────────────

    private data class RecoKey(
        val ids: List<String>, val mood: String?, val temp: Int?, val condition: WeatherCondition?,
        val day: LocalDate, val seed: Int, val ready: Boolean,
    )

    private fun observeRecommendations() {
        viewModelScope.launch {
            raw.map { r ->
                RecoKey(
                    ids = r.garments.filter { !it.isWashing && !it.forSale }.map { it.id },
                    mood = r.pendingMood ?: r.user?.mood,
                    temp = r.weather?.temp, condition = r.weather?.condition,
                    day = r.now.toLocalDate(), seed = r.reco.seed,
                    ready = r.garmentsStatus != Status.Loading && (r.weatherStatus != Status.Loading || !r.weatherGrace),
                )
            }.distinctUntilChanged().collectLatest { key ->
                if (!key.ready) return@collectLatest
                val snapshot = raw.value
                val input = RecommenderInput(snapshot.garments, key.mood, key.temp, key.condition, key.day)
                val seed = key.day.toEpochDay() * 1009L + key.seed * 7919L + (key.mood?.hashCode() ?: 0)
                val result = withContext(computeDispatcher) { OutfitRecommender.recommend(input, Random(seed)) }
                val index = if (result is RecommendResult.Ready && result.outfits.size > 1 && key.seed > 0 &&
                    result.outfits[0].key == lastShownKey
                ) 1 else 0
                raw.update { r -> r.copy(reco = r.reco.copy(result = result, index = index)) }
                lastShownKey = (result as? RecommendResult.Ready)?.outfits?.getOrNull(index)?.key
            }
        }
    }

    fun nextProposal() {
        val reco = raw.value.reco
        val result = reco.result as? RecommendResult.Ready ?: return
        nextProposalInternal(reco, result)
    }

    private fun nextProposalInternal(reco: RecoCore, result: RecommendResult.Ready) {
        lastShownKey = result.outfits.getOrNull(reco.index)?.key
        if (reco.index + 1 < result.outfits.size) {
            raw.update { it.copy(reco = it.reco.copy(index = it.reco.index + 1, tick = it.reco.tick + 1)) }
        } else {
            // Se agotaron: nueva semilla => nuevo barajado.
            raw.update { it.copy(reco = it.reco.copy(seed = it.reco.seed + 1, tick = it.reco.tick + 1)) }
        }
    }

    private fun currentOutfit(): Outfit? {
        val reco = raw.value.reco
        return (reco.result as? RecommendResult.Ready)?.outfits?.getOrNull(reco.index)
    }

    private suspend fun ensureLookSaved(outfit: Outfit): Look? {
        val existing = raw.value.savedLookByKey[outfit.key]
        if (existing != null) {
            raw.value.looks.firstOrNull { it.id == existing }?.let { return it }
        }
        val mood = raw.value.pendingMood ?: raw.value.user?.mood
        val name = "Look del ${raw.value.now.toLocalDate().format(DateTimeFormatter.ofPattern("d MMM", Locale("es", "ES")))}"
        return when (val res = lookRepo.create(name, outfit.garments.map { it.id }, false, mood, listOf("hoy"))) {
            is ApiResult.Success -> {
                raw.update { it.copy(savedLookByKey = it.savedLookByKey + (outfit.key to res.value.id)) }
                res.value
            }
            is ApiResult.Failure -> {
                events.trySend(HomeEvent.Message(res.error.message, error = true))
                null
            }
        }
    }

    fun saveLook() {
        val outfit = currentOutfit() ?: return
        if (raw.value.saving || raw.value.planning || raw.value.savedLookByKey.containsKey(outfit.key)) return
        raw.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                if (ensureLookSaved(outfit) != null) events.trySend(HomeEvent.Message("Look guardado en tus looks"))
            } finally {
                raw.update { it.copy(saving = false) }
            }
        }
    }

    fun planToday(confirmed: Boolean = false) {
        val outfit = currentOutfit() ?: return
        if (raw.value.saving || raw.value.planning) return
        val todayIso = raw.value.now.toLocalDate().format(WeekMath.ISO)
        val existing = raw.value.entries.firstOrNull { WeekMath.normalizeDate(it.date) == todayIso }
        val savedId = raw.value.savedLookByKey[outfit.key]
        if (existing?.lookId != null && existing.lookId != savedId && !confirmed) {
            val name = existing.look?.name ?: raw.value.looks.firstOrNull { it.id == existing.lookId }?.name ?: "otro look"
            raw.update { it.copy(confirmReplace = name) }
            return
        }
        raw.update { it.copy(planning = true, confirmReplace = null) }
        viewModelScope.launch {
            try {
                val look = ensureLookSaved(outfit) ?: return@launch
                when (val res = planner.save(todayIso, look.id, existing?.eventNote.orEmpty())) {
                    is ApiResult.Success -> {
                        val saved = res.value.let { e -> if (e.look == null) e.copy(look = look) else e }
                        raw.update { r ->
                            r.copy(entries = r.entries.filterNot { WeekMath.normalizeDate(it.date) == todayIso } + saved)
                        }
                        events.trySend(HomeEvent.Message("Look planeado para hoy"))
                        events.trySend(HomeEvent.Celebrate)
                    }
                    is ApiResult.Failure -> events.trySend(
                        HomeEvent.Message("Look guardado, pero no se pudo planear: ${res.error.message}", error = true),
                    )
                }
            } finally {
                raw.update { it.copy(planning = false) }
            }
        }
    }

    fun dismissReplace() { raw.update { it.copy(confirmReplace = null) } }

    // ───────────────────────── Derivación de UI ─────────────────────────

    private fun Raw.toUi(): HomeUiState {
        val today = now.toLocalDate()
        val todayIso = today.format(WeekMath.ISO)
        val gender = user?.gender
        val mood = pendingMood ?: user?.mood
        val female = gender == "female" && user?.cycleTracking == true
        val userId = user?.id
        @Suppress("UNUSED_VARIABLE") val ignore = cycleVersion

        // Nivel: del servidor; si falla, con los datos de la sesión.
        val levelSection: Section<LevelUi> = when {
            progress != null -> Section.Ready(levelUi(progress.experiencePoints, progress.level, progress.streak.loginCount))
            progressStatus is Status.Failed && user != null ->
                Section.Ready(levelUi(user.experiencePoints, user.level.coerceAtLeast(1), 0))
            progressStatus is Status.Failed -> Section.Failed(progressStatus.message)
            else -> Section.Loading
        }

        val lookById = looks.associateBy { it.id }
        fun entryLook(e: PlannerEntry): Look? = e.look ?: e.lookId?.let(lookById::get)
        val byDate = entries.associateBy { WeekMath.normalizeDate(it.date) }
        val weekSection: Section<List<WeekDayUi>> = when {
            plannerStatus is Status.Failed -> Section.Failed(plannerStatus.message)
            plannerStatus is Status.Loading -> Section.Loading
            else -> Section.Ready(
                WeekMath.weekOf(today).map { d ->
                    val e = byDate[d.iso]
                    WeekDayUi(d, e?.let(::entryLook), e != null, female && cycle.isInCycle(userId, d.date))
                },
            )
        }
        val todayEntry = byDate[todayIso]
        val todayLook = todayEntry?.let(::entryLook)

        val weatherUi = run {
            val section: Section<WeatherData> = when {
                weather != null -> Section.Ready(weather)
                weatherStatus is Status.Failed -> Section.Failed(weatherStatus.message)
                else -> Section.Loading
            }
            if (weather == null) WeatherUi(section, canAskLocation = false)
            else WeatherUi(
                section = section,
                tips = WeatherLogic.essentialTips(weather.temp, weather.condition),
                picks = OutfitRecommender.moodPicks(RecommenderInput(garments, mood, weather.temp, weather.condition, today), 6),
                advice = WeatherLogic.advice(weather.temp),
                canAskLocation = false,
            )
        }.let { w ->
            w.copy(canAskLocation = user?.locationName.isNullOrBlank() && !locationGranted && !locationDenied)
        }

        val recoUi: RecoUi = when {
            garmentsStatus is Status.Failed && garments.isEmpty() -> RecoUi.Failed(garmentsStatus.message)
            garmentsStatus is Status.Loading && garments.isEmpty() -> RecoUi.Loading
            reco.result == null -> if (garments.size < 2 && garmentsStatus == Status.Ready) RecoUi.NotEnoughGarments else RecoUi.Loading
            else -> when (val r = reco.result) {
                RecommendResult.NotEnoughGarments -> RecoUi.NotEnoughGarments
                is RecommendResult.NoCombination -> RecoUi.NoCombination(r.message)
                is RecommendResult.Ready -> {
                    val outfit = r.outfits[reco.index.coerceIn(0, r.outfits.lastIndex)]
                    val saved = savedLookByKey[outfit.key]
                    RecoUi.Ready(
                        outfit = outfit, position = reco.index.coerceIn(0, r.outfits.lastIndex) + 1, total = r.outfits.size,
                        savedLookId = saved, saving = saving, planning = planning,
                        plannedToday = saved != null && todayEntry?.lookId == saved, shuffleTick = reco.tick,
                    )
                }
            }
        }

        return HomeUiState(
            greeting = Greeting.forHour(now.hour),
            todayIso = todayIso,
            name = user?.name?.trim()?.split(' ')?.firstOrNull().orEmpty(),
            gender = gender,
            mood = mood,
            moodSaving = pendingMood != null,
            level = levelSection,
            showCycle = female,
            cycleMessage = if (female) cycle.messageFor(userId, today) else null,
            cycleToday = female && cycle.isInCycle(userId, today),
            weather = weatherUi,
            week = weekSection,
            todayLook = todayLook,
            todayNote = todayEntry?.eventNote?.takeIf { it.isNotBlank() },
            reco = recoUi,
            counts = QuickCounts(garments.size, looks.size, favorites, trips),
            countsReady = garmentsStatus == Status.Ready,
            refreshing = refreshing,
            confirmReplace = confirmReplace,
        )
    }

    private fun levelUi(xp: Int, level: Int, streak: Int): LevelUi {
        val p = LevelMath.progress(xp, level)
        return LevelUi(p, LevelMath.title(level), xp, streak, xp > 0)
    }

    companion object {
        private const val RESUME_REFRESH_MS = 45_000L
        private const val WEATHER_GRACE_MS = 2_500L
    }
}
