package com.estilovivo.app.feature.notifications

import androidx.compose.runtime.Immutable
import com.estilovivo.app.app.MainTab
import com.estilovivo.app.core.data.model.AppNotification
import com.estilovivo.app.core.data.model.NotificationTypes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/*
 * Lógica pura (sin Android) del módulo de notificaciones: destinos, agrupación por día, contador y política de avisos.
 * Todo está cubierto por tests JVM.
 */

/** A dónde lleva tocar una notificación. */
sealed interface NotificationDestination {
    data class Chat(val conversationId: String) : NotificationDestination
    data class UserProfile(val userId: String) : NotificationDestination
    data class Tab(val tab: MainTab) : NotificationDestination
    data object FollowRequests : NotificationDestination
    data object Washing : NotificationDestination

    /** Tipo sin pantalla asociada: se marca como leída y no se navega. */
    data object None : NotificationDestination
}

/** Un identificador remoto es válido si es corto y sólo contiene caracteres seguros (cuid/uuid). */
internal fun isSafeId(id: String?): Boolean =
    !id.isNullOrBlank() && id.length <= 64 && id.all { it.isLetterOrDigit() || it == '-' || it == '_' }

/** Texto con el que el servidor avisa de una solicitud de seguimiento pendiente (no hay un campo propio para ello). */
private const val FOLLOW_REQUEST_MARK = "quiere seguirte"

/**
 * Mapeo tipo → destino. Nunca lanza: tipos desconocidos o datos inválidos acaban en una opción segura.
 * - CHAT / STORY_REACTION → conversación (si el id no es válido, pestaña Social)
 * - LIKE / COMMENT → pestaña Social
 * - FOLLOW → solicitudes si "quiere seguirte"; perfil de la persona si hay id; si no, Perfil
 * - WASHING → Lavadora
 */
fun destinationFor(type: String, content: String, relatedId: String?): NotificationDestination = when (type) {
    NotificationTypes.CHAT, NotificationTypes.STORY_REACTION ->
        if (isSafeId(relatedId)) NotificationDestination.Chat(relatedId!!) else NotificationDestination.Tab(MainTab.Social)
    NotificationTypes.LIKE, NotificationTypes.COMMENT -> NotificationDestination.Tab(MainTab.Social)
    NotificationTypes.FOLLOW -> when {
        content.contains(FOLLOW_REQUEST_MARK, ignoreCase = true) -> NotificationDestination.FollowRequests
        isSafeId(relatedId) -> NotificationDestination.UserProfile(relatedId!!)
        else -> NotificationDestination.Tab(MainTab.Profile)
    }
    NotificationTypes.WASHING -> NotificationDestination.Washing
    else -> NotificationDestination.None
}

fun AppNotification.destination(): NotificationDestination = destinationFor(type, content, relatedId)

/** Texto del badge de la campana: nada si 0, el número hasta 9 y "9+" a partir de 10. */
fun badgeText(count: Int): String? = when {
    count <= 0 -> null
    count > 9 -> "9+"
    else -> count.toString()
}

// ─────────────── Agrupación por día ───────────────

@Immutable
data class DaySection(val date: LocalDate, val label: String, val items: List<AppNotification>)

private val ES = Locale.forLanguageTag("es-ES")
private val DAY_FORMAT = DateTimeFormatter.ofPattern("d 'de' MMMM", ES)
private val DAY_YEAR_FORMAT = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", ES)
private val CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm", ES)

/** Fecha ISO del servidor → instante; `null` si no se puede leer (no debe romper la lista). */
fun parseInstant(raw: String): Instant? = runCatching { Instant.parse(raw) }.getOrNull()

/** "Hoy", "Ayer", "12 de octubre" (con año si no es el actual). */
fun dayLabel(date: LocalDate, today: LocalDate): String = when {
    date >= today -> "Hoy" // también absorbe pequeñas desviaciones de reloj hacia el futuro
    date == today.minusDays(1) -> "Ayer"
    date.year == today.year -> date.format(DAY_FORMAT)
    else -> date.format(DAY_YEAR_FORMAT)
}

/**
 * Agrupa por día natural en la zona dada (de más reciente a más antiguo). Las fechas ilegibles van al final
 * en una sección "Anteriores". Mantiene el orden del servidor dentro de cada día (ordenado por fecha desc).
 */
fun groupByDay(list: List<AppNotification>, now: Instant, zone: ZoneId): List<DaySection> {
    val today = now.atZone(zone).toLocalDate()
    val dated = list.map { n -> n to parseInstant(n.createdAt) }
    val ok = dated.filter { it.second != null }.sortedByDescending { it.second }
    val sections = ok.groupBy({ it.second!!.atZone(zone).toLocalDate().coerceAtMost(today) }, { it.first })
        .map { (date, items) -> DaySection(date, dayLabel(date, today), items) }
    val broken = dated.filter { it.second == null }.map { it.first }
    return if (broken.isEmpty()) sections else sections + DaySection(LocalDate.MIN, "Anteriores", broken)
}

/** "Ahora", "Hace 5 min" (menos de 1 h) o la hora "18:42". */
fun timeLabel(createdAt: String, now: Instant, zone: ZoneId): String {
    val at = parseInstant(createdAt) ?: return ""
    val minutes = ChronoUnit.MINUTES.between(at, now)
    return when {
        minutes < 1 -> "Ahora"
        minutes < 60 -> "Hace $minutes min"
        else -> at.atZone(zone).format(CLOCK_FORMAT)
    }
}

// ─────────────── Política de avisos ───────────────

/** Qué hacer cuando llega una notificación por el socket. */
enum class AlertAction { None, InAppBanner, SystemNotification }

/**
 * - Repetida (mismo id ya avisado) → nada.
 * - App en primer plano → banner dentro de la app.
 * - App en segundo plano → notificación del sistema.
 */
fun alertAction(isForeground: Boolean, alreadySeen: Boolean): AlertAction = when {
    alreadySeen -> AlertAction.None
    isForeground -> AlertAction.InAppBanner
    else -> AlertAction.SystemNotification
}

/** Memoria acotada de ids ya avisados (el socket puede repetir eventos tras reconectar). */
class SeenIds(private val capacity: Int = 128) {
    private val ids = object : LinkedHashMap<String, Unit>(capacity, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?) = size > capacity
    }

    /** `true` si es la primera vez que se ve [id]. */
    @Synchronized
    fun firstTime(id: String): Boolean = ids.put(id, Unit) == null

    @Synchronized
    fun clear() = ids.clear()
}
