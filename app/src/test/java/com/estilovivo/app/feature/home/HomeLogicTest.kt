package com.estilovivo.app.feature.home

import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class LevelMathTest {
    @Test fun cumulativeMatchesServerFormula() {
        assertEquals(100, LevelMath.cumulativeXp(1))
        assertEquals(300, LevelMath.cumulativeXp(2))
        assertEquals(600, LevelMath.cumulativeXp(3))
    }

    @Test fun levelFromXpMatchesServer() {
        assertEquals(1, LevelMath.levelFromXp(0))
        assertEquals(1, LevelMath.levelFromXp(99))
        assertEquals(2, LevelMath.levelFromXp(100))
        assertEquals(2, LevelMath.levelFromXp(299))
        assertEquals(3, LevelMath.levelFromXp(300))
        assertEquals(1, LevelMath.levelFromXp(-50))
    }

    @Test fun progressInsideLevel() {
        val p = LevelMath.progress(250, 2)
        assertEquals(150, p.current)
        assertEquals(200, p.needed)
        assertEquals(75, p.percentage)
        assertEquals(50, p.toNext)
        assertEquals(0.75f, p.fraction, 0.0001f)
    }

    @Test fun progressAtLevelStartAndFirstLevel() {
        assertEquals(0, LevelMath.progress(100, 2).current)
        val first = LevelMath.progress(40, 1)
        assertEquals(40, first.current)
        assertEquals(100, first.needed)
    }

    @Test fun progressIsClamped() {
        val over = LevelMath.progress(10_000, 2)
        assertEquals(over.needed, over.current)
        assertEquals(100, over.percentage)
        assertEquals(0, LevelMath.progress(0, 3).current)
        assertEquals(1, LevelMath.progress(10, 0).level)
    }

    @Test fun titles() {
        assertEquals("Principiante", LevelMath.title(1))
        assertEquals("Principiante", LevelMath.title(2))
        assertEquals("Estilista", LevelMath.title(3))
        assertEquals("Fashionista", LevelMath.title(6))
        assertEquals("Icono", LevelMath.title(10))
        assertEquals("Leyenda", LevelMath.title(15))
    }
}

class WeekMathTest {
    @Test fun weekStartsOnMondayAndHasSevenDays() {
        val week = WeekMath.weekOf(LocalDate.of(2026, 10, 14)) // miércoles
        assertEquals(7, week.size)
        assertEquals("2026-10-12", week.first().iso)
        assertEquals(DayOfWeek.MONDAY, week.first().date.dayOfWeek)
        assertEquals("2026-10-18", week.last().iso)
        assertEquals(listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"), week.map { it.letter })
        assertEquals(listOf(false, false, true, false, false, false, false), week.map { it.isToday })
    }

    @Test fun sundayBelongsToTheWeekThatEndsOnIt() {
        val week = WeekMath.weekOf(LocalDate.of(2026, 10, 18))
        assertEquals("2026-10-12", week.first().iso)
        assertTrue(week.last().isToday)
    }

    @Test fun mondayIsFirstDay() {
        assertTrue(WeekMath.weekOf(LocalDate.of(2026, 10, 12)).first().isToday)
    }

    @Test fun localMidnightEdgeUsesLocalDateNotUtc() {
        // 22:30 UTC del 12 = 00:30 del 13 en Madrid (UTC+2 en octubre): hoy es el 13 y no el 12.
        val instant = Instant.parse("2026-10-12T22:30:00Z")
        val madrid = Clock.fixed(instant, ZoneId.of("Europe/Madrid"))
        val utc = Clock.fixed(instant, ZoneId.of("UTC"))
        assertEquals("2026-10-13", LocalDate.now(madrid).format(WeekMath.ISO))
        assertEquals("2026-10-12", LocalDate.now(utc).format(WeekMath.ISO))
        assertEquals("2026-10-13", WeekMath.weekOf(LocalDate.now(madrid)).single { it.isToday }.iso)
    }

    @Test fun weekAcrossDstChangeKeepsSevenDistinctDays() {
        val week = WeekMath.weekOf(LocalDate.of(2026, 10, 25)) // fin del horario de verano en la UE
        assertEquals(7, week.map { it.iso }.distinct().size)
        assertEquals("2026-10-19", week.first().iso)
        assertEquals("2026-10-25", week.last().iso)
    }

    @Test fun weekAcrossYearBoundary() {
        val week = WeekMath.weekOf(LocalDate.of(2027, 1, 1)) // viernes
        assertEquals("2026-12-28", week.first().iso)
        assertEquals("2027-01-03", week.last().iso)
    }

    @Test fun normalizeServerDates() {
        assertEquals("2026-10-13", WeekMath.normalizeDate("2026-10-13"))
        assertEquals("2026-10-13", WeekMath.normalizeDate("2026-10-13T00:00:00.000Z"))
    }

    @Test fun greetingByHour() {
        assertEquals("Buenos días", Greeting.forHour(8))
        assertEquals("Buenas tardes", Greeting.forHour(15))
        assertEquals("Buenas noches", Greeting.forHour(23))
        assertEquals("Buenas noches", Greeting.forHour(3))
    }
}
