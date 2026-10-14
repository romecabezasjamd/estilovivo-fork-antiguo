package com.estilovivo.app.core.image

/**
 * Post-proceso de máscaras de recorte (pura JVM): quita islas pequeñas, rellena huecos internos,
 * suaviza el borde y calcula el recuadro. Las máscaras son `FloatArray` 0..1 de `w*h` (fila a fila).
 */
object MaskOps {
    /** Resultado de [refine]: máscara final y fracción de píxeles de primer plano. */
    class Refined(val mask: FloatArray, val fraction: Float)

    /**
     * @param islandRatio una isla se elimina si su área es menor que `islandRatio` × la de la mayor (0..1).
     * @param feather radio del suavizado del borde en píxeles (0 = sin suavizar).
     */
    fun refine(input: FloatArray, w: Int, h: Int, islandRatio: Float = 0.02f, feather: Int = 1): Refined {
        require(input.size >= w * h) { "máscara demasiado pequeña" }
        val n = w * h
        val fg = BooleanArray(n) { input[it] >= 0.5f }
        removeIslands(fg, w, h, islandRatio)
        fillHoles(fg, w, h)
        var count = 0
        for (v in fg) if (v) count++
        val m = FloatArray(n) { if (fg[it]) 1f else 0f }
        val out = if (feather > 0 && count > 0) boxBlur(m, w, h, feather) else m
        return Refined(out, count.toFloat() / n)
    }

    internal fun removeIslands(fg: BooleanArray, w: Int, h: Int, ratio: Float) {
        val n = w * h
        val label = IntArray(n)
        val areas = ArrayList<Int>()
        val stack = IntArray(n)
        for (start in 0 until n) {
            if (!fg[start] || label[start] != 0) continue
            val id = areas.size + 1
            var sp = 0
            var area = 0
            stack[sp++] = start; label[start] = id
            while (sp > 0) {
                val i = stack[--sp]
                area++
                val x = i % w
                val y = i / w
                if (x > 0 && fg[i - 1] && label[i - 1] == 0) { label[i - 1] = id; stack[sp++] = i - 1 }
                if (x < w - 1 && fg[i + 1] && label[i + 1] == 0) { label[i + 1] = id; stack[sp++] = i + 1 }
                if (y > 0 && fg[i - w] && label[i - w] == 0) { label[i - w] = id; stack[sp++] = i - w }
                if (y < h - 1 && fg[i + w] && label[i + w] == 0) { label[i + w] = id; stack[sp++] = i + w }
            }
            areas += area
        }
        if (areas.isEmpty()) return
        val biggest = areas.max()
        val min = (biggest * ratio).toInt()
        for (i in 0 until n) if (fg[i] && areas[label[i] - 1] < min) fg[i] = false
    }

    /** El fondo no conectado con el borde es un hueco interior: pasa a primer plano. */
    internal fun fillHoles(fg: BooleanArray, w: Int, h: Int) {
        val n = w * h
        val outside = BooleanArray(n)
        val stack = IntArray(n)
        var sp = 0
        fun seed(i: Int) { if (!fg[i] && !outside[i]) { outside[i] = true; stack[sp++] = i } }
        for (x in 0 until w) { seed(x); seed((h - 1) * w + x) }
        for (y in 0 until h) { seed(y * w); seed(y * w + w - 1) }
        while (sp > 0) {
            val i = stack[--sp]
            val x = i % w
            val y = i / w
            if (x > 0) seed(i - 1)
            if (x < w - 1) seed(i + 1)
            if (y > 0) seed(i - w)
            if (y < h - 1) seed(i + w)
        }
        for (i in 0 until n) if (!fg[i] && !outside[i]) fg[i] = true
    }

    internal fun boxBlur(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        val tmp = FloatArray(src.size)
        val out = FloatArray(src.size)
        val k = 2 * r + 1
        for (y in 0 until h) {
            var acc = 0f
            for (x in -r..r) acc += src[y * w + x.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                tmp[y * w + x] = acc / k
                acc += src[y * w + (x + r + 1).coerceAtMost(w - 1)] - src[y * w + (x - r).coerceAtLeast(0)]
            }
        }
        for (x in 0 until w) {
            var acc = 0f
            for (y in -r..r) acc += tmp[y.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                out[y * w + x] = acc / k
                acc += tmp[(y + r + 1).coerceAtMost(h - 1) * w + x] - tmp[(y - r).coerceAtLeast(0) * w + x]
            }
        }
        return out
    }

    /** Recuadro (left, top, right, bottom exclusivos) del primer plano con margen relativo; null si está vacío. */
    fun bounds(mask: FloatArray, w: Int, h: Int, margin: Float, threshold: Float = 0.02f): IntArray? {
        var l = w; var t = h; var r = -1; var b = -1
        for (y in 0 until h) for (x in 0 until w) {
            if (mask[y * w + x] > threshold) {
                if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > b) b = y
            }
        }
        if (r < 0) return null
        val mx = ((r - l + 1) * margin).toInt().coerceAtLeast(1)
        val my = ((b - t + 1) * margin).toInt().coerceAtLeast(1)
        return intArrayOf((l - mx).coerceAtLeast(0), (t - my).coerceAtLeast(0), (r + 1 + mx).coerceAtMost(w), (b + 1 + my).coerceAtMost(h))
    }
}

/**
 * Máscara de opacidad de baja resolución (≤ 64 celdas por lado) para el hit-test: tocar un hueco transparente
 * de la prenda no la selecciona. Cada celda guarda el alfa máximo de su bloque (así no se pierden bordes finos).
 */
class AlphaMask(val w: Int, val h: Int, private val opaque: BooleanArray) {
    /** (u,v) en 0..1 sobre la imagen. [radius] en fracción de la imagen: tolerancia del dedo. */
    fun opaqueAt(u: Float, v: Float, radius: Float = 0f): Boolean {
        if (u.isNaN() || v.isNaN()) return false
        val cx = (u * w).toInt()
        val cy = (v * h).toInt()
        val rx = (radius * w).toInt().coerceIn(0, 6)
        val ry = (radius * h).toInt().coerceIn(0, 6)
        for (y in (cy - ry)..(cy + ry)) for (x in (cx - rx)..(cx + rx)) {
            if (x in 0 until w && y in 0 until h && opaque[y * w + x]) return true
        }
        return false
    }

    companion object {
        /** [argb] píxeles ARGB de `iw*ih`; umbral de alfa 40/255. */
        fun fromPixels(argb: IntArray, iw: Int, ih: Int, maxCells: Int = 64): AlphaMask {
            val scale = maxOf(iw, ih).toFloat() / maxCells
            val cw = if (scale <= 1f) iw else (iw / scale).toInt().coerceAtLeast(1)
            val ch = if (scale <= 1f) ih else (ih / scale).toInt().coerceAtLeast(1)
            val cells = BooleanArray(cw * ch)
            for (y in 0 until ih) {
                val cyy = (y * ch / ih).coerceAtMost(ch - 1)
                for (x in 0 until iw) {
                    if ((argb[y * iw + x] ushr 24) >= 40) cells[cyy * cw + (x * cw / iw).coerceAtMost(cw - 1)] = true
                }
            }
            return AlphaMask(cw, ch, cells)
        }

        /** Máscara opaca entera (sin información: se comporta como un rectángulo). */
        fun solid(): AlphaMask = AlphaMask(1, 1, booleanArrayOf(true))
    }
}
