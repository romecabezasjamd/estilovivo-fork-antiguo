package com.estilovivo.app.feature.profile

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.model.Achievement
import com.estilovivo.app.core.data.model.Badge
import com.estilovivo.app.core.data.model.Garment
import com.estilovivo.app.core.data.model.GamificationProgress
import com.estilovivo.app.core.data.model.Stats
import com.estilovivo.app.core.data.model.User
import com.estilovivo.app.core.data.repo.AuthRepository
import com.estilovivo.app.core.data.repo.GamificationRepository
import com.estilovivo.app.core.data.repo.GarmentRepository
import com.estilovivo.app.core.data.repo.SocialRepository
import com.estilovivo.app.core.data.repo.StatsRepository
import com.estilovivo.app.core.data.socket.SocketEvent
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ProfileSection(val label: String) { Stats("Estadísticas"), Progress("Progreso"), Favorites("Favoritos"), Settings("Ajustes") }

/** Estado de carga de una sección. */
sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>
    @Immutable data class Error(val message: String) : Loadable<Nothing>
    @Immutable data class Ready<T>(val value: T) : Loadable<T>
}

@Immutable
data class DonutSlice(val key: String, val label: String, val value: Int)

@Immutable
data class StatsUi(
    val stats: Stats,
    val categories: List<DonutSlice>,
    val seasons: List<DonutSlice>,
    val averageUsage: Int,
    val recommendations: List<String>,
)

@Immutable
data class ProgressUi(
    val progress: GamificationProgress,
    val level: LevelProgress,
    val achievements: List<Achievement>,
    val badges: List<Badge>,
)

@Immutable
data class FavoritesUi(val looks: Int, val garments: Int, val previews: List<String>)

@Immutable
data class ProfileUiState(
    val user: User? = null,
    val section: ProfileSection = ProfileSection.Stats,
    val level: LevelProgress = ProfileRules.progress(0),
    val stats: Loadable<StatsUi> = Loadable.Loading,
    val progress: Loadable<ProgressUi> = Loadable.Loading,
    val favorites: Loadable<FavoritesUi> = Loadable.Loading,
    /** Sube cada vez que hay que lanzar confeti. */
    val confetti: Int = 0,
    /** Logros recién conseguidos pendientes de enseñar (se muestran de uno en uno). */
    val celebrate: List<Achievement> = emptyList(),
    val levelUp: Int? = null,
)

/** Textos de sugerencias a partir de las estadísticas (puro, probado en JVM). */
object StatsInsights {
    fun recommendations(stats: Stats, categories: List<DonutSlice>): List<String> = buildList {
        if (stats.garmentCount == 0) {
            add("Sube tu primera prenda para empezar a ver tus estadísticas.")
            return@buildList
        }
        if (stats.leastWornCount > 0) {
            val n = stats.leastWornCount
            add(if (n == 1) "Tienes 1 prenda que casi no usas: dale una oportunidad en un look." else "Tienes $n prendas que casi no usas: dales una oportunidad en un look.")
        }
        if (stats.lookCount == 0 && stats.garmentCount >= 2) add("Aún no has creado ningún look: combina tus prendas y guárdalo.")
        if (stats.leastWornCount >= 3 && stats.salesCount == 0) add("Si ya no las llevas, ponlas a la venta y dales una segunda vida.")
        val total = categories.sumOf { it.value }
        val top = categories.maxByOrNull { it.value }
        if (top != null && total >= 5 && top.value * 100 / total >= 60) {
            add("Más de la mitad de tu armario son ${top.label.lowercase()}: equilibra con otras categorías para crear más looks.")
        }
        if (stats.totalUsage == 0) add("Marca como puestas las prendas que uses para que tus estadísticas sean más precisas.")
    }

    fun slices(garments: List<Garment>): Pair<List<DonutSlice>, List<DonutSlice>> {
        val cats = garments.groupingBy { it.type }.eachCount().entries
            .sortedByDescending { it.value }
            .map { DonutSlice(it.key, ProfileRules.categoryLabel(it.key), it.value) }
        val seasons = garments.groupingBy { if (it.season in listOf("summer", "winter", "spring", "autumn", "transition")) it.season else "all" }
            .eachCount().entries.sortedByDescending { it.value }
            .map { DonutSlice(it.key, ProfileRules.seasonLabel(it.key), it.value) }
        return cats to seasons
    }
}

class ProfileViewModel(
    private val auth: AuthRepository,
    private val statsRepo: StatsRepository,
    private val garments: GarmentRepository,
    private val gamification: GamificationRepository,
    private val social: SocialRepository,
    socketEvents: Flow<SocketEvent>,
    private val saved: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private data class Aux(
        val section: ProfileSection,
        val stats: Loadable<StatsUi> = Loadable.Loading,
        val progress: Loadable<ProgressUi> = Loadable.Loading,
        val favorites: Loadable<FavoritesUi> = Loadable.Loading,
        val confetti: Int = 0,
        val celebrate: List<Achievement> = emptyList(),
        val levelUp: Int? = null,
    )

    private val aux = MutableStateFlow(
        Aux(section = saved.get<String>(KEY_SECTION)?.let { n -> ProfileSection.entries.firstOrNull { it.name == n } } ?: ProfileSection.Stats),
    )
    private val loaded = mutableSetOf<ProfileSection>()
    private val _events = Channel<ProfileEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val state: StateFlow<ProfileUiState> = combine(auth.currentUser, aux) { user, a ->
        ProfileUiState(
            user = user, section = a.section, level = ProfileRules.progress(user?.experiencePoints ?: 0),
            stats = a.stats, progress = a.progress, favorites = a.favorites,
            confetti = a.confetti, celebrate = a.celebrate, levelUp = a.levelUp,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ProfileUiState(section = aux.value.section))

    init {
        refreshUser()
        load(aux.value.section)
        viewModelScope.launch {
            socketEvents.collect { e ->
                when (e) {
                    is SocketEvent.LevelUp -> {
                        val lvl = e.level ?: ProfileRules.progress(e.experiencePoints ?: 0).level
                        aux.update { it.copy(confetti = it.confetti + 1, levelUp = lvl) }
                        refreshUser(); reloadProgress()
                    }
                    is SocketEvent.AchievementUnlocked -> {
                        val title = e.title ?: "Nuevo logro"
                        aux.update { it.copy(confetti = it.confetti + 1) }
                        _events.send(ProfileEvent.Success("¡Logro desbloqueado: $title!"))
                        refreshUser(); reloadProgress()
                    }
                    else -> Unit
                }
            }
        }
    }

    fun refreshUser() {
        viewModelScope.launch { auth.refreshMe() }
    }

    fun selectSection(section: ProfileSection) {
        if (aux.value.section == section) return
        saved[KEY_SECTION] = section.name
        aux.update { it.copy(section = section) }
        load(section)
    }

    /** Recarga la sección visible (reintento o "tirar para actualizar"). */
    fun reload() {
        loaded.remove(aux.value.section)
        load(aux.value.section, force = true)
    }

    fun dismissCelebration() = aux.update { it.copy(celebrate = it.celebrate.drop(1)) }
    fun dismissLevelUp() = aux.update { it.copy(levelUp = null) }

    private fun load(section: ProfileSection, force: Boolean = false) {
        if (section == ProfileSection.Settings) return
        if (!loaded.add(section) && !force) return
        when (section) {
            ProfileSection.Stats -> loadStats()
            ProfileSection.Progress -> loadProgress(check = true)
            ProfileSection.Favorites -> loadFavorites()
            ProfileSection.Settings -> Unit
        }
    }

    private fun loadStats() {
        // Si ya había datos se conservan mientras se refresca; si no, esqueleto.
        if (aux.value.stats !is Loadable.Ready) aux.update { it.copy(stats = Loadable.Loading) }
        viewModelScope.launch {
            val (s, g) = coroutineScope {
                val a = async { statsRepo.getStats() }
                val b = async { garments.getAll() }
                a.await() to b.await()
            }
            when (s) {
                is ApiResult.Failure -> {
                    loaded.remove(ProfileSection.Stats)
                    if (aux.value.stats !is Loadable.Ready) aux.update { it.copy(stats = Loadable.Error(s.error.message)) }
                    else _events.send(ProfileEvent.Error(s.error.message))
                }
                is ApiResult.Success -> {
                    val list = (g as? ApiResult.Success)?.value ?: garments.garments.value
                    val (cats, seasons) = StatsInsights.slices(list)
                    val avg = if (list.isEmpty()) 0 else Math.round(list.sumOf { it.usageCount }.toFloat() / list.size)
                    aux.update {
                        it.copy(stats = Loadable.Ready(StatsUi(s.value, cats, seasons, avg, StatsInsights.recommendations(s.value, cats))))
                    }
                }
            }
        }
    }

    private fun loadProgress(check: Boolean) {
        if (aux.value.progress !is Loadable.Ready) aux.update { it.copy(progress = Loadable.Loading) }
        viewModelScope.launch {
            val ui = fetchProgress()
            if (ui == null) {
                loaded.remove(ProfileSection.Progress)
                if (aux.value.progress !is Loadable.Ready) aux.update { it.copy(progress = Loadable.Error(lastProgressError)) }
                else _events.send(ProfileEvent.Error(lastProgressError))
                return@launch
            }
            aux.update { it.copy(progress = Loadable.Ready(ui)) }
            if (check) {
                val fresh = (gamification.checkAchievements() as? ApiResult.Success)?.value.orEmpty()
                if (fresh.isNotEmpty()) {
                    aux.update { it.copy(celebrate = it.celebrate + fresh, confetti = it.confetti + 1) }
                    refreshUser()
                    fetchProgress()?.let { updated -> aux.update { it.copy(progress = Loadable.Ready(updated)) } }
                }
            }
        }
    }

    private var lastProgressError = "No se pudo cargar tu progreso."

    private suspend fun fetchProgress(): ProgressUi? = coroutineScope {
        val p = async { gamification.getProgress() }
        val a = async { gamification.getAchievements() }
        val b = async { gamification.getBadges() }
        val progress = p.await()
        val achievements = a.await()
        val badges = b.await()
        when {
            progress is ApiResult.Failure -> { lastProgressError = progress.error.message; null }
            else -> {
                val pr = (progress as ApiResult.Success).value
                ProgressUi(
                    progress = pr,
                    level = ProfileRules.progress(pr.experiencePoints),
                    achievements = ((achievements as? ApiResult.Success)?.value ?: emptyList())
                        .sortedWith(compareByDescending<Achievement> { it.unlocked }.thenBy { it.xpReward }),
                    badges = ((badges as? ApiResult.Success)?.value ?: emptyList()).sortedByDescending { it.unlocked },
                )
            }
        }
    }

    private fun reloadProgress() {
        if (aux.value.progress is Loadable.Ready) viewModelScope.launch {
            fetchProgress()?.let { ui -> aux.update { it.copy(progress = Loadable.Ready(ui)) } }
        }
    }

    private fun loadFavorites() {
        if (aux.value.favorites !is Loadable.Ready) aux.update { it.copy(favorites = Loadable.Loading) }
        viewModelScope.launch {
            when (val r = social.getFavorites()) {
                is ApiResult.Success -> {
                    val favs = r.value
                    aux.update {
                        it.copy(
                            favorites = Loadable.Ready(
                                FavoritesUi(
                                    looks = favs.count { f -> f.look != null },
                                    garments = favs.count { f -> f.garment != null },
                                    previews = favs.mapNotNull { f -> f.look?.imageUrl ?: f.garment?.imageUrl }.filter { u -> u.isNotBlank() }.take(8),
                                ),
                            ),
                        )
                    }
                }
                is ApiResult.Failure -> {
                    loaded.remove(ProfileSection.Favorites)
                    if (aux.value.favorites !is Loadable.Ready) aux.update { it.copy(favorites = Loadable.Error(r.error.message)) }
                    else _events.send(ProfileEvent.Error(r.error.message))
                }
            }
        }
    }

    private companion object {
        const val KEY_SECTION = "section"
    }
}
