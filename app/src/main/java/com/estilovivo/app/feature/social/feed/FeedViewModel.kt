package com.estilovivo.app.feature.social.feed

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.ImageUpload
import com.estilovivo.app.core.data.model.Comment
import com.estilovivo.app.core.data.model.Look
import com.estilovivo.app.core.data.model.TopUser
import com.estilovivo.app.core.data.repo.LookRepository
import com.estilovivo.app.core.data.repo.SocialRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FeedPhase { Loading, Content, Error }

@Immutable
data class FeedUiState(
    val phase: FeedPhase = FeedPhase.Loading,
    val posts: List<Look> = emptyList(),
    val errorMessage: String? = null,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val loadMoreError: String? = null,
    val topUsers: List<TopUser> = emptyList(),
    val publishing: Boolean = false,
    val deletingId: String? = null,
    /** Última publicación propia añadida (la UI la resalta con una animación). */
    val newPostId: String? = null,
)

@Immutable
data class ReplyTarget(val rootId: String, val userName: String)

@Immutable
data class CommentsUiState(
    val lookId: String,
    val lookOwnerId: String?,
    val loading: Boolean = true,
    val error: String? = null,
    val comments: List<Comment> = emptyList(),
    val sending: Boolean = false,
    val replyTo: ReplyTarget? = null,
    val deletingId: String? = null,
)

sealed interface FeedEvent {
    data class Toast(val text: String, val isError: Boolean = false) : FeedEvent
    data object Confetti : FeedEvent
    data object ScrollToTop : FeedEvent
    /** El comentario se envió: la UI vacía el campo. */
    data object CommentSent : FeedEvent
    /** La publicación se creó: la UI cierra la hoja y vacía el borrador. */
    data object Published : FeedEvent
}

/**
 * Feed de looks públicos: paginación por cursor con scroll infinito, me gusta/favorito optimistas con
 * reversión, comentarios con hilos, publicar y borrar.
 */
class FeedViewModel(
    private val looks: LookRepository,
    private val social: SocialRepository,
    private val meProvider: MeProvider,
    private val pageSize: Int = PAGE_SIZE,
) : ViewModel() {

    private val _state = MutableStateFlow(FeedUiState())
    val state: StateFlow<FeedUiState> = _state.asStateFlow()

    private val _comments = MutableStateFlow<CommentsUiState?>(null)
    val comments: StateFlow<CommentsUiState?> = _comments.asStateFlow()

    private val _events = Channel<FeedEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var cursor: String? = null
    private var loadJob: Job? = null
    private var moreJob: Job? = null
    private var commentsJob: Job? = null
    private val likeInFlight = HashSet<String>()
    private val favoriteInFlight = HashSet<String>()

    /** Usuario propio (nivel, XP, id) para la UI. */
    val me = meProvider.me
    val myUserId: String? get() = meProvider.me.value?.id

    init { load() }

    // ───────────── Carga ─────────────

    /** Primera carga (o reintento tras un error). */
    fun load() {
        loadJob?.cancel(); moreJob?.cancel()
        _state.update { it.copy(phase = FeedPhase.Loading, errorMessage = null, loadingMore = false, loadMoreError = null) }
        viewModelScope.launch { loadTopUsers(force = false) }
        loadJob = viewModelScope.launch {
            when (val r = looks.feed(null, pageSize)) {
                is ApiResult.Success -> {
                    cursor = r.value.nextCursor
                    _state.update {
                        it.copy(
                            phase = FeedPhase.Content, posts = r.value.items.filterNot(::isSaleLook).distinctBy { p -> p.id },
                            hasMore = r.value.hasMore && r.value.nextCursor != null, refreshing = false,
                        )
                    }
                    // Una página entera de ventas dejaría el feed vacío con más páginas: sigue buscando.
                    if (_state.value.posts.isEmpty() && _state.value.hasMore) loadMore()
                }
                is ApiResult.Failure -> _state.update { it.copy(phase = FeedPhase.Error, errorMessage = r.error.message, refreshing = false) }
            }
        }
    }

    /** Pull-to-refresh: mantiene lo que hay en pantalla y sólo avisa si falla. */
    fun refresh() {
        if (_state.value.phase != FeedPhase.Content) { load(); return }
        if (_state.value.refreshing) return
        moreJob?.cancel()
        _state.update { it.copy(refreshing = true, loadingMore = false) }
        viewModelScope.launch { loadTopUsers(force = true) }
        loadJob = viewModelScope.launch {
            when (val r = looks.feed(null, pageSize)) {
                is ApiResult.Success -> {
                    cursor = r.value.nextCursor
                    _state.update {
                        it.copy(
                            posts = r.value.items.filterNot(::isSaleLook).distinctBy { p -> p.id },
                            hasMore = r.value.hasMore && r.value.nextCursor != null, refreshing = false, loadMoreError = null,
                        )
                    }
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(refreshing = false) }
                    toast(r.error.message, error = true)
                }
            }
        }
    }

    private suspend fun loadTopUsers(force: Boolean) {
        social.getTopUsers(force).getOrNullSafe()?.let { users -> _state.update { it.copy(topUsers = users) } }
    }

    /** Siguiente página (scroll infinito). Si una página es toda de ventas, sigue hasta encontrar contenido (máx. 5). */
    fun loadMore() {
        val s = _state.value
        if (s.phase != FeedPhase.Content || !s.hasMore || s.loadingMore || s.refreshing || cursor == null) return
        _state.update { it.copy(loadingMore = true, loadMoreError = null) }
        moreJob = viewModelScope.launch {
            var attempts = 0
            while (attempts < MAX_EMPTY_PAGES) {
                attempts++
                when (val r = looks.feed(cursor, pageSize)) {
                    is ApiResult.Success -> {
                        cursor = r.value.nextCursor
                        val more = r.value.hasMore && r.value.nextCursor != null
                        var added = 0
                        _state.update { cur ->
                            val known = cur.posts.mapTo(HashSet()) { it.id }
                            val fresh = r.value.items.filterNot(::isSaleLook).filter { known.add(it.id) }
                            added = fresh.size
                            cur.copy(posts = cur.posts + fresh, hasMore = more)
                        }
                        if (added > 0 || !more) break
                    }
                    is ApiResult.Failure -> {
                        _state.update { it.copy(loadMoreError = r.error.message) }
                        break
                    }
                }
            }
            _state.update { it.copy(loadingMore = false) }
        }
    }

    fun retryLoadMore() = loadMore()

    // ───────────── Me gusta / favorito ─────────────

    /** Me gusta optimista: se refleja al instante, se concilia con el servidor y se revierte si falla. */
    fun toggleLike(lookId: String) {
        val before = _state.value.posts.firstOrNull { it.id == lookId } ?: return
        if (!likeInFlight.add(lookId)) return // doble toque: la segunda pulsación se ignora hasta que responda el servidor
        val optimisticLiked = !before.isLiked
        updatePost(lookId) { it.copy(isLiked = optimisticLiked, likesCount = (it.likesCount + if (optimisticLiked) 1 else -1).coerceAtLeast(0)) }
        viewModelScope.launch {
            when (val r = social.toggleLike(lookId)) {
                is ApiResult.Success -> updatePost(lookId) { it.copy(isLiked = r.value.liked, likesCount = r.value.likesCount) }
                is ApiResult.Failure -> {
                    updatePost(lookId) { it.copy(isLiked = before.isLiked, likesCount = before.likesCount) }
                    toast(r.error.message, error = true)
                }
            }
            likeInFlight.remove(lookId)
        }
    }

    /** Doble toque en la foto: sólo da me gusta (nunca lo quita). */
    fun likeOnly(lookId: String) {
        if (_state.value.posts.firstOrNull { it.id == lookId }?.isLiked == false) toggleLike(lookId)
    }

    fun toggleFavorite(lookId: String) {
        val before = _state.value.posts.firstOrNull { it.id == lookId } ?: return
        if (!favoriteInFlight.add(lookId)) return
        updatePost(lookId) { it.copy(isFavorited = !before.isFavorited) }
        viewModelScope.launch {
            when (val r = social.toggleLookFavorite(lookId)) {
                is ApiResult.Success -> {
                    updatePost(lookId) { it.copy(isFavorited = r.value) }
                    toast(if (r.value) "Guardado en favoritos" else "Quitado de favoritos")
                }
                is ApiResult.Failure -> {
                    updatePost(lookId) { it.copy(isFavorited = before.isFavorited) }
                    toast(r.error.message, error = true)
                }
            }
            favoriteInFlight.remove(lookId)
        }
    }

    // ───────────── Publicar / borrar ─────────────

    /** Publica una foto en el feed (look público). La descripción es el título (máx. 100, límite del servidor). */
    fun publish(image: ImageUpload, description: String) {
        if (_state.value.publishing) return
        val text = description.trim()
        if (text.length > TITLE_MAX) { toast("La descripción no puede superar los $TITLE_MAX caracteres.", error = true); return }
        _state.update { it.copy(publishing = true) }
        viewModelScope.launch {
            when (val r = looks.createWithImage(name = text.ifBlank { "Publicación" }, garmentIds = emptyList(), image = image, isPublic = true)) {
                is ApiResult.Success -> {
                    val me = meProvider.me.value
                    val look = r.value.copy(
                        userId = r.value.userId ?: me?.id,
                        userName = r.value.userName ?: me?.name,
                        userAvatar = r.value.userAvatar ?: me?.avatar,
                    )
                    _state.update {
                        it.copy(
                            publishing = false, phase = FeedPhase.Content, errorMessage = null,
                            posts = listOf(look) + it.posts.filterNot { p -> p.id == look.id }, newPostId = look.id,
                        )
                    }
                    _events.trySend(FeedEvent.Published)
                    _events.trySend(FeedEvent.ScrollToTop)
                    _events.trySend(FeedEvent.Confetti)
                    toast("Publicación compartida")
                    viewModelScope.launch { meProvider.refresh() }
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(publishing = false) }
                    toast(r.error.message, error = true)
                }
            }
        }
    }

    fun delete(lookId: String) {
        if (_state.value.deletingId != null) return
        val post = _state.value.posts.firstOrNull { it.id == lookId } ?: return
        if (post.userId != null && post.userId != myUserId) { toast("Sólo puedes borrar tus publicaciones.", error = true); return }
        _state.update { it.copy(deletingId = lookId) }
        viewModelScope.launch {
            when (val r = looks.delete(lookId)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(posts = it.posts.filterNot { p -> p.id == lookId }, deletingId = null) }
                    toast("Publicación eliminada")
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(deletingId = null) }
                    toast(r.error.message, error = true)
                }
            }
        }
    }

    // ───────────── Comentarios ─────────────

    fun openComments(lookId: String) {
        val post = _state.value.posts.firstOrNull { it.id == lookId } ?: return
        _comments.value = CommentsUiState(lookId = lookId, lookOwnerId = post.userId)
        fetchComments()
    }

    fun closeComments() {
        commentsJob?.cancel()
        _comments.value = null
    }

    fun retryComments() {
        _comments.update { it?.copy(loading = true, error = null) }
        fetchComments()
    }

    private fun fetchComments() {
        val lookId = _comments.value?.lookId ?: return
        commentsJob?.cancel()
        commentsJob = viewModelScope.launch {
            when (val r = social.getComments(lookId)) {
                is ApiResult.Success -> {
                    _comments.update { if (it?.lookId == lookId) it.copy(loading = false, error = null, comments = r.value) else it }
                    syncCount(lookId, r.value)
                }
                is ApiResult.Failure -> _comments.update { if (it?.lookId == lookId) it.copy(loading = false, error = r.error.message) else it }
            }
        }
    }

    fun setReplyTo(target: ReplyTarget?) = _comments.update { it?.copy(replyTo = target) }

    fun sendComment(text: String) {
        val c = _comments.value ?: return
        if (c.sending || c.loading) return
        commentError(text)?.let { toast(it, error = true); return }
        _comments.update { it?.copy(sending = true) }
        viewModelScope.launch {
            val reply = c.replyTo
            when (val r = social.addComment(c.lookId, text.trim(), reply?.rootId)) {
                is ApiResult.Success -> {
                    val created = r.value
                    _comments.update { cur ->
                        if (cur?.lookId != c.lookId) cur else {
                            val list = if (reply == null) listOf(created) + cur.comments
                            else cur.comments.map { root -> if (root.id == reply.rootId) root.copy(replies = root.replies + created) else root }
                            cur.copy(sending = false, comments = list, replyTo = null)
                        }
                    }
                    _comments.value?.let { syncCount(it.lookId, it.comments) }
                    _events.trySend(FeedEvent.CommentSent)
                }
                is ApiResult.Failure -> {
                    _comments.update { it?.copy(sending = false) }
                    toast(r.error.message, error = true)
                }
            }
        }
    }

    /** Puede borrar quien escribió el comentario o la dueña del look (el servidor lo comprueba igualmente). */
    fun canDelete(comment: Comment): Boolean {
        val me = myUserId ?: return false
        return comment.userId == me || _comments.value?.lookOwnerId == me
    }

    fun deleteComment(comment: Comment) {
        val c = _comments.value ?: return
        if (c.deletingId != null || !canDelete(comment)) return
        _comments.update { it?.copy(deletingId = comment.id) }
        viewModelScope.launch {
            when (val r = social.deleteComment(comment.id)) {
                is ApiResult.Success -> {
                    _comments.update { cur ->
                        if (cur?.lookId != c.lookId) cur else {
                            val list = cur.comments.filterNot { it.id == comment.id }
                                .map { root -> root.copy(replies = root.replies.filterNot { it.id == comment.id }) }
                            cur.copy(
                                comments = list, deletingId = null,
                                replyTo = cur.replyTo?.takeIf { rt -> list.any { it.id == rt.rootId } },
                            )
                        }
                    }
                    _comments.value?.let { syncCount(it.lookId, it.comments) }
                }
                is ApiResult.Failure -> {
                    _comments.update { it?.copy(deletingId = null) }
                    toast(r.error.message, error = true)
                }
            }
        }
    }

    /** El contador del feed refleja siempre lo que acabamos de ver en la hoja. */
    private fun syncCount(lookId: String, roots: List<Comment>) {
        val total = countAllComments(roots)
        updatePost(lookId) { it.copy(commentsCount = total) }
    }

    // ───────────── Utilidades ─────────────

    fun consumeNewPost() = _state.update { it.copy(newPostId = null) }

    private fun updatePost(id: String, transform: (Look) -> Look) {
        _state.update { s -> s.copy(posts = s.posts.map { if (it.id == id) transform(it) else it }) }
    }

    private fun toast(text: String, error: Boolean = false) {
        _events.trySend(FeedEvent.Toast(text, error))
    }

    private fun <T> ApiResult<T>.getOrNullSafe(): T? = (this as? ApiResult.Success)?.value

    companion object {
        const val PAGE_SIZE = 12
        const val MAX_EMPTY_PAGES = 5
        const val TITLE_MAX = 100
    }
}
