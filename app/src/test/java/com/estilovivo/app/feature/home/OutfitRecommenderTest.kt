package com.estilovivo.app.feature.home

import com.estilovivo.app.core.data.model.Garment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

internal fun garment(
    id: String, type: String, color: String = "negro", season: String = "all",
    usage: Int = 0, name: String = id, washing: Boolean = false, forSale: Boolean = false, lastWorn: String? = null,
) = Garment(
    id = id, imageUrl = "https://img/$id.jpg", name = name, type = type, color = color, season = season,
    usageCount = usage, lastWorn = lastWorn, forSale = forSale, isWashing = washing,
)

class OutfitRecommenderTest {
    private val today = LocalDate.of(2026, 10, 13)

    private fun input(
        garments: List<Garment>, mood: String? = null, temp: Int? = 18, condition: WeatherCondition? = WeatherCondition.Sunny,
    ) = RecommenderInput(garments, mood, temp, condition, today)

    private val closet = listOf(
        garment("t1", "top", "blanco", usage = 8), garment("t2", "top", "azul", usage = 1),
        garment("t3", "top", "rojo", usage = 0),
        garment("b1", "bottom", "negro", usage = 5), garment("b2", "bottom", "vaquero", usage = 2),
        garment("s1", "shoes", "blanco", usage = 3), garment("s2", "shoes", "negro", usage = 1),
        garment("o1", "outerwear", "negro", usage = 1), garment("o2", "outerwear", "beige", usage = 4),
        garment("d1", "dress", "negro", usage = 0),
    )

    private fun ready(r: RecommendResult) = (r as RecommendResult.Ready).outfits

    @Test fun deterministicForSameSeed() {
        val a = ready(OutfitRecommender.recommend(input(closet, "casual"), Random(42)))
        val b = ready(OutfitRecommender.recommend(input(closet, "casual"), Random(42)))
        assertEquals(a.map { it.key }, b.map { it.key })
        assertEquals(a.map { it.score }, b.map { it.score })
    }

    @Test fun differentSeedsCanShuffleOrder() {
        val orders = (1..12).map { seed -> ready(OutfitRecommender.recommend(input(closet), Random(seed))).map { it.key } }
        assertTrue(orders.distinct().size > 1)
    }

    @Test fun lessThanTwoGarmentsIsNotEnough() {
        assertEquals(RecommendResult.NotEnoughGarments, OutfitRecommender.recommend(input(emptyList()), Random(1)))
        assertEquals(RecommendResult.NotEnoughGarments, OutfitRecommender.recommend(input(closet.take(1)), Random(1)))
    }

    @Test fun washingAndForSaleGarmentsAreExcluded() {
        val pool = listOf(
            garment("t", "top"), garment("b", "bottom", washing = true), garment("b2", "bottom", forSale = true),
        )
        assertEquals(RecommendResult.NotEnoughGarments, OutfitRecommender.recommend(input(pool), Random(1)))
        val withOk = pool + garment("b3", "bottom")
        val outfits = ready(OutfitRecommender.recommend(input(withOk), Random(1)))
        assertTrue(outfits.all { o -> o.garments.none { it.id == "b" || it.id == "b2" } })
    }

    @Test fun onlyTopsCannotFormAnOutfit() {
        val r = OutfitRecommender.recommend(input(listOf(garment("a", "top"), garment("b", "top"))), Random(1))
        assertTrue(r is RecommendResult.NoCombination)
    }

    @Test fun everyOutfitHasACoreAndNoDuplicatedSlots() {
        for (seed in 1..10) {
            ready(OutfitRecommender.recommend(input(closet), Random(seed))).forEach { o ->
                val slots = o.garments.map { OutfitRecommender.slotOf(it) }
                val hasFull = Slot.Full in slots
                assertTrue(hasFull || (Slot.Top in slots && Slot.Bottom in slots))
                assertEquals(slots.size, slots.distinct().size)
                if (hasFull) assertFalse(Slot.Top in slots || Slot.Bottom in slots)
            }
        }
    }

    @Test fun summerGarmentsAreDiscardedWhenCold() {
        val pool = listOf(
            garment("ts", "top", season = "summer"), garment("tw", "top", season = "winter"),
            garment("bs", "bottom", season = "summer"), garment("bw", "bottom", season = "winter"),
        )
        val outfits = ready(OutfitRecommender.recommend(input(pool, temp = 3), Random(1)))
        assertTrue(outfits.all { o -> o.garments.all { it.season != "summer" } })
        val hot = ready(OutfitRecommender.recommend(input(pool, temp = 32), Random(1)))
        assertTrue(hot.all { o -> o.garments.all { it.season != "winter" } })
    }

    @Test fun coldOrRainyWeatherAddsAnOuterLayerWhenAvailable() {
        val cold = ready(OutfitRecommender.recommend(input(closet, temp = 6), Random(3)))
        assertTrue(cold.all { o -> o.garments.any { OutfitRecommender.slotOf(it) == Slot.Outer } })
        val rain = ready(OutfitRecommender.recommend(input(closet, temp = 20, condition = WeatherCondition.Rainy), Random(3)))
        assertTrue(rain.all { o -> o.garments.any { OutfitRecommender.slotOf(it) == Slot.Outer } })
        val hot = ready(OutfitRecommender.recommend(input(closet, temp = 30), Random(3)))
        assertTrue(hot.none { o -> o.garments.any { OutfitRecommender.slotOf(it) == Slot.Outer } })
    }

    @Test fun clashingColorsAreAvoidedInTheTopProposal() {
        val pool = listOf(
            garment("red", "top", "rojo"), garment("blue", "bottom", "azul"),
            garment("white", "top", "blanco"), garment("black", "bottom", "negro"),
        )
        for (seed in 1..8) {
            val best = ready(OutfitRecommender.recommend(input(pool), Random(seed))).first()
            // rojo + azul choca; la propuesta principal nunca mezcla dos tonos que chocan
            val ids = best.garments.map { it.id }.toSet()
            assertNotEquals(setOf("red", "blue"), ids)
        }
        assertTrue(OutfitRecommender.harmony(pool[0], pool[1], null) < 0)
        assertTrue(OutfitRecommender.harmony(pool[0], pool[3], null) > 0)
    }

    @Test fun lessUsedGarmentsAreFavoured() {
        val pool = listOf(
            garment("old", "top", "blanco", usage = 30), garment("new", "top", "blanco", usage = 0),
            garment("b", "bottom", "negro", usage = 3),
        )
        val firsts = (1..10).map { ready(OutfitRecommender.recommend(input(pool), Random(it))).first().garments.first { g -> g.type == "top" }.id }
        assertTrue(firsts.count { it == "new" } > firsts.count { it == "old" })
    }

    @Test fun moodColorsInfluenceChoice() {
        val pool = listOf(
            garment("red", "top", "rojo"), garment("grey", "top", "gris"), garment("b", "bottom", "negro"),
        )
        val relaxed = (1..10).map { ready(OutfitRecommender.recommend(input(pool, "relaxed"), Random(it))).first().garments.first { g -> g.type == "top" }.id }
        assertTrue(relaxed.count { it == "grey" } >= 7)
    }

    @Test fun anotherProposalsAreDistinct() {
        val outfits = ready(OutfitRecommender.recommend(input(closet), Random(7)))
        assertTrue(outfits.size >= 3)
        assertEquals(outfits.size, outfits.map { it.key }.distinct().size)
    }

    @Test fun recentlyWornGarmentsArePenalised() {
        val pool = listOf(
            garment("worn", "top", "blanco", lastWorn = "2026-10-13T08:00:00.000Z"), garment("rested", "top", "blanco"),
            garment("b", "bottom", "negro"),
        )
        val firsts = (1..10).map { ready(OutfitRecommender.recommend(input(pool), Random(it))).first().garments.first { g -> g.type == "top" }.id }
        assertTrue(firsts.count { it == "rested" } >= 7)
    }

    @Test fun classifiesLegacyAndFreeFormTypes() {
        assertEquals(Slot.Outer, OutfitRecommender.slotOf(garment("x", "outer")))
        assertEquals(Slot.Accessory, OutfitRecommender.slotOf(garment("x", "acc")))
        assertEquals(Slot.Bottom, OutfitRecommender.slotOf(garment("x", "otro", name = "Pantalón chino")))
        assertEquals(Slot.Full, OutfitRecommender.slotOf(garment("x", "set")))
        assertEquals(null, OutfitRecommender.slotOf(garment("x", "swimwear")))
    }

    @Test fun moodPicksReturnSeasonalGarments() {
        val pool = listOf(
            garment("a", "top", season = "summer"), garment("b", "top", season = "winter"), garment("c", "shoes", season = "all"),
        )
        val picks = OutfitRecommender.moodPicks(input(pool, "casual", temp = 4))
        assertTrue(picks.none { it.id == "a" })
        assertTrue(picks.isNotEmpty() && picks.size <= 6)
    }
}
