package com.estilovivo.app.feature.social.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.estilovivo.app.app.ChallengeHistory
import com.estilovivo.app.app.LocalNavigator
import com.estilovivo.app.app.MainTab
import com.estilovivo.app.app.StoryViewer
import com.estilovivo.app.app.UserProfile
import com.estilovivo.app.core.design.EvSpace
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.LocalEvToast
import com.estilovivo.app.core.design.components.EvConfetti
import com.estilovivo.app.core.design.components.EvEmptyState
import com.estilovivo.app.core.design.components.EvHeadline
import com.estilovivo.app.core.design.components.EvLoadingDots
import com.estilovivo.app.core.design.components.EvPillButton
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.staggeredEntrance
import org.koin.androidx.compose.koinViewModel

/** Pestaña Feed: historias, reto semanal, ranking y publicaciones de la comunidad. */
@Composable
fun FeedTab() {
    val vm: FeedViewModel = koinViewModel()
    val storiesVm: StoriesViewModel = koinViewModel()
    val challengeVm: ChallengeViewModel = koinViewModel()
    val nav = LocalNavigator.current
    val toast = LocalEvToast.current
    val context = LocalContext.current
    val c = EvTheme.colors

    val feed by vm.state.collectAsStateWithLifecycle()
    val comments by vm.comments.collectAsStateWithLifecycle()
    val me by vm.me.collectAsStateWithLifecycle()
    val stories by storiesVm.state.collectAsStateWithLifecycle()
    val viewed by storiesVm.viewedIds.collectAsStateWithLifecycle()
    val challenge by challengeVm.state.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    var confetti by remember { mutableIntStateOf(0) }
    var commentsClear by remember { mutableIntStateOf(0) }
    var showPublish by rememberSaveable { mutableStateOf(false) }
    var publishPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    var publishText by rememberSaveable { mutableStateOf("") }
    var showStoryComposer by rememberSaveable { mutableStateOf(false) }
    var showChallenge by rememberSaveable { mutableStateOf(false) }
    var challengePhoto by rememberSaveable { mutableStateOf<String?>(null) }
    var challengeText by rememberSaveable { mutableStateOf("") }
    var deletePostId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDeleteParticipation by rememberSaveable { mutableStateOf(false) }

    // Eventos (toasts, confeti, cerrar hojas)
    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is FeedEvent.Toast -> if (e.isError) toast.error(e.text) else toast.success(e.text)
                FeedEvent.Confetti -> confetti++
                FeedEvent.ScrollToTop -> listState.animateScrollToItem(0)
                FeedEvent.CommentSent -> commentsClear++
                FeedEvent.Published -> { showPublish = false; publishPhoto = null; publishText = "" }
            }
        }
    }
    LaunchedEffect(storiesVm) {
        storiesVm.events.collect { e ->
            when (e) {
                is FeedEvent.Toast -> if (e.isError) toast.error(e.text) else toast.success(e.text)
                FeedEvent.Confetti -> confetti++
                FeedEvent.Published -> showStoryComposer = false
                else -> Unit
            }
        }
    }
    LaunchedEffect(challengeVm) {
        challengeVm.events.collect { e ->
            when (e) {
                is FeedEvent.Toast -> if (e.isError) toast.error(e.text) else toast.success(e.text)
                FeedEvent.Confetti -> confetti++
                FeedEvent.Published -> { showChallenge = false; challengePhoto = null; challengeText = "" }
                else -> Unit
            }
        }
    }

    // Al volver (p. ej. del visor de historias) se refrescan los aros sin parpadeo
    var firstResume by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (firstResume) firstResume = false else storiesVm.load(silent = true)
    }

    // Scroll infinito
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, feed.posts.size, feed.hasMore, feed.phase) { if (nearEnd) vm.loadMore() }

    val levels = remember(feed.topUsers, me) {
        buildMap {
            feed.topUsers.forEach { put(it.id, it.level) }
            me?.let { put(it.id, it.level) }
        }
    }
    val firstPostOfUser = remember(feed.posts) {
        val seen = HashSet<String?>()
        feed.posts.filter { seen.add(it.userId) }.mapTo(HashSet()) { it.id }
    }
    val myId = me?.id

    fun openProfile(userId: String?) {
        if (userId == null) return
        if (userId == myId) nav.goToTab(MainTab.Profile) else nav.navigate(UserProfile(userId))
    }

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = feed.refreshing,
            onRefresh = { vm.refresh(); storiesVm.load(silent = true); challengeVm.load(silent = true) },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = EvSpace.screen, end = EvSpace.screen, top = 6.dp, bottom = 132.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item(key = "header") {
                    Column(Modifier.staggeredEntrance(0), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        EvHeadline("Lo último de\n*tu comunidad*")
                        PublishPrompt(avatar = me?.avatar, name = me?.name.orEmpty(), onClick = { showPublish = true })
                    }
                }
                item(key = "stories") {
                    StoriesCard(
                        state = stories, viewed = viewed, myAvatar = me?.avatar, myName = me?.name.orEmpty(),
                        onAdd = { showStoryComposer = true }, onOpen = { nav.navigate(StoryViewer(it)) },
                        onRetry = { storiesVm.load() }, modifier = Modifier.staggeredEntrance(1),
                    )
                }
                item(key = "challenge") {
                    ChallengeCard(
                        state = challenge, me = me,
                        onParticipate = { challengeVm.clearFormError(); showChallenge = true },
                        onDeleteSubmission = { confirmDeleteParticipation = true },
                        onHistory = { nav.navigate(ChallengeHistory) },
                        onRetry = { challengeVm.load() }, modifier = Modifier.staggeredEntrance(2),
                    )
                }
                if (feed.topUsers.isNotEmpty()) {
                    item(key = "top") { TopUsersRow(feed.topUsers, onClick = { openProfile(it.id) }, modifier = Modifier.staggeredEntrance(3)) }
                }

                item(key = "postsHeader") {
                    Column(Modifier.padding(top = 6.dp)) {
                        EvOverline("Publicaciones")
                        Text("Para ti", style = EvType.title, color = c.ink)
                    }
                }

                when (feed.phase) {
                    FeedPhase.Loading -> items(2, key = { "skeleton$it" }) { FeedPostSkeleton() }
                    FeedPhase.Error -> item(key = "error") {
                        EvEmptyState(
                            Icons.Rounded.CloudOff, "No pudimos cargar el feed", feed.errorMessage ?: "Revisa tu conexión e inténtalo de nuevo.",
                            actionText = "Reintentar", onAction = vm::load,
                        )
                    }
                    FeedPhase.Content -> if (feed.posts.isEmpty() && !feed.loadingMore) {
                        item(key = "empty") {
                            EvEmptyState(
                                Icons.Rounded.AutoAwesome, "Sé la primera en inspirar", "Comparte tu estilo y conecta con otras amantes de la moda.",
                                actionText = "Publicar un look", onAction = { showPublish = true },
                            )
                        }
                    } else {
                        items(feed.posts, key = { it.id }) { post ->
                            FeedPostCard(
                                post = post,
                                isMine = post.userId != null && post.userId == myId,
                                authorLevel = post.userId?.let(levels::get),
                                shareAvatar = post.id in firstPostOfUser,
                                deleting = feed.deletingId == post.id,
                                onAuthor = { openProfile(post.userId) },
                                onToggleLike = { vm.toggleLike(post.id) },
                                onDoubleTapLike = { vm.likeOnly(post.id) },
                                onComments = { vm.openComments(post.id) },
                                onShare = { shareText(context, post.name.ifBlank { "Look en EstiloVivo" }, lookShareText(post)) },
                                onFavorite = { vm.toggleFavorite(post.id) },
                                onDelete = { deletePostId = post.id },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        item(key = "footer") { FeedFooter(feed, onRetry = vm::retryLoadMore) }
                    }
                }
            }
        }
        EvConfetti(confetti, Modifier.fillMaxSize())
    }

    // ───────────── Hojas y diálogos ─────────────
    if (showPublish) {
        PublishSheet(
            photo = publishPhoto, text = publishText, publishing = feed.publishing,
            onPhoto = { publishPhoto = it }, onText = { publishText = it },
            onPublish = { upload, text -> vm.publish(upload, text) },
            onDismiss = { showPublish = false },
        )
    }
    if (showStoryComposer) {
        StoryComposerSheet(
            creating = stories.creating, garments = stories.garments, onLoadGarments = storiesVm::loadGarments,
            onPublish = { type, text, image, url -> storiesVm.create(type, text, image, url) },
            onDismiss = { showStoryComposer = false },
        )
    }
    val currentChallenge = challenge.challenge
    if (showChallenge && currentChallenge != null) {
        ChallengeSheet(
            challenge = currentChallenge, photo = challengePhoto, description = challengeText,
            submitting = challenge.submitting, error = challenge.formError,
            onPhoto = { challengePhoto = it; challengeVm.clearFormError() },
            onDescription = { challengeText = it; if (challenge.formError != null) challengeVm.clearFormError() },
            onSubmit = { image, text -> challengeVm.submit(image, text) },
            onDismiss = { showChallenge = false },
        )
    }
    comments?.let { state ->
        CommentsSheet(
            state = state, canDelete = vm::canDelete, clearSignal = commentsClear,
            onDismiss = vm::closeComments, onRetry = vm::retryComments, onSend = vm::sendComment,
            onReply = vm::setReplyTo, onDelete = vm::deleteComment,
            onOpenProfile = { id -> vm.closeComments(); openProfile(id) },
        )
    }
    deletePostId?.let { id ->
        EvConfirmDialog(
            title = "¿Eliminar publicación?",
            message = "Se borrará para siempre y dejará de verse en el feed.",
            confirmText = "Eliminar",
            onConfirm = { deletePostId = null; vm.delete(id) },
            onDismiss = { deletePostId = null },
        )
    }
    if (confirmDeleteParticipation) {
        EvConfirmDialog(
            title = "¿Eliminar tu participación?",
            message = "Perderás los XP que ganaste con este reto, pero podrás volver a participar.",
            confirmText = "Eliminar",
            onConfirm = { confirmDeleteParticipation = false; challengeVm.deleteSubmission() },
            onDismiss = { confirmDeleteParticipation = false },
        )
    }
}


@Composable
private fun PublishPrompt(avatar: String?, name: String, onClick: () -> Unit) {
    val c = EvTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(CircleShape).background(c.surface)
            .evClickable(pressedScale = 0.98f, onClick = onClick)
            .semantics { contentDescription = "Publicar un look en el feed" }
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EvAvatar(avatar, name.ifBlank { "Tú" }, 44.dp, contentDescription = null)
        Text("¿Qué look llevas hoy?", style = EvType.body, color = c.inkSoft, modifier = Modifier.weight(1f))
        Box(Modifier.size(44.dp).clip(CircleShape).background(c.ink), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.CameraAlt, null, tint = c.onInk, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun FeedFooter(feed: FeedUiState, onRetry: () -> Unit) {
    val c = EvTheme.colors
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        when {
            feed.loadMoreError != null -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(feed.loadMoreError, style = EvType.bodySmall, color = c.inkSoft)
                EvPillButton("Reintentar", onRetry, height = 42.dp, style = com.estilovivo.app.core.design.components.EvButtonStyle.Secondary)
            }
            feed.loadingMore -> EvLoadingDots()
            !feed.hasMore -> Text("Has visto todo por ahora ✨", style = EvType.caption, color = c.inkMuted)
        }
    }
}
