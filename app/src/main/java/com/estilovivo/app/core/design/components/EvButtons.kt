package com.estilovivo.app.core.design.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.estilovivo.app.core.design.EvMotion
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.rememberEvHaptics
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class EvButtonStyle { Primary, Secondary, Ghost, Danger, Light }

/** Botón en píldora. `loading` sustituye el texto por tres puntos animados. */
@Composable
fun EvPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: EvButtonStyle = EvButtonStyle.Primary,
    icon: ImageVector? = null,
    loading: Boolean = false,
    enabled: Boolean = true,
    height: Dp = 56.dp,
) {
    val c = EvTheme.colors
    val (bg, fg, border) = when (style) {
        EvButtonStyle.Primary -> Triple(c.brand, c.onBrand, Color.Transparent)
        EvButtonStyle.Secondary -> Triple(c.surface, c.ink, c.outline)
        EvButtonStyle.Ghost -> Triple(Color.Transparent, c.ink, Color.Transparent)
        EvButtonStyle.Danger -> Triple(c.danger, Color.White, Color.Transparent)
        EvButtonStyle.Light -> Triple(Color.White, Color(0xFF0B0B0F), Color.Transparent)
    }
    val animatedBg by animateColorAsState(if (enabled) bg else bg.copy(alpha = 0.35f), tween(220), label = "btnBg")
    Box(
        modifier = modifier
            .height(height)
            .clip(CircleShape)
            .background(animatedBg)
            .border(1.dp, border, CircleShape)
            .evClickable(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = loading,
            transitionSpec = { (fadeIn(tween(160)) + scaleIn(initialScale = 0.8f)) togetherWith (fadeOut(tween(120)) + scaleOut(targetScale = 0.8f)) },
            label = "btnContent",
        ) { isLoading ->
            if (isLoading) {
                EvLoadingDots(color = fg)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
                    Text(text, style = EvType.label.copy(fontSize = 15.sp), color = fg)
                }
            }
        }
    }
}

/** Tres puntos que saltan en onda. */
@Composable
fun EvLoadingDots(color: Color = EvTheme.colors.brand, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "dots")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val s by t.animateFloat(
                initialValue = 0.5f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(520, delayMillis = i * 130, easing = LinearEasing), RepeatMode.Reverse),
                label = "dot$i",
            )
            Box(Modifier.size(8.dp).scale(s).alpha(0.4f + 0.6f * s).background(color, CircleShape))
        }
    }
}

/** Botón circular de icono (volver, notificaciones, filtros). */
@Composable
fun EvCircleButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    size: Dp = 44.dp,
    container: Color = EvTheme.colors.surface,
    tint: Color = EvTheme.colors.ink,
    badge: Boolean = false,
) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(container)
                .evClickable(onClick = onClick)
                .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(size * 0.46f))
        }
        if (badge) {
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = (-2).dp, y = 2.dp).size(10.dp)
                    .background(EvTheme.colors.accent, CircleShape)
                    .border(2.dp, container, CircleShape),
            )
        }
    }
}

/**
 * Corazón con rebote y explosión de partículas al activarse.
 * Tamaño por defecto 34dp (círculo blanco sobre las fotos de producto).
 */
@Composable
fun EvLikeButton(
    liked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    container: Color = Color.White,
) {
    val accent = EvTheme.colors.accent
    val haptics = rememberEvHaptics()
    val pop = remember { Animatable(1f) }
    val burst = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(liked) {
        if (first) { first = false; return@LaunchedEffect }
        if (liked) {
            haptics.toggle(true)
            burst.snapTo(0f)
            pop.snapTo(0.6f)
            launch { burst.animateTo(1f, tween(520)) }
            pop.animateTo(1f, EvMotion.bouncy())
        } else {
            haptics.toggle(false)
            pop.snapTo(0.8f)
            pop.animateTo(1f, EvMotion.bouncy())
        }
    }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(container)
            .evClickable(haptic = false, role = androidx.compose.ui.semantics.Role.Checkbox, onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * 1.9f)) {
            val p = burst.value
            if (p < 1f) {
                val r0 = this.size.minDimension * 0.22f
                val r1 = this.size.minDimension * 0.48f
                for (i in 0 until 8) {
                    val a = i * (Math.PI / 4).toFloat()
                    val r = r0 + (r1 - r0) * p
                    val len = 5.dp.toPx() * (1f - p)
                    val start = Offset(center.x + cos(a) * r, center.y + sin(a) * r)
                    val end = Offset(center.x + cos(a) * (r + len), center.y + sin(a) * (r + len))
                    drawLine(accent.copy(alpha = 1f - p), start, end, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                }
            }
        }
        Icon(
            imageVector = if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = if (liked) "Quitar de favoritos" else "Añadir a favoritos",
            tint = if (liked) accent else Color(0xFF0B0B0F),
            modifier = Modifier.size(size * 0.5f).scale(pop.value),
        )
    }
}

/**
 * "Desliza para empezar": pista oscura con chevrones en onda y un pulgar blanco con la etiqueta.
 * Se completa al arrastrar más del 60 % (o al tocarlo, para accesibilidad).
 */
@Composable
fun EvSlideToStart(
    label: String,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 64.dp,
) {
    val density = LocalDensity.current
    val haptics = rememberEvHaptics()
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var done by remember { mutableStateOf(false) }
    val pad = 5.dp

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(Color(0xFF0B0B0F))
            .semantics { onClick(label) { if (!done) { done = true; onComplete() }; true } },
    ) {
        val trackPx = with(density) { maxWidth.toPx() }
        val padPx = with(density) { pad.toPx() }
        val thumbW = maxWidth * 0.56f
        val thumbPx = with(density) { thumbW.toPx() }
        val maxOffset = (trackPx - thumbPx - padPx * 2).coerceAtLeast(1f)

        // Chevrones del lado derecho (parpadean en secuencia)
        val t = rememberInfiniteTransition(label = "chev")
        Row(
            Modifier.align(Alignment.CenterEnd).padding(end = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { i ->
                val a by t.animateFloat(
                    initialValue = 0.2f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(700, delayMillis = i * 160, easing = LinearEasing), RepeatMode.Reverse),
                    label = "chev$i",
                )
                Icon(Icons.Rounded.ChevronRight, null, tint = Color.White.copy(alpha = a), modifier = Modifier.size(24.dp).offset(x = (-8 * i).dp))
            }
        }

        Box(
            Modifier
                .padding(pad)
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .width(thumbW)
                .height(height - pad * 2)
                .clip(CircleShape)
                .background(Color.White)
                .pointerInput(maxOffset) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (offset.value > maxOffset * 0.6f && !done) {
                                    done = true
                                    haptics.confirm()
                                    offset.animateTo(maxOffset, EvMotion.snappy())
                                    onComplete()
                                } else if (!done) {
                                    offset.animateTo(0f, EvMotion.bouncy())
                                }
                            }
                        },
                        onDragCancel = { scope.launch { if (!done) offset.animateTo(0f, EvMotion.bouncy()) } },
                    ) { change, drag ->
                        change.consume()
                        scope.launch {
                            val before = offset.value
                            offset.snapTo((before + drag).coerceIn(0f, maxOffset))
                            if (before < maxOffset * 0.6f && offset.value >= maxOffset * 0.6f) haptics.threshold()
                        }
                    }
                }
                .evClickable(haptic = true) {
                    if (!done) {
                        done = true
                        scope.launch { offset.animateTo(maxOffset, EvMotion.snappy()); onComplete() }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = EvType.label.copy(fontSize = 15.sp), color = Color(0xFF0B0B0F))
        }
    }
}
