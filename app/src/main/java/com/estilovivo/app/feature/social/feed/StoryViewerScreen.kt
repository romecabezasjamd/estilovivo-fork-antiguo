package com.estilovivo.app.feature.social.feed

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.estilovivo.app.app.LocalNavigator
import com.estilovivo.app.core.data.model.Story
import com.estilovivo.app.core.design.EvMotion
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.LocalEvToast
import com.estilovivo.app.core.design.components.EvButtonStyle
import com.estilovivo.app.core.design.components.EvEmptyState
import com.estilovivo.app.core.design.components.EvLoadingDots
import com.estilovivo.app.core.design.components.EvPillButton
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.rememberEvHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt
import kotlin.random.Random

/** Visor de historias a pantalla completa. */
@Composable
fun StoryViewerScreen(storyId: String, modifier: Modifier = Modifier) {
    val vm: StoryViewerViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    var currentId by rememberSaveable { mutableStateOf(storyId) }
    val toast = LocalEvToast.current
    val haptics = rememberEvHaptics()
    var reactionSeq by remember { mutableIntStateOf(0) }
    val floating = remember { mutableStateListOf<FloatingReaction>() }

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is ViewerEvent.Toast -> if (e.isError) toast.error(e.text) else toast.success(e.text)
                is ViewerEvent.Reacted -> {
                    haptics.confirm()
                    repeat(6) { floating += FloatingReaction(reactionSeq++, e.emoji, Random.nextFloat() * 2f - 1f, it * 90) }
                }
                is ViewerEvent.Deleted -> {
                    toast.success("Historia eliminada")
                    val next = e.nextStoryId
                    if (next == null) nav.back() else { currentId = next; vm.removeStory(e.storyId) }
                }
            }
        }
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        val cursor = remember(state.groups, currentId) { locateStory(state.groups, currentId) }
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { EvLoadingDots(color = Color.White) }
            state.error != null -> ViewerMessage(state.error.orEmpty(), "Reintentar", vm::load, nav::back)
            cursor == null -> ViewerMessage("Esta historia ya no está disponible. Puede que haya caducado (duran 24 horas).", "Cerrar", nav::back, nav::back)
            else -> StoryPlayer(
                groups = state.groups, cursor = cursor, vm = vm, reacting = state.reacting, deleting = state.deleting, floating = floating,
                onGo = { currentId = it.id }, onClose = nav::back,
            )
        }
    }
}

@Composable
private fun ViewerMessage(message: String, action: String, onAction: () -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Box(
            Modifier.align(Alignment.TopEnd).padding(12.dp).size(44.dp).clip(CircleShape).evClickable(onClick = onClose)
                .semantics { contentDescription = "Cerrar" },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Close, null, tint = Color.White) }
        Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(message, style = EvType.body, color = Color.White, textAlign = TextAlign.Center)
            EvPillButton(action, onAction, style = EvButtonStyle.Light, height = 48.dp)
        }
    }
}

private class FloatingReaction(val id: Int, val emoji: String, val dx: Float, val delayMs: Int)

@Composable
private fun StoryPlayer(
    groups: List<StoryGroup>,
    cursor: StoryCursor,
    vm: StoryViewerViewModel,
    reacting: Boolean,
    deleting: Boolean,
    floating: SnapshotStateList<FloatingReaction>,
    onGo: (Story) -> Unit,
    onClose: () -> Unit,
) {
    val group = groups[cursor.group]
    val story = group.stories[cursor.index]
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val progress = remember(story.id) { Animatable(0f) }
    var touching by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var lifecyclePaused by remember { mutableStateOf(false) }
    var imageLoading by remember(story.id) { mutableStateOf(!story.imageUrl.isNullOrBlank()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var dragY by remember { mutableFloatStateOf(0f) }

    val currentCursor by rememberUpdatedState(cursor)
    val currentGroups by rememberUpdatedState(groups)
    val goTo by rememberUpdatedState(onGo)
    val close by rememberUpdatedState(onClose)

    fun goNext() {
        val next = nextStory(currentGroups, currentCursor)
        if (next == null) close() else next.storyIn(currentGroups)?.let(goTo) ?: close()
    }
    fun goPrev() {
        val prev = prevStory(currentGroups, currentCursor)
        prev.storyIn(currentGroups)?.let(goTo)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { lifecyclePaused = true }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { lifecyclePaused = false }

    // Contabiliza la visualización una sola vez
    LaunchedEffect(story.id) { vm.onShown(story) }

    // Temporizador (7 s): se detiene al mantener pulsado, arrastrar, con diálogos, en segundo plano o mientras carga la foto
    val running = !touching && !dragging && !lifecyclePaused && !imageLoading && !confirmDelete && !deleting
    LaunchedEffect(story.id, running) {
        if (running) {
            val remaining = ((1f - progress.value) * STORY_DURATION_MS).roundToInt().coerceAtLeast(1)
            progress.animateTo(1f, tween(remaining, easing = LinearEasing))
            goNext()
        }
    }

    val closeThreshold = with(density) { 140.dp.toPx() }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val f = (dragY / (closeThreshold * 3)).coerceIn(0f, 1f)
                translationY = dragY
                scaleX = 1f - f * 0.25f; scaleY = 1f - f * 0.25f
                alpha = 1f - f * 0.6f
            }
            .clip(if (dragY > 1f) RoundedCornerShape(28.dp) else RoundedCornerShape(0.dp))
            .background(Color.Black),
    ) {
        // Capa de contenido + gestos
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(story.id) {
                    detectTapGestures(
                        onPress = { touching = true; tryAwaitRelease(); touching = false },
                        onTap = { offset -> if (offset.x < size.width / 3f) goPrev() else goNext() },
                    )
                }
                .pointerInput(story.id) {
                    detectVerticalDragGestures(
                        onDragStart = { dragging = true },
                        onVerticalDrag = { change, amount -> change.consume(); dragY = (dragY + amount).coerceAtLeast(0f) },
                        onDragEnd = {
                            dragging = false
                            if (dragY > closeThreshold) close()
                            else scope.launch { animate(dragY, 0f, animationSpec = EvMotion.bouncy()) { v, _ -> dragY = v } }
                        },
                        onDragCancel = {
                            dragging = false
                            scope.launch { animate(dragY, 0f, animationSpec = EvMotion.bouncy()) { v, _ -> dragY = v } }
                        },
                    )
                },
        ) {
            AnimatedContent(
                targetState = story, label = "storyContent", contentKey = { it.id },
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) },
                modifier = Modifier.fillMaxSize(),
            ) { s -> StoryContent(s, onImageState = { loading -> if (s.id == story.id) imageLoading = loading }) }

            // Degradados para leer la cabecera y los controles
            Box(Modifier.fillMaxWidth().height(160.dp).background(scrimBrush(true)))
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(180.dp).background(scrimBrush(false)))
        }

        // Cabecera: progreso + autora + acciones
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                group.stories.forEachIndexed { i, _ ->
                    Box(
                        Modifier.weight(1f).height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)).drawBehind {
                            val f = when { i < cursor.index -> 1f; i == cursor.index -> progress.value; else -> 0f }
                            if (f > 0f) drawRect(Color.White, size = Size(size.width * f, size.height))
                        },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EvAvatar(group.userAvatar, group.userName, 38.dp, contentDescription = null)
                Column(Modifier.weight(1f)) {
                    Text(if (story.isOwn) "Tu historia" else story.userName, style = EvType.label, color = Color.White)
                    Text(relativeTime(story.createdAt), style = EvType.caption, color = Color.White.copy(alpha = 0.7f))
                }
                if (story.isOwn) {
                    ViewerIconButton(Icons.Rounded.DeleteOutline, "Borrar historia") {
                        confirmDelete = true
                    }
                }
                ViewerIconButton(Icons.Rounded.Close, "Cerrar", onClick = onClose)
            }
        }

        // Pie: vistas (propias) o reacciones
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 18.dp)) {
            if (story.isOwn) {
                Row(
                    Modifier.align(Alignment.Center).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).padding(horizontal = 16.dp, vertical = 10.dp)
                        .semantics { contentDescription = "${story.views} visualizaciones" },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.Visibility, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text(if (story.views == 1) "1 visualización" else "${story.views} visualizaciones", style = EvType.label, color = Color.White)
                }
            } else {
                Row(
                    Modifier.align(Alignment.Center).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 4.dp)
                        .alpha(if (reacting) 0.5f else 1f),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    STORY_REACTIONS.forEach { emoji ->
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).evClickable(enabled = !reacting, pressedScale = 0.8f, haptic = false) { vm.react(story, emoji) }
                                .semantics { contentDescription = "Reaccionar con $emoji" },
                            contentAlignment = Alignment.Center,
                        ) { Text(emoji, fontSize = 26.sp) }
                    }
                }
            }
        }

        // Emojis flotantes
        floating.forEach { f -> key(f.id) { FloatingEmoji(f) { floating.remove(f) } } }
    }

    if (confirmDelete) {
        EvConfirmDialog(
            title = "¿Borrar esta historia?",
            message = "Dejará de verse y perderás sus visualizaciones.",
            confirmText = "Borrar",
            onConfirm = { confirmDelete = false; vm.delete(story) },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun StoryContent(story: Story, onImageState: (loading: Boolean) -> Unit) {
    if (story.type == "image" && !story.imageUrl.isNullOrBlank()) {
        var failed by remember(story.id) { mutableStateOf(false) }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(story.imageUrl).crossfade(250).build(),
                contentDescription = story.text ?: "Historia de ${story.userName}",
                contentScale = ContentScale.Crop,
                onState = { st ->
                    onImageState(st is AsyncImagePainter.State.Loading)
                    failed = st is AsyncImagePainter.State.Error
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (failed) Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.BrokenImage, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(48.dp))
                Text("No se pudo cargar la foto", style = EvType.caption, color = Color.White.copy(alpha = 0.7f))
            }
            if (!story.text.isNullOrBlank()) {
                Text(
                    story.text, style = EvType.title.copy(color = Color.White, fontSize = 22.sp, lineHeight = 30.sp), textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 110.dp),
                )
            }
        }
    } else {
        LaunchedEffect(story.id) { onImageState(false) }
        Box(
            Modifier.fillMaxSize().background(Brush.linearGradient(storyGradient(story.id))).padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(story.text.orEmpty(), style = StoryTextStyle.copy(color = Color.White, textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ViewerIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).evClickable(onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
}

/** Emoji que sube desde el pie con un vaivén y se desvanece. */
@Composable
private fun FloatingEmoji(f: FloatingReaction, onDone: () -> Unit) {
    val p = remember { Animatable(0f) }
    val density = LocalDensity.current
    LaunchedEffect(Unit) {
        delay(f.delayMs.toLong())
        p.animateTo(1f, tween(1500, easing = LinearEasing))
        onDone()
    }
    if (p.value > 0f) {
        Box(Modifier.fillMaxSize().navigationBarsPadding(), contentAlignment = Alignment.BottomCenter) {
            Text(
                f.emoji, fontSize = (26 + f.dx * 6).sp,
                modifier = Modifier
                    .offset {
                        val rise = with(density) { (60 + 420 * p.value).dp.toPx() }
                        IntOffset((f.dx * with(density) { 80.dp.toPx() } * (0.4f + p.value)).roundToInt(), -rise.roundToInt())
                    }
                    .graphicsLayer { alpha = (1f - p.value).coerceIn(0f, 1f) * 1.2f; scaleX = 0.8f + 0.5f * p.value; scaleY = scaleX },
            )
        }
    }
}
