package com.estilovivo.app.feature.profile

import androidx.compose.runtime.Immutable
import com.estilovivo.app.core.data.model.PreferencesUpdate
import com.estilovivo.app.core.data.model.User
import java.time.LocalDate
import kotlin.math.floor
import kotlin.math.sqrt

/** Progreso dentro del nivel actual (misma fórmula que el servidor: XP acumulado del nivel n = 100·n·(n+1)/2). */
@Immutable
data class LevelProgress(val level: Int, val current: Int, val needed: Int, val fraction: Float, val totalXp: Int) {
    val remaining: Int get() = (needed - current).coerceAtLeast(0)
}

/** Reglas puras del módulo Perfil (sin Android: se prueban en JVM). */
object ProfileRules {
    const val NAME_MIN = 2
    const val NAME_MAX = 60
    const val BIO_MAX = 500
    const val LOCATION_MAX = 120
    const val PASSWORD_MIN = 8
    const val PASSWORD_MAX = 128
    const val DELETE_WORD = "ELIMINAR"
    const val MIN_FONT_SCALE = 0.85f
    const val MAX_FONT_SCALE = 1.5f

    // ───────── Nivel y XP ─────────

    /** XP acumulado necesario para COMPLETAR el nivel [level] (100·n·(n+1)/2). */
    fun cumulativeXp(level: Int): Int = if (level <= 0) 0 else 100 * level * (level + 1) / 2

    fun levelFromXp(xp: Int): Int {
        val safe = xp.coerceAtLeast(0)
        return maxOf(1, floor((sqrt(1.0 + 8.0 * safe / 100.0) - 1.0) / 2.0).toInt() + 1)
    }

    /** Progreso del nivel que corresponde a [xp]. Si [serverLevel] se conoce y difiere, manda el XP (fuente de verdad). */
    fun progress(xp: Int): LevelProgress {
        val safe = xp.coerceAtLeast(0)
        val level = levelFromXp(safe)
        val start = if (level <= 1) 0 else cumulativeXp(level - 1)
        val needed = cumulativeXp(level) - start
        val current = (safe - start).coerceIn(0, needed)
        return LevelProgress(level, current, needed, (current.toFloat() / needed.coerceAtLeast(1)).coerceIn(0f, 1f), safe)
    }

    // ───────── Perfil ─────────

    fun validateName(name: String): String? {
        val n = name.trim()
        return when {
            n.length < NAME_MIN -> "El nombre debe tener al menos $NAME_MIN caracteres."
            n.length > NAME_MAX -> "El nombre no puede pasar de $NAME_MAX caracteres."
            else -> null
        }
    }

    fun validateBio(bio: String): String? =
        if (bio.trim().length > BIO_MAX) "La bio no puede pasar de $BIO_MAX caracteres." else null

    fun validateLocation(location: String): String? =
        if (location.trim().length > LOCATION_MAX) "La ubicación es demasiado larga." else null

    /** [iso] `yyyy-MM-dd` (o con hora). Vacío = sin fecha (válido). */
    fun validateBirthDate(iso: String, today: LocalDate = LocalDate.now()): String? {
        if (iso.isBlank()) return null
        val date = parseDate(iso) ?: return "La fecha de nacimiento no es válida."
        return when {
            date.isAfter(today) -> "La fecha de nacimiento no puede ser futura."
            date.isBefore(LocalDate.of(1900, 1, 1)) -> "La fecha de nacimiento no es válida."
            else -> null
        }
    }

    /** Admite `2000-01-31` y `2000-01-31T00:00:00.000Z`; devuelve null si no se entiende. */
    fun parseDate(iso: String?): LocalDate? =
        iso?.trim()?.takeIf { it.length >= 10 }?.let { runCatching { LocalDate.parse(it.substring(0, 10)) }.getOrNull() }

    // ───────── Contraseña ─────────

    @Immutable
    data class PasswordErrors(val current: String? = null, val new: String? = null, val confirm: String? = null) {
        val any: Boolean get() = current != null || new != null || confirm != null
    }

    /** Validación local previa a la llamada (el servidor repite `samePassword` e `invalidCurrentPassword`). */
    fun validatePassword(current: String, new: String, confirm: String): PasswordErrors = PasswordErrors(
        current = if (current.isEmpty()) "Escribe tu contraseña actual." else null,
        new = when {
            new.length < PASSWORD_MIN -> "La nueva contraseña debe tener al menos $PASSWORD_MIN caracteres."
            new.length > PASSWORD_MAX -> "La nueva contraseña es demasiado larga."
            current.isNotEmpty() && new == current -> "La nueva contraseña debe ser distinta de la actual."
            else -> null
        },
        confirm = if (confirm != new) "Las contraseñas no coinciden." else null,
    )

    // ───────── Emails ─────────

    @Immutable
    data class EmailPrefs(
        val master: Boolean, val chat: Boolean, val follows: Boolean, val washing: Boolean, val challenges: Boolean,
    ) {
        fun toUpdate() = PreferencesUpdate(
            emailNotifications = master, emailChat = chat, emailFollows = follows,
            emailWashing = washing, emailChallenges = challenges,
        )
    }

    enum class EmailKey { Master, Chat, Follows, Washing, Challenges }

    fun emailPrefs(user: User?) = EmailPrefs(
        user?.emailNotifications ?: true, user?.emailChat ?: true, user?.emailFollows ?: true,
        user?.emailWashing ?: true, user?.emailChallenges ?: true,
    )

    /**
     * Aplica un cambio coherente: el maestro sincroniza los cuatro; encender un tipo enciende el maestro;
     * apagar el último tipo encendido apaga el maestro.
     */
    fun applyEmailToggle(current: EmailPrefs, key: EmailKey, value: Boolean): EmailPrefs {
        if (key == EmailKey.Master) return EmailPrefs(value, value, value, value, value)
        val next = when (key) {
            EmailKey.Chat -> current.copy(chat = value)
            EmailKey.Follows -> current.copy(follows = value)
            EmailKey.Washing -> current.copy(washing = value)
            EmailKey.Challenges -> current.copy(challenges = value)
            EmailKey.Master -> current
        }
        val anyOn = next.chat || next.follows || next.washing || next.challenges
        return next.copy(master = anyOn)
    }

    // ───────── Ciclo y cuenta ─────────

    /** El seguimiento del ciclo sólo se ofrece al perfil femenino. */
    fun canTrackCycle(gender: String?): Boolean = gender == "female"

    fun deleteConfirmed(typed: String): Boolean = typed.trim().equals(DELETE_WORD, ignoreCase = true)

    fun clampFontScale(value: Float): Float = value.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)

    /** Etiqueta legible de la categoría de prenda (ids del servidor). */
    fun categoryLabel(type: String): String = when (type) {
        "top" -> "Tops"
        "bottom" -> "Bottoms"
        "shoes" -> "Zapatos"
        "outerwear", "outer" -> "Abrigos"
        "accessories", "acc" -> "Accesorios"
        "dress" -> "Vestidos"
        "set" -> "Conjuntos"
        "swimwear" -> "Traje de baño"
        "activewear" -> "Ropa deportiva"
        else -> type.replaceFirstChar { it.uppercase() }
    }

    fun seasonLabel(id: String): String = when (id) {
        "summer" -> "Verano"
        "winter" -> "Invierno"
        "spring" -> "Primavera"
        "autumn" -> "Otoño"
        "transition" -> "Entretiempo"
        else -> "Todo el año"
    }
}
