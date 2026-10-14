package com.estilovivo.app.feature.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlin.math.cos
import kotlin.math.sin

private val SunYellow = Color(0xFFFFD84D)
private val SunOrange = Color(0xFFFFB347)

/** Icono del tiempo dibujado y animado con Canvas (sol que gira, nubes a la deriva, lluvia, nieve, viento). */
@Composable
fun WeatherGlyph(condition: WeatherCondition, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "glyph")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(16000, easing = LinearEasing)), label = "spin")
    val pulse by t.animateFloat(0.94f, 1.06f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "pulse")
    val drift by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(3600), RepeatMode.Reverse), label = "drift")
    val fall by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "fall")
    val slowFall by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "slowFall")

    Canvas(modifier.clearAndSetSemantics { }) {
        val s = size.minDimension
        when (condition) {
            WeatherCondition.Sunny -> sun(Offset(s / 2, s / 2), s * 0.22f * pulse, s * 0.40f, spin, SunYellow)
            WeatherCondition.Hot -> sun(Offset(s / 2, s / 2), s * 0.24f * pulse, s * 0.44f, spin * 2, Color(0xFFFFE066))
            WeatherCondition.Cloudy -> {
                sun(Offset(s * 0.66f, s * 0.34f), s * 0.15f * pulse, s * 0.27f, spin, SunOrange)
                cloud(Offset(s * 0.5f + drift * s * 0.04f, s * 0.58f), s * 0.62f, Color.White)
            }
            WeatherCondition.Rainy -> {
                cloud(Offset(s * 0.5f, s * 0.38f), s * 0.64f, Color(0xFFE5ECF6))
                for (i in 0..2) {
                    val p = (fall + i / 3f) % 1f
                    val x = s * (0.32f + 0.18f * i) - p * s * 0.04f
                    val y = s * (0.58f + 0.34f * p)
                    drawLine(Color(0xFFBFE0FF).copy(alpha = 1f - p * 0.7f), Offset(x, y), Offset(x - s * 0.03f, y + s * 0.09f), s * 0.035f, StrokeCap.Round)
                }
            }
            WeatherCondition.Snowy -> {
                cloud(Offset(s * 0.5f, s * 0.38f), s * 0.64f, Color(0xFFF1F5FA))
                for (i in 0..2) {
                    val p = (slowFall + i / 3f) % 1f
                    val x = s * (0.32f + 0.18f * i) + sin((p + i) * 6.28f) * s * 0.03f
                    drawCircle(Color.White.copy(alpha = 1f - p * 0.6f), s * 0.035f, Offset(x, s * (0.6f + 0.34f * p)))
                }
            }
            WeatherCondition.Windy -> {
                for (i in 0..2) {
                    val y = s * (0.32f + 0.18f * i)
                    val dx = drift * s * 0.05f * (if (i % 2 == 0) 1 else -1)
                    drawLine(Color.White.copy(alpha = 0.95f - i * 0.15f), Offset(s * 0.16f + dx, y), Offset(s * (0.74f - i * 0.06f) + dx, y), s * 0.05f, StrokeCap.Round)
                    drawArc(
                        Color.White.copy(alpha = 0.95f - i * 0.15f), 0f, 270f, false,
                        Offset(s * (0.66f - i * 0.06f) + dx, y - s * 0.09f), Size(s * 0.18f, s * 0.18f), style = Stroke(s * 0.045f, cap = StrokeCap.Round),
                    )
                }
            }
            WeatherCondition.Cold -> {
                val center = Offset(s / 2, s / 2)
                rotate(spin / 2, center) {
                    for (i in 0..2) rotate(i * 60f, center) {
                        drawLine(Color.White, Offset(s / 2, s * 0.1f), Offset(s / 2, s * 0.9f), s * 0.05f, StrokeCap.Round)
                        for (side in listOf(-1f, 1f)) {
                            drawLine(Color.White, Offset(s / 2, s * 0.24f), Offset(s / 2 + side * s * 0.08f, s * 0.14f), s * 0.035f, StrokeCap.Round)
                            drawLine(Color.White, Offset(s / 2, s * 0.76f), Offset(s / 2 + side * s * 0.08f, s * 0.86f), s * 0.035f, StrokeCap.Round)
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.sun(center: Offset, radius: Float, rayEnd: Float, angle: Float, color: Color) {
    rotate(angle, center) {
        for (i in 0 until 8) {
            val a = i * (Math.PI / 4).toFloat()
            val from = Offset(center.x + cos(a) * (radius * 1.45f), center.y + sin(a) * (radius * 1.45f))
            val to = Offset(center.x + cos(a) * rayEnd, center.y + sin(a) * rayEnd)
            drawLine(color, from, to, radius * 0.26f, StrokeCap.Round)
        }
    }
    drawCircle(color, radius, center)
}

private fun DrawScope.cloud(center: Offset, width: Float, color: Color) {
    val h = width * 0.5f
    val base = Offset(center.x - width / 2, center.y - h * 0.1f)
    drawRoundRect(color, base, Size(width, h * 0.52f), androidx.compose.ui.geometry.CornerRadius(h * 0.26f))
    drawCircle(color, h * 0.34f, Offset(center.x - width * 0.16f, center.y - h * 0.08f))
    drawCircle(color, h * 0.44f, Offset(center.x + width * 0.06f, center.y - h * 0.2f))
    drawCircle(color, h * 0.3f, Offset(center.x + width * 0.28f, center.y - h * 0.02f))
}
