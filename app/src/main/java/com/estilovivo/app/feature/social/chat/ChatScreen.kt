package com.estilovivo.app.feature.social.chat

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.estilovivo.app.app.LocalNavigator
import com.estilovivo.app.app.ProductDetail
import com.estilovivo.app.app.UserProfile
import com.estilovivo.app.core.data.ImageUpload
import com.estilovivo.app.core.design.EvMotion
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvSpace
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.EvCircleButton
import com.estilovivo.app.core.design.components.EvEmptyState
import com.estilovivo.app.core.design.components.EvImage
import com.estilovivo.app.core.design.components.EvLoadingDots
import com.estilovivo.app.core.design.components.EvShimmerBox
import com.estilovivo.app.core.design.components.LocalEvToast
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.rememberEvHaptics
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import java.io.File
import java.time.LocalDate

private val REACTIONS = listOf("❤️", "👍", "😂", "😮", "😢", "🔥")
private const val HEART = "❤️"

/** Conversación 1 a 1. Ruta: `Chat(conversationId)`. */
@Composable
fun ChatScreen(conversationId: String, modifier: Modifier = Modifier) {
    val vm = koinViewModel<ChatViewModel>(key = "chat-$conversationId", parameters = { parametersOf(conversationId) })
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val toast = LocalEvToast.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val haptics = rememberEvHaptics()

    var text by rememberSaveable { mutableStateOf("") }
    var showAttach by rememberSaveable { mutableStateOf(false) }
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var pickerFor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is ChatEvent.Error -> toast.error(e.message)
                is ChatEvent.RestoreText -> text = if (text.isBlank()) e.text else e.text + "\n" + text
            }
        }
    }

    fun sendUri(uri: Uri) {
        showAttach = false
        scope.launch {
            val upload = ImageUpload.fromUri(context, uri)
            if (upload == null) toast.error("No se pudo leer la imagen. Prueba con otra.")
            else vm.sendImage(upload, uri)
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) sendUri(uri) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) sendUri(uri)
    }
    fun launchCamera() {
        runCatching {
            val dir = File(context.cacheDir, "images").apply { mkdirs() }
            val file = File.createTempFile("chat-", ".jpg", dir)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            cameraUri = uri
            camera.launch(uri)
        }.onFailure { toast.error("No se pudo abrir la cámara.") }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else toast.error("Necesitamos permiso de cámara para hacer la foto. Puedes usar la galería.")
    }

    val openLink: (String) -> Unit = { url ->
        runCatching { uriHandler.openUri(url) }.onFailure { toast.error("No se pudo abrir el enlace.") }
    }

    Box(modifier.fillMaxSize().background(EvTheme.colors.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            ChatHeader(
                state = state,
                onBack = { nav.back() },
                onOpenProfile = { state.conversation?.otherUser?.id?.let { nav.navigate(UserProfile(it)) } },
                onOpenItem = { state.conversation?.itemId?.let { nav.navigate(ProductDetail(it)) } },
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading && state.messages.isEmpty() -> MessagesSkeleton()
                    state.error != null && state.messages.isEmpty() -> EvEmptyState(
                        Icons.Rounded.CloudOff, "No hemos podido abrir el chat", state.error.orEmpty(),
                        actionText = "Reintentar", onAction = vm::retryLoad,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    else -> MessageList(
                        state = state,
                        onReact = { key -> vm.toggleReaction(key, HEART); haptics.toggle(true) },
                        onPicker = { pickerFor = it },
                        onRetry = vm::retry,
                        onDiscard = vm::discard,
                        onLink = openLink,
                        showEmptyHint = !state.loading,
                    )
                }
                // Selector de reacciones (pulsación larga)
                androidx.compose.animation.AnimatedVisibility(pickerFor != null, modifier = Modifier.fillMaxSize(), enter = fadeIn(), exit = fadeOut()) {
                    Box(
                        Modifier.fillMaxSize().background(EvTheme.colors.scrim.copy(alpha = 0.25f)).evClickable(haptic = false, role = null, pressedScale = 1f) { pickerFor = null },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            Modifier.clip(CircleShape).background(EvTheme.colors.surface).padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            REACTIONS.forEach { emoji ->
                                Box(
                                    Modifier.size(46.dp).clip(CircleShape)
                                        .evClickable(pressedScale = 0.8f) { pickerFor?.let { vm.toggleReaction(it, emoji) }; pickerFor = null }
                                        .semantics { contentDescription = "Reaccionar con $emoji" },
                                    contentAlignment = Alignment.Center,
                                ) { Text(emoji, style = EvType.title) }
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(showAttach, enter = expandVertically(EvMotion.smooth()) + fadeIn(), exit = shrinkVertically(EvMotion.snappy()) + fadeOut()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = EvSpace.screen, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AttachOption("Cámara", Icons.Rounded.CameraAlt) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
                        else cameraPermission.launch(Manifest.permission.CAMERA)
                    }
                    AttachOption("Galería", Icons.Rounded.AddPhotoAlternate) {
                        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                }
            }
            Composer(
                text = text,
                attachOpen = showAttach,
                enabled = state.error == null || state.messages.isNotEmpty(),
                onText = { text = it.take(ChatViewModel.MAX_LENGTH); vm.onInputChanged(text) },
                onAttach = { showAttach = !showAttach },
                onSend = { if (vm.sendText(text)) text = "" },
            )
        }
    }
}

// ───────────────────────── Cabecera ─────────────────────────

@Composable
private fun ChatHeader(state: ChatUiState, onBack: () -> Unit, onOpenProfile: () -> Unit, onOpenItem: () -> Unit) {
    val c = EvTheme.colors
    val conversation = state.conversation
    val other = conversation?.otherUser
    val name = other?.name?.ifBlank { null } ?: "Conversación"
    Column(Modifier.animateContentSize(EvMotion.smooth()).padding(horizontal = EvSpace.screen).padding(top = 8.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EvCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, onBack, contentDescription = "Volver")
            Row(
                Modifier.weight(1f).heightIn(min = 48.dp).clip(EvShapes.pill).evClickable(role = null, pressedScale = 0.97f, onClick = onOpenProfile),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ChatAvatar(other?.avatar, name, 44.dp)
                Column {
                    Text(name, style = EvType.subtitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    AnimatedVisibility(state.otherTyping, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        Text("escribiendo…", style = EvType.caption, color = c.accent)
                    }
                }
            }
        }
        if (conversation?.itemTitle != null) {
            Row(
                Modifier.padding(top = 10.dp).fillMaxWidth().clip(EvShapes.cardSmall).background(c.surface)
                    .evClickable(role = null, pressedScale = 0.98f, onClick = onOpenItem).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                EvImage(conversation.itemImage, conversation.itemTitle, Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)))
                Column(Modifier.weight(1f)) {
                    Text(conversation.itemTitle, style = EvType.label, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Ver artículo", style = EvType.caption, color = c.inkMuted)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = c.inkMuted)
            }
        }
    }
}

// ───────────────────────── Lista ─────────────────────────

private sealed interface ChatRow {
    val key: String

    data class Day(override val key: String, val label: String) : ChatRow
    data class Msg(val ui: ChatMessageUi, val showTime: Boolean, val newGroup: Boolean) : ChatRow {
        override val key: String get() = ui.key
    }
}

/** Filas cronológicas con separadores de día y marca de "último de su racha" para mostrar la hora una sola vez. */
private fun buildRows(messages: List<ChatMessageUi>, today: LocalDate): List<ChatRow> {
    val rows = ArrayList<ChatRow>(messages.size + 4)
    var lastDay: LocalDate? = null
    messages.forEachIndexed { i, m ->
        val day = ChatTime.day(m.createdAtIso)
        if (day != null && day != lastDay) {
            rows += ChatRow.Day("day-$day", ChatTime.dayLabel(day, today))
            lastDay = day
        }
        val prev = messages.getOrNull(i - 1)
        val next = messages.getOrNull(i + 1)
        val newGroup = prev == null || prev.mine != m.mine || ChatTime.day(prev.createdAtIso) != day
        val sameRun = next != null && next.mine == m.mine && ChatTime.day(next.createdAtIso) == day &&
            next.createdAtMillis / 60_000 == m.createdAtMillis / 60_000
        rows += ChatRow.Msg(m, showTime = !sameRun, newGroup = newGroup)
    }
    return rows
}

@Composable
private fun MessageList(
    state: ChatUiState,
    onReact: (String) -> Unit,
    onPicker: (String) -> Unit,
    onRetry: (String) -> Unit,
    onDiscard: (String) -> Unit,
    onLink: (String) -> Unit,
    showEmptyHint: Boolean,
) {
    val c = EvTheme.colors
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val rows = remember(state.messages) { buildRows(state.messages, LocalDate.now()) }
    // La lista está invertida (reverseLayout): el índice 0 es lo más reciente y queda pegado al campo de texto
    val atBottom by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 120 } }
    var unseen by remember { mutableIntStateOf(0) }
    val newest = state.messages.lastOrNull()
    var lastKey by remember { mutableStateOf(newest?.key) }

    LaunchedEffect(newest?.key) {
        if (newest != null && newest.key != lastKey) {
            if (atBottom || newest.mine) listState.animateScrollToItem(0) else unseen++
        }
        lastKey = newest?.key
    }
    LaunchedEffect(state.otherTyping) { if (state.otherTyping && atBottom) listState.animateScrollToItem(0) }
    LaunchedEffect(atBottom) { if (atBottom) unseen = 0 }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = EvSpace.screen, vertical = 12.dp),
        ) {
            if (state.otherTyping) {
                item(key = "typing") {
                    Row(Modifier.animateItem().padding(top = 8.dp)) {
                        Box(
                            Modifier.clip(BubbleShapeOther).background(c.surface).padding(horizontal = 16.dp, vertical = 14.dp)
                                .semantics { contentDescription = "Está escribiendo" },
                        ) { EvLoadingDots(color = c.inkMuted) }
                    }
                }
            }
            items(rows.asReversed(), key = { it.key }) { row ->
                when (row) {
                    is ChatRow.Day -> DayChip(row.label, Modifier.animateItem())
                    is ChatRow.Msg -> MessageBubble(
                        msg = row.ui,
                        showTime = row.showTime,
                        topGap = if (row.newGroup) 10.dp else 3.dp,
                        reaction = state.reactions[row.ui.key],
                        onDoubleTap = { onReact(row.ui.key) },
                        onLongPress = { onPicker(row.ui.key) },
                        onRetry = { onRetry(row.ui.key) },
                        onDiscard = { onDiscard(row.ui.key) },
                        onLink = onLink,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
        if (rows.isEmpty() && showEmptyHint) {
            Text(
                "Escribe el primer mensaje 👋",
                style = EvType.body, color = c.inkMuted,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        // Salto al final cuando hay mensajes nuevos fuera de pantalla
        AnimatedVisibility(
            visible = !atBottom,
            enter = scaleIn(EvMotion.bouncy()) + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = EvSpace.screen, bottom = 12.dp),
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                EvCircleButton(
                    Icons.Rounded.KeyboardArrowDown,
                    { scope.launch { listState.animateScrollToItem(0) } },
                    contentDescription = if (unseen > 0) "Ir al final, $unseen mensajes nuevos" else "Ir al final",
                    container = c.ink, tint = c.onInk,
                )
                if (unseen > 0) {
                    Text(
                        if (unseen > 9) "9+" else unseen.toString(), style = EvType.caption, color = c.onInk,
                        modifier = Modifier.offset(x = 4.dp, y = (-4).dp).clip(CircleShape).background(c.accent).padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayChip(label: String, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    Box(modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(
            label, style = EvType.caption, color = c.inkSoft,
            modifier = Modifier.clip(CircleShape).background(c.surfaceAlt).padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

private val BubbleShapeMine = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp)
private val BubbleShapeOther = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    msg: ChatMessageUi,
    showTime: Boolean,
    topGap: androidx.compose.ui.unit.Dp,
    reaction: String?,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
    onLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = EvTheme.colors
    val haptics = LocalHapticFeedback.current
    // Sólo se anima lo que acaba de llegar; al volver a una fila antigua al hacer scroll no se repite
    val fresh = remember { msg.arrivedAt > 0L && System.currentTimeMillis() - msg.arrivedAt < 2_500L }
    val enter = remember { Animatable(if (fresh) 0f else 1f) }
    LaunchedEffect(Unit) { if (fresh) enter.animateTo(1f, EvMotion.bouncy()) }

    val bubbleColor = if (msg.mine) c.ink else c.surface
    val textColor = if (msg.mine) c.onInk else c.ink
    val failed = msg.status == SendStatus.Failed
    val description = buildString {
        append(if (msg.mine) "Tú" else "Mensaje recibido")
        append(", ").append(ChatTime.hour(msg.createdAtIso)).append(". ")
        append(if (msg.content.isNotBlank()) msg.content else "Foto")
        reaction?.let { append(". Reacción $it") }
        if (failed) append(". No enviado")
    }
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = topGap)
            .graphicsLayer {
                val p = enter.value
                alpha = p
                translationY = (1f - p) * 36f
                val s = 0.9f + 0.1f * p
                scaleX = s; scaleY = s
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (msg.mine) 1f else 0f, 1f)
            },
        horizontalAlignment = if (msg.mine) Alignment.End else Alignment.Start,
    ) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .padding(bottom = if (reaction != null) 10.dp else 0.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = description
                    customActions = listOf(
                        CustomAccessibilityAction("Reaccionar con corazón") { onDoubleTap(); true },
                        CustomAccessibilityAction("Elegir reacción") { onLongPress(); true },
                    )
                },
        ) {
            Column(
                Modifier
                    .clip(if (msg.mine) BubbleShapeMine else BubbleShapeOther)
                    .background(bubbleColor)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                        onDoubleClick = onDoubleTap,
                        onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onLongPress() },
                    ),
            ) {
                if (msg.image != null) {
                    Box(Modifier.widthIn(max = 240.dp).padding(4.dp)) {
                        EvImage(
                            msg.image, "Foto enviada",
                            Modifier.fillMaxWidth().aspectRatio(0.85f).clip(RoundedCornerShape(16.dp)),
                        )
                        if (msg.status == SendStatus.Sending) {
                            Box(Modifier.matchParentSizeScrim(), contentAlignment = Alignment.Center) { EvLoadingDots(color = androidx.compose.ui.graphics.Color.White) }
                        }
                    }
                }
                if (msg.content.isNotBlank()) {
                    val annotated = rememberLinkedText(msg.content, textColor, onLink)
                    Text(
                        annotated, style = EvType.body, color = textColor,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = if (msg.image != null) 8.dp else 10.dp),
                    )
                }
            }
            if (reaction != null) {
                ReactionBadge(reaction, Modifier.align(if (msg.mine) Alignment.BottomStart else Alignment.BottomEnd).offset(y = 12.dp, x = if (msg.mine) (-6).dp else 6.dp))
            }
        }
        if (failed) {
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ErrorOutline, null, tint = c.danger, modifier = Modifier.size(14.dp))
                Text("No enviada", style = EvType.caption, color = c.danger)
                Box(Modifier.heightIn(min = 44.dp).evClickable(onClick = onRetry).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                    Text("Reintentar", style = EvType.caption.copy(fontWeight = FontWeight.Bold), color = c.ink)
                }
                Box(Modifier.heightIn(min = 44.dp).evClickable(onClick = onDiscard).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                    Text("Descartar", style = EvType.caption, color = c.inkMuted)
                }
            }
        } else if (showTime || msg.status == SendStatus.Sending) {
            Row(Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(ChatTime.hour(msg.createdAtIso), style = EvType.caption, color = c.inkMuted)
                if (msg.mine) {
                    Icon(
                        if (msg.status == SendStatus.Sending) Icons.Rounded.Schedule else Icons.Rounded.Check,
                        null, tint = c.inkMuted, modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

private fun Modifier.matchParentSizeScrim(): Modifier =
    this.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f))

@Composable
private fun ReactionBadge(emoji: String, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    // Aparece con un rebote cada vez que cambia la reacción
    val scale = remember(emoji) { Animatable(0.3f) }
    LaunchedEffect(emoji) { scale.animateTo(1f, EvMotion.bouncy()) }
    Box(
        modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .clip(CircleShape).background(c.bg).padding(3.dp).clip(CircleShape).background(c.surface).padding(horizontal = 6.dp, vertical = 2.dp),
    ) { Text(emoji, style = EvType.bodySmall) }
}

@Composable
private fun rememberLinkedText(content: String, color: androidx.compose.ui.graphics.Color, onLink: (String) -> Unit): AnnotatedString =
    remember(content, color) {
        val links = findLinks(content)
        if (links.isEmpty()) AnnotatedString(content)
        else buildAnnotatedString {
            var cursor = 0
            links.forEach { link ->
                append(content.substring(cursor, link.start))
                withLink(
                    LinkAnnotation.Url(
                        link.url,
                        TextLinkStyles(SpanStyle(color = color, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.SemiBold)),
                        LinkInteractionListener { onLink(link.url) },
                    ),
                ) { append(content.substring(link.start, link.end)) }
                cursor = link.end
            }
            append(content.substring(cursor))
        }
    }

// ───────────────────────── Compositor ─────────────────────────

@Composable
private fun AttachOption(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val c = EvTheme.colors
    Row(
        Modifier.heightIn(min = 44.dp).clip(CircleShape).background(c.surface).evClickable(onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = c.ink, modifier = Modifier.size(18.dp))
        Text(label, style = EvType.label, color = c.ink)
    }
}

@Composable
private fun Composer(
    text: String,
    attachOpen: Boolean,
    enabled: Boolean,
    onText: (String) -> Unit,
    onAttach: () -> Unit,
    onSend: () -> Unit,
) {
    val c = EvTheme.colors
    val canSend = enabled && text.isNotBlank()
    val sendScale by animateFloatAsState(if (canSend) 1f else 0.88f, EvMotion.bouncy(), label = "sendScale")
    Row(
        Modifier.fillMaxWidth().padding(horizontal = EvSpace.screen, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EvCircleButton(
            if (attachOpen) Icons.Rounded.Close else Icons.Rounded.AddPhotoAlternate, onAttach,
            contentDescription = if (attachOpen) "Cerrar adjuntos" else "Adjuntar foto", size = 48.dp,
        )
        Box(
            Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(c.surface)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (text.isEmpty()) Text("Escribe un mensaje…", style = EvType.body, color = c.inkMuted)
            BasicTextField(
                value = text,
                onValueChange = onText,
                enabled = enabled,
                maxLines = 5,
                textStyle = EvType.body.copy(color = c.ink),
                cursorBrush = SolidColor(c.ink),
                keyboardOptions = KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Mensaje" },
            )
        }
        Box(Modifier.graphicsLayer { scaleX = sendScale; scaleY = sendScale }) {
            EvCircleButton(
                Icons.AutoMirrored.Rounded.Send, { if (canSend) onSend() },
                contentDescription = "Enviar", size = 48.dp,
                container = if (canSend) c.ink else c.surfaceAlt, tint = if (canSend) c.onInk else c.inkMuted,
            )
        }
    }
}

@Composable
private fun MessagesSkeleton() {
    Column(Modifier.fillMaxSize().padding(EvSpace.screen), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.Start) {
        listOf(0.55f to false, 0.7f to true, 0.4f to false, 0.6f to true, 0.5f to false).forEach { (w, mine) ->
            Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
                EvShimmerBox(Modifier.fillMaxWidth(w).height(46.dp), RoundedCornerShape(20.dp))
            }
        }
    }
}
