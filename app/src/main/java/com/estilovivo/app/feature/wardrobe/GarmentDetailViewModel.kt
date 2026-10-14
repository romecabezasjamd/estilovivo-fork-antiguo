package com.estilovivo.app.feature.wardrobe

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.ImageUpload
import com.estilovivo.app.core.data.model.Garment
import com.estilovivo.app.core.data.model.Trip
import com.estilovivo.app.core.data.repo.GarmentRepository
import com.estilovivo.app.core.data.repo.SocialRepository
import com.estilovivo.app.core.data.repo.TripRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class DetailUiState(
    val garment: Garment? = null,
    val loading: Boolean = true,
    /** Cargado todo y la prenda no existe (borrada, o enlace antiguo). */
    val missing: Boolean = false,
    val busy: Set<String> = emptySet(),
    val favorite: Boolean = false,
    val trips: List<Trip> = emptyList(),
    val tripsLoading: Boolean = false,
    val tripsError: String? = null,
)

class GarmentDetailViewModel(
    private val id: String,
    private val garments: GarmentRepository,
    private val trips: TripRepository,
    private val social: SocialRepository,
    private val actions: GarmentActions,
) : ViewModel() {

    private data class Aux(
        val loaded: Boolean = false,
        val busy: Set<String> = emptySet(),
        val favorite: Boolean = false,
        val trips: List<Trip> = emptyList(),
        val tripsLoading: Boolean = false,
        val tripsError: String? = null,
    )

    private val aux = MutableStateFlow(Aux())
    private val _events = Channel<WardrobeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val state: StateFlow<DetailUiState> = combine(garments.garments, aux) { all, a ->
        val g = all.firstOrNull { it.id == id }
        DetailUiState(
            garment = g,
            loading = g == null && !a.loaded,
            missing = g == null && a.loaded,
            busy = a.busy,
            favorite = a.favorite,
            trips = a.trips,
            tripsLoading = a.tripsLoading,
            tripsError = a.tripsError,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DetailUiState())

    init {
        viewModelScope.launch {
            if (garments.garments.value.none { it.id == id }) garments.getAll()
            aux.update { it.copy(loaded = true) }
        }
        viewModelScope.launch {
            val favs = social.getFavorites().getOrNullSafe() ?: return@launch
            aux.update { it.copy(favorite = favs.any { f -> f.garment?.id == id }) }
        }
    }

    private fun <T> ApiResult<T>.getOrNullSafe(): T? = (this as? ApiResult.Success)?.value

    private fun operation(key: String, success: String?, block: suspend () -> ApiResult<*>) {
        if (key in aux.value.busy) return
        aux.update { it.copy(busy = it.busy + key) }
        viewModelScope.launch {
            val r = block()
            aux.update { it.copy(busy = it.busy - key) }
            when (r) {
                is ApiResult.Success -> {
                    success?.let { _events.send(WardrobeEvent.Message(it)) }
                    _events.send(WardrobeEvent.OperationResult(key, true))
                }
                is ApiResult.Failure -> {
                    _events.send(WardrobeEvent.Message(r.error.message, error = true))
                    _events.send(WardrobeEvent.OperationResult(key, false, r.error.message))
                }
            }
        }
    }

    private fun garment() = state.value.garment

    fun markWorn() {
        val g = garment() ?: return
        operation(KEY_WORN, "¡Anotado! ${g.usageCount + 1} ${if (g.usageCount + 1 == 1) "uso" else "usos"}.") { actions.markWorn(g.id) }
    }

    fun setWashing(washing: Boolean) {
        val g = garment() ?: return
        operation(KEY_WASH, if (washing) "“${g.name}” va a la lavadora." else "“${g.name}” vuelve al armario.") { actions.setWashing(g, washing) }
    }

    fun delete() {
        val g = garment() ?: return
        operation(KEY_DELETE, "“${g.name}” eliminada.") { actions.delete(g.id) }
    }

    fun edit(form: GarmentForm, image: ImageUpload?) {
        val g = garment() ?: return
        operation(KEY_EDIT, "Cambios guardados.") { actions.edit(g, form, image) }
    }

    fun sell(form: GarmentForm) {
        val g = garment() ?: return
        operation(KEY_SELL, "Tu prenda ya está en venta.") { actions.sell(g, form) }
    }

    fun cancelSale() {
        val g = garment() ?: return
        operation(KEY_SELL, "Retirada de la venta.") { actions.cancelSale(g) }
    }

    fun toggleFavorite() {
        garment() ?: return
        if (KEY_FAV in aux.value.busy) return
        aux.update { it.copy(busy = it.busy + KEY_FAV) }
        viewModelScope.launch {
            when (val r = garments.toggleFavorite(id)) {
                is ApiResult.Success -> aux.update { it.copy(favorite = r.value, busy = it.busy - KEY_FAV) }
                is ApiResult.Failure -> {
                    aux.update { it.copy(busy = it.busy - KEY_FAV) }
                    _events.send(WardrobeEvent.Message(r.error.message, error = true))
                }
            }
        }
    }

    // ── Viajes ───────────────────────────────────────────────────────────────────────────────

    fun loadTrips() {
        if (aux.value.tripsLoading) return
        aux.update { it.copy(tripsLoading = true, tripsError = null) }
        viewModelScope.launch {
            when (val r = trips.getAll()) {
                is ApiResult.Success -> aux.update { it.copy(trips = r.value, tripsLoading = false) }
                is ApiResult.Failure -> aux.update { it.copy(tripsLoading = false, tripsError = r.error.message) }
            }
        }
    }

    fun addToTrip(trip: Trip) {
        if (trip.garments.any { it.id == id }) {
            viewModelScope.launch { _events.send(WardrobeEvent.Message("Ya está en la maleta de ${trip.destination}.")) }
            return
        }
        val key = "trip:${trip.id}"
        operation(key, "Añadida a tu viaje a ${trip.destination}.") {
            val r = trips.updateMeta(trip.id, trip.destination, trip.dateStart, trip.dateEnd, trip.garments.map { it.id } + id)
            if (r is ApiResult.Success) aux.update { a -> a.copy(trips = a.trips.map { if (it.id == trip.id) r.value else it }) }
            r
        }
    }

    companion object {
        const val KEY_WORN = "worn"
        const val KEY_WASH = "wash"
        const val KEY_DELETE = "delete"
        const val KEY_EDIT = "edit"
        const val KEY_SELL = "sell"
        const val KEY_FAV = "fav"
    }
}
