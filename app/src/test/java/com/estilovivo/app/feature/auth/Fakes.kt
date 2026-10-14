package com.estilovivo.app.feature.auth

import com.estilovivo.app.core.data.ApiError
import com.estilovivo.app.core.data.ApiErrorKind
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.model.Achievement
import com.estilovivo.app.core.data.model.AuthOutcome
import com.estilovivo.app.core.data.model.Badge
import com.estilovivo.app.core.data.model.DailyLoginResult
import com.estilovivo.app.core.data.model.GamificationProgress
import com.estilovivo.app.core.data.model.PreferencesUpdate
import com.estilovivo.app.core.data.model.ProfileUpdate
import com.estilovivo.app.core.data.model.RegisterOutcome
import com.estilovivo.app.core.data.model.User
import com.estilovivo.app.core.data.repo.AuthRepository
import com.estilovivo.app.core.data.repo.GamificationRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

fun fail(message: String, kind: ApiErrorKind = ApiErrorKind.Validation) = ApiResult.Failure(ApiError(message, kind))
fun ok() = ApiResult.Success(Unit)

class FakeAuthRepo : AuthRepository {
    override val currentUser: StateFlow<User?> = MutableStateFlow(null)
    var loginResult: ApiResult<AuthOutcome> = ApiResult.Success(AuthOutcome(User(id = "u1"), false))
    var loginGate: CompletableDeferred<Unit>? = null
    var registerResult: ApiResult<RegisterOutcome> =
        ApiResult.Success(RegisterOutcome(User(id = "u1"), loggedIn = false, requiresVerification = true))
    var forgotResult: ApiResult<Unit> = ok()
    var resetResult: ApiResult<Unit> = ok()
    var verifyResult: ApiResult<Unit> = ok()
    var resendResult: ApiResult<Unit> = ok()
    var prefsResult: ApiResult<User> = ApiResult.Success(User(id = "u1"))

    var loginCalls = 0
    val loginEmails = mutableListOf<String>()
    val registers = mutableListOf<List<String?>>()
    var resendCalls = 0
    var forgotCalls = 0
    var logouts = 0
    val prefsUpdates = mutableListOf<PreferencesUpdate>()
    val resetTokens = mutableListOf<String>()

    override suspend fun login(email: String, password: String, remember: Boolean): ApiResult<AuthOutcome> {
        loginCalls++
        loginEmails += email
        loginGate?.await()
        return loginResult
    }

    override suspend fun register(
        email: String, password: String, name: String, gender: String?, birthDate: String?, remember: Boolean,
    ): ApiResult<RegisterOutcome> {
        registers += listOf(email, name, gender, birthDate)
        return registerResult
    }

    override suspend fun logout() { logouts++ }
    override suspend fun refreshMe(): ApiResult<User> = ApiResult.Success(User(id = "u1"))
    override suspend fun updateProfile(update: ProfileUpdate): ApiResult<User> = ApiResult.Success(User(id = "u1"))
    override suspend fun updatePreferences(update: PreferencesUpdate): ApiResult<User> {
        prefsUpdates += update
        return prefsResult
    }
    override suspend fun changePassword(currentPassword: String, newPassword: String): ApiResult<Unit> = ok()
    override suspend fun forgotPassword(email: String): ApiResult<Unit> { forgotCalls++; return forgotResult }
    override suspend fun resetPassword(token: String, newPassword: String): ApiResult<Unit> { resetTokens += token; return resetResult }
    override suspend fun verifyEmail(token: String): ApiResult<Unit> = verifyResult
    override suspend fun resendVerification(email: String): ApiResult<Unit> { resendCalls++; return resendResult }
    override suspend fun deleteAccount(): ApiResult<Unit> = ok()
}

class FakeGamification : GamificationRepository {
    var dailyCalls = 0
    override suspend fun getProgress(): ApiResult<GamificationProgress> = fail("no")
    override suspend fun getAchievements(): ApiResult<List<Achievement>> = ApiResult.Success(emptyList())
    override suspend fun getBadges(): ApiResult<List<Badge>> = ApiResult.Success(emptyList())
    override suspend fun checkAchievements(): ApiResult<List<Achievement>> = ApiResult.Success(emptyList())
    override suspend fun dailyLogin(): ApiResult<DailyLoginResult> {
        dailyCalls++
        return ApiResult.Success(DailyLoginResult(1, 5, emptyList()))
    }
}

class FakeStore(var email: String? = null, var remember: Boolean = true, var done: Boolean = false) : AuthStore {
    override suspend fun lastEmail() = email
    override suspend fun setLastEmail(email: String?) { this.email = email }
    override suspend fun rememberMe() = remember
    override suspend fun onboardingDone() = done
    override suspend fun setOnboardingDone() { done = true }
}
