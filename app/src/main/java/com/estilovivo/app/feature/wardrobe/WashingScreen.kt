package com.estilovivo.app.feature.wardrobe

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.estilovivo.app.app.GarmentDetail
import com.estilovivo.app.app.LocalNavigator
import com.estilovivo.app.core.data.model.Garment
import com.estilovivo.app.core.design.EvMotion
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvSpace
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.SharedKey
import com.estilovivo.app.core.design.components.EvButtonStyle
import com.estilovivo.app.core.design.components.EvCircleButton
import com.estilovivo.app.core.design.components.EvEmptyState
import com.estilovivo.app.core.design.components.EvHeadline
import com.estilovivo.app.core.design.components.EvImage
import com.estilovivo.app.core.design.components.EvPillButton
import com.estilovivo.app.core.design.components.EvShimmerBox
import com.estilovivo.app.core.design.components.LocalEvToast
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.sharedElementIfAvailable
import com.estilovivo.app.core.design.staggeredEntrance
import org.koin.androidx.compose.koinViewModel
import kotlin.math.cos
import kotlin.math.sin

private val Water = Color(0xFF3B82F6)

@Composable
fun WashingScreen(modifier: Modifier = Modifier) {
    val vm: WashingViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val toast = LocalEvToast.current
    val c = EvTheme.colors
    CollectWardrobeEvents(vm.events, toast)

    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = EvSpace.screen, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            EvCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, nav::back, contentDescription = "Volver")
            EvHeadline("La *lavadora*")
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = EvSpace.screen, end = EvSpace.screen, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "drum") {
                DrumCard(count = state.items.size, working = state.returningAll || state.busy.isNotEmpty(), modifier = Modifier.staggeredEntrance(0))
            }
            if (state.items.size > 1) {
                item(key = "all") {
                    EvPillButton(
                        "Devolver todas al armario", vm::returnAll, Modifier.fillMaxWidth(), style = EvButtonStyle.Primary,
                        icon = Icons.Rounded.Undo, loading = state.returningAll, height = 56.dp,
                    )
                }
            }
            when {
                state.loading -> items(3, key = { "sk$it" }) { EvShimmerBox(Modifier.fillMaxWidth().heightIn(min = 92.dp).size(height = 92.dp, width = 100.dp), EvShapes.card) }
                state.error != null -> item(key = "error") {
                    EvEmptyState(Icons.Rounded.CloudOff, "No pudimos cargarla", state.error.orEmpty(), actionText = "Reintentar", onAction = vm::refresh)
                }
                state.items.isEmpty() -> item(key = "empty") {
                    EvEmptyState(
                        Icons.Rounded.LocalLaundryService, "Lavadora vacía",
                        "Mantén pulsada una prenda del armario y elige “Lavar” para meterla aquí.",
                        actionText = "Volver al armario", onAction = nav::back,
                    )
                }
                else -> itemsIndexed(state.items, key = { _, g -> g.id }) { index, g ->
                    WashingRow(
                        g = g, index = index, busy = g.id in state.busy, disabled = state.returningAll,
                        onOpen = { nav.navigate(GarmentDetail(g.id)) }, onReturn = { vm.returnToCloset(g.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DrumCard(count: Int, working: Boolean, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    Column(
        modifier.fillMaxWidth().clip(EvShapes.hero).background(c.surface).padding(vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        WashingDrum(spinning = count > 0, fast = working, modifier = Modifier.size(150.dp))
        Text(
            when (count) { 0 -> "Sin prendas dando vueltas"; 1 -> "1 prenda dando vueltas"; else -> "$count prendas dando vueltas" },
            style = EvType.subtitle, color = c.ink,
        )
    }
}

/** Lavadora de frente: el tambor gira (más rápido mientras se trabaja) con burbujas que suben. */
@Composable
fun WashingDrum(spinning: Boolean, fast: Boolean, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    val transition = rememberInfiniteTransition(label = "drum")
    val turn by transition.animateFloat(
        0f, 360f, infiniteRepeatable(tween(if (fast) 1100 else 3600, easing = LinearEasing)), label = "turn",
    )
    val rise by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "rise")
    val power by animateFloatAsState(if (spinning) 1f else 0f, EvMotion.smooth(), label = "power")
    val body = c.surfaceAlt
    val rim = c.ink
    Canvas(modifier) {
        val w = size.width
        val r = w * 0.34f
        val center = Offset(w / 2f, size.height / 2f)
        // Carcasa
        drawRoundRect(body, topLeft = Offset.Zero, size = Size(w, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.2f))
        drawCircle(rim, radius = r + w * 0.045f, center = center, style = Stroke(width = w * 0.05f))
        drawCircle(Water.copy(alpha = 0.16f + 0.1f * power), radius = r, center = center)
        // Prendas girando
        val angle = Math.toRadians((turn * power).toDouble())
        val palette = listOf(Color(0xFFFF3B5C), Color(0xFFFFC145), Color(0xFF60A5FA), Color(0xFFA78BFA), Color(0xFF4ADE80))
        palette.forEachIndexed { i, col ->
            val a = angle + i * (2 * Math.PI / palette.size)
            val dist = r * (0.42f + 0.14f * (i % 2))
            val p = Offset(center.x + (cos(a) * dist).toFloat(), center.y + (sin(a) * dist).toFloat())
            drawCircle(col.copy(alpha = 0.85f), radius = r * (0.2f + 0.04f * (i % 3)), center = p)
        }
        // Burbujas
        if (power > 0.01f) {
            repeat(5) { i ->
                val t = (rise + i * 0.2f) % 1f
                val x = center.x + (i - 2) * r * 0.32f + (sin((t * 6f + i).toDouble()) * r * 0.06f).toFloat()
                val y = center.y + r * 0.7f - t * r * 1.4f
                drawCircle(Color.White.copy(alpha = (1f - t) * 0.7f * power), radius = r * 0.07f * (1f - t * 0.4f), center = Offset(x, y))
            }
        }
        // Reflejo del cristal
        drawArc(
            Color.White.copy(alpha = 0.5f), startAngle = 200f, sweepAngle = 50f, useCenter = false,
            topLeft = Offset(center.x - r * 0.8f, center.y - r * 0.8f), size = Size(r * 1.6f, r * 1.6f), style = Stroke(width = w * 0.025f),
        )
    }
}

@Composable
private fun WashingRow(
    g: Garment,
    index: Int,
    busy: Boolean,
    disabled: Boolean,
    onOpen: () -> Unit,
    onReturn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = EvTheme.colors
    Row(
        modifier.fillMaxWidth().staggeredEntrance(index + 1).clip(EvShapes.card).background(c.surface)
            .evClickable(pressedScale = 0.97f, onClick = onOpen).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EvImage(g.imageUrl, g.name, Modifier.size(72.dp).sharedElementIfAvailable(SharedKey(g.id)).clip(EvShapes.cardSmall))
        Column(Modifier.weight(1f)) {
            Text(g.name, style = EvType.subtitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(WardrobeCatalog.categoryLabel(WardrobeCatalog.categoryOf(g.type)), g.color.takeIf { it.isNotBlank() }).joinToString(" · "),
                style = EvType.caption, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        EvPillButton("Devolver", onReturn, style = EvButtonStyle.Secondary, loading = busy, enabled = !disabled, height = 44.dp)
    }
}
