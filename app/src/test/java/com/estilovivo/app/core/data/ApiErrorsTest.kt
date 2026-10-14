package com.estilovivo.app.core.data

import com.estilovivo.app.core.data.net.parseApiErrorMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class ApiErrorsTest {
    private val json = "application/json; charset=utf-8"

    @Test fun `usa el primer details message de la validacion zod`() {
        val body = """{"error":"Error de validación","details":[{"field":"email","message":"Email inválido"},{"field":"password","message":"x"}]}"""
        assertEquals("Email inválido", parseApiErrorMessage(400, json, body))
    }

    @Test fun `usa el campo error cuando no hay details`() {
        assertEquals("Look no encontrado", parseApiErrorMessage(404, json, """{"error":"Look no encontrado"}"""))
    }

    @Test fun `cae a message y a detail`() {
        assertEquals("Aviso", parseApiErrorMessage(400, json, """{"message":"Aviso"}"""))
        assertEquals("Detalle", parseApiErrorMessage(400, json, """{"detail":"Detalle"}"""))
    }

    @Test fun `traduce los errores conocidos en ingles`() {
        assertEquals("Email o contraseña incorrectos.", parseApiErrorMessage(401, json, """{"error":"Invalid credentials"}"""))
        assertEquals("La contraseña actual no es correcta.", parseApiErrorMessage(400, json, """{"error":"invalidCurrentPassword"}"""))
        assertEquals("Ya existe una cuenta con este email.", parseApiErrorMessage(400, json, """{"error":"Email already registered"}"""))
    }

    @Test fun `token caducado depende del estado`() {
        val body = """{"error":"Invalid or expired token"}"""
        assertEquals("Tu sesión ha caducado. Vuelve a iniciar sesión.", parseApiErrorMessage(403, json, body))
        assertEquals("El enlace no es válido o ha caducado.", parseApiErrorMessage(400, json, body))
    }

    @Test fun `mensaje del servidor en espanol se respeta`() {
        val msg = "Demasiados intentos. Por favor, espera 15 minutos."
        assertEquals(msg, parseApiErrorMessage(429, json, """{"error":"$msg"}"""))
    }

    @Test fun `json sin campos conocidos da mensaje generico por estado`() {
        assertEquals(
            "Ha ocurrido un error en el servidor. Inténtalo de nuevo en un momento.",
            parseApiErrorMessage(500, json, "{}"),
        )
        assertEquals("No hemos encontrado lo que buscas.", parseApiErrorMessage(404, json, "{}"))
    }

    @Test fun `texto plano corto se muestra y el largo o vacio se sustituye`() {
        assertEquals("Servicio en mantenimiento", parseApiErrorMessage(503, "text/plain", "Servicio en mantenimiento"))
        assertEquals(
            "Ha ocurrido un error en el servidor. Inténtalo de nuevo en un momento.",
            parseApiErrorMessage(502, "text/html", "<html>" + "x".repeat(300)),
        )
        assertEquals(
            "Ha ocurrido un error en el servidor. Inténtalo de nuevo en un momento.",
            parseApiErrorMessage(500, null, null),
        )
    }

    @Test fun `json corrupto no lanza`() {
        assertEquals("No se pudo completar la acción. Inténtalo de nuevo.", parseApiErrorMessage(400, json, "{no es json"))
    }
}
