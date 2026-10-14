package com.estilovivo.app.feature.notifications

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.estilovivo.app.app.LocalNavigator
import com.estilovivo.app.core.data.AppScope
import com.estilovivo.app.core.data.model.AppNotification
import com.estilovivo.app.core.data.repo.NotificationRepository
import com.estilovivo.app.core.design.EvMotion
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvSpace
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.LocalEvToast
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.rememberEvHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Banner sutil que baja desde arriba cuando llega una notificación por el socket con la app visible.
 * Tocable (marca como leída y navega), se descarta solo a los 5 s o deslizándolo hacia arriba.
 * [suppress] evita avisar de lo que el usuario ya está viendo (la pantalla de notificaciones o el chat abierto).
 */
@Composable
fun InAppNotificationBannerHost(suppress: (AppNotification) -> Boolean, modifier: Modifier = Modifier) {
    val alerts: NotificationAlerts = koinInject()
    val repo: NotificationRepository = koinInject()
    val appScope: AppScope = koinInject()
    val nav = LocalNavigator.current
    val toast = LocalEvToast.current
    val haptics = rememberEvHaptics()
    val currentSuppress by rememberUpdatedState(suppress)

    var current by remember { mutableStateOf<AppNotification?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(alerts) {
        alerts.inApp.collect { n ->
            if (currentSuppress(n)) return@collect
            // Un aviso nuevo sustituye al anterior (sin apilar ni repetir)
            if (current?.id == n.id) return@collect
            current = n
            visible = true
            haptics.tick()
        }
    }
    LaunchedEffect(current?.id, visible) {
        if (visible) {
            delay(BANNER_MS)
            visible = false
        }
    }

    Box(modifier.fillMaxSize().statusBarsPadding().padding(top = 6.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(EvMotion.bouncy()) { -it * 2 } + fadeIn(tween(160)),
            exit = slideOutVertically(tween(240)) { -it * 2 } + fadeOut(tween(180)),
        ) {
            current?.let { n ->
                BannerCard(
                    n = n,
                    onTap = {
                        visible = false
                        if (!n.isRead) appScope.scope.launch { repo.markRead(n.id) }
                        nav.open(n.destination()) { toast.show("No pudimos abrir esa pantalla") }
                    },
                    onDismiss = { visible = false },
                )
            }
        }
    }
}

@Composable
private fun BannerCard(n: AppNotification, onTap: () -> Unit, onDismiss: () -> Unit) {
    val c = EvTheme.colors
    val visual = visualFor(n.type)
    Row(
        Modifier
            .padding(horizontal = EvSpace.screen)
            .fillMaxWidth()
            .shadow(18.dp, EvShapes.card, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
            .clip(EvShapes.card)
            .background(c.ink)
            .pointerInput(Unit) {
                var drag = 0f
                detectVerticalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = { if (drag < -DISMISS_DRAG_PX) onDismiss() },
                    onVerticalDrag = { _, delta -> drag += delta },
                )
            }
            .evClickable(pressedScale = 0.98f, haptic = false, onClick = onTap)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(visual.color.copy(alpha = 0.28f)), contentAlignment = Alignment.Center) {
            Icon(visual.icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(n.content, style = EvType.label, color = c.onInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Toca para verla", style = EvType.caption, color = c.onInk.copy(alpha = 0.6f))
        }
    }
}

private const val BANNER_MS = 5_000L
private const val DISMISS_DRAG_PX = 24f
