package com.estilovivo.app.feature.social.feed

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.ImageUpload
import com.estilovivo.app.core.data.model.Garment
import com.estilovivo.app.core.data.model.Story
import com.estilovivo.app.core.data.repo.GarmentRepository
import com.estilovivo.app.core.data.repo.StoryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Historias ya vistas en este dispositivo (los aros se apagan). Se guarda para sobrevivir a la muerte del proceso. */
interface ViewedStories {
    val ids: StateFlow<Set<String>>
    fun mark(id: String)
    /** Olvida ids de historias que ya no existen (caducan a las 24 h). */
    fun prune(valid: Set<String>)
}

class InMemoryViewedStories : ViewedStories {
    private val _ids = MutableStateFlow<Set<String>>(emptySet())
    override val ids: StateFlow<Set<String>> = _ids.asStateFlow()
    override fun mark(id: String) = _ids.update { it + id }
    override fun prune(valid: Set<String>) = _ids.update { cur -> cur.filterTo(HashSet()) { it in valid } }
}

class PrefsViewedStories(context: Context) : ViewedStories {
    private val prefs = context.applicationContext.getSharedPreferences("ev_social_viewed_stories", Context.MODE_PRIVATE)
    private val _ids = MutableStateFlow(prefs.getStringSet(KEY, emptySet()).orEmpty().toSet())
    override val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    override fun mark(id: String) {
        _ids.update { it + id }
        persist()
    }

    override fun prune(valid: Set<String>) {
        val before = _ids.value
        val after = before.filterTo(HashSet()) { it in valid }
        if (after.size != before.size) { _ids.value = after; persist() }
    }

    private fun persist() = prefs.edit().putStringSet(KEY, HashSet(_ids.value)).apply()

    private companion object { const val KEY = "ids" }
}

@Immutable
data class StoriesUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val stories: List<Story> = emptyList(),
    val creating: Boolean = false,
    val garments: List<Garment> = emptyList(),
)

/** Fila de aros de historias del feed y creación de historias. */
class StoriesViewModel(
    private val stories: StoryRepository,
    private val garments: GarmentRepository,
    private val viewed: ViewedStories,
    private val meProvider: MeProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(StoriesUiState())
    val state: StateFlow<StoriesUiState> = _state.asStateFlow()
    val viewedIds: StateFlow<Set<String>> get() = viewed.ids

    private val _events = Channel<FeedEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var loadJob: Job? = null

    init { load() }

    fun load(silent: Boolean = false) {
        loadJob?.cancel()
        if (!silent) _state.update { it.copy(loading = true, error = null) }
        loadJob = viewModelScope.launch {
            when (val r = stories.getStories()) {
                is ApiResult.Success -> {
                    viewed.prune(r.value.mapTo(HashSet()) { it.id })
                    _state.update { it.copy(loading = false, error = null, stories = r.value) }
                }
                // Una recarga silenciosa que falla no estropea lo que ya se ve
                is ApiResult.Failure -> _state.update { if (silent && it.stories.isNotEmpty()) it.copy(loading = false) else it.copy(loading = false, error = r.error.message) }
            }
        }
    }

    /** Prendas propias para crear una historia desde el armario. */
    fun loadGarments() {
        _state.update { it.copy(garments = garments.garments.value.filter { g -> g.imageUrl.isNotBlank() }.take(MAX_GARMENTS)) }
        viewModelScope.launch {
            if (garments.garments.value.isEmpty()) garments.getAll()
            _state.update { it.copy(garments = garments.garments.value.filter { g -> g.imageUrl.isNotBlank() }.take(MAX_GARMENTS)) }
        }
    }

    /**
     * Publica una historia. `type = "image"` necesita [image] o [existingImageUrl] (+ [text] opcional superpuesto);
     * `type = "text"` necesita [text].
     */
    fun create(type: String, text: String?, image: ImageUpload?, existingImageUrl: String? = null) {
        if (_state.value.creating) return
        val clean = text?.trim().orEmpty()
        when (type) {
            "text" -> if (clean.isEmpty()) { toast("Escribe una frase para tu historia.", true); return }
            "image" -> if (image == null && existingImageUrl.isNullOrBlank()) { toast("Elige una foto o una prenda para tu historia.", true); return }
            else -> return
        }
        if (clean.length > STORY_TEXT_MAX) { toast("El texto no puede superar los $STORY_TEXT_MAX caracteres.", true); return }
        _state.update { it.copy(creating = true) }
        viewModelScope.launch {
            when (val r = stories.create(type, clean.ifEmpty { null }, image, existingImageUrl)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(creating = false, stories = listOf(r.value) + it.stories.filterNot { s -> s.id == r.value.id }) }
                    _events.trySend(FeedEvent.Published)
                    _events.trySend(FeedEvent.Confetti)
                    toast("Tu historia ya está publicada. Dura 24 horas.", false)
                    viewModelScope.launch { meProvider.refresh() }
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(creating = false) }
                    toast(r.error.message, true)
                }
            }
        }
    }

    private fun toast(text: String, error: Boolean) { _events.trySend(FeedEvent.Toast(text, error)) }

    private companion object { const val MAX_GARMENTS = 12 }
}

// ─────────────────────────────────────────────────────────────────────────────
// Visor
// ─────────────────────────────────────────────────────────────────────────────

@Immutable
data class ViewerUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val groups: List<StoryGroup> = emptyList(),
    val reacting: Boolean = false,
    val deleting: Boolean = false,
)

sealed interface ViewerEvent {
    data class Toast(val text: String, val isError: Boolean = false) : ViewerEvent
    /** Reacción enviada: la UI lanza la animación flotante. */
    data class Reacted(val emoji: String) : ViewerEvent
    /** La historia se borró en el servidor: la UI salta a [nextStoryId] (o cierra si es `null`) y llama a [StoryViewerViewModel.removeStory]. */
    data class Deleted(val storyId: String, val nextStoryId: String?) : ViewerEvent
}

class StoryViewerViewModel(
    private val stories: StoryRepository,
    private val viewed: ViewedStories,
) : ViewModel() {
    private val _state = MutableStateFlow(ViewerUiState())
    val state: StateFlow<ViewerUiState> = _state.asStateFlow()

    private val _events = Channel<ViewerEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val countedViews = HashSet<String>()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        // El orden se fija ahora, con lo visto en este momento: no se reordena mientras se mira.
        val snapshot = viewed.ids.value
        viewModelScope.launch {
            when (val r = stories.getStories()) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, groups = groupStories(r.value, snapshot)) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = r.error.message) }
            }
        }
    }

    /** Se llama al mostrar una historia: la marca como vista y suma una visualización (una sola vez, no las propias). */
    fun onShown(story: Story) {
        if (story.isOwn) return
        val alreadySeen = story.id in viewed.ids.value
        viewed.mark(story.id)
        if (!alreadySeen && countedViews.add(story.id)) {
            viewModelScope.launch { stories.markViewed(story.id) }
        }
    }

    fun react(story: Story, emoji: String) {
        if (story.isOwn || _state.value.reacting) return
        _state.update { it.copy(reacting = true) }
        viewModelScope.launch {
            when (val r = stories.react(story.id, emoji)) {
                is ApiResult.Success -> {
                    _events.trySend(ViewerEvent.Reacted(emoji))
                    if (r.value.conversationId == null) _events.trySend(ViewerEvent.Toast("No puedes reaccionar a tu propia historia.", true))
                }
                is ApiResult.Failure -> _events.trySend(ViewerEvent.Toast(r.error.message, true))
            }
            _state.update { it.copy(reacting = false) }
        }
    }

    /** Quita la historia de la lista local (la UI lo llama tras saltar a la siguiente, para evitar parpadeos). */
    fun removeStory(storyId: String) = _state.update { s ->
        s.copy(groups = s.groups.map { g -> g.copy(stories = g.stories.filterNot { it.id == storyId }) }.filter { it.stories.isNotEmpty() })
    }

    fun delete(story: Story) {
        if (!story.isOwn || _state.value.deleting) return
        _state.update { it.copy(deleting = true) }
        viewModelScope.launch {
            when (val r = stories.delete(story.id)) {
                is ApiResult.Success -> {
                    val groups = _state.value.groups
                    val here = locateStory(groups, story.id)
                    val next = here?.let { nextStory(groups, it) ?: prevStory(groups, it).takeIf { p -> p != it } }?.storyIn(groups)?.id
                    _state.update { it.copy(deleting = false) }
                    _events.trySend(ViewerEvent.Deleted(story.id, next))
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(deleting = false) }
                    _events.trySend(ViewerEvent.Toast(r.error.message, true))
                }
            }
        }
    }
}
