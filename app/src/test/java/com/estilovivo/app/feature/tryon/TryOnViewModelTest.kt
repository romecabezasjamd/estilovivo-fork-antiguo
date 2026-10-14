package com.estilovivo.app.feature.tryon

import androidx.compose.ui.graphics.ImageBitmap
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.ImageUpload
import com.estilovivo.app.core.data.model.ProfileUpdate
import com.estilovivo.app.core.data.model.TryonLayer
import com.estilovivo.app.core.data.model.TryonPreset
import com.estilovivo.app.core.data.model.User
import com.estilovivo.app.core.data.repo.AuthRepository
import com.estilovivo.app.core.data.repo.TryonPresetRepository
import com.estilovivo.app.core.image.AlphaMask
import com.estilovivo.app.core.image.CutoutModelStatus
import com.estilovivo.app.feature.wardrobe.FakeGarmentRepo
import com.estilovivo.app.feature.wardrobe.FakeLookRepo
import com.estilovivo.app.feature.wardrobe.failure
import com.estilovivo.app.feature.wardrobe.garment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy
import kotlin.random.Random

private class FakeAssets : TryOnAssets {
    override val revision: StateFlow<Int> = MutableStateFlow(0)
    override val cutoutStatus: StateFlow<CutoutModelStatus> = MutableStateFlow(CutoutModelStatus.Ready)
    override val canSaveToGallery = true
    var pose: BodyLandmarks? = null
    var garmentOk = true
    var cutoutOk = true
    var renderFile: File? = File.createTempFile("tryon", ".jpg")
    val cutoutCalls = mutableListOf<String>()
    var released = false

    override suspend fun importBody(source: String) = BodyInfo(source, 0.75f)
    override suspend fun loadBody(path: String) = true
    override fun body(path: String?): ImageBitmap? = null
    override suspend fun garmentInfo(url: String) = if (garmentOk) GarmentInfo(1.2f, 0) else null
    override suspend fun detectPose(path: String) = pose
    override suspend fun ensure(url: String, cutout: Int) = true
    override fun garment(url: String, cutout: Int): ImageBitmap? = null
    override fun failed(url: String) = false
    override suspend fun autoCutout(url: String): Boolean { cutoutCalls += url; return cutoutOk }
    override fun cutoutNote(url: String, cutout: Int): String? = null
    override fun alphaMask(url: String, cutout: Int): AlphaMask? = null
    override suspend fun render(spec: RenderSpec, maxSide: Int, jpeg: Boolean) = renderFile
    override suspend fun thumbnail(spec: RenderSpec) = "data:image/jpeg;base64,AAAA"
    override suspend fun saveToGallery(file: File) = true
    override suspend fun upload(file: File) = ImageUpload("t.jpg", "image/jpeg", ByteArray(4))
    override suspend fun bodyUpload(path: String) = ImageUpload("b.jpg", "image/jpeg", ByteArray(4))
    override fun release() { released = true }
}

private class FakePresets(var items: MutableList<TryonPreset> = mutableListOf()) : TryonPresetRepository {
    var saveResult: ApiResult<TryonPreset>? = null
    var saved: List<TryonLayer>? = null
    override suspend fun getAll() = ApiResult.Success(items.toList())
    override suspend fun save(name: String, thumbnail: String?, layers: List<TryonLayer>, rating: Int?, occasion: String?): ApiResult<TryonPreset> {
        saved = layers
        return saveResult ?: ApiResult.Success(TryonPreset("p${items.size}", name, thumbnail, layers, rating, occasion)).also { items += it.value }
    }
    override suspend fun update(id: String, name: String?, thumbnail: String?, layers: List<TryonLayer>?, rating: Int?, occasion: String?): ApiResult<TryonPreset> {
        val old = items.first { it.id == id }
        val n = old.copy(name = name ?: old.name, layers = layers ?: old.layers)
        items[items.indexOf(old)] = n
        return ApiResult.Success(n)
    }
    override suspend fun delete(id: String): ApiResult<Unit> { items.removeAll { it.id == id }; return ApiResult.Success(Unit) }
}

private class MemoryDrafts : TryOnDraftStore {
    var json: String? = null
    override fun load() = json
    override fun save(json: String) { this.json = json }
    override fun clear() { json = null }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TryOnViewModelTest {
    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private val user = MutableStateFlow<User?>(User(id = "u1"))
    private var profileUpdates = mutableListOf<ProfileUpdate>()
    private val auth: AuthRepository = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(AuthRepository::class.java)) { _, m, args ->
        when (m.name) {
            "getCurrentUser" -> user
            "updateProfile" -> { profileUpdates += args[0] as ProfileUpdate; ApiResult.Success(user.value!!) }
            else -> error("auth.${m.name} no esperado")
        }
    } as AuthRepository

    private val wardrobe = FakeGarmentRepo(
        listOf(garment("t1", type = "top"), garment("t2", type = "top"), garment("b1", type = "bottom"), garment("s1", type = "shoes"), garment("o1", type = "outerwear")),
    )
    private val looks = FakeLookRepo()
    private val presets = FakePresets()
    private val assets = FakeAssets()
    private val drafts = MemoryDrafts()
    private var ids = 0

    private fun vm(garmentId: String? = null, a: FakeAssets = assets, d: MemoryDrafts = drafts) = TryOnViewModel(
        garmentId, wardrobe, looks, presets, auth, a, d, Random(1), { "L${ids++}" }, { System.nanoTime() / 1000 }, animateFits = false,
    )

    private fun TryOnViewModel.ids() = layers.value.map { it.garmentId }

    @Test fun `tocar una prenda la anade seleccionada y se puede deshacer y rehacer`() = runTest {
        val vm = vm()
        vm.addGarment(wardrobe.state.value.first { it.id == "t1" })
        assertEquals(listOf("t1"), vm.ids())
        assertEquals(vm.layers.value[0].id, vm.ui.value.selectedId)
        assertTrue(vm.ui.value.canUndo)
        vm.undo(); assertTrue(vm.layers.value.isEmpty()); assertTrue(vm.ui.value.canRedo); assertEquals(null, vm.ui.value.selectedId)
        vm.redo(); assertEquals(listOf("t1"), vm.ids())
    }

    @Test fun `probar otra prenda de la misma zona sustituye a la anterior salvo bloqueada`() = runTest {
        val vm = vm()
        val g = wardrobe.state.value
        vm.addGarment(g.first { it.id == "t1" })
        Thread.sleep(2) // evita el antirrebote de dobles toques
        vm.addGarment(g.first { it.id == "t2" })
        assertEquals(listOf("t2"), vm.ids())
        vm.toggleLock()
        Thread.sleep(2)
        vm.addGarment(g.first { it.id == "t1" })
        assertEquals(setOf("t1", "t2"), vm.ids().toSet())
        vm.setReplaceSame(false)
    }

    @Test fun `orden Z por zona al anadir`() = runTest {
        val vm = vm()
        val g = wardrobe.state.value
        listOf("o1", "t1", "s1", "b1").forEach { id -> vm.addGarment(g.first { it.id == id }); Thread.sleep(2) }
        assertEquals(listOf("b1", "s1", "t1", "o1"), vm.ids())
    }

    @Test fun `un gesto completo es una sola entrada de historial`() = runTest {
        val vm = vm()
        vm.addGarment(wardrobe.state.value.first { it.id == "t1" })
        val l = vm.layers.value[0]
        vm.beginGesture()
        repeat(30) { i -> vm.liveUpdate(l.copy(cx = 0.5f + i * 0.001f)) }
        vm.endGesture()
        assertEquals(0.529f, vm.layers.value[0].cx, 1e-5f)
        vm.undo()
        assertEquals(l.cx, vm.layers.value[0].cx, 1e-6f)  // vuelve al estado previo al gesto
        vm.undo()
        assertTrue(vm.layers.value.isEmpty())
    }

    @Test fun `un gesto sin cambios no ensucia el historial`() = runTest {
        val vm = vm()
        vm.addGarment(wardrobe.state.value.first { it.id == "t1" })
        vm.undo(); vm.redo()
        vm.beginGesture(); vm.endGesture()
        vm.undo()
        assertTrue(vm.layers.value.isEmpty())
    }

    @Test fun `acciones de capa voltear bloquear duplicar y borrar`() = runTest {
        val vm = vm()
        vm.addGarment(wardrobe.state.value.first { it.id == "t1" })
        vm.flipH(); assertTrue(vm.layers.value[0].flipX)
        vm.flipV(); assertTrue(vm.layers.value[0].flipY)
        vm.duplicateSelected(); assertEquals(2, vm.layers.value.size)
        assertEquals(vm.layers.value.last().id, vm.ui.value.selectedId)
        vm.deleteSelected(); assertEquals(1, vm.layers.value.size); assertEquals(null, vm.ui.value.selectedId)
    }

    @Test fun `escala y giro accesibles respetan el bloqueo`() = runTest {
        val vm = vm()
        vm.addGarment(wardrobe.state.value.first { it.id == "t1" })
        val w = vm.layers.value[0].width
        vm.scaleSelected(1.1f); assertTrue(vm.layers.value[0].width > w)
        vm.rotateSelected(10f); assertEquals(10f, vm.layers.value[0].rotation, 1e-4f)
        vm.toggleLock()
        val locked = vm.layers.value[0]
        vm.scaleSelected(2f); vm.nudgeSelected(0.1f, 0f)
        assertEquals(locked, vm.layers.value[0])
    }

    @Test fun `limite de capas`() = runTest {
        val repo = FakeGarmentRepo((1..30).map { garment("a$it", type = "accessories") })
        val vm = TryOnViewModel(null, repo, looks, presets, auth, assets, drafts, Random(1), { "L${ids++}" }, { System.nanoTime() }, animateFits = false)
        repo.state.value.forEach { vm.addGarment(it) }
        assertEquals(LayerMath.MAX_LAYERS, vm.layers.value.size)
    }

    @Test fun `la prenda de la ruta se anade al abrir`() = runTest {
        val vm = vm(garmentId = "b1")
        assertEquals(listOf("b1"), vm.ids())
    }

    @Test fun `prenda de la ruta inexistente avisa`() = runTest {
        val vm = vm(garmentId = "nope")
        val e = vm.events.first()
        assertTrue(e is TryOnEvent.Message && e.error)
    }

    @Test fun `si la imagen no carga no se anade y se avisa`() = runTest {
        val a = FakeAssets().also { it.garmentOk = false }
        val vm = vm(a = a)
        vm.addGarment(wardrobe.state.value.first())
        assertTrue(vm.layers.value.isEmpty())
        assertTrue((vm.events.first() as TryOnEvent.Message).error)
        assertFalse(vm.ui.value.adding)
    }

    @Test fun `el recorte automatico se aplica a la capa y no entra en el historial`() = runTest {
        val vm = vm()
        vm.addGarment(wardrobe.state.value.first { it.id == "t1" })
        assertEquals(-1, vm.layers.value[0].cutout)
        assertEquals(1, assets.cutoutCalls.size)
        vm.undo()
        assertTrue(vm.layers.value.isEmpty())
    }

    @Test fun `si el recorte automatico falla la prenda queda original`() = runTest {
        val a = FakeAssets().also { it.cutoutOk = false }
        val vm = vm(a = a)
        vm.addGarment(wardrobe.state.value.first { it.id == "t1" })
        assertEquals(0, vm.layers.value[0].cutout)
    }

    @Test fun `restaurar recorte vuelve a la original`() = runTest {
        val vm = vm()
        vm.addGarment(wardrobe.state.value.first { it.id == "t1" })
        vm.restoreCutout(vm.layers.value[0].id)
        assertEquals(0, vm.layers.value[0].cutout)
    }

    // ── Pose ───────────────────────────────────────────────────────────────────────────────

    private fun pose() = BodyLandmarks(
        lShoulder = Pt(0.4f, 0.25f), rShoulder = Pt(0.6f, 0.25f), lHip = Pt(0.43f, 0.55f), rHip = Pt(0.57f, 0.55f),
        lKnee = Pt(0.43f, 0.75f), rKnee = Pt(0.57f, 0.75f), lAnkle = Pt(0.43f, 0.93f), rAnkle = Pt(0.57f, 0.93f),
    )

    @Test fun `con pose la prenda se ajusta al cuerpo y sin pose usa proporciones`() = runTest {
        val a = FakeAssets().also { it.pose = pose() }
        val vm = vm(a = a)
        vm.setBody("/foto.jpg")
        assertEquals(PoseStatus.Found, vm.ui.value.pose.status)
        vm.addGarment(wardrobe.state.value.first { it.id == "t1" })
        val withPose = vm.layers.value[0]
        val lm = vm.ui.value.sceneLandmarks!!
        assertEquals(0.2f * 1.3f, withPose.width, 0.03f)
        assertTrue(withPose.cy > lm.lShoulder!!.y)

        val vm2 = vm()
        vm2.setBody("/foto.jpg")
        assertEquals(PoseStatus.NotFound, vm2.ui.value.pose.status)
        vm2.addGarment(wardrobe.state.value.first { it.id == "t1" })
        assertEquals(AutoPlacer.place(TryCategory.Top, 1.2f, vm2.ui.value.sceneHeight).width, vm2.layers.value[0].width, 1e-4f)
    }

    @Test fun `ajustar al cuerpo es un paso de historial`() = runTest {
        val a = FakeAssets().also { it.pose = pose() }
        val vm = vm(a = a)
        vm.setBody("/foto.jpg")
        vm.addGarment(wardrobe.state.value.first { it.id == "t1" })
        vm.nudgeSelected(0.2f, 0.1f)
        val moved = vm.layers.value[0]
        vm.fitSelected()
        assertTrue(vm.layers.value[0].cx != moved.cx)
        vm.undo()
        assertEquals(moved.cx, vm.layers.value[0].cx, 1e-6f)
    }

    // ── Foto base ──────────────────────────────────────────────────────────────────────────

    @Test fun `foto de perfil y guardar como foto de cuerpo entero`() = runTest {
        user.value = User(id = "u1", fullBodyAvatar = "https://img/body.jpg")
        val vm = vm()
        assertEquals("https://img/body.jpg", vm.ui.value.profileBodyUrl)
        vm.useProfileBody()
        assertEquals("https://img/body.jpg", vm.ui.value.body.path)
        vm.saveBodyAsProfile()
        assertEquals(1, profileUpdates.size)
        assertNotNull(profileUpdates[0].fullBodyAvatar)
    }

    @Test fun `sin foto de perfil avisa`() = runTest {
        val vm = vm()
        vm.useProfileBody()
        assertTrue((vm.events.first() as TryOnEvent.Message).error)
    }

    // ── Exportar / look ────────────────────────────────────────────────────────────────────

    @Test fun `exportar sin contenido avisa y con contenido emite el fichero`() = runTest {
        val vm = vm()
        vm.share()
        assertTrue((vm.events.first() as TryOnEvent.Message).error)
        vm.addGarment(wardrobe.state.value.first { it.id == "t1" })
        vm.share()
        val e = vm.events.first { it is TryOnEvent.Share }
        assertEquals(assets.renderFile, (e as TryOnEvent.Share).file)
        assertEquals(null, vm.ui.value.busy)
    }

    @Test fun `guardar como look envia las prendas existentes y lanza confeti`() = runTest {
        val vm = vm()
        val g = wardrobe.state.value
        vm.addGarment(g.first { it.id == "t1" }); Thread.sleep(2); vm.addGarment(g.first { it.id == "b1" })
        vm.saveAsLook("  Mi   look ", true)
        assertEquals(1, looks.createCalls.size)
        assertEquals(setOf("t1", "b1"), looks.createCalls[0].toSet())
        assertEquals(1, vm.ui.value.confetti)
        assertEquals(null, vm.ui.value.busy)
    }

    @Test fun `error al guardar el look muestra el mensaje del servidor`() = runTest {
        looks.createResult = failure("Nombre duplicado")
        val vm = vm()
        vm.addGarment(wardrobe.state.value.first { it.id == "t1" })
        vm.saveAsLook("x", false)
        val e = vm.events.first() as TryOnEvent.Message
        assertTrue(e.error); assertEquals("Nombre duplicado", e.text)
        assertEquals(0, vm.ui.value.confetti)
    }

    // ── Presets ────────────────────────────────────────────────────────────────────────────

    @Test fun `guardar cargar renombrar y borrar presets`() = runTest {
        val vm = vm()
        val g = wardrobe.state.value
        vm.addGarment(g.first { it.id == "t1" }); Thread.sleep(2); vm.addGarment(g.first { it.id == "b1" })
        vm.loadPresets()
        vm.savePreset("  Cena  ", 4, "formal")
        val saved = vm.ui.value.presets.single()
        assertEquals("Cena", saved.name); assertEquals(4, saved.rating); assertEquals(2, presets.saved!!.size)

        vm.clearAll(); assertTrue(vm.layers.value.isEmpty())
        vm.applyPreset(saved)
        assertEquals(setOf("t1", "b1"), vm.ids().toSet())

        vm.renamePreset(saved, "Boda")
        assertEquals("Boda", vm.ui.value.presets.single().name)
        vm.deletePreset(vm.ui.value.presets.single())
        assertTrue(vm.ui.value.presets.isEmpty())
    }

    @Test fun `preset con prendas borradas las omite con aviso`() = runTest {
        val vm = vm()
        val api = listOf(
            LayerCodec.toApi(TryLayer("a", "t1", "u", cx = 0.5f, cy = 0.5f, width = 0.4f, baseWidth = 0.4f), vm.ui.value.sceneHeight),
            LayerCodec.toApi(TryLayer("b", "borrada", "u", cx = 0.5f, cy = 0.5f, width = 0.4f, baseWidth = 0.4f), vm.ui.value.sceneHeight),
        )
        vm.applyPreset(TryonPreset("p", "X", null, api, null, null))
        assertEquals(listOf("t1"), vm.ids())
        val m = vm.events.first() as TryOnEvent.Message
        assertTrue(m.text.contains("1 prenda"))
    }

    @Test fun `preset con todas las prendas borradas no cambia el lienzo`() = runTest {
        val vm = vm()
        vm.addGarment(wardrobe.state.value.first { it.id == "t1" })
        val api = listOf(LayerCodec.toApi(TryLayer("b", "borrada", "u", cx = 0.5f, cy = 0.5f, width = 0.4f), vm.ui.value.sceneHeight))
        vm.applyPreset(TryonPreset("p", "X", null, api, null, null))
        assertEquals(listOf("t1"), vm.ids())
    }

    @Test fun `limite de 50 presets muestra mensaje y no llama al servidor`() = runTest {
        presets.items = MutableList(50) { TryonPreset("p$it", "n$it", null, emptyList(), null, null) }
        val vm = vm()
        vm.addGarment(wardrobe.state.value.first { it.id == "t1" })
        vm.loadPresets()
        vm.savePreset("otro", null, null)
        val m = vm.events.first() as TryOnEvent.Message
        assertTrue(m.error && m.text.contains("50"))
        assertEquals(50, presets.items.size)
    }

    @Test fun `error del servidor al guardar preset se muestra tal cual`() = runTest {
        presets.saveResult = failure("Has alcanzado el máximo de 50 looks guardados")
        val vm = vm()
        vm.addGarment(wardrobe.state.value.first { it.id == "t1" })
        vm.savePreset("x", null, null)
        assertEquals("Has alcanzado el máximo de 50 looks guardados", (vm.events.first() as TryOnEvent.Message).text)
    }

    // ── Borrador / ciclo de vida ───────────────────────────────────────────────────────────

    @Test fun `el borrador restaura capas tras la muerte del proceso`() = runTest {
        val vm1 = vm()
        vm1.addGarment(wardrobe.state.value.first { it.id == "t1" })
        vm1.setMirror(true)
        vm1.flushDraft()
        assertNotNull(drafts.json)
        val vm2 = vm()
        assertEquals(listOf("t1"), vm2.ids())
        assertTrue(vm2.ui.value.mirror)
        assertTrue(vm2.ui.value.ready)
    }

    @Test fun `un borrador corrupto se ignora`() = runTest {
        val d = MemoryDrafts().also { it.json = "{basura" }
        val vm = vm(d = d)
        assertTrue(vm.layers.value.isEmpty()); assertTrue(vm.ui.value.ready)
    }

    @Test fun `la ruta con prenda no duplica si ya esta en el borrador`() = runTest {
        val vm1 = vm(garmentId = "b1"); vm1.flushDraft()
        val vm2 = vm(garmentId = "b1")
        assertEquals(listOf("b1"), vm2.ids())
    }

    @Test fun `sorprendeme arma un look completo y se deshace`() = runTest {
        val vm = vm()
        vm.surprise()
        val cats = vm.layers.value.map { it.category }
        assertTrue(cats.size >= 2 && TryCategory.Shoes in cats)
        vm.undo()
        assertTrue(vm.layers.value.isEmpty())
    }

    @Test fun `sorprendeme sin prendas avisa`() = runTest {
        val repo = FakeGarmentRepo()
        val vm = TryOnViewModel(null, repo, looks, presets, auth, assets, drafts, Random(1), { "L${ids++}" }, { 0L }, animateFits = false)
        vm.surprise()
        assertTrue((vm.events.first() as TryOnEvent.Message).error)
    }

    @Test fun `vaciar respeta las capas bloqueadas`() = runTest {
        val vm = vm()
        val g = wardrobe.state.value
        vm.addGarment(g.first { it.id == "t1" }); vm.toggleLock(); Thread.sleep(2)
        vm.addGarment(g.first { it.id == "b1" })
        vm.clearAll()
        assertEquals(listOf("t1"), vm.ids())
    }
}
