package com.estilovivo.app.feature.profile

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.model.FollowRequest
import com.estilovivo.app.core.data.repo.AuthRepository
import com.estilovivo.app.core.data.repo.SocialRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ─────────────────────────────── Cambiar contraseña ───────────────────────────────

@Immutable
data class ChangePasswordUiState(
    val current: String = "",
    val new: String = "",
    val confirm: String = "",
    val errors: ProfileRules.PasswordErrors = ProfileRules.PasswordErrors(),
    val saving: Boolean = false,
    /** Tras un intento fallido se valida en vivo para que el error desaparezca al corregirlo. */
    val showErrors: Boolean = false,
) {
    val canSubmit: Boolean get() = current.isNotEmpty() && new.isNotEmpty() && confirm.isNotEmpty() && !saving
}

class ChangePasswordViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(ChangePasswordUiState())
    val state: StateFlow<ChangePasswordUiState> = _state.asStateFlow()
    private val _events = Channel<ProfileEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onCurrent(v: String) = edit { it.copy(current = v.take(ProfileRules.PASSWORD_MAX)) }
    fun onNew(v: String) = edit { it.copy(new = v.take(ProfileRules.PASSWORD_MAX + 1)) }
    fun onConfirm(v: String) = edit { it.copy(confirm = v.take(ProfileRules.PASSWORD_MAX + 1)) }

    private fun edit(block: (ChangePasswordUiState) -> ChangePasswordUiState) = _state.update { s ->
        val n = block(s)
        if (n.showErrors) n.copy(errors = ProfileRules.validatePassword(n.current, n.new, n.confirm)) else n
    }

    fun submit() {
        val s = _state.value
        if (s.saving) return
        val errors = ProfileRules.validatePassword(s.current, s.new, s.confirm)
        if (errors.any) {
            _state.update { it.copy(errors = errors, showErrors = true) }
            return
        }
        _state.update { it.copy(saving = true, errors = ProfileRules.PasswordErrors()) }
        viewModelScope.launch {
            when (val r = auth.changePassword(s.current, s.new)) {
                is ApiResult.Success -> {
                    // El repositorio ya guarda los tokens nuevos del servidor (las demás sesiones se cierran).
                    _state.value = ChangePasswordUiState()
                    _events.send(ProfileEvent.Success("Contraseña actualizada. Cerramos tus otras sesiones por seguridad."))
                    _events.send(ProfileEvent.Saved)
                }
                is ApiResult.Failure -> {
                    val msg = r.error.message
                    _state.update {
                        it.copy(
                            saving = false,
                            errors = when {
                                msg.contains("actual", ignoreCase = true) && msg.contains("correcta", ignoreCase = true) ->
                                    ProfileRules.PasswordErrors(current = msg)
                                msg.contains("distinta", ignoreCase = true) -> ProfileRules.PasswordErrors(new = msg)
                                else -> it.errors
                            },
                            showErrors = true,
                        )
                    }
                    _events.send(ProfileEvent.Error(msg))
                }
            }
        }
    }
}

// ─────────────────────────────── Solicitudes de seguimiento ───────────────────────────────

@Immutable
data class FollowRequestsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val requests: List<FollowRequest> = emptyList(),
    /** Solicitudes que se están resolviendo (aprobar/rechazar en vuelo). */
    val busy: Set<String> = emptySet(),
    val isPublic: Boolean = true,
)

class FollowRequestsViewModel(
    private val social: SocialRepository,
    private val auth: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(FollowRequestsUiState(isPublic = auth.currentUser.value?.isProfilePublic ?: true))
    val state: StateFlow<FollowRequestsUiState> = _state.asStateFlow()
    private val _events = Channel<ProfileEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = social.getFollowRequests()) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, requests = r.value) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = r.error.message) }
            }
        }
    }

    fun approve(id: String) = resolve(id, approve = true)
    fun reject(id: String) = resolve(id, approve = false)

    /** Optimista: la fila sale al instante y vuelve a su sitio si el servidor falla. */
    private fun resolve(id: String, approve: Boolean) {
        val before = _state.value
        val index = before.requests.indexOfFirst { it.id == id }
        if (index < 0 || id in before.busy) return
        val req = before.requests[index]
        _state.update { it.copy(requests = it.requests.filterNot { r -> r.id == id }, busy = it.busy + id) }
        viewModelScope.launch {
            val r = if (approve) social.approveFollowRequest(id) else social.rejectFollowRequest(id)
            when (r) {
                is ApiResult.Success -> {
                    _state.update { it.copy(busy = it.busy - id) }
                    _events.send(
                        ProfileEvent.Success(
                            if (approve) "${req.requester.name} ya te sigue." else "Solicitud de ${req.requester.name} rechazada.",
                        ),
                    )
                    if (approve) auth.refreshMe() // actualiza el contador de seguidores
                }
                is ApiResult.Failure -> {
                    _state.update {
                        val restored = it.requests.toMutableList().apply { add(index.coerceAtMost(size), req) }
                        it.copy(requests = restored, busy = it.busy - id)
                    }
                    _events.send(ProfileEvent.Error(r.error.message))
                }
            }
        }
    }
}
