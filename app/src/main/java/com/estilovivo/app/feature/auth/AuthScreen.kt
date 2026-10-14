package com.estilovivo.app.feature.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.estilovivo.app.app.Auth
import com.estilovivo.app.app.LocalNavigator
import com.estilovivo.app.app.Onboarding
import com.estilovivo.app.app.Privacy
import com.estilovivo.app.core.design.EvMotion
import com.estilovivo.app.core.design.EvSpace
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.EvButtonStyle
import com.estilovivo.app.core.design.components.EvChip
import com.estilovivo.app.core.design.components.EvCircleButton
import com.estilovivo.app.core.design.components.EvHeadline
import com.estilovivo.app.core.design.components.EvPillButton
import com.estilovivo.app.core.design.components.LocalEvToast
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.rememberEvHaptics
import com.estilovivo.app.core.design.staggeredEntrance
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Destino `Auth`: acceso, registro, recuperación, verificación y "revisa tu correo". */
@Composable
fun AuthRoute() {
    val vm: AuthViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val toast = LocalEvToast.current
    val haptics = rememberEvHaptics()
    val c = EvTheme.colors

    LaunchedEffect(vm) {
        vm.effects.collect { fx ->
            when (fx) {
                is AuthEffect.Toast -> if (fx.success) toast.success(fx.message) else toast.show(fx.message)
                is AuthEffect.Enter -> when (fx.destination) {
                    PostAuthDestination.Main -> nav.enterMain()
                    PostAuthDestination.Onboarding -> nav.navigate(Onboarding)
                }
                is AuthEffect.ResetDone -> if (fx.hadSession) {
                    nav.resetToWelcome()
                    nav.navigate(Auth)
                }
            }
        }
    }
    LaunchedEffect(state.error) { if (state.error != null) haptics.reject() }
    LaunchedEffect(state.stage) { if (state.stage is AuthStage.Verify && (state.stage as AuthStage.Verify).status == VerifyStatus.Success) haptics.confirm() }

    BackHandler(enabled = state.stage != AuthStage.Form) { vm.backToForm() }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = EvSpace.screen, vertical = 12.dp),
        ) {
            EvCircleButton(
                Icons.AutoMirrored.Rounded.ArrowBack,
                onClick = { if (state.stage == AuthStage.Form) nav.back() else vm.backToForm() },
                contentDescription = "Volver",
            )
            AnimatedContent(
                targetState = state.stage,
                contentKey = { it::class },
                transitionSpec = {
                    (slideInHorizontally(tween(360, easing = EvMotion.emphasized)) { it / 5 } + fadeIn(tween(320))) togetherWith
                        (slideOutHorizontally(tween(240)) { -it / 6 } + fadeOut(tween(160)))
                },
                label = "authStage",
            ) { stage ->
                Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 24.dp)) {
                    when (stage) {
                        AuthStage.Form -> LoginRegisterForm(state, vm)
                        is AuthStage.CheckEmail -> CheckEmailStage(
                            title = "Revisa tu *correo*.",
                            body = "Te hemos enviado un enlace de verificación a",
                            email = stage.email, state = state,
                            onResend = vm::resend,
                            primaryText = "Ya lo he verificado", onPrimary = { vm.backToForm(AuthMode.Login) },
                        )
                        AuthStage.Forgot -> ForgotStage(state, vm)
                        is AuthStage.ForgotSent -> CheckEmailStage(
                            title = "Correo *enviado*.",
                            body = "Si existe una cuenta con este email, recibirás un enlace para crear una contraseña nueva en",
                            email = stage.email, state = state,
                            onResend = vm::resend,
                            primaryText = "Volver a entrar", onPrimary = { vm.backToForm(AuthMode.Login) },
                        )
                        is AuthStage.Reset -> ResetStage(state, vm)
                        is AuthStage.Verify -> VerifyStage(stage, state, vm)
                    }
                }
            }
        }
    }
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale("es", "ES"))

@Composable
private fun LoginRegisterForm(state: AuthUiState, vm: AuthViewModel) {
    val c = EvTheme.colors
    val nav = LocalNavigator.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val mode = state.mode
    val register = mode == AuthMode.Register

    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") } // la contraseña nunca se guarda
    var confirm by remember { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf<String?>(null) }
    var birthIso by rememberSaveable { mutableStateOf("") }
    var remember by rememberSaveable { mutableStateOf(true) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var prefilled by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.ready) {
        if (state.ready && !prefilled) {
            prefilled = true
            if (email.isEmpty()) email = state.savedEmail
            remember = state.rememberMe
        }
    }

    val today = remember { LocalDate.now() }
    val birth = AuthRules.parseIsoDate(birthIso)
    val loading = state.loading

    fun submit() {
        attempted = true
        keyboard?.hide()
        if (register) vm.register(email, password, confirm, name, gender, birth, remember)
        else vm.login(email, password, remember)
    }

    // Titular y subtítulo cambian con el modo
    AnimatedContent(
        targetState = mode,
        transitionSpec = { (fadeIn(tween(260)) + slideInVertically(tween(300)) { it / 4 }) togetherWith fadeOut(tween(120)) },
        label = "authTitle",
    ) { m ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EvHeadline(if (m == AuthMode.Login) "Bienvenida de *nuevo*." else "Crea tu *cuenta*.")
            Text(
                if (m == AuthMode.Login) "Entra para seguir con tu armario." else "Unos datos y empezamos a vestir tu día.",
                style = EvType.body, color = c.inkSoft,
            )
        }
    }

    Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AuthTabs(mode, onChange = { attempted = false; vm.setMode(it) }, Modifier.staggeredEntrance(0))

        AnimatedVisibility(register, enter = expandVertically(EvMotion.snappy()) + fadeIn(), exit = shrinkVertically(tween(200)) + fadeOut(tween(120))) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                AuthField(
                    name, { name = it.take(AuthRules.NAME_MAX + 10) }, "Nombre",
                    error = AuthRules.name(name), attempted = attempted, icon = Icons.Rounded.Person,
                    contentType = ContentType.PersonFirstName,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier.fillMaxWidth(), enabled = !loading,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Género", style = EvType.caption, color = c.inkMuted, modifier = Modifier.padding(start = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("female" to "Mujer", "male" to "Hombre", "other" to "Otro").forEach { (id, label) ->
                            EvChip(label, selected = gender == id, onClick = { gender = if (gender == id) null else id })
                        }
                    }
                }
                BirthField(birth, error = AuthRules.birthDate(birth, today), enabled = !loading, onClick = { showPicker = true }, onClear = { birthIso = "" })
            }
        }

        AuthField(
            email, { email = it.replace(" ", "").take(AuthRules.EMAIL_MAX + 10) }, "Email",
            error = AuthRules.email(email), attempted = attempted, icon = Icons.Rounded.Mail,
            contentType = ContentType.EmailAddress,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email, capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
            modifier = Modifier.fillMaxWidth().staggeredEntrance(1), enabled = !loading,
        )
        PasswordField(
            password, { password = it }, "Contraseña",
            error = if (register) AuthRules.newPassword(password) else AuthRules.loginPassword(password),
            attempted = attempted, newPassword = register,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password, imeAction = if (register) ImeAction.Next else ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }, onDone = { submit() }),
            modifier = Modifier.fillMaxWidth().staggeredEntrance(2), enabled = !loading,
        )
        AnimatedVisibility(register, enter = expandVertically(EvMotion.snappy()) + fadeIn(), exit = shrinkVertically(tween(200)) + fadeOut(tween(120))) {
            PasswordField(
                confirm, { confirm = it }, "Repite la contraseña",
                error = AuthRules.confirmPassword(password, confirm), attempted = attempted, newPassword = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth(), enabled = !loading,
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            CheckRow(remember, "Recordarme", { remember = it })
            if (!register) {
                Text(
                    "¿Olvidaste tu contraseña?", style = EvType.label, color = c.brandInk,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.evClickable(pressedScale = 0.95f) { vm.goForgot() }.padding(8.dp),
                )
            }
        }

        ErrorBanner(state.error)

        EvPillButton(
            text = if (register) "Crear cuenta" else "Entrar",
            onClick = ::submit, loading = loading, modifier = Modifier.fillMaxWidth().staggeredEntrance(3),
        )

        Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Al continuar aceptas nuestros", style = EvType.caption, color = c.inkMuted)
            Text(
                "Términos y Política de privacidad", style = EvType.caption.copy(textDecoration = TextDecoration.Underline), color = c.ink,
                modifier = Modifier.evClickable(pressedScale = 0.95f) { nav.navigate(Privacy) }.padding(8.dp),
            )
        }
    }

    if (showPicker) {
        BirthDateDialog(
            initial = birth, today = today,
            onDismiss = { showPicker = false },
            onPick = { birthIso = it.toString(); showPicker = false },
        )
    }
}

@Composable
private fun BirthField(date: LocalDate?, error: String?, enabled: Boolean, onClick: () -> Unit, onClear: () -> Unit) {
    val c = EvTheme.colors
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth()
                .evClickable(enabled = enabled, pressedScale = 0.985f, onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                com.estilovivo.app.core.design.components.EvTextField(
                    value = date?.format(DATE_FORMAT).orEmpty(),
                    onValueChange = {},
                    label = "Fecha de nacimiento (opcional)",
                    leadingIcon = Icons.Rounded.CalendarMonth,
                    error = error,
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (date != null) {
            Text(
                "Quitar fecha", style = EvType.caption, color = c.inkMuted, textDecoration = TextDecoration.Underline,
                modifier = Modifier.padding(start = 6.dp).evClickable(pressedScale = 0.95f) { onClear() }.padding(4.dp),
            )
        }
    }
}

@Composable
private fun BirthDateDialog(initial: LocalDate?, today: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val todayMillis = remember(today) { today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        initialDisplayedMonthMillis = initial?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
            ?: today.minusYears(25).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        yearRange = 1900..today.year,
        selectableDates = object : SelectableDates {
            // No futura (la edad mínima se explica en el campo si se elige una fecha reciente)
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
            override fun isSelectableYear(year: Int) = year <= today.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            EvPillButton(
                "Aceptar", height = 44.dp, modifier = Modifier.padding(end = 8.dp, bottom = 8.dp),
                enabled = state.selectedDateMillis != null,
                onClick = {
                    state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                },
            )
        },
        dismissButton = {
            EvPillButton("Cancelar", onClick = onDismiss, style = EvButtonStyle.Ghost, height = 44.dp, modifier = Modifier.padding(bottom = 8.dp))
        },
    ) {
        DatePicker(state)
    }
}
