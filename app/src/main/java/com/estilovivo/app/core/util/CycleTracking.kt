package com.estilovivo.app.core.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/** Periodo del ciclo (fechas ISO `yyyy-MM-dd`). Dato de salud: sólo vive cifrado en el dispositivo. */
data class CyclePeriod(val startDate: String, val endDate: String)

val CYCLE_MESSAGES = listOf(
    "Hoy brillas más que nunca",
    "Tu energía está contigo",
    "Cuida de ti, tu estilo también descansa",
    "Eres fuerte, viste con calma",
    "Tu bienestar es tu mejor accesorio",
    "Respira, brilla a tu ritmo",
)

/**
 * Seguimiento del ciclo (equivalente a `cycleTracking.ts` de la web). Se guarda por usuaria en
 * EncryptedSharedPreferences y nunca se envía al servidor.
 */
class CycleStore(context: Context) {
    private val prefs: SharedPreferences = runCatching {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "ev_cycle", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse { context.getSharedPreferences("ev_cycle_fallback", Context.MODE_PRIVATE) }

    private val _changes = MutableStateFlow(0)
    /** Cambia cada vez que se guarda/borra un periodo (para recomponer). */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    private fun key(userId: String) = "period_$userId"

    fun get(userId: String?): CyclePeriod? {
        if (userId.isNullOrBlank()) return null
        val raw = prefs.getString(key(userId), null) ?: return null
        val parts = raw.split('|')
        return if (parts.size == 2 && parts.all { it.isNotBlank() }) CyclePeriod(parts[0], parts[1]) else null
    }

    /** `period = null` borra. Devuelve false si las fechas no son válidas o el fin es anterior al inicio. */
    fun save(userId: String, period: CyclePeriod?): Boolean {
        if (period == null) {
            prefs.edit().remove(key(userId)).apply(); _changes.value++; return true
        }
        val start = runCatching { LocalDate.parse(period.startDate) }.getOrNull() ?: return false
        val end = runCatching { LocalDate.parse(period.endDate) }.getOrNull() ?: return false
        if (end.isBefore(start)) return false
        prefs.edit().putString(key(userId), "${period.startDate}|${period.endDate}").apply()
        _changes.value++
        return true
    }

    fun isInCycle(userId: String?, date: LocalDate): Boolean {
        val p = get(userId) ?: return false
        val s = LocalDate.parse(p.startDate); val e = LocalDate.parse(p.endDate)
        return !date.isBefore(s) && !date.isAfter(e)
    }

    /** Mensaje motivacional del día (rota por el día dentro del periodo) o null si no estás en el ciclo. */
    fun messageFor(userId: String?, date: LocalDate): String? {
        val p = get(userId) ?: return null
        if (!isInCycle(userId, date)) return null
        val day = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(p.startDate), date).toInt().coerceAtLeast(0)
        return CYCLE_MESSAGES[day % CYCLE_MESSAGES.size]
    }
}
