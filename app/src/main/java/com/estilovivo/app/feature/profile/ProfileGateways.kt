package com.estilovivo.app.feature.profile

import com.estilovivo.app.core.data.session.AppPrefs
import com.estilovivo.app.core.data.session.AppSettings
import com.estilovivo.app.core.data.session.ThemeMode
import com.estilovivo.app.core.util.CyclePeriod
import com.estilovivo.app.core.util.CycleStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Preferencias locales del dispositivo (tema, fuente, contraste, háptica). Abstracción de [AppPrefs] para poder probar sin Android. */
interface LocalSettings {
    val settings: Flow<AppSettings>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setFontScale(value: Float)
    suspend fun setHighContrast(value: Boolean)
    suspend fun setHaptics(value: Boolean)

    /** Tema de color de marca (preset + color propio `#RRGGBB`). */
    suspend fun setBrandTheme(preset: String?, customColor: String?) {}
}

internal class AppPrefsLocalSettings(private val prefs: AppPrefs) : LocalSettings {
    override val settings: Flow<AppSettings> get() = prefs.settings
    override suspend fun setThemeMode(mode: ThemeMode) = prefs.setThemeMode(mode)
    override suspend fun setFontScale(value: Float) = prefs.setFontScale(value)
    override suspend fun setHighContrast(value: Boolean) = prefs.setHighContrast(value)
    override suspend fun setHaptics(value: Boolean) = prefs.setHaptics(value)
    override suspend fun setBrandTheme(preset: String?, customColor: String?) = prefs.setBrandTheme(preset, customColor)
}

/** Periodo del ciclo (dato de salud, sólo en el dispositivo). Abstracción de [CycleStore]. */
interface CycleStorage {
    /** Cambia cada vez que se guarda/borra un periodo. */
    val changes: StateFlow<Int>
    fun get(userId: String?): CyclePeriod?

    /** `period = null` borra. `false` si las fechas no son válidas. */
    fun save(userId: String, period: CyclePeriod?): Boolean
}

internal class CycleStoreStorage(private val store: CycleStore) : CycleStorage {
    override val changes: StateFlow<Int> get() = store.changes
    override fun get(userId: String?): CyclePeriod? = store.get(userId)
    override fun save(userId: String, period: CyclePeriod?): Boolean = store.save(userId, period)
}
