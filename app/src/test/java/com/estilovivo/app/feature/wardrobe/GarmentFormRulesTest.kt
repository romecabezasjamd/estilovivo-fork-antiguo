package com.estilovivo.app.feature.wardrobe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GarmentFormRulesTest {
    @Test fun `precio de venta valido solo entre 0 y 100000`() {
        assertEquals(25.0, GarmentFormRules.validSalePrice("25")!!, 0.0)
        assertEquals(12.5, GarmentFormRules.validSalePrice("12,5")!!, 0.0)
        assertEquals(12.35, GarmentFormRules.validSalePrice("12.349")!!, 0.0)
        assertEquals(100000.0, GarmentFormRules.validSalePrice("100000")!!, 0.0)
        assertNull(GarmentFormRules.validSalePrice("0"))
        assertNull(GarmentFormRules.validSalePrice("-3"))
        assertNull(GarmentFormRules.validSalePrice("100000.01"))
        assertNull(GarmentFormRules.validSalePrice(""))
        assertNull(GarmentFormRules.validSalePrice("abc"))
        assertNull(GarmentFormRules.validSalePrice("NaN"))
        assertNull(GarmentFormRules.validSalePrice("Infinity"))
    }

    @Test fun `validacion del formulario por campos`() {
        val empty = GarmentFormRules.validate(GarmentForm(), requirePhoto = true, requirePrice = true)
        assertNotNull(empty.name); assertNotNull(empty.price); assertNotNull(empty.photo)
        val ok = GarmentFormRules.validate(GarmentForm(name = "  Falda ", price = "30", photoUri = "content://x"), true, true)
        assertFalse(ok.any)
        val noPriceNeeded = GarmentFormRules.validate(GarmentForm(name = "A"), requirePhoto = false, requirePrice = false)
        assertFalse(noPriceNeeded.any)
        assertTrue(GarmentFormRules.validate(GarmentForm(name = "   "), false, false).name != null)
    }

    @Test fun `imagen valida solo jpeg png webp y hasta 10 MB`() {
        assertNull(GarmentFormRules.imageProblem("image/jpeg", 1000))
        assertNull(GarmentFormRules.imageProblem("IMAGE/PNG", 10L * 1024 * 1024))
        assertNull(GarmentFormRules.imageProblem("image/webp", null))
        assertNotNull(GarmentFormRules.imageProblem("image/gif", 10))
        assertNotNull(GarmentFormRules.imageProblem("image/heic", 10))
        assertNotNull(GarmentFormRules.imageProblem(null, 10))
        assertNotNull(GarmentFormRules.imageProblem("image/jpeg", 10L * 1024 * 1024 + 1))
    }

    @Test fun `alta no envia campos vacios y respeta venta`() {
        val d = GarmentFormRules.draftForAdd(GarmentForm(name = " Camisa ", category = "top", color = " ", brand = "", size = ""))
        assertEquals("Camisa", d.name)
        assertNull(d.color); assertNull(d.brand); assertNull(d.size); assertNull(d.price); assertNull(d.condition)
        assertEquals(false, d.forSale)
        val sale = GarmentFormRules.draftForAdd(GarmentForm(name = "A", price = "9,99", condition = "fair"), forSale = true)
        assertEquals(true, sale.forSale); assertEquals(9.99, sale.price!!, 0.0); assertEquals("fair", sale.condition)
    }

    @Test fun `edicion envia cadenas vacias para borrar campos y borra el precio si no esta en venta`() {
        val original = garment("1", name = "Vieja", forSale = true, price = 40.0, brand = "Zara").copy(size = "M", description = "algo")
        val form = GarmentForm.of(original).copy(name = "Nueva", brand = "", size = "", description = "", forSale = false)
        val d = GarmentFormRules.draftForEdit(original, form)
        assertEquals("Nueva", d.name)
        assertEquals("", d.brand); assertEquals("", d.size); assertEquals("", d.description)
        assertNull(d.price)
        assertEquals(false, d.forSale)
    }

    @Test fun `quitar de la venta borra el precio`() {
        val d = GarmentFormRules.draftCancelSale(garment("1", forSale = true, price = 15.0))
        assertEquals(false, d.forSale)
        assertNull(d.price)
    }

    @Test fun `vender una prenda la saca de la lavadora`() {
        val d = GarmentFormRules.draftForSale(garment("1", washing = true), GarmentForm(price = "20", condition = "worn"))
        assertEquals(true, d.forSale); assertEquals(20.0, d.price!!, 0.0); assertEquals("worn", d.condition); assertEquals(false, d.isWashing)
    }

    @Test fun `formato de precios`() {
        assertEquals("25", formatPriceInput(25.0))
        assertEquals("24.5", formatPriceInput(24.5))
        assertEquals("24.99", formatPriceInput(24.99))
        assertEquals("25 €", formatPrice(25.0))
        assertEquals("24,50 €", formatPrice(24.5))
    }

    @Test fun `el formulario se guarda y restaura`() {
        val f = GarmentForm(name = "a", category = "shoes", color = "c", season = "winter", brand = "b", size = "s", condition = "worn", description = "d", price = "1", forSale = true, photoUri = "content://p")
        val saved = with(GarmentForm.Saver) { androidx.compose.runtime.saveable.SaverScope { true }.save(f) }
        assertEquals(f, GarmentForm.Saver.restore(saved as List<Any?>))
    }
}
