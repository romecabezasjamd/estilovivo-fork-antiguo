package com.estilovivo.app.core.design.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.estilovivo.app.core.design.EvSpace
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.floatingBob
import com.estilovivo.app.core.design.rememberEvHaptics
import com.estilovivo.app.core.design.shimmer
import kotlin.random.Random

/**
 * Imagen remota (URL), recurso local (`R.drawable.x` como Int) o `null`.
 * Muestra un esqueleto con brillo mientras carga y hace *crossfade* al aparecer.
 */
@Composable
fun EvImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val c = EvTheme.colors
    when (model) {
        null, "" -> Box(modifier.background(c.surfaceAlt))
        is Int -> Image(painterResource(model), contentDescription, modifier, contentScale = contentScale)
        else -> {
            var loading by remember(model) { mutableStateOf(true) }
            Box(modifier) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(model).crossfade(350).build(),
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    onState = { loading = it is AsyncImagePainter.State.Loading },
                    modifier = Modifier.matchParentSize(),
                )
                if (loading) Box(Modifier.matchParentSize().shimmer())
            }
        }
    }
}

/** Bloque esqueleto genérico. */
@Composable
fun EvShimmerBox(modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp)) {
    Box(modifier.clip(shape).shimmer())
}

/** Estado vacío con icono flotante. */
@Composable
fun EvEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = EvTheme.colors
    Column(
        modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(88.dp).floatingBob(amplitudePx = 8f).clip(CircleShape).background(c.surface),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = c.ink, modifier = Modifier.size(36.dp)) }
        Text(title, style = EvType.title, color = c.ink, textAlign = TextAlign.Center)
        Text(message, style = EvType.body, color = c.inkSoft, textAlign = TextAlign.Center)
        if (actionText != null && onAction != null) {
            EvPillButton(actionText, onAction, modifier = Modifier.padding(top = 8.dp), height = 48.dp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Toasts
// ─────────────────────────────────────────────────────────────────────────────
enum class EvToastKind { Success, Error, Info }

@Stable
class EvToastState {
    var message by mutableStateOf<String?>(null)
        private set
    var kind by mutableStateOf(EvToastKind.Info)
        private set
    var token by mutableStateOf(0)
        private set

    fun show(text: String, kind: EvToastKind = EvToastKind.Info) {
        this.message = text; this.kind = kind; token++
    }
    fun success(text: String) = show(text, EvToastKind.Success)
    fun error(text: String) = show(text, EvToastKind.Error)
    internal fun dismiss() { message = null }
}

val LocalEvToast = staticCompositionLocalOf { EvToastState() }

/** Colocar una vez en la raíz de la app, por encima de todo el contenido. */
@Composable
fun EvToastHost(state: EvToastState, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    val haptics = rememberEvHaptics()
    LaunchedEffect(state.token) {
        if (state.message != null) {
            if (state.kind == EvToastKind.Error) haptics.reject() else if (state.kind == EvToastKind.Success) haptics.confirm()
            kotlinx.coroutines.delay(3200)
            state.dismiss()
        }
    }
    Box(modifier.fillMaxSize().statusBarsPadding().padding(top = 8.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = state.message != null,
            enter = slideInVertically(com.estilovivo.app.core.design.EvMotion.bouncy()) { -it * 2 } + fadeIn(),
            exit = slideOutVertically(tween(220)) { -it * 2 } + fadeOut(tween(180)),
        ) {
            val (icon, tint) = when (state.kind) {
                EvToastKind.Success -> Icons.Rounded.CheckCircle to c.success
                EvToastKind.Error -> Icons.Rounded.ErrorOutline to c.danger
                EvToastKind.Info -> Icons.Rounded.Info to c.inkMuted
            }
            Row(
                Modifier
                    .padding(horizontal = EvSpace.screen)
                    .shadow(18.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
                    .clip(CircleShape)
                    .background(c.ink)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
                Text(state.message.orEmpty(), style = EvType.label, color = c.onInk)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Confeti (retos completados, subida de nivel, logros)
// ─────────────────────────────────────────────────────────────────────────────
private class Particle(
    val x0: Float, val vx: Float, val vy: Float, val spin: Float, val size: Float, val color: Color,
)

/** Cada vez que `trigger` cambia (y es > 0) lanza una ráfaga de confeti desde arriba. */
@Composable
fun EvConfetti(trigger: Int, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(1f) }
    val palette = remember { listOf(Color(0xFFFF3B5C), Color(0xFFFFC145), Color(0xFF4ADE80), Color(0xFF60A5FA), Color(0xFFA78BFA), Color(0xFFFFFFFF)) }
    val particles = remember(trigger) {
        val r = Random(trigger)
        List(70) {
            Particle(
                x0 = 0.1f + r.nextFloat() * 0.8f, vx = (r.nextFloat() - 0.5f) * 0.5f,
                vy = 0.6f + r.nextFloat() * 0.9f, spin = (r.nextFloat() - 0.5f) * 900f,
                size = 6f + r.nextFloat() * 8f, color = palette[r.nextInt(palette.size)],
            )
        }
    }
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(2200))
        }
    }
    if (progress.value < 1f) {
        Canvas(modifier.fillMaxSize()) {
            val p = progress.value
            particles.forEach { q ->
                val x = (q.x0 + q.vx * p) * size.width
                val y = (-0.05f + q.vy * p + 0.9f * p * p) * size.height
                rotate(q.spin * p, pivot = Offset(x, y)) {
                    drawRect(q.color.copy(alpha = (1f - p * p)), topLeft = Offset(x, y), size = Size(q.size, q.size * 0.55f))
                }
            }
        }
    }
}
