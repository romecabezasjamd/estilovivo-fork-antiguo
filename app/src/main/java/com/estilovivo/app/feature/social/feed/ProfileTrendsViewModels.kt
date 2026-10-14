package com.estilovivo.app.feature.social.feed

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estilovivo.app.core.data.ApiErrorKind
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.model.PublicProfile
import com.estilovivo.app.core.data.model.Trend
import com.estilovivo.app.core.data.repo.ChatRepository
import com.estilovivo.app.core.data.repo.SocialRepository
import com.estilovivo.app.core.data.repo.TrendsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// Perfil de otra usuaria
// ─────────────────────────────────────────────────────────────────────────────

enum class ProfileTab { Looks, ForSale }

/** Aspecto del botón principal del perfil. */
enum class FollowButton(val label: String) {
    Follow("Seguir"), Request("Solicitar"), Following("Siguiendo"), Requested("Solicitud enviada"), Hidden("")
}

fun followButton(isMe: Boolean, isPublic: Boolean, following: Boolean, pending: Boolean): FollowButton = when {
    isMe -> FollowButton.Hidden
    following -> FollowButton.Following
    pending -> FollowButton.Requested
    isPublic -> FollowButton.Follow
    else -> FollowButton.Request
}

@Immutable
data class ProfileUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val notFound: Boolean = false,
    val profile: PublicProfile? = null,
    val isMe: Boolean = false,
    val following: Boolean = false,
    val pending: Boolean = false,
    val followersCount: Int = 0,
    val followBusy: Boolean = false,
    val messageBusy: Boolean = false,
    val refreshing: Boolean = false,
    val tab: ProfileTab = ProfileTab.Looks,
) {
    val button: FollowButton
        get() = followButton(isMe, profile?.isProfilePublic ?: true, following, pending)

    /** Perfil privado que no sigo: looks y prendas ocultos tras un candado. */
    val locked: Boolean
        get() = profile?.isProfilePublic == false && !following && !isMe
}

sealed interface ProfileEvent {
    data class Toast(val text: String, val isError: Boolean = false) : ProfileEvent
    data class OpenChat(val conversationId: String) : ProfileEvent
}

/**
 * Reglas de seguir del servidor: perfil público => alterna seguir; privado => la primera pulsación crea una
 * solicitud, la segunda la cancela; si ya se sigue, siempre se deja de seguir (y el contenido privado se oculta).
 */
class UserProfileViewModel(
    private val userId: String,
    private val social: SocialRepository,
    private val chat: ChatRepository,
    private val meProvider: MeProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState(isMe = meProvider.me.value?.id == userId))
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    private val _events = Channel<ProfileEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init { load() }

    fun load(refresh: Boolean = false) {
        _state.update { if (refresh && it.profile != null) it.copy(refreshing = true) else it.copy(loading = true, error = null, notFound = false) }
        viewModelScope.launch {
            when (val r = social.getUserProfile(userId)) {
                is ApiResult.Success -> apply(r.value)
                is ApiResult.Failure -> _state.update {
                    when {
                        it.profile != null -> it.copy(loading = false, refreshing = false)
                        r.error.kind == ApiErrorKind.NotFound -> it.copy(loading = false, refreshing = false, notFound = true, error = "Esta persona no existe o ya no está disponible.")
                        else -> it.copy(loading = false, refreshing = false, error = r.error.message)
                    }
                }
            }
            if (refresh) _state.update { it.copy(refreshing = false) }
        }
    }

    private fun apply(p: PublicProfile) = _state.update {
        it.copy(
            loading = false, refreshing = false, error = null, notFound = false, profile = p,
            isMe = meProvider.me.value?.id == p.id,
            following = p.isFollowing, pending = p.hasPendingRequest && !p.isFollowing, followersCount = p.followersCount,
        )
    }

    fun selectTab(tab: ProfileTab) = _state.update { it.copy(tab = tab) }

    fun toggleFollow() {
        val s = _state.value
        val p = s.profile ?: return
        if (s.isMe || s.followBusy) return
        _state.update { it.copy(followBusy = true) }
        viewModelScope.launch {
            when (val r = social.toggleFollow(userId)) {
                is ApiResult.Success -> {
                    val nowFollowing = r.value.following
                    val nowPending = r.value.requesting && !nowFollowing
                    // El contador sólo cambia si cambia el seguimiento real (cancelar una solicitud no lo toca).
                    val delta = (if (nowFollowing) 1 else 0) - (if (s.following) 1 else 0)
                    _state.update {
                        it.copy(
                            followBusy = false, following = nowFollowing, pending = nowPending,
                            followersCount = (it.followersCount + delta).coerceAtLeast(0),
                        )
                    }
                    val first = p.name.substringBefore(' ')
                    toast(
                        when {
                            nowPending -> "Solicitud enviada a $first"
                            nowFollowing -> "Ahora sigues a $first"
                            s.pending -> "Solicitud cancelada"
                            else -> "Has dejado de seguir a $first"
                        },
                    )
                    // Al (dejar de) seguir un perfil privado cambia lo que se puede ver: recarga el contenido.
                    if (!p.isProfilePublic && nowFollowing != s.following) load(refresh = true)
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(followBusy = false) }
                    toast(r.error.message, true)
                }
            }
        }
    }

    /** Abre (o recupera) la conversación con esta persona. */
    fun message() {
        val s = _state.value
        if (s.isMe || s.messageBusy) return
        _state.update { it.copy(messageBusy = true) }
        viewModelScope.launch {
            when (val r = chat.createConversation(targetUserId = userId)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(messageBusy = false) }
                    _events.trySend(ProfileEvent.OpenChat(r.value.id))
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(messageBusy = false) }
                    toast(r.error.message, true)
                }
            }
        }
    }

    private fun toast(text: String, error: Boolean = false) { _events.trySend(ProfileEvent.Toast(text, error)) }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tendencias
// ─────────────────────────────────────────────────────────────────────────────

const val ALL_TRENDS = "Todo"

@Immutable
data class TrendsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val trends: List<Trend> = emptyList(),
    val refreshing: Boolean = false,
    val category: String = ALL_TRENDS,
) {
    val categories: List<String>
        get() = listOf(ALL_TRENDS) + trends.map { it.category.trim() }.filter { it.isNotEmpty() }.distinct()

    val visible: List<Trend>
        get() = if (category == ALL_TRENDS) trends else trends.filter { it.category.trim() == category }
}

class TrendsViewModel(private val trends: TrendsRepository) : ViewModel() {
    private val _state = MutableStateFlow(TrendsUiState())
    val state: StateFlow<TrendsUiState> = _state.asStateFlow()

    private val _events = Channel<String>(Channel.BUFFERED)
    val errors = _events.receiveAsFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch { fetch(force = false) }
    }

    fun refresh() {
        if (_state.value.refreshing) return
        _state.update { it.copy(refreshing = true) }
        viewModelScope.launch { fetch(force = true) }
    }

    private suspend fun fetch(force: Boolean) {
        when (val r = trends.getTrends(force)) {
            is ApiResult.Success -> _state.update {
                // Si la categoría elegida desaparece al refrescar, vuelve a "Todo"
                val cat = if (it.category == ALL_TRENDS || r.value.any { t -> t.category.trim() == it.category }) it.category else ALL_TRENDS
                it.copy(loading = false, refreshing = false, error = null, trends = r.value, category = cat)
            }
            is ApiResult.Failure -> {
                val hadData = _state.value.trends.isNotEmpty()
                _state.update { if (hadData) it.copy(loading = false, refreshing = false) else it.copy(loading = false, refreshing = false, error = r.error.message) }
                if (hadData) _events.trySend(r.error.message)
            }
        }
    }

    fun selectCategory(category: String) = _state.update { it.copy(category = category) }
}
