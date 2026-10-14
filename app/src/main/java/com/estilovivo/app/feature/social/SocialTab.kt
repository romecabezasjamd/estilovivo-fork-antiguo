package com.estilovivo.app.feature.social

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.estilovivo.app.core.design.EvMotion
import com.estilovivo.app.core.design.EvSpace
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.EvHeadline
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.staggeredEntrance
import com.estilovivo.app.feature.social.chat.ChatTab
import com.estilovivo.app.feature.social.feed.FeedTab
import com.estilovivo.app.feature.social.feed.TrendsTab
import com.estilovivo.app.feature.social.shop.ShopTab
import kotlin.math.roundToInt

enum class SocialSection(val label: String) { Feed("Feed"), Shop("Tienda"), Trends("Tendencias"), Chat("Chat") }

/**
 * Pestaña Social: título, selector con indicador deslizante y las 4 secciones.
 * Cada sección conserva su posición de scroll al cambiar de una a otra.
 */
@Composable
fun SocialTab() {
    var section by rememberSaveable { mutableStateOf(SocialSection.Feed) }
    val holder = rememberSaveableStateHolder()
    Column(Modifier.fillMaxSize()) {
        // Fila del título (la campana de notificaciones flota a la derecha, la pone MainShell)
        Row(
            Modifier.statusBarsPadding().padding(start = EvSpace.screen, end = 72.dp, top = 8.dp).height(44.dp).staggeredEntrance(0),
            verticalAlignment = Alignment.CenterVertically,
        ) { EvHeadline("*Comunidad*") }

        SocialSelector(
            current = section, onSelect = { section = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = EvSpace.screen, vertical = 12.dp),
        )

        AnimatedContent(
            targetState = section,
            transitionSpec = {
                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (slideInHorizontally(tween(320, easing = EvMotion.emphasized)) { dir * it / 6 } + fadeIn(tween(240))) togetherWith
                    (slideOutHorizontally(tween(220, easing = EvMotion.emphasized)) { -dir * it / 6 } + fadeOut(tween(120)))
            },
            label = "socialSection",
            modifier = Modifier.fillMaxSize(),
        ) { s ->
            holder.SaveableStateProvider(s.name) {
                Box(Modifier.fillMaxSize()) {
                    when (s) {
                        SocialSection.Feed -> FeedTab()
                        SocialSection.Shop -> ShopTab()
                        SocialSection.Trends -> TrendsTab()
                        SocialSection.Chat -> ChatTab()
                    }
                }
            }
        }
    }
}

/** Selector en píldora con un indicador oscuro que se desliza con un muelle bajo la opción activa. */
@Composable
private fun SocialSelector(current: SocialSection, onSelect: (SocialSection) -> Unit, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    val options = SocialSection.entries
    BoxWithConstraints(modifier.clip(CircleShape).background(c.surfaceAlt).padding(4.dp)) {
        val itemWidth = maxWidth / options.size
        val indicatorX by animateDpAsState(itemWidth * current.ordinal, EvMotion.snappy(), label = "socialIndicator")
        Box(
            Modifier
                .offset { IntOffset(indicatorX.roundToPx(), 0) }
                .width(itemWidth).height(40.dp)
                .clip(CircleShape).background(c.ink),
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEach { option ->
                val isSelected = option == current
                val fg by animateColorAsState(if (isSelected) c.onInk else c.inkSoft, tween(220), label = "socialFg")
                Box(
                    Modifier.width(itemWidth).height(40.dp).clip(CircleShape)
                        .evClickable(pressedScale = 0.95f, role = Role.Tab) { onSelect(option) }
                        .semantics { role = Role.Tab; selected = isSelected; contentDescription = option.label },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(option.label, style = EvType.label, color = fg, maxLines = 1, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
