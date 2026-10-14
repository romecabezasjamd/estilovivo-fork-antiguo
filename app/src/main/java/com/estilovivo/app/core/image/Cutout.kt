package com.estilovivo.app.core.image

/**
 * Recorte de fondo uniforme sin dependencias: relleno por inundación (flood-fill) desde los bordes
 * sobre píxeles ARGB (`IntArray`), con tolerancia ajustable. Pura JVM (testeable sin Android).
 *
 * Fiabilidad: sólo se considera válido si el borde de la imagen es casi uniforme y el recorte elimina una
 * parte razonable (ni nada, ni casi todo). Si no, [apply] devuelve `ok = false` y se conserva la original.
 */
object Cutout {
    const val DEFAULT_TOLERANCE = 35
    const val MAX_TOLERANCE = 100

    class Result(val pixels: IntArray, val removedFraction: Float, val ok: Boolean)

    /** Distancia euclídea máxima (0..441) aceptada como "fondo" para una tolerancia 0..100. */
    internal fun maxDistance(tolerance: Int): Float = 6f + tolerance.coerceIn(0, MAX_TOLERANCE) * 1.3f

    private fun alpha(p: Int) = p ushr 24
    private fun red(p: Int) = (p shr 16) and 0xFF
    private fun green(p: Int) = (p shr 8) and 0xFF
    private fun blue(p: Int) = p and 0xFF

    private fun dist2(p: Int, r: Int, g: Int, b: Int): Int {
        val dr = red(p) - r
        val dg = green(p) - g
        val db = blue(p) - b
        return dr * dr + dg * dg + db * db
    }

    /** Color de fondo estimado (mediana por canal del perímetro) o null si el borde ya es transparente. */
    internal class Background(val r: Int, val g: Int, val b: Int, val uniform: Float, val alreadyTransparent: Boolean)

    internal fun detectBackground(px: IntArray, w: Int, h: Int): Background? {
        if (w < 4 || h < 4 || px.size < w * h) return null
        val border = ArrayList<Int>(2 * (w + h))
        for (x in 0 until w) { border += px[x]; border += px[(h - 1) * w + x] }
        for (y in 1 until h - 1) { border += px[y * w]; border += px[y * w + w - 1] }
        val transparent = border.count { alpha(it) < 16 }
        if (transparent > border.size * 0.5f) return Background(0, 0, 0, 1f, alreadyTransparent = true)
        val opaque = border.filter { alpha(it) >= 16 }
        fun median(sel: (Int) -> Int): Int = opaque.map(sel).sorted()[opaque.size / 2]
        val r = median(::red); val g = median(::green); val b = median(::blue)
        val limit = 40 * 40
        val near = opaque.count { dist2(it, r, g, b) <= limit }
        return Background(r, g, b, near.toFloat() / border.size, alreadyTransparent = false)
    }

    /** ¿Tiene sentido recortar automáticamente? (borde uniforme y no transparente ya). */
    fun isAutoCandidate(px: IntArray, w: Int, h: Int): Boolean {
        val bg = detectBackground(px, w, h) ?: return false
        return !bg.alreadyTransparent && bg.uniform >= 0.9f
    }

    /**
     * Elimina el fondo conectado con el borde. [tolerance] 0..100. Los píxeles del contorno del sujeto se suavizan
     * (alfa parcial) para evitar el "serrucho". Devuelve `ok=false` (y los píxeles originales) si no es fiable.
     */
    fun apply(src: IntArray, w: Int, h: Int, tolerance: Int): Result {
        if (tolerance <= 0 || w < 4 || h < 4 || src.size < w * h) return Result(src, 0f, false)
        val bg = detectBackground(src, w, h) ?: return Result(src, 0f, false)
        if (bg.alreadyTransparent || bg.uniform < 0.85f) return Result(src, 0f, false)

        val limit = maxDistance(tolerance).let { it * it }.toInt()
        val total = w * h
        val removed = BooleanArray(total)
        val stack = IntArray(total)
        var sp = 0
        var count = 0

        fun isBg(i: Int): Boolean {
            val p = src[i]
            return alpha(p) < 16 || dist2(p, bg.r, bg.g, bg.b) <= limit
        }
        fun seed(i: Int) {
            if (!removed[i] && isBg(i)) { removed[i] = true; stack[sp++] = i; count++ }
        }
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

        val fraction = count.toFloat() / total
        // Nada que quitar o casi todo (prenda del mismo color que el fondo): no es fiable.
        if (fraction < 0.04f || fraction > 0.92f) return Result(src, fraction, false)

        val out = IntArray(total)
        for (i in 0 until total) {
            if (removed[i]) { out[i] = 0; continue }
            val x = i % w
            val y = i / w
            val edge = (x > 0 && removed[i - 1]) || (x < w - 1 && removed[i + 1]) || (y > 0 && removed[i - w]) || (y < h - 1 && removed[i + w])
            val p = src[i]
            out[i] = if (edge) ((alpha(p) * 150 / 255) shl 24) or (p and 0x00FFFFFF) else p
        }
        return Result(out, fraction, true)
    }
}
