package com.estilovivo.app.feature.wardrobe

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.estilovivo.app.app.LocalNavigator
import com.estilovivo.app.core.data.model.Garment
import com.estilovivo.app.core.design.EvMotion
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvSpace
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.EvButtonStyle
import com.estilovivo.app.core.design.components.EvChip
import com.estilovivo.app.core.design.components.EvCircleButton
import com.estilovivo.app.core.design.components.EvConfetti
import com.estilovivo.app.core.design.components.EvEmptyState
import com.estilovivo.app.core.design.components.EvHeadline
import com.estilovivo.app.core.design.components.EvImage
import com.estilovivo.app.core.design.components.EvPillButton
import com.estilovivo.app.core.design.components.EvShimmerBox
import com.estilovivo.app.core.design.components.EvTextField
import com.estilovivo.app.core.design.components.LocalEvToast
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.staggeredEntrance
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun CreateLookScreen(lookId: String?, modifier: Modifier = Modifier) {
    val vm: CreateLookViewModel = koinViewModel(parameters = { parametersOf(lookId.orEmpty()) })
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val toast = LocalEvToast.current
    val host = rememberSheetHost()
    val c = EvTheme.colors
    var discard by rememberSaveable { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var confetti by remember { mutableIntStateOf(0) }

    // El borrador sobrevive a la muerte del proceso
    var savedDraft by rememberSaveable { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(Unit) { LookDraft.decode(savedDraft)?.let(vm::restore) }
    LaunchedEffect(state.draft) { savedDraft = state.draft.encode() }

    CollectWardrobeEvents(vm.events, toast) { e ->
        if (e is WardrobeEvent.OperationResult && e.key == "save" && e.success) {
            closing = true
            confetti++
            host.close()
        }
    }
    LaunchedEffect(closing) {
        if (closing) {
            delay(1300)
            nav.back()
        }
    }

    val dirty = lookId == null && state.draft.selectedIds.isNotEmpty() && !closing
    BackHandler(enabled = dirty && !state.saving) { discard = true }
    fun leave() { if (dirty) discard = true else nav.back() }

    Box(modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = EvSpace.screen, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                EvCircleButton(Icons.Rounded.Close, ::leave, contentDescription = "Cerrar")
                EvHeadline(if (lookId == null) "Nuevo *look*" else "Editar *look*", Modifier.weight(1f), base = EvType.headline.copy(fontSize = 24.sp, lineHeight = 30.sp), accent = EvType.headlineAccent.copy(fontSize = 27.sp))
            }
            when {
                state.loading -> Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = EvSpace.screen)) {
                    EvShimmerBox(Modifier.fillMaxSize().padding(bottom = 16.dp), EvShapes.hero)
                }
                state.notFound -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EvEmptyState(Icons.Rounded.AutoAwesome, "No encontramos este look", "Puede que lo hayas eliminado.", actionText = "Volver", onAction = nav::back)
                }
                state.error != null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EvEmptyState(Icons.Rounded.CloudOff, "No pudimos cargar tu armario", state.error.orEmpty(), actionText = "Reintentar", onAction = vm::load)
                }
                state.garments.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EvEmptyState(Icons.Rounded.Checkroom, "Tu armario está vacío", "Añade prendas para empezar a componer looks.", actionText = "Ir al armario", onAction = nav::back)
                }
                else -> Editor(
                    state = state, modifier = Modifier.weight(1f),
                    onToggle = vm::toggle, onRemove = vm::remove, onSurprise = vm::surprise, onClear = vm::clear,
                    onCategory = vm::setPickerCategory, onSave = { host.open("save") },
                )
            }
        }
        EvConfetti(trigger = confetti)
    }

    SheetHostContent(host) { _ ->
        SaveLookSheet(
            state = state,
            onName = vm::setName, onMood = vm::setMood, onPublic = vm::setPublic,
            onAddTag = vm::addTag, onRemoveTag = vm::removeTag,
            onSave = vm::save, onClose = host::close,
        )
    }

    WardrobeConfirmDialog(
        open = discard,
        title = "¿Descartar este look?",
        message = "Perderás la selección que llevas hasta ahora.",
        confirmLabel = "Descartar",
        icon = Icons.Rounded.Close,
        onConfirm = { discard = false; nav.back() },
        onDismiss = { discard = false },
    )
}

@Composable
private fun Editor(
    state: CreateLookUiState,
    onToggle: (String) -> Unit,
    onRemove: (String) -> Unit,
    onSurprise: () -> Unit,
    onClear: () -> Unit,
    onCategory: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = EvTheme.colors
    var pickerOpen by rememberSaveable { mutableStateOf(true) }
    val selected = state.selected
    Column(modifier.fillMaxSize()) {
        // Lienzo
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = EvSpace.screen).clip(EvShapes.hero).background(c.surfaceAlt),
        ) {
            if (selected.isEmpty()) {
                Column(
                    Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.size(84.dp).clip(CircleShape).background(c.surface), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = c.inkMuted, modifier = Modifier.size(34.dp))
                    }
                    Text("Tu lienzo está vacío", style = EvType.title, color = c.ink)
                    Text("Toca prendas de abajo o deja que te sorprendamos.", style = EvType.body, color = c.inkSoft, modifier = Modifier.padding(horizontal = 12.dp))
                }
            } else {
                LookBoard(WardrobeLogic.lookOrder(selected), onRemove, Modifier.fillMaxSize().padding(14.dp))
                Text(
                    "${selected.size} ${if (selected.size == 1) "prenda" else "prendas"}", style = EvType.caption, color = c.ink,
                    modifier = Modifier.align(Alignment.TopStart).padding(14.dp).clip(CircleShape).background(c.surface.copy(alpha = 0.92f)).padding(horizontal = 12.dp, vertical = 6.dp),
                )
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(44.dp).clip(CircleShape).evClickable(onClick = onClear)
                        .semantics { contentDescription = "Vaciar el lienzo" },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Close, null, tint = c.inkSoft, modifier = Modifier.size(20.dp)) }
            }
            Row(Modifier.align(Alignment.BottomCenter).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EvPillButton("Sorpréndeme", onSurprise, style = EvButtonStyle.Secondary, icon = Icons.Rounded.Shuffle, height = 50.dp)
                EvPillButton("Guardar", onSave, enabled = selected.isNotEmpty(), height = 50.dp)
            }
        }

        // Selector de prendas
        Column(
            Modifier.padding(top = 14.dp).fillMaxWidth().clip(EvShapes.sheet).background(c.surface).navigationBarsPadding().animateContentSize(EvMotion.smooth()),
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).evClickable(pressedScale = 0.99f, haptic = false, onClick = { pickerOpen = !pickerOpen })
                    .padding(horizontal = EvSpace.screen),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Tu armario", style = EvType.title, color = c.ink)
                Text(
                    if (pickerOpen) "${state.garments.size} prendas · ocultar" else "Mostrar prendas",
                    style = EvType.caption, color = c.inkMuted,
                )
            }
            AnimatedVisibility(pickerOpen, enter = expandVertically(EvMotion.smooth()) + fadeIn(), exit = shrinkVertically(EvMotion.snappy()) + fadeOut()) {
                Column(Modifier.padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = EvSpace.screen)) {
                        items(state.pickerCategories, key = { it }) { cat ->
                            EvChip(
                                text = if (cat == WardrobeCatalog.ALL) "Todo" else WardrobeCatalog.categoryLabel(cat),
                                selected = cat == state.pickerCategory, onClick = { onCategory(cat) },
                            )
                        }
                    }
                    if (state.pickerItems.isEmpty()) {
                        Text("No hay prendas en esta categoría.", style = EvType.body, color = c.inkMuted, modifier = Modifier.padding(horizontal = EvSpace.screen, vertical = 20.dp))
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = EvSpace.screen)) {
                            items(state.pickerItems, key = { it.id }) { g ->
                                PickerThumb(g, selected = g.id in state.draft.selectedIds, onClick = { onToggle(g.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerThumb(g: Garment, selected: Boolean, onClick: () -> Unit) {
    val c = EvTheme.colors
    val ring = if (selected) c.ink else androidx.compose.ui.graphics.Color.Transparent
    val tag = when {
        g.isWashing -> "En lavadora"
        g.forSale -> "En venta"
        else -> null
    }
    Box(
        Modifier.size(width = 96.dp, height = 128.dp).clip(EvShapes.cardSmall).background(c.surfaceAlt)
            .border(2.dp, ring, EvShapes.cardSmall)
            .evClickable(pressedScale = 0.94f, role = Role.Checkbox, onClick = onClick)
            .semantics {
                contentDescription = g.name + (tag?.let { ", $it" } ?: "")
                stateDescription = if (selected) "En el look" else "No está en el look"
            },
    ) {
        EvImage(g.imageUrl, null, Modifier.fillMaxSize())
        Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.42f)).padding(horizontal = 6.dp, vertical = 4.dp)) {
            Text(g.name, style = EvType.caption, color = androidx.compose.ui.graphics.Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (tag != null && !selected) {
            Text(
                tag, style = EvType.caption.copy(fontSize = 9.sp), color = c.ink,
                modifier = Modifier.align(Alignment.TopStart).padding(5.dp).clip(CircleShape).background(c.surface.copy(alpha = 0.92f)).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        AnimatedVisibility(selected, enter = scaleIn(EvMotion.bouncy()) + fadeIn(), exit = scaleOut() + fadeOut(), modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(c.ink), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, null, tint = c.onInk, modifier = Modifier.size(15.dp))
            }
        }
    }
}

// ── Lienzo animado ───────────────────────────────────────────────────────────────────────────

/** Posiciones (centro y tamaño) normalizadas de [n] prendas en una cuadrícula centrada. */
internal data class Slot(val cx: Float, val cy: Float, val size: Float)

internal fun boardSlots(n: Int, w: Float, h: Float): List<Slot> {
    if (n <= 0) return emptyList()
    val cols = when { n <= 1 -> 1; n <= 4 -> 2; else -> 3 }
    val rows = ceil(n / cols.toFloat()).toInt()
    val cellW = w / cols
    val cellH = h / rows
    val size = min(cellW / 0.82f, cellH) * 0.9f
    return List(n) { i ->
        val row = i / cols
        val col = i % cols
        val inRow = if (row == rows - 1) n - row * cols else cols
        val startX = (w - inRow * cellW) / 2f
        Slot(startX + col * cellW + cellW / 2f, row * cellH + cellH / 2f, size)
    }
}

@Composable
private fun LookBoard(items: List<Garment>, onRemove: (String) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val slots = boardSlots(items.size, w, h * 0.86f)
        items.forEachIndexed { i, g ->
            androidx.compose.runtime.key(g.id) {
                BoardItem(g, slots[i], rotation = if (i % 2 == 0) -3f else 3f, onRemove = { onRemove(g.id) })
            }
        }
    }
}

@Composable
private fun BoardItem(g: Garment, slot: Slot, rotation: Float, onRemove: () -> Unit) {
    val c = EvTheme.colors
    val density = LocalDensity.current
    val cx by animateFloatAsState(slot.cx, EvMotion.smooth(), label = "cx")
    val cy by animateFloatAsState(slot.cy, EvMotion.smooth(), label = "cy")
    val size by animateFloatAsState(slot.size, EvMotion.smooth(), label = "size")
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, EvMotion.bouncy()) }
    val wPx = size * 0.82f
    Box(
        Modifier
            .offset { IntOffset((cx - wPx / 2f).roundToInt(), (cy - size / 2f).roundToInt()) }
            .size(with(density) { wPx.toDp() }, with(density) { size.toDp() })
            .graphicsLayer {
                val s = appear.value
                scaleX = s; scaleY = s; rotationZ = rotation * s; alpha = (s * 2f).coerceAtMost(1f)
            }
            .shadow(10.dp, EvShapes.cardSmall)
            .clip(EvShapes.cardSmall)
            .background(c.surface)
            .padding(4.dp),
    ) {
        EvImage(g.imageUrl, g.name, Modifier.fillMaxSize().clip(EvShapes.field))
        Box(
            Modifier.align(Alignment.TopEnd).size(44.dp).evClickable(pressedScale = 0.85f, onClick = onRemove)
                .semantics { contentDescription = "Quitar ${g.name} del look" },
            contentAlignment = Alignment.TopEnd,
        ) {
            Box(Modifier.padding(4.dp).size(24.dp).clip(CircleShape).background(c.ink), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, null, tint = c.onInk, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ── Guardar ──────────────────────────────────────────────────────────────────────────────────

@Composable
private fun SaveLookSheet(
    state: CreateLookUiState,
    onName: (String) -> Unit,
    onMood: (String) -> Unit,
    onPublic: (Boolean) -> Unit,
    onAddTag: (String) -> Boolean,
    onRemoveTag: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    val c = EvTheme.colors
    val d = state.draft
    var tagInput by remember { mutableStateOf("") }
    fun commitTag() { if (tagInput.isNotBlank() && onAddTag(tagInput)) tagInput = "" }
    Column(Modifier.fillMaxWidth().imePadding()) {
        EvHeadline("Guarda tu *look*", Modifier.padding(horizontal = EvSheetPadding, vertical = 6.dp))
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = EvSheetPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ErrorBanner(state.saveError)
            EvTextField(d.name, onName, "Nombre del look (p. ej. Domingo de brunch)")
            SectionLabel("Estado de ánimo")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WardrobeCatalog.moods.forEach { m -> EvChip("${m.emoji} ${m.label}", d.mood == m.id, { onMood(m.id) }) }
            }
            SectionLabel("Etiquetas (${d.tags.size}/${CreateLookViewModel.MAX_TAGS})")
            if (d.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    d.tags.forEach { t ->
                        Row(
                            Modifier.heightIn(min = 40.dp).clip(CircleShape).background(c.ink).evClickable(onClick = { onRemoveTag(t) })
                                .semantics { contentDescription = "Quitar la etiqueta $t" }.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("#$t", style = EvType.label, color = c.onInk)
                            Icon(Icons.Rounded.Close, null, tint = c.onInk, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
            AnimatedVisibility(d.tags.size < CreateLookViewModel.MAX_TAGS, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    EvTextField(
                        tagInput, { tagInput = it.take(30) }, "Añade una etiqueta", Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { commitTag() }),
                    )
                    EvPillButton("Añadir", ::commitTag, style = EvButtonStyle.Secondary, enabled = tagInput.isNotBlank(), height = 52.dp)
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(EvShapes.cardSmall).background(c.surface).padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(if (d.isPublic) Icons.Rounded.Language else Icons.Rounded.Lock, null, tint = if (d.isPublic) c.ink else c.inkMuted)
                Column(Modifier.weight(1f)) {
                    Text(if (d.isPublic) "Look público" else "Look privado", style = EvType.subtitle, color = c.ink)
                    Text(if (d.isPublic) "Aparecerá en la comunidad" else "Sólo lo verás tú", style = EvType.caption, color = c.inkMuted)
                }
                WardrobeSwitch(d.isPublic, onPublic, "Look público")
            }
            SectionLabel("Vista previa (${state.selected.size})")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(WardrobeLogic.lookOrder(state.selected), key = { it.id }) { g ->
                    EvImage(g.imageUrl, g.name, Modifier.size(64.dp).clip(EvShapes.field))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = EvSheetPadding, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EvPillButton("Cancelar", onClose, Modifier.weight(1f), style = EvButtonStyle.Secondary, enabled = !state.saving, height = 54.dp)
            EvPillButton(
                if (state.editing != null) "Guardar cambios" else "Guardar look", onSave, Modifier.weight(2f),
                loading = state.saving, enabled = state.selected.isNotEmpty(), height = 54.dp,
            )
        }
    }
}
