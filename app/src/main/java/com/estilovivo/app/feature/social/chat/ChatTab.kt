package com.estilovivo.app.feature.social.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.estilovivo.app.app.Chat
import com.estilovivo.app.app.LocalNavigator
import com.estilovivo.app.core.data.model.Conversation
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvSpace
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.EvEmptyState
import com.estilovivo.app.core.design.components.EvHeadline
import com.estilovivo.app.core.design.components.EvImage
import com.estilovivo.app.core.design.components.EvShimmerBox
import com.estilovivo.app.core.design.components.LocalEvToast
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.staggeredEntrance
import org.koin.compose.viewmodel.koinViewModel

/**
 * Pestaña Chat de Social: conversaciones ordenadas por actividad, en tiempo real.
 * [onExploreShop] lo pasa quien aloja las secciones (para llevar a la tienda desde el estado vacío); sin él no se muestra el botón.
 */
@Composable
fun ChatTab(modifier: Modifier = Modifier, onExploreShop: (() -> Unit)? = null) {
    val vm = koinViewModel<ConversationsViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val toast = LocalEvToast.current
    LaunchedEffect(vm) { vm.errors.collect { toast.error(it) } }
    // Al volver desde una conversación, se actualiza sin mostrar indicador de carga
    LifecycleResumeEffect(vm) {
        vm.refresh(silent = true)
        onPauseOrDispose { }
    }
    ConversationsScreen(
        state = state,
        onOpen = { nav.navigate(Chat(it)) },
        onRefresh = { vm.refresh(silent = true, userInitiated = true) },
        onRetry = { vm.refresh(silent = false) },
        onExploreShop = onExploreShop,
        modifier = modifier,
    )
}

@Composable
fun ConversationsScreen(
    state: ConversationsUiState,
    onOpen: (String) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onExploreShop: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = EvSpace.screen, end = EvSpace.screen, top = 12.dp, bottom = 132.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") {
                EvHeadline(
                    "Tus *conversaciones*",
                    Modifier.padding(end = 56.dp, bottom = 8.dp).staggeredEntrance(0),
                )
            }
            when {
                state.loading && state.conversations.isEmpty() -> items(5, key = { "sk$it" }) { ConversationSkeleton() }
                state.error != null && state.conversations.isEmpty() -> item(key = "error") {
                    EvEmptyState(
                        Icons.Rounded.CloudOff, "No hemos podido cargar tus chats", state.error,
                        actionText = "Reintentar", onAction = onRetry,
                    )
                }
                state.conversations.isEmpty() -> item(key = "empty") {
                    EvEmptyState(
                        icon = Icons.Rounded.ChatBubbleOutline,
                        title = "Aún no tienes conversaciones",
                        message = "Entra en un artículo de la tienda y pulsa «Chatear» para hablar con quien lo vende.",
                        actionText = if (onExploreShop != null) "Explorar la tienda" else null,
                        onAction = onExploreShop,
                    )
                }
                else -> items(state.conversations, key = { it.id }) { conversation ->
                    ConversationRow(
                        conversation = conversation,
                        meId = state.meId,
                        onClick = { onOpen(conversation.id) },
                        modifier = Modifier.animateItem().staggeredEntrance(1),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(conversation: Conversation, meId: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    val other = conversation.otherUser
    val name = other?.name?.ifBlank { null } ?: "Conversación"
    val last = conversation.lastMessage
    val preview = when {
        last == null -> "Aún no hay mensajes"
        last.content.isBlank() -> "Foto"
        else -> last.content.replace('\n', ' ')
    }
    val fromMe = last?.sender?.id != null && last.sender?.id == meId
    val time = ChatTime.relative(last?.createdAt ?: conversation.updatedAt)
    Row(
        modifier
            .fillMaxWidth()
            .clip(EvShapes.card)
            .background(c.surface)
            .evClickable(pressedScale = 0.97f, role = null, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "Conversación con $name. ${if (fromMe) "Tú: " else ""}$preview. $time"
            }
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChatAvatar(other?.avatar, name, 52.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = EvType.subtitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(time, style = EvType.caption, color = c.inkMuted, modifier = Modifier.padding(start = 8.dp))
            }
            Text(
                (if (fromMe) "Tú: " else "") + preview,
                style = EvType.bodySmall, color = c.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (conversation.itemTitle != null) {
                Row(
                    Modifier.padding(top = 4.dp).clip(CircleShape).background(c.surfaceAlt).padding(start = 3.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    EvImage(conversation.itemImage, null, Modifier.size(22.dp).clip(CircleShape))
                    Text(conversation.itemTitle, style = EvType.caption, color = c.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ConversationSkeleton() {
    val c = EvTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(EvShapes.card).background(c.surface).padding(14.dp).clearAndSetSemantics { contentDescription = "Cargando conversación" },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EvShimmerBox(Modifier.size(52.dp), CircleShape)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EvShimmerBox(Modifier.fillMaxWidth(0.45f).height(14.dp))
            EvShimmerBox(Modifier.fillMaxWidth(0.8f).height(12.dp))
        }
    }
}

/** Avatar redondo: foto o inicial del nombre sobre fondo oscuro. */
@Composable
fun ChatAvatar(url: String?, name: String, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    Box(modifier.size(size).clip(CircleShape).background(c.ink), contentAlignment = Alignment.Center) {
        if (url.isNullOrBlank()) {
            Text(name.trim().take(1).uppercase().ifEmpty { "·" }, style = EvType.title, color = c.onInk)
        } else {
            EvImage(url, name, Modifier.size(size))
        }
    }
}
