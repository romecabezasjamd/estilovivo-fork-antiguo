package com.estilovivo.app.feature.suitcase

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.model.Trip
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Mensaje para la UI (toast). */
sealed interface SuitcaseEvent {
    data class Message(val text: String, val error: Boolean = false) : SuitcaseEvent
}

@Immutable
data class TripsUiState(
    val trips: List<Trip> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    /** Error de carga (sólo si no hay nada que mostrar). */
    val error: String? = null,
    /** Viajes que se están borrando (para atenuarlos / evitar dobles borrados). */
    val deleting: Set<String> = emptySet(),
)

class TripsViewModel(
    private val store: TripStore,
    private val clock: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private data class Aux(
        val loading: Boolean = true,
        val refreshing: Boolean = false,
        val error: String? = null,
        val deleting: Set<String> = emptySet(),
    )

    private val aux = MutableStateFlow(Aux())
    private val today = MutableStateFlow(clock())
    private val _events = Channel<SuitcaseEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val state: StateFlow<TripsUiState> = combine(store.trips, store.loaded, aux, today) { trips, loaded, a, day ->
        TripsUiState(
            trips = TripTiming.sort(trips, day),
            today = day,
            loading = a.loading && !loaded && trips.isEmpty(),
            refreshing = a.refreshing,
            error = a.error.takeIf { trips.isEmpty() },
            deleting = a.deleting,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TripsUiState(today = today.value))

    init {
        load()
    }

    /** Recarga inicial / reintento tras error (con esqueleto). */
    fun load() {
        aux.update { it.copy(loading = true, error = null) }
        viewModelScope.launch { finish(store.refresh(), pull = false) }
    }

    /** Pull-to-refresh: mantiene la lista visible. */
    fun refresh() {
        if (aux.value.refreshing) return
        aux.update { it.copy(refreshing = true) }
        viewModelScope.launch { finish(store.refresh(), pull = true) }
    }

    private suspend fun finish(r: ApiResult<List<Trip>>, pull: Boolean) {
        aux.update { it.copy(loading = false, refreshing = false, error = (r as? ApiResult.Failure)?.error?.message) }
        // Con datos ya en pantalla, un fallo se avisa en un toast en vez de sustituir la lista por un error.
        if (r is ApiResult.Failure && store.trips.value.isNotEmpty() && pull) {
            _events.send(SuitcaseEvent.Message(r.error.message, error = true))
        }
    }

    /** Al volver a la app puede haber cambiado el día: las cuentas atrás se recalculan. */
    fun onResume() {
        today.value = clock()
    }

    /** Borrado optimista: desaparece al instante y, si el servidor falla, vuelve a su sitio con un aviso. */
    fun delete(tripId: String) {
        val trip = store.trip(tripId) ?: return
        if (tripId in aux.value.deleting) return
        aux.update { it.copy(deleting = it.deleting + tripId) }
        viewModelScope.launch {
            val r = store.delete(tripId)
            aux.update { it.copy(deleting = it.deleting - tripId) }
            when (r) {
                is ApiResult.Success -> _events.send(SuitcaseEvent.Message("Viaje a ${trip.destination} eliminado."))
                is ApiResult.Failure -> _events.send(
                    SuitcaseEvent.Message("No se pudo eliminar el viaje a ${trip.destination}. ${r.error.message}", error = true),
                )
            }
        }
    }
}
