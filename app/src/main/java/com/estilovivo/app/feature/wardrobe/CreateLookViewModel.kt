package com.estilovivo.app.feature.wardrobe

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.model.Garment
import com.estilovivo.app.core.data.model.Look
import com.estilovivo.app.core.data.model.LookChanges
import com.estilovivo.app.core.data.repo.GarmentRepository
import com.estilovivo.app.core.data.repo.LookRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Lo que el usuario ha compuesto hasta ahora (se guarda para sobrevivir a la muerte del proceso). */
@Immutable
data class LookDraft(
    val selectedIds: List<String> = emptyList(),
    val name: String = "",
    val mood: String = "",
    val tags: List<String> = emptyList(),
    val isPublic: Boolean = false,
) {
    fun encode(): List<String> = listOf(
        selectedIds.joinToString("\u0002"), name, mood, tags.joinToString("\u0002"), isPublic.toString(),
    )

    companion object {
        fun decode(p: List<String>?): LookDraft? {
            if (p == null || p.size != 5) return null
            return LookDraft(
                selectedIds = p[0].split("\u0002").filter { it.isNotEmpty() },
                name = p[1], mood = p[2], tags = p[3].split("\u0002").filter { it.isNotEmpty() }, isPublic = p[4].toBoolean(),
            )
        }
    }
}

@Immutable
data class CreateLookUiState(
    val garments: List<Garment> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    /** En edición: el look no existe. */
    val notFound: Boolean = false,
    val editing: Look? = null,
    val draft: LookDraft = LookDraft(),
    val pickerCategory: String = WardrobeCatalog.ALL,
    val saving: Boolean = false,
    val saveError: String? = null,
    /** Sube en cada guardado correcto (dispara el confeti). */
    val savedCount: Int = 0,
) {
    val selected: List<Garment> get() = draft.selectedIds.mapNotNull { id -> garments.firstOrNull { it.id == id } }
    val pickerItems: List<Garment>
        get() = if (pickerCategory == WardrobeCatalog.ALL) garments else garments.filter { WardrobeCatalog.categoryOf(it.type) == pickerCategory }
    val pickerCategories: List<String>
        get() = listOf(WardrobeCatalog.ALL) + WardrobeCatalog.categories.map { it.id }.filter { c -> garments.any { WardrobeCatalog.categoryOf(it.type) == c } }
}

class CreateLookViewModel(
    private val lookId: String?,
    private val garments: GarmentRepository,
    private val looks: LookRepository,
    private val random: Random = Random.Default,
) : ViewModel() {

    private data class Aux(
        val loading: Boolean = true,
        val error: String? = null,
        val notFound: Boolean = false,
        val editing: Look? = null,
        val draft: LookDraft = LookDraft(),
        val pickerCategory: String = WardrobeCatalog.ALL,
        val saving: Boolean = false,
        val saveError: String? = null,
        val savedCount: Int = 0,
    )

    private val aux = MutableStateFlow(Aux())
    private val _events = Channel<WardrobeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val state: StateFlow<CreateLookUiState> = combine(garments.garments, aux) { all, a ->
        CreateLookUiState(
            garments = all, loading = a.loading && all.isEmpty() || (lookId != null && a.editing == null && !a.notFound && a.loading),
            error = a.error.takeIf { all.isEmpty() }, notFound = a.notFound, editing = a.editing, draft = a.draft,
            pickerCategory = a.pickerCategory, saving = a.saving, saveError = a.saveError, savedCount = a.savedCount,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CreateLookUiState())

    private var restored = false

    init {
        load()
    }

    fun load() {
        aux.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            var error: String? = null
            if (garments.garments.value.isEmpty()) {
                (garments.getAll() as? ApiResult.Failure)?.let { error = it.error.message }
            }
            if (lookId != null) {
                var look = looks.myLooks.value.firstOrNull { it.id == lookId }
                if (look == null) {
                    (looks.getMine() as? ApiResult.Failure)?.let { error = error ?: it.error.message }
                    look = looks.myLooks.value.firstOrNull { it.id == lookId }
                }
                if (look == null) {
                    aux.update { it.copy(loading = false, error = error, notFound = error == null) }
                    return@launch
                }
                val existing = garments.garments.value.mapTo(HashSet()) { it.id }
                // El `mood` llega mezclado en las etiquetas (la API de lectura lo antepone): se separa para no duplicarlo.
                val tags = look.tags.filter { it != look.mood }
                val initial = LookDraft(
                    selectedIds = look.garmentIds.filter { it in existing },
                    name = look.name, mood = look.mood.orEmpty(), tags = tags, isPublic = look.isPublic,
                )
                aux.update { a -> a.copy(loading = false, error = error, editing = look, draft = if (restored) a.draft else initial) }
            } else {
                aux.update { it.copy(loading = false, error = error) }
            }
        }
    }

    /** Recupera un borrador guardado (muerte del proceso). Sólo si aún no se ha tocado nada. */
    fun restore(draft: LookDraft) {
        if (restored) return
        restored = true
        aux.update { it.copy(draft = draft) }
    }

    private fun update(block: (LookDraft) -> LookDraft) = aux.update { it.copy(draft = block(it.draft), saveError = null) }

    fun toggle(id: String) = update { d ->
        d.copy(selectedIds = if (id in d.selectedIds) d.selectedIds - id else d.selectedIds + id)
    }

    fun remove(id: String) = update { d -> d.copy(selectedIds = d.selectedIds - id) }

    fun clear() = update { it.copy(selectedIds = emptyList()) }

    /** "Sorpréndeme": sustituye la selección por un look completo al azar. */
    fun surprise() {
        val picks = WardrobeLogic.surprise(garments.garments.value, random)
        if (picks.isEmpty()) {
            viewModelScope.launch { _events.send(WardrobeEvent.Message("Añade algunas prendas a tu armario para poder sorprenderte.", error = true)) }
            return
        }
        update { it.copy(selectedIds = picks.map { g -> g.id }) }
    }

    fun setName(v: String) = update { it.copy(name = v.take(100)) }
    fun setMood(v: String) = update { it.copy(mood = if (it.mood == v) "" else v) }
    fun setPublic(v: Boolean) = update { it.copy(isPublic = v) }
    fun setPickerCategory(c: String) = aux.update { it.copy(pickerCategory = c) }

    /** Añade una etiqueta (minúsculas, sin #, ≤30 caracteres, máx. 5, sin repetidas). Devuelve true si se añadió. */
    fun addTag(raw: String): Boolean {
        val tag = normalizeTag(raw)
        val d = aux.value.draft
        if (tag.isEmpty() || tag in d.tags || d.tags.size >= MAX_TAGS) return false
        update { it.copy(tags = it.tags + tag) }
        return true
    }

    fun removeTag(tag: String) = update { it.copy(tags = it.tags - tag) }

    fun save() {
        val a = aux.value
        // Tras guardar un look nuevo no se vuelve a crear otro igual (doble toque / pantalla aún cerrándose)
        if (a.saving || (lookId == null && a.savedCount > 0)) return
        val d = a.draft
        val ids = d.selectedIds.filter { id -> garments.garments.value.any { it.id == id } }
        if (ids.isEmpty()) {
            aux.update { it.copy(saveError = "Elige al menos una prenda para tu look.") }
            return
        }
        aux.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            val r = if (lookId == null) {
                looks.create(d.name.trim(), ids, d.isPublic, d.mood.ifBlank { null }, d.tags)
            } else {
                // mood "" = quitar el estado de ánimo (null significaría "no tocar")
                looks.update(lookId, LookChanges(name = d.name.trim().ifBlank { "Sin título" }, isPublic = d.isPublic, mood = d.mood, garmentIds = ids, tags = d.tags))
            }
            when (r) {
                is ApiResult.Success -> {
                    aux.update { it.copy(saving = false, savedCount = it.savedCount + 1) }
                    _events.send(WardrobeEvent.Message(if (lookId == null) "Look guardado." else "Look actualizado."))
                    _events.send(WardrobeEvent.OperationResult("save", true))
                }
                is ApiResult.Failure -> {
                    aux.update { it.copy(saving = false, saveError = r.error.message) }
                    _events.send(WardrobeEvent.Message(r.error.message, error = true))
                }
            }
        }
    }

    companion object {
        const val MAX_TAGS = 5
        fun normalizeTag(raw: String): String = raw.trim().trimStart('#').trim().lowercase().take(30)
    }
}
