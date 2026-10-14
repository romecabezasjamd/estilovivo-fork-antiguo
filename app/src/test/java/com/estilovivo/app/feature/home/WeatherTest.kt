package com.estilovivo.app.feature.home

import kotlinx.coroutines.test.runTest
import java.io.IOException
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class WeatherLogicTest {
    @Test fun tempRangesMatchWebBoundaries() {
        assertEquals(TempRange.VeryHot, WeatherLogic.tempRange(30))
        assertEquals(TempRange.Hot, WeatherLogic.tempRange(29))
        assertEquals(TempRange.Warm, WeatherLogic.tempRange(20))
        assertEquals(TempRange.Mild, WeatherLogic.tempRange(15))
        assertEquals(TempRange.Cool, WeatherLogic.tempRange(10))
        assertEquals(TempRange.Cold, WeatherLogic.tempRange(0))
        assertEquals(TempRange.Freezing, WeatherLogic.tempRange(-1))
    }

    @Test fun conditionFromWmoCode() {
        assertEquals(WeatherCondition.Sunny, WeatherLogic.condition(0, 20.0))
        assertEquals(WeatherCondition.Hot, WeatherLogic.condition(0, 31.0))
        assertEquals(WeatherCondition.Cold, WeatherLogic.condition(0, 2.0))
        assertEquals(WeatherCondition.Cloudy, WeatherLogic.condition(3, 20.0))
        assertEquals(WeatherCondition.Cloudy, WeatherLogic.condition(45, 12.0)) // niebla: la web decía "despejado"
        assertEquals(WeatherCondition.Rainy, WeatherLogic.condition(61, 12.0))
        assertEquals(WeatherCondition.Rainy, WeatherLogic.condition(95, 25.0))
        assertEquals(WeatherCondition.Snowy, WeatherLogic.condition(73, -1.0))
        assertEquals(WeatherCondition.Windy, WeatherLogic.condition(1, 18.0, windKmh = 55.0))
        assertEquals(WeatherCondition.Rainy, WeatherLogic.condition(63, 18.0, windKmh = 55.0))
    }

    @Test fun conditionTipsAreNotDuplicatedByCategory() {
        val tips = WeatherLogic.tips(18, WeatherCondition.Rainy)
        assertEquals(tips.map { it.category }.distinct(), tips.map { it.category })
        assertTrue(WeatherLogic.essentialTips(5, WeatherCondition.Sunny).size <= 3)
        assertTrue(WeatherLogic.essentialTips(5, WeatherCondition.Sunny).all { it.essential })
    }

    @Test fun seasonFromTemp() {
        assertEquals("summer", WeatherLogic.seasonForTemp(25))
        assertEquals("transition", WeatherLogic.seasonForTemp(15))
        assertEquals("winter", WeatherLogic.seasonForTemp(14))
    }
}

private class FakeWeatherApi : WeatherApi {
    var forecastCalls = 0
    var geocodeCalls = 0
    var failForecast = false
    var temp = 21.4
    var place: GeoPlace? = GeoPlace("Sevilla", Coordinates(37.38, -5.99))
    override suspend fun geocode(name: String): GeoPlace? { geocodeCalls++; return place }
    override suspend fun forecast(coords: Coordinates): RawWeather {
        forecastCalls++
        if (failForecast) throw IOException("sin red")
        return RawWeather(temp, 40, 0, 5.0)
    }
}

class WeatherRepositoryTest {
    private var now = 1_000_000L
    private val api = FakeWeatherApi()
    private val repo = WeatherRepository(api, nowMs = { now })

    @Test fun cachesForThirtyMinutes() = runTest {
        assertEquals(21, repo.load(WeatherLocation.Default)?.temp)
        now += 29 * 60_000L
        api.temp = 30.0
        assertEquals(21, repo.load(WeatherLocation.Default)?.temp)
        assertEquals(1, api.forecastCalls)
        now += 2 * 60_000L // 31 min en total
        assertEquals(30, repo.load(WeatherLocation.Default)?.temp)
        assertEquals(2, api.forecastCalls)
    }

    @Test fun forceBypassesCache() = runTest {
        repo.load(WeatherLocation.Default)
        repo.load(WeatherLocation.Default, force = true)
        assertEquals(2, api.forecastCalls)
    }

    @Test fun differentLocationsHaveSeparateCaches() = runTest {
        repo.load(WeatherLocation.Default)
        repo.load(WeatherLocation.City("Sevilla"))
        assertEquals(2, api.forecastCalls)
        repo.load(WeatherLocation.City("sevilla "))
        assertEquals(2, api.forecastCalls)
        assertEquals(1, api.geocodeCalls)
    }

    @Test fun failureReturnsStaleOrNull() = runTest {
        api.failForecast = true
        assertNull(repo.load(WeatherLocation.Default))
        api.failForecast = false
        assertNotNull(repo.load(WeatherLocation.Default))
        api.failForecast = true
        now += 60 * 60_000L
        val stale = repo.load(WeatherLocation.Default)
        assertNotNull(stale)
        assertTrue(stale!!.stale)
        assertEquals(21, stale!!.temp)
    }

    @Test fun unknownCityFallsBackToMadrid() = runTest {
        api.place = null
        assertEquals("Madrid", repo.load(WeatherLocation.City("Zzzzland"))?.place)
    }

    @Test fun deviceLocationLabel() = runTest {
        val data = repo.load(WeatherLocation.Device(Coordinates(40.0, -3.0)))
        assertEquals("Tu ubicación", data?.place)
        assertFalse(data!!.stale)
    }
}
