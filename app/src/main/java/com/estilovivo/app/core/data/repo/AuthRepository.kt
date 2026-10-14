package com.estilovivo.app.core.data.repo

import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.model.AuthOutcome
import com.estilovivo.app.core.data.model.PreferencesUpdate
import com.estilovivo.app.core.data.model.ProfileUpdate
import com.estilovivo.app.core.data.model.RegisterOutcome
import com.estilovivo.app.core.data.model.User
import com.estilovivo.app.core.data.net.ApiService
import com.estilovivo.app.core.data.net.ChangePasswordRequest
import com.estilovivo.app.core.data.net.EmailRequest
import com.estilovivo.app.core.data.net.LoginRequest
import com.estilovivo.app.core.data.net.Mappers
import com.estilovivo.app.core.data.net.RegisterRequest
import com.estilovivo.app.core.data.net.ResetPasswordRequest
import com.estilovivo.app.core.data.net.TokenRequest
import com.estilovivo.app.core.data.session.AppPrefs
import com.estilovivo.app.core.data.session.SessionManager
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Cuenta y sesión. Tras `login`/`register` (con sesión) la app queda autenticada y el socket se conecta solo. */
interface AuthRepository {
    /** Usuario cacheado de la sesión (null si no hay sesión). Se actualiza con login/me/updateProfile. */
    val currentUser: StateFlow<User?>

    /** [remember] se guarda en [AppPrefs.rememberMe] (para rellenar el email la próxima vez). */
    suspend fun login(email: String, password: String, remember: Boolean = true): ApiResult<AuthOutcome>

    /** [gender]: "male" | "female" | "other". [birthDate]: `YYYY-MM-DD`. */
    suspend fun register(
        email: String,
        password: String,
        name: String,
        gender: String? = null,
        birthDate: String? = null,
        remember: Boolean = true,
    ): ApiResult<RegisterOutcome>

    /** Llama a `/auth/logout` (sin bloquear si falla) y borra la sesión local. */
    suspend fun logout()

    /** `GET /auth/me`: refresca el usuario (contadores incluidos) y lo cachea. */
    suspend fun refreshMe(): ApiResult<User>

    /** Edita el perfil; con [ProfileUpdate.avatar]/[ProfileUpdate.fullBodyAvatar] se envía multipart. */
    suspend fun updateProfile(update: ProfileUpdate): ApiResult<User>

    /** `PUT /auth/preferences`: ajustes (privacidad, notificaciones, estilo, tema...). */
    suspend fun updatePreferences(update: PreferencesUpdate): ApiResult<User>

    suspend fun changePassword(currentPassword: String, newPassword: String): ApiResult<Unit>
    suspend fun forgotPassword(email: String): ApiResult<Unit>
    suspend fun resetPassword(token: String, newPassword: String): ApiResult<Unit>
    suspend fun verifyEmail(token: String): ApiResult<Unit>
    suspend fun resendVerification(email: String): ApiResult<Unit>

    /** Borra la cuenta y todos sus datos; cierra la sesión local. */
    suspend fun deleteAccount(): ApiResult<Unit>
}

internal class AuthRepositoryImpl(
    private val api: ApiService,
    private val session: SessionManager,
    private val mappers: Mappers,
    private val prefs: AppPrefs,
) : AuthRepository {

    override val currentUser: StateFlow<User?> get() = session.user

    override suspend fun login(email: String, password: String, remember: Boolean): ApiResult<AuthOutcome> = apiCall {
        val res = api.login(LoginRequest(email.trim(), password))
        val token = res.token ?: missing("el token")
        val user = mappers.user(res.user ?: missing("el usuario"))
        session.startSession(token, res.refreshToken, user)
        prefs.setRememberMe(remember)
        prefs.setBrandTheme(user.themePreset, user.customColor)
        AuthOutcome(user, res.requiresVerification)
    }

    override suspend fun register(
        email: String,
        password: String,
        name: String,
        gender: String?,
        birthDate: String?,
        remember: Boolean,
    ): ApiResult<RegisterOutcome> = apiCall {
        val res = api.register(RegisterRequest(email.trim(), password, name.trim(), gender, birthDate?.takeIf { it.isNotBlank() }))
        val user = res.user?.let { mappers.user(it) }
        val token = res.token
        if (token != null && !res.requiresVerification) {
            session.startSession(token, res.refreshToken, user)
            prefs.setRememberMe(remember)
            RegisterOutcome(user, loggedIn = true, requiresVerification = false)
        } else {
            RegisterOutcome(user, loggedIn = false, requiresVerification = true)
        }
    }

    override suspend fun logout() {
        try {
            if (session.hasSession) withTimeoutOrNull(5_000) { runCatching { api.logout() } }
        } finally {
            session.clear()
            runCatching { prefs.setBrandTheme(null, null) } // al cerrar sesión vuelve el tema clásico
        }
    }

    override suspend fun refreshMe(): ApiResult<User> = apiCall {
        val user = mappers.user(api.me(), previous = session.user.value)
        session.updateUser(user)
        prefs.setBrandTheme(user.themePreset, user.customColor)
        user
    }

    override suspend fun updateProfile(update: ProfileUpdate): ApiResult<User> = apiCall {
        val dto = if (update.avatar != null || update.fullBodyAvatar != null) {
            api.updateProfileMultipart(
                fields = textParts(
                    "name" to update.name, "bio" to update.bio, "mood" to update.mood,
                    "gender" to update.gender, "birthDate" to update.birthDate,
                ),
                avatar = update.avatar?.toPart("avatar"),
                fullBodyAvatar = update.fullBodyAvatar?.toPart("fullBodyAvatar"),
            )
        } else {
            api.updateProfile(
                buildJsonObject {
                    update.name?.let { put("name", it) }
                    update.bio?.let { put("bio", it) }
                    update.mood?.let { put("mood", it) }
                    update.gender?.let { put("gender", it) }
                    update.birthDate?.let { put("birthDate", it) }
                },
            )
        }
        val user = mappers.user(dto, previous = session.user.value)
        session.updateUser(user)
        user
    }

    override suspend fun updatePreferences(update: PreferencesUpdate): ApiResult<User> = apiCall {
        val body: JsonObject = buildJsonObject {
            update.cycleTracking?.let { put("cycleTracking", it) }
            update.musicSync?.let { put("musicSync", it) }
            update.emailNotifications?.let { put("emailNotifications", it) }
            update.emailChat?.let { put("emailChat", it) }
            update.emailFollows?.let { put("emailFollows", it) }
            update.emailWashing?.let { put("emailWashing", it) }
            update.emailChallenges?.let { put("emailChallenges", it) }
            update.isProfilePublic?.let { put("isProfilePublic", it) }
            update.highContrast?.let { put("highContrast", it) }
            update.themePreset?.let { put("themePreset", it) }
            update.customColor?.let { put("customColor", it) }
            update.locationName?.let { put("locationName", it) }
            update.darkModeSetting?.let { put("darkModeSetting", it) }
            update.fontSize?.let { put("fontSize", it) }
            update.language?.let { put("language", it) }
            update.dialect?.let { put("dialect", it) }
            update.styleColors?.let { put("styleColors", JsonArray(it.map(::JsonPrimitive))) }
            update.styleStyles?.let { put("styleStyles", JsonArray(it.map(::JsonPrimitive))) }
            update.styleOccasions?.let { put("styleOccasions", JsonArray(it.map(::JsonPrimitive))) }
            update.styleFabrics?.let { put("styleFabrics", JsonArray(it.map(::JsonPrimitive))) }
        }
        val user = mappers.user(api.updatePreferences(body), previous = session.user.value)
        session.updateUser(user)
        user
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String): ApiResult<Unit> = apiCall {
        val res = api.changePassword(ChangePasswordRequest(currentPassword, newPassword))
        // El servidor cierra el resto de sesiones y entrega un par nuevo para ésta
        res.token?.let { session.saveTokens(it, res.refreshToken) }
    }

    override suspend fun forgotPassword(email: String): ApiResult<Unit> = apiCall { api.forgotPassword(EmailRequest(email.trim())); Unit }

    override suspend fun resetPassword(token: String, newPassword: String): ApiResult<Unit> =
        apiCall { api.resetPassword(ResetPasswordRequest(token, newPassword)); Unit }

    override suspend fun verifyEmail(token: String): ApiResult<Unit> = apiCall { api.verifyEmail(TokenRequest(token)); Unit }

    override suspend fun resendVerification(email: String): ApiResult<Unit> =
        apiCall { api.resendVerification(EmailRequest(email.trim())); Unit }

    override suspend fun deleteAccount(): ApiResult<Unit> = apiCall {
        api.deleteAccount()
        session.clear()
        prefs.setBrandTheme(null, null)
    }
}
