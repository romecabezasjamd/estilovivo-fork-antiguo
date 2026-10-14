package com.estilovivo.app.feature.tryon

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.estilovivo.app.app.LocalNavigator
import com.estilovivo.app.app.MainTab
import com.estilovivo.app.core.design.DarkEvColors
import com.estilovivo.app.core.design.EvMotion
import com.estilovivo.app.core.design.EvSpace
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.LocalEvColors
import com.estilovivo.app.core.design.components.EvButtonStyle
import com.estilovivo.app.core.design.components.EvCircleButton
import com.estilovivo.app.core.design.components.EvConfetti
import com.estilovivo.app.core.design.components.EvHeadline
import com.estilovivo.app.core.design.components.EvLoadingDots
import com.estilovivo.app.core.design.components.EvPillButton
import com.estilovivo.app.core.design.components.EvSegmented
import com.estilovivo.app.core.design.components.LocalEvToast
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.floatingBob
import com.estilovivo.app.core.design.rememberEvHaptics
import com.estilovivo.app.core.image.CutoutModelStatus
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.io.File

private enum class Sheet { None, Body, Export, Menu, Presets, SaveLook, SavePreset, Cutout, Help }

private val PANEL_HEIGHTS = listOf(96f, 280f, 440f)

@Composable
fun TryOnScreen(garmentId: String?, modifier: Modifier = Modifier) {
    val vm: TryOnViewModel = koinViewModel(parameters = { parametersOf(garmentId.orEmpty()) })
    CompositionLocalProvider(LocalEvColors provides DarkEvColors) { TryOnContent(vm, modifier) }
}

@Composable
private fun TryOnContent(vm: TryOnViewModel, modifier: Modifier) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val layers = vm.layers.collectAsStateWithLifecycle()
    val revision = vm.assets.revision.collectAsStateWithLifecycle()
    val cutoutStatus by vm.assets.cutoutStatus.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val toast = LocalEvToast.current
    val context = LocalContext.current
    val haptics = rememberEvHaptics()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val c = EvTheme.colors

    val viewport = rememberSaveable(saver = Viewport.Saver) { Viewport() }
    var sheet by rememberSaveable { mutableStateOf(Sheet.None) }
    var cameraOpen by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var panelIdx by rememberSaveable { mutableIntStateOf(1) }
    var compare by rememberSaveable { mutableStateOf(false) }
    var compareFraction by rememberSaveable { mutableFloatStateOf(0.5f) }
    var showPose by rememberSaveable { mutableStateOf(false) }
    var emptyDismissed by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    val briefs by remember { derivedStateOf { layers.value.map { it.brief() } } }
    val placed by remember { derivedStateOf { layers.value.mapTo(HashSet()) { it.garmentId } as Set<String> } }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { cameraOpen = false; vm.setBody(uri.toString()) }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.flushDraft() }
    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is TryOnEvent.Message -> if (e.error) toast.error(e.text) else toast.success(e.text)
                is TryOnEvent.Share -> shareFile(context, e.file)
                TryOnEvent.Haptic -> haptics.tick()
            }
        }
    }
    // Al seleccionar una capa se abre su pestaña y el panel se asoma.
    LaunchedEffect(ui.selectedId) {
        if (ui.selectedId != null) { tab = 1; if (panelIdx == 0) panelIdx = 1 }
    }
    BackHandler(enabled = compare) { compare = false }

    val actions = remember(vm) {
        LayerActions(
            select = vm::select, flipH = vm::flipH, flipV = vm::flipV, front = vm::bringToFront, back = vm::sendToBack,
            forward = vm::moveForward, backward = vm::moveBackward, lock = vm::toggleLock, duplicate = vm::duplicateSelected,
            delete = vm::deleteSelected, fit = vm::fitSelected, cutout = { sheet = Sheet.Cutout },
            opacity = { id, v, commit -> vm.edit(id, commit) { it.copy(opacity = v) } },
            scale = vm::scaleSelected, rotate = vm::rotateSelected, nudge = vm::nudgeSelected,
        )
    }

    Box(modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TopBar(
                canUndo = ui.canUndo, canRedo = ui.canRedo, onBack = { nav.back() }, onUndo = vm::undo, onRedo = vm::redo,
                onSave = { sheet = Sheet.Export }, onMenu = { sheet = Sheet.Menu },
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                TryOnCanvas(
                    layers = layers, viewport = viewport, assets = vm.assets, revision = revision,
                    bodyPath = ui.body.path, sceneAspect = ui.sceneAspect, selectedId = ui.selectedId,
                    darkBackground = ui.darkBackground, mirror = ui.mirror, grid = ui.grid,
                    compare = compare, compareFraction = compareFraction, onCompareFraction = { compareFraction = it },
                    onSelect = vm::select, onBegin = vm::beginGesture, onLive = vm::liveUpdate, onEnd = vm::endGesture,
                    onDelete = vm::deleteLayer, onScale = vm::scaleSelected, onRotate = vm::rotateSelected, onNudge = vm::nudgeSelected,
                    onDoubleTapLayer = vm::fitSelected,
                    poseOverlay = if (showPose) ui.sceneLandmarks else null,
                    showSilhouetteHint = true,
                    modifier = Modifier.fillMaxSize(),
                )
                // Herramientas del lienzo
                Column(
                    Modifier.align(Alignment.CenterEnd).padding(end = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CanvasTool(Icons.Rounded.AutoAwesome, "Sorpréndeme", false) { vm.surprise() }
                    CanvasTool(Icons.Rounded.Flip, "Espejo", ui.mirror) { vm.setMirror(!ui.mirror) }
                    CanvasTool(Icons.Rounded.GridOn, "Cuadrícula", ui.grid) { vm.setGrid(!ui.grid) }
                    CanvasTool(Icons.Rounded.Contrast, if (ui.darkBackground) "Fondo claro" else "Fondo oscuro", ui.darkBackground) { vm.setDarkBackground(!ui.darkBackground) }
                    CanvasTool(Icons.Rounded.Compare, "Comparar antes y después", compare) {
                        if (layers.value.isEmpty()) toast.error("Añade alguna prenda para comparar.") else compare = !compare
                    }
                    ResetZoomButton(viewport) { scope.launch { viewport.reset() } }
                }
                androidx.compose.animation.AnimatedVisibility(compare, Modifier.align(Alignment.TopCenter).padding(top = 10.dp), enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                    Row(Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Antes", style = EvType.caption, color = Color.White)
                        Text("Desliza para comparar", style = EvType.caption, color = Color.White.copy(alpha = 0.7f))
                        Text("Después", style = EvType.caption, color = Color.White)
                    }
                }
                // Estado de procesos
                val status = when {
                    ui.busy == Busy.Body -> "Preparando tu foto…"
                    ui.busy == Busy.Surprise -> "Armando tu look…"
                    ui.busy == Busy.Export -> "Generando la imagen…"
                    ui.adding -> "Cargando prenda…"
                    ui.cutoutBusy.isNotEmpty() -> if (cutoutStatus == CutoutModelStatus.Preparing) "Preparando el recorte…" else "Recortando prenda…"
                    ui.pose.status == PoseStatus.Detecting -> "Analizando tu pose…"
                    else -> null
                }
                AnimatedContent(
                    status, Modifier.align(Alignment.TopCenter).padding(top = if (compare) 52.dp else 10.dp),
                    transitionSpec = { (fadeIn(tween(160)) + slideInVertically { -it }) togetherWith (fadeOut(tween(120)) + slideOutVertically { -it }) },
                    label = "status",
                ) { s ->
                    if (s != null) Row(
                        Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) { EvLoadingDots(Color.White); Text(s, style = EvType.caption, color = Color.White) }
                }
                // Estado inicial: invita a hacer la foto
                androidx.compose.animation.AnimatedVisibility(
                    visible = ui.ready && ui.body.path == null && !emptyDismissed && briefs.isEmpty() && ui.busy != Busy.Body,
                    enter = fadeIn() + scaleIn(initialScale = 0.92f), exit = fadeOut() + scaleOut(targetScale = 0.92f),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    EmptyBodyCard(
                        hasProfilePhoto = ui.profileBodyUrl != null,
                        onCamera = { cameraOpen = true }, onGallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onProfile = vm::useProfileBody, onSkip = { emptyDismissed = true },
                    )
                }
            }
            BottomPanel(
                heightDp = PANEL_HEIGHTS[panelIdx], onIndex = { panelIdx = it },
                header = {
                    EvSegmented(listOf(0, 1), tab, { tab = it }, { if (it == 0) "Prendas" else "Capas${if (briefs.isNotEmpty()) " (${briefs.size})" else ""}" }, Modifier.padding(horizontal = EvSpace.screen))
                },
            ) {
                AnimatedContent(tab, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "panelTab") { t ->
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        if (t == 0) GarmentTray(
                            ui, placed, onPick = vm::addGarment, onCategory = vm::setCategory, onRetry = vm::retryGarments,
                            onGoWardrobe = { nav.goToTab(MainTab.Wardrobe) },
                        ) else LayerPanel(
                            briefs, ui.selectedId, ui.cutoutBusy,
                            if (cutoutStatus == CutoutModelStatus.Preparing) "Preparando el recorte…" else null,
                            when (ui.pose.status) {
                                PoseStatus.NotFound -> if (ui.body.path != null) "No detectamos tu cuerpo: se coloca por proporciones." else null
                                else -> null
                            },
                            actions,
                        )
                    }
                }
            }
        }

        EvConfetti(ui.confetti)

        // ── Hojas y diálogos ────────────────────────────────────────────────────────────────
        val close = { sheet = Sheet.None }
        when (sheet) {
            Sheet.Body -> BodySheet(
                ui, onCamera = { cameraOpen = true }, onGallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onProfile = vm::useProfileBody, onSaveProfile = vm::saveBodyAsProfile, onRemove = vm::removeBody, onDismiss = close,
            )
            Sheet.Export -> ExportSheet(
                ui, onShare = vm::share, onGallery = vm::saveToGallery,
                onLook = { sheet = Sheet.SaveLook }, onPreset = { vm.loadPresets(); sheet = Sheet.SavePreset }, onDismiss = close,
            )
            Sheet.Menu -> MenuSheet(
                ui, showPose, { showPose = it }, onPresets = { vm.loadPresets(); sheet = Sheet.Presets }, onBody = { sheet = Sheet.Body },
                onReplace = vm::setReplaceSame, onClear = { confirmClear = true }, onHelp = { sheet = Sheet.Help }, onDismiss = close,
            )
            Sheet.Help -> HelpSheet(close)
            Sheet.SaveLook -> {
                val wasBusy = remember { ui.confetti }
                SaveLookSheet(ui.busy == Busy.Look, { n, p -> vm.saveAsLook(n, p) }, close)
                LaunchedEffect(ui.confetti) { if (ui.confetti != wasBusy) sheet = Sheet.None }
            }
            Sheet.SavePreset -> {
                val wasConf = remember { ui.confetti }
                SavePresetSheet(ui.busy == Busy.Preset, ui.presets.size, { n, r, o -> vm.savePreset(n, r, o) }, close)
                LaunchedEffect(ui.confetti) { if (ui.confetti != wasConf) sheet = Sheet.None }
            }
            Sheet.Presets -> PresetsSheet(
                ui, canSaveCurrent = briefs.isNotEmpty(), onSaveCurrent = { sheet = Sheet.SavePreset },
                onLoad = vm::applyPreset, onRename = vm::renamePreset, onOverwrite = vm::overwritePreset, onDelete = vm::deletePreset,
                onRetry = { vm.loadPresets(force = true) }, onDismiss = close,
            )
            Sheet.Cutout -> {
                val b = briefs.firstOrNull { it.id == ui.selectedId }
                if (b == null) sheet = Sheet.None else CutoutSheet(
                    b, vm.assets, revision, busy = b.id in ui.cutoutBusy,
                    onSmart = vm::autoCutoutSelected,
                    onBasic = { tol, commit -> vm.setCutout(b.id, tol, commit) },
                    onRestore = { vm.restoreCutout(b.id) }, onDismiss = close,
                )
            }
            Sheet.None -> Unit
        }
        if (confirmClear) ConfirmDialog(
            "¿Vaciar el lienzo?", "Se quitarán todas las prendas (puedes deshacerlo).", "Vaciar",
            onConfirm = { confirmClear = false; vm.clearAll() }, onDismiss = { confirmClear = false },
        )

        AnimatedVisibility(cameraOpen, enter = fadeIn() + scaleIn(initialScale = 0.96f), exit = fadeOut()) {
            TryOnCameraScreen(
                onCaptured = { f -> cameraOpen = false; vm.setBody(f.absolutePath) },
                onGallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onClose = { cameraOpen = false },
            )
        }
    }
}

private fun shareFile(context: Context, file: File) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = if (file.extension.equals("png", true)) "image/png" else "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("Look", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Compartir mi look").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun TopBar(canUndo: Boolean, canRedo: Boolean, onBack: () -> Unit, onUndo: () -> Unit, onRedo: () -> Unit, onSave: () -> Unit, onMenu: () -> Unit) {
    val c = EvTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EvCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, onBack, contentDescription = "Volver", container = c.surface)
        Box(Modifier.weight(1f))
        FadeEnabled(canUndo) { EvCircleButton(Icons.AutoMirrored.Rounded.Undo, { if (canUndo) onUndo() }, contentDescription = "Deshacer", container = c.surface) }
        FadeEnabled(canRedo) { EvCircleButton(Icons.AutoMirrored.Rounded.Redo, { if (canRedo) onRedo() }, contentDescription = "Rehacer", container = c.surface) }
        EvPillButton("Guardar", onSave, style = EvButtonStyle.Light, height = 44.dp, icon = Icons.Rounded.IosShare)
        EvCircleButton(Icons.Rounded.MoreHoriz, onMenu, contentDescription = "Más opciones", container = c.surface)
    }
}

@Composable
private fun FadeEnabled(enabled: Boolean, content: @Composable () -> Unit) {
    val a by animateFloatAsState(if (enabled) 1f else 0.35f, tween(200), label = "fade")
    Box(Modifier.alpha(a)) { content() }
}


@Composable
private fun CanvasTool(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, active: Boolean, onClick: () -> Unit) {
    val bg = if (active) Color.White else Color.Black.copy(alpha = 0.55f)
    val fg = if (active) Color.Black else Color.White
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(bg).evClickable(onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun ResetZoomButton(viewport: Viewport, onClick: () -> Unit) {
    AnimatedVisibility(!viewport.isIdentity, enter = scaleIn(EvMotion.bouncy()) + fadeIn(), exit = scaleOut() + fadeOut()) {
        CanvasTool(Icons.Rounded.CenterFocusStrong, "Centrar lienzo", false, onClick)
    }
}

@Composable
private fun EmptyBodyCard(hasProfilePhoto: Boolean, onCamera: () -> Unit, onGallery: () -> Unit, onProfile: () -> Unit, onSkip: () -> Unit) {
    val c = EvTheme.colors
    Column(
        Modifier.padding(horizontal = 28.dp).clip(RoundedCornerShape(32.dp)).background(c.surface.copy(alpha = 0.94f)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Canvas(Modifier.size(120.dp, 150.dp).floatingBob(amplitudePx = 6f)) {
            translate((size.width - size.width * 0.9f) / 2f, 0f) {
                drawPath(
                    silhouettePath(Size(size.width * 0.9f, size.height)), c.ink.copy(alpha = 0.85f),
                    style = Stroke(3.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(18f, 14f))),
                )
            }
        }
        EvHeadline("Tu *probador* te espera", base = EvType.headline.copy(fontSize = androidx.compose.ui.unit.TextUnit(26f, androidx.compose.ui.unit.TextUnitType.Sp)))
        Text(
            "Hazte una foto de pie, cuerpo entero y fondo liso, y pruébate tu armario encima.",
            style = EvType.body, color = c.inkSoft, textAlign = TextAlign.Center,
        )
        EvPillButton("Hacer una foto", onCamera, Modifier.fillMaxWidth(), style = EvButtonStyle.Light, icon = Icons.Rounded.CameraAlt)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            EvPillButton("Galería", onGallery, Modifier.weight(1f), style = EvButtonStyle.Secondary, height = 48.dp, icon = Icons.Rounded.PhotoLibrary)
            if (hasProfilePhoto) EvPillButton("Mi perfil", onProfile, Modifier.weight(1f), style = EvButtonStyle.Secondary, height = 48.dp, icon = Icons.Rounded.Person)
        }
        Text("Probar sin foto", style = EvType.label, color = c.inkSoft, modifier = Modifier.evClickable(onClick = onSkip).padding(10.dp))
    }
}

/** Panel inferior deslizable: arrastra el asa o tócala para cambiar entre mínimo, medio y completo (con muelle). */
@Composable
private fun BottomPanel(heightDp: Float, onIndex: (Int) -> Unit, header: @Composable () -> Unit, content: @Composable () -> Unit) {
    val c = EvTheme.colors
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptics = rememberEvHaptics()
    val h = remember { Animatable(heightDp) }
    LaunchedEffect(heightDp) { h.animateTo(heightDp, EvMotion.smooth()) }
    val dragState = rememberDraggableState { dy ->
        scope.launch { h.snapTo((h.value - dy / density.density).coerceIn(PANEL_HEIGHTS.first(), PANEL_HEIGHTS.last())) }
    }
    Column(
        Modifier.fillMaxWidth().height(h.value.dp).clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)).background(c.surface).navigationBarsPadding(),
    ) {
        Box(
            Modifier.fillMaxWidth().height(28.dp)
                .draggable(
                    dragState, Orientation.Vertical,
                    onDragStopped = { v ->
                        val projected = h.value - v / density.density * 0.12f
                        val idx = PANEL_HEIGHTS.indices.minBy { kotlin.math.abs(PANEL_HEIGHTS[it] - projected) }
                        haptics.tick()
                        onIndex(idx)
                        h.animateTo(PANEL_HEIGHTS[idx], EvMotion.smooth())
                    },
                )
                .evClickable(haptic = false) { onIndex(if (heightDp < PANEL_HEIGHTS[1] + 1f) 1 else if (heightDp < PANEL_HEIGHTS[2]) 2 else 1) }
                .semantics { contentDescription = "Panel de prendas. Arrastra para ampliar o reducir" },
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.width(44.dp).height(5.dp).clip(CircleShape).background(c.outline)) }
        header()
        Box(Modifier.padding(top = 12.dp)) { content() }
    }
}
