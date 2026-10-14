package com.estilovivo.app.feature.suitcase

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.model.Garment
import com.estilovivo.app.core.data.model.NewTripItem
import com.estilovivo.app.core.data.repo.GarmentRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.LocalDate

/** Dónde se guarda el borrador para sobrevivir a la muerte del proceso (en tests: memoria). */
interface DraftStore {
    fun load(): String?
    fun save(value: String?)
}

class SavedStateDraftStore(private val handle: SavedStateHandle) : DraftStore {
    override fun load(): String? = handle[KEY]
    override fun save(value: String?) { handle[KEY] = value }
    private companion object { const val KEY = "trip_form" }
}

class MemoryDraftStore(private var value: String? = null) : DraftStore {
    override fun load() = value
    override fun save(value: String?) { this.value = value }
}

@Immutable
data class CreateTripUiState(
    val form: TripForm = TripForm(),
    val wardrobe: List<Garment> = emptyList(),
    /** Errores visibles (sólo tras un primer intento de crear). */
    val errors: TripFormErrors = TripFormErrors(),
    val submitting: Boolean = false,
    /** Aviso al añadir un elemento a la lista inicial (vacío, repetido...). */
    val itemError: String? = null,
)

sealed interface CreateTripEvent {
    data class Created(val tripId: String, val destination: String) : CreateTripEvent
    data class Failed(val message: String) : CreateTripEvent
}

class CreateTripViewModel(
    private val store: TripStore,
    private val garments: GarmentRepository,
    private val drafts: DraftStore,
) : ViewModel() {

    private data class Aux(val attempted: Boolean = false, val submitting: Boolean = false, val itemError: String? = null)

    private val json = Json { ignoreUnknownKeys = true }
    private val form = MutableStateFlow(restore())
    private val aux = MutableStateFlow(Aux())
    private val _events = Channel<CreateTripEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val state: StateFlow<CreateTripUiState> = combine(form, garments.garments, aux) { f, wardrobe, a ->
        CreateTripUiState(
            form = f,
            wardrobe = wardrobe,
            errors = if (a.attempted) TripFormRules.validate(f) else TripFormErrors(),
            submitting = a.submitting,
            itemError = a.itemError,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CreateTripUiState(form = form.value))

    init {
        if (garments.garments.value.isEmpty()) viewModelScope.launch { garments.getAll() }
    }

    private fun restore(): TripForm =
        drafts.load()?.let { runCatching { json.decodeFromString<TripForm>(it) }.getOrNull() } ?: TripForm()

    private fun edit(transform: (TripForm) -> TripForm) {
        if (aux.value.submitting) return
        form.update(transform)
        drafts.save(form.value.takeIf { it.isDirty }?.let { json.encodeToString(TripForm.serializer(), it) })
    }

    fun setDestination(value: String) = edit { it.copy(destination = value.take(TripFormRules.MAX_DESTINATION + 20)) }

    /** [end] puede ser `null` mientras se elige el rango (queda igual que la ida). */
    fun setDates(start: LocalDate, end: LocalDate?) = edit {
        val e = end?.takeIf { !it.isBefore(start) } ?: start
        it.copy(dateStart = TripDates.iso(start), dateEnd = TripDates.iso(e))
    }

    fun toggleGarment(id: String) = edit {
        it.copy(garmentIds = if (id in it.garmentIds) it.garmentIds - id else it.garmentIds + id)
    }

    fun toggleItemEssential(index: Int) = edit { f ->
        f.copy(items = f.items.mapIndexed { i, item -> if (i == index) item.copy(isEssential = !item.isEssential) else item })
    }

    fun removeItem(index: Int) = edit { f -> f.copy(items = f.items.filterIndexed { i, _ -> i != index }) }

    /** Devuelve `true` si se añadió (la UI limpia el campo). */
    fun addItem(raw: String): Boolean {
        val (next, error) = TripFormRules.addItem(form.value, raw)
        aux.update { it.copy(itemError = error) }
        if (error != null) return false
        edit { next }
        return true
    }

    fun clearItemError() = aux.update { it.copy(itemError = null) }

    fun resetItems() = edit { it.copy(items = TripFormRules.suggested) }

    fun discard() {
        if (aux.value.submitting) return
        form.value = TripForm()
        aux.value = Aux()
        drafts.save(null)
    }

    /** Valida y crea el viaje; espera al servidor y avisa con el id definitivo. Un segundo toque mientras se envía no hace nada. */
    fun submit() {
        if (aux.value.submitting) return
        val f = form.value
        aux.update { it.copy(attempted = true) }
        if (TripFormRules.validate(f).any) return
        aux.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val ownIds = garments.garments.value.mapTo(HashSet()) { it.id }
            val r = store.create(
                destination = f.destination.trim(),
                dateStart = f.dateStart,
                dateEnd = TripFormRules.normalizedEnd(f),
                items = f.items.map { NewTripItem(it.label, checked = false, isEssential = it.isEssential) },
                garmentIds = f.garmentIds.filter { it in ownIds || ownIds.isEmpty() },
            )
            when (r) {
                is ApiResult.Success -> {
                    form.value = TripForm()
                    drafts.save(null)
                    aux.value = Aux()
                    _events.send(CreateTripEvent.Created(r.value.id, r.value.destination))
                }
                is ApiResult.Failure -> {
                    aux.update { it.copy(submitting = false) }
                    _events.send(CreateTripEvent.Failed(r.error.message))
                }
            }
        }
    }
}
