package com.estilovivo.app.feature.profile

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.model.PreferencesUpdate
import com.estilovivo.app.core.data.model.User
import com.estilovivo.app.core.data.repo.AuthRepository
import com.estilovivo.app.core.data.repo.SocialRepository
import com.estilovivo.app.core.data.session.AppSettings
import com.estilovivo.app.core.data.session.ThemeCodec
import com.estilovivo.app.core.data.session.ThemeMode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.estilovivo.app.core.util.CyclePeriod
import com.estilovivo.app.feature.profile.ProfileRules.EmailKey
import com.estilovivo.app.feature.profile.ProfileRules.EmailPrefs
import kotlinx.coroutines.CancellationException
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

/** Avisos de una sola vez hacia la UI (toasts y navegación). */
sealed interface ProfileEvent {
    data class Success(val text: String) : ProfileEvent
    data class Error(val text: String) : ProfileEvent
    data object LoggedOut : ProfileEvent
    data object AccountDeleted : ProfileEvent
    data object Saved : ProfileEvent
}

@Immutable
data class DeleteFlow(val stage: Stage = Stage.None, val typed: String = "", val deleting: Boolean = false) {
    enum class Stage { None, Warning, Typing }

    val canConfirm: Boolean get() = stage == Stage.Typing && !deleting && ProfileRules.deleteConfirmed(typed)
}

@Immutable
data class SettingsUiState(
    val loaded: Boolean = false,
    val gender: String? = null,
    val isPublic: Boolean = true,
    val emails: EmailPrefs = EmailPrefs(true, true, true, true, true),
    val cycleAvailable: Boolean = false,
    val cycleTracking: Boolean = false,
    val cyclePeriod: CyclePeriod? = null,
    val local: AppSettings = AppSettings(),
    /** Solicitudes de seguimiento pendientes (null = no se sabe / no cargado). */
    val pendingRequests: Int? = null,
    /** Grupos de ajustes con una petición en vuelo ("public", "emails", "cycle"). */
    val saving: Set<String> = emptySet(),
    val showLogoutConfirm: Boolean = false,
    val loggingOut: Boolean = false,
    val delete: DeleteFlow = DeleteFlow(),
)

/**
 * Ajustes de la cuenta. Todos los guardados del servidor son optimistas: el interruptor cambia al instante
 * (override local) y, si la petición falla, vuelve a su valor real y avisa con el mensaje del servidor.
 * Mientras un grupo se guarda queda bloqueado (sin dobles toques ni carreras).
 */
class SettingsViewModel(
    private val auth: AuthRepository,
    private val social: SocialRepository,
    private val local: LocalSettings,
    private val cycle: CycleStorage,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private data class Overrides(
        val isPublic: Boolean? = null,
        val emails: EmailPrefs? = null,
        val cycle: Boolean? = null,
    )

    private data class Aux(
        val saving: Set<String> = emptySet(),
        val pendingRequests: Int? = null,
        val showLogoutConfirm: Boolean = false,
        val loggingOut: Boolean = false,
        val delete: DeleteFlow = DeleteFlow(),
    )

    private val overrides = MutableStateFlow(Overrides())
    private val aux = MutableStateFlow(Aux())
    private val _events = Channel<ProfileEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val state: StateFlow<SettingsUiState> = combine(
        auth.currentUser, overrides, local.settings, cycle.changes, aux,
    ) { user, o, settings, _, a ->
        val female = ProfileRules.canTrackCycle(user?.gender)
        SettingsUiState(
            loaded = user != null,
            gender = user?.gender,
            isPublic = o.isPublic ?: user?.isProfilePublic ?: true,
            emails = o.emails ?: ProfileRules.emailPrefs(user),
            cycleAvailable = female,
            cycleTracking = female && (o.cycle ?: user?.cycleTracking ?: false),
            cyclePeriod = if (female) cycle.get(user?.id) else null,
            local = settings,
            pendingRequests = a.pendingRequests,
            saving = a.saving,
            showLogoutConfirm = a.showLogoutConfirm,
            loggingOut = a.loggingOut,
            delete = a.delete,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    init {
        refreshRequests()
    }

    // ───────── Solicitudes de seguimiento ─────────

    fun refreshRequests() {
        viewModelScope.launch {
            val r = social.getFollowRequests()
            if (r is ApiResult.Success) aux.update { it.copy(pendingRequests = r.value.size) }
        }
    }

    // ───────── Ajustes del servidor (optimistas) ─────────

    fun setProfilePublic(value: Boolean) = commit(
        group = "public",
        optimistic = { it.copy(isPublic = value) },
        update = PreferencesUpdate(isProfilePublic = value),
        success = if (value) "Tu perfil ahora es público." else "Tu perfil ahora es privado: tendrás que aprobar a quien quiera seguirte.",
    )

    fun setEmail(key: EmailKey, value: Boolean) {
        val current = state.value.emails
        val next = ProfileRules.applyEmailToggle(current, key, value)
        if (next == current) return
        commit(group = "emails", optimistic = { it.copy(emails = next) }, update = next.toUpdate(), success = null)
    }

    fun setCycleTracking(value: Boolean) {
        val s = state.value
        if (!s.cycleAvailable) {
            _events.trySend(ProfileEvent.Error("El seguimiento del ciclo sólo está disponible en perfiles con género femenino."))
            return
        }
        val userId = auth.currentUser.value?.id
        commit(
            group = "cycle",
            optimistic = { it.copy(cycle = value) },
            update = PreferencesUpdate(cycleTracking = value),
            success = if (value) "Seguimiento del ciclo activado." else "Seguimiento del ciclo desactivado y periodo borrado.",
            onSuccess = { if (!value && userId != null) cycle.save(userId, null) },
        )
    }

    /** Guarda el periodo (sólo en el dispositivo). Devuelve el motivo si no es válido. */
    fun saveCyclePeriod(start: LocalDate, end: LocalDate): String? {
        val userId = auth.currentUser.value?.id ?: return "Vuelve a iniciar sesión."
        if (!state.value.cycleTracking) return "Activa antes el seguimiento del ciclo."
        if (end.isBefore(start)) return "La fecha de fin debe ser posterior al inicio."
        if (start.isAfter(today().plusYears(1)) || end.isAfter(today().plusYears(1))) return "Esas fechas están demasiado lejos."
        if (java.time.temporal.ChronoUnit.DAYS.between(start, end) > 60) return "El periodo no puede durar más de 60 días."
        val ok = cycle.save(userId, CyclePeriod(start.toString(), end.toString()))
        if (!ok) return "No se pudieron guardar las fechas."
        _events.trySend(ProfileEvent.Success("Periodo guardado."))
        return null
    }

    fun clearCyclePeriod() {
        val userId = auth.currentUser.value?.id ?: return
        cycle.save(userId, null)
        _events.trySend(ProfileEvent.Success("Periodo borrado."))
    }

    private fun commit(
        group: String,
        optimistic: (Overrides) -> Overrides,
        update: PreferencesUpdate,
        success: String?,
        onSuccess: suspend () -> Unit = {},
    ) {
        if (group in aux.value.saving) return
        aux.update { it.copy(saving = it.saving + group) }
        overrides.update(optimistic)
        viewModelScope.launch {
            when (val result = auth.updatePreferences(update)) {
                is ApiResult.Success -> {
                    onSuccess()
                    success?.let { _events.send(ProfileEvent.Success(it)) }
                }
                is ApiResult.Failure -> _events.send(ProfileEvent.Error(result.error.message))
            }
            // Éxito: el repositorio ya publicó el usuario nuevo. Fallo: volver al valor real (rollback).
            overrides.update { clearGroup(it, group) }
            aux.update { it.copy(saving = it.saving - group) }
        }
    }

    private fun clearGroup(o: Overrides, group: String) = when (group) {
        "public" -> o.copy(isPublic = null)
        "emails" -> o.copy(emails = null)
        "cycle" -> o.copy(cycle = null)
        else -> o
    }

    // ───────── Ajustes locales ─────────

    fun setThemeMode(mode: ThemeMode) = localChange { local.setThemeMode(mode) }
    fun setFontScale(value: Float) = localChange { local.setFontScale(ProfileRules.clampFontScale(value)) }
    fun setHighContrast(value: Boolean) = localChange { local.setHighContrast(value) }
    fun setHaptics(value: Boolean) = localChange { local.setHaptics(value) }

    private val themeLock = Mutex()

    /**
     * Cambia el tema de color: se pinta al instante (guardado local) y se sincroniza con el servidor.
     * Si falla vuelve al valor anterior y avisa. [customColor] null = sin color propio (usa el preset).
     */
    fun setBrandTheme(preset: String, customColor: String?) {
        viewModelScope.launch {
            themeLock.withLock {
                val prev = state.value.local
                val newPreset = ThemeCodec.preset(preset)
                val newCustom = ThemeCodec.customColor(customColor)
                if (prev.themePreset == newPreset && prev.customColor == newCustom) return@withLock
                try {
                    local.setBrandTheme(newPreset, newCustom)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _events.send(ProfileEvent.Error("No se pudo guardar el tema en este dispositivo."))
                    return@withLock
                }
                // "" borra el color propio en el servidor (null = no tocar)
                when (val r = auth.updatePreferences(PreferencesUpdate(themePreset = newPreset, customColor = newCustom ?: ""))) {
                    is ApiResult.Success -> Unit
                    is ApiResult.Failure -> {
                        runCatching { local.setBrandTheme(prev.themePreset, prev.customColor) } // rollback
                        _events.send(ProfileEvent.Error(r.error.message))
                    }
                }
            }
        }
    }

    private fun localChange(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(ProfileEvent.Error("No se pudo guardar el ajuste en este dispositivo."))
            }
        }
    }

    // ───────── Sesión ─────────

    fun askLogout() = aux.update { it.copy(showLogoutConfirm = true) }
    fun dismissLogout() = aux.update { if (it.loggingOut) it else it.copy(showLogoutConfirm = false) }

    fun confirmLogout() {
        if (aux.value.loggingOut) return
        aux.update { it.copy(loggingOut = true) }
        viewModelScope.launch {
            auth.logout() // no lanza: limpia la sesión local aunque el servidor no responda
            aux.update { it.copy(loggingOut = false, showLogoutConfirm = false) }
            _events.send(ProfileEvent.LoggedOut)
        }
    }

    // ───────── Eliminar cuenta (doble confirmación) ─────────

    fun startDelete() = aux.update { it.copy(delete = DeleteFlow(DeleteFlow.Stage.Warning)) }
    fun proceedDelete() = aux.update { if (it.delete.stage == DeleteFlow.Stage.Warning) it.copy(delete = DeleteFlow(DeleteFlow.Stage.Typing)) else it }
    fun cancelDelete() = aux.update { if (it.delete.deleting) it else it.copy(delete = DeleteFlow()) }
    fun setDeleteText(text: String) = aux.update { if (it.delete.deleting) it else it.copy(delete = it.delete.copy(typed = text.take(20))) }

    fun confirmDelete() {
        val flow = aux.value.delete
        if (!flow.canConfirm) return
        val user: User? = auth.currentUser.value
        aux.update { it.copy(delete = it.delete.copy(deleting = true)) }
        viewModelScope.launch {
            when (val r = auth.deleteAccount()) {
                is ApiResult.Success -> {
                    user?.id?.let { cycle.save(it, null) } // el periodo es un dato de salud: no debe quedar en el dispositivo
                    aux.update { it.copy(delete = DeleteFlow()) }
                    _events.send(ProfileEvent.AccountDeleted)
                }
                is ApiResult.Failure -> {
                    aux.update { it.copy(delete = it.delete.copy(deleting = false)) }
                    _events.send(ProfileEvent.Error(r.error.message))
                }
            }
        }
    }
}
