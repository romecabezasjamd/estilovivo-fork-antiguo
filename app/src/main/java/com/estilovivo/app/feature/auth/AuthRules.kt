package com.estilovivo.app.feature.auth

import java.time.LocalDate
import java.time.Period

/** Reglas de validación de los formularios de acceso (idénticas a las del servidor, `validators.ts`). */
object AuthRules {
    const val PASSWORD_MIN = 8
    const val PASSWORD_MAX = 128
    const val NAME_MIN = 2
    const val NAME_MAX = 60
    const val MIN_AGE = 13
    const val MAX_AGE = 120
    const val EMAIL_MAX = 254

    private val EMAIL = Regex(
        "^[A-Za-z0-9.!#\$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$",
    )

    /** Normaliza como el servidor: sin espacios y en minúsculas. */
    fun normalizeEmail(raw: String): String = raw.trim().lowercase()

    fun email(raw: String): String? {
        val v = raw.trim()
        return when {
            v.isEmpty() -> "Escribe tu email."
            v.length > EMAIL_MAX || !EMAIL.matches(v) || v.contains("..") -> "Introduce un email válido."
            else -> null
        }
    }

    /** Al entrar sólo se exige que no esté vacía (cuentas antiguas); al crearla/cambiarla, [newPassword]. */
    fun loginPassword(raw: String): String? = when {
        raw.isEmpty() -> "Escribe tu contraseña."
        raw.length > PASSWORD_MAX -> "La contraseña es demasiado larga."
        else -> null
    }

    fun newPassword(raw: String): String? = when {
        raw.isEmpty() -> "Escribe una contraseña."
        raw.length < PASSWORD_MIN -> "Mínimo $PASSWORD_MIN caracteres."
        raw.length > PASSWORD_MAX -> "Máximo $PASSWORD_MAX caracteres."
        else -> null
    }

    fun confirmPassword(password: String, confirm: String): String? = when {
        confirm.isEmpty() -> "Repite la contraseña."
        confirm != password -> "Las contraseñas no coinciden."
        else -> null
    }

    fun name(raw: String): String? {
        val v = raw.trim()
        return when {
            v.isEmpty() -> "Escribe tu nombre."
            v.length < NAME_MIN -> "Mínimo $NAME_MIN caracteres."
            v.length > NAME_MAX -> "Máximo $NAME_MAX caracteres."
            else -> null
        }
    }

    /** Fecha opcional: no futura, al menos [MIN_AGE] años y no absurda. */
    fun birthDate(date: LocalDate?, today: LocalDate): String? {
        if (date == null) return null
        if (date.isAfter(today)) return "La fecha no puede ser futura."
        val years = Period.between(date, today).years
        return when {
            years < MIN_AGE -> "Debes tener al menos $MIN_AGE años."
            years > MAX_AGE -> "Revisa la fecha de nacimiento."
            else -> null
        }
    }

    fun parseIsoDate(raw: String?): LocalDate? =
        raw?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}
