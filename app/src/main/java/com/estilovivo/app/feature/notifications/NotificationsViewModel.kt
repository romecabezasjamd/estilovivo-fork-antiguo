package com.estilovivo.app.feature.notifications

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.AppScope
import com.estilovivo.app.core.data.model.AppNotification
import com.estilovivo.app.core.data.repo.NotificationRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

@Immutable
data class NotificationsUiState(
    /** Primera carga sin nada en caché. */
    val loading: Boolean = true,
    /** Error de la primera carga (con la lista vacía): pantalla de error con reintento. */
    val error: String? = null,
    val sections: List<DaySection> = emptyList(),
    val unread: Int = 0,
    /** Pull-to-refresh en curso. */
    val refreshing: Boolean = false,
    val markingAll: Boolean = false,
    val now: Instant = Instant.EPOCH,
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    val isEmpty: Boolean get() = sections.isEmpty()
}

sealed interface NotificationsEvent {
    data class Error(val message: String) : NotificationsEvent
    data class Open(val destination: NotificationDestination) : NotificationsEvent
}

class NotificationsViewModel(
    private val repo: NotificationRepository,
    private val appScope: AppScope,
    private val clock: () -> Instant = Instant::now,
    private val zone: ZoneId = ZoneId.systemDefault(),
    /** Cada cuánto se refrescan las etiquetas relativas; 0 lo desactiva (tests). */
    tickMs: Long = 60_000L,
) : ViewModel() {

    private data class Aux(
        val loaded: Boolean = false,
        val error: String? = null,
        val refreshing: Boolean = false,
        val markingAll: Boolean = false,
    )

    private val aux = MutableStateFlow(Aux())
    private val now = MutableStateFlow(clock())
    private val marking = HashSet<String>()
    private val _events = Channel<NotificationsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val state: StateFlow<NotificationsUiState> = combine(repo.notifications, aux, now) { list, a, t ->
        NotificationsUiState(
            loading = !a.loaded && list.isEmpty(),
            error = a.error.takeIf { list.isEmpty() },
            sections = groupByDay(list, t, zone),
            unread = list.count { !it.isRead },
            refreshing = a.refreshing,
            markingAll = a.markingAll,
            now = t,
            zone = zone,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, NotificationsUiState(now = now.value, zone = zone))

    init {
        load(initial = true)
        // Las etiquetas relativas ("Hace 5 min") y la frontera Hoy/Ayer se mantienen al día
        if (tickMs > 0) viewModelScope.launch {
            while (true) {
                delay(tickMs)
                now.value = clock()
            }
        }
    }

    private fun load(initial: Boolean) {
        aux.update { it.copy(error = null, refreshing = !initial) }
        viewModelScope.launch {
            val r = repo.refresh()
            now.value = clock()
            val message = (r as? ApiResult.Failure)?.error?.message
            aux.update { it.copy(loaded = true, refreshing = false, error = message) }
            // Con datos en pantalla un fallo de recarga no tapa la lista: se avisa con un toast
            if (message != null && repo.notifications.value.isNotEmpty()) _events.trySend(NotificationsEvent.Error(message))
        }
    }

    /** Reintento desde la pantalla de error. */
    fun retry() {
        aux.update { it.copy(loaded = false) }
        load(initial = true)
    }

    fun refresh() = load(initial = false)

    /** Tocar una notificación: se marca como leída (sin esperar) y se navega según su tipo. */
    fun open(n: AppNotification) {
        markRead(n)
        _events.trySend(NotificationsEvent.Open(n.destination()))
    }

    /** Deslizar o tocar el punto: sólo marca como leída. */
    fun markRead(n: AppNotification) {
        if (n.isRead || !marking.add(n.id)) return
        // En el ámbito de la app: si te vas de la pantalla a mitad, la petición no se cancela.
        appScope.scope.launch {
            val r = repo.markRead(n.id)
            marking.remove(n.id)
            if (r is ApiResult.Failure) _events.trySend(NotificationsEvent.Error(r.error.message))
        }
    }

    fun markAllRead() {
        if (aux.value.markingAll || repo.notifications.value.none { !it.isRead }) return
        aux.update { it.copy(markingAll = true) }
        viewModelScope.launch {
            val r = repo.markAllRead()
            aux.update { it.copy(markingAll = false) }
            if (r is ApiResult.Failure) _events.trySend(NotificationsEvent.Error(r.error.message))
        }
    }
}
