package com.estilovivo.app.feature.auth

import com.estilovivo.app.core.data.ApiErrorKind
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.model.AuthOutcome
import com.estilovivo.app.core.data.model.RegisterOutcome
import com.estilovivo.app.core.data.model.User
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val tok = "b".repeat(64)

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class Env(
        val auth: FakeAuthRepo = FakeAuthRepo(),
        val gami: FakeGamification = FakeGamification(),
        val store: FakeStore = FakeStore(),
        val hub: DeepLinkHub = DeepLinkHub(),
        var session: Boolean = false,
    )

    private fun vm(e: Env) = AuthViewModel(
        e.auth, e.gami, e.store, e.hub, CoroutineScope(dispatcher), hasSession = { e.session },
        today = { LocalDate.of(2026, 10, 13) },
    )

    @Test fun `carga el ultimo email y la preferencia recordarme`() = runTest {
        val e = Env(store = FakeStore(email = "a@b.es", remember = false))
        val s = vm(e).state.value
        assertEquals("a@b.es", s.savedEmail)
        assertFalse(s.rememberMe)
        assertTrue(s.ready)
    }

    @Test fun `login correcto normaliza email, guarda email, daily login y va a onboarding`() = runTest {
        val e = Env()
        val vm = vm(e)
        vm.login("  Ana@Correo.ES ", "secreto123", remember = true)
        assertEquals(listOf("ana@correo.es"), e.auth.loginEmails)
        assertEquals("ana@correo.es", e.store.email)
        assertEquals(1, e.gami.dailyCalls)
        assertEquals(AuthEffect.Enter(PostAuthDestination.Onboarding), vm.effects.first())
    }

    @Test fun `sin recordarme no se guarda el email`() = runTest {
        val e = Env(store = FakeStore(email = "viejo@x.es"))
        vm(e).login("ana@correo.es", "secreto123", remember = false)
        assertNull(e.store.email)
    }

    @Test fun `con onboarding hecho o cuenta con datos va a Main`() = runTest {
        val e = Env(store = FakeStore(done = true))
        val v = vm(e)
        v.login("ana@correo.es", "x", true)
        assertEquals(AuthEffect.Enter(PostAuthDestination.Main), v.effects.first())

        val e2 = Env()
        e2.auth.loginResult = ApiResult.Success(AuthOutcome(User(id = "1", garmentCount = 4), false))
        val v2 = vm(e2)
        v2.login("ana@correo.es", "x", true)
        assertEquals(AuthEffect.Enter(PostAuthDestination.Main), v2.effects.first())
        assertTrue(e2.store.done)
    }

    @Test fun `login invalido no llama al servidor`() = runTest {
        val e = Env()
        val v = vm(e)
        v.login("no-es-email", "x", true)
        assertEquals(0, e.auth.loginCalls)
        assertEquals("Introduce un email válido.", v.state.value.error)
        v.login("ana@correo.es", "", true)
        assertEquals("Escribe tu contraseña.", v.state.value.error)
    }

    @Test fun `error del servidor se muestra tal cual y se puede reintentar`() = runTest {
        val e = Env()
        e.auth.loginResult = fail("Email o contraseña incorrectos.", ApiErrorKind.Unauthorized)
        val v = vm(e)
        v.login("ana@correo.es", "x", true)
        assertEquals("Email o contraseña incorrectos.", v.state.value.error)
        assertFalse(v.state.value.loading)
        v.login("ana@correo.es", "x", true)
        assertEquals(2, e.auth.loginCalls)
    }

    @Test fun `rate limit muestra el mensaje del servidor`() = runTest {
        val e = Env()
        e.auth.loginResult = fail("Demasiados intentos. Por favor, espera 15 minutos.", ApiErrorKind.RateLimited)
        val v = vm(e)
        v.login("ana@correo.es", "x", true)
        assertEquals("Demasiados intentos. Por favor, espera 15 minutos.", v.state.value.error)
    }

    @Test fun `doble toque solo envia una peticion`() = runTest {
        val e = Env()
        e.auth.loginGate = CompletableDeferred()
        val v = vm(e)
        v.login("ana@correo.es", "x", true)
        v.login("ana@correo.es", "x", true)
        assertTrue(v.state.value.loading)
        assertEquals(1, e.auth.loginCalls)
        e.auth.loginGate!!.complete(Unit)
        assertEquals(1, e.auth.loginCalls)
    }

    @Test fun `emailNotVerified lleva a revisa tu correo`() = runTest {
        val e = Env()
        e.auth.loginResult = fail("emailNotVerified", ApiErrorKind.Forbidden)
        val v = vm(e)
        v.login("ana@correo.es", "x", true)
        assertEquals(AuthStage.CheckEmail("ana@correo.es"), v.state.value.stage)
        assertNull(v.state.value.error)
    }

    @Test fun `registro con verificacion pendiente y cuenta atras de reenvio`() = runTest {
        val e = Env()
        val v = vm(e)
        v.register("ana@correo.es", "secreto123", "secreto123", "Ana", "female", null, true)
        assertEquals(AuthStage.CheckEmail("ana@correo.es"), v.state.value.stage)
        assertEquals(60, v.state.value.cooldown)
        v.resend()
        assertEquals(0, e.auth.resendCalls)
        advanceTimeBy(30_001)
        assertEquals(30, v.state.value.cooldown)
        advanceTimeBy(30_001)
        assertEquals(0, v.state.value.cooldown)
        v.resend()
        assertEquals(1, e.auth.resendCalls)
        assertEquals(60, v.state.value.cooldown)
        assertEquals(AuthEffect.Toast("Correo reenviado. Revisa también el spam.", true), v.effects.first())
    }

    @Test fun `reenvio fallido no reinicia la cuenta atras salvo rate limit`() = runTest {
        val e = Env()
        val v = vm(e)
        v.register("ana@correo.es", "secreto123", "secreto123", "Ana", null, null, true)
        advanceTimeBy(60_001)
        e.auth.resendResult = fail("sin red", ApiErrorKind.Network)
        v.resend()
        assertEquals(0, v.state.value.cooldown)
        assertFalse(v.state.value.resending)
        e.auth.resendResult = fail("Demasiados intentos", ApiErrorKind.RateLimited)
        v.resend()
        assertEquals(60, v.state.value.cooldown)
    }

    @Test fun `registro envia other si no hay genero y valida antes`() = runTest {
        val e = Env()
        val v = vm(e)
        v.register("ana@correo.es", "secreto123", "distinta1", "Ana", null, null, true)
        assertEquals("Las contraseñas no coinciden.", v.state.value.error)
        assertTrue(e.auth.registers.isEmpty())
        v.register("ana@correo.es", "secreto123", "secreto123", "Ana", null, LocalDate.of(2000, 1, 2), true)
        assertEquals(listOf<String?>("ana@correo.es", "Ana", "other", "2000-01-02"), e.auth.registers.single())
        v.backToForm()
        v.register("ana@correo.es", "secreto123", "secreto123", "Ana", "female", LocalDate.of(2020, 1, 1), true)
        assertEquals("Debes tener al menos 13 años.", v.state.value.error)
    }

    @Test fun `registro con sesion iniciada entra`() = runTest {
        val e = Env()
        e.auth.registerResult = ApiResult.Success(RegisterOutcome(User(id = "n"), loggedIn = true, requiresVerification = false))
        val v = vm(e)
        v.register("ana@correo.es", "secreto123", "secreto123", "Ana", "female", null, true)
        assertEquals(AuthEffect.Enter(PostAuthDestination.Onboarding), v.effects.first())
    }

    @Test fun `email ya registrado muestra el mensaje`() = runTest {
        val e = Env()
        e.auth.registerResult = fail("Ya existe una cuenta con este email.")
        val v = vm(e)
        v.register("ana@correo.es", "secreto123", "secreto123", "Ana", "female", null, true)
        assertEquals("Ya existe una cuenta con este email.", v.state.value.error)
        assertEquals(AuthStage.Form, v.state.value.stage)
    }

    @Test fun `recuperar contrasena respuesta generica y reenvio`() = runTest {
        val e = Env()
        val v = vm(e)
        v.goForgot()
        v.sendRecovery("nadie@correo.es")
        assertEquals(AuthStage.ForgotSent("nadie@correo.es"), v.state.value.stage)
        assertEquals(60, v.state.value.cooldown)
        advanceTimeBy(60_001)
        v.resend()
        assertEquals(2, e.auth.forgotCalls)
    }

    @Test fun `enlace de reset abre el estado de restablecer y valida`() = runTest {
        val e = Env()
        val v = vm(e)
        e.hub.submit("https://estilovivo.xyoncloud.win/reset-password/$tok")
        assertEquals(AuthStage.Reset(tok), v.state.value.stage)
        assertNull(e.hub.pending.value)
        v.resetPassword("corta", "corta")
        assertTrue(e.auth.resetTokens.isEmpty())
        v.resetPassword("nuevaclave1", "nuevaclave2")
        assertEquals("Las contraseñas no coinciden.", v.state.value.error)
        v.resetPassword("nuevaclave1", "nuevaclave1")
        assertEquals(listOf(tok), e.auth.resetTokens)
        assertEquals(AuthStage.Form, v.state.value.stage)
        assertEquals(AuthEffect.Toast("Contraseña cambiada. Ya puedes entrar.", true), v.effects.first())
        assertEquals(AuthEffect.ResetDone(hadSession = false), v.effects.first())
        assertEquals(0, e.auth.logouts)
    }

    @Test fun `reset con sesion abierta cierra la sesion local`() = runTest {
        val e = Env(session = true)
        val v = vm(e)
        e.hub.submit("estilovivo://reset-password/$tok")
        v.resetPassword("nuevaclave1", "nuevaclave1")
        assertEquals(1, e.auth.logouts)
        v.effects.first()
        assertEquals(AuthEffect.ResetDone(hadSession = true), v.effects.first())
    }

    @Test fun `token de reset caducado ofrece pedir otro`() = runTest {
        val e = Env()
        e.auth.resetResult = fail("El enlace no es válido o ha caducado.")
        val v = vm(e)
        e.hub.submit("estilovivo://reset-password/$tok")
        v.resetPassword("nuevaclave1", "nuevaclave1")
        assertTrue(v.state.value.resetLinkInvalid)
        assertEquals("El enlace no es válido o ha caducado.", v.state.value.error)
        v.goForgot()
        assertEquals(AuthStage.Forgot, v.state.value.stage)
    }

    @Test fun `verificacion por enlace sin sesion exito y error`() = runTest {
        val e = Env()
        val v = vm(e)
        e.hub.submit("https://estilovivo.xyoncloud.win/auth?verifyToken=$tok")
        assertEquals(AuthStage.Verify(tok, VerifyStatus.Success), v.state.value.stage)

        val e2 = Env()
        e2.auth.verifyResult = fail("El enlace de verificación no es válido o ha caducado.")
        val v2 = vm(e2)
        e2.hub.submit("https://estilovivo.xyoncloud.win/auth?verifyToken=$tok")
        assertEquals(AuthStage.Verify(tok, VerifyStatus.Error), v2.state.value.stage)
        assertEquals("El enlace de verificación no es válido o ha caducado.", v2.state.value.error)
        e2.auth.verifyResult = ok()
        v2.retryVerify()
        assertEquals(AuthStage.Verify(tok, VerifyStatus.Success), v2.state.value.stage)
    }

    @Test fun `verificacion con sesion abierta la deja al DeepLinkEffect`() = runTest {
        val e = Env(session = true)
        val v = vm(e)
        e.hub.submit("https://estilovivo.xyoncloud.win/auth?verifyToken=$tok")
        assertEquals(AuthStage.Form, v.state.value.stage)
        assertTrue(e.hub.pending.value != null)
    }

    @Test fun `enlace de error del servidor abre recuperar con mensaje`() = runTest {
        val e = Env()
        val v = vm(e)
        e.hub.submit("https://estilovivo.xyoncloud.win/auth?error=invalid_token")
        assertEquals(AuthStage.Forgot, v.state.value.stage)
        assertTrue(v.state.value.error!!.contains("caducado"))
    }

    @Test fun `enlace pendiente antes de abrir la pantalla tambien se trata`() = runTest {
        val e = Env()
        e.hub.submit("estilovivo://reset-password/$tok")
        val v = vm(e)
        assertEquals(AuthStage.Reset(tok), v.state.value.stage)
    }
}
