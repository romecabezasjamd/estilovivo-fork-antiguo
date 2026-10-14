package com.estilovivo.app.feature.planner

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.estilovivo.app.core.data.model.Look
import com.estilovivo.app.core.design.EvMotion
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.EvCircleButton
import com.estilovivo.app.core.design.components.EvImage
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.rememberEvHaptics

/** Rosa del ciclo (el de la gota de la web), algo más suave en oscuro. */
val CycleRose = Color(0xFFC084A8)
val CycleRoseSoft @Composable get() = if (EvTheme.colors.isDark) CycleRose.copy(alpha = 0.16f) else CycleRose.copy(alpha = 0.14f)

// ─────────────────────────────────────────────────────────────────────────────
// Miniatura del look: foto, o collage de prendas, o icono
// ─────────────────────────────────────────────────────────────────────────────

/** Imágenes de las prendas del look (sin nulos ni vacías), máximo 4. */
internal fun Look.collageImages(): List<String> = garments.mapNotNull { it.imageUrl.takeIf { u -> u.isNotBlank() } }.distinct().take(4)

internal fun Look.preferredImage(): String? = imageUrl?.takeIf { it.isNotBlank() } ?: collageImages().firstOrNull()

internal fun Look.garmentCount(): Int = garments.size.takeIf { it > 0 } ?: garmentIds.size

@Composable
fun LookThumb(
    look: Look?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
) {
    val c = EvTheme.colors
    Box(modifier.clip(shape).background(c.surfaceAlt), contentAlignment = Alignment.Center) {
        val photo = look?.imageUrl?.takeIf { it.isNotBlank() }
        val pieces = look?.collageImages().orEmpty()
        when {
            photo != null -> EvImage(photo, look?.name, Modifier.fillMaxSize())
            pieces.size >= 2 -> Column(Modifier.fillMaxSize()) {
                val rows = pieces.chunked(2)
                rows.forEach { row ->
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        row.forEach { url -> EvImage(url, null, Modifier.weight(1f).fillMaxSize()) }
                        if (row.size == 1) Box(Modifier.weight(1f).fillMaxSize())
                    }
                }
            }
            pieces.size == 1 -> EvImage(pieces[0], look?.name, Modifier.fillMaxSize())
            else -> Icon(Icons.Rounded.Checkroom, null, tint = c.inkMuted, modifier = Modifier.fillMaxSize(0.45f))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Ciclo: gota con pulso suave
// ─────────────────────────────────────────────────────────────────────────────

/** Un único reloj de pulso compartido por todas las gotas visibles (0..1 ida y vuelta). */
@Composable
fun rememberCyclePulse(): State<Float> {
    val t = rememberInfiniteTransition(label = "cyclePulse")
    return t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
}

@Composable
fun CycleDrop(pulse: State<Float>, modifier: Modifier = Modifier, size: Dp = 14.dp) {
    Canvas(
        modifier
            .size(size * 0.86f, size)
            .graphicsLayer {
                val p = pulse.value
                val s = 0.9f + 0.14f * p
                scaleX = s; scaleY = s
                alpha = 0.75f + 0.25f * p
            },
    ) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w / 2, 0f)
            cubicTo(w * 0.62f, h * 0.16f, w, h * 0.42f, w, h * 0.65f)
            cubicTo(w, h * 0.87f, w * 0.78f, h, w / 2, h)
            cubicTo(w * 0.22f, h, 0f, h * 0.87f, 0f, h * 0.65f)
            cubicTo(0f, h * 0.42f, w * 0.38f, h * 0.16f, w / 2, 0f)
            close()
        }
        drawPath(path, CycleRose)
        drawOval(Color.White.copy(alpha = 0.45f), Offset(w * 0.24f, h * 0.42f), Size(w * 0.2f, h * 0.22f))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Piezas sueltas
// ─────────────────────────────────────────────────────────────────────────────

/** Hueco con borde discontinuo y "+" para un día sin planificar. */
@Composable
fun DashedAddBox(size: Dp, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(18.dp)) {
    val c = EvTheme.colors
    val density = LocalDensity.current
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(c.surfaceAlt.copy(alpha = 0.6f))
            .drawBehind {
                val sw = with(density) { 1.5.dp.toPx() }
                drawRoundRect(
                    color = c.inkMuted.copy(alpha = 0.6f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(with(density) { 18.dp.toPx() }),
                    style = Stroke(sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(sw * 4, sw * 3))),
                )
            },
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.Add, null, tint = c.inkMuted, modifier = Modifier.size(size * 0.38f)) }
}

/** Nota del evento en píldora. */
@Composable
fun NoteChip(text: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val c = EvTheme.colors
    Row(
        modifier
            .clip(CircleShape)
            .background(c.accent.copy(alpha = 0.12f))
            .let { if (onClick != null) it.evClickable(pressedScale = 0.96f, onClick = onClick) else it }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Rounded.EditNote, null, tint = c.accent, modifier = Modifier.size(14.dp))
        Text(text, style = EvType.caption, color = c.accent, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Botón circular pequeño de acción de tarjeta. */
@Composable
fun CardAction(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit, tint: Color = EvTheme.colors.inkSoft) {
    EvCircleButton(icon, onClick, contentDescription = description, size = 44.dp, container = EvTheme.colors.surfaceAlt, tint = tint)
}

/** Botón de volver (pila). */
@Composable
fun BackButton(onClick: () -> Unit) {
    EvCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, onClick, contentDescription = "Volver")
}

// ─────────────────────────────────────────────────────────────────────────────
// Navegación temporal: etiqueta animada, anterior/siguiente, "Hoy" y deslizamiento
// ─────────────────────────────────────────────────────────────────────────────

/** Transición horizontal según la dirección (+1 avanzar, -1 retroceder, 0 fundido). */
fun <T> AnimatedContentTransitionScope<T>.directionalSlide(direction: Int, distanceDivisor: Int = 4): ContentTransform {
    if (direction == 0) return fadeIn(tween(220)) togetherWith fadeOut(tween(160))
    val sign = if (direction > 0) 1 else -1
    return (slideInHorizontally(EvMotion.snappy()) { w -> sign * (w / distanceDivisor) } + fadeIn(tween(240))) togetherWith
        (slideOutHorizontally(EvMotion.snappy()) { w -> -sign * (w / distanceDivisor) } + fadeOut(tween(140)))
}

@Composable
fun PeriodNavigator(
    label: String,
    direction: Int,
    showToday: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    previousDescription: String,
    nextDescription: String,
    modifier: Modifier = Modifier,
) {
    val c = EvTheme.colors
    val haptics = rememberEvHaptics()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EvCircleButton(Icons.Rounded.ChevronLeft, { haptics.tick(); onPrevious() }, contentDescription = previousDescription)
        Box(
            Modifier.weight(1f).height(48.dp).clip(CircleShape).background(c.surface),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = label,
                transitionSpec = { directionalSlide(direction, distanceDivisor = 3) },
                label = "periodLabel",
            ) { text ->
                Text(
                    text, style = EvType.label, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp).semantics { contentDescription = text },
                )
            }
        }
        EvCircleButton(Icons.Rounded.ChevronRight, { haptics.tick(); onNext() }, contentDescription = nextDescription)
        androidx.compose.animation.AnimatedVisibility(
            visible = showToday,
            enter = androidx.compose.animation.scaleIn(EvMotion.bouncy(), initialScale = 0.6f) + fadeIn() + androidx.compose.animation.expandHorizontally(),
            exit = androidx.compose.animation.scaleOut(targetScale = 0.6f) + fadeOut() + androidx.compose.animation.shrinkHorizontally(),
        ) {
            Box(
                Modifier.height(48.dp).clip(CircleShape).background(c.ink)
                    .evClickable(pressedScale = 0.92f) { haptics.tick(); onToday() }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Hoy", style = EvType.label, color = c.onInk) }
        }
    }
}

/** Desliza a izquierda/derecha para pasar de periodo (con háptica). */
@Composable
fun Modifier.swipeToNavigate(key: Any?, onPrevious: () -> Unit, onNext: () -> Unit): Modifier {
    val prev by rememberUpdatedState(onPrevious)
    val next by rememberUpdatedState(onNext)
    val haptics = rememberEvHaptics()
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    return pointerInput(key) {
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragCancel = { total = 0f },
            onDragEnd = {
                when {
                    total > threshold -> { haptics.tick(); prev() }
                    total < -threshold -> { haptics.tick(); next() }
                }
                total = 0f
            },
            onHorizontalDrag = { _, delta -> total += delta },
        )
    }
}

