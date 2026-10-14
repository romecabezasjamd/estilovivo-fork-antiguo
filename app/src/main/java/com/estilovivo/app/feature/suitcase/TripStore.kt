package com.estilovivo.app.feature.suitcase

import com.estilovivo.app.core.data.ApiErrorKind
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.model.Garment
import com.estilovivo.app.core.data.model.NewTripItem
import com.estilovivo.app.core.data.model.Trip
import com.estilovivo.app.core.data.model.TripItem
import com.estilovivo.app.core.data.repo.TripRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Caché compartida de viajes (lista y detalle ven lo mismo) con escritura optimista y rollback.
 *
 * Las operaciones de red corren en [scope] (la vida de la app), así que salir de la pantalla a mitad
 * de un cambio no lo deja a medias: se completa o se deshace aquí mismo.
 */
class TripStore(private val repo: TripRepository, private val scope: CoroutineScope) {

    private val _trips = MutableStateFlow<List<Trip>>(emptyList())
    val trips: StateFlow<List<Trip>> = _trips.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    /** `true` cuando ya hay una lista real del servidor. */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _itemErrors = MutableSharedFlow<String>(extraBufferCapacity = 16)
    /** Fallos de cambios en segundo plano (marcar, añadir o borrar elementos): ya revertidos, sólo falta avisar. */
    val itemErrors: SharedFlow<String> = _itemErrors.asSharedFlow()

    private val lock = Any()
    private val version = AtomicInteger(0)
    private val tempCounter = AtomicInteger(0)

    /** Elementos con una sincronización en marcha y el último estado que el servidor confirmó. */
    private val syncing = HashSet<String>()
    private val confirmed = HashMap<String, TripItem>()

    private fun touch() { version.incrementAndGet() }

    fun trip(id: String): Trip? = _trips.value.firstOrNull { it.id == id }

    // ───────────── carga ─────────────

    /** Recarga del servidor. Si mientras tanto hubo cambios locales, descarta esa respuesta (no pisa lo optimista) y reintenta. */
    suspend fun refresh(): ApiResult<List<Trip>> {
        repeat(3) {
            val v = version.get()
            val result = repo.getAll()
            if (result is ApiResult.Failure) return result
            val list = (result as ApiResult.Success).value
            val applied = synchronized(lock) {
                if (version.get() == v && syncing.isEmpty()) {
                    _trips.value = list
                    true
                } else false
            }
            if (applied) {
                _loaded.value = true
                return result
            }
        }
        // Mucha actividad local: lo que hay ya es lo más fresco que sabemos.
        _loaded.value = true
        return ApiResult.Success(_trips.value)
    }

    // ───────────── viajes ─────────────

    /** Crea el viaje y espera al servidor: el id definitivo es el que se usa para navegar. */
    suspend fun create(
        destination: String,
        dateStart: String,
        dateEnd: String,
        items: List<NewTripItem>,
        garmentIds: List<String>,
    ): ApiResult<Trip> = scope.async {
        val r = repo.create(destination, dateStart, dateEnd, items, garmentIds)
        if (r is ApiResult.Success) {
            touch()
            _trips.update { list -> list.filterNot { it.id == r.value.id } + r.value }
        }
        r
    }.await()

    /**
     * Borra el viaje. Con [optimistic] desaparece al instante y vuelve a su sitio si el servidor falla.
     * Si ya no existe en el servidor se da por borrado.
     */
    suspend fun delete(tripId: String, optimistic: Boolean = true): ApiResult<Unit> = scope.async {
        val previous = _trips.value
        val index = previous.indexOfFirst { it.id == tripId }
        val removed = previous.getOrNull(index)
        if (optimistic && removed != null) {
            touch()
            _trips.update { list -> list.filterNot { it.id == tripId } }
        }
        val r = repo.delete(tripId)
        val gone = r is ApiResult.Success || (r is ApiResult.Failure && r.error.kind in GONE)
        when {
            gone -> {
                touch()
                _trips.update { list -> list.filterNot { it.id == tripId } }
                ApiResult.Success(Unit)
            }
            else -> {
                if (optimistic && removed != null) {
                    touch()
                    _trips.update { list ->
                        if (list.any { it.id == tripId }) list
                        else list.toMutableList().apply { add(index.coerceIn(0, size), removed) }
                    }
                }
                r
            }
        }
    }.await()

    /**
     * Cambia destino, fechas y prendas. Optimista; si falla, se restaura lo anterior.
     * Los elementos del checklist no se tocan (su estado local manda sobre la respuesta del servidor).
     */
    suspend fun updateMeta(
        tripId: String,
        destination: String,
        dateStart: String,
        dateEnd: String,
        garmentIds: List<String>,
        garments: List<Garment>,
    ): ApiResult<Trip> = scope.async {
        val before = trip(tripId) ?: return@async notFound()
        touch()
        replace(tripId) { it.copy(destination = destination.trim(), dateStart = dateStart, dateEnd = dateEnd, garments = garments) }
        val r = repo.updateMeta(tripId, destination, dateStart, dateEnd, garmentIds)
        when (r) {
            is ApiResult.Success -> {
                touch()
                replace(tripId) {
                    it.copy(destination = r.value.destination, dateStart = r.value.dateStart, dateEnd = r.value.dateEnd, garments = r.value.garments)
                }
            }
            is ApiResult.Failure -> {
                touch()
                replace(tripId) { it.copy(destination = before.destination, dateStart = before.dateStart, dateEnd = before.dateEnd, garments = before.garments) }
            }
        }
        r
    }.await()

    // ───────────── elementos del checklist ─────────────

    /**
     * Marca/desmarca o cambia "esencial". El cambio es inmediato; el envío es por elemento y coalescido
     * (varios toques seguidos acaban en un único estado final y un fallo vuelve al último estado confirmado).
     */
    fun setItem(tripId: String, itemId: String, checked: Boolean? = null, essential: Boolean? = null) {
        if (isTemp(itemId)) return
        var base: TripItem? = null
        touch()
        replace(tripId) { t ->
            t.copy(
                items = t.items.map { i ->
                    if (i.id != itemId) i else {
                        base = i
                        i.copy(checked = checked ?: i.checked, isEssential = essential ?: i.isEssential)
                    }
                },
            )
        }
        val original = base ?: return
        val launch = synchronized(lock) {
            confirmed.putIfAbsent(itemId, original)
            syncing.add(itemId)
        }
        if (launch) scope.launch { syncItem(tripId, itemId) }
    }

    private suspend fun syncItem(tripId: String, itemId: String) {
        while (true) {
            var sendChecked: Boolean? = null
            var sendEssential: Boolean? = null
            var known: TripItem? = null
            val done = synchronized(lock) {
                val local = trip(tripId)?.items?.firstOrNull { it.id == itemId }
                val conf = confirmed[itemId]
                if (local != null && conf != null) {
                    sendChecked = local.checked.takeIf { it != conf.checked }
                    sendEssential = local.isEssential.takeIf { it != conf.isEssential }
                    known = conf
                }
                if (sendChecked == null && sendEssential == null) {
                    syncing.remove(itemId)
                    confirmed.remove(itemId)
                    true
                } else false
            }
            if (done) return
            val conf = known!!
            when (val r = repo.updateItem(tripId, itemId, sendChecked, null, sendEssential)) {
                is ApiResult.Success -> synchronized(lock) {
                    confirmed[itemId] = conf.copy(
                        checked = sendChecked ?: conf.checked,
                        isEssential = sendEssential ?: conf.isEssential,
                    )
                }
                is ApiResult.Failure -> {
                    // 404/403: el elemento ya no existe en el servidor; se quita en local en vez de revertir.
                    val vanished = r.error.kind in GONE
                    touch()
                    replace(tripId) { t ->
                        t.copy(
                            items = if (vanished) t.items.filterNot { it.id == itemId } else t.items.map { i ->
                                if (i.id != itemId) i else i.copy(
                                    checked = if (sendChecked != null) conf.checked else i.checked,
                                    isEssential = if (sendEssential != null) conf.isEssential else i.isEssential,
                                )
                            },
                        )
                    }
                    if (vanished) synchronized(lock) { confirmed.remove(itemId) }
                    _itemErrors.tryEmit(if (vanished) "Ese elemento ya no existe. Hemos actualizado la lista." else r.error.message)
                }
            }
        }
    }

    /** Añade un elemento: aparece al momento (atenuado) y se confirma con el id del servidor; si falla, desaparece y se avisa. */
    fun addItem(tripId: String, rawLabel: String, essential: Boolean = false) {
        val label = Checklist.cleanLabel(rawLabel)
        if (label.isEmpty()) return
        val tempId = "$TEMP_PREFIX${tempCounter.incrementAndGet()}"
        touch()
        replace(tripId) { it.copy(items = it.items + TripItem(tempId, label, checked = false, isEssential = essential)) }
        scope.launch {
            when (val r = repo.addItem(tripId, label, essential)) {
                is ApiResult.Success -> {
                    touch()
                    replace(tripId) { t -> t.copy(items = t.items.map { if (it.id == tempId) r.value else it }) }
                }
                is ApiResult.Failure -> {
                    touch()
                    replace(tripId) { t -> t.copy(items = t.items.filterNot { it.id == tempId }) }
                    _itemErrors.tryEmit("No se pudo añadir «$label». ${r.error.message}")
                }
            }
        }
    }

    fun deleteItem(tripId: String, itemId: String) {
        if (isTemp(itemId)) return
        val trip = trip(tripId) ?: return
        val index = trip.items.indexOfFirst { it.id == itemId }
        if (index < 0) return
        val removed = trip.items[index]
        touch()
        replace(tripId) { t -> t.copy(items = t.items.filterNot { it.id == itemId }) }
        scope.launch {
            val r = repo.deleteItem(tripId, itemId)
            if (r is ApiResult.Failure && r.error.kind !in GONE) {
                touch()
                replace(tripId) { t ->
                    if (t.items.any { it.id == itemId }) t
                    else t.copy(items = t.items.toMutableList().apply { add(index.coerceIn(0, size), removed) })
                }
                _itemErrors.tryEmit("No se pudo quitar «${removed.label}». ${r.error.message}")
            }
        }
    }

    // ───────────── interno ─────────────

    private fun replace(tripId: String, transform: (Trip) -> Trip) {
        _trips.update { list -> list.map { if (it.id == tripId) transform(it) else it } }
    }

    private fun notFound() = ApiResult.Failure(com.estilovivo.app.core.data.ApiError("Este viaje ya no existe.", ApiErrorKind.NotFound))

    companion object {
        const val TEMP_PREFIX = "tmp-"
        fun isTemp(id: String) = id.startsWith(TEMP_PREFIX)

        /** El servidor responde 403 (en vez de 404) cuando el viaje/elemento no existe. */
        private val GONE = setOf(ApiErrorKind.NotFound, ApiErrorKind.Forbidden)
    }
}
