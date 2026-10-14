package com.estilovivo.app.feature.suitcase

import androidx.compose.runtime.Immutable
import com.estilovivo.app.core.data.model.Trip
import com.estilovivo.app.core.data.model.TripItem
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** Fechas de viaje: se guardan como texto ISO `YYYY-MM-DD` (los viajes antiguos pueden traer texto ya formateado). */
object TripDates {
    private val ISO = Regex("""^\d{4}-\d{2}-\d{2}$""")
    private val MONTHS = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sept", "oct", "nov", "dic")

    /** `null` si el texto no es una fecha ISO válida (incluye "13 oct" de los viajes antiguos y fechas imposibles como 2026-02-30). */
    fun parse(value: String?): LocalDate? {
        val v = value?.trim().orEmpty()
        if (!ISO.matches(v)) return null
        return runCatching { LocalDate.parse(v) }.getOrNull()
    }

    fun iso(date: LocalDate): String = date.toString()

    /** "13 oct" (con año si no es el actual). Si no parsea, devuelve el texto tal cual. */
    fun format(value: String, today: LocalDate, withYear: Boolean? = null): String {
        val d = parse(value) ?: return value
        val showYear = withYear ?: (d.year != today.year)
        val base = "${d.dayOfMonth} ${MONTHS[d.monthValue - 1]}"
        return if (showYear) "$base ${d.year}" else base
    }

    /** "13 oct – 15 oct", "13 oct" si es un solo día. Años sólo si alguna fecha no es del año actual. */
    fun range(start: String, end: String, today: LocalDate): String {
        val s = parse(start)
        val e = parse(end)
        if (s == null || e == null) {
            return listOf(start, end).filter { it.isNotBlank() }.distinct().joinToString(" – ")
        }
        val year = s.year != today.year || e.year != today.year
        val a = format(start, today, year)
        val b = format(end, today, year)
        return if (s == e) a else "$a – $b"
    }

    /** Número de días del viaje (ida y vuelta incluidos), `null` si las fechas no parsean. */
    fun length(start: String, end: String): Int? {
        val s = parse(start) ?: return null
        val e = parse(end) ?: s
        return (ChronoUnit.DAYS.between(s, maxOf(s, e)) + 1).toInt()
    }
}

/** En qué momento está un viaje respecto a hoy (siempre con la fecha LOCAL del dispositivo). */
@Immutable
sealed interface TripPhase {
    data class Upcoming(val days: Int) : TripPhase
    data class Ongoing(val day: Int, val total: Int) : TripPhase
    data class Finished(val daysAgo: Int) : TripPhase
}

object TripTiming {
    /** `null` si las fechas no parsean (viajes antiguos): no se muestra cuenta atrás. Una vuelta anterior a la ida se trata como viaje de un día. */
    fun phase(dateStart: String, dateEnd: String, today: LocalDate): TripPhase? {
        val start = TripDates.parse(dateStart) ?: return null
        val end = TripDates.parse(dateEnd)?.let { maxOf(it, start) } ?: start
        return when {
            today.isBefore(start) -> TripPhase.Upcoming(ChronoUnit.DAYS.between(today, start).toInt())
            today.isAfter(end) -> TripPhase.Finished(ChronoUnit.DAYS.between(end, today).toInt())
            else -> TripPhase.Ongoing(ChronoUnit.DAYS.between(start, today).toInt() + 1, ChronoUnit.DAYS.between(start, end).toInt() + 1)
        }
    }

    fun label(phase: TripPhase?): String? = when (phase) {
        null -> null
        is TripPhase.Upcoming -> if (phase.days == 1) "Mañana" else "En ${phase.days} días"
        is TripPhase.Ongoing -> "En curso"
        is TripPhase.Finished -> "Terminado"
    }

    /** Orden de la lista: en curso, próximos (el más cercano primero), terminados (el más reciente primero) y los de fecha ilegible al final. */
    fun sort(trips: List<Trip>, today: LocalDate): List<Trip> {
        fun key(t: Trip): Pair<Int, Long> {
            val start = TripDates.parse(t.dateStart)
            val end = TripDates.parse(t.dateEnd)?.let { e -> start?.let { maxOf(it, e) } ?: e } ?: start
            return when (phase(t.dateStart, t.dateEnd, today)) {
                is TripPhase.Ongoing -> 0 to (end?.toEpochDay() ?: 0L)
                is TripPhase.Upcoming -> 1 to (start?.toEpochDay() ?: 0L)
                is TripPhase.Finished -> 2 to -(end?.toEpochDay() ?: 0L)
                null -> 3 to 0L
            }
        }
        return trips.sortedWith(compareBy({ key(it).first }, { key(it).second }))
    }
}

object Checklist {
    fun checked(items: List<TripItem>) = items.count { it.checked }

    /** Porcentaje 0..100 (lista vacía = 0, nunca NaN). */
    fun percent(items: List<TripItem>): Int =
        if (items.isEmpty()) 0 else (checked(items) * 100.0 / items.size).roundToInt()

    fun fraction(items: List<TripItem>): Float = if (items.isEmpty()) 0f else checked(items).toFloat() / items.size

    const val MAX_LABEL = 120
    const val MAX_ITEMS = 200

    fun cleanLabel(raw: String): String = raw.trim().replace(Regex("\\s+"), " ").take(MAX_LABEL).trim()

    fun sameLabel(a: String, b: String) = a.trim().equals(b.trim(), ignoreCase = true)

    /** Mensaje de error al añadir [raw] a una lista que ya tiene [existing], o `null` si es válido. */
    fun validateNew(raw: String, existing: List<String>): String? {
        val label = cleanLabel(raw)
        return when {
            label.isEmpty() -> "Escribe qué quieres llevar."
            existing.size >= MAX_ITEMS -> "La lista ya tiene el máximo de $MAX_ITEMS elementos."
            existing.any { sameLabel(it, label) } -> "Ya tienes «$label» en la lista."
            else -> null
        }
    }
}

/** Elemento de la lista inicial al crear un viaje. */
@Serializable
@Immutable
data class FormItem(val label: String, val isEssential: Boolean = false)

/** Formulario de viaje nuevo. Serializable para sobrevivir a la muerte del proceso. */
@Serializable
@Immutable
data class TripForm(
    val destination: String = "",
    /** ISO o vacío. */
    val dateStart: String = "",
    val dateEnd: String = "",
    val garmentIds: List<String> = emptyList(),
    val items: List<FormItem> = TripFormRules.suggested,
) {
    /** ¿Hay algo escrito/elegido que merezca la pena guardar como borrador? */
    val isDirty: Boolean
        get() = destination.isNotBlank() || dateStart.isNotBlank() || garmentIds.isNotEmpty() || items != TripFormRules.suggested
}

@Immutable
data class TripFormErrors(val destination: String? = null, val dates: String? = null, val items: String? = null) {
    val any get() = destination != null || dates != null || items != null
}

object TripFormRules {
    const val MAX_DESTINATION = 100

    /** Lista inicial sugerida (la web sólo añade las dos primeras). */
    val suggested: List<FormItem> = listOf(
        FormItem("Documentación", true),
        FormItem("Neceser básico", true),
        FormItem("Cargador y móvil", true),
        FormItem("Medicamentos", true),
        FormItem("Ropa interior y calcetines"),
        FormItem("Calzado cómodo"),
        FormItem("Gafas de sol"),
    )

    fun validate(form: TripForm): TripFormErrors {
        val dest = form.destination.trim()
        val destError = when {
            dest.isEmpty() -> "Cuéntanos a dónde vas."
            dest.length > MAX_DESTINATION -> "El destino es demasiado largo (máx. $MAX_DESTINATION letras)."
            else -> null
        }
        val start = TripDates.parse(form.dateStart)
        val end = TripDates.parse(form.dateEnd)
        val datesError = when {
            form.dateStart.isBlank() -> "Elige las fechas del viaje."
            start == null -> "La fecha de ida no es válida."
            form.dateEnd.isNotBlank() && end == null -> "La fecha de vuelta no es válida."
            end != null && end.isBefore(start) -> "La vuelta no puede ser anterior a la ida."
            else -> null
        }
        val itemsError = if (form.items.size > Checklist.MAX_ITEMS) "La lista admite como máximo ${Checklist.MAX_ITEMS} elementos." else null
        return TripFormErrors(destError, datesError, itemsError)
    }

    /** Fin = inicio si sólo se eligió un día (igual que la web). */
    fun normalizedEnd(form: TripForm): String = form.dateEnd.ifBlank { form.dateStart }

    fun addItem(form: TripForm, raw: String): Pair<TripForm, String?> {
        val error = Checklist.validateNew(raw, form.items.map { it.label })
        if (error != null) return form to error
        return form.copy(items = form.items + FormItem(Checklist.cleanLabel(raw))) to null
    }
}
