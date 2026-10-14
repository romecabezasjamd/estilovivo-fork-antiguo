package com.estilovivo.app.core.design

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.animation.core.animateFloatAsState

// ─────────────────────────────────────────────────────────────────────────────
// Springs y duraciones estándar. Usar siempre estos en vez de valores sueltos.
// ─────────────────────────────────────────────────────────────────────────────
object EvMotion {
    /** Rebote vivo para elementos pequeños (botones, corazones). */
    fun <T> bouncy() = spring<T>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
    /** Muelle suave para movimientos de layout/paneles. */
    fun <T> smooth() = spring<T>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
    /** Muelle firme sin rebote (indicadores, deslizadores). */
    fun <T> snappy() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
    const val ENTER_MS = 420
    const val STAGGER_MS = 55
    val emphasized = FastOutSlowInEasing
}

// ─────────────────────────────────────────────────────────────────────────────
// Háptica
// ─────────────────────────────────────────────────────────────────────────────
/** Vibración táctil. Respeta el ajuste del usuario ([LocalHapticsEnabled], alimentado por `AppPrefs.haptics`). */
class EvHaptics(private val h: HapticFeedback, private val enabled: Boolean = true) {
    private fun perform(type: HapticFeedbackType) { if (enabled) h.performHapticFeedback(type) }
    fun tick() = perform(HapticFeedbackType.SegmentTick)
    fun click() = perform(HapticFeedbackType.ContextClick)
    fun confirm() = perform(HapticFeedbackType.Confirm)
    fun reject() = perform(HapticFeedbackType.Reject)
    fun toggle(on: Boolean) = perform(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
    fun heavy() = perform(HapticFeedbackType.LongPress)
    fun threshold() = perform(HapticFeedbackType.GestureThresholdActivate)
}

/** `false` desactiva toda la háptica de la app (ajuste de accesibilidad del perfil). Lo provee [EvTheme]. */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

@Composable
fun rememberEvHaptics(): EvHaptics {
    val h = LocalHapticFeedback.current
    val enabled = LocalHapticsEnabled.current
    return remember(h, enabled) { EvHaptics(h, enabled) }
}

// ─────────────────────────────────────────────────────────────────────────────
// Micro-interacciones
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Clic con *press-scale* elástico y háptica. Sustituye a `clickable` en TODA la UI interactiva
 * para que cada toque se sienta igual.
 */
@Composable
fun Modifier.evClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.94f,
    haptic: Boolean = true,
    role: Role? = Role.Button,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = EvMotion.bouncy(),
        label = "pressScale",
    )
    val haptics = rememberEvHaptics()
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = { if (haptic) haptics.click(); onClick() },
        )
}

/** Sólo el efecto de escala al pulsar (para componentes con su propio clickable). */
@Composable
fun Modifier.pressScale(source: MutableInteractionSource, pressedScale: Float = 0.95f): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, EvMotion.bouncy(), label = "pressScale")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Esqueleto de carga con barrido de brillo. */
@Composable
fun Modifier.shimmer(): Modifier {
    val c = EvTheme.colors
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerX",
    )
    val base = c.surfaceAlt
    val glow = if (c.isDark) Color(0x22FFFFFF) else Color(0xAAFFFFFF)
    return drawWithContent {
        drawRect(base)
        val w = size.width
        drawRect(
            Brush.linearGradient(
                colors = listOf(Color.Transparent, glow, Color.Transparent),
                start = Offset(w * x, 0f),
                end = Offset(w * x + w * 0.6f, size.height),
            ),
        )
    }
}

/**
 * Entrada escalonada: aparece desde abajo con fundido y un ligero zoom.
 * Para listas: `Modifier.staggeredEntrance(index)`. Sólo anima los primeros elementos.
 */
@Composable
fun Modifier.staggeredEntrance(index: Int, maxAnimated: Int = 10, offsetPx: Float = 48f): Modifier {
    if (index > maxAnimated) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(EvMotion.ENTER_MS, delayMillis = index * EvMotion.STAGGER_MS, easing = EvMotion.emphasized))
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * offsetPx
        val s = 0.94f + 0.06f * p
        scaleX = s; scaleY = s
    }
}

/** Flotación lenta (decorativa) de arriba abajo. */
@Composable
fun Modifier.floatingBob(amplitudePx: Float = 10f, periodMs: Int = 3600): Modifier {
    val t = rememberInfiniteTransition(label = "bob")
    val y by t.animateFloat(
        initialValue = -amplitudePx, targetValue = amplitudePx,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bobY",
    )
    return graphicsLayer { translationY = y }
}

// ─────────────────────────────────────────────────────────────────────────────
// Transiciones de elemento compartido (lista → detalle)
// ─────────────────────────────────────────────────────────────────────────────
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/** Clave estándar del elemento compartido: misma `key` en lista y detalle. */
data class SharedKey(val id: String, val kind: String = "image")

/** Aplica `sharedBounds` si hay scopes disponibles (si no, no hace nada: seguro en previews/tests). */
@Composable
fun Modifier.sharedElementIfAvailable(key: SharedKey): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val visibility = LocalAnimatedVisibilityScope.current ?: return this
    return with(shared) {
        this@sharedElementIfAvailable.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = visibility,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }
}
