package com.estilovivo.app.feature.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.EvButtonStyle
import com.estilovivo.app.core.design.components.EvHeadline
import com.estilovivo.app.core.design.components.EvLoadingDots
import com.estilovivo.app.core.design.components.EvPillButton
import com.estilovivo.app.core.design.floatingBob
import com.estilovivo.app.core.design.staggeredEntrance

/** "Revisa tu correo" (registro pendiente / recuperación enviada) con reenvío y cuenta atrás de 60 s. */
@Composable
internal fun CheckEmailStage(
    title: String,
    body: String,
    email: String,
    state: AuthUiState,
    onResend: () -> Unit,
    primaryText: String,
    onPrimary: () -> Unit,
) {
    val c = EvTheme.colors
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            Modifier.padding(top = 12.dp).size(112.dp).floatingBob(8f).clip(CircleShape).background(c.surface).staggeredEntrance(0),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.MarkEmailRead, null, tint = c.ink, modifier = Modifier.size(48.dp)) }
        EvHeadline(title, Modifier.staggeredEntrance(1))
        Text(body, style = EvType.body, color = c.inkSoft, textAlign = TextAlign.Center, modifier = Modifier.staggeredEntrance(2))
        Text(email, style = EvType.subtitle, color = c.ink, textAlign = TextAlign.Center, modifier = Modifier.staggeredEntrance(2))
        Text(
            "Puede tardar un minuto. Mira también en spam o correo no deseado.",
            style = EvType.bodySmall, color = c.inkMuted, textAlign = TextAlign.Center,
        )

        AnimatedContent(
            targetState = state.cooldown > 0,
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.9f)) togetherWith fadeOut() },
            label = "resendArea",
        ) { waiting ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (waiting) {
                    CooldownRing(state.cooldown)
                    Text("Podrás reenviarlo en ${state.cooldown} s", style = EvType.bodySmall, color = c.inkMuted)
                } else {
                    EvPillButton(
                        "Reenviar correo", onClick = onResend, style = EvButtonStyle.Secondary,
                        loading = state.resending, height = 48.dp,
                    )
                }
            }
        }
        EvPillButton(primaryText, onClick = onPrimary, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    }
}

/** "Olvidé mi contraseña": email → respuesta genérica (nunca revela si la cuenta existe). */
@Composable
internal fun ForgotStage(state: AuthUiState, vm: AuthViewModel) {
    val c = EvTheme.colors
    val keyboard = LocalSoftwareKeyboardController.current
    var email by rememberSaveable { mutableStateOf("") }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var prefilled by rememberSaveable { mutableStateOf(false) }
    if (!prefilled && state.ready) { prefilled = true; if (email.isEmpty()) email = state.savedEmail }
    fun submit() { attempted = true; keyboard?.hide(); vm.sendRecovery(email) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        EvHeadline("¿Olvidaste tu *contraseña*?")
        Text("Escribe tu email y te enviaremos un enlace para crear una nueva.", style = EvType.body, color = c.inkSoft)
        AuthField(
            email, { email = it.replace(" ", "") }, "Email",
            error = AuthRules.email(email), attempted = attempted, icon = Icons.Rounded.Mail,
            contentType = ContentType.EmailAddress,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp), enabled = !state.loading,
        )
        ErrorBanner(state.error)
        EvPillButton("Enviar enlace", onClick = ::submit, loading = state.loading, modifier = Modifier.fillMaxWidth())
        EvPillButton("Volver", onClick = { vm.backToForm(AuthMode.Login) }, style = EvButtonStyle.Ghost, modifier = Modifier.fillMaxWidth())
    }
}

/** Restablecer con el token del enlace. */
@Composable
internal fun ResetStage(state: AuthUiState, vm: AuthViewModel) {
    val c = EvTheme.colors
    val keyboard = LocalSoftwareKeyboardController.current
    var pass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var attempted by rememberSaveable { mutableStateOf(false) }
    fun submit() { attempted = true; keyboard?.hide(); vm.resetPassword(pass, confirm) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        EvHeadline("Una contraseña *nueva*.")
        Text("Elige una contraseña segura de al menos ${AuthRules.PASSWORD_MIN} caracteres.", style = EvType.body, color = c.inkSoft)
        PasswordField(
            pass, { pass = it }, "Nueva contraseña",
            error = AuthRules.newPassword(pass), attempted = attempted, newPassword = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp), enabled = !state.loading,
        )
        PasswordField(
            confirm, { confirm = it }, "Repite la contraseña",
            error = AuthRules.confirmPassword(pass, confirm), attempted = attempted, newPassword = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth(), enabled = !state.loading,
        )
        ErrorBanner(state.error)
        if (state.resetLinkInvalid) {
            EvPillButton("Pedir otro enlace", onClick = vm::goForgot, modifier = Modifier.fillMaxWidth())
        } else {
            EvPillButton("Guardar contraseña", onClick = ::submit, loading = state.loading, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Verificación de correo por enlace: cargando / éxito con check animado / error. */
@Composable
internal fun VerifyStage(stage: AuthStage.Verify, state: AuthUiState, vm: AuthViewModel) {
    val c = EvTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AnimatedContent(targetState = stage.status, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "verifyStatus") { status ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                when (status) {
                    VerifyStatus.Loading -> {
                        Box(Modifier.size(96.dp).clip(CircleShape).background(c.surface), contentAlignment = Alignment.Center) { EvLoadingDots() }
                        EvHeadline("Verificando tu *correo*…")
                    }
                    VerifyStatus.Success -> {
                        AnimatedCheck()
                        EvHeadline("¡Correo *verificado*!")
                        Text("Tu cuenta está lista. Ya puedes entrar.", style = EvType.body, color = c.inkSoft, textAlign = TextAlign.Center)
                        EvPillButton("Continuar", onClick = { vm.backToForm(AuthMode.Login) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                    VerifyStatus.Error -> {
                        Box(Modifier.size(96.dp).clip(CircleShape).background(c.danger.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.ErrorOutline, null, tint = c.danger, modifier = Modifier.size(44.dp))
                        }
                        EvHeadline("No pudimos *verificarlo*.")
                        Text(state.error ?: "El enlace no es válido o ha caducado.", style = EvType.body, color = c.inkSoft, textAlign = TextAlign.Center)
                        EvPillButton("Reintentar", onClick = vm::retryVerify, style = EvButtonStyle.Secondary, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                        EvPillButton("Ir a Entrar", onClick = { vm.backToForm(AuthMode.Login) }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}
