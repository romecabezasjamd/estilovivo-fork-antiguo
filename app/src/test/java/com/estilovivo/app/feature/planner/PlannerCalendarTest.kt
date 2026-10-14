package com.estilovivo.app.feature.planner

import com.estilovivo.app.core.data.model.Garment
import com.estilovivo.app.core.data.model.Look
import com.estilovivo.app.core.data.model.PlannerEntry
import com.estilovivo.app.core.util.CYCLE_MESSAGES
import com.estilovivo.app.core.util.CyclePeriod
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.TimeZone

class PlannerCalendarTest {
    private lateinit var previousZone: TimeZone

    @Before fun setZone() {
        previousZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Madrid"))
    }

    @After fun restoreZone() = TimeZone.setDefault(previousZone)

    private fun d(s: String) = LocalDate.parse(s)

    // ── Semana ───────────────────────────────────────────────────────────────

    @Test fun `la semana empieza en lunes y acaba en domingo desde cualquier dia`() {
        listOf("2026-10-12", "2026-10-13", "2026-10-17", "2026-10-18").forEach { raw ->
            val week = PlannerCalendar.weekDays(d(raw))
            assertEquals(d("2026-10-12"), week.first())
            assertEquals(DayOfWeek.MONDAY, week.first().dayOfWeek)
            assertEquals(d("2026-10-18"), week.last())
            assertEquals(DayOfWeek.SUNDAY, week.last().dayOfWeek)
            assertEquals(7, week.size)
        }
    }

    @Test fun `el domingo pertenece a la semana que acaba en el y no a la siguiente`() {
        assertEquals(d("2026-10-12"), PlannerCalendar.weekStart(d("2026-10-18")))
        assertEquals(d("2026-10-19"), PlannerCalendar.weekStart(d("2026-10-19")))
    }

    @Test fun `el cambio de horario de Europa Madrid no duplica ni salta dias`() {
        // Fin del horario de verano: 25-oct-2026 (día de 25 h). Inicio: 29-mar-2026 (día de 23 h).
        listOf("2026-10-25", "2026-03-29").forEach { raw ->
            val week = PlannerCalendar.weekDays(d(raw))
            val isos = week.map { PlannerCalendar.iso(it) }
            assertEquals(7, isos.toSet().size)
            week.zipWithNext().forEach { (a, b) -> assertEquals(a.plusDays(1), b) }
            assertTrue(raw in isos)
        }
        var cursor = d("2026-10-19")
        repeat(3) { cursor = cursor.plusWeeks(1); assertEquals(DayOfWeek.MONDAY, PlannerCalendar.weekStart(cursor).dayOfWeek) }
    }

    @Test fun `la fecha ISO es siempre la local sin pasar por UTC`() {
        // A las 00:30 locales de verano (UTC+2) el día en UTC sería el anterior: LocalDate no se ve afectada.
        val midnight = java.time.ZonedDateTime.of(2026, 10, 13, 0, 30, 0, 0, java.time.ZoneId.of("Europe/Madrid"))
        assertEquals("2026-10-13", PlannerCalendar.iso(midnight.toLocalDate()))
        assertEquals("2026-10-12", midnight.toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDate().toString())
    }

    @Test fun `parse acepta fecha simple e ISO con hora y rechaza basura`() {
        assertEquals(d("2026-10-13"), PlannerCalendar.parse("2026-10-13"))
        assertEquals(d("2026-10-13"), PlannerCalendar.parse("2026-10-13T00:00:00.000Z"))
        assertNull(PlannerCalendar.parse("13/10/2026"))
        assertNull(PlannerCalendar.parse("2026-13-45"))
        assertNull(PlannerCalendar.parse(null))
        assertNull(PlannerCalendar.parse(""))
    }

    // ── Mes ──────────────────────────────────────────────────────────────────

    @Test fun `febrero bisiesto tiene 29 dias y febrero normal 28`() {
        assertEquals(29, PlannerCalendar.monthGrid(YearMonth.of(2028, 2)).filterNotNull().size)
        assertEquals(28, PlannerCalendar.monthGrid(YearMonth.of(2027, 2)).filterNotNull().size)
    }

    @Test fun `mes que empieza en lunes no tiene huecos previos`() {
        val grid = PlannerCalendar.monthGrid(YearMonth.of(2027, 2)) // 1-feb-2027 es lunes
        assertEquals(d("2027-02-01"), grid.first())
        assertEquals(28, grid.size)
    }

    @Test fun `mes que empieza en domingo deja seis huecos`() {
        val grid = PlannerCalendar.monthGrid(YearMonth.of(2026, 2)) // 1-feb-2026 es domingo
        assertEquals(6, grid.takeWhile { it == null }.size)
        assertEquals(d("2026-02-01"), grid[6])
    }

    @Test fun `la cuadricula siempre son filas completas con los dias en orden`() {
        for (m in 1..12) {
            val ym = YearMonth.of(2026, m)
            val grid = PlannerCalendar.monthGrid(ym)
            assertEquals(0, grid.size % 7)
            val days = grid.filterNotNull()
            assertEquals(ym.lengthOfMonth(), days.size)
            days.zipWithNext().forEach { (a, b) -> assertEquals(a.plusDays(1), b) }
            grid.chunked(7).forEach { row -> row[0]?.let { assertEquals(DayOfWeek.MONDAY, it.dayOfWeek) } }
        }
    }

    @Test fun `navegar por meses desde el dia 31 recorta al ultimo dia valido`() {
        assertEquals(d("2026-02-28"), d("2026-01-31").plusMonths(1))
        assertEquals(d("2028-02-29"), d("2028-01-31").plusMonths(1))
    }

    // ── Etiquetas ────────────────────────────────────────────────────────────

    @Test fun `etiquetas en español`() {
        assertEquals("lunes 12 de octubre", PlannerCalendar.dayLabel(d("2026-10-12")))
        assertEquals("martes 13 de octubre de 2026", PlannerCalendar.dayLabelFull(d("2026-10-13")))
        assertEquals("Octubre 2026", PlannerCalendar.monthLabel(YearMonth.of(2026, 10)))
        assertEquals("Octubre 2026", PlannerCalendar.weekLabel(d("2026-10-12")))
        assertEquals("Sep – Oct 2026", PlannerCalendar.weekLabel(d("2026-09-28")))
        assertEquals("Dic 2026 – Ene 2027", PlannerCalendar.weekLabel(d("2026-12-28")))
        assertEquals("10 – 14 oct", PlannerCalendar.rangeLabel(d("2026-10-10"), d("2026-10-14")))
        assertEquals("28 sep – 2 oct", PlannerCalendar.rangeLabel(d("2026-09-28"), d("2026-10-02")))
    }

    // ── Día vacío ────────────────────────────────────────────────────────────

    private val someEntry = PlannerEntry("2026-10-13", "look1", null, "nota")

    @Test fun `sin look ni nota el dia existente se elimina`() {
        assertEquals(DayAction.Delete, resolveDayAction(someEntry, null, ""))
        assertEquals(DayAction.Delete, resolveDayAction(someEntry, null, "   "))
        assertEquals(DayAction.Delete, resolveDayAction(someEntry, "", ""))
    }

    @Test fun `sin look ni nota y sin entrada previa no se hace nada`() {
        assertEquals(DayAction.Nothing, resolveDayAction(null, null, ""))
    }

    @Test fun `con look o con nota se guarda recortando espacios y longitud`() {
        assertEquals(DayAction.Save("l1", ""), resolveDayAction(null, "l1", ""))
        assertEquals(DayAction.Save(null, "cena"), resolveDayAction(null, null, "  cena  "))
        val long = "x".repeat(500)
        assertEquals(MAX_NOTE_LENGTH, (resolveDayAction(null, null, long) as DayAction.Save).note.length)
    }

    // ── Ciclo ────────────────────────────────────────────────────────────────

    private val period = CyclePeriod("2026-10-10", "2026-10-14")

    @Test fun `pertenencia al ciclo incluye inicio y fin`() {
        assertFalse(period.contains(d("2026-10-09")))
        assertTrue(period.contains(d("2026-10-10")))
        assertTrue(period.contains(d("2026-10-12")))
        assertTrue(period.contains(d("2026-10-14")))
        assertFalse(period.contains(d("2026-10-15")))
        assertFalse((null as CyclePeriod?).contains(d("2026-10-12")))
    }

    @Test fun `periodo corrupto o invertido no marca ningun dia`() {
        assertFalse(CyclePeriod("2026-10-14", "2026-10-10").contains(d("2026-10-12")))
        assertFalse(CyclePeriod("basura", "2026-10-10").contains(d("2026-10-10")))
        assertNull(CyclePeriod("basura", "x").messageFor(d("2026-10-10")))
    }

    @Test fun `el mensaje rota por dia dentro del periodo y es nulo fuera`() {
        assertEquals(CYCLE_MESSAGES[0], period.messageFor(d("2026-10-10")))
        assertEquals(CYCLE_MESSAGES[2], period.messageFor(d("2026-10-12")))
        assertNull(period.messageFor(d("2026-10-20")))
        val long = CyclePeriod("2026-10-01", "2026-10-31")
        assertEquals(CYCLE_MESSAGES[0], long.messageFor(d("2026-10-07")))
    }

    @Test fun `el selector de Material convierte fechas en UTC sin desplazar el dia`() {
        listOf("2026-10-13", "2026-03-29", "2026-10-25", "2028-02-29").forEach { raw ->
            assertEquals(d(raw), d(raw).toPickerMillis().fromPickerMillis())
        }
    }

    // ── Buscador de looks ────────────────────────────────────────────────────

    private fun look(id: String, name: String, tags: List<String> = emptyList(), garment: String? = null) = Look(
        id = id, name = name, garmentIds = emptyList(), tags = tags,
        garments = listOfNotNull(garment?.let { Garment("g$id", "https://x/$id.jpg", it, "top", "negro", "all", 0) }),
    )

    @Test fun `el buscador mira nombre etiquetas y prendas`() {
        val looks = listOf(look("1", "Oficina", listOf("formal")), look("2", "Playa", garment = "Bikini rojo"), look("3", "Cena"))
        assertEquals(listOf("1"), filterLooks(looks, " ofi ").map { it.id })
        assertEquals(listOf("1"), filterLooks(looks, "FORMAL").map { it.id })
        assertEquals(listOf("2"), filterLooks(looks, "bikini").map { it.id })
        assertEquals(3, filterLooks(looks, "").size)
        assertTrue(filterLooks(looks, "zzz").isEmpty())
    }
}
