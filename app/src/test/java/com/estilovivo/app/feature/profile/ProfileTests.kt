package com.estilovivo.app.feature.profile

import com.estilovivo.app.core.data.ApiError
import com.estilovivo.app.core.data.ApiErrorKind
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.model.AuthOutcome
import com.estilovivo.app.core.data.model.Favorite
import com.estilovivo.app.core.data.model.FollowRequest
import com.estilovivo.app.core.data.model.FollowResult
import com.estilovivo.app.core.data.model.LikeResult
import com.estilovivo.app.core.data.model.PreferencesUpdate
import com.estilovivo.app.core.data.model.ProfileUpdate
import com.estilovivo.app.core.data.model.PublicProfile
import com.estilovivo.app.core.data.model.RegisterOutcome
import com.estilovivo.app.core.data.model.TopUser
import com.estilovivo.app.core.data.model.User
import com.estilovivo.app.core.data.model.UserSummary
import com.estilovivo.app.core.data.model.Comment
import com.estilovivo.app.core.data.repo.AuthRepository
import com.estilovivo.app.core.data.repo.SocialRepository
import com.estilovivo.app.core.data.session.AppSettings
import com.estilovivo.app.core.data.session.ThemeMode
import com.estilovivo.app.core.util.CyclePeriod
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

private fun fail(msg: String) = ApiResult.Failure(ApiError(msg, ApiErrorKind.Network))

private fun me(gender: String? = "female", public: Boolean = true) =
    User(id = "me", name = "Ana", gender = gender, isProfilePublic = public, cycleTracking = gender == "female")

class FakeAuth(initial: User? = me()) : AuthRepository {
    val user = MutableStateFlow(initial)
    override val currentUser: StateFlow<User?> get() = user
    var prefsResult: (PreferencesUpdate) -> ApiResult<User>? = { null }
    var passwordResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var deleteResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var gate: CompletableDeferred<Unit>? = null
    val prefCalls = mutableListOf<PreferencesUpdate>()
    var loggedOut = 0
    var deleteCalls = 0
    var passwordCalls = 0

    override suspend fun updatePreferences(update: PreferencesUpdate): ApiResult<User> {
        prefCalls += update
        gate?.await()
        prefsResult(update)?.let { return it as ApiResult<User> }
        val u = user.value!!
        val n = u.copy(
            isProfilePublic = update.isProfilePublic ?: u.isProfilePublic,
            cycleTracking = update.cycleTracking ?: u.cycleTracking,
            emailNotifications = update.emailNotifications ?: u.emailNotifications,
            emailChat = update.emailChat ?: u.emailChat,
            emailFollows = update.emailFollows ?: u.emailFollows,
            emailWashing = update.emailWashing ?: u.emailWashing,
            emailChallenges = update.emailChallenges ?: u.emailChallenges,
            locationName = update.locationName ?: u.locationName,
        )
        user.value = n
        return ApiResult.Success(n)
    }

    override suspend fun logout() { loggedOut++; user.value = null }
    override suspend fun deleteAccount(): ApiResult<Unit> { deleteCalls++; return deleteResult }
    override suspend fun changePassword(currentPassword: String, newPassword: String): ApiResult<Unit> { passwordCalls++; return passwordResult }
    override suspend fun refreshMe(): ApiResult<User> = ApiResult.Success(user.value!!)
    override suspend fun updateProfile(update: ProfileUpdate): ApiResult<User> {
        val u = user.value!!
        val n = u.copy(name = update.name ?: u.name, bio = update.bio ?: u.bio, gender = update.gender ?: u.gender)
        user.value = n
        return ApiResult.Success(n)
    }
    override suspend fun login(email: String, password: String, remember: Boolean): ApiResult<AuthOutcome> = error("unused")
    override suspend fun register(email: String, password: String, name: String, gender: String?, birthDate: String?, remember: Boolean): ApiResult<RegisterOutcome> = error("unused")
    override suspend fun forgotPassword(email: String): ApiResult<Unit> = error("unused")
    override suspend fun resetPassword(token: String, newPassword: String): ApiResult<Unit> = error("unused")
    override suspend fun verifyEmail(token: String): ApiResult<Unit> = error("unused")
    override suspend fun resendVerification(email: String): ApiResult<Unit> = error("unused")
}

class FakeSocial(var requests: List<FollowRequest> = emptyList()) : SocialRepository {
    var approveResult: ApiResult<Unit> = ApiResult.Success(Unit)
    override suspend fun getFollowRequests(): ApiResult<List<FollowRequest>> = ApiResult.Success(requests)
    override suspend fun approveFollowRequest(requestId: String) = approveResult
    override suspend fun rejectFollowRequest(requestId: String) = ApiResult.Success(Unit)
    override suspend fun toggleLike(lookId: String): ApiResult<LikeResult> = error("unused")
    override suspend fun getComments(lookId: String): ApiResult<List<Comment>> = error("unused")
    override suspend fun addComment(lookId: String, content: String, parentId: String?): ApiResult<Comment> = error("unused")
    override suspend fun deleteComment(commentId: String): ApiResult<Unit> = error("unused")
    override suspend fun toggleLookFavorite(lookId: String): ApiResult<Boolean> = error("unused")
    override suspend fun toggleGarmentFavorite(garmentId: String): ApiResult<Boolean> = error("unused")
    override suspend fun getFavorites(): ApiResult<List<Favorite>> = ApiResult.Success(emptyList())
    override suspend fun toggleFollow(userId: String): ApiResult<FollowResult> = error("unused")
    override suspend fun getUserProfile(userId: String): ApiResult<PublicProfile> = error("unused")
    override suspend fun getTopUsers(forceRefresh: Boolean): ApiResult<List<TopUser>> = error("unused")
}

class FakeLocal : LocalSettings {
    val state = MutableStateFlow(AppSettings())
    override val settings: Flow<AppSettings> get() = state
    override suspend fun setThemeMode(mode: ThemeMode) { state.value = state.value.copy(themeMode = mode) }
    override suspend fun setFontScale(value: Float) { state.value = state.value.copy(fontScale = value) }
    override suspend fun setHighContrast(value: Boolean) { state.value = state.value.copy(highContrast = value) }
    override suspend fun setHaptics(value: Boolean) { state.value = state.value.copy(haptics = value) }
}

class FakeCycle : CycleStorage {
    private val map = mutableMapOf<String, CyclePeriod>()
    private val _changes = MutableStateFlow(0)
    override val changes: StateFlow<Int> get() = _changes
    override fun get(userId: String?): CyclePeriod? = userId?.let { map[it] }
    override fun save(userId: String, period: CyclePeriod?): Boolean {
        if (period == null) map.remove(userId) else map[userId] = period
        _changes.value++
        return true
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileTests {
    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    // ── Fórmula de nivel ─────────────────────────────────────────────────────────

    @Test fun `xp acumulado por nivel sigue la formula del servidor`() {
        assertEquals(100, ProfileRules.cumulativeXp(1))
        assertEquals(300, ProfileRules.cumulativeXp(2))
        assertEquals(600, ProfileRules.cumulativeXp(3))
    }

    @Test fun `nivel y progreso desde el xp`() {
        assertEquals(1, ProfileRules.levelFromXp(0))
        assertEquals(1, ProfileRules.levelFromXp(99))
        assertEquals(2, ProfileRules.levelFromXp(100))
        assertEquals(2, ProfileRules.levelFromXp(299))
        assertEquals(3, ProfileRules.levelFromXp(300))
        val p = ProfileRules.progress(150) // nivel 2: 100..300
        assertEquals(2, p.level); assertEquals(50, p.current); assertEquals(200, p.needed)
        assertEquals(0.25f, p.fraction, 0.0001f); assertEquals(150, p.remaining.let { 200 - 50 })
        assertEquals(0, ProfileRules.progress(-5).current)
    }

    // ── Emails ──────────────────────────────────────────────────────────────────

    @Test fun `el maestro sincroniza los cuatro y los tipos mantienen el maestro coherente`() {
        val all = ProfileRules.EmailPrefs(true, true, true, true, true)
        val off = ProfileRules.applyEmailToggle(all, ProfileRules.EmailKey.Master, false)
        assertEquals(ProfileRules.EmailPrefs(false, false, false, false, false), off)
        val chatOn = ProfileRules.applyEmailToggle(off, ProfileRules.EmailKey.Chat, true)
        assertTrue(chatOn.master && chatOn.chat && !chatOn.follows)
        val lastOff = ProfileRules.applyEmailToggle(chatOn, ProfileRules.EmailKey.Chat, false)
        assertFalse(lastOff.master)
    }

    // ── Ajustes: optimista y rollback ───────────────────────────────────────────

    private fun settingsVm(auth: FakeAuth = FakeAuth(), cycle: FakeCycle = FakeCycle(), local: FakeLocal = FakeLocal()) =
        SettingsViewModel(auth, FakeSocial(), local, cycle)

    @Test fun `perfil privado es optimista y se confirma`() = runTest {
        val auth = FakeAuth().apply { gate = CompletableDeferred() }
        val vm = settingsVm(auth)
        vm.setProfilePublic(false)
        assertFalse(vm.state.value.isPublic) // al instante
        assertTrue("public" in vm.state.value.saving)
        auth.gate!!.complete(Unit)
        assertFalse(vm.state.value.isPublic)
        assertTrue(vm.state.value.saving.isEmpty())
    }

    @Test fun `si el guardado falla vuelve al valor real y avisa con el mensaje`() = runTest {
        val auth = FakeAuth().apply { prefsResult = { fail("Sin conexión") } }
        val vm = settingsVm(auth)
        vm.setProfilePublic(false)
        assertTrue(vm.state.value.isPublic)
        assertEquals(ProfileEvent.Error("Sin conexión"), vm.events.first())
    }

    @Test fun `el maestro de emails envia los cuatro valores`() = runTest {
        val auth = FakeAuth()
        val vm = settingsVm(auth)
        vm.setEmail(ProfileRules.EmailKey.Master, false)
        val call = auth.prefCalls.single()
        assertEquals(false, call.emailNotifications); assertEquals(false, call.emailChat)
        assertEquals(false, call.emailFollows); assertEquals(false, call.emailWashing); assertEquals(false, call.emailChallenges)
        assertFalse(vm.state.value.emails.master)
    }

    @Test fun `doble toque mientras guarda no duplica la peticion`() = runTest {
        val auth = FakeAuth().apply { gate = CompletableDeferred() }
        val vm = settingsVm(auth)
        vm.setProfilePublic(false); vm.setProfilePublic(true)
        assertEquals(1, auth.prefCalls.size)
        auth.gate!!.complete(Unit)
    }

    // ── Ciclo ───────────────────────────────────────────────────────────────────

    @Test fun `el ciclo solo esta disponible para genero femenino`() = runTest {
        assertTrue(ProfileRules.canTrackCycle("female"))
        assertFalse(ProfileRules.canTrackCycle("male")); assertFalse(ProfileRules.canTrackCycle(null))
        val male = settingsVm(FakeAuth(me("male")))
        assertFalse(male.state.value.cycleAvailable); assertFalse(male.state.value.cycleTracking)
        male.setCycleTracking(true)
        assertTrue(male.events.first() is ProfileEvent.Error)
    }

    @Test fun `desactivar el ciclo borra el periodo y valida fechas`() = runTest {
        val cycle = FakeCycle()
        val vm = settingsVm(FakeAuth(), cycle)
        assertNull(vm.saveCyclePeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5)))
        assertNotNull(cycle.get("me"))
        assertNotNull(vm.saveCyclePeriod(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 1)))
        vm.setCycleTracking(false)
        assertNull(cycle.get("me"))
    }

    // ── Contraseña ──────────────────────────────────────────────────────────────

    @Test fun `validacion de contraseña`() {
        assertTrue(ProfileRules.validatePassword("", "12345678", "12345678").current != null)
        assertTrue(ProfileRules.validatePassword("abc", "short", "short").new != null)
        assertTrue(ProfileRules.validatePassword("12345678", "12345678", "12345678").new != null) // igual a la actual
        assertTrue(ProfileRules.validatePassword("oldpass1", "newpass12", "otra").confirm != null)
        assertFalse(ProfileRules.validatePassword("oldpass1", "newpass12", "newpass12").any)
    }

    @Test fun `cambio de contraseña no llama al servidor si es invalido y mapea el error del servidor`() = runTest {
        val auth = FakeAuth()
        val vm = ChangePasswordViewModel(auth)
        vm.onCurrent("oldpass1"); vm.onNew("short"); vm.onConfirm("short"); vm.submit()
        assertEquals(0, auth.passwordCalls)
        vm.onNew("newpass12"); vm.onConfirm("newpass12")
        auth.passwordResult = fail("La contraseña actual no es correcta.")
        vm.submit()
        assertEquals(1, auth.passwordCalls)
        assertEquals("La contraseña actual no es correcta.", vm.state.value.errors.current)
        auth.passwordResult = ApiResult.Success(Unit)
        vm.submit()
        assertEquals("", vm.state.value.current)
    }

    // ── Eliminar cuenta ─────────────────────────────────────────────────────────

    @Test fun `eliminar cuenta exige la palabra ELIMINAR`() = runTest {
        assertTrue(ProfileRules.deleteConfirmed(" eliminar "))
        assertFalse(ProfileRules.deleteConfirmed("borrar"))
        val auth = FakeAuth()
        val cycle = FakeCycle().also { it.save("me", CyclePeriod("2026-10-01", "2026-10-05")) }
        val vm = settingsVm(auth, cycle)
        vm.confirmDelete() // sin abrir el flujo: nada
        assertEquals(0, auth.deleteCalls)
        vm.startDelete(); vm.confirmDelete()
        assertEquals(0, auth.deleteCalls) // aún en el aviso
        vm.proceedDelete(); vm.setDeleteText("nop"); vm.confirmDelete()
        assertEquals(0, auth.deleteCalls)
        vm.setDeleteText("ELIMINAR"); vm.confirmDelete()
        assertEquals(1, auth.deleteCalls)
        assertEquals(ProfileEvent.AccountDeleted, vm.events.first())
        assertNull(cycle.get("me"))
    }

    @Test fun `si falla eliminar la cuenta se queda y avisa`() = runTest {
        val auth = FakeAuth().apply { deleteResult = fail("Sin conexión") }
        val vm = settingsVm(auth)
        vm.startDelete(); vm.proceedDelete(); vm.setDeleteText("ELIMINAR"); vm.confirmDelete()
        assertEquals(ProfileEvent.Error("Sin conexión"), vm.events.first())
        assertFalse(vm.state.value.delete.deleting)
        assertEquals(DeleteFlow.Stage.Typing, vm.state.value.delete.stage)
    }

    @Test fun `cerrar sesion llama al repositorio una sola vez`() = runTest {
        val auth = FakeAuth()
        val vm = settingsVm(auth)
        vm.askLogout(); vm.confirmLogout()
        assertEquals(1, auth.loggedOut)
        assertEquals(ProfileEvent.LoggedOut, vm.events.first())
    }

    // ── Solicitudes de seguimiento ──────────────────────────────────────────────

    @Test fun `aprobar quita la fila y si falla la devuelve a su sitio`() = runTest {
        val r = listOf("a", "b", "c").map { FollowRequest(it, UserSummary(it, "User $it"), "pending", "") }
        val social = FakeSocial(r)
        val vm = FollowRequestsViewModel(social, FakeAuth())
        assertEquals(3, vm.state.value.requests.size)
        social.approveResult = fail("Sin conexión")
        vm.approve("b")
        assertEquals(listOf("a", "b", "c"), vm.state.value.requests.map { it.id })
        social.approveResult = ApiResult.Success(Unit)
        vm.approve("b")
        assertEquals(listOf("a", "c"), vm.state.value.requests.map { it.id })
    }

    // ── Ajustes locales ─────────────────────────────────────────────────────────

    @Test fun `ajustes locales de accesibilidad se guardan y el tamano se limita`() = runTest {
        val local = FakeLocal()
        val vm = settingsVm(local = local)
        vm.setHighContrast(true); vm.setHaptics(false); vm.setFontScale(3f); vm.setThemeMode(ThemeMode.Dark)
        val s = vm.state.value.local
        assertTrue(s.highContrast); assertFalse(s.haptics); assertEquals(1.5f, s.fontScale, 0f); assertEquals(ThemeMode.Dark, s.themeMode)
    }

    // ── Estadísticas ────────────────────────────────────────────────────────────

    @Test fun `recomendaciones y fechas`() {
        val stats = com.estilovivo.app.core.data.model.Stats(0, 0, 0, 0, 0, null, 0, emptyList())
        assertEquals(1, StatsInsights.recommendations(stats, emptyList()).size)
        assertNull(ProfileRules.validateBirthDate("2000-01-31", LocalDate.of(2026, 1, 1)))
        assertNotNull(ProfileRules.validateBirthDate("2030-01-01", LocalDate.of(2026, 1, 1)))
        assertNotNull(ProfileRules.validateName("A")); assertNull(ProfileRules.validateName("Ana"))
    }
}
