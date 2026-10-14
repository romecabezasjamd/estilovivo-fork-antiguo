package com.estilovivo.app.feature.tryon

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.min
import kotlin.math.roundToInt

internal val SceneLight = Color(0xFFF2F2F5)
internal val SceneDark = Color(0xFF111114)

/** Silueta de pie (cabeza, hombros, tronco y piernas) dentro de un rectángulo [size]; sirve de guía y de marcador. */
internal fun silhouettePath(size: Size): Path {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    return Path().apply {
        // Cabeza
        addOval(androidx.compose.ui.geometry.Rect(Offset(cx, h * 0.115f), h * 0.058f))
        // Cuerpo: hombros -> brazos -> piernas, un solo contorno cerrado
        moveTo(cx - w * 0.06f, h * 0.185f)
        cubicTo(cx - w * 0.30f, h * 0.20f, cx - w * 0.34f, h * 0.24f, cx - w * 0.36f, h * 0.40f)
        lineTo(cx - w * 0.30f, h * 0.55f)
        lineTo(cx - w * 0.22f, h * 0.50f)
        lineTo(cx - w * 0.22f, h * 0.30f)
        lineTo(cx - w * 0.17f, h * 0.55f)
        lineTo(cx - w * 0.16f, h * 0.97f)
        lineTo(cx - w * 0.04f, h * 0.97f)
        lineTo(cx, h * 0.62f)
        lineTo(cx + w * 0.04f, h * 0.97f)
        lineTo(cx + w * 0.16f, h * 0.97f)
        lineTo(cx + w * 0.17f, h * 0.55f)
        lineTo(cx + w * 0.22f, h * 0.30f)
        lineTo(cx + w * 0.22f, h * 0.50f)
        lineTo(cx + w * 0.30f, h * 0.55f)
        lineTo(cx + w * 0.36f, h * 0.40f)
        cubicTo(cx + w * 0.34f, h * 0.24f, cx + w * 0.30f, h * 0.20f, cx + w * 0.06f, h * 0.185f)
        close()
    }
}

/**
 * Dibuja la composición completa en el rectángulo `(0,0)-(size)`; el ancho de [size] equivale a 1 unidad de escena.
 * La usan igual la pantalla, la exportación y la miniatura (lo que ves es lo que se exporta).
 *
 * @param appear progreso 0..1 de la animación de aparición de cada capa (1 = ya visible).
 */
internal fun DrawScope.drawScene(
    sceneAspect: Float,
    dark: Boolean,
    mirror: Boolean,
    body: ImageBitmap?,
    layers: List<TryLayer>,
    bitmapOf: (TryLayer) -> ImageBitmap?,
    appear: (TryLayer) -> Float = { 1f },
    quality: FilterQuality = FilterQuality.Medium,
    silhouetteHint: Boolean = false,
) {
    val w = size.width
    val h = size.height
    withTransform({
        if (mirror) scale(-1f, 1f, pivot = Offset(w / 2f, h / 2f))
    }) {
        drawRect(if (dark) SceneDark else SceneLight, size = Size(w, h))
        if (body != null) {
            val s = min(w / body.width, h / body.height)
            val dw = (body.width * s).roundToInt().coerceAtLeast(1)
            val dh = (body.height * s).roundToInt().coerceAtLeast(1)
            drawImage(
                body, dstOffset = IntOffset(((w - dw) / 2f).roundToInt(), ((h - dh) / 2f).roundToInt()),
                dstSize = IntSize(dw, dh), filterQuality = quality,
            )
        } else if (silhouetteHint) {
            val sw = h * 0.5f * 0.62f
            withTransform({ translate((w - sw) / 2f, h * 0.04f) }) {
                drawPath(
                    silhouettePath(Size(sw, h * 0.92f)),
                    color = (if (dark) Color.White else Color.Black).copy(alpha = 0.10f),
                    style = Stroke(width = 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f))),
                )
            }
        }
        for (l in layers) drawLayer(l, w, bitmapOf(l), appear(l), quality)
    }
}

internal fun DrawScope.drawLayer(l: TryLayer, unit: Float, bmp: ImageBitmap?, appear: Float, quality: FilterQuality) {
    val lw = l.width * unit
    val lh = l.height * unit
    val ap = appear.coerceIn(0f, 1f)
    if (ap <= 0f) return
    val pop = 0.6f + 0.4f * ap
    withTransform({
        translate(l.cx * unit, l.cy * unit)
        rotate(l.rotation, Offset.Zero)
        scale(if (l.flipX) -1f else 1f, if (l.flipY) -1f else 1f, Offset.Zero)
        scale(pop, pop, Offset.Zero)
    }) {
        if (bmp == null) {
            drawRoundRect(
                Color.Gray.copy(alpha = 0.25f * ap), Offset(-lw / 2f, -lh / 2f), Size(lw, lh),
                CornerRadius(min(lw, lh) * 0.1f),
            )
            drawRoundRect(
                Color.Gray.copy(alpha = 0.6f * ap), Offset(-lw / 2f, -lh / 2f), Size(lw, lh),
                CornerRadius(min(lw, lh) * 0.1f), Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
            )
        } else {
            withTransform({
                scale(lw / bmp.width, lh / bmp.height, Offset.Zero)
            }) {
                drawImage(
                    bmp, dstOffset = IntOffset(-bmp.width / 2, -bmp.height / 2), dstSize = IntSize(bmp.width, bmp.height),
                    alpha = (l.opacity * ap).coerceIn(0f, 1f), filterQuality = quality,
                )
            }
        }
    }
}
