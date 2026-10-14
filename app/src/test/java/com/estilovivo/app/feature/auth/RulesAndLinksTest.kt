package com.estilovivo.app.feature.auth

import com.estilovivo.app.core.data.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RulesAndLinksTest {
    private val today = LocalDate.of(2026, 10, 13)
    private val tok = "a".repeat(64)
    private val base = "https://estilovivo.xyoncloud.win"

    // ── validadores ──
    @Test fun `email valido e invalido`() {
        assertNull(AuthRules.email("ana@correo.es"))
        assertNull(AuthRules.email("  Ana.Lopez+x@sub.dominio.com "))
        assertNotNull(AuthRules.email(""))
        assertNotNull(AuthRules.email("ana"))
        assertNotNull(AuthRules.email("ana@"))
        assertNotNull(AuthRules.email("ana@correo"))
        assertNotNull(AuthRules.email("a..b@correo.es"))
        assertNotNull(AuthRules.email("a b@correo.es"))
        assertNotNull(AuthRules.email("a".repeat(250) + "@x.es"))
    }

    @Test fun `contrasenas`() {
        assertNotNull(AuthRules.newPassword("1234567"))
        assertNull(AuthRules.newPassword("12345678"))
        assertNull(AuthRules.newPassword("a".repeat(128)))
        assertNotNull(AuthRules.newPassword("a".repeat(129)))
        assertNull(AuthRules.loginPassword("corta"))
        assertNotNull(AuthRules.loginPassword(""))
        assertNotNull(AuthRules.confirmPassword("abcdefgh", "abcdefgx"))
        assertNull(AuthRules.confirmPassword("abcdefgh", "abcdefgh"))
    }

    @Test fun `nombre 2 a 60`() {
        assertNotNull(AuthRules.name(" a "))
        assertNull(AuthRules.name("Ana"))
        assertNull(AuthRules.name("a".repeat(60)))
        assertNotNull(AuthRules.name("a".repeat(61)))
    }

    @Test fun `edad minima 13, no futura y opcional`() {
        assertNull(AuthRules.birthDate(null, today))
        assertNotNull(AuthRules.birthDate(today.plusDays(1), today))
        assertNotNull(AuthRules.birthDate(today.minusYears(13).plusDays(1), today))
        assertNull(AuthRules.birthDate(today.minusYears(13), today))
        assertNotNull(AuthRules.birthDate(LocalDate.of(1890, 1, 1), today))
        assertEquals(LocalDate.of(2000, 1, 31), AuthRules.parseIsoDate("2000-01-31"))
        assertNull(AuthRules.parseIsoDate("31/01/2000"))
    }

    // ── deep links ──
    @Test fun `verificacion y reset por https`() {
        assertEquals(AppLink.VerifyEmail(tok), DeepLinkParser.parse("$base/auth?verifyToken=$tok"))
        assertEquals(AppLink.VerifyEmail(tok), DeepLinkParser.parse("$base/auth?token=$tok&type=verify"))
        assertEquals(AppLink.ResetPassword(tok), DeepLinkParser.parse("$base/auth?token=$tok&type=reset"))
        assertEquals(AppLink.ResetPassword(tok), DeepLinkParser.parse("$base/reset-password/$tok"))
        assertEquals(AppLink.AuthError(false), DeepLinkParser.parse("$base/auth?error=invalid_token"))
    }

    @Test fun `esquema propio`() {
        assertEquals(AppLink.ResetPassword(tok), DeepLinkParser.parse("estilovivo://reset-password/$tok"))
        assertEquals(AppLink.VerifyEmail(tok), DeepLinkParser.parse("estilovivo://auth?verifyToken=$tok"))
        assertEquals(AppLink.User("abc-123"), DeepLinkParser.parse("estilovivo://user/abc-123"))
        assertEquals(AppLink.Look("l1"), DeepLinkParser.parse("estilovivo:///look/l1"))
    }

    @Test fun `usuario y look`() {
        assertEquals(AppLink.User("cm9xyz"), DeepLinkParser.parse("$base/user/cm9xyz"))
        assertEquals(AppLink.Look("cm9xyz"), DeepLinkParser.parse("$base/look/cm9xyz"))
    }

    @Test fun `rechaza host falso, esquemas peligrosos y puertos`() {
        assertNull(DeepLinkParser.parse("https://evil.com/auth?verifyToken=$tok"))
        assertNull(DeepLinkParser.parse("https://estilovivo.xyoncloud.win.evil.com/auth?verifyToken=$tok"))
        assertNull(DeepLinkParser.parse("https://estilovivo.xyoncloud.win@evil.com/auth?verifyToken=$tok"))
        assertNull(DeepLinkParser.parse("https://evil.com@estilovivo.xyoncloud.win/auth?verifyToken=$tok"))
        assertNull(DeepLinkParser.parse("https://estilovivo.xyoncloud.win:8443/auth?verifyToken=$tok"))
        assertNull(DeepLinkParser.parse("http://estilovivo.xyoncloud.win/auth?verifyToken=$tok"))
        assertNull(DeepLinkParser.parse("javascript:alert(1)"))
        assertNull(DeepLinkParser.parse("intent://auth#Intent;scheme=x;end"))
        assertNull(DeepLinkParser.parse("file:///etc/passwd"))
    }

    @Test fun `rechaza tokens e ids malformados`() {
        assertNull(DeepLinkParser.parse("$base/auth?verifyToken=corto"))
        assertNull(DeepLinkParser.parse("$base/auth?verifyToken=${"a".repeat(300)}"))
        assertNull(DeepLinkParser.parse("$base/auth?verifyToken=${"a".repeat(30)}<script>"))
        assertNull(DeepLinkParser.parse("$base/reset-password/../etc"))
        assertNull(DeepLinkParser.parse("$base/reset-password/$tok/extra"))
        assertNull(DeepLinkParser.parse("$base/user/a%2Fb"))
        assertNull(DeepLinkParser.parse("$base/user/"))
        assertNull(DeepLinkParser.parse("$base/auth?token=$tok"))
        assertNull(DeepLinkParser.parse("$base/otra"))
        assertNull(DeepLinkParser.parse(null))
        assertNull(DeepLinkParser.parse("  "))
        assertNull(DeepLinkParser.parse("$base/auth?verifyToken=$tok&x=" + "b".repeat(3000)))
        assertNull(DeepLinkParser.parse("ht!tp://%%%"))
    }

    @Test fun `hub guarda el ultimo y consume solo el mismo`() {
        val hub = DeepLinkHub()
        assertFalse(hub.submit("https://evil.com/auth"))
        assertNull(hub.pending.value)
        assertTrue(hub.submit("estilovivo://user/a1"))
        val first = hub.pending.value!!
        assertTrue(hub.submit("estilovivo://user/a2"))
        hub.consume(first.seq)
        assertEquals(AppLink.User("a2"), hub.pending.value!!.link)
        hub.consume(hub.pending.value!!.seq)
        assertNull(hub.pending.value)
    }

    // ── destino tras login ──
    @Test fun `destino tras login`() {
        val empty = User(id = "1")
        assertEquals(PostAuthDestination.Onboarding, decidePostAuth(empty, onboardingDone = false))
        assertEquals(PostAuthDestination.Main, decidePostAuth(empty, onboardingDone = true))
        assertEquals(PostAuthDestination.Main, decidePostAuth(empty.copy(styleColors = listOf("red")), false))
        assertEquals(PostAuthDestination.Main, decidePostAuth(empty.copy(garmentCount = 3), false))
        assertEquals(PostAuthDestination.Main, decidePostAuth(empty.copy(lookCount = 1), false))
        assertEquals(PostAuthDestination.Main, decidePostAuth(null, false))
    }
}
