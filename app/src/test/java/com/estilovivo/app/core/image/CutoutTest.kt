package com.estilovivo.app.core.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CutoutTest {
    private val WHITE = 0xFFFFFFFF.toInt()
    private val RED = 0xFFCC2222.toInt()

    /** Fondo [bg] con un cuadrado [fg] de lado [side] en el centro. */
    private fun image(w: Int, h: Int, bg: Int, fg: Int, side: Int): IntArray {
        val px = IntArray(w * h) { bg }
        val x0 = (w - side) / 2
        val y0 = (h - side) / 2
        for (y in y0 until y0 + side) for (x in x0 until x0 + side) px[y * w + x] = fg
        return px
    }

    private fun alpha(p: Int) = p ushr 24

    @Test fun `quita el fondo uniforme y conserva la prenda`() {
        val r = Cutout.apply(image(40, 40, WHITE, RED, 20), 40, 40, 35)
        assertTrue(r.ok)
        assertEquals(0, alpha(r.pixels[0]))
        assertEquals(255, alpha(r.pixels[20 * 40 + 20]))
        assertEquals(0.75f, r.removedFraction, 0.02f)
    }

    @Test fun `un hueco interior del mismo color del fondo no se quita en el relleno por bordes`() {
        val px = image(40, 40, WHITE, RED, 24)
        for (y in 18..21) for (x in 18..21) px[y * 40 + x] = WHITE // agujero dentro de la prenda
        val r = Cutout.apply(px, 40, 40, 35)
        assertTrue(r.ok)
        assertEquals(255, alpha(r.pixels[20 * 40 + 20]))
    }

    @Test fun `fondo no uniforme no es fiable`() {
        val px = IntArray(40 * 40) { if ((it % 40 + it / 40) % 2 == 0) 0xFF000000.toInt() else WHITE }
        assertFalse(Cutout.apply(px, 40, 40, 35).ok)
        assertFalse(Cutout.isAutoCandidate(px, 40, 40))
    }

    @Test fun `prenda del color del fondo se rechaza`() {
        val r = Cutout.apply(IntArray(30 * 30) { WHITE }, 30, 30, 35)
        assertFalse(r.ok)
        assertTrue(r.removedFraction > 0.9f)
    }

    @Test fun `mayor tolerancia quita mas fondo degradado`() {
        val w = 40; val h = 40
        val px = image(w, h, WHITE, RED, 16)
        // Sombra gris suave a la izquierda
        for (y in 0 until h) for (x in 0 until 6) px[y * w + x] = 0xFFD8D8D8.toInt()
        val low = Cutout.apply(px, w, h, 5)
        val high = Cutout.apply(px, w, h, 60)
        assertTrue(high.removedFraction >= low.removedFraction)
    }

    @Test fun `imagen ya transparente no se toca`() {
        val px = IntArray(30 * 30)
        assertFalse(Cutout.apply(px, 30, 30, 35).ok)
        assertFalse(Cutout.isAutoCandidate(px, 30, 30))
    }

    @Test fun `tolerancia cero o tamanos invalidos no fallan`() {
        val px = image(40, 40, WHITE, RED, 20)
        assertFalse(Cutout.apply(px, 40, 40, 0).ok)
        assertFalse(Cutout.apply(px, 2, 2, 35).ok)
        assertFalse(Cutout.apply(IntArray(3), 40, 40, 35).ok)
    }

    // ── MaskOps ────────────────────────────────────────────────────────────────────────────

    private fun mask(w: Int, h: Int, f: (Int, Int) -> Boolean) = FloatArray(w * h) { if (f(it % w, it / w)) 1f else 0f }

    @Test fun `elimina islas pequenas y mantiene la principal`() {
        val m = mask(40, 40) { x, y -> (x in 10..29 && y in 10..29) || (x in 1..2 && y in 1..2) }
        val r = MaskOps.refine(m, 40, 40, feather = 0)
        assertEquals(0f, r.mask[1 * 40 + 1], 0f)
        assertEquals(1f, r.mask[20 * 40 + 20], 0f)
        assertEquals(400f / 1600f, r.fraction, 1e-4f)
    }

    @Test fun `rellena huecos interiores pero no el fondo exterior`() {
        val m = mask(40, 40) { x, y -> (x in 8..31 && y in 8..31) && !(x in 18..21 && y in 18..21) }
        val r = MaskOps.refine(m, 40, 40, feather = 0)
        assertEquals(1f, r.mask[20 * 40 + 20], 0f)
        assertEquals(0f, r.mask[2 * 40 + 2], 0f)
    }

    @Test fun `el suavizado crea alfa parcial en el borde`() {
        val m = mask(40, 40) { x, y -> x in 10..29 && y in 10..29 }
        val r = MaskOps.refine(m, 40, 40, feather = 1)
        val edge = r.mask[20 * 40 + 10]
        assertTrue(edge > 0f && edge < 1f)
        assertEquals(1f, r.mask[20 * 40 + 20], 1e-5f)
    }

    @Test fun `recuadro con margen y mascara vacia`() {
        val m = mask(40, 40) { x, y -> x in 10..19 && y in 20..29 }
        val b = MaskOps.bounds(m, 40, 40, 0.1f)!!
        assertTrue(b[0] <= 9 && b[1] <= 19 && b[2] >= 21 && b[3] >= 31)
        assertTrue(b[0] >= 0 && b[2] <= 40)
        assertNull(MaskOps.bounds(FloatArray(100), 10, 10, 0.1f))
    }

    @Test fun `limites de fiabilidad del recorte`() {
        assertNotNull(CutoutLimits.reject(0.02f))
        assertNotNull(CutoutLimits.reject(0.98f))
        assertNull(CutoutLimits.reject(0.5f))
        assertNull(CutoutLimits.reject(0.03f))
        assertNull(CutoutLimits.reject(0.97f))
    }

    @Test fun `mascara de alfa recoge bordes finos al reducir`() {
        val w = 256
        val px = IntArray(w * w)
        for (y in 0 until w) px[y * w + 100] = 0xFF000000.toInt() // linea fina de 1 px
        val m = AlphaMask.fromPixels(px, w, w, maxCells = 64)
        assertTrue(m.opaqueAt(100f / w, 0.5f))
        assertFalse(m.opaqueAt(0.9f, 0.5f))
        assertFalse(m.opaqueAt(Float.NaN, 0.5f))
    }
}
