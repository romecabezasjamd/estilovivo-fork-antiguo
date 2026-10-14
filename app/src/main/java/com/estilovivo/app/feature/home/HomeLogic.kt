package com.estilovivo.app.feature.home

import androidx.compose.runtime.Immutable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/** Estado de ánimo seleccionable. [id] es el valor que guarda el servidor (igual que la web). */
@Immutable
data class Mood(val id: String, val emoji: String, val labelFeminine: String, val labelMasculine: String) {
    fun label(gender: String?): String = if (gender == "male") labelMasculine else labelFeminine
}

object Moods {
    val all: List<Mood> = listOf(
        Mood("confident", "🦁", "Segura", "Seguro"),
        Mood("Sport", "🏃", "Sport", "Sport"),
        Mood("creative", "🎨", "Creativa", "Creativo"),
        Mood("relaxed", "🧘", "Relajada", "Relajado"),
        Mood("powerful", "⚡", "Poderosa", "Poderoso"),
        Mood("elegant", "✨", "Elegante", "Elegante"),
        Mood("casual", "🌿", "Casual", "Casual"),
    )

    fun byId(id: String?): Mood? = all.firstOrNull { it.id == id }
}

/** Fórmula de nivel/XP del servidor (`gamificationSystem.ts`): XP acumulado para completar el nivel n = 100·n·(n+1)/2. */
object LevelMath {
    fun cumulativeXp(level: Int): Int = 100 * level * (level + 1) / 2

    /** Nivel que corresponde a [xp] (misma función que `levelFromXp` del servidor). */
    fun levelFromXp(xp: Int): Int {
        val safe = xp.coerceAtLeast(0)
        return (((Math.sqrt(1.0 + 8.0 * safe / 100.0) - 1.0) / 2.0).toInt() + 1).coerceAtLeast(1)
    }

    @Immutable
    data class Progress(val level: Int, val current: Int, val needed: Int, val percentage: Int, val toNext: Int) {
        val fraction: Float get() = (current.toFloat() / needed.coerceAtLeast(1)).coerceIn(0f, 1f)
    }

    /** Progreso dentro del nivel [level] con [xp] acumulado. Acotado: `0 <= current <= needed`. */
    fun progress(xp: Int, level: Int): Progress {
        val lvl = level.coerceAtLeast(1)
        val start = if (lvl <= 1) 0 else cumulativeXp(lvl - 1)
        val end = cumulativeXp(lvl)
        val needed = (end - start).coerceAtLeast(1)
        val current = (xp - start).coerceIn(0, needed)
        val pct = Math.round(current * 100f / needed)
        return Progress(lvl, current, needed, pct, needed - current)
    }

    fun title(level: Int): String = when {
        level < 3 -> "Principiante"
        level < 6 -> "Estilista"
        level < 10 -> "Fashionista"
        level < 15 -> "Icono"
        else -> "Leyenda"
    }
}

/** Un día de la tira semanal. [iso] es la fecha local `yyyy-MM-dd` (la que usa el planificador del servidor). */
@Immutable
data class WeekDayInfo(val date: LocalDate, val iso: String, val letter: String, val dayOfMonth: Int, val isToday: Boolean)

object WeekMath {
    private val letters = mapOf(
        DayOfWeek.MONDAY to "Lun", DayOfWeek.TUESDAY to "Mar", DayOfWeek.WEDNESDAY to "Mié",
        DayOfWeek.THURSDAY to "Jue", DayOfWeek.FRIDAY to "Vie", DayOfWeek.SATURDAY to "Sáb", DayOfWeek.SUNDAY to "Dom",
    )

    /** Lunes a domingo de la semana que contiene [today]. Todo con `LocalDate` local: nunca se pasa por UTC. */
    fun weekOf(today: LocalDate): List<WeekDayInfo> {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return (0..6).map { i ->
            val d = monday.plusDays(i.toLong())
            WeekDayInfo(d, d.format(ISO), letters.getValue(d.dayOfWeek), d.dayOfMonth, d == today)
        }
    }

    val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /** Normaliza lo que devuelve el servidor (`2026-10-13` o `2026-10-13T00:00:00.000Z`) a `yyyy-MM-dd`. */
    fun normalizeDate(raw: String): String = raw.trim().take(10)
}

object Greeting {
    fun forHour(hour: Int): String = when (hour) {
        in 6..11 -> "Buenos días"
        in 12..20 -> "Buenas tardes"
        else -> "Buenas noches"
    }
}
