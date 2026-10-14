package com.estilovivo.app.feature.auth

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estilovivo.app.core.data.ApiErrorKind
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.model.User
import com.estilovivo.app.core.data.repo.AuthRepository
import com.estilovivo.app.core.data.repo.GamificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class AuthMode { Login, Register }
enum class VerifyStatus { Loading, Success, Error }

/** Qué se muestra en la pantalla de acceso. */
sealed interface AuthStage {
    data object Form : AuthStage

    /** "Revisa tu correo": cuenta creada/sin verificar. */
    data class CheckEmail(val email: String) : AuthStage
    data object Forgot : AuthStage
    data class ForgotSent(val email: String) : AuthStage
    data class Reset(val token: String) : AuthStage
    data class Verify(val token: String, val status: VerifyStatus) : AuthStage
}

@Immutable
data class AuthUiState(
    val stage: AuthStage = AuthStage.Form,
    val mode: AuthMode = AuthMode.Login,
    val loading: Boolean = false,
    /** Error visible en el formulario actual (mensaje legible). */
    val error: String? = null,
    /** Segundos hasta poder reenviar un correo (0 = ya se puede). */
    val cooldown: Int = 0,
    val resending: Boolean = false,
    /** En [AuthStage.Reset]: el enlace ya no vale; se ofrece pedir otro. */
    val resetLinkInvalid: Boolean = false,
    /** Datos locales cargados al abrir. */
    val savedEmail: String = "",
    val rememberMe: Boolean = true,
    val ready: Boolean = false,
)

sealed interface AuthEffect {
    data class Toast(val message: String, val success: Boolean) : AuthEffect
    data class Enter(val destination: PostAuthDestination) : AuthEffect

    /** Contraseña restablecida; si había sesión abierta ya se cerró y hay que reiniciar la navegación. */
    data class ResetDone(val hadSession: Boolean) : AuthEffect
}

const val RESEND_COOLDOWN_SECONDS = 60

class AuthViewModel(
    private val auth: AuthRepository,
    private val gamification: GamificationRepository,
    private val store: AuthStore,
    private val hub: DeepLinkHub,
    private val appScope: CoroutineScope,
    private val hasSession: () -> Boolean,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private val _effects = Channel<AuthEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var cooldownJob: Job? = null
    private var busy = false
    private var entering = false

    init {
        viewModelScope.launch {
            val email = store.lastEmail().orEmpty()
            val remember = store.rememberMe()
            _state.update { it.copy(savedEmail = email, rememberMe = remember, ready = true) }
        }
        viewModelScope.launch { hub.pending.collect { p -> if (p != null) handleLink(p) } }
    }

    // ── navegación entre estados ─────────────────────────────────────────────────────────────

    fun setMode(mode: AuthMode) = _state.update { if (it.loading) it else it.copy(mode = mode, error = null) }
    fun goForgot() = _state.update { it.copy(stage = AuthStage.Forgot, error = null, loading = false) }

    fun backToForm(mode: AuthMode? = null) {
        _state.update { it.copy(stage = AuthStage.Form, error = null, loading = false, resetLinkInvalid = false, mode = mode ?: it.mode) }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    // ── enlaces entrantes ────────────────────────────────────────────────────────────────────

    private fun handleLink(p: PendingLink) {
        when (val link = p.link) {
            is AppLink.ResetPassword -> {
                hub.consume(p.seq)
                _state.update { it.copy(stage = AuthStage.Reset(link.token), error = null, loading = false, resetLinkInvalid = false) }
            }
            is AppLink.VerifyEmail -> {
                // Con sesión abierta lo resuelve `DeepLinkEffect` (toast y seguir)
                if (hasSession()) return
                hub.consume(p.seq)
                verify(link.token)
            }
            is AppLink.AuthError -> {
                hub.consume(p.seq)
                val msg = if (link.serverError) "Error del servidor. Inténtalo de nuevo más tarde."
                else "El enlace de recuperación ha caducado o no es válido. Pide uno nuevo."
                _state.update { it.copy(stage = AuthStage.Forgot, error = msg, loading = false) }
            }
            is AppLink.Look, is AppLink.User -> Unit // los trata DeepLinkEffect
        }
    }

    // ── login / registro ─────────────────────────────────────────────────────────────────────

    fun login(email: String, password: String, remember: Boolean) {
        if (busy) return
        val problem = AuthRules.email(email) ?: AuthRules.loginPassword(password)
        if (problem != null) { _state.update { it.copy(error = problem) }; return }
        val normalized = AuthRules.normalizeEmail(email)
        launchBusy {
            when (val r = auth.login(normalized, password, remember)) {
                is ApiResult.Success -> {
                    rememberEmail(normalized, remember)
                    if (r.value.requiresVerification) {
                        _effects.send(AuthEffect.Toast("Recuerda verificar tu correo con el enlace que te enviamos.", success = false))
                    }
                    finishSession(r.value.user)
                }
                is ApiResult.Failure -> onAuthFailure(r.error.message, r.error.kind, normalized)
            }
        }
    }

    fun register(
        email: String, password: String, confirm: String, name: String,
        gender: String?, birthDate: LocalDate?, remember: Boolean,
    ) {
        if (busy) return
        val problem = AuthRules.name(name) ?: AuthRules.email(email) ?: AuthRules.newPassword(password)
            ?: AuthRules.confirmPassword(password, confirm) ?: AuthRules.birthDate(birthDate, today())
        if (problem != null) { _state.update { it.copy(error = problem) }; return }
        val normalized = AuthRules.normalizeEmail(email)
        launchBusy {
            // Sin género elegido se envía "other": el servidor asumiría "female" si faltara
            val res = auth.register(normalized, password, name.trim(), gender ?: "other", birthDate?.toString(), remember)
            when (res) {
                is ApiResult.Success -> {
                    rememberEmail(normalized, remember)
                    val out = res.value
                    if (out.loggedIn) {
                        finishSession(out.user)
                    } else {
                        _state.update { it.copy(stage = AuthStage.CheckEmail(normalized), loading = false, error = null) }
                        startCooldown()
                    }
                }
                is ApiResult.Failure -> onAuthFailure(res.error.message, res.error.kind, normalized)
            }
        }
    }

    private suspend fun onAuthFailure(message: String, kind: ApiErrorKind, email: String) {
        if (message.equals("emailNotVerified", ignoreCase = true)) {
            _state.update { it.copy(stage = AuthStage.CheckEmail(email), loading = false, error = null) }
            startCooldown(0)
        } else {
            _state.update { it.copy(error = message, loading = false) }
        }
    }

    private suspend fun rememberEmail(email: String, remember: Boolean) {
        store.setLastEmail(if (remember) email else null)
    }

    private suspend fun finishSession(user: User?) {
        // Racha diaria en segundo plano: no bloquea ni falla la entrada
        appScope.launch { gamification.dailyLogin() }
        val done = store.onboardingDone()
        val dest = decidePostAuth(user, done)
        if (dest == PostAuthDestination.Main && !done) store.setOnboardingDone()
        entering = true // el botón sigue en carga hasta que la navegación lo reemplaza
        _effects.send(AuthEffect.Enter(dest))
    }

    // ── recuperar / restablecer ──────────────────────────────────────────────────────────────

    fun sendRecovery(email: String) {
        if (busy) return
        val problem = AuthRules.email(email)
        if (problem != null) { _state.update { it.copy(error = problem) }; return }
        val normalized = AuthRules.normalizeEmail(email)
        launchBusy {
            when (val r = auth.forgotPassword(normalized)) {
                // Respuesta genérica del servidor: no se revela si la cuenta existe
                is ApiResult.Success -> {
                    _state.update { it.copy(stage = AuthStage.ForgotSent(normalized), loading = false, error = null) }
                    startCooldown()
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(error = r.error.message, loading = false) }
                    if (r.error.kind == ApiErrorKind.RateLimited) startCooldown()
                }
            }
        }
    }

    fun resetPassword(newPassword: String, confirm: String) {
        val stage = _state.value.stage as? AuthStage.Reset ?: return
        if (busy) return
        val problem = AuthRules.newPassword(newPassword) ?: AuthRules.confirmPassword(newPassword, confirm)
        if (problem != null) { _state.update { it.copy(error = problem) }; return }
        launchBusy {
            when (val r = auth.resetPassword(stage.token, newPassword)) {
                is ApiResult.Success -> {
                    // El servidor invalida los refresh tokens: con sesión abierta se cierra la local
                    val had = hasSession()
                    if (had) auth.logout()
                    _state.update { it.copy(stage = AuthStage.Form, mode = AuthMode.Login, loading = false, error = null) }
                    _effects.send(AuthEffect.Toast("Contraseña cambiada. Ya puedes entrar.", success = true))
                    _effects.send(AuthEffect.ResetDone(had))
                }
                is ApiResult.Failure -> {
                    val invalid = r.error.kind == ApiErrorKind.Validation || r.error.kind == ApiErrorKind.NotFound
                    _state.update { it.copy(error = r.error.message, loading = false, resetLinkInvalid = invalid) }
                }
            }
        }
    }

    // ── verificación y reenvío ───────────────────────────────────────────────────────────────

    fun verify(token: String) {
        _state.update { it.copy(stage = AuthStage.Verify(token, VerifyStatus.Loading), error = null, loading = false) }
        viewModelScope.launch {
            val r = auth.verifyEmail(token)
            _state.update { s ->
                if (s.stage !is AuthStage.Verify) s
                else when (r) {
                    is ApiResult.Success -> s.copy(stage = AuthStage.Verify(token, VerifyStatus.Success))
                    is ApiResult.Failure -> s.copy(stage = AuthStage.Verify(token, VerifyStatus.Error), error = r.error.message)
                }
            }
        }
    }

    fun retryVerify() = (state.value.stage as? AuthStage.Verify)?.let { verify(it.token) }

    /** Reenvía el correo de verificación (CheckEmail) o de recuperación (ForgotSent), con enfriamiento de 60 s. */
    fun resend() {
        val s = _state.value
        if (s.cooldown > 0 || s.resending) return
        when (val st = s.stage) {
            is AuthStage.CheckEmail -> doResend(st.email) { auth.resendVerification(it) }
            is AuthStage.ForgotSent -> doResend(st.email) { auth.forgotPassword(it) }
            else -> Unit
        }
    }

    private fun doResend(email: String, call: suspend (String) -> ApiResult<Unit>) {
        _state.update { it.copy(resending = true) }
        viewModelScope.launch {
            when (val r = call(email)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(resending = false) }
                    startCooldown()
                    _effects.send(AuthEffect.Toast("Correo reenviado. Revisa también el spam.", success = true))
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(resending = false) }
                    if (r.error.kind == ApiErrorKind.RateLimited) startCooldown()
                    _effects.send(AuthEffect.Toast(r.error.message, success = false))
                }
            }
        }
    }

    private fun startCooldown(seconds: Int = RESEND_COOLDOWN_SECONDS) {
        cooldownJob?.cancel()
        _state.update { it.copy(cooldown = seconds) }
        if (seconds <= 0) return
        cooldownJob = viewModelScope.launch {
            while (_state.value.cooldown > 0) {
                delay(1000)
                _state.update { it.copy(cooldown = (it.cooldown - 1).coerceAtLeast(0)) }
            }
        }
    }

    /** Ejecuta [block] con `loading` y protección contra doble toque. */
    private fun launchBusy(block: suspend () -> Unit) {
        busy = true
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                busy = false
                if (!entering) _state.update { it.copy(loading = false) }
            }
        }
    }
}
