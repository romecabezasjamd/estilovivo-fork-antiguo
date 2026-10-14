package com.estilovivo.app.core.data.session

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.estilovivo.app.core.data.model.User
import com.estilovivo.app.core.data.net.ApiJson
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Estado de autenticación de la app. */
sealed interface SessionState {
    /** Sin sesión (nunca entró o cerró sesión voluntariamente). */
    data object LoggedOut : SessionState

    /** Hay tokens guardados. */
    data object LoggedIn : SessionState

    /** La sesión caducó y no se pudo renovar: hay que volver a iniciar sesión. */
    data object Expired : SessionState
}

/** Eventos puntuales de sesión. */
sealed interface SessionEvent {
    data object SessionExpired : SessionEvent
}

/** Lo mínimo que necesita la capa de red de la sesión (permite probar el refresh sin Android). */
interface AuthSession {
    val accessToken: String?
    val refreshToken: String?

    /** Guarda el par de tokens renovado (rotación del refresh token). */
    fun saveTokens(access: String, refresh: String?)

    /** Invalida la sesión: borra tokens y emite [SessionEvent.SessionExpired]. */
    fun expire()
}

/**
 * Sesión del usuario. Los tokens viven en [EncryptedSharedPreferences] (clave AES-256 en el Keystore) y se
 * mantienen en memoria para no descifrar en cada petición. Nunca se registran en logs.
 */
class SessionManager(context: Context) : AuthSession {
    private val prefs: SharedPreferences? = createPrefs(context.applicationContext)

    @Volatile private var access: String? = prefs?.getString(KEY_ACCESS, null)
    @Volatile private var refresh: String? = prefs?.getString(KEY_REFRESH, null)

    private val _state = MutableStateFlow<SessionState>(
        if (access != null || refresh != null) SessionState.LoggedIn else SessionState.LoggedOut,
    )
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _user = MutableStateFlow(readUser())

    /** Usuario cacheado (se refresca con [com.estilovivo.app.core.data.repo.AuthRepository.refreshMe]). */
    val user: StateFlow<User?> = _user.asStateFlow()

    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    override val accessToken: String? get() = access
    override val refreshToken: String? get() = refresh

    /** Hay tokens guardados (access o refresh): se puede intentar entrar directamente a la app. */
    val hasSession: Boolean get() = access != null || refresh != null

    val isLoggedIn: Boolean get() = _state.value == SessionState.LoggedIn

    val userId: String? get() = _user.value?.id

    /** Inicia sesión: guarda tokens y usuario. */
    fun startSession(accessToken: String, refreshToken: String?, user: User?) {
        access = accessToken
        refresh = refreshToken
        prefs?.edit()?.apply {
            putString(KEY_ACCESS, accessToken)
            if (refreshToken != null) putString(KEY_REFRESH, refreshToken) else remove(KEY_REFRESH)
        }?.apply()
        if (user != null) updateUser(user)
        _state.value = SessionState.LoggedIn
    }

    override fun saveTokens(access: String, refresh: String?) {
        this.access = access
        if (refresh != null) this.refresh = refresh
        prefs?.edit()?.apply {
            putString(KEY_ACCESS, access)
            if (refresh != null) putString(KEY_REFRESH, refresh)
        }?.apply()
        if (_state.value != SessionState.LoggedIn) _state.value = SessionState.LoggedIn
    }

    fun updateUser(user: User) {
        _user.value = user
        prefs?.edit()?.putString(KEY_USER, ApiJson.encodeToString(User.serializer(), user))?.apply()
    }

    /** Cierre de sesión voluntario: borra todo. */
    fun clear() {
        wipe()
        _state.value = SessionState.LoggedOut
    }

    override fun expire() {
        val wasLoggedIn = _state.value == SessionState.LoggedIn
        wipe()
        if (wasLoggedIn) {
            _state.value = SessionState.Expired
            _events.tryEmit(SessionEvent.SessionExpired)
        }
    }

    /** La UI ya llevó al usuario a la bienvenida: pasa de Expired a LoggedOut. */
    fun acknowledgeExpired() {
        if (_state.value == SessionState.Expired) _state.value = SessionState.LoggedOut
    }

    private fun wipe() {
        access = null
        refresh = null
        _user.value = null
        prefs?.edit()?.clear()?.apply()
    }

    private fun readUser(): User? {
        val raw = prefs?.getString(KEY_USER, null) ?: return null
        return runCatching { ApiJson.decodeFromString(User.serializer(), raw) }.getOrNull()
    }

    private companion object {
        const val FILE = "ev_secure_session"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_USER = "user_json"

        fun createPrefs(context: Context): SharedPreferences? {
            fun build(): SharedPreferences = EncryptedSharedPreferences.create(
                context,
                FILE,
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            return try {
                build()
            } catch (_: Exception) {
                // Keystore/archivo corrupto (restauración de backup, cambio de huella...): se empieza de cero
                runCatching { context.deleteSharedPreferences(FILE) }
                try { build() } catch (_: Exception) { null }
            }
        }
    }
}
