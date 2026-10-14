package com.estilovivo.app.feature.tryon

import androidx.compose.runtime.Immutable
import com.estilovivo.app.core.data.model.Garment
import com.estilovivo.app.core.image.AlphaMask
import com.estilovivo.app.core.data.model.TryonLayer
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/*
 * Matemática pura (JVM, sin Android) del probador: capas, transformaciones, orden Z, historial,
 * (de)serialización de presets, colocación automática y "sorpréndeme".
 *
 * ESPACIO DE ESCENA: unidades isótropas con el ANCHO del lienzo = 1 y el ALTO = 1 / aspecto
 * (aspecto = ancho/alto del lienzo). Así rotar y escalar no distorsionan nunca, sea cual sea
 * el tamaño de pantalla (la web guardaba píxeles de pantalla y los looks no eran portables).
 */

@Immutable
@Serializable
data class TryLayer(
    val id: String,
    val garmentId: String,
    val url: String,
    val name: String = "",
    /** Centro de la capa en unidades de escena. */
    val cx: Float,
    val cy: Float,
    /** Ancho en unidades de escena (el ancho del lienzo vale 1). */
    val width: Float,
    /** Alto/ancho de la imagen (se conserva siempre: no se deforma la prenda). */
    val aspect: Float = 1f,
    /** Grados, sentido horario. */
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    val flipX: Boolean = false,
    val flipY: Boolean = false,
    val locked: Boolean = false,
    /** Tolerancia del recorte de fondo aplicado (0 = imagen original). */
    val cutout: Int = 0,
    val category: TryCategory = TryCategory.Other,
    /** Ancho inicial: la escala del usuario se acota a 0.2x-5x de este valor. */
    val baseWidth: Float = 0f,
) {
    val height: Float get() = width * aspect
}

object Scene {
    const val DEFAULT_ASPECT = 0.75f
    const val MIN_ASPECT = 0.45f
    const val MAX_ASPECT = 1.8f

    fun clampAspect(a: Float): Float = if (a.isNaN() || a <= 0f) DEFAULT_ASPECT else a.coerceIn(MIN_ASPECT, MAX_ASPECT)

    /** Alto de la escena (ancho = 1). */
    fun height(aspect: Float): Float = 1f / clampAspect(aspect)
}

/** Resultado de ajustar una capa a las "marcas" magnéticas (centro y ángulos rectos). */
data class SnapResult(val layer: TryLayer, val snappedX: Boolean, val snappedRotation: Boolean)

object LayerMath {
    const val MIN_WIDTH = 0.05f
    const val MAX_WIDTH = 2.5f
    const val MAX_LAYERS = 20
    const val MIN_OPACITY = 0.05f

    fun normalizeDeg(deg: Float): Float {
        var d = deg % 360f
        if (d > 180f) d -= 360f
        if (d <= -180f) d += 360f
        return d
    }

    /** Mantiene el centro dentro del lienzo y el ancho en un rango razonable: la capa nunca se pierde. */
    private fun fin(v: Float, d: Float) = if (v.isNaN() || v.isInfinite()) d else v

    fun widthRange(l: TryLayer): ClosedFloatingPointRange<Float> {
        val base = if (l.baseWidth > 0f) l.baseWidth else l.width
        val lo = maxOf(MIN_WIDTH, base * 0.2f)
        val hi = maxOf(lo, minOf(MAX_WIDTH, base * 5f))
        return lo..hi
    }

    fun clampToScene(l: TryLayer, sceneH: Float): TryLayer {
        val r = widthRange(l)
        return l.copy(
            width = fin(l.width, r.start).coerceIn(r.start, r.endInclusive),
            cx = fin(l.cx, 0.5f).coerceIn(0f, 1f),
            cy = fin(l.cy, sceneH / 2f).coerceIn(0f, sceneH),
            opacity = fin(l.opacity, 1f).coerceIn(MIN_OPACITY, 1f),
            rotation = normalizeDeg(fin(l.rotation, 0f)),
        )
    }

    /**
     * Gesto combinado (arrastre + pellizco + giro) alrededor del punto [pivotX],[pivotY] (el centroide de
     * los dedos, en unidades de escena). [panX]/[panY] ya están en unidades de escena.
     */
    fun transform(
        l: TryLayer, sceneH: Float,
        panX: Float, panY: Float, zoom: Float, rotateDeg: Float, pivotX: Float, pivotY: Float,
    ): TryLayer {
        if (listOf(panX, panY, rotateDeg, pivotX, pivotY).any { it.isNaN() || it.isInfinite() }) return l
        val safeZoom = if (zoom.isNaN() || zoom.isInfinite() || zoom <= 0f) 1f else zoom
        val range = widthRange(l)
        val newWidth = (l.width * safeZoom).coerceIn(range.start, range.endInclusive)
        val z = newWidth / l.width
        val r = Math.toRadians(rotateDeg.toDouble())
        val cosR = cos(r).toFloat()
        val sinR = sin(r).toFloat()
        val dx = l.cx - pivotX
        val dy = l.cy - pivotY
        val nx = pivotX + (dx * cosR - dy * sinR) * z + panX
        val ny = pivotY + (dx * sinR + dy * cosR) * z + panY
        return clampToScene(l.copy(cx = nx, cy = ny, width = newWidth, rotation = l.rotation + rotateDeg), sceneH)
    }

    /** Arrastre del asa de la esquina: escala y gira respecto al centro de la capa (como en Canva). */
    fun handleDrag(start: TryLayer, startPx: Float, startPy: Float, px: Float, py: Float, sceneH: Float): TryLayer {
        val d0 = hypot(startPx - start.cx, startPy - start.cy)
        val d1 = hypot(px - start.cx, py - start.cy)
        if (d0 < 1e-4f) return start
        val a0 = atan2(startPy - start.cy, startPx - start.cx)
        val a1 = atan2(py - start.cy, px - start.cx)
        val rot = start.rotation + Math.toDegrees((a1 - a0).toDouble()).toFloat()
        return clampToScene(start.copy(width = start.width * (d1 / d0), rotation = rot), sceneH)
    }

    /** Ajuste magnético: centrado horizontal (±1,2 %) y ángulos múltiplos de 90° (±4°). */
    fun snap(l: TryLayer): SnapResult {
        val sx = abs(l.cx - 0.5f) < 0.012f
        val rotNorm = normalizeDeg(l.rotation)
        val nearest = (Math.round(rotNorm / 90f) * 90f)
        val sr = abs(rotNorm - nearest) < 4f
        return SnapResult(l.copy(cx = if (sx) 0.5f else l.cx, rotation = if (sr) normalizeDeg(nearest) else rotNorm), sx, sr)
    }

    /** ¿El punto (px,py) de la escena cae dentro de la capa (rotada)? [slop] amplía el área táctil. */
    fun contains(l: TryLayer, px: Float, py: Float, slop: Float = 0f): Boolean {
        val r = Math.toRadians(-l.rotation.toDouble())
        val dx = px - l.cx
        val dy = py - l.cy
        val lx = (dx * cos(r) - dy * sin(r)).toFloat()
        val ly = (dx * sin(r) + dy * cos(r)).toFloat()
        return abs(lx) <= l.width / 2f + slop && abs(ly) <= l.height / 2f + slop
    }

    /** Capa más alta bajo el punto, o null. Capas con opacidad mínima siguen siendo seleccionables. */
    fun hitTest(layers: List<TryLayer>, px: Float, py: Float, slop: Float = 0f, mask: (TryLayer) -> AlphaMask? = { null }): TryLayer? =
        layers.asReversed().firstOrNull { containsOpaque(it, px, py, slop, mask(it)) }

    /** Como [contains] pero ignorando los huecos transparentes (si hay [mask]). */
    fun containsOpaque(l: TryLayer, px: Float, py: Float, slop: Float, mask: AlphaMask?): Boolean {
        if (!contains(l, px, py, slop)) return false
        if (mask == null) return true
        val r = Math.toRadians(-l.rotation.toDouble())
        val dx = px - l.cx
        val dy = py - l.cy
        var lx = (dx * cos(r) - dy * sin(r)).toFloat()
        var ly = (dx * sin(r) + dy * cos(r)).toFloat()
        if (l.flipX) lx = -lx
        if (l.flipY) ly = -ly
        val u = lx / l.width + 0.5f
        val v = ly / l.height + 0.5f
        return mask.opaqueAt(u.coerceIn(0f, 1f), v.coerceIn(0f, 1f), slop / l.width)
    }

    /** Esquinas (sup-izq, sup-der, inf-der, inf-izq) en unidades de escena, con la rotación aplicada. */
    fun corners(l: TryLayer): List<Pair<Float, Float>> {
        val r = Math.toRadians(l.rotation.toDouble())
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        val hw = l.width / 2f
        val hh = l.height / 2f
        return listOf(-hw to -hh, hw to -hh, hw to hh, -hw to hh).map { (x, y) -> (l.cx + x * c - y * s) to (l.cy + x * s + y * c) }
    }

    // ── Orden Z (índice 0 = al fondo) ───────────────────────────────────────────────────────

    fun bringToFront(list: List<TryLayer>, id: String): List<TryLayer> {
        val i = list.indexOfFirst { it.id == id }
        return if (i < 0 || i == list.lastIndex) list else list.toMutableList().also { it.add(it.removeAt(i)) }
    }

    fun sendToBack(list: List<TryLayer>, id: String): List<TryLayer> {
        val i = list.indexOfFirst { it.id == id }
        return if (i <= 0) list else list.toMutableList().also { it.add(0, it.removeAt(i)) }
    }

    fun moveForward(list: List<TryLayer>, id: String): List<TryLayer> {
        val i = list.indexOfFirst { it.id == id }
        return if (i < 0 || i == list.lastIndex) list else list.toMutableList().also { val x = it.removeAt(i); it.add(i + 1, x) }
    }

    fun moveBackward(list: List<TryLayer>, id: String): List<TryLayer> {
        val i = list.indexOfFirst { it.id == id }
        return if (i <= 0) list else list.toMutableList().also { val x = it.removeAt(i); it.add(i - 1, x) }
    }

    /** Rango Z por defecto: lo de abajo (pantalón) debajo de lo de arriba, el abrigo sobre la camiseta, accesorios al frente. */
    fun rank(c: TryCategory): Int = when (c) {
        TryCategory.Bottom -> 0
        TryCategory.Shoes -> 1
        TryCategory.Top, TryCategory.Dress, TryCategory.Other -> 2
        TryCategory.Outer -> 3
        TryCategory.Accessory -> 4
    }

    /** Categorías de las que sólo suele haber una prenda puesta (probar otra sustituye a la anterior). */
    val singleSlot = setOf(TryCategory.Top, TryCategory.Bottom, TryCategory.Dress, TryCategory.Outer, TryCategory.Shoes)

    /** Inserta [layer] justo antes de la primera capa de rango mayor (o al frente). */
    fun insertByRank(list: List<TryLayer>, layer: TryLayer): List<TryLayer> {
        val r = rank(layer.category)
        val idx = list.indexOfFirst { rank(it.category) > r }
        return if (idx < 0) list + layer else list.toMutableList().also { it.add(idx, layer) }
    }

    fun duplicate(list: List<TryLayer>, id: String, newId: String): List<TryLayer> {
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return list
        val src = list[i]
        val copy = src.copy(id = newId, cx = src.cx + 0.04f, cy = src.cy + 0.04f, locked = false)
        return list + copy
    }
}

/** Historial de deshacer/rehacer genérico e inmutable. */
data class UndoStack<T>(val past: List<T> = emptyList(), val future: List<T> = emptyList()) {
    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()

    /** Llamar con el estado ANTERIOR a un cambio: lo guarda y descarta el futuro. */
    fun record(previous: T, limit: Int = LIMIT): UndoStack<T> =
        UndoStack((past + previous).takeLast(limit), emptyList())

    fun undo(current: T): Pair<UndoStack<T>, T>? =
        if (past.isEmpty()) null else UndoStack(past.dropLast(1), future + current) to past.last()

    fun redo(current: T): Pair<UndoStack<T>, T>? =
        if (future.isEmpty()) null else UndoStack(past + current, future.dropLast(1)) to future.last()

    companion object { const val LIMIT = 60 }
}

// ─────────────────────────────────────────────────────────────────────────────────────────────
// Categorías y colocación automática
// ─────────────────────────────────────────────────────────────────────────────────────────────

enum class TryCategory(val label: String) {
    Top("Tops"), Bottom("Bottoms"), Dress("Vestidos"), Outer("Abrigos"), Shoes("Zapatos"), Accessory("Accesorios"), Other("Otros"),
}

object TryCatalog {
    private val SHOES = Regex("shoe|zapat|bota|sandal|boot|sneaker")
    private val DRESS = Regex("dress|vestido|enterizo|\\bmono\\b|conjunto|\\bset\\b")
    private val OUTER = Regex("outer|chaqueta|abrigo|saco|jacket|coat|blazer|cazadora")
    private val ACC = Regex("accesorio|accessor|\\bacc\\b|sombrero|gorra|bolso|gafas|collar|cinturon|bufanda")
    private val BOTTOM = Regex("\\bbottom|pantal|falda|short|jean|trouser|vaquero|legging")
    private val TOP = Regex("top|camis|blusa|shirt|polo|jersey|sudadera|suéter|sueter|sweater|hoodie|deport|sport|activ|swim|bikini|bano|baño")

    fun categoryOf(type: String): TryCategory {
        val t = type.lowercase()
        return when {
            SHOES.containsMatchIn(t) -> TryCategory.Shoes
            DRESS.containsMatchIn(t) -> TryCategory.Dress
            OUTER.containsMatchIn(t) -> TryCategory.Outer
            ACC.containsMatchIn(t) -> TryCategory.Accessory
            BOTTOM.containsMatchIn(t) -> TryCategory.Bottom
            TOP.containsMatchIn(t) -> TryCategory.Top
            else -> TryCategory.Other
        }
    }
}

data class Placement(val cx: Float, val cy: Float, val width: Float)

object AutoPlacer {
    private data class Spec(val width: Float, val top: Float, val maxHeight: Float)

    private fun spec(c: TryCategory) = when (c) {
        TryCategory.Dress -> Spec(0.55f, 0.14f, 0.55f)
        TryCategory.Bottom -> Spec(0.42f, 0.44f, 0.42f)
        TryCategory.Outer -> Spec(0.62f, 0.12f, 0.42f)
        TryCategory.Top -> Spec(0.50f, 0.14f, 0.36f)
        TryCategory.Shoes -> Spec(0.34f, 0.87f, 0.11f)
        TryCategory.Accessory -> Spec(0.24f, 0.02f, 0.14f)
        TryCategory.Other -> Spec(0.45f, 0.20f, 0.40f)
    }

    /**
     * Colocación proporcional al cuerpo. [aspect] = alto/ancho de la imagen de la prenda. Si la prenda es
     * muy alta se reduce el ancho para respetar la altura máxima de su zona (sin deformar nunca).
     */
    fun place(category: TryCategory, aspect: Float, sceneH: Float): Placement {
        val s = spec(category)
        val a = if (aspect.isNaN() || aspect <= 0.05f) 1f else aspect.coerceIn(0.1f, 6f)
        var w = s.width
        val maxH = s.maxHeight * sceneH
        if (w * a > maxH) w = maxH / a
        w = w.coerceIn(LayerMath.MIN_WIDTH, LayerMath.MAX_WIDTH)
        val h = w * a
        return Placement(cx = 0.5f, cy = (s.top * sceneH + h / 2f).coerceAtMost(sceneH), width = w)
    }
}

object OutfitBuilder {
    /**
     * Elige prendas del armario para un look completo (de fondo a frente): parte baja, calzado, parte alta o vestido,
     * y a veces abrigo/accesorio. Devuelve vacío si no hay prendas utilizables.
     */
    fun build(garments: List<Garment>, random: Random, avoidIds: Set<String> = emptySet()): List<Garment> {
        val usable = garments.filter { it.imageUrl.isNotBlank() && !it.isWashing }
        if (usable.isEmpty()) return emptyList()
        fun pick(c: TryCategory): Garment? {
            val all = usable.filter { TryCatalog.categoryOf(it.type) == c }
            if (all.isEmpty()) return null
            val fresh = all.filter { it.id !in avoidIds }
            return (fresh.ifEmpty { all }).random(random)
        }
        val top = pick(TryCategory.Top)
        val bottom = pick(TryCategory.Bottom)
        val dress = pick(TryCategory.Dress)
        val useDress = dress != null && (top == null || bottom == null || random.nextFloat() < 0.35f)
        val out = ArrayList<Garment>()
        if (useDress) {
            pick(TryCategory.Shoes)?.let { out += it }
            out += dress!!
        } else {
            bottom?.let { out += it }
            pick(TryCategory.Shoes)?.let { out += it }
            top?.let { out += it }
        }
        if (random.nextFloat() < 0.45f) pick(TryCategory.Outer)?.let { out += it }
        if (random.nextFloat() < 0.4f) pick(TryCategory.Accessory)?.let { out += it }
        if (out.isEmpty()) pick(TryCategory.Other)?.let { out += it }
        return out
    }
}

// ─────────────────────────────────────────────────────────────────────────────────────────────
// Presets: (de)serialización
// ─────────────────────────────────────────────────────────────────────────────────────────────

object LayerCodec {
    /** Referencia para convertir los píxeles de pantalla que guardaba la web (ancho de lienzo típico). */
    private const val LEGACY_REF = 400f

    /** `TryonLayer` del servidor: x,y,w,h como fracciones del ancho/alto del lienzo (esquina sup-izq sin rotar). */
    fun toApi(l: TryLayer, sceneH: Float): TryonLayer = TryonLayer(
        id = l.id, garmentId = l.garmentId, url = l.url,
        x = l.cx - l.width / 2f, y = (l.cy - l.height / 2f) / sceneH,
        w = l.width, h = l.height / sceneH,
        rotation = l.rotation, opacity = l.opacity, flipX = l.flipX, flipY = l.flipY, locked = l.locked,
    )

    private fun isLegacy(a: TryonLayer) = a.w > 2f || a.h > 2f || a.x > 2f || a.y > 2f

    /** Convierte una capa del servidor; devuelve null si es inservible (sin tamaño o sin prenda). */
    fun fromApi(a: TryonLayer, sceneH: Float, newId: String): TryLayer? {
        if (a.garmentId.isBlank() || a.w <= 0f || a.h <= 0f || a.w.isNaN() || a.h.isNaN()) return null
        val base = if (isLegacy(a)) {
            val w = a.w / LEGACY_REF
            val h = a.h / LEGACY_REF
            TryLayer(newId, a.garmentId, a.url, cx = (a.x + a.w / 2f) / LEGACY_REF, cy = (a.y + a.h / 2f) / LEGACY_REF, width = w, aspect = h / w, baseWidth = w)
        } else {
            val aspect = a.h * sceneH / a.w
            TryLayer(newId, a.garmentId, a.url, cx = a.x + a.w / 2f, cy = (a.y + a.h / 2f) * sceneH, width = a.w, aspect = aspect, baseWidth = a.w)
        }
        return LayerMath.clampToScene(
            base.copy(
                aspect = base.aspect.coerceIn(0.1f, 6f),
                rotation = a.rotation, opacity = a.opacity, flipX = a.flipX, flipY = a.flipY, locked = a.locked,
            ),
            sceneH,
        )
    }

    /** Resuelve un preset contra el armario actual: prendas borradas se omiten (se cuentan en `missing`). */
    fun resolve(api: List<TryonLayer>, garments: List<Garment>, sceneH: Float, newId: () -> String): ResolvedPreset {
        val byId = garments.associateBy { it.id }
        var missing = 0
        val out = ArrayList<TryLayer>()
        for (a in api) {
            val g = byId[a.garmentId]
            if (g == null) { missing++; continue }
            val l = fromApi(a, sceneH, newId()) ?: continue
            out += l.copy(url = g.imageUrl, name = g.name, category = TryCatalog.categoryOf(g.type))
            if (out.size >= LayerMath.MAX_LAYERS) break
        }
        return ResolvedPreset(out, missing)
    }
}

data class ResolvedPreset(val layers: List<TryLayer>, val missing: Int)

object PresetRules {
    const val MAX_PRESETS = 50
    const val MAX_NAME = 80
    const val MAX_THUMB_BYTES = 300_000

    val occasions = listOf("casual" to "Casual", "formal" to "Formal", "sport" to "Deporte", "party" to "Fiesta")

    fun occasionLabel(id: String?): String? = occasions.firstOrNull { it.first == id }?.second ?: id?.takeIf { it.isNotBlank() }

    fun cleanName(raw: String): String = raw.trim().replace(Regex("\\s+"), " ").take(MAX_NAME)

    /** Tamaño aproximado en bytes de un data URL base64 (para comprobar el límite de miniatura). */
    fun dataUrlBytes(dataUrl: String): Int = (dataUrl.substringAfter(',', "").length * 3L / 4L).toInt()
}


/** Zoom/desplazamiento de la ESCENA (pura, testeable): siempre finito y acotado. */
data class ViewState(val zoom: Float = 1f, val panX: Float = 0f, val panY: Float = 0f)

object ViewMath {
    const val MIN_ZOOM = 1f
    const val MAX_ZOOM = 5f

    /**
     * @param ox,oy centroide de los dedos relativo al centro del contenedor (px).
     * @param s0 escala base (px por unidad de escena cuando zoom = 1).
     */
    fun apply(
        v: ViewState, zoomChange: Float, dx: Float, dy: Float, ox: Float, oy: Float,
        s0: Float, sceneH: Float, cw: Float, ch: Float,
    ): ViewState {
        if (listOf(dx, dy, ox, oy, s0, sceneH, cw, ch).any { it.isNaN() || it.isInfinite() } || s0 <= 0f) return v
        val zc = if (zoomChange.isNaN() || zoomChange.isInfinite() || zoomChange <= 0f) 1f else zoomChange
        val z = (v.zoom * zc).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val applied = z / v.zoom
        var px = ox - (ox - v.panX) * applied + dx
        var py = oy - (oy - v.panY) * applied + dy
        val s = s0 * z
        val sw = s
        val sh = s * sceneH
        val lx = kotlin.math.abs(sw - cw) / 2f + minOf(sw, cw) * 0.3f
        val ly = kotlin.math.abs(sh - ch) / 2f + minOf(sh, ch) * 0.3f
        px = px.coerceIn(-lx, lx)
        py = py.coerceIn(-ly, ly)
        return ViewState(z, px, py)
    }
}
