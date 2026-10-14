package com.estilovivo.app.feature.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `avanza por carrusel y quiz y respeta los topes`() = runTest {
        val vm = OnboardingViewModel(FakeAuthRepo(), FakeStore())
        repeat(INTRO_PAGES) { vm.next() }
        assertTrue(vm.state.value.inQuiz)
        assertEquals(QuizKind.Colors, vm.state.value.kind)
        listOf("black", "white", "blue", "red", "green", "pink").forEach { vm.toggle(QuizKind.Colors, it) }
        assertEquals(5, vm.state.value.picks(QuizKind.Colors).size)
        assertFalse("pink" in vm.state.value.picks(QuizKind.Colors))
        vm.toggle(QuizKind.Colors, "black")
        assertEquals(4, vm.state.value.picks(QuizKind.Colors).size)
    }

    @Test fun `saltar el carrusel va al quiz y atras vuelve`() = runTest {
        val vm = OnboardingViewModel(FakeAuthRepo(), FakeStore())
        vm.skipIntro()
        assertEquals(INTRO_PAGES, vm.state.value.step)
        vm.back()
        assertEquals(INTRO_PAGES - 1, vm.state.value.step)
    }

    @Test fun `completar guarda preferencias, marca onboarding y emite Done`() = runTest {
        val auth = FakeAuthRepo()
        val store = FakeStore()
        val vm = OnboardingViewModel(auth, store)
        vm.goTo(TOTAL_STEPS - 1)
        vm.toggle(QuizKind.Colors, "red")
        vm.toggle(QuizKind.Styles, "casual")
        vm.toggle(QuizKind.Fabrics, "wool")
        vm.next()
        val up = auth.prefsUpdates.single()
        assertEquals(listOf("red"), up.styleColors)
        assertEquals(listOf("casual"), up.styleStyles)
        assertEquals(emptyList<String>(), up.styleOccasions)
        assertEquals(listOf("wool"), up.styleFabrics)
        assertTrue(store.done)
        assertEquals(OnboardingEffect.Done, vm.effects.first())
        assertTrue(vm.state.value.finished)
    }

    @Test fun `fallo al guardar permite reintentar o seguir sin guardar`() = runTest {
        val auth = FakeAuthRepo()
        val store = FakeStore()
        auth.prefsResult = fail("No hay conexión con el servidor.")
        val vm = OnboardingViewModel(auth, store)
        vm.goTo(TOTAL_STEPS - 1)
        vm.toggle(QuizKind.Styles, "casual")
        vm.next()
        assertNotNull(vm.state.value.error)
        assertFalse(store.done)
        assertFalse(vm.state.value.saving)
        vm.skipAll()
        assertTrue(store.done)
        assertEquals(OnboardingEffect.Done, vm.effects.first())
    }

    @Test fun `sin selecciones no llama al servidor`() = runTest {
        val auth = FakeAuthRepo()
        val vm = OnboardingViewModel(auth, FakeStore())
        vm.goTo(TOTAL_STEPS - 1)
        vm.next()
        assertTrue(auth.prefsUpdates.isEmpty())
        assertEquals(OnboardingEffect.Done, vm.effects.first())
    }
}
