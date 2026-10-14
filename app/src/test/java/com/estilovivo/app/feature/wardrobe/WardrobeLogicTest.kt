package com.estilovivo.app.feature.wardrobe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

class WardrobeLogicTest {
    private val all = listOf(
        garment("1", "Camiseta rayas", "top", "Azul", "summer", usage = 5, brand = "Zara"),
        garment("2", "Vaqueros", "bottom", "azul", "all", usage = 9),
        garment("3", "Botas", "shoes", "Negro", "winter", usage = 1, brand = "Dr. Martens"),
        garment("4", "Abrigo lana", "outer", "gris", "winter", usage = 0),
        garment("5", "Baño bikini", "Traje de baño", "rojo", "summer", usage = 2),
        garment("6", "Jersey en la lavadora", "top", "azul", "winter", usage = 3, washing = true),
    )

    @Test fun `categoryOf normaliza ids, sinonimos y texto libre`() {
        assertEquals("top", WardrobeCatalog.categoryOf("top"))
        assertEquals("top", WardrobeCatalog.categoryOf("Camiseta"))
        assertEquals("outerwear", WardrobeCatalog.categoryOf("outer"))
        assertEquals("outerwear", WardrobeCatalog.categoryOf("Abrigo"))
        assertEquals("swimwear", WardrobeCatalog.categoryOf("Traje de baño"))
        assertEquals("bottom", WardrobeCatalog.categoryOf("pantalón"))
        assertEquals("shoes", WardrobeCatalog.categoryOf("Zapatos"))
        assertEquals("accessories", WardrobeCatalog.categoryOf("accessories"))
        assertEquals("top", WardrobeCatalog.categoryOf(""))
        assertEquals("top", WardrobeCatalog.categoryOf(null))
    }

    @Test fun `la lavadora queda fuera del armario`() {
        val closet = WardrobeLogic.closet(all, WardrobeFilters())
        assertEquals(listOf("1", "2", "3", "4", "5"), closet.map { it.id })
    }

    @Test fun `filtra por categoria usando la misma normalizacion que los contadores`() {
        val outer = WardrobeLogic.closet(all, WardrobeFilters(category = "outerwear"))
        assertEquals(listOf("4"), outer.map { it.id })
        val swim = WardrobeLogic.closet(all, WardrobeFilters(category = "swimwear"))
        assertEquals(listOf("5"), swim.map { it.id })
    }

    @Test fun `contadores coherentes con lo visible y con los demas filtros`() {
        val none = WardrobeLogic.categoryCounts(all, WardrobeFilters())
        assertEquals(5, none["all"])
        assertEquals(1, none["top"]) // el jersey de la lavadora no cuenta
        assertEquals(1, none["outerwear"])
        val winter = WardrobeLogic.categoryCounts(all, WardrobeFilters(season = "winter", category = "top"))
        // la categoria elegida no afecta a su propio contador (se puede cambiar de categoria sin perder referencia)
        assertEquals(2, winter["all"])
        assertEquals(null, winter["top"])
        assertEquals(1, winter["shoes"])
    }

    @Test fun `busqueda sin tildes ni mayusculas por nombre tipo color y marca`() {
        fun ids(q: String) = WardrobeLogic.closet(all, WardrobeFilters(query = q)).map { it.id }
        assertEquals(listOf("5"), ids("BANO"))
        assertEquals(listOf("1"), ids("zara"))
        assertEquals(listOf("3"), ids("negro"))
        assertEquals(listOf("4"), ids("abrigos")) // por etiqueta de categoria
        assertEquals(listOf("1", "2"), ids("  azul "))
        assertTrue(ids("zzz").isEmpty())
    }

    @Test fun `filtra por temporada y color ignorando mayusculas`() {
        assertEquals(listOf("3", "4"), WardrobeLogic.closet(all, WardrobeFilters(season = "winter")).map { it.id })
        assertEquals(listOf("1", "2"), WardrobeLogic.closet(all, WardrobeFilters(color = "azul")).map { it.id })
    }

    @Test fun `ordena por usos y mantiene el orden original en recientes`() {
        assertEquals(listOf("2", "1", "5", "3", "4"), WardrobeLogic.closet(all, WardrobeFilters(sort = SortOrder.MostUsed)).map { it.id })
        assertEquals(listOf("4", "3", "5", "1", "2"), WardrobeLogic.closet(all, WardrobeFilters(sort = SortOrder.LeastUsed)).map { it.id })
        assertEquals(listOf("1", "2", "3", "4", "5"), WardrobeLogic.closet(all, WardrobeFilters(sort = SortOrder.Recent)).map { it.id })
    }

    @Test fun `colores disponibles sin repetidos y normalizados`() {
        assertEquals(listOf("azul", "gris", "negro", "rojo"), WardrobeLogic.availableColors(all))
    }

    @Test fun `filtros se codifican y decodifican`() {
        val f = WardrobeFilters("a b", "top", "summer", "rojo", SortOrder.MostUsed)
        assertEquals(f, WardrobeFilters.decode(f.encode()))
        assertNull(WardrobeFilters.decode(null))
        assertNull(WardrobeFilters.decode("roto"))
        assertFalse(WardrobeFilters().advancedActive)
        assertTrue(f.advancedActive)
    }

    @Test fun `sorprendeme devuelve un look coherente`() {
        val wardrobe = listOf(
            garment("t", type = "top"), garment("b", type = "bottom"), garment("s", type = "shoes"),
            garment("d", type = "dress"), garment("o", type = "outerwear"),
        )
        repeat(30) { seed ->
            val picks = WardrobeLogic.surprise(wardrobe, Random(seed))
            val cats = picks.map { WardrobeCatalog.categoryOf(it.type) }
            assertTrue("calzado siempre", "shoes" in cats)
            assertTrue("vestido o top+bottom", "dress" in cats || ("top" in cats && "bottom" in cats))
            assertEquals("una prenda por categoria", cats.size, cats.toSet().size)
        }
        assertTrue(WardrobeLogic.surprise(emptyList()).isEmpty())
    }

    @Test fun `ultima vez puesta en lenguaje natural`() {
        val today = LocalDate.of(2026, 10, 13)
        val z = ZoneId.of("UTC")
        assertEquals("Hoy", WardrobeLogic.lastWornLabel("2026-10-13T08:00:00.000Z", today, z))
        assertEquals("Ayer", WardrobeLogic.lastWornLabel("2026-10-12T23:00:00.000Z", today, z))
        assertEquals("Hace 3 días", WardrobeLogic.lastWornLabel("2026-10-10T10:00:00.000Z", today, z))
        assertEquals("Hace 2 semanas", WardrobeLogic.lastWornLabel("2026-09-29T10:00:00.000Z", today, z))
        assertNull(WardrobeLogic.lastWornLabel(null, today, z))
        assertNull(WardrobeLogic.lastWornLabel("basura", today, z))
    }

    @Test fun `ventas y total`() {
        val list = listOf(garment("a", forSale = true, price = 10.5), garment("b"), garment("c", forSale = true, price = 20.0))
        val sales = WardrobeLogic.sales(list)
        assertEquals(2, sales.size)
        assertEquals(30.5, WardrobeLogic.salesTotal(sales), 0.001)
    }
}
