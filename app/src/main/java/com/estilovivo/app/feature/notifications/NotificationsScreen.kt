package com.estilovivo.app.feature.notifications

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.estilovivo.app.app.LocalNavigator
import com.estilovivo.app.core.data.model.AppNotification
import com.estilovivo.app.core.data.model.NotificationTypes
import com.estilovivo.app.core.design.EvMotion
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvSpace
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.EvCircleButton
import com.estilovivo.app.core.design.components.EvEmptyState
import com.estilovivo.app.core.design.components.EvHeadline
import com.estilovivo.app.core.design.components.EvShimmerBox
import com.estilovivo.app.core.design.components.LocalEvToast
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.staggeredEntrance
import org.koin.androidx.compose.koinViewModel

/** Pantalla de notificaciones: titular editorial, lista por día, marcar leídas, pull-to-refresh. */
@Composable
fun NotificationsScreen(modifier: Modifier = Modifier) {
    val vm: NotificationsViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val toast = LocalEvToast.current

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is NotificationsEvent.Error -> toast.error(e.message)
                is NotificationsEvent.Open -> nav.open(e.destination) { toast.show("No pudimos abrir esa pantalla") }
            }
        }
    }

    NotificationsContent(
        state = state,
        onBack = nav::back,
        onRefresh = vm::refresh,
        onRetry = vm::retry,
        onOpen = vm::open,
        onMarkRead = vm::markRead,
        onMarkAll = vm::markAllRead,
        modifier = modifier,
    )
}

@Composable
internal fun NotificationsContent(
    state: NotificationsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (AppNotification) -> Unit,
    onMarkRead: (AppNotification) -> Unit,
    onMarkAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = EvTheme.colors
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = EvSpace.screen, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            EvCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, onBack, contentDescription = "Volver")
            AnimatedVisibility(
                visible = state.unread > 0,
                enter = fadeIn(tween(200)) + scaleIn(EvMotion.bouncy(), initialScale = 0.7f),
                exit = fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.7f),
            ) {
                MarkAllPill(busy = state.markingAll, onClick = onMarkAll)
            }
        }
        Column(Modifier.padding(horizontal = EvSpace.screen).padding(bottom = 8.dp)) {
            EvHeadline("Tus *novedades*", Modifier.staggeredEntrance(0))
            AnimatedContent(
                targetState = when {
                    state.loading || (state.error != null && state.isEmpty) -> ""
                    state.unread == 0 -> "Todo al día"
                    state.unread == 1 -> "1 sin leer"
                    else -> "${state.unread} sin leer"
                },
                transitionSpec = { (fadeIn(tween(220)) + slideInVertically { it / 3 }) togetherWith (fadeOut(tween(120)) + slideOutVertically { -it / 3 }) },
                label = "unreadSubtitle",
            ) { sub ->
                Text(sub, style = EvType.subtitle, color = c.inkSoft, modifier = Modifier.height(24.dp))
            }
        }
        PermissionCard(Modifier.padding(horizontal = EvSpace.screen))

        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.weight(1f)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = EvSpace.screen, end = EvSpace.screen, top = 4.dp, bottom = 32.dp + navBottom),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when {
                    state.loading -> items(5, key = { "sk$it" }) {
                        EvShimmerBox(Modifier.fillMaxWidth().height(80.dp), EvShapes.card)
                    }
                    state.error != null && state.isEmpty -> item(key = "error") {
                        EvEmptyState(
                            Icons.Rounded.CloudOff, "No pudimos cargarlas", state.error,
                            actionText = "Reintentar", onAction = onRetry,
                        )
                    }
                    state.isEmpty -> item(key = "empty") {
                        EvEmptyState(
                            Icons.Rounded.NotificationsNone, "Todo tranquilo por aquí",
                            "Cuando alguien te escriba, te siga o le guste uno de tus looks, lo verás aquí.",
                            modifier = Modifier.padding(top = 40.dp),
                        )
                    }
                    else -> {
                        var index = 0
                        state.sections.forEach { section ->
                            item(key = "h-${section.label}-${section.date}") {
                                Text(
                                    section.label,
                                    style = EvType.headlineAccent.copy(fontSize = 24.sp, lineHeight = 28.sp),
                                    color = c.ink,
                                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp).animateItem(),
                                )
                            }
                            items(section.items, key = { "n-${it.id}" }) { n ->
                                val i = index++
                                NotificationRow(
                                    n = n, state = state,
                                    onOpen = { onOpen(n) }, onMarkRead = { onMarkRead(n) },
                                    modifier = Modifier.animateItem().staggeredEntrance(i + 1),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkAllPill(busy: Boolean, onClick: () -> Unit) {
    val c = EvTheme.colors
    Row(
        Modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(c.ink)
            .evClickable(enabled = !busy, onClick = onClick)
            .padding(horizontal = 18.dp)
            .semantics { contentDescription = "Marcar todas como leídas" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.DoneAll, null, tint = c.onInk, modifier = Modifier.size(18.dp))
        Text(if (busy) "Marcando…" else "Marcar todas", style = EvType.label, color = c.onInk)
    }
}

/** Icono y color por tipo de notificación (los desconocidos usan una campana neutra). */
@androidx.compose.runtime.Immutable
internal data class NotificationVisual(val icon: ImageVector, val color: Color)

internal fun visualFor(type: String): NotificationVisual = when (type) {
    NotificationTypes.CHAT -> NotificationVisual(Icons.Rounded.ChatBubble, Color(0xFF3B82F6))
    NotificationTypes.LIKE -> NotificationVisual(Icons.Rounded.Favorite, Color(0xFFFF3B5C))
    NotificationTypes.COMMENT -> NotificationVisual(Icons.Rounded.QuestionAnswer, Color(0xFF8B5CF6))
    NotificationTypes.FOLLOW -> NotificationVisual(Icons.Rounded.PersonAdd, Color(0xFF10B981))
    NotificationTypes.WASHING -> NotificationVisual(Icons.Rounded.LocalLaundryService, Color(0xFF0EA5E9))
    NotificationTypes.STORY_REACTION -> NotificationVisual(Icons.Rounded.AutoAwesome, Color(0xFFF59E0B))
    else -> NotificationVisual(Icons.Rounded.Notifications, Color(0xFF64748B))
}

@Composable
private fun NotificationRow(
    n: AppNotification,
    state: NotificationsUiState,
    onOpen: () -> Unit,
    onMarkRead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = EvTheme.colors
    val swipe = rememberSwipeToDismissBoxState(
        confirmValueChange = { target ->
            // Deslizar marca como leída y la tarjeta vuelve a su sitio (no se borra)
            if (target != SwipeToDismissBoxValue.Settled) onMarkRead()
            false
        },
    )
    SwipeToDismissBox(
        state = swipe,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = !n.isRead,
        backgroundContent = {
            Row(
                Modifier.fillMaxSize().clip(EvShapes.card).background(c.success.copy(alpha = 0.16f)).padding(end = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                Icon(Icons.Rounded.DoneAll, null, tint = c.success, modifier = Modifier.size(22.dp))
                Text("  Leída", style = EvType.label, color = c.success)
            }
        },
    ) {
        val visual = visualFor(n.type)
        val container by animateColorAsState(if (n.isRead) c.bg else c.surface, tween(260), label = "rowBg")
        Row(
            Modifier
                .fillMaxWidth()
                .clip(EvShapes.card)
                .background(container)
                .border(1.dp, if (n.isRead) c.outline else Color.Transparent, EvShapes.card)
                .evClickable(pressedScale = 0.97f, onClick = onOpen)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(visual.color.copy(alpha = if (n.isRead) 0.10f else 0.18f)),
                contentAlignment = Alignment.Center,
            ) { Icon(visual.icon, null, tint = visual.color, modifier = Modifier.size(22.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    n.content,
                    style = EvType.body.copy(fontWeight = if (n.isRead) FontWeight.Normal else FontWeight.SemiBold),
                    color = if (n.isRead) c.inkSoft else c.ink,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(timeLabel(n.createdAt, state.now, state.zone), style = EvType.caption, color = c.inkMuted)
            }
            AnimatedVisibility(
                visible = !n.isRead,
                enter = scaleIn(EvMotion.bouncy()),
                exit = scaleOut(tween(200)) + fadeOut(tween(200)),
            ) {
                Box(Modifier.size(10.dp).background(c.accent, CircleShape).semantics { contentDescription = "Sin leer" })
            }
        }
    }
}

/** Pide el permiso de notificaciones (Android 13+) con una tarjeta no bloqueante, sólo si aún no está concedido. */
@Composable
private fun PermissionCard(modifier: Modifier = Modifier) {
    if (Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current
    val c = EvTheme.colors
    val toast = LocalEvToast.current
    val store = remember { context.getSharedPreferences("ev_notifications_ui", 0) }
    var needed by remember { mutableStateOf(!canPostNotifications(context) && !store.getBoolean(KEY_DISMISSED, false)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        needed = false
        if (granted) toast.success("¡Listo! Te avisaremos de lo importante")
        else {
            store.edit().putBoolean(KEY_DISMISSED, true).apply()
            toast.show("Puedes activar los avisos más tarde desde los ajustes del sistema")
        }
    }
    AnimatedVisibility(
        visible = needed,
        enter = fadeIn(tween(240)) + slideInVertically(EvMotion.smooth()) { -it / 2 },
        exit = fadeOut(tween(180)) + slideOutVertically(tween(200)) { -it / 2 },
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(bottom = 10.dp).fillMaxWidth().clip(EvShapes.card).background(c.surface).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.NotificationsActive, null, tint = c.ink, modifier = Modifier.size(26.dp))
            Column(Modifier.weight(1f)) {
                Text("No te pierdas nada", style = EvType.label, color = c.ink)
                Text("Activa los avisos para enterarte aunque la app esté cerrada.", style = EvType.bodySmall, color = c.inkSoft)
            }
            Text(
                "Activar",
                style = EvType.label, color = c.onInk,
                modifier = Modifier.clip(CircleShape).background(c.ink).evClickable { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
            Text(
                "Ahora no",
                style = EvType.caption, color = c.inkMuted,
                modifier = Modifier.evClickable { needed = false; store.edit().putBoolean(KEY_DISMISSED, true).apply() }.padding(4.dp),
            )
        }
    }
}

private const val KEY_DISMISSED = "perm_card_dismissed"
