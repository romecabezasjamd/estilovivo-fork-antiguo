package com.estilovivo.app.feature.planner

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estilovivo.app.core.data.ApiErrorKind
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.model.Look
import com.estilovivo.app.core.data.model.PlannerEntry
import com.estilovivo.app.core.data.model.User
import com.estilovivo.app.core.data.repo.GarmentRepository
import com.estilovivo.app.core.data.repo.LookRepository
import com.estilovivo.app.core.data.repo.PlannerRepository
import com.estilovivo.app.core.util.CyclePeriod
import com.estilovivo.app.core.util.CycleStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.YearMonth

enum class PlannerMode(val label: String) { Monthly("Mensual"), Weekly("Semanal"), Daily("Diario") }

/** Mensaje efímero para el toast. */
@Immutable
data class PlannerEvent(val message: String, val isError: Boolean)

/** Acceso al seguimiento del ciclo (abstraído para poder probar el ViewModel en JVM). */
interface CycleGateway {
    /** Cambia cada vez que se guarda/borra un periodo. */
    val changes: StateFlow<Int>
    fun get(userId: String?): CyclePeriod?
    fun save(userId: String, period: CyclePeriod?): Boolean
}

class CycleStoreGateway(private val store: CycleStore) : CycleGateway {
    override val changes: StateFlow<Int> get() = store.changes
    override fun get(userId: String?) = store.get(userId)
    override fun save(userId: String, period: CyclePeriod?) = store.save(userId, period)
}

@Immutable
data class PlannerUiState(
    val mode: PlannerMode = PlannerMode.Weekly,
    /** Día de referencia: la vista semanal muestra su semana, la mensual su mes y la diaria ese día. */
    val selected: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    /** +1 si se avanzó en el tiempo, -1 si se retrocedió (dirección de la animación). */
    val direction: Int = 0,
    /** Entradas por fecha ISO. */
    val entries: Map<String, PlannerEntry> = emptyMap(),
    val looks: List<Look> = emptyList(),
    val loaded: Boolean = false,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    /** Error a pantalla completa (sólo si no hay datos que enseñar). */
    val error: String? = null,
    val busyDates: Set<String> = emptySet(),
    val cycleEnabled: Boolean = false,
    val cyclePeriod: CyclePeriod? = null,
) {
    val month: YearMonth get() = YearMonth.from(selected)
    val weekDays: List<LocalDate> get() = PlannerCalendar.weekDays(selected)

    fun entry(date: LocalDate): PlannerEntry? = entries[PlannerCalendar.iso(date)]

    /** Look de una entrada: primero el de la caché propia (más fresco), si no el que trajo el servidor. */
    fun lookOf(entry: PlannerEntry?): Look? {
        if (entry?.lookId == null) return null
        return looks.firstOrNull { it.id == entry.lookId } ?: entry.look
    }

    fun isCycleDay(date: LocalDate): Boolean = cycleEnabled && cyclePeriod.contains(date)
}

class PlannerViewModel(
    private val planner: PlannerRepository,
    private val lookRepo: LookRepository,
    private val garments: GarmentRepository,
    private val user: StateFlow<User?>,
    private val cycle: CycleGateway,
    /** Alcance con vida de app: refrescar prendas aunque la pantalla se cierre. */
    private val backgroundScope: CoroutineScope,
    private val handle: SavedStateHandle = SavedStateHandle(),
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private data class Core(
        val mode: PlannerMode,
        val selected: LocalDate,
        val today: LocalDate,
        val direction: Int = 0,
        val entries: Map<String, PlannerEntry> = emptyMap(),
        val loaded: Boolean = false,
        val loading: Boolean = true,
        val refreshing: Boolean = false,
        val error: String? = null,
        val busy: Set<String> = emptySet(),
    )

    private val core = MutableStateFlow(
        Core(
            mode = handle.get<String>(KEY_MODE)?.let { runCatching { PlannerMode.valueOf(it) }.getOrNull() } ?: PlannerMode.Weekly,
            selected = PlannerCalendar.parse(handle.get<String>(KEY_DATE)) ?: today(),
            today = today(),
        ),
    )
    private val eventChannel = Channel<PlannerEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    /** Serializa las escrituras para que el orden de las acciones del usuario se respete y el rollback sea correcto. */
    private val writeLock = Mutex()
    private val touchedSinceFetch = mutableSetOf<String>()
    private var fetchJob: Job? = null

    val state: StateFlow<PlannerUiState> = combine(core, lookRepo.myLooks, user, cycle.changes) { c, looks, u, _ ->
        val enabled = u?.gender.equals("female", ignoreCase = true) && u?.cycleTracking == true
        PlannerUiState(
            mode = c.mode, selected = c.selected, today = c.today, direction = c.direction,
            entries = c.entries, looks = looks, loaded = c.loaded, loading = c.loading, refreshing = c.refreshing,
            error = c.error, busyDates = c.busy,
            cycleEnabled = enabled,
            cyclePeriod = if (enabled) cycle.get(u?.id) else null,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PlannerUiState())

    init {
        refresh(initial = true)
    }

    // ── Navegación ───────────────────────────────────────────────────────────

    /** Abre la ruta con una fecha concreta (vista diaria). Sólo se aplica una vez (sobrevive a rotación y a la muerte del proceso). */
    fun openInitialDate(raw: String?) {
        if (handle.get<Boolean>(KEY_INITIAL_DONE) == true) return
        handle[KEY_INITIAL_DONE] = true
        val date = PlannerCalendar.parse(raw) ?: return
        moveTo(date, PlannerMode.Daily)
    }

    fun selectMode(mode: PlannerMode) = moveTo(core.value.selected, mode)

    /** Toca un día: en mensual lo selecciona; si ya estaba seleccionado abre su vista diaria. */
    fun tapDay(date: LocalDate) {
        val c = core.value
        if (c.mode == PlannerMode.Monthly && c.selected != date) moveTo(date, PlannerMode.Monthly) else openDay(date)
    }

    fun openDay(date: LocalDate) = moveTo(date, PlannerMode.Daily)

    fun previous() = step(-1)
    fun next() = step(1)

    private fun step(sign: Int) {
        val c = core.value
        val target = when (c.mode) {
            PlannerMode.Weekly -> c.selected.plusWeeks(sign.toLong())
            PlannerMode.Monthly -> c.selected.plusMonths(sign.toLong())
            PlannerMode.Daily -> c.selected.plusDays(sign.toLong())
        }
        moveTo(target, c.mode)
    }

    fun goToday() = moveTo(core.value.today, core.value.mode)

    private fun moveTo(date: LocalDate, mode: PlannerMode) {
        core.update { c ->
            val dir = when {
                date.isAfter(c.selected) -> 1
                date.isBefore(c.selected) -> -1
                else -> 0
            }
            c.copy(selected = date, mode = mode, direction = dir)
        }
        handle[KEY_MODE] = mode.name
        handle[KEY_DATE] = PlannerCalendar.iso(date)
    }

    /** Al volver a primer plano: actualiza "hoy" (pudo cambiar la fecha) y recarga sin parpadeos. */
    fun onResume() {
        val now = today()
        core.update { if (it.today == now) it else it.copy(today = now) }
        if (core.value.loaded) refresh(silent = true)
    }

    // ── Carga ────────────────────────────────────────────────────────────────

    fun refresh(initial: Boolean = false, silent: Boolean = false) {
        if (fetchJob?.isActive == true) return
        core.update {
            it.copy(
                loading = !it.loaded,
                refreshing = !silent && it.loaded,
                error = if (it.loaded) it.error else null,
            )
        }
        synchronized(touchedSinceFetch) { touchedSinceFetch.clear() }
        fetchJob = viewModelScope.launch {
            val entriesCall = async { planner.getAll() }
            val looksCall = async { lookRepo.getMine() }
            val result = entriesCall.await()
            looksCall.await()
            writeLock.withLock {
                when (result) {
                    is ApiResult.Success -> {
                        val fresh = result.value
                            .mapNotNull { e ->
                                PlannerCalendar.parse(e.date)?.let { d -> PlannerCalendar.iso(d).let { k -> k to e.copy(date = k) } }
                            }
                            .toMap()
                        val touched = synchronized(touchedSinceFetch) { touchedSinceFetch.toSet() }
                        core.update { c ->
                            // Lo tocado mientras se descargaba manda sobre la respuesta (que pudo salir antes del cambio).
                            val merged = fresh.toMutableMap()
                            touched.forEach { key ->
                                val local = c.entries[key]
                                if (local != null) merged[key] = local else merged.remove(key)
                            }
                            c.copy(entries = merged, loaded = true, loading = false, refreshing = false, error = null)
                        }
                    }
                    is ApiResult.Failure -> {
                        val hadData = core.value.loaded
                        core.update {
                            it.copy(loading = false, refreshing = false, error = if (hadData) it.error else result.error.message)
                        }
                        if (hadData && !silent) eventChannel.trySend(PlannerEvent(result.error.message, true))
                    }
                }
            }
        }
    }

    // ── Edición de días ──────────────────────────────────────────────────────

    /** Asigna [lookId] al día conservando la nota. */
    fun assignLook(date: LocalDate, lookId: String) {
        val name = lookRepo.myLooks.value.firstOrNull { it.id == lookId }?.name
        val msg = if (name != null) "«$name» planificado para el ${PlannerCalendar.dayLabel(date)}" else "Look asignado"
        mutate(date, success = msg) { e -> lookId to e?.eventNote.orEmpty() }
    }

    /** Quita el look del día conservando la nota; si no quedaba nota el día se elimina. */
    fun clearLook(date: LocalDate) =
        mutate(date, success = "Look quitado del ${PlannerCalendar.dayLabel(date)}") { e -> null to e?.eventNote.orEmpty() }

    /** Guarda la nota conservando el look; nota vacía y sin look → día eliminado. */
    fun saveNote(date: LocalDate, note: String) {
        val clean = note.trim()
        mutate(date, success = if (clean.isEmpty()) "Nota eliminada" else "Nota guardada") { e -> e?.lookId to clean }
    }

    private fun mutate(date: LocalDate, success: String?, change: (PlannerEntry?) -> Pair<String?, String>) {
        val key = PlannerCalendar.iso(date)
        viewModelScope.launch {
            writeLock.withLock {
                val before = core.value.entries[key]
                val (lookId, note) = change(before)
                val action = resolveDayAction(before, lookId, note)
                if (action is DayAction.Nothing) return@withLock
                synchronized(touchedSinceFetch) { touchedSinceFetch += key }

                // Optimista
                val optimistic = (action as? DayAction.Save)?.let { a ->
                    PlannerEntry(
                        date = key, lookId = a.lookId,
                        look = a.lookId?.let { id -> lookRepo.myLooks.value.firstOrNull { it.id == id } ?: before?.look?.takeIf { it.id == id } },
                        eventNote = a.note.ifEmpty { null },
                    )
                }
                setEntry(key, optimistic, busy = true)

                val failure: String? = when (action) {
                    is DayAction.Save -> when (val r = planner.save(key, action.lookId, action.note)) {
                        is ApiResult.Success -> {
                            val s = r.value
                            val keepLook = s.look ?: optimistic?.look?.takeIf { it.id == s.lookId }
                            setEntry(key, s.copy(date = key, look = keepLook), busy = false)
                            null
                        }
                        is ApiResult.Failure -> r.error.message
                    }
                    DayAction.Delete -> when (val r = planner.delete(key)) {
                        is ApiResult.Success -> { setEntry(key, null, busy = false); null }
                        // Ya no existía en el servidor: el resultado deseado (día vacío) se cumple igualmente.
                        is ApiResult.Failure ->
                            if (r.error.kind == ApiErrorKind.NotFound) { setEntry(key, null, busy = false); null } else r.error.message
                    }
                    DayAction.Nothing -> null
                }

                if (failure != null) {
                    setEntry(key, before, busy = false) // rollback
                    eventChannel.trySend(PlannerEvent(failure, true))
                } else {
                    if (success != null) eventChannel.trySend(PlannerEvent(success, false))
                    if (before?.lookId != core.value.entries[key]?.lookId) refreshGarments()
                }
            }
        }
    }

    private fun setEntry(key: String, entry: PlannerEntry?, busy: Boolean) {
        core.update { c ->
            c.copy(
                entries = if (entry == null) c.entries - key else c.entries + (key to entry),
                busy = if (busy) c.busy + key else c.busy - key,
            )
        }
    }

    /** El servidor mantiene `usageCount` de las prendas al cambiar de look: se refresca el armario en segundo plano. */
    private fun refreshGarments() {
        backgroundScope.launch { garments.getAll() }
    }

    // ── Ciclo ────────────────────────────────────────────────────────────────

    /** Guarda el periodo. Devuelve un mensaje de error o `null` si todo fue bien. */
    fun saveCycle(start: LocalDate, end: LocalDate): String? {
        val id = user.value?.id ?: return "Inicia sesión para guardar tu periodo."
        if (!state.value.cycleEnabled) return "Activa el seguimiento del ciclo en tu perfil."
        if (end.isBefore(start)) return "El final no puede ser anterior al inicio."
        val ok = cycle.save(id, CyclePeriod(PlannerCalendar.iso(start), PlannerCalendar.iso(end)))
        if (!ok) return "No se ha podido guardar el periodo."
        eventChannel.trySend(PlannerEvent("Periodo guardado", false))
        return null
    }

    fun clearCycle() {
        val id = user.value?.id ?: return
        if (cycle.save(id, null)) eventChannel.trySend(PlannerEvent("Periodo eliminado", false))
    }

    private companion object {
        const val KEY_MODE = "planner_mode"
        const val KEY_DATE = "planner_date"
        const val KEY_INITIAL_DONE = "planner_initial_done"
    }
}
