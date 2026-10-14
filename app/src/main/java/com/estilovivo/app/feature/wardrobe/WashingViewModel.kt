package com.estilovivo.app.feature.wardrobe

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.model.Garment
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

@Immutable
data class WashingUiState(
    val items: List<Garment> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val busy: Set<String> = emptySet(),
    val returningAll: Boolean = false,
)

class WashingViewModel(
    private val garments: GarmentRepository,
    private val actions: GarmentActions,
) : ViewModel() {

    private data class Aux(
        val loading: Boolean = true,
        val error: String? = null,
        val busy: Set<String> = emptySet(),
        val returningAll: Boolean = false,
    )

    private val aux = MutableStateFlow(Aux())
    private val _events = Channel<WardrobeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val state: StateFlow<WashingUiState> = combine(garments.garments, aux) { all, a ->
        val items = WardrobeLogic.washing(all)
        WashingUiState(items, a.loading && items.isEmpty(), a.error.takeIf { items.isEmpty() }, a.busy, a.returningAll)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WashingUiState())

    init {
        refresh()
    }

    fun refresh() {
        aux.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val r = garments.getAll()
            aux.update { it.copy(loading = false, error = (r as? ApiResult.Failure)?.error?.message) }
        }
    }

    /** Devuelve una prenda al armario. */
    fun returnToCloset(id: String) {
        val g = state.value.items.firstOrNull { it.id == id } ?: return
        if (id in aux.value.busy || aux.value.returningAll) return
        aux.update { it.copy(busy = it.busy + id) }
        viewModelScope.launch {
            val r = actions.setWashing(g, false)
            aux.update { it.copy(busy = it.busy - id) }
            when (r) {
                is ApiResult.Success -> _events.send(WardrobeEvent.Message("“${g.name}” vuelve al armario."))
                is ApiResult.Failure -> _events.send(WardrobeEvent.Message(r.error.message, error = true))
            }
        }
    }

    /** Vacía la lavadora una a una (así un fallo suelto no tira el resto) y resume el resultado. */
    fun returnAll() {
        val items = state.value.items
        if (items.isEmpty() || aux.value.returningAll) return
        aux.update { it.copy(returningAll = true) }
        viewModelScope.launch {
            var ok = 0
            var lastError: String? = null
            for (g in items) {
                aux.update { it.copy(busy = it.busy + g.id) }
                when (val r = actions.setWashing(g, false)) {
                    is ApiResult.Success -> ok++
                    is ApiResult.Failure -> lastError = r.error.message
                }
                aux.update { it.copy(busy = it.busy - g.id) }
            }
            aux.update { it.copy(returningAll = false) }
            val failed = items.size - ok
            _events.send(
                when {
                    failed == 0 -> WardrobeEvent.Message(if (ok == 1) "Prenda devuelta al armario." else "$ok prendas vuelven al armario.")
                    ok == 0 -> WardrobeEvent.Message(lastError ?: "No se pudo devolver las prendas.", error = true)
                    else -> WardrobeEvent.Message("$ok devueltas; $failed no se pudieron devolver. ${lastError.orEmpty()}".trim(), error = true)
                },
            )
        }
    }
}
