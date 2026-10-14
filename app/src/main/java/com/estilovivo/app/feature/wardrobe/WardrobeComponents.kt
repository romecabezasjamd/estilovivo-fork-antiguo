package com.estilovivo.app.feature.wardrobe

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.estilovivo.app.core.data.model.Garment
import com.estilovivo.app.core.data.session.AppPrefs
import com.estilovivo.app.core.design.EvHaptics
import com.estilovivo.app.core.design.EvMotion
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.SharedKey
import com.estilovivo.app.core.design.components.EvChip
import com.estilovivo.app.core.design.components.EvImage
import com.estilovivo.app.core.design.components.EvLoadingDots
import com.estilovivo.app.core.design.components.EvPillButton
import com.estilovivo.app.core.design.components.EvButtonStyle
import com.estilovivo.app.core.design.components.EvToastKind
import com.estilovivo.app.core.design.components.EvToastState
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.pressScale
import com.estilovivo.app.core.design.rememberEvHaptics
import com.estilovivo.app.core.design.sharedElementIfAvailable
import com.estilovivo.app.core.design.staggeredEntrance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

// ── Háptica que respeta el ajuste de la app ──────────────────────────────────────────────────

/** Como [EvHaptics] pero silenciada si el usuario desactivó la vibración en Ajustes. */
@Stable
class WardrobeHaptics(private val base: EvHaptics, private val enabled: State0) {
    fun click() { if (enabled.value) base.click() }
    fun tick() { if (enabled.value) base.tick() }
    fun confirm() { if (enabled.value) base.confirm() }
    fun reject() { if (enabled.value) base.reject() }
    fun heavy() { if (enabled.value) base.heavy() }
    fun toggle(on: Boolean) { if (enabled.value) base.toggle(on) }
}

typealias State0 = androidx.compose.runtime.State<Boolean>

@Composable
fun rememberWardrobeHaptics(): WardrobeHaptics {
    val base = rememberEvHaptics()
    val prefs = koinInject<AppPrefs>()
    val enabled = prefs.haptics.collectAsStateWithLifecycle(true)
    return remember(base) { WardrobeHaptics(base, enabled) }
}

// ── Eventos de un solo uso → toasts ──────────────────────────────────────────────────────────

/** Recoge los eventos del ViewModel; los mensajes se muestran como toast y el resto se delega. */
@Composable
fun CollectWardrobeEvents(events: Flow<WardrobeEvent>, toast: EvToastState, onOther: (WardrobeEvent) -> Unit = {}) {
    val other = rememberUpdatedState(onOther)
    LaunchedEffect(events) {
        events.collect { e ->
            when (e) {
                is WardrobeEvent.Message -> toast.show(e.text, if (e.error) EvToastKind.Error else EvToastKind.Success)
                else -> other.value(e)
            }
        }
    }
}

// ── Hojas inferiores con cierre animado ─────────────────────────────────────────────────────

/** Una sola hoja por pantalla; el contenido cambia con fundido según la clave. */
@Stable
class SheetHost(val state: SheetState, private val scope: CoroutineScope, private val keyState: MutableState<String?>) {
    val key: String? get() = keyState.value
    fun open(k: String) { keyState.value = k }
    fun close() {
        if (keyState.value == null) return
        scope.launch { state.hide() }.invokeOnCompletion { keyState.value = null }
    }
    internal fun forget() { keyState.value = null }
}

@Composable
fun rememberSheetHost(): SheetHost {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val key = rememberSaveable { mutableStateOf<String?>(null) }
    return remember(state, scope, key) { SheetHost(state, scope, key) }
}

@Composable
fun SheetHostContent(host: SheetHost, content: @Composable ColumnScope.(key: String) -> Unit) {
    val c = EvTheme.colors
    val key = host.key ?: return
    ModalBottomSheet(
        onDismissRequest = { host.forget() },
        sheetState = host.state,
        containerColor = c.bg,
        contentColor = c.ink,
        shape = EvShapes.sheet,
        dragHandle = {
            Box(Modifier.padding(top = 12.dp, bottom = 4.dp).width(44.dp).size(width = 44.dp, height = 5.dp).clip(CircleShape).background(c.outline))
        },
    ) {
        AnimatedContent(
            targetState = key,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            label = "sheetContent",
        ) { k ->
            Column(Modifier.fillMaxWidth().navigationBarsPadding()) { content(k) }
        }
    }
}

// ── Diálogo de confirmación animado ────────────────────────────────────────────────────────

@Composable
fun WardrobeConfirmDialog(
    open: Boolean,
    title: String,
    message: String,
    confirmLabel: String,
    icon: ImageVector,
    destructive: Boolean = true,
    loading: Boolean = false,
    cancelLabel: String = "Cancelar",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = open
    if (!visible.currentState && !visible.targetState) return
    val c = EvTheme.colors
    Dialog(onDismissRequest = { if (!loading) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        AnimatedVisibility(
            visibleState = visible,
            enter = scaleIn(EvMotion.bouncy(), initialScale = 0.8f) + fadeIn(tween(160)),
            exit = scaleOut(tween(150), targetScale = 0.9f) + fadeOut(tween(120)),
        ) {
            Column(
                Modifier.padding(horizontal = 28.dp).widthIn(max = 400.dp).fillMaxWidth()
                    .clip(EvShapes.hero).background(c.surface).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val tint = if (destructive) c.danger else c.ink
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, tint = tint, modifier = Modifier.size(30.dp)) }
                Text(title, style = EvType.title, color = c.ink, textAlign = TextAlign.Center)
                Text(message, style = EvType.body, color = c.inkSoft, textAlign = TextAlign.Center)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    EvPillButton(cancelLabel, onDismiss, Modifier.weight(1f), style = EvButtonStyle.Secondary, enabled = !loading, height = 52.dp)
                    EvPillButton(
                        confirmLabel, onConfirm, Modifier.weight(1f),
                        style = if (destructive) EvButtonStyle.Danger else EvButtonStyle.Primary, loading = loading, height = 52.dp,
                    )
                }
            }
        }
    }
}

// ── Piezas sueltas ───────────────────────────────────────────────────────────────────────────

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = EvType.caption, color = EvTheme.colors.inkMuted, modifier = modifier)
}

/** Selección única en varias líneas. */
@Composable
fun OptionChips(options: List<Option>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> EvChip(text = o.label, selected = o.id == selected, onClick = { onSelect(o.id) }) }
    }
}

/** Chip de categoría con contador. */
@Composable
fun CountChip(label: String, count: Int, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    val bg by animateColorAsState(if (selected) c.ink else c.surface, tween(220), label = "ccBg")
    val fg by animateColorAsState(if (selected) c.onInk else c.inkSoft, tween(220), label = "ccFg")
    val border by animateColorAsState(if (selected) c.ink else c.outline, tween(220), label = "ccBorder")
    val badgeBg by animateColorAsState(if (selected) c.onInk.copy(alpha = 0.18f) else c.surfaceAlt, tween(220), label = "ccBadge")
    Row(
        modifier
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, border, CircleShape)
            .evClickable(pressedScale = 0.92f, onClick = onClick)
            .semantics { stateDescription = if (selected) "Seleccionado" else "" }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = EvType.label, color = fg)
        AnimatedVisibility(visible = count > 0, enter = scaleIn(EvMotion.bouncy()) + fadeIn(), exit = scaleOut() + fadeOut()) {
            Text(
                "$count", style = EvType.caption, color = fg,
                modifier = Modifier.clip(CircleShape).background(badgeBg).padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}

/** Interruptor con muelle. */
@Composable
fun WardrobeSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = EvTheme.colors
    val bg by animateColorAsState(if (checked) c.ink else c.outline, tween(200), label = "swBg")
    val x by animateDpAsState(if (checked) 22.dp else 0.dp, EvMotion.bouncy(), label = "swX")
    val haptics = rememberWardrobeHaptics()
    Box(
        modifier
            .size(width = 54.dp, height = 44.dp)
            .semantics { contentDescription = label; role = Role.Switch; stateDescription = if (checked) "Activado" else "Desactivado" }
            .evClickable(enabled = enabled, haptic = false, role = Role.Switch) { haptics.toggle(!checked); onCheckedChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(width = 52.dp, height = 32.dp).clip(CircleShape).background(bg).padding(4.dp)) {
            Box(Modifier.offset(x = x).size(24.dp).clip(CircleShape).background(c.surface))
        }
    }
}

/** Punto de color para un nombre de color en español/inglés (gris si no se reconoce). */
fun colorDot(name: String): Color = when (WardrobeCatalog.fold(name)) {
    "negro", "black" -> Color(0xFF111111)
    "blanco", "white" -> Color(0xFFFFFFFF)
    "gris", "grey", "gray" -> Color(0xFF9CA3AF)
    "beige", "crema", "cream" -> Color(0xFFE8D9BF)
    "marron", "brown", "camel" -> Color(0xFF8B5E3C)
    "rojo", "red", "burdeos" -> Color(0xFFDC2626)
    "rosa", "pink" -> Color(0xFFF9A8D4)
    "naranja", "orange" -> Color(0xFFF97316)
    "amarillo", "yellow" -> Color(0xFFFACC15)
    "verde", "green", "kaki" -> Color(0xFF22C55E)
    "azul", "blue", "marino" -> Color(0xFF3B82F6)
    "morado", "purple", "lila" -> Color(0xFF8B5CF6)
    "dorado", "gold" -> Color(0xFFCA8A04)
    "plateado", "silver" -> Color(0xFFCBD5E1)
    else -> Color(0xFFB8B8C4)
}

@Composable
fun ColorDot(name: String, modifier: Modifier = Modifier, size: Dp = 14.dp) {
    Box(modifier.size(size).clip(CircleShape).background(colorDot(name)).border(1.dp, EvTheme.colors.outline, CircleShape))
}

/** Marco discontinuo (zona de foto vacía). */
fun Modifier.dashedBorder(color: Color, radius: Dp = 26.dp): Modifier = drawBehind {
    val stroke = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f)))
    drawRoundRect(color, size = Size(size.width, size.height), cornerRadius = CornerRadius(radius.toPx()), style = stroke)
}

@Composable
fun ErrorBanner(message: String?, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    AnimatedVisibility(
        visible = message != null,
        enter = expandVertically(EvMotion.smooth()) + fadeIn(),
        exit = shrinkVertically(EvMotion.snappy()) + fadeOut(),
        modifier = modifier,
    ) {
        Text(
            message.orEmpty(), style = EvType.bodySmall, color = c.danger,
            modifier = Modifier.fillMaxWidth().clip(EvShapes.field).background(c.danger.copy(alpha = 0.10f)).padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

/** Fila de una hoja de acciones. */
@Composable
fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, danger: Boolean = false, enabled: Boolean = true, detail: String? = null) {
    val c = EvTheme.colors
    val tint = if (danger) c.danger else c.ink
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(EvShapes.cardSmall)
            .background(c.surface)
            .evClickable(enabled = enabled, pressedScale = 0.97f, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = if (danger) 0.12f else 0.07f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint.copy(alpha = if (enabled) 1f else 0.4f), modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = EvType.subtitle, color = tint.copy(alpha = if (enabled) 1f else 0.4f))
            if (detail != null) Text(detail, style = EvType.caption, color = c.inkMuted)
        }
    }
}

/** Cabecera de una hoja: miniatura + título/subtítulo. */
@Composable
fun SheetHeader(title: String, subtitle: String?, image: Any?, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    Row(
        modifier.fillMaxWidth().padding(horizontal = EvSheetPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (image != null) EvImage(image, null, Modifier.size(64.dp).clip(EvShapes.cardSmall))
        Column(Modifier.weight(1f)) {
            Text(title, style = EvType.title, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = EvType.bodySmall, color = c.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

val EvSheetPadding = 20.dp

// ── Tarjeta de prenda ────────────────────────────────────────────────────────────────────────

/**
 * Tarjeta de prenda: toque abre el detalle, pulsación larga el menú contextual.
 * [pending] = subiendo (alta optimista); [busy] = operación en curso.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GarmentCard(
    garment: Garment,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int = 0,
    pending: Boolean = false,
    busy: Boolean = false,
    inTrip: Boolean = false,
    shared: Boolean = true,
    aspect: Float = 0.8f,
    showUsage: Boolean = true,
) {
    val c = EvTheme.colors
    val source = remember { MutableInteractionSource() }
    val haptics = rememberWardrobeHaptics()
    Column(
        modifier
            .staggeredEntrance(index)
            .pressScale(source, 0.96f)
            .clip(EvShapes.card)
            .background(c.surface)
            .combinedClickable(
                interactionSource = source,
                indication = null,
                enabled = !pending,
                onClickLabel = "Ver ${garment.name}",
                onLongClickLabel = "Más acciones",
                role = Role.Button,
                onClick = { haptics.click(); onClick() },
                onLongClick = { haptics.heavy(); onLongClick() },
            )
            .padding(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(aspect)) {
            EvImage(
                model = garment.imageUrl,
                contentDescription = garment.name,
                modifier = Modifier.matchParentSize().let { if (pending || !shared) it else it.sharedElementIfAvailable(SharedKey(garment.id)) }
                    .clip(EvShapes.cardSmall).background(c.surfaceAlt),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            )
            if (garment.forSale) {
                Text(
                    "En venta · ${formatPrice(garment.price)}", style = EvType.caption, color = Color.White,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp).clip(CircleShape).background(c.success).padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
            if (inTrip) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp).clip(CircleShape).background(c.surface.copy(alpha = 0.92f))
                        .semantics { contentDescription = "En una maleta de viaje" },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Luggage, null, tint = c.warning, modifier = Modifier.size(16.dp)) }
            }
            if (showUsage && !garment.forSale && !pending) {
                Text(
                    "${garment.usageCount} ${if (garment.usageCount == 1) "uso" else "usos"}", style = EvType.caption, color = c.onInk,
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).clip(CircleShape).background(c.ink.copy(alpha = 0.82f)).padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = pending || busy,
                enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.matchParentSize(),
            ) {
                Box(Modifier.matchParentSize().clip(EvShapes.cardSmall).background(Color.Black.copy(alpha = 0.38f)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        EvLoadingDots(color = Color.White)
                        if (pending) Text("Subiendo…", style = EvType.caption, color = Color.White)
                    }
                }
            }
        }
        Text(
            garment.name, style = EvType.label, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp),
        )
        val meta = listOfNotNull(garment.color.takeIf { it.isNotBlank() }, garment.brand).joinToString(" · ")
        Text(
            meta, style = EvType.bodySmall, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp, top = 2.dp),
        )
    }
}

/** Esqueleto de una tarjeta mientras carga. */
@Composable
fun GarmentCardSkeleton(modifier: Modifier = Modifier) {
    com.estilovivo.app.core.design.components.EvShimmerBox(modifier.fillMaxWidth().aspectRatio(0.66f), EvShapes.card)
}
