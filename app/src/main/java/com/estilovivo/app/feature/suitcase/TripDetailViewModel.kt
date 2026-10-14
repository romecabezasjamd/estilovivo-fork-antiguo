package com.estilovivo.app.feature.suitcase

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.model.Garment
import com.estilovivo.app.core.data.model.Trip
import com.estilovivo.app.core.data.repo.GarmentRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

@Immutable
data class TripDetailUiState(
    val trip: Trip? = null,
    val wardrobe: List<Garment> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val loading: Boolean = true,
    val error: String? = null,
    /** Cargado pero el viaje no existe (borrado desde otro sitio). */
    val notFound: Boolean = false,
    /** Hay un cambio de destino/fechas/prendas en vuelo: se bloquean más cambios de ese tipo. */
    val savingMeta: Boolean = false,
    val deleting: Boolean = false,
) {
    val phase: TripPhase? get() = trip?.let { TripTiming.phase(it.dateStart, it.dateEnd, today) }
}

sealed interface TripDetailEvent {
    data class Message(val text: String, val error: Boolean = false) : TripDetailEvent
    /** Viaje borrado: la pantalla debe cerrarse. */
    data object Deleted : TripDetailEvent
}

class TripDetailViewModel(
    private val tripId: String,
    private val store: TripStore,
    private val garments: GarmentRepository,
    private val clock: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private data class Aux(
        val loading: Boolean = true,
        val error: String? = null,
        val savingMeta: Boolean = false,
        val deleting: Boolean = false,
    )

    private val aux = MutableStateFlow(Aux())
    private val today = MutableStateFlow(clock())
    private val _events = Channel<TripDetailEvent>(Channel.BUFFERED)
    val events = merge(
        _events.receiveAsFlow(),
        store.itemErrors.map { TripDetailEvent.Message(it, error = true) },
    )

    val state: StateFlow<TripDetailUiState> = combine(store.trips, store.loaded, garments.garments, aux, today) { trips, loaded, wardrobe, a, day ->
        val trip = trips.firstOrNull { it.id == tripId }
        TripDetailUiState(
            trip = trip,
            wardrobe = wardrobe,
            today = day,
            loading = trip == null && a.loading && !loaded,
            error = a.error.takeIf { trip == null },
            notFound = trip == null && loaded && a.error == null && !a.loading && !a.deleting,
            savingMeta = a.savingMeta,
            deleting = a.deleting,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TripDetailUiState(today = today.value))

    init {
        load()
        if (garments.garments.value.isEmpty()) viewModelScope.launch { garments.getAll() }
    }

    fun load() {
        aux.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val r = store.refresh()
            aux.update { it.copy(loading = false, error = (r as? ApiResult.Failure)?.error?.message) }
        }
    }

    fun onResume() {
        today.value = clock()
    }

    // ───────────── checklist ─────────────

    fun toggleItem(itemId: String) {
        val item = state.value.trip?.items?.firstOrNull { it.id == itemId } ?: return
        store.setItem(tripId, itemId, checked = !item.checked)
    }

    fun toggleEssential(itemId: String) {
        val item = state.value.trip?.items?.firstOrNull { it.id == itemId } ?: return
        store.setItem(tripId, itemId, essential = !item.isEssential)
    }

    fun deleteItem(itemId: String) = store.deleteItem(tripId, itemId)

    /** Devuelve el motivo si el texto no es válido (para mostrarlo bajo el campo) o `null` si se añadió. */
    fun addItem(raw: String, essential: Boolean = false): String? {
        val trip = state.value.trip ?: return "Este viaje ya no existe."
        val error = Checklist.validateNew(raw, trip.items.map { it.label })
        if (error != null) return error
        store.addItem(tripId, raw, essential)
        return null
    }

    // ───────────── datos del viaje ─────────────

    /** Prendas del viaje a partir de ids: las del armario y, si ya estaban en el viaje, las que tenga el viaje. */
    fun saveGarments(ids: List<String>, onResult: (Boolean) -> Unit = {}) {
        val trip = state.value.trip ?: return
        val wardrobe = state.value.wardrobe
        val known = (wardrobe + trip.garments).associateBy { it.id }
        val ordered = ids.distinct()
        val resolved = ordered.mapNotNull { known[it] }
        launchMeta(ok = "Prendas actualizadas.", onResult = onResult) { store.updateMeta(tripId, trip.destination, trip.dateStart, trip.dateEnd, ordered, resolved) }
    }

    fun removeGarment(garmentId: String) {
        val trip = state.value.trip ?: return
        saveGarments(trip.garments.map { it.id }.filterNot { it == garmentId })
    }

    /** Valida y guarda destino y fechas. Devuelve el error de validación o `null` si se envió. */
    fun saveBasics(destination: String, dateStart: String, dateEnd: String, onResult: (Boolean) -> Unit = {}): TripFormErrors {
        val trip = state.value.trip ?: return TripFormErrors(destination = "Este viaje ya no existe.")
        val form = TripForm(destination = destination, dateStart = dateStart, dateEnd = dateEnd)
        val errors = TripFormRules.validate(form)
        if (errors.any) return errors
        launchMeta(ok = "Viaje actualizado.", onResult = onResult) {
            store.updateMeta(
                tripId, destination.trim(), dateStart, TripFormRules.normalizedEnd(form),
                trip.garments.map { it.id }, trip.garments,
            )
        }
        return TripFormErrors()
    }

    private fun launchMeta(ok: String, onResult: (Boolean) -> Unit = {}, block: suspend () -> ApiResult<Trip>) {
        if (aux.value.savingMeta) {
            onResult(false)
            return
        }
        aux.update { it.copy(savingMeta = true) }
        viewModelScope.launch {
            val r = block()
            aux.update { it.copy(savingMeta = false) }
            onResult(r is ApiResult.Success)
            _events.send(
                when (r) {
                    is ApiResult.Success -> TripDetailEvent.Message(ok)
                    is ApiResult.Failure -> TripDetailEvent.Message(r.error.message, error = true)
                },
            )
        }
    }

    fun deleteTrip() {
        if (aux.value.deleting) return
        aux.update { it.copy(deleting = true) }
        viewModelScope.launch {
            // Sin optimismo: se cierra la pantalla sólo cuando el servidor confirma.
            when (val r = store.delete(tripId, optimistic = false)) {
                is ApiResult.Success -> _events.send(TripDetailEvent.Deleted)
                is ApiResult.Failure -> {
                    aux.update { it.copy(deleting = false) }
                    _events.send(TripDetailEvent.Message(r.error.message, error = true))
                }
            }
        }
    }
}
