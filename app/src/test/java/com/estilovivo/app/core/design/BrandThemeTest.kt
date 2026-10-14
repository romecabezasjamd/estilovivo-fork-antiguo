package com.estilovivo.app.core.design

import com.estilovivo.app.core.data.session.ThemeCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandThemeTest {
    private val lightSurface = 0xFFFFFF
    private val lightInk = 0x0B0B0F
    private val lightOnInk = 0xFFFFFF
    private val darkSurface = 0x17171C
    private val darkInk = 0xF5F5F8
    private val darkOnInk = 0x0B0B0F

    private fun derive(seed: Int?, dark: Boolean) =
        if (dark) BrandMath.derive(seed, darkSurface, darkInk, darkOnInk) else BrandMath.derive(seed, lightSurface, lightInk, lightOnInk)

    @Test fun `onBrand cumple 4,5 en todos los presets en claro y oscuro`() {
        for (p in BrandPresets.all) for (dark in listOf(false, true)) {
            val b = derive(p.rgb, dark)
            assertTrue("${p.id} dark=$dark", BrandMath.contrast(b.brand, b.onBrand) >= 4.5)
            assertTrue("${p.id} nav", BrandMath.contrast(b.navBrand, b.onNavBrand) >= 4.5)
        }
    }

    @Test fun `la marca es visible sobre la superficie (3 a 1) y brandInk legible (4,5 a 1)`() {
        for (p in BrandPresets.all.filter { it.rgb != null }) {
            val l = derive(p.rgb, false)
            val d = derive(p.rgb, true)
            assertTrue(p.id, BrandMath.contrast(l.brand, lightSurface) >= 3.0)
            assertTrue(p.id, BrandMath.contrast(d.brand, darkSurface) >= 3.0)
            assertTrue(p.id, BrandMath.contrast(l.brandInk, lightSurface) >= 4.5)
            assertTrue(p.id, BrandMath.contrast(d.brandInk, darkSurface) >= 4.5)
            assertTrue(p.id, BrandMath.contrast(d.navBrand, 0x0B0B0F) >= 3.0)
        }
    }

    @Test fun `clasico conserva la tinta original`() {
        val l = derive(null, false)
        assertEquals(lightInk, l.brand); assertEquals(lightOnInk, l.onBrand)
        val d = derive(null, true)
        assertEquals(darkInk, d.brand); assertEquals(darkOnInk, d.onBrand)
        assertEquals(0xFFFFFF, l.navBrand)
    }

    @Test fun `ids de presets coinciden con la web e incluyen clasico`() {
        val ids = BrandPresets.all.map { it.id }
        assertEquals(BrandPresets.CLASSIC, ids.first())
        assertEquals(ids.size, ids.toSet().size)
        listOf("pink", "blue", "green", "lavender", "amber", "rose", "indigo", "teal", "forest", "midnight", "petrol", "coral")
            .forEach { assertTrue(it, it in ids) }
    }

    @Test fun `parseo y validacion de hex`() {
        assertEquals(0xEC4899, BrandMath.parseHex("#ec4899"))
        assertEquals(0xEC4899, BrandMath.parseHex(" EC4899 "))
        assertEquals("#EC4899", BrandMath.normalizeHex("ec4899"))
        for (bad in listOf(null, "", "#fff", "#12345", "#1234567", "#GGGGGG", "rojo", "#12 456", "#ec4899ff"))
            assertNull(bad, BrandMath.parseHex(bad))
        assertEquals("#00FF0A", BrandMath.toHex(0x00FF0A))
    }

    @Test fun `preset desconocido o hex invalido vuelve a clasico`() {
        assertNull(BrandChoice("noexiste").seed)
        assertNull(BrandChoice("").seed)
        assertNull(BrandChoice("classic", "zzz").seed)
        assertEquals(BrandPresets.CLASSIC, BrandPresets.normalizeId("noexiste"))
        assertEquals(BrandPresets.CLASSIC, BrandPresets.normalizeId(null))
        assertEquals("teal", BrandPresets.normalizeId(" Teal "))
        assertEquals(0x14B8A6, BrandChoice("teal", "bad").seed)
        // el color propio válido gana al preset
        assertEquals(0x112233, BrandChoice("teal", "#112233").seed)
    }

    @Test fun `derivacion estable y determinista`() {
        for (p in BrandPresets.all) for (dark in listOf(false, true)) assertEquals(derive(p.rgb, dark), derive(p.rgb, dark))
        // oscuro aclara las marcas muy oscuras
        val dark = derive(0x083344, true)
        assertTrue(BrandMath.luminance(dark.brand) > BrandMath.luminance(0x083344))
        // claro no toca una marca que ya contrasta
        assertEquals(0x166534, derive(0x166534, false).brand)
    }

    @Test fun `correccion de color propio demasiado claro`() {
        val fix = BrandMath.fixCustom(0xFFFFE0)
        assertTrue(fix.changed)
        assertTrue(BrandMath.contrast(fix.rgb, 0xFFFFFF) >= 1.6)
        val ok = BrandMath.fixCustom(0x3B82F6)
        assertFalse(ok.changed); assertEquals(0x3B82F6, ok.rgb)
        // cualquier color corregido siempre produce onBrand legible
        for (rgb in listOf(0xFFFFFF, 0x000000, 0x808080, 0xFFFF00, 0x00FFFF)) {
            val b = derive(BrandMath.fixCustom(rgb).rgb, false)
            assertTrue(BrandMath.contrast(b.brand, b.onBrand) >= 4.5)
        }
    }

    @Test fun `hsv ida y vuelta`() {
        for (rgb in listOf(0xEC4899, 0x3B82F6, 0x166534, 0xFFFFFF, 0x000000, 0x808080)) {
            val (h, s, v) = BrandMath.toHsv(rgb)
            assertEquals(rgb, BrandMath.fromHsv(h, s, v))
        }
    }

    @Test fun `serializacion de AppPrefs normaliza tema`() {
        assertEquals("classic", ThemeCodec.preset(null))
        assertEquals("classic", ThemeCodec.preset("  "))
        assertEquals("pink", ThemeCodec.preset(" Pink "))
        assertEquals("#EC4899", ThemeCodec.customColor("ec4899"))
        assertNull(ThemeCodec.customColor(""))
        assertNull(ThemeCodec.customColor("#abc"))
        assertNotNull(ThemeCodec.customColor("#abcdef"))
        // el valor guardado vuelve a leerse igual
        val saved = ThemeCodec.customColor("#1a2b3c")
        assertEquals(saved, ThemeCodec.customColor(saved))
    }
}
