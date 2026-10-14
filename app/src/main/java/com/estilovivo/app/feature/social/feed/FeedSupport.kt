package com.estilovivo.app.feature.social.feed

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.estilovivo.app.core.data.model.Comment
import com.estilovivo.app.core.data.model.Look
import com.estilovivo.app.core.data.model.Story
import com.estilovivo.app.core.data.model.User
import com.estilovivo.app.core.data.repo.AuthRepository
import kotlinx.coroutines.flow.StateFlow
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Utilidades puras del feed social (sin Android): fáciles de probar en JVM.
// ─────────────────────────────────────────────────────────────────────────────

const val SHARE_BASE_URL = "https://estilovivo.xyoncloud.win"

fun lookShareUrl(lookId: String) = "$SHARE_BASE_URL/look/$lookId"

/** Texto que se envía con ACTION_SEND. */
fun lookShareText(look: Look): String {
    val title = look.name.takeIf { it.isNotBlank() } ?: "Un look en EstiloVivo"
    return "$title\n${lookShareUrl(look.id)}"
}

/** Mismo criterio que la web: las publicaciones de venta no se muestran en el feed social. */
fun isSaleLook(look: Look): Boolean =
    look.garments.any { it.forSale } ||
        look.mood?.contains("venta", ignoreCase = true) == true ||
        look.tags.any { it.contains("venta", ignoreCase = true) }

/** Sólo enlaces http/https con host (evita `javascript:`, `file:`, `intent:`...). */
fun isSafeWebUrl(url: String?): Boolean {
    val raw = url?.trim().orEmpty()
    if (raw.isEmpty()) return false
    val uri = runCatching { URI(raw) }.getOrNull() ?: return false
    return (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) && !uri.host.isNullOrBlank()
}

fun parseInstantOrNull(iso: String?): Instant? {
    val s = iso?.trim().orEmpty()
    if (s.isEmpty()) return null
    return runCatching { Instant.parse(s) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(s).toInstant() }.getOrNull()
}

private val shortDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es", "ES")).withZone(ZoneId.systemDefault())

/** "ahora", "hace 5 min", "hace 3 h", "hace 2 d" o una fecha corta. */
fun relativeTime(iso: String?, now: Instant = Instant.now()): String {
    val then = parseInstantOrNull(iso) ?: return ""
    val secs = Duration.between(then, now).seconds
    return when {
        secs < 45 -> "ahora"
        secs < 3600 -> "hace ${(secs / 60).coerceAtLeast(1)} min"
        secs < 86_400 -> "hace ${secs / 3600} h"
        secs < 7 * 86_400 -> "hace ${secs / 86_400} d"
        else -> shortDate.format(then)
    }
}

/** Fórmula del servidor: XP acumulado para completar el nivel n = 100·n·(n+1)/2. */
private fun cumulativeXp(level: Int) = 100 * level * (level + 1) / 2

@Immutable
data class XpProgress(val current: Int, val needed: Int, val percentage: Int, val toNext: Int)

fun xpProgress(xp: Int, level: Int): XpProgress {
    val lvl = level.coerceAtLeast(1)
    val start = if (lvl <= 1) 0 else cumulativeXp(lvl - 1)
    val needed = (cumulativeXp(lvl) - start).coerceAtLeast(1)
    val current = (xp - start).coerceIn(0, needed)
    return XpProgress(current, needed, (current * 100f / needed).toInt(), needed - current)
}

fun levelTitle(level: Int): String = when {
    level < 3 -> "Principiante"
    level < 6 -> "Estilista"
    level < 10 -> "Fashionista"
    level < 15 -> "Icono"
    else -> "Leyenda"
}

// ───────────── Retos ─────────────

const val CHALLENGE_DESC_MIN = 10
const val CHALLENGE_DESC_MAX = 500

private val DEFAULT_TIPS = listOf("Busca buena luz natural", "Elige un fondo limpio", "Que se vea el look completo")

/** Consejos por reto (el servidor no los envía: se mantienen los de su rotación semanal). */
fun challengeTips(tag: String?): List<String> = when (tag?.trim()?.lowercase()) {
    "color block" -> listOf("Usa 3 colores como máximo", "Busca contraste entre prendas", "Añade un accesorio neutro", "La luz natural resalta los tonos")
    "monocromo" -> listOf("Usa tonos similares", "Varía texturas para evitar monotonía", "Añade un accesorio metalizado", "Juega con claroscuros")
    "street" -> listOf("Mezcla casual con elegante", "Zapatillas blancas: siempre aciertan", "Añade una chaqueta estructurada", "Busca fondos urbanos")
    "retro" -> listOf("Prendas de segunda mano o herencia", "Estampados de los 70 u 80", "Complementos retro", "Tono sepia en la foto")
    "eco" -> listOf("Materiales naturales o reciclados", "Colores tierra y verdes", "Muestra el entorno natural", "Menos es más")
    "gala" -> listOf("Tejidos nobles: seda o terciopelo", "Joyería elegante", "Maquillaje acorde", "Fondo oscuro para destacar")
    "verano" -> listOf("Tejidos ligeros: lino o algodón", "Colores vibrantes y estampados", "Complementos de playa", "Luz natural y fondo verde")
    "boho" -> listOf("Capas y texturas naturales", "Tonos tierra y naranjas", "Accesorios artesanales", "Fondo con hojas otoñales")
    "minimalista" -> listOf("Líneas limpias y cortes precisos", "Paleta de colores neutros", "Tejidos de calidad", "Fondo minimalista")
    "sporty" -> listOf("Mezcla deportivo con formal", "Zapatillas limpias", "Tejidos técnicos", "Energía y movimiento en la foto")
    else -> DEFAULT_TIPS
}

/** Devuelve el mensaje de error de validación, o `null` si se puede enviar. Reglas del servidor. */
fun challengeSubmitError(hasImage: Boolean, description: String): String? {
    if (!hasImage) return "Elige una foto de tu look para participar."
    val d = description.trim()
    return when {
        d.isEmpty() -> "Cuéntanos cómo has creado el look (mínimo $CHALLENGE_DESC_MIN caracteres)."
        d.length < CHALLENGE_DESC_MIN -> "La descripción debe tener al menos $CHALLENGE_DESC_MIN caracteres."
        d.length > CHALLENGE_DESC_MAX -> "La descripción no puede superar los $CHALLENGE_DESC_MAX caracteres."
        else -> null
    }
}

fun challengeTimeLeft(endIso: String?, now: Instant = Instant.now()): String? {
    val end = parseInstantOrNull(endIso) ?: return null
    val hours = Duration.between(now, end).toHours()
    return when {
        hours < 0 -> "Reto cerrado"
        hours < 24 -> "Termina hoy"
        else -> "Termina en ${hours / 24 + 1} días"
    }
}

// ───────────── Comentarios ─────────────

const val COMMENT_MAX = 500

fun commentError(text: String): String? {
    val t = text.trim()
    return when {
        t.isEmpty() -> "Escribe algo antes de enviar."
        t.length > COMMENT_MAX -> "El comentario no puede superar los $COMMENT_MAX caracteres."
        else -> null
    }
}

/** Raíces + respuestas. */
fun countAllComments(roots: List<Comment>): Int = roots.sumOf { 1 + it.replies.size }

// ───────────── Historias ─────────────

const val STORY_TEXT_MAX = 500
const val STORY_DURATION_MS = 7_000

/** Una persona con todas sus historias activas (más antigua primero, como en el visor). */
@Immutable
data class StoryGroup(
    val userId: String,
    val userName: String,
    val userAvatar: String?,
    val isOwn: Boolean,
    val stories: List<Story>,
    val allViewed: Boolean,
)

fun Story.isActive(now: Instant = Instant.now()): Boolean {
    val exp = parseInstantOrNull(expiresAt) ?: return true
    return exp.isAfter(now)
}

/**
 * Agrupa por autora: la propia primero; después las no vistas y, dentro de cada bloque, la más reciente primero.
 * (La API no expone a quién sigues, por eso no se puede priorizar a las seguidas.)
 */
fun groupStories(stories: List<Story>, viewed: Set<String>, now: Instant = Instant.now()): List<StoryGroup> {
    fun created(s: Story) = parseInstantOrNull(s.createdAt) ?: Instant.EPOCH
    return stories.filter { it.isActive(now) }
        .groupBy { it.userId }
        .map { (userId, list) ->
            val ordered = list.sortedBy(::created)
            val first = ordered.first()
            StoryGroup(
                userId = userId, userName = first.userName, userAvatar = first.userAvatar, isOwn = ordered.any { it.isOwn },
                stories = ordered,
                allViewed = ordered.all { it.isOwn || it.id in viewed },
            )
        }
        .sortedWith(
            compareBy<StoryGroup> { !it.isOwn }
                .thenBy { it.allViewed }
                .thenByDescending { g -> g.stories.maxOf(::created) },
        )
}

/** Primera historia sin ver de la autora (o la primera si las ha visto todas). */
fun StoryGroup.entryStoryId(viewed: Set<String>): String =
    (stories.firstOrNull { !it.isOwn && it.id !in viewed } ?: stories.first()).id

private val STORY_GRADIENTS = listOf(
    listOf(Color(0xFF0B0B0F), Color(0xFF3B3B4F)),
    listOf(Color(0xFFFF3B5C), Color(0xFFFF9A62)),
    listOf(Color(0xFF4F46E5), Color(0xFFA78BFA)),
    listOf(Color(0xFF0EA5E9), Color(0xFF34D399)),
    listOf(Color(0xFFF59E0B), Color(0xFFEF4444)),
    listOf(Color(0xFF111827), Color(0xFF9D174D)),
)

/** El servidor no guarda el fondo de las historias de texto: se deduce del id para que todas las vean igual. */
fun storyGradient(id: String): List<Color> = STORY_GRADIENTS[(id.hashCode() and Int.MAX_VALUE) % STORY_GRADIENTS.size]

val STORY_REACTIONS = listOf("❤️", "🔥", "💯", "😍", "👏")

// ───────────── Sesión ─────────────

/** Usuario propio y refresco tras ganar XP (adaptador mínimo sobre [AuthRepository], fácil de falsear en tests). */
interface MeProvider {
    val me: StateFlow<User?>
    suspend fun refresh()
}

class AuthMeProvider(private val auth: AuthRepository) : MeProvider {
    override val me: StateFlow<User?> get() = auth.currentUser
    override suspend fun refresh() { auth.refreshMe() }
}

// ───────────── Navegación dentro del visor de historias ─────────────

/** Posición en el visor: persona [group] e historia [index] dentro de ella. */
@Immutable
data class StoryCursor(val group: Int, val index: Int)

fun locateStory(groups: List<StoryGroup>, storyId: String): StoryCursor? {
    groups.forEachIndexed { g, group ->
        val i = group.stories.indexOfFirst { it.id == storyId }
        if (i >= 0) return StoryCursor(g, i)
    }
    return null
}

fun StoryCursor.storyIn(groups: List<StoryGroup>): Story? = groups.getOrNull(group)?.stories?.getOrNull(index)

/** Siguiente historia (pasa a la siguiente persona al acabar); `null` = se acabó todo. */
fun nextStory(groups: List<StoryGroup>, c: StoryCursor): StoryCursor? {
    val group = groups.getOrNull(c.group) ?: return null
    return when {
        c.index + 1 < group.stories.size -> StoryCursor(c.group, c.index + 1)
        c.group + 1 < groups.size -> StoryCursor(c.group + 1, 0)
        else -> null
    }
}

/** Historia anterior (la última de la persona anterior); en la primera de todas se queda donde está. */
fun prevStory(groups: List<StoryGroup>, c: StoryCursor): StoryCursor = when {
    c.index > 0 -> StoryCursor(c.group, c.index - 1)
    c.group > 0 -> StoryCursor(c.group - 1, (groups[c.group - 1].stories.size - 1).coerceAtLeast(0))
    else -> c
}
