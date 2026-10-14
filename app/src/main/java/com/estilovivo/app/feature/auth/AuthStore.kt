package com.estilovivo.app.feature.auth

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.estilovivo.app.core.data.session.AppPrefs
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Preferencias locales que necesita el acceso (se abstrae para probar sin Android). */
interface AuthStore {
    suspend fun lastEmail(): String?
    suspend fun setLastEmail(email: String?)
    suspend fun rememberMe(): Boolean
    suspend fun onboardingDone(): Boolean
    suspend fun setOnboardingDone()
}

private val Context.authPrefsStore by preferencesDataStore(
    name = "auth_prefs",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** Último email en un DataStore propio (nunca la contraseña); `rememberMe` y `onboardingDone` en [AppPrefs]. */
internal class DataStoreAuthStore(context: Context, private val prefs: AppPrefs) : AuthStore {
    private val store = context.applicationContext.authPrefsStore
    private val key = stringPreferencesKey("last_email")

    override suspend fun lastEmail(): String? =
        runCatching { store.data.catch { emit(emptyPreferences()) }.map { it[key] }.first() }.getOrNull()

    override suspend fun setLastEmail(email: String?) {
        runCatching { store.edit { if (email.isNullOrBlank()) it.remove(key) else it[key] = email } }
    }

    override suspend fun rememberMe(): Boolean = runCatching { prefs.current().rememberMe }.getOrDefault(true)
    override suspend fun onboardingDone(): Boolean = runCatching { prefs.current().onboardingDone }.getOrDefault(false)
    override suspend fun setOnboardingDone() { runCatching { prefs.setOnboardingDone(true) } }
}
