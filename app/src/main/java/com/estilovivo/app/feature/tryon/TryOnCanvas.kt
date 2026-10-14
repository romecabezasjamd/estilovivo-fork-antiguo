package com.estilovivo.app.feature.tryon

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.estilovivo.app.core.design.EvMotion
import com.estilovivo.app.core.design.rememberEvHaptics
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/** Zoom y desplazamiento de la ESCENA (no de una capa). Se guarda con la rotación de pantalla. */
@Stable
class Viewport(zoom: Float = 1f, panX: Float = 0f, panY: Float = 0f) {
    var zoom by mutableFloatStateOf(zoom)
    var panX by mutableFloatStateOf(panX)
    var panY by mutableFloatStateOf(panY)

    val isIdentity: Boolean get() = abs(zoom - 1f) < 0.01f && abs(panX) < 1f && abs(panY) < 1f

    suspend fun reset() {
        val z0 = zoom; val x0 = panX; val y0 = panY
        animate(0f, 1f, animationSpec = EvMotion.smooth()) { t, _ ->
            zoom = z0 + (1f - z0) * t; panX = x0 * (1f - t); panY = y0 * (1f - t)
        }
        zoom = 1f; panX = 0f; panY = 0f
    }

    companion object {
        val Saver: Saver<Viewport, Any> = listSaver(
            save = { listOf(it.zoom, it.panX, it.panY) },
            restore = { Viewport(it[0], it[1], it[2]) },
        )
    }
}

private enum class DragMode { Scene, Layer, Handle, Delete }

/** Geometría pantalla <-> escena para un instante dado. */
private class Geo(
    val cx: Float, val cy: Float, val panX: Float, val panY: Float, val s: Float, val sceneH: Float, val mirror: Boolean,
) {
    fun toScene(p: Offset): Offset {
        val x = 0.5f + (p.x - cx - panX) / s
        return Offset(if (mirror) 1f - x else x, sceneH / 2f + (p.y - cy - panY) / s)
    }

    fun toScreen(x: Float, y: Float): Offset {
        val ux = if (mirror) 1f - x else x
        return Offset(cx + panX + (ux - 0.5f) * s, cy + panY + (y - sceneH / 2f) * s)
    }
}

/**
 * Lienzo del probador. Dos capas de dibujo (escena + marco de selección) que leen el estado de las capas
 * sólo en la fase de dibujo: arrastrar/pellizcar NO recompone nada, sólo redibuja.
 */
@Composable
internal fun TryOnCanvas(
    layers: State<List<TryLayer>>,
    viewport: Viewport,
    assets: TryOnAssets,
    revision: State<Int>,
    bodyPath: String?,
    sceneAspect: Float,
    selectedId: String?,
    darkBackground: Boolean,
    mirror: Boolean,
    grid: Boolean,
    compare: Boolean,
    compareFraction: Float,
    onCompareFraction: (Float) -> Unit,
    onSelect: (String?) -> Unit,
    onBegin: () -> Unit,
    onLive: (TryLayer) -> Unit,
    onEnd: () -> Unit,
    onDelete: (String) -> Unit,
    onScale: (Float) -> Unit,
    onRotate: (Float) -> Unit,
    onNudge: (Float, Float) -> Unit,
    onDoubleTapLayer: () -> Unit,
    modifier: Modifier = Modifier,
    poseOverlay: BodyLandmarks? = null,
    showSilhouetteHint: Boolean = false,
) {
    val sceneH = Scene.height(sceneAspect)
    val density = LocalDensity.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val haptics = rememberEvHaptics()
    val scope = rememberCoroutineScope()
    var size by remember { mutableStateOf(IntSize.Zero) }

    val latestSelected = rememberUpdatedState(selectedId)
    val latestMirror = rememberUpdatedState(mirror)
    val latestSceneH = rememberUpdatedState(sceneH)
    val latestCompare = rememberUpdatedState(compare)
    val marginPx = with(density) { 14.dp.toPx() }

    // Escala base: la escena cabe entera (con margen) en el contenedor; el zoom del usuario va encima.
    fun baseScale(): Float =
        min((size.width - 2 * marginPx).coerceAtLeast(1f), ((size.height - 2 * marginPx) / latestSceneH.value).coerceAtLeast(1f))

    fun geo(): Geo = Geo(
        size.width / 2f, size.height / 2f, viewport.panX, viewport.panY,
        baseScale() * viewport.zoom, latestSceneH.value, latestMirror.value,
    )

    fun clampPan() {
        val s = baseScale() * viewport.zoom
        val sw = s
        val sh = s * latestSceneH.value
        val lx = abs(sw - size.width) / 2f + min(sw, size.width.toFloat()) * 0.3f
        val ly = abs(sh - size.height) / 2f + min(sh, size.height.toFloat()) * 0.3f
        viewport.panX = viewport.panX.coerceIn(-lx, lx)
        viewport.panY = viewport.panY.coerceIn(-ly, ly)
    }

    // Animación de aparición de capas nuevas.
    val ids by remember(layers) { derivedStateOf { layers.value.map { it.id } } }
    val appear = remember { mutableStateMapOf<String, Float>() }
    LaunchedEffect(ids) {
        appear.keys.toList().filter { it !in ids }.forEach { appear.remove(it) }
        ids.filter { it !in appear }.forEachIndexed { i, id ->
            appear[id] = 0f
            launch {
                kotlinx.coroutines.delay(i * 70L)
                Animatable(0f).animateTo(1f, EvMotion.bouncy()) { appear[id] = value }
            }
        }
    }

    var lastTapTime by remember { mutableFloatStateOf(0f) }
    var lastTapId by remember { mutableStateOf<String?>(null) }

    val a11yLabel = if (selectedId != null) "Lienzo del probador. Prenda seleccionada." else "Lienzo del probador."

    Box(
        modifier
            .clipToBounds()
            .onSizeChanged { size = it }
            .semantics {
                contentDescription = a11yLabel
                if (selectedId != null) {
                    customActions = listOf(
                        CustomAccessibilityAction("Agrandar prenda") { onScale(1.1f); true },
                        CustomAccessibilityAction("Reducir prenda") { onScale(1f / 1.1f); true },
                        CustomAccessibilityAction("Girar a la derecha") { onRotate(5f); true },
                        CustomAccessibilityAction("Girar a la izquierda") { onRotate(-5f); true },
                        CustomAccessibilityAction("Mover a la izquierda") { onNudge(-0.02f, 0f); true },
                        CustomAccessibilityAction("Mover a la derecha") { onNudge(0.02f, 0f); true },
                        CustomAccessibilityAction("Mover arriba") { onNudge(0f, -0.02f); true },
                        CustomAccessibilityAction("Mover abajo") { onNudge(0f, 0.02f); true },
                        CustomAccessibilityAction("Quitar prenda") { onDelete(selectedId); true },
                    )
                }
            }
            .pointerInput(compare) {
                if (compare) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        fun frac(x: Float): Float {
                            val g = geo()
                            val left = g.cx + g.panX - g.s / 2f
                            return ((x - left) / g.s).coerceIn(0.02f, 0.98f)
                        }
                        onCompareFraction(frac(down.position.x))
                        down.consume()
                        do {
                            val ev = awaitPointerEvent()
                            ev.changes.firstOrNull()?.let { onCompareFraction(frac(it.position.x)); it.consume() }
                        } while (ev.changes.any { it.pressed })
                    }
                } else {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val g0 = geo()
                        val all = layers.value
                        val sel = all.firstOrNull { it.id == latestSelected.value }
                        val handleR = with(density) { 26.dp.toPx() }
                        var mode = DragMode.Scene
                        var target: TryLayer? = null
                        if (sel != null) {
                            val c = LayerMath.corners(sel).map { g0.toScreen(it.first, it.second) }
                            // El orden de las esquinas ya viene en pantalla; con espejo se invierten izq/der.
                            val del = if (g0.mirror) c[1] else c[0]
                            val scl = if (g0.mirror) c[3] else c[2]
                            if (hypot(down.position.x - del.x, down.position.y - del.y) <= handleR) mode = DragMode.Delete
                            else if (!sel.locked && hypot(down.position.x - scl.x, down.position.y - scl.y) <= handleR) {
                                mode = DragMode.Handle; target = sel
                            }
                        }
                        val startScene = g0.toScene(down.position)
                        if (mode == DragMode.Scene) {
                            val slop = with(density) { 8.dp.toPx() } / g0.s
                            val hit = LayerMath.hitTest(all, startScene.x, startScene.y, slop) { assets.alphaMask(it.url, it.cutout) }
                                ?: sel?.takeIf { LayerMath.contains(it, startScene.x, startScene.y, slop) }
                            if (hit != null) {
                                target = hit
                                mode = if (hit.locked) DragMode.Scene else DragMode.Layer
                                if (hit.id != latestSelected.value) { onSelect(hit.id); haptics.click() }
                            }
                        }
                        val editing = (mode == DragMode.Layer || mode == DragMode.Handle) && target != null
                        var raw: TryLayer? = target
                        val startLayer: TryLayer? = target
                        if (editing) { onBegin(); haptics.click() }

                        var pastSlop = false
                        var travelled = Offset.Zero
                        var snapX = false
                        var snapRot = false
                        var multiTouch = false
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.any { it.isConsumed && !it.pressed && false }) break
                            val pressed = event.changes.count { it.pressed }
                            if (pressed >= 2) multiTouch = true
                            val pan = event.calculatePan()
                            val zoom = event.calculateZoom()
                            val rot = event.calculateRotation()
                            val centroid = event.calculateCentroid(useCurrent = true)
                            travelled += pan
                            if (!pastSlop && (travelled.getDistance() > touchSlop || (multiTouch && (abs(zoom - 1f) > 0.002f || abs(rot) > 0.2f)))) pastSlop = true
                            if (pastSlop && mode != DragMode.Delete) {
                                val g = geo()
                                when {
                                    mode == DragMode.Handle && raw != null && startLayer != null -> {
                                        val pos = event.changes.firstOrNull { it.id == down.id }?.position ?: centroid
                                        val ps = g.toScene(pos)
                                        raw = LayerMath.handleDrag(startLayer, startScene.x, startScene.y, ps.x, ps.y, latestSceneH.value)
                                        val s = LayerMath.snap(raw!!)
                                        if (s.snappedRotation && !snapRot) haptics.tick()
                                        snapRot = s.snappedRotation
                                        onLive(s.layer.copy(cx = raw!!.cx))
                                    }
                                    mode == DragMode.Layer && raw != null -> {
                                        val sign = if (g.mirror) -1f else 1f
                                        val pivot = g.toScene(centroid)
                                        raw = LayerMath.transform(
                                            raw!!, latestSceneH.value, sign * pan.x / g.s, pan.y / g.s, zoom, sign * rot, pivot.x, pivot.y,
                                        )
                                        val s = LayerMath.snap(raw!!)
                                        if (s.snappedX && !snapX) haptics.tick()
                                        if (s.snappedRotation && !snapRot) haptics.tick()
                                        snapX = s.snappedX; snapRot = s.snappedRotation
                                        onLive(s.layer)
                                    }
                                    else -> {
                                        // Escena: zoom alrededor de los dedos + desplazamiento.
                                        val nv = ViewMath.apply(
                                            ViewState(viewport.zoom, viewport.panX, viewport.panY), zoom, pan.x, pan.y,
                                            centroid.x - g.cx, centroid.y - g.cy, baseScale(), latestSceneH.value,
                                            size.width.toFloat(), size.height.toFloat(),
                                        )
                                        viewport.zoom = nv.zoom; viewport.panX = nv.panX; viewport.panY = nv.panY
                                    }
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })

                        if (editing) {
                            onEnd()
                            if (pastSlop) haptics.tick()
                        }
                        if (!pastSlop) {
                            when (mode) {
                                DragMode.Delete -> sel?.let { onDelete(it.id); haptics.heavy() }
                                DragMode.Scene, DragMode.Layer -> {
                                    if (target == null) onSelect(null)
                                    val now = System.nanoTime() / 1_000_000f
                                    if (now - lastTapTime < 320f && target?.id == lastTapId) {
                                        if (target == null) scope.launch { viewport.reset() } else { onDoubleTapLayer(); haptics.confirm() }
                                        lastTapTime = 0f
                                    } else {
                                        lastTapTime = now
                                        lastTapId = target?.id
                                    }
                                }
                                else -> Unit
                            }
                        }
                    }
                }
            },
    ) {
        // ── Escena ───────────────────────────────────────────────────────────────────────────
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            revision.value // relee cuando llega un bitmap nuevo
            val g = geo()
            val sw = g.s
            val sh = g.s * g.sceneH
            val left = g.cx + g.panX - sw / 2f
            val top = g.cy + g.panY - sh / 2f
            val radius = 26.dp.toPx() * viewport.zoom.coerceAtMost(1.4f)
            val clip = Path().apply { addRoundRect(RoundRect(left, top, left + sw, top + sh, CornerRadius(radius))) }
            val body = assets.body(bodyPath)
            clipPath(clip) {
                inset(left, top, size.width - left - sw, size.height - top - sh) {
                    drawScene(
                        sceneAspect, darkBackground, mirror, body, layers.value,
                        bitmapOf = { assets.garment(it.url, it.cutout) },
                        appear = { appear[it.id] ?: 0f },
                        silhouetteHint = showSilhouetteHint && body == null,
                    )
                    if (compare) {
                        val cut = this.size.width * compareFraction
                        clipRect(0f, 0f, cut, this.size.height) {
                            drawScene(sceneAspect, darkBackground, mirror, body, emptyList(), bitmapOf = { null })
                        }
                    }
                }
            }
        }

        // ── Marco de selección, cuadrícula y divisor (capa propia: no invalida la escena) ───────
        val pulse = rememberInfiniteTransition(label = "selPulse")
        val pulseV by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse), label = "pulse")
        val pop = remember(selectedId) { Animatable(if (selectedId == null) 1f else 0f) }
        LaunchedEffect(selectedId) { if (selectedId != null) pop.animateTo(1f, EvMotion.bouncy()) }
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val g = geo()
            val sw = g.s
            val sh = g.s * g.sceneH
            val left = g.cx + g.panX - sw / 2f
            val top = g.cy + g.panY - sh / 2f
            if (grid && !compare) {
                val gc = Color.White.copy(alpha = 0.28f)
                val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                for (i in 1..2) {
                    drawLine(gc, Offset(left + sw * i / 3f, top), Offset(left + sw * i / 3f, top + sh), 1.5f, pathEffect = dash)
                    drawLine(gc, Offset(left, top + sh * i / 3f), Offset(left + sw, top + sh * i / 3f), 1.5f, pathEffect = dash)
                }
                drawLine(Color.White.copy(alpha = 0.5f), Offset(left + sw / 2f, top), Offset(left + sw / 2f, top + sh), 1.5f)
            }
            if (compare) {
                val x = left + sw * compareFraction
                drawLine(Color.White, Offset(x, top), Offset(x, top + sh), 3.dp.toPx())
                val r = 20.dp.toPx()
                val cy = top + sh / 2f
                drawCircle(Color.Black.copy(alpha = 0.28f), r + 3.dp.toPx(), Offset(x, cy))
                drawCircle(Color.White, r, Offset(x, cy))
                val a = Path().apply { moveTo(x - 4.dp.toPx(), cy - 6.dp.toPx()); lineTo(x - 10.dp.toPx(), cy); lineTo(x - 4.dp.toPx(), cy + 6.dp.toPx()) }
                val b = Path().apply { moveTo(x + 4.dp.toPx(), cy - 6.dp.toPx()); lineTo(x + 10.dp.toPx(), cy); lineTo(x + 4.dp.toPx(), cy + 6.dp.toPx()) }
                val st = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round)
                drawPath(a, Color.Black, style = st); drawPath(b, Color.Black, style = st)
                return@Canvas
            }
            poseOverlay?.let { drawPose(it, g, pop.value) }
            val sel = layers.value.firstOrNull { it.id == selectedId } ?: return@Canvas
            drawSelection(sel, g, pop.value, pulseV)
        }
    }
}

private fun DrawScope.drawSelection(l: TryLayer, g: Geo, pop: Float, pulse: Float) {
    val pts = LayerMath.corners(l).map { g.toScreen(it.first, it.second) }
    val frame = Path().apply { moveTo(pts[0].x, pts[0].y); lineTo(pts[1].x, pts[1].y); lineTo(pts[2].x, pts[2].y); lineTo(pts[3].x, pts[3].y); close() }
    val w = 1.6.dp.toPx()
    val dash = if (l.locked) PathEffect.dashPathEffect(floatArrayOf(14f, 10f)) else null
    drawPath(frame, Color.Black.copy(alpha = 0.35f), style = Stroke(w + 2.dp.toPx(), pathEffect = dash))
    drawPath(frame, Color.White, style = Stroke(w, pathEffect = dash))
    if (l.locked) {
        // Candado: círculo con un cuerpo y un arco.
        val c = if (g.mirror) pts[0] else pts[1]
        val r = 11.dp.toPx() * pop
        drawCircle(Color.White, r, c)
        drawRoundRect(Color.Black, Offset(c.x - r * 0.4f, c.y - r * 0.05f), Size(r * 0.8f, r * 0.55f), CornerRadius(2f))
        drawArc(Color.Black, 180f, 180f, false, Offset(c.x - r * 0.28f, c.y - r * 0.55f), Size(r * 0.56f, r * 0.7f), style = Stroke(1.6.dp.toPx()))
        return
    }
    val dot = 4.5.dp.toPx() * pop
    pts.forEach { drawCircle(Color.White, dot, it); drawCircle(Color.Black.copy(alpha = 0.4f), dot, it, style = Stroke(1.dp.toPx())) }
    // Asa de borrar (esquina superior izquierda en pantalla) y de escalar/girar (inferior derecha).
    val del = if (g.mirror) pts[1] else pts[0]
    val scl = if (g.mirror) pts[3] else pts[2]
    val rr = 13.dp.toPx() * pop
    drawCircle(Color(0xFFFF3B5C), rr, del)
    val k = rr * 0.38f
    val xs = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
    drawLine(Color.White, Offset(del.x - k, del.y - k), Offset(del.x + k, del.y + k), xs.width, StrokeCap.Round)
    drawLine(Color.White, Offset(del.x - k, del.y + k), Offset(del.x + k, del.y - k), xs.width, StrokeCap.Round)
    val ring = rr + 3.dp.toPx() * pulse
    drawCircle(Color.White.copy(alpha = 0.35f * (1f - pulse)), ring + 3.dp.toPx(), scl, style = Stroke(2.dp.toPx()))
    drawCircle(Color.White, rr, scl)
    drawCircle(Color.Black.copy(alpha = 0.25f), rr, scl, style = Stroke(1.dp.toPx()))
    // Flecha diagonal doble (escalar/girar).
    val d = rr * 0.42f
    val arrow = Path().apply {
        moveTo(scl.x - d, scl.y - d); lineTo(scl.x + d, scl.y + d)
        moveTo(scl.x + d, scl.y + d); lineTo(scl.x + d, scl.y + d * 0.3f)
        moveTo(scl.x + d, scl.y + d); lineTo(scl.x + d * 0.3f, scl.y + d)
        moveTo(scl.x - d, scl.y - d); lineTo(scl.x - d, scl.y - d * 0.3f)
        moveTo(scl.x - d, scl.y - d); lineTo(scl.x - d * 0.3f, scl.y - d)
    }
    drawPath(arrow, Color.Black, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
}


private fun DrawScope.drawPose(lm: BodyLandmarks, g: Geo, pop: Float) {
    fun s(p: Pt?): Offset? = p?.takeIf { it.ok }?.let { g.toScreen(it.x, it.y) }
    val bones = listOf(
        lm.lShoulder to lm.rShoulder, lm.lShoulder to lm.lHip, lm.rShoulder to lm.rHip, lm.lHip to lm.rHip,
        lm.lHip to lm.lKnee, lm.rHip to lm.rKnee, lm.lKnee to lm.lAnkle, lm.rKnee to lm.rAnkle,
    )
    val line = Color(0xFF4ADE80)
    for ((a, b) in bones) {
        val pa = s(a) ?: continue
        val pb = s(b) ?: continue
        drawLine(line.copy(alpha = 0.85f), pa, pb, 2.5.dp.toPx(), StrokeCap.Round)
    }
    lm.all().forEach { p ->
        val o = s(p) ?: return@forEach
        drawCircle(Color.Black.copy(alpha = 0.4f), 6.dp.toPx() * pop, o)
        drawCircle(line, 4.5.dp.toPx() * pop, o)
    }
}
