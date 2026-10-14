package com.estilovivo.app.feature.wardrobe

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.ImageUpload
import com.estilovivo.app.core.data.model.Garment
import com.estilovivo.app.core.data.model.Look
import com.estilovivo.app.core.data.model.LookChanges
import com.estilovivo.app.core.data.repo.GarmentRepository
import com.estilovivo.app.core.data.repo.LookRepository
import com.estilovivo.app.core.data.repo.TripRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WardrobeView(val label: String) { Closet("Armario"), Looks("Looks"), Sales("Ventas") }

enum class AddKind { Closet, Sale }

/** Petición de alta: se conserva entera para poder restaurar el formulario si el servidor la rechaza. */
class AddRequest(val kind: AddKind, val form: GarmentForm, val upload: ImageUpload)

/** Eventos de una sola vez hacia la UI (toasts, restauraciones, resultado de operaciones con hoja). */
sealed interface WardrobeEvent {
    data class Message(val text: String, val error: Boolean = false) : WardrobeEvent

    /** El alta optimista falló y se deshizo: la UI puede reabrir el formulario con lo que había escrito. */
    data class AddRolledBack(val request: AddRequest, val message: String) : WardrobeEvent

    /** Fin de una operación lanzada desde una hoja (clave = la de [WardrobeViewModel.busy]). */
    data class OperationResult(val key: String, val success: Boolean, val message: String? = null) : WardrobeEvent
}

@Immutable
data class WardrobeUiState(
    val view: WardrobeView = WardrobeView.Closet,
    val filters: WardrobeFilters = WardrobeFilters(),
    /** Primera carga sin nada que mostrar. */
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    /** Error de carga (sólo si no hay datos que mostrar). */
    val error: String? = null,
    /** Todas las prendas (las pendientes de subir, primero). */
    val all: List<Garment> = emptyList(),
    /** Armario visible: sin lavadora y con filtros. */
    val closet: List<Garment> = emptyList(),
    val categoryCounts: Map<String, Int> = emptyMap(),
    val availableColors: List<String> = emptyList(),
    val washingCount: Int = 0,
    val sales: List<Garment> = emptyList(),
    val salesTotal: Double = 0.0,
    val pendingIds: Set<String> = emptySet(),
    val busy: Set<String> = emptySet(),
    val tripGarmentIds: Set<String> = emptySet(),
    val looks: List<Look> = emptyList(),
    val looksLoading: Boolean = false,
    val looksError: String? = null,
)

class WardrobeViewModel(
    private val garments: GarmentRepository,
    private val looks: LookRepository,
    private val trips: TripRepository,
    private val actions: GarmentActions,
) : ViewModel() {

    private data class Pending(val id: String, val preview: Garment)

    private data class Aux(
        val view: WardrobeView = WardrobeView.Closet,
        val filters: WardrobeFilters = WardrobeFilters(),
        val initialLoad: Boolean = true,
        val refreshing: Boolean = false,
        val error: String? = null,
        val busy: Set<String> = emptySet(),
        val tripIds: Set<String> = emptySet(),
        val looksLoading: Boolean = true,
        val looksError: String? = null,
    )

    private val pending = MutableStateFlow<List<Pending>>(emptyList())
    private val aux = MutableStateFlow(Aux())
    private var counter = 0

    private val _events = Channel<WardrobeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val state: StateFlow<WardrobeUiState> = combine(garments.garments, looks.myLooks, pending, aux) { real, myLooks, pend, a ->
        val all = pend.map { it.preview } + real
        val sales = WardrobeLogic.sales(all)
        WardrobeUiState(
            view = a.view,
            filters = a.filters,
            loading = a.initialLoad && all.isEmpty(),
            refreshing = a.refreshing,
            error = a.error.takeIf { all.isEmpty() },
            all = all,
            closet = WardrobeLogic.closet(all, a.filters),
            categoryCounts = WardrobeLogic.categoryCounts(all, a.filters),
            availableColors = WardrobeLogic.availableColors(all),
            washingCount = all.count { it.isWashing },
            sales = sales,
            salesTotal = WardrobeLogic.salesTotal(sales),
            pendingIds = pend.mapTo(HashSet()) { it.id },
            busy = a.busy,
            tripGarmentIds = a.tripIds,
            looks = myLooks,
            looksLoading = a.looksLoading && myLooks.isEmpty(),
            looksError = a.looksError.takeIf { myLooks.isEmpty() },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WardrobeUiState())

    init {
        refresh()
    }

    // ── Navegación y filtros ────────────────────────────────────────────────────────────────

    fun setView(v: WardrobeView) = aux.update { it.copy(view = v) }

    fun setFilters(f: WardrobeFilters) = aux.update { it.copy(filters = f) }

    fun updateFilters(block: (WardrobeFilters) -> WardrobeFilters) = aux.update { it.copy(filters = block(it.filters)) }

    fun clearAdvancedFilters() = updateFilters { it.copy(season = WardrobeCatalog.ALL, color = WardrobeCatalog.ALL, sort = SortOrder.Recent) }

    // ── Carga ───────────────────────────────────────────────────────────────────────────────

    /** Recarga armario, looks y viajes. Ignora toques repetidos mientras hay una carga en curso. */
    fun refresh() {
        if (aux.value.refreshing) return
        aux.update { it.copy(refreshing = true, error = null, looksError = null) }
        viewModelScope.launch {
            val g = async { garments.getAll() }
            val l = async { looks.getMine() }
            val t = async { trips.getAll() }
            val gr = g.await()
            val lr = l.await()
            val tr = t.await()
            val hadGarments = garments.garments.value.isNotEmpty() || pending.value.isNotEmpty()
            aux.update {
                it.copy(
                    refreshing = false,
                    initialLoad = false,
                    error = (gr as? ApiResult.Failure)?.error?.message,
                    looksLoading = false,
                    looksError = (lr as? ApiResult.Failure)?.error?.message,
                    tripIds = (tr as? ApiResult.Success)?.value?.flatMapTo(HashSet()) { trip -> trip.garments.map { g -> g.id } } ?: it.tripIds,
                )
            }
            if (gr is ApiResult.Failure && hadGarments) send(WardrobeEvent.Message(gr.error.message, error = true))
        }
    }

    // ── Alta optimista ───────────────────────────────────────────────────────────────────────

    /**
     * La prenda aparece al instante (con la foto local) y se sincroniza en segundo plano.
     * Si el servidor la rechaza (p. ej. límite de ventas), se deshace y se avisa con [WardrobeEvent.AddRolledBack].
     */
    fun add(request: AddRequest) {
        val tempId = "pending-${counter++}"
        val f = request.form
        val sale = request.kind == AddKind.Sale
        val preview = Garment(
            id = tempId,
            imageUrl = f.photoUri.orEmpty(),
            name = f.name.trim(),
            type = f.category,
            color = f.color.trim().ifBlank { "varios" },
            season = f.season,
            usageCount = 0,
            forSale = sale,
            price = if (sale) GarmentFormRules.validSalePrice(f.price) ?: 0.0 else 0.0,
            brand = f.brand.trim().ifBlank { null },
            size = f.size.trim().ifBlank { null },
            condition = if (sale) f.condition else "new",
        )
        pending.update { listOf(Pending(tempId, preview)) + it }
        viewModelScope.launch {
            val draft = GarmentFormRules.draftForAdd(f, forSale = sale)
            when (val r = garments.add(draft, request.upload)) {
                is ApiResult.Success -> {
                    pending.update { list -> list.filterNot { it.id == tempId } }
                    send(WardrobeEvent.Message(if (sale) "Tu prenda ya está en venta." else "Prenda añadida al armario."))
                }
                is ApiResult.Failure -> {
                    pending.update { list -> list.filterNot { it.id == tempId } }
                    send(WardrobeEvent.AddRolledBack(request, r.error.message))
                }
            }
        }
    }

    // ── Operaciones sobre prendas existentes ──────────────────────────────────────────────────

    /** Ejecuta [block] marcando [key] como ocupado (ignora toques dobles) y notifica el resultado. */
    private fun operation(key: String, success: String?, block: suspend () -> ApiResult<*>) {
        if (key in aux.value.busy) return
        aux.update { it.copy(busy = it.busy + key) }
        viewModelScope.launch {
            val r = block()
            aux.update { it.copy(busy = it.busy - key) }
            when (r) {
                is ApiResult.Success -> {
                    success?.let { send(WardrobeEvent.Message(it)) }
                    send(WardrobeEvent.OperationResult(key, true))
                }
                is ApiResult.Failure -> {
                    send(WardrobeEvent.Message(r.error.message, error = true))
                    send(WardrobeEvent.OperationResult(key, false, r.error.message))
                }
            }
        }
    }

    private fun find(id: String) = state.value.all.firstOrNull { it.id == id }

    fun setWashing(id: String, washing: Boolean) {
        val g = find(id) ?: return
        operation(id, if (washing) "“${g.name}” va a la lavadora." else "“${g.name}” vuelve al armario.") { actions.setWashing(g, washing) }
    }

    fun delete(id: String) {
        val g = find(id) ?: return
        operation(id, "“${g.name}” eliminada.") { actions.delete(g.id) }
    }

    fun edit(id: String, form: GarmentForm, image: ImageUpload?) {
        val g = find(id) ?: return
        operation(id, "Cambios guardados.") { actions.edit(g, form, image) }
    }

    fun sell(id: String, form: GarmentForm) {
        val g = find(id) ?: return
        operation(id, "Tu prenda ya está en venta.") { actions.sell(g, form) }
    }

    fun cancelSale(id: String) {
        val g = find(id) ?: return
        operation(id, "Retirada de la venta.") { actions.cancelSale(g) }
    }

    // ── Looks ────────────────────────────────────────────────────────────────────────────────

    fun toggleLookPublic(lookId: String) {
        val look = state.value.looks.firstOrNull { it.id == lookId } ?: return
        val makePublic = !look.isPublic
        operation("look:$lookId", if (makePublic) "Look publicado." else "Look privado.") {
            looks.update(lookId, LookChanges(isPublic = makePublic))
        }
    }

    fun deleteLook(lookId: String) {
        operation("look:$lookId", "Look eliminado.") { looks.delete(lookId) }
    }

    private suspend fun send(e: WardrobeEvent) {
        _events.send(e)
    }
}
