package com.estilovivo.app.feature.tryon

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.model.Garment
import com.estilovivo.app.core.data.model.ProfileUpdate
import com.estilovivo.app.core.data.model.TryonPreset
import com.estilovivo.app.core.data.repo.AuthRepository
import com.estilovivo.app.core.data.repo.GarmentRepository
import com.estilovivo.app.core.data.repo.LookRepository
import com.estilovivo.app.core.data.repo.TryonPresetRepository
import com.estilovivo.app.core.image.Cutout
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import java.io.File
import java.util.UUID
import kotlin.random.Random

enum class Busy { Body, Export, Look, Preset, Surprise }

@Immutable
data class BodyState(val path: String? = null, val aspect: Float = Scene.DEFAULT_ASPECT, val rawAspect: Float = aspect)

enum class PoseStatus { Idle, Detecting, Found, NotFound }

/** [landmarks] en coordenadas normalizadas de la foto. */
@Immutable
data class PoseUi(val status: PoseStatus = PoseStatus.Idle, val landmarks: BodyLandmarks? = null)

@Immutable
data class TryOnUi(
    val ready: Boolean = false,
    val body: BodyState = BodyState(),
    val selectedId: String? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val darkBackground: Boolean = false,
    val mirror: Boolean = false,
    val grid: Boolean = false,
    val replaceSameCategory: Boolean = true,
    val garments: List<Garment> = emptyList(),
    val garmentsLoading: Boolean = true,
    val garmentsError: String? = null,
    val category: TryCategory? = null,
    val profileBodyUrl: String? = null,
    val adding: Boolean = false,
    val busy: Busy? = null,
    val presets: List<TryonPreset> = emptyList(),
    val presetsLoading: Boolean = false,
    val presetsLoaded: Boolean = false,
    val presetsError: String? = null,
    val presetBusyId: String? = null,
    val loadedPresetId: String? = null,
    /** Sube con cada guardado correcto (dispara el confeti). */
    val confetti: Int = 0,
    val canSaveToGallery: Boolean = true,
    val pose: PoseUi = PoseUi(),
    val cutoutBusy: Set<String> = emptySet(),
) {
    val sceneLandmarks: BodyLandmarks? get() = pose.landmarks?.toScene(body.rawAspect, body.aspect)
    val sceneAspect: Float get() = body.aspect
    val sceneHeight: Float get() = Scene.height(body.aspect)
    val categories: List<TryCategory> get() = TryCategory.entries.filter { c -> garments.any { TryCatalog.categoryOf(it.type) == c } }
    val visibleGarments: List<Garment>
        get() = if (category == null) garments else garments.filter { TryCatalog.categoryOf(it.type) == category }
}

sealed interface TryOnEvent {
    data class Message(val text: String, val error: Boolean = false) : TryOnEvent
    data class Share(val file: File) : TryOnEvent
    /** Una capa nueva o recién cargada: la pantalla puede animar la selección. */
    data object Haptic : TryOnEvent
}

@OptIn(FlowPreview::class)
class TryOnViewModel(
    initialGarmentId: String?,
    private val garmentRepo: GarmentRepository,
    private val looks: LookRepository,
    private val presetRepo: TryonPresetRepository,
    private val auth: AuthRepository,
    val assets: TryOnAssets,
    private val drafts: TryOnDraftStore,
    private val random: Random = Random.Default,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Long = System::currentTimeMillis,
    private val animateFits: Boolean = true,
) : ViewModel() {

    private val _layers = MutableStateFlow<List<TryLayer>>(emptyList())

    /** Capas (se actualiza en cada fotograma de un gesto: sólo debe leerse en la fase de dibujo). */
    val layers: StateFlow<List<TryLayer>> = _layers.asStateFlow()

    private val _ui = MutableStateFlow(TryOnUi(canSaveToGallery = assets.canSaveToGallery))
    val ui: StateFlow<TryOnUi> = _ui.asStateFlow()

    private val _events = Channel<TryOnEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var history = UndoStack<List<TryLayer>>()
    private var gestureBase: List<TryLayer>? = null
    private var pendingGarmentId: String? = initialGarmentId?.takeIf { it.isNotBlank() }
    private var draftJob: Job? = null
    private var lastAddId: String? = null
    private var lastAddAt = 0L
    private val failedToasted = HashSet<String>()
    private var poseJob: Job? = null

    private val sceneH get() = _ui.value.sceneHeight

    init {
        viewModelScope.launch { restoreDraft() }
        viewModelScope.launch {
            garmentRepo.garments.collect { list ->
                _ui.update { it.copy(garments = list, category = it.category?.takeIf { c -> list.any { g -> TryCatalog.categoryOf(g.type) == c } }) }
                if (list.isNotEmpty()) maybeAddPending()
            }
        }
        viewModelScope.launch { refreshGarments() }
        viewModelScope.launch { auth.currentUser.collect { u -> _ui.update { it.copy(profileBodyUrl = u?.fullBodyAvatar?.takeIf { s -> s.isNotBlank() }) } } }
        viewModelScope.launch {
            _layers.map { l -> l.map { it.url to it.cutout }.toSet() }.distinctUntilChanged().debounce(90).collectLatest { set ->
                coroutineScope {
                    set.forEach { (url, cutout) ->
                        launch {
                            if (!assets.ensure(url, cutout) && failedToasted.add(url)) {
                                val name = _layers.value.firstOrNull { it.url == url }?.name.orEmpty()
                                emit(TryOnEvent.Message("No se pudo cargar la imagen${if (name.isNotBlank()) " de «$name»" else ""}. Comprueba tu conexión.", true))
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Arranque / borrador ───────────────────────────────────────────────────────────────────

    private suspend fun restoreDraft() {
        val d = DraftCodec.decode(drafts.load())
        if (d != null) {
            var body = BodyState()
            val path = d.bodyPath
            if (path != null && File(path).exists() && assets.loadBody(path)) body = BodyState(path, d.bodyAspect, rawAspectOf(path, d.bodyAspect))
            val h = Scene.height(body.aspect)
            // Si la foto ya no existe cambia el lienzo: reescala la altura relativa.
            val ratio = h / Scene.height(d.bodyAspect)
            val layers = d.layers.map { LayerMath.clampToScene(it.copy(cy = it.cy * ratio), h) }
            _layers.value = layers
            _ui.update { it.copy(body = body, darkBackground = d.darkBackground, mirror = d.mirror, grid = d.grid) }
        }
        _ui.update { it.copy(ready = true) }
        _ui.value.body.path?.let { startPose(it) }
        maybeAddPending()
    }

    private suspend fun refreshGarments() {
        _ui.update { it.copy(garmentsLoading = true, garmentsError = null) }
        when (val r = garmentRepo.getAll()) {
            is ApiResult.Success -> _ui.update { it.copy(garmentsLoading = false) }
            is ApiResult.Failure -> _ui.update {
                it.copy(garmentsLoading = false, garmentsError = if (it.garments.isEmpty()) r.error.message else null)
            }
        }
    }

    private fun rawAspectOf(path: String, fallback: Float): Float =
        assets.body(path)?.let { it.width.toFloat() / it.height }?.takeIf { it.isFinite() && it > 0f } ?: fallback

    private fun startPose(path: String) {
        poseJob?.cancel()
        poseJob = viewModelScope.launch {
            _ui.update { it.copy(pose = PoseUi(PoseStatus.Detecting)) }
            val lm = assets.detectPose(path)
            _ui.update { it.copy(pose = if (lm != null) PoseUi(PoseStatus.Found, lm) else PoseUi(PoseStatus.NotFound)) }
        }
    }

    /** Ajuste de una prenda al cuerpo detectado (null si no hay pose fiable para esa categoría). */
    private suspend fun fitFor(cat: TryCategory, name: String, aspect: Float): Fit? {
        kotlinx.coroutines.withTimeoutOrNull(3000) { poseJob?.join() }
        val lm = _ui.value.sceneLandmarks ?: return null
        return PoseFitter.fit(cat, name, aspect, lm, sceneH)
    }

    private fun animateFit(id: String, f: Fit) {
        viewModelScope.launch {
            val start = _layers.value.firstOrNull { it.id == id } ?: return@launch
            val h = sceneH
            fun at(k: Float) = start.copy(
                cx = start.cx + (f.cx - start.cx) * k, cy = start.cy + (f.cy - start.cy) * k,
                width = start.width + (f.width - start.width) * k, rotation = start.rotation + (f.rotation - start.rotation) * k,
            )
            if (animateFits) {
                var t = 0f
                while (t < 0.8f) {
                    delay(16); t += 0.016f
                    if (gestureBase != null || _layers.value.none { it.id == id }) return@launch
                    liveUpdate(LayerMath.clampToScene(at(FitSpring.value(t)), h).copy(baseWidth = start.baseWidth))
                }
            }
            if (gestureBase == null && _layers.value.any { it.id == id }) liveUpdate(LayerMath.clampToScene(at(1f), h).copy(baseWidth = start.baseWidth))
            scheduleDraft()
        }
    }

    private fun startAutoCutout(id: String, manual: Boolean = false) {
        val l = _layers.value.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            _ui.update { it.copy(cutoutBusy = it.cutoutBusy + id) }
            val ok = assets.autoCutout(l.url)
            _ui.update { it.copy(cutoutBusy = it.cutoutBusy - id) }
            if (ok) {
                _layers.update { list -> list.map { if (it.id == id && (manual || it.cutout == 0)) it.copy(cutout = -1) else it } }
                scheduleDraft()
            } else if (manual) {
                toast(assets.cutoutNote(l.url, -1) ?: "No se pudo recortar esta prenda. Prueba el recorte básico.", true)
            }
        }
    }

    /** Recorte inteligente a petición (botón de la hoja de recorte). */
    fun autoCutoutSelected() { selected()?.let { startAutoCutout(it.id, manual = true) } }

    /** Reajusta la prenda seleccionada al cuerpo (o a las proporciones del lienzo) con animación de muelle. */
    fun fitSelected() {
        val s = selected() ?: return
        if (s.locked) { toast("La prenda está bloqueada. Desbloquéala para editarla."); return }
        viewModelScope.launch {
            val fit = fitFor(s.category, s.name, s.aspect)
            val f = fit ?: AutoPlacer.place(s.category, s.aspect, sceneH).let { Fit(it.cx, it.cy, it.width, 0f) }
            history = history.record(_layers.value)
            refreshHistoryFlags()
            animateFit(s.id, f)
            if (fit == null && _ui.value.body.path != null && _ui.value.pose.status == PoseStatus.NotFound) {
                toast("No detectamos tu cuerpo en la foto: colocada por proporciones. Prueba con una foto de pie y cuerpo entero.")
            }
        }
    }

    fun retryGarments() { viewModelScope.launch { refreshGarments() } }

    private fun maybeAddPending() {
        val id = pendingGarmentId ?: return
        val st = _ui.value
        if (!st.ready || st.garments.isEmpty()) return
        pendingGarmentId = null
        val g = st.garments.firstOrNull { it.id == id }
        when {
            g == null -> emit(TryOnEvent.Message("No encontramos esa prenda en tu armario.", true))
            _layers.value.any { it.garmentId == id } -> select(_layers.value.first { it.garmentId == id }.id)
            else -> addGarment(g)
        }
    }

    private fun emit(e: TryOnEvent) { _events.trySend(e) }
    private fun toast(text: String, error: Boolean = false) = emit(TryOnEvent.Message(text, error))

    // ── Estado derivado / historial ──────────────────────────────────────────────────────────

    private fun refreshHistoryFlags() {
        _ui.update { it.copy(canUndo = history.canUndo, canRedo = history.canRedo, selectedId = it.selectedId?.takeIf { s -> _layers.value.any { l -> l.id == s } }) }
    }

    /** Cambio atómico con una entrada de historial. */
    private fun commit(new: List<TryLayer>, select: String? = null, keepSelection: Boolean = true) {
        val old = _layers.value
        if (new == old) return
        history = history.record(old)
        _layers.value = new
        _ui.update { it.copy(selectedId = select ?: if (keepSelection) it.selectedId else null) }
        refreshHistoryFlags()
        scheduleDraft()
    }

    fun undo() {
        val r = history.undo(_layers.value) ?: return
        history = r.first
        _layers.value = r.second
        refreshHistoryFlags()
        scheduleDraft()
    }

    fun redo() {
        val r = history.redo(_layers.value) ?: return
        history = r.first
        _layers.value = r.second
        refreshHistoryFlags()
        scheduleDraft()
    }

    // ── Gestos (cambios en vivo sin historial hasta soltar) ──────────────────────────────────

    fun select(id: String?) {
        _ui.update { it.copy(selectedId = id?.takeIf { s -> _layers.value.any { l -> l.id == s } }) }
    }

    fun beginGesture() { if (gestureBase == null) gestureBase = _layers.value }

    fun liveUpdate(layer: TryLayer) {
        _layers.update { list -> list.map { if (it.id == layer.id) layer else it } }
    }

    fun endGesture() {
        val base = gestureBase ?: return
        gestureBase = null
        if (base != _layers.value) {
            history = history.record(base)
            refreshHistoryFlags()
            scheduleDraft()
        }
    }

    /** Edición en vivo de una propiedad (opacidad, recorte...). [commit] = al soltar el control. */
    fun edit(id: String, commit: Boolean, f: (TryLayer) -> TryLayer) {
        val cur = _layers.value.firstOrNull { it.id == id } ?: return
        beginGesture()
        liveUpdate(LayerMath.clampToScene(f(cur), sceneH))
        if (commit) endGesture()
    }

    private fun selected(): TryLayer? = _ui.value.selectedId?.let { id -> _layers.value.firstOrNull { it.id == id } }

    // ── Operaciones sobre capas ──────────────────────────────────────────────────────────────

    fun addGarment(g: Garment) {
        val now = clock()
        if (lastAddId == g.id && now - lastAddAt < 350) return
        lastAddId = g.id; lastAddAt = now
        if (_layers.value.size >= LayerMath.MAX_LAYERS) {
            toast("Máximo ${LayerMath.MAX_LAYERS} prendas a la vez. Quita alguna para añadir más.", true); return
        }
        viewModelScope.launch {
            _ui.update { it.copy(adding = true) }
            val info = assets.garmentInfo(g.imageUrl)
            _ui.update { it.copy(adding = false) }
            if (info == null) {
                toast("No se pudo cargar la imagen de «${g.name}». Comprueba tu conexión e inténtalo de nuevo.", true); return@launch
            }
            val cat = TryCatalog.categoryOf(g.type)
            val p = AutoPlacer.place(cat, info.aspect, sceneH)
            val fit = fitFor(cat, g.name, info.aspect)
            val layer = TryLayer(
                id = newId(), garmentId = g.id, url = g.imageUrl, name = g.name, cx = p.cx, cy = p.cy, width = p.width,
                aspect = info.aspect, category = cat, baseWidth = fit?.width ?: p.width,
            )
            var base = _layers.value
            if (base.size >= LayerMath.MAX_LAYERS) { toast("Máximo ${LayerMath.MAX_LAYERS} prendas a la vez.", true); return@launch }
            if (_ui.value.replaceSameCategory && cat in LayerMath.singleSlot) base = base.filterNot { it.category == cat && !it.locked }
            commit(LayerMath.insertByRank(base, layer), select = layer.id)
            if (fit != null) animateFit(layer.id, fit)
            startAutoCutout(layer.id)
        }
    }

    fun deleteSelected() {
        val s = selected() ?: return
        commit(_layers.value.filterNot { it.id == s.id }, keepSelection = false)
        _ui.update { it.copy(selectedId = null) }
    }

    fun deleteLayer(id: String) {
        commit(_layers.value.filterNot { it.id == id })
        _ui.update { st -> st.copy(selectedId = st.selectedId?.takeIf { it != id }) }
    }

    private fun mutateSelected(f: (List<TryLayer>, String) -> List<TryLayer>) {
        val s = selected() ?: return
        commit(f(_layers.value, s.id))
    }

    private fun updateSelected(f: (TryLayer) -> TryLayer) {
        val s = selected() ?: return
        commit(_layers.value.map { if (it.id == s.id) LayerMath.clampToScene(f(it), sceneH) else it })
    }

    fun flipH() = updateSelected { it.copy(flipX = !it.flipX) }
    fun flipV() = updateSelected { it.copy(flipY = !it.flipY) }
    fun toggleLock() = updateSelected { it.copy(locked = !it.locked) }
    fun bringToFront() = mutateSelected { l, id -> LayerMath.bringToFront(l, id) }
    fun sendToBack() = mutateSelected { l, id -> LayerMath.sendToBack(l, id) }
    fun moveForward() = mutateSelected { l, id -> LayerMath.moveForward(l, id) }
    fun moveBackward() = mutateSelected { l, id -> LayerMath.moveBackward(l, id) }

    fun duplicateSelected() {
        val s = selected() ?: return
        if (_layers.value.size >= LayerMath.MAX_LAYERS) { toast("Máximo ${LayerMath.MAX_LAYERS} prendas a la vez.", true); return }
        val id = newId()
        commit(LayerMath.duplicate(_layers.value, s.id, id).map { LayerMath.clampToScene(it, sceneH) }, select = id)
    }

    /** Escala (acción accesible alternativa al pellizco). */
    fun scaleSelected(factor: Float) {
        val s = selected() ?: return
        if (s.locked) { toast("La prenda está bloqueada. Desbloquéala para editarla."); return }
        updateSelected { LayerMath.transform(it, sceneH, 0f, 0f, factor, 0f, it.cx, it.cy) }
    }

    fun rotateSelected(deg: Float) {
        val s = selected() ?: return
        if (s.locked) { toast("La prenda está bloqueada. Desbloquéala para editarla."); return }
        updateSelected { LayerMath.transform(it, sceneH, 0f, 0f, 1f, deg, it.cx, it.cy) }
    }

    fun nudgeSelected(dx: Float, dy: Float) {
        val s = selected() ?: return
        if (s.locked) { toast("La prenda está bloqueada. Desbloquéala para editarla."); return }
        updateSelected { LayerMath.transform(it, sceneH, dx, dy, 1f, 0f, it.cx, it.cy) }
    }

    fun setCutout(id: String, tolerance: Int, commit: Boolean) = edit(id, commit) { it.copy(cutout = tolerance.coerceIn(0, Cutout.MAX_TOLERANCE)) }

    fun restoreCutout(id: String) = setCutout(id, 0, true)

    fun clearAll() {
        val keep = _layers.value.filter { it.locked }
        if (keep.size == _layers.value.size) { if (keep.isNotEmpty()) toast("Todas las prendas están bloqueadas."); return }
        commit(keep, keepSelection = false)
        _ui.update { it.copy(selectedId = null, loadedPresetId = null) }
    }

    // ── Escena ───────────────────────────────────────────────────────────────────────────────

    fun setDarkBackground(v: Boolean) { _ui.update { it.copy(darkBackground = v) }; scheduleDraft() }
    fun setMirror(v: Boolean) { _ui.update { it.copy(mirror = v) }; scheduleDraft() }
    fun setGrid(v: Boolean) { _ui.update { it.copy(grid = v) }; scheduleDraft() }
    fun setReplaceSame(v: Boolean) { _ui.update { it.copy(replaceSameCategory = v) } }
    fun setCategory(c: TryCategory?) { _ui.update { it.copy(category = c) } }

    /** "Sorpréndeme": arma un look con una prenda por zona y las coloca en proporción al cuerpo. */
    fun surprise() {
        if (_ui.value.busy != null) return
        val pick = OutfitBuilder.build(_ui.value.garments, random, avoidIds = _layers.value.map { it.garmentId }.toSet())
        if (pick.isEmpty()) { toast("Necesitas prendas en tu armario para que te sorprendamos.", true); return }
        viewModelScope.launch {
            _ui.update { it.copy(busy = Busy.Surprise) }
            val infos = pick.map { g -> async { assets.garmentInfo(g.imageUrl) } }.awaitAll()
            _ui.update { it.copy(busy = null) }
            val h = sceneH
            val built = pick.zip(infos).mapNotNull { (g, info) ->
                info ?: return@mapNotNull null
                val cat = TryCatalog.categoryOf(g.type)
                val p = fitFor(cat, g.name, info.aspect)?.let { Placement2(it.cx, it.cy, it.width, it.rotation) }
                    ?: AutoPlacer.place(cat, info.aspect, h).let { Placement2(it.cx, it.cy, it.width, 0f) }
                LayerMath.clampToScene(TryLayer(newId(), g.id, g.imageUrl, g.name, p.cx, p.cy, p.width, info.aspect, rotation = p.rot, category = cat, baseWidth = p.width), h)
            }
            if (built.isEmpty()) { toast("No se pudieron cargar las prendas. Comprueba tu conexión.", true); return@launch }
            val locked = _layers.value.filter { it.locked }
            var result = locked
            for (l in built) result = LayerMath.insertByRank(result, l)
            commit(result.take(LayerMath.MAX_LAYERS), keepSelection = false)
            _ui.update { it.copy(selectedId = null, loadedPresetId = null) }
            built.forEach { startAutoCutout(it.id) }
            toast("¡Look sorpresa listo! Puedes deshacerlo o ajustarlo.")
        }
    }

    // ── Foto base ────────────────────────────────────────────────────────────────────────────

    /** [source]: `content://`, ruta o URL. */
    fun setBody(source: String) {
        if (_ui.value.busy == Busy.Body) return
        viewModelScope.launch {
            _ui.update { it.copy(busy = Busy.Body) }
            val info = assets.importBody(source)
            if (info == null || !assets.loadBody(info.path)) {
                _ui.update { it.copy(busy = null) }
                toast("No pudimos leer esa foto. Prueba con otra (JPG, PNG o WebP).", true); return@launch
            }
            val oldH = sceneH
            val newH = Scene.height(info.aspect)
            _ui.update { it.copy(busy = null, body = BodyState(info.path, info.aspect, rawAspectOf(info.path, info.aspect))) }
            startPose(info.path)
            if (oldH != newH) {
                val ratio = newH / oldH
                _layers.update { l -> l.map { LayerMath.clampToScene(it.copy(cy = it.cy * ratio), newH) } }
            }
            scheduleDraft()
        }
    }

    fun useProfileBody() {
        val url = _ui.value.profileBodyUrl ?: run { toast("Aún no tienes foto de cuerpo entero en tu perfil.", true); return }
        setBody(url)
    }

    fun removeBody() {
        poseJob?.cancel()
        _ui.update { it.copy(body = BodyState(), pose = PoseUi()) }
        scheduleDraft()
    }

    fun saveBodyAsProfile() {
        val path = _ui.value.body.path ?: run { toast("Primero elige una foto base.", true); return }
        if (_ui.value.busy != null) return
        viewModelScope.launch {
            _ui.update { it.copy(busy = Busy.Body) }
            val up = assets.bodyUpload(path)
            if (up == null) {
                _ui.update { it.copy(busy = null) }; toast("No se pudo preparar la foto.", true); return@launch
            }
            when (val r = auth.updateProfile(ProfileUpdate(fullBodyAvatar = up))) {
                is ApiResult.Success -> toast("Guardada como tu foto de cuerpo entero.")
                is ApiResult.Failure -> toast(r.error.message, true)
            }
            _ui.update { it.copy(busy = null) }
        }
    }

    // ── Exportar ─────────────────────────────────────────────────────────────────────────────

    private fun spec() = _ui.value.let { RenderSpec(_layers.value, it.body.path, it.sceneAspect, it.darkBackground, it.mirror) }
    private fun hasContent() = _layers.value.isNotEmpty() || _ui.value.body.path != null

    fun share() = runExport { f -> emit(TryOnEvent.Share(f)) }

    fun saveToGallery() {
        if (!assets.canSaveToGallery) { toast("Guardar en la galería requiere Android 10 o superior. Usa «Compartir».", true); return }
        runExport { f ->
            if (assets.saveToGallery(f)) toast("Guardada en tu galería (Pictures/EstiloVivo).")
            else toast("No se pudo guardar en la galería.", true)
        }
    }

    private fun runExport(after: suspend (File) -> Unit) {
        if (_ui.value.busy != null) return
        if (!hasContent()) { toast("Añade una foto o alguna prenda antes de exportar.", true); return }
        viewModelScope.launch {
            _ui.update { it.copy(busy = Busy.Export) }
            val file = assets.render(spec(), EXPORT_SIDE, jpeg = false)
            _ui.update { it.copy(busy = null) }
            if (file == null) toast("No se pudo generar la imagen. Comprueba tu conexión e inténtalo de nuevo.", true) else after(file)
        }
    }

    /** "Guardar como look" en el armario (opcionalmente público). */
    fun saveAsLook(name: String, isPublic: Boolean) {
        if (_ui.value.busy != null) return
        if (_layers.value.isEmpty()) { toast("Añade alguna prenda para guardar el look.", true); return }
        val clean = PresetRules.cleanName(name).ifBlank { "Mi look" }
        val known = _ui.value.garments.mapTo(HashSet()) { it.id }
        val ids = _layers.value.map { it.garmentId }.filter { it in known }.distinct()
        if (ids.isEmpty()) { toast("Estas prendas ya no están en tu armario.", true); return }
        viewModelScope.launch {
            _ui.update { it.copy(busy = Busy.Look) }
            val file = assets.render(spec(), LOOK_SIDE, jpeg = true)
            val up = file?.let { assets.upload(it) }
            if (up == null) {
                _ui.update { it.copy(busy = null) }; toast("No se pudo generar la imagen del look.", true); return@launch
            }
            when (val r = looks.createWithImage(clean, ids, up, isPublic)) {
                is ApiResult.Success -> {
                    _ui.update { it.copy(confetti = it.confetti + 1) }
                    toast(if (isPublic) "¡Look publicado!" else "Look guardado en tu armario")
                }
                is ApiResult.Failure -> toast(r.error.message, true)
            }
            _ui.update { it.copy(busy = null) }
            file.delete()
        }
    }

    // ── Presets ──────────────────────────────────────────────────────────────────────────────

    fun loadPresets(force: Boolean = false) {
        val st = _ui.value
        if (st.presetsLoading || (st.presetsLoaded && !force)) return
        viewModelScope.launch {
            _ui.update { it.copy(presetsLoading = true, presetsError = null) }
            when (val r = presetRepo.getAll()) {
                is ApiResult.Success -> _ui.update { it.copy(presets = r.value, presetsLoading = false, presetsLoaded = true) }
                is ApiResult.Failure -> _ui.update { it.copy(presetsLoading = false, presetsError = r.error.message) }
            }
        }
    }

    fun savePreset(name: String, rating: Int?, occasion: String?) {
        if (_ui.value.busy != null) return
        if (_layers.value.isEmpty()) { toast("Añade alguna prenda para guardar el montaje.", true); return }
        if (_ui.value.presetsLoaded && _ui.value.presets.size >= PresetRules.MAX_PRESETS) {
            toast("Has alcanzado el máximo de ${PresetRules.MAX_PRESETS} looks guardados. Borra alguno para guardar otro.", true); return
        }
        viewModelScope.launch {
            _ui.update { it.copy(busy = Busy.Preset) }
            val thumb = assets.thumbnail(spec())
            val api = _layers.value.map { LayerCodec.toApi(it, sceneH) }
            when (val r = presetRepo.save(PresetRules.cleanName(name).ifBlank { "Look" }, thumb, api, rating?.takeIf { it in 1..5 }, occasion)) {
                is ApiResult.Success -> {
                    _ui.update { it.copy(presets = it.presets + r.value, loadedPresetId = r.value.id, confetti = it.confetti + 1) }
                    toast("Look guardado en «Mis looks»")
                }
                is ApiResult.Failure -> toast(r.error.message, true)
            }
            _ui.update { it.copy(busy = null) }
        }
    }

    fun overwritePreset(p: TryonPreset) {
        if (_ui.value.busy != null || _ui.value.presetBusyId != null) return
        if (_layers.value.isEmpty()) { toast("No hay prendas que guardar.", true); return }
        viewModelScope.launch {
            _ui.update { it.copy(presetBusyId = p.id) }
            val thumb = assets.thumbnail(spec())
            val api = _layers.value.map { LayerCodec.toApi(it, sceneH) }
            when (val r = presetRepo.update(p.id, thumbnail = thumb, layers = api)) {
                is ApiResult.Success -> {
                    _ui.update { it.copy(presets = it.presets.map { x -> if (x.id == p.id) r.value else x }, loadedPresetId = p.id) }
                    toast("«${r.value.name}» actualizado")
                }
                is ApiResult.Failure -> toast(r.error.message, true)
            }
            _ui.update { it.copy(presetBusyId = null) }
        }
    }

    fun renamePreset(p: TryonPreset, name: String) {
        val clean = PresetRules.cleanName(name)
        if (clean.isBlank() || clean == p.name || _ui.value.presetBusyId != null) return
        viewModelScope.launch {
            _ui.update { it.copy(presetBusyId = p.id) }
            when (val r = presetRepo.update(p.id, name = clean)) {
                is ApiResult.Success -> _ui.update { it.copy(presets = it.presets.map { x -> if (x.id == p.id) r.value else x }) }
                is ApiResult.Failure -> toast(r.error.message, true)
            }
            _ui.update { it.copy(presetBusyId = null) }
        }
    }

    fun deletePreset(p: TryonPreset) {
        if (_ui.value.presetBusyId != null) return
        viewModelScope.launch {
            _ui.update { it.copy(presetBusyId = p.id) }
            when (val r = presetRepo.delete(p.id)) {
                is ApiResult.Success -> {
                    _ui.update { it.copy(presets = it.presets.filterNot { x -> x.id == p.id }, loadedPresetId = it.loadedPresetId.takeIf { id -> id != p.id }) }
                    toast("Look eliminado")
                }
                is ApiResult.Failure -> toast(r.error.message, true)
            }
            _ui.update { it.copy(presetBusyId = null) }
        }
    }

    fun applyPreset(p: TryonPreset) {
        viewModelScope.launch {
            if (_ui.value.garments.isEmpty()) refreshGarments()
            val r = LayerCodec.resolve(p.layers, _ui.value.garments, sceneH, newId)
            if (r.layers.isEmpty()) {
                toast(if (r.missing > 0) "Las prendas de este look ya no están en tu armario." else "Este look no tiene prendas.", true); return@launch
            }
            commit(r.layers, keepSelection = false)
            _ui.update { it.copy(selectedId = null, loadedPresetId = p.id) }
            r.layers.forEach { startAutoCutout(it.id) }
            toast(if (r.missing > 0) "Look cargado. ${r.missing} prenda${if (r.missing == 1) "" else "s"} ya no está${if (r.missing == 1) "" else "n"} en tu armario y se ${if (r.missing == 1) "omitió" else "omitieron"}." else "Look «${p.name}» cargado")
        }
    }

    // ── Borrador / ciclo de vida ─────────────────────────────────────────────────────────────

    private fun draftNow(): TryDraft = _ui.value.let {
        TryDraft(
            bodyPath = it.body.path, bodyAspect = it.body.aspect, layers = _layers.value,
            darkBackground = it.darkBackground, mirror = it.mirror, grid = it.grid,
        )
    }

    private fun scheduleDraft() {
        if (!_ui.value.ready) return
        draftJob?.cancel()
        draftJob = viewModelScope.launch {
            delay(500)
            drafts.save(DraftCodec.encode(draftNow()))
        }
    }

    /** Guarda sin esperar (la pantalla pasa a segundo plano: el proceso puede morir). */
    fun flushDraft() {
        if (!_ui.value.ready) return
        draftJob?.cancel()
        drafts.save(DraftCodec.encode(draftNow()))
    }

    override fun onCleared() {
        if (_ui.value.ready) drafts.save(DraftCodec.encode(draftNow()))
        assets.release()
        super.onCleared()
    }

    private companion object {
        const val EXPORT_SIDE = 2048
        const val LOOK_SIDE = 1600
    }
}


private data class Placement2(val cx: Float, val cy: Float, val width: Float, val rot: Float)
