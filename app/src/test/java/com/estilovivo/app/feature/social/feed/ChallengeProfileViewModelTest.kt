package com.estilovivo.app.feature.social.feed

import com.estilovivo.app.core.data.ApiErrorKind
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.model.ChallengeSubmission
import com.estilovivo.app.core.data.model.FollowResult
import com.estilovivo.app.core.data.model.Trend
import com.estilovivo.app.core.data.model.SubmitResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChallengeViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val repo = FakeChallengeRepository()
    private val me = FakeMe()

    private fun <T> TestScope.collect(flow: Flow<T>): MutableList<T> {
        val out = mutableListOf<T>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.toList(out) }
        return out
    }

    private fun submission(reward: Int = 50) = ChallengeSubmission("s1", "ch1", "https://x/i.jpg", "desc", verified = true, awarded = true, createdAt = "")

    private fun result(leveledUp: Boolean = false, msg: String? = null, level: Int = 2) =
        SubmitResult(submission(), experiencePoints = 120, level = level, leveledUp = leveledUp, validationMessage = msg)

    @Test fun `la validacion exige foto y entre 10 y 500 caracteres`() {
        assertEquals("Elige una foto de tu look para participar.", challengeSubmitError(false, "descripción válida"))
        assertNotNull(challengeSubmitError(true, ""))
        assertNotNull(challengeSubmitError(true, "         corto "))
        assertNotNull(challengeSubmitError(true, "x".repeat(CHALLENGE_DESC_MAX + 1)))
        assertNull(challengeSubmitError(true, "  1234567890  "))
        assertNull(challengeSubmitError(true, "x".repeat(CHALLENGE_DESC_MAX)))
    }

    @Test fun `no se envia si falla la validacion y el error se muestra en el formulario`() = runTest {
        val vm = ChallengeViewModel(repo, me)
        vm.submit(upload(), "corta")
        assertEquals(0, repo.submitCalls)
        assertEquals("La descripción debe tener al menos 10 caracteres.", vm.state.value.formError)
        vm.submit(null, "Una descripción suficiente")
        assertEquals(0, repo.submitCalls)
        assertEquals("Elige una foto de tu look para participar.", vm.state.value.formError)
    }

    @Test fun `participar con exito marca completado, lanza confeti, avisa del XP y refresca el usuario`() = runTest {
        repo.submitHandler = { _, _ -> ApiResult.Success(result()) }
        val vm = ChallengeViewModel(repo, me)
        val events = collect(vm.events)
        vm.submit(upload(), "  Combiné rojo y azul con un cinturón  ")
        assertNotNull(vm.state.value.submission)
        assertFalse(vm.state.value.submitting)
        assertTrue(events.any { it is FeedEvent.Confetti })
        assertTrue(events.any { it is FeedEvent.Published })
        assertTrue(events.any { it is FeedEvent.Toast && it.text.contains("+50 XP") })
        assertEquals(1, me.refreshCount)
        // No se puede participar dos veces
        vm.submit(upload(), "Otra participación distinta")
        assertEquals(1, repo.submitCalls)
    }

    @Test fun `subir de nivel muestra un aviso especial`() = runTest {
        repo.submitHandler = { _, _ -> ApiResult.Success(result(leveledUp = true, level = 3)) }
        val vm = ChallengeViewModel(repo, me)
        val events = collect(vm.events)
        vm.submit(upload(), "Look completo con accesorios")
        assertTrue(events.any { it is FeedEvent.Toast && it.text.contains("nivel 3") })
    }

    @Test fun `el aviso de color no uniforme del servidor se muestra en lugar del generico`() = runTest {
        repo.submitHandler = { _, _ -> ApiResult.Success(result(msg = "La imagen no parece tener una gama uniforme")) }
        val vm = ChallengeViewModel(repo, me)
        val events = collect(vm.events)
        vm.submit(upload(), "Look monocromático de hoy")
        assertTrue(events.any { it is FeedEvent.Toast && it.text.startsWith("La imagen no parece") })
    }

    @Test fun `un error del servidor queda en el formulario y no completa el reto`() = runTest {
        repo.submitHandler = { _, _ -> failure("Este reto ya no está activo") }
        val vm = ChallengeViewModel(repo, me)
        vm.submit(upload(), "Descripción suficientemente larga")
        assertEquals("Este reto ya no está activo", vm.state.value.formError)
        assertNull(vm.state.value.submission)
        assertFalse(vm.state.value.submitting)
        assertEquals(0, me.refreshCount)
    }

    @Test fun `si el servidor dice que ya participaste se sincroniza el estado`() = runTest {
        repo.submitHandler = { _, _ ->
            repo.mine = listOf(submission())
            failure("Ya participaste en este reto")
        }
        val vm = ChallengeViewModel(repo, me)
        vm.submit(upload(), "Descripción suficientemente larga")
        assertNotNull(vm.state.value.submission)
    }

    @Test fun `al abrir detecta mi participacion existente y se puede eliminar`() = runTest {
        repo.mine = listOf(submission())
        val vm = ChallengeViewModel(repo, me)
        assertNotNull(vm.state.value.submission)
        vm.deleteSubmission()
        assertNull(vm.state.value.submission)
        assertEquals(1, me.refreshCount) // se restan los XP
    }

    @Test fun `el doble toque en enviar no duplica la participacion`() = runTest {
        val gate = CompletableDeferred<ApiResult<SubmitResult>>()
        repo.submitHandler = { _, _ -> gate.await() }
        val vm = ChallengeViewModel(repo, me)
        vm.submit(upload(), "Descripción suficientemente larga")
        vm.submit(upload(), "Descripción suficientemente larga")
        assertEquals(1, repo.submitCalls)
        gate.complete(ApiResult.Success(result()))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class UserProfileViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val social = FakeSocialRepository()
    private val chat = FakeChatRepository()
    private val me = FakeMe(user("me"))

    private fun <T> TestScope.collect(flow: Flow<T>): MutableList<T> {
        val out = mutableListOf<T>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.toList(out) }
        return out
    }

    private fun vm(userId: String = "u-other") = UserProfileViewModel(userId, social, chat, me)

    @Test fun `etiquetas del boton segun el estado`() {
        assertEquals(FollowButton.Follow, followButton(false, true, false, false))
        assertEquals(FollowButton.Request, followButton(false, false, false, false))
        assertEquals(FollowButton.Following, followButton(false, false, true, false))
        assertEquals(FollowButton.Requested, followButton(false, false, false, true))
        assertEquals(FollowButton.Hidden, followButton(true, true, false, false))
        assertEquals("Solicitud enviada", FollowButton.Requested.label)
    }

    @Test fun `perfil publico seguir y dejar de seguir ajusta el contador`() = runTest {
        social.profileHandler = { ApiResult.Success(profile(isPublic = true, followers = 10)) }
        val vm = vm()
        assertEquals(FollowButton.Follow, vm.state.value.button)
        vm.toggleFollow()
        assertTrue(vm.state.value.following)
        assertEquals(11, vm.state.value.followersCount)
        social.followHandler = { ApiResult.Success(FollowResult(following = false, requesting = false)) }
        vm.toggleFollow()
        assertFalse(vm.state.value.following)
        assertEquals(10, vm.state.value.followersCount)
        assertEquals(2, social.followCalls.size)
    }

    @Test fun `perfil privado la primera pulsacion crea la solicitud y la segunda la cancela`() = runTest {
        social.profileHandler = { ApiResult.Success(profile(isPublic = false, followers = 10)) }
        val vm = vm()
        assertEquals(FollowButton.Request, vm.state.value.button)
        assertTrue(vm.state.value.locked)
        social.followHandler = { ApiResult.Success(FollowResult(following = false, requesting = true)) }
        vm.toggleFollow()
        assertEquals(FollowButton.Requested, vm.state.value.button)
        assertEquals(10, vm.state.value.followersCount) // una solicitud no suma seguidores
        assertTrue(vm.state.value.locked)
        // Segundo toque: el servidor cancela la solicitud
        social.followHandler = { ApiResult.Success(FollowResult(following = false, requesting = false)) }
        vm.toggleFollow()
        assertEquals(FollowButton.Request, vm.state.value.button)
        assertEquals(10, vm.state.value.followersCount)
    }

    @Test fun `con solicitud pendiente cargada del servidor el boton ya indica solicitud enviada`() = runTest {
        social.profileHandler = { ApiResult.Success(profile(isPublic = false, pending = true)) }
        assertEquals(FollowButton.Requested, vm().state.value.button)
    }

    @Test fun `dejar de seguir un perfil privado recarga y oculta el contenido`() = runTest {
        var loads = 0
        social.profileHandler = {
            loads++
            ApiResult.Success(if (loads == 1) profile(isPublic = false, following = true) else profile(isPublic = false, following = false))
        }
        val vm = vm()
        assertFalse(vm.state.value.locked)
        social.followHandler = { ApiResult.Success(FollowResult(following = false, requesting = false)) }
        vm.toggleFollow()
        assertEquals(2, loads)
        assertTrue(vm.state.value.locked)
        assertEquals(10, vm.state.value.followersCount) // manda el contador recargado del servidor
    }

    @Test fun `el doble toque en seguir solo envia una peticion`() = runTest {
        social.profileHandler = { ApiResult.Success(profile()) }
        val gate = CompletableDeferred<ApiResult<FollowResult>>()
        social.followHandler = { gate.await() }
        val vm = vm()
        vm.toggleFollow()
        vm.toggleFollow()
        assertEquals(1, social.followCalls.size)
        gate.complete(ApiResult.Success(FollowResult(true, false)))
        assertTrue(vm.state.value.following)
    }

    @Test fun `en mi propio perfil no se puede seguir`() = runTest {
        social.profileHandler = { ApiResult.Success(profile(id = "me")) }
        val vm = vm("me")
        assertEquals(FollowButton.Hidden, vm.state.value.button)
        vm.toggleFollow()
        assertTrue(social.followCalls.isEmpty())
    }

    @Test fun `perfil inexistente muestra un mensaje claro`() = runTest {
        social.profileHandler = { failure("Not found", ApiErrorKind.NotFound) }
        val vm = vm()
        assertTrue(vm.state.value.notFound)
        assertNotNull(vm.state.value.error)
        assertNull(vm.state.value.profile)
    }

    @Test fun `un fallo de red al seguir avisa y deja el estado intacto`() = runTest {
        social.profileHandler = { ApiResult.Success(profile()) }
        social.followHandler = { failure("Sin conexión") }
        val vm = vm()
        val events = collect(vm.events)
        vm.toggleFollow()
        assertFalse(vm.state.value.following)
        assertFalse(vm.state.value.followBusy)
        assertTrue(events.any { it is ProfileEvent.Toast && it.isError })
    }

    @Test fun `mensaje abre la conversacion con la persona`() = runTest {
        social.profileHandler = { ApiResult.Success(profile()) }
        var target: String? = null
        chat.createHandler = { target = it; ApiResult.Success(com.estilovivo.app.core.data.model.Conversation(id = "conv-9")) }
        val vm = vm()
        val events = collect(vm.events)
        vm.message()
        assertEquals("u-other", target)
        assertTrue(events.any { it is ProfileEvent.OpenChat && it.conversationId == "conv-9" })
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TrendsViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val repo = FakeTrendsRepository()

    private fun trend(id: String, cat: String) = Trend(id, "T$id", cat, "d", "https://x/i.jpg", emptyList(), "Vogue", null)

    @Test fun `filtra por categoria y vuelve a Todo si la categoria desaparece`() = runTest {
        repo.result = ApiResult.Success(listOf(trend("1", "Calle"), trend("2", "Gala"), trend("3", "Calle")))
        val vm = TrendsViewModel(repo)
        assertEquals(listOf("Todo", "Calle", "Gala"), vm.state.value.categories)
        vm.selectCategory("Calle")
        assertEquals(listOf("1", "3"), vm.state.value.visible.map { it.id })
        repo.result = ApiResult.Success(listOf(trend("2", "Gala")))
        vm.refresh()
        assertEquals(ALL_TRENDS, vm.state.value.category)
        assertEquals(listOf(true), repo.forceFlags.drop(1))
    }

    @Test fun `error con datos previos mantiene la lista`() = runTest {
        repo.result = ApiResult.Success(listOf(trend("1", "Calle")))
        val vm = TrendsViewModel(repo)
        repo.result = failure("Sin conexión")
        vm.refresh()
        assertEquals(1, vm.state.value.trends.size)
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.refreshing)
    }

    @Test fun `error inicial se muestra y reintentar recupera`() = runTest {
        repo.result = failure("Sin conexión")
        val vm = TrendsViewModel(repo)
        assertEquals("Sin conexión", vm.state.value.error)
        repo.result = ApiResult.Success(listOf(trend("1", "Calle")))
        vm.load()
        assertNull(vm.state.value.error)
        assertEquals(1, vm.state.value.trends.size)
    }
}
