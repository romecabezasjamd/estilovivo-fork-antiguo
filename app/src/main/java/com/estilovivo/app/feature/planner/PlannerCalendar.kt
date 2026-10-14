package com.estilovivo.app.feature.planner

import com.estilovivo.app.core.data.model.PlannerEntry
import com.estilovivo.app.core.util.CYCLE_MESSAGES
import com.estilovivo.app.core.util.CyclePeriod
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Longitud máxima de la nota del evento (la valida el servidor: 300). */
const val MAX_NOTE_LENGTH = 300

/**
 * Lógica de calendario pura (sin Android, sin zona horaria): todo trabaja con [LocalDate], por lo que el
 * cambio de horario (DST) o la zona del dispositivo nunca desplazan un día. La fecha ISO `yyyy-MM-dd` es
 * siempre la local, nunca UTC.
 */
object PlannerCalendar {
    private val DAYS = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
    val SHORT_DAYS = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
    private val MONTHS = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
    )

    fun iso(date: LocalDate): String = date.toString()

    /** Acepta `yyyy-MM-dd` o un ISO con hora (`2026-10-13T00:00:00.000Z`: se toma sólo la parte de fecha). */
    fun parse(raw: String?): LocalDate? =
        raw?.trim()?.takeIf { it.length >= 10 }?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Lunes a domingo de la semana que contiene [date]. */
    fun weekDays(date: LocalDate): List<LocalDate> {
        val start = weekStart(date)
        return List(7) { start.plusDays(it.toLong()) }
    }

    /**
     * Cuadrícula del mes en filas completas de 7 (lunes primero): `null` = hueco antes del día 1 / después del último.
     */
    fun monthGrid(month: YearMonth): List<LocalDate?> {
        val lead = month.atDay(1).dayOfWeek.value - 1 // lunes = 0
        val cells = ArrayList<LocalDate?>()
        repeat(lead) { cells += null }
        for (d in 1..month.lengthOfMonth()) cells += month.atDay(d)
        while (cells.size % 7 != 0) cells += null
        return cells
    }

    fun dayName(date: LocalDate): String = DAYS[date.dayOfWeek.value - 1]
    fun monthName(month: Int): String = MONTHS[month - 1]

    /** "lunes 13 de octubre". */
    fun dayLabel(date: LocalDate): String = "${dayName(date)} ${date.dayOfMonth} de ${monthName(date.monthValue)}"

    /** "lunes 13 de octubre de 2026". */
    fun dayLabelFull(date: LocalDate): String = "${dayLabel(date)} de ${date.year}"

    /** "Octubre 2026". */
    fun monthLabel(month: YearMonth): String = "${monthName(month.monthValue).replaceFirstChar { it.uppercase() }} ${month.year}"

    /** "Octubre 2026" o "Sep – Oct 2026" cuando la semana cruza de mes (y con año en ambos si cruza de año). */
    fun weekLabel(start: LocalDate): String {
        val end = start.plusDays(6)
        return when {
            start.month == end.month && start.year == end.year -> monthLabel(YearMonth.from(start))
            start.year == end.year -> "${shortMonth(start)} – ${shortMonth(end)} ${end.year}"
            else -> "${shortMonth(start)} ${start.year} – ${shortMonth(end)} ${end.year}"
        }
    }

    private fun shortMonth(d: LocalDate) = monthName(d.monthValue).take(3).replaceFirstChar { it.uppercase() }

    /** "10 – 14 oct" / "28 sep – 2 oct". */
    fun rangeLabel(start: LocalDate, end: LocalDate): String {
        val s = shortMonth(start).lowercase()
        val e = shortMonth(end).lowercase()
        return when {
            start == end -> "${start.dayOfMonth} $s"
            start.month == end.month && start.year == end.year -> "${start.dayOfMonth} – ${end.dayOfMonth} $e"
            else -> "${start.dayOfMonth} $s – ${end.dayOfMonth} $e"
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Regla de "día vacío"
// ─────────────────────────────────────────────────────────────────────────────

/** Qué hay que hacer en el servidor tras un cambio en un día. */
sealed interface DayAction {
    /** Nada que hacer (día vacío que ya no existía). */
    data object Nothing : DayAction

    data class Save(val lookId: String?, val note: String) : DayAction

    /** El día se queda sin look ni nota: se elimina la entrada, nunca se guardan entradas vacías. */
    data object Delete : DayAction
}

fun resolveDayAction(existing: PlannerEntry?, lookId: String?, note: String): DayAction {
    val cleanNote = note.trim().take(MAX_NOTE_LENGTH)
    val cleanLook = lookId?.takeIf { it.isNotBlank() }
    if (cleanLook == null && cleanNote.isEmpty()) return if (existing != null) DayAction.Delete else DayAction.Nothing
    return DayAction.Save(cleanLook, cleanNote)
}

// ─────────────────────────────────────────────────────────────────────────────
// Ciclo
// ─────────────────────────────────────────────────────────────────────────────

private fun CyclePeriod.bounds(): Pair<LocalDate, LocalDate>? {
    val s = PlannerCalendar.parse(startDate) ?: return null
    val e = PlannerCalendar.parse(endDate) ?: return null
    return if (e.isBefore(s)) null else s to e
}

fun CyclePeriod?.contains(date: LocalDate): Boolean {
    val (s, e) = this?.bounds() ?: return false
    return !date.isBefore(s) && !date.isAfter(e)
}

fun CyclePeriod?.messageFor(date: LocalDate): String? {
    val (s, _) = this?.bounds() ?: return null
    if (!contains(date)) return null
    val day = ChronoUnit.DAYS.between(s, date).toInt().coerceAtLeast(0)
    return CYCLE_MESSAGES[day % CYCLE_MESSAGES.size]
}
