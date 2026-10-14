package com.estilovivo.app.core.design.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.estilovivo.app.core.design.EvMotion
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.evClickable

@Immutable
data class EvNavItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val badge: Int = 0,
)

/**
 * Barra inferior flotante en píldora oscura. El elemento activo se expande en una cápsula
 * clara con su etiqueta (el icono rebota al seleccionarse). Colocar sobre el contenido.
 */
@Composable
fun EvBottomBar(
    items: List<EvNavItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = EvTheme.colors
    Row(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 14.dp)
            .shadow(24.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.35f))
            .clip(CircleShape)
            .background(Color(0xFF0B0B0F))
            .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
            .padding(6.dp)
            .animateContentSize(EvMotion.smooth()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            NavItem(item, selected = item.id == selectedId, onClick = { onSelect(item.id) })
        }
    }
}

@Composable
private fun NavItem(item: EvNavItem, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) EvTheme.colors.navBrand else Color.Transparent, tween(260), label = "navBg")
    val fg by animateColorAsState(if (selected) EvTheme.colors.onNavBrand else Color.White.copy(alpha = 0.62f), tween(260), label = "navFg")
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) { bounce.snapTo(0.6f); bounce.animateTo(1f, EvMotion.bouncy()) }
    }
    Row(
        Modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(bg)
            .evClickable(pressedScale = 0.9f, onClick = onClick)
            .padding(horizontal = if (selected) 18.dp else 14.dp)
            .animateContentSize(EvMotion.smooth()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Icon(item.icon, item.label, tint = fg, modifier = Modifier.size(24.dp).scale(bounce.value))
            if (item.badge > 0) {
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-3).dp).size(9.dp)
                        .background(EvTheme.colors.accent, CircleShape)
                        .border(1.5.dp, Color(0xFF0B0B0F), CircleShape),
                )
            }
        }
        AnimatedVisibility(
            visible = selected,
            enter = expandHorizontally(EvMotion.smooth()) + fadeIn(tween(200)),
            exit = shrinkHorizontally(EvMotion.snappy()) + fadeOut(tween(100)),
        ) {
            Text(item.label, style = EvType.label, color = fg, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Immutable
data class EvBubble(
    val label: String,
    val icon: ImageVector,
    val tint: Color,
    val badge: Int = 0,
    val onClick: () -> Unit,
)

private val Overshoot = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

/**
 * Burbujas de acción que brotan sobre la barra (submenú de "Armario": Viajes, Lavar, Probar).
 * Cada burbuja entra con rebote escalonado; tocar fuera las cierra.
 */
@Composable
fun EvActionBubbles(
    visible: Boolean,
    bubbles: List<EvBubble>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = EvTheme.colors
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) {
        Box(
            modifier.fillMaxSize().background(Color.Black.copy(alpha = if (c.isDark) 0.45f else 0.25f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
    }
    Box(modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 96.dp), contentAlignment = Alignment.BottomCenter) {
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.Bottom) {
            bubbles.forEachIndexed { i, b ->
                AnimatedVisibility(
                    visible = visible,
                    enter = scaleIn(tween(380, delayMillis = i * 70, easing = Overshoot), initialScale = 0.2f) +
                        slideInVertically(tween(380, delayMillis = i * 70, easing = Overshoot)) { it / 2 } + fadeIn(tween(200, delayMillis = i * 70)),
                    exit = scaleOut(tween(160), targetScale = 0.4f) + slideOutVertically(tween(160)) { it / 2 } + fadeOut(tween(120)),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                Modifier.size(60.dp)
                                    .shadow(14.dp, CircleShape, ambientColor = b.tint.copy(alpha = 0.4f), spotColor = b.tint.copy(alpha = 0.4f))
                                    .clip(CircleShape).background(c.surface)
                                    .evClickable(pressedScale = 0.88f) { onDismiss(); b.onClick() },
                                contentAlignment = Alignment.Center,
                            ) { Icon(b.icon, b.label, tint = b.tint, modifier = Modifier.size(26.dp)) }
                            if (b.badge > 0) {
                                Box(
                                    Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).size(22.dp)
                                        .background(c.accent, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) { Text("${b.badge}", style = EvType.caption, color = Color.White) }
                            }
                        }
                        Text(
                            b.label, style = EvType.caption, color = c.ink,
                            modifier = Modifier.clip(CircleShape).background(c.surface.copy(alpha = 0.92f)).padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
