package com.estilovivo.app.feature.notifications

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.estilovivo.app.core.data.repo.NotificationRepository
import com.estilovivo.app.core.design.EvMotion
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.rememberEvHaptics
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Campana de la barra superior: nº de no leídas (tope "9+") que se actualiza solo con el socket.
 * Al llegar una nueva, el badge rebota y la campana se sacude suavemente.
 */
@Composable
fun NotificationBell(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val repo: NotificationRepository = koinInject()
    val unread by repo.unreadCount.collectAsStateWithLifecycle()
    NotificationBellContent(unread, onClick, modifier)
}

/** Parte visual sin dependencias (previsualizable y testeable). */
@Composable
fun NotificationBellContent(unread: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    val haptics = rememberEvHaptics()
    val shake = remember { Animatable(0f) }
    val pop = remember { Animatable(1f) }
    var previous by remember { mutableIntStateOf(unread) }

    LaunchedEffect(unread) {
        val arrived = unread > previous
        previous = unread
        if (arrived) {
            haptics.tick()
            // Rebote del badge y sacudida de la campana en paralelo
            coroutineScope {
                launch { pop.snapTo(0.5f); pop.animateTo(1f, EvMotion.bouncy()) }
                launch {
                    for (target in listOf(16f, -13f, 9f, -5f, 0f)) shake.animateTo(target, tween(70))
                }
            }
        }
    }

    val text = badgeText(unread)
    val description = if (unread > 0) "Notificaciones, $unread sin leer" else "Notificaciones"
    Box(modifier.size(44.dp).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(c.surface)
                .border(1.dp, c.outline, CircleShape)
                .evClickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (unread > 0) Icons.Rounded.Notifications else Icons.Rounded.NotificationsNone,
                contentDescription = null,
                tint = c.ink,
                modifier = Modifier
                    .size(21.dp)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 0.1f)
                        rotationZ = shake.value
                    },
            )
        }
        AnimatedVisibility(
            visible = text != null,
            enter = scaleIn(EvMotion.bouncy(), initialScale = 0.2f) + fadeIn(tween(120)),
            exit = scaleOut(tween(160), targetScale = 0.2f) + fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp),
        ) {
            AnimatedContent(
                targetState = text.orEmpty(),
                transitionSpec = { (scaleIn(EvMotion.bouncy(), initialScale = 0.4f) + fadeIn(tween(100))) togetherWith (scaleOut(tween(100)) + fadeOut(tween(100))) },
                label = "badgeText",
                modifier = Modifier
                    .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                    .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                    .clip(CircleShape)
                    .background(c.accent)
                    .border(2.dp, c.bg, CircleShape),
            ) { label ->
                Box(Modifier.defaultMinSize(minWidth = 20.dp, minHeight = 20.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        style = EvType.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, lineHeight = 12.sp),
                        color = Color.White,
                    )
                }
            }
        }
    }
}
