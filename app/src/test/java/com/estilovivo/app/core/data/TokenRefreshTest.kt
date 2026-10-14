package com.estilovivo.app.core.data

import com.estilovivo.app.core.data.net.ApiConfig
import com.estilovivo.app.core.data.net.ApiService
import com.estilovivo.app.core.data.net.LoginRequest
import com.estilovivo.app.core.data.net.NetworkFactory
import com.estilovivo.app.core.data.session.AuthSession
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

/** Sesión en memoria para probar la capa de red sin Android. */
private class FakeSession(override var accessToken: String?, override var refreshToken: String?) : AuthSession {
    var expired = 0
    override fun saveTokens(access: String, refresh: String?) {
        accessToken = access
        if (refresh != null) refreshToken = refresh
    }

    override fun expire() {
        expired++
        accessToken = null
        refreshToken = null
    }
}

class TokenRefreshTest {
    private lateinit var server: MockWebServer
    private lateinit var config: ApiConfig

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        config = ApiConfig(server.url("/").toString())
    }

    @After fun tearDown() {
        server.close()
    }

    private fun api(session: AuthSession): ApiService =
        NetworkFactory.service(NetworkFactory.retrofit(NetworkFactory.apiClient(session, config, debug = false), config))

    private fun json(code: Int, body: String) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    private val user = """{"id":"u1","name":"Ana"}"""

    @Test fun `403 token caducado renueva y reintenta una vez con el token nuevo`() = runBlocking {
        val session = FakeSession("old", "r1")
        server.enqueue(json(403, """{"error":"Invalid or expired token"}"""))
        server.enqueue(json(200, """{"token":"new","refreshToken":"r2","user":$user}"""))
        server.enqueue(json(200, user))

        val me = api(session).me()

        assertEquals("u1", me.id)
        assertEquals("new", session.accessToken)
        assertEquals("r2", session.refreshToken) // rotación
        assertEquals(0, session.expired)

        val first = server.takeRequest()
        assertEquals("Bearer old", first.headers["Authorization"])
        val refresh = server.takeRequest()
        assertEquals("/api/auth/refresh", refresh.url.encodedPath)
        assertTrue(refresh.body!!.utf8().contains("\"refreshToken\":\"r1\""))
        assertNull(refresh.headers["Authorization"])
        val retry = server.takeRequest()
        assertEquals("Bearer new", retry.headers["Authorization"])
        assertEquals(3, server.requestCount)
    }

    @Test fun `401 tambien dispara el refresco`() = runBlocking {
        val session = FakeSession("old", "r1")
        server.enqueue(json(401, """{"error":"Token required"}"""))
        server.enqueue(json(200, """{"token":"new","refreshToken":"r2"}"""))
        server.enqueue(json(200, user))
        api(session).me()
        assertEquals("new", session.accessToken)
        assertEquals(3, server.requestCount)
    }

    @Test fun `si el refresh es rechazado se limpia la sesion y se emite expiracion`() = runBlocking {
        val session = FakeSession("old", "r1")
        server.enqueue(json(403, """{"error":"Invalid or expired token"}"""))
        server.enqueue(json(401, """{"error":"Invalid refresh token"}"""))

        val error = runCatching { api(session).me() }.exceptionOrNull()

        assertTrue(error is HttpException)
        assertEquals(1, session.expired)
        assertNull(session.accessToken)
        assertEquals(2, server.requestCount) // sin bucle
    }

    @Test fun `si la peticion reintentada vuelve a fallar se expira la sesion sin segundo refresh`() = runBlocking {
        val session = FakeSession("old", "r1")
        server.enqueue(json(403, """{"error":"Invalid or expired token"}"""))
        server.enqueue(json(200, """{"token":"new","refreshToken":"r2"}"""))
        server.enqueue(json(403, """{"error":"Invalid or expired token"}"""))

        val error = runCatching { api(session).me() }.exceptionOrNull()

        assertTrue(error is HttpException)
        assertEquals(1, session.expired)
        assertEquals(3, server.requestCount)
    }

    @Test fun `un fallo de red al refrescar no cierra la sesion`() = runBlocking {
        val session = FakeSession("old", "r1")
        server.enqueue(json(403, """{"error":"Invalid or expired token"}"""))
        server.enqueue(json(503, """{"error":"down"}"""))

        runCatching { api(session).me() }

        assertEquals(0, session.expired)
        assertEquals("r1", session.refreshToken)
    }

    @Test fun `el login fallido no intenta refrescar`() = runBlocking {
        val session = FakeSession(null, "r1")
        server.enqueue(json(401, """{"error":"Invalid credentials"}"""))

        val error = runCatching { api(session).login(LoginRequest("a@b.c", "x")) }.exceptionOrNull()

        assertTrue(error is HttpException)
        assertEquals(1, server.requestCount)
        assertEquals(0, session.expired)
    }

    @Test fun `un 403 que no es de token no se toca`() = runBlocking {
        val session = FakeSession("old", "r1")
        server.enqueue(json(403, """{"error":"Not authorized"}"""))

        val error = runCatching { api(session).me() }.exceptionOrNull() as HttpException

        assertEquals(403, error.code())
        assertEquals(1, server.requestCount)
        assertEquals(0, session.expired)
    }

    @Test fun `sin refresh token un 401 expira la sesion`() = runBlocking {
        val session = FakeSession("old", null)
        server.enqueue(json(401, """{"error":"Token required"}"""))

        runCatching { api(session).me() }

        assertEquals(1, session.expired)
        assertEquals(1, server.requestCount)
    }

    @Test fun `el token solo se envia a la API propia`() = runBlocking {
        val session = FakeSession("tok", "r1")
        server.enqueue(json(200, user))
        // config apunta a otro host: la peticion no debe llevar la cabecera
        val otherConfig = ApiConfig("http://otro.invalid")
        val client = NetworkFactory.apiClient(session, otherConfig, debug = false)
        client.newCall(okhttp3.Request.Builder().url(server.url("/api/auth/me")).build()).execute().close()
        assertNull(server.takeRequest().headers["Authorization"])
    }
}
