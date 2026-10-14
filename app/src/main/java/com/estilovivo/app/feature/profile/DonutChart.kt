package com.estilovivo.app.feature.profile

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType

private const val MAX_SLICES = 6

/** Agrupa lo que sobra en "Otras" para que el gráfico y la leyenda sigan siendo legibles. */
internal fun collapseSlices(slices: List<DonutSlice>): List<DonutSlice> {
    val positive = slices.filter { it.value > 0 }
    if (positive.size <= MAX_SLICES) return positive
    val head = positive.take(MAX_SLICES - 1)
    return head + DonutSlice("other", "Otras", positive.drop(MAX_SLICES - 1).sumOf { it.value })
}

@Composable
internal fun donutPalette(): List<Color> {
    val c = EvTheme.colors
    return listOf(c.ink, c.accent, c.warning, c.success, Color(0xFF60A5FA), Color(0xFFA78BFA), c.inkMuted)
}

/** Donut con barrido animado (Canvas) y leyenda. El centro enseña el total. */
@Composable
fun DonutChart(slices: List<DonutSlice>, centerLabel: String, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    val data = remember(slices) { collapseSlices(slices) }
    val total = data.sumOf { it.value }
    val palette = donutPalette()
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(data) {
        sweep.snapTo(0f)
        sweep.animateTo(1f, tween(1000, easing = com.estilovivo.app.core.design.EvMotion.emphasized))
    }
    val description = data.joinToString(", ") { "${it.label}: ${it.value}" }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(Modifier.size(132.dp).semantics { contentDescription = "Gráfico. $description" }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(132.dp).aspectRatio(1f)) {
                val stroke = 20.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(c.surfaceAlt, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                if (total > 0) {
                    var start = -90f
                    val gap = if (data.size > 1) 3f else 0f
                    data.forEachIndexed { i, s ->
                        val full = 360f * s.value / total
                        val visible = (full * sweep.value).coerceAtLeast(0f)
                        val drawn = (visible - gap).coerceAtLeast(0.01f)
                        if (visible > 0f) {
                            drawArc(palette[i % palette.size], start + gap / 2, drawn, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                        }
                        start += full
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(total.toString(), style = EvType.bigNumber.copy(fontSize = androidx.compose.ui.unit.TextUnit(26f, androidx.compose.ui.unit.TextUnitType.Sp)), color = c.ink)
                Text(centerLabel, style = EvType.caption, color = c.inkSoft)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            data.forEachIndexed { i, s ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(10.dp).background(palette[i % palette.size], CircleShape))
                    Text(s.label, style = EvType.bodySmall, color = c.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text("${s.value} · ${if (total > 0) s.value * 100 / total else 0}%", style = EvType.caption, color = c.inkSoft)
                }
            }
        }
    }
}
