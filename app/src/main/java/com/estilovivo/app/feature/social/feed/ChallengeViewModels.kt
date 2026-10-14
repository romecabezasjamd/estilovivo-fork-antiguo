package com.estilovivo.app.feature.social.feed

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.ImageUpload
import com.estilovivo.app.core.data.model.Challenge
import com.estilovivo.app.core.data.model.ChallengeHistory
import com.estilovivo.app.core.data.model.ChallengeSubmission
import com.estilovivo.app.core.data.repo.ChallengeRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class ChallengeUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val challenge: Challenge? = null,
    /** Mi participación en el reto actual (si ya participé). */
    val submission: ChallengeSubmission? = null,
    val submitting: Boolean = false,
    val deleting: Boolean = false,
    /** Error de validación o del servidor mostrado dentro de la hoja de participación. */
    val formError: String? = null,
)

/** Tarjeta del reto semanal: participar, completado y eliminar participación. */
class ChallengeViewModel(
    private val challenges: ChallengeRepository,
    private val meProvider: MeProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(ChallengeUiState())
    val state: StateFlow<ChallengeUiState> = _state.asStateFlow()

    private val _events = Channel<FeedEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init { load() }

    /** [silent] = recarga sin esqueleto (pull-to-refresh): si falla, se queda lo que había. */
    fun load(silent: Boolean = false) {
        if (!silent) _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = challenges.getCurrent()) {
                is ApiResult.Success -> {
                    _state.update { it.copy(challenge = r.value, loading = false, error = null) }
                    loadSubmission(r.value.id)
                }
                is ApiResult.Failure -> _state.update {
                    if (silent && it.challenge != null) it.copy(loading = false) else it.copy(loading = false, error = r.error.message)
                }
            }
        }
    }

    private suspend fun loadSubmission(challengeId: String) {
        val mine = (challenges.getMySubmissions() as? ApiResult.Success)?.value?.firstOrNull { it.challengeId == challengeId }
        _state.update { it.copy(submission = mine) }
    }

    fun clearFormError() = _state.update { it.copy(formError = null) }

    /** Valida (foto + 10..500 caracteres) y envía. Premia con confeti y aviso de XP / nivel. */
    fun submit(image: ImageUpload?, description: String) {
        val s = _state.value
        val challenge = s.challenge ?: return
        if (s.submitting || s.submission != null) return
        challengeSubmitError(image != null, description)?.let { msg -> _state.update { it.copy(formError = msg) }; return }
        val upload = image ?: return
        _state.update { it.copy(submitting = true, formError = null) }
        viewModelScope.launch {
            when (val r = challenges.submit(challenge.id, description.trim(), upload)) {
                is ApiResult.Success -> {
                    val res = r.value
                    _state.update { it.copy(submitting = false, submission = res.submission, formError = null) }
                    _events.trySend(FeedEvent.Published)
                    _events.trySend(FeedEvent.Confetti)
                    if (res.leveledUp) _events.trySend(FeedEvent.Toast("¡Subes al nivel ${res.level}! +${challenge.reward} XP del reto", false))
                    else _events.trySend(FeedEvent.Toast(res.validationMessage ?: "+${challenge.reward} XP por participar en el reto", false))
                    meProvider.refresh()
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(submitting = false, formError = r.error.message) }
                    // Si el servidor dice que ya participaste, sincroniza el estado para mostrar "Completado".
                    if (r.error.message.contains("Ya participaste", ignoreCase = true)) loadSubmission(challenge.id)
                }
            }
        }
    }

    fun deleteSubmission() {
        val s = _state.value
        val sub = s.submission ?: return
        if (s.deleting) return
        _state.update { it.copy(deleting = true) }
        viewModelScope.launch {
            when (val r = challenges.deleteSubmission(sub.id)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(deleting = false, submission = null) }
                    _events.trySend(FeedEvent.Toast("Participación eliminada. Puedes volver a participar.", false))
                    meProvider.refresh()
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(deleting = false) }
                    _events.trySend(FeedEvent.Toast(r.error.message, true))
                }
            }
        }
    }
}

@Immutable
data class ChallengeHistoryUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val history: ChallengeHistory? = null,
    val refreshing: Boolean = false,
)

class ChallengeHistoryViewModel(private val challenges: ChallengeRepository) : ViewModel() {
    private val _state = MutableStateFlow(ChallengeHistoryUiState())
    val state: StateFlow<ChallengeHistoryUiState> = _state.asStateFlow()

    init { load() }

    fun load(refresh: Boolean = false) {
        _state.update { if (refresh) it.copy(refreshing = true) else it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = challenges.getHistory()) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, refreshing = false, error = null, history = r.value) }
                is ApiResult.Failure -> _state.update {
                    if (it.history != null) it.copy(loading = false, refreshing = false) else it.copy(loading = false, refreshing = false, error = r.error.message)
                }
            }
        }
    }
}
