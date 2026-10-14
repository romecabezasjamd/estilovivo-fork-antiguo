package com.estilovivo.app.feature.suitcase

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.estilovivo.app.core.data.model.Garment
import com.estilovivo.app.core.design.EvMotion
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvSpace
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.EvButtonStyle
import com.estilovivo.app.core.design.components.EvImage
import com.estilovivo.app.core.design.components.EvLoadingDots
import com.estilovivo.app.core.design.components.EvPillButton
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.floatingBob
import com.estilovivo.app.core.design.rememberEvHaptics
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.min

// ───────────── Progreso ─────────────

/** Fracción que se anima desde 0 la primera vez (entrada) y después con muelle. */
@Composable
fun rememberAnimatedFraction(target: Float, delayMs: Int = 0): Float {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(target) {
        if (anim.value == 0f && delayMs > 0) kotlinx.coroutines.delay(delayMs.toLong())
        anim.animateTo(target, EvMotion.smooth())
    }
    return anim.value
}

/** Barra de progreso en píldora; se pone verde al llegar al 100 %. */
@Composable
fun TripProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    track: Color = EvTheme.colors.surfaceAlt,
    fill: Color = EvTheme.colors.ink,
    complete: Color = EvTheme.colors.success,
) {
    val color by animateColorAsState(if (fraction >= 0.999f) complete else fill, tween(300), label = "barColor")
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(track)) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                // Anchura por escala horizontal: sin relayout durante la animación.
                scaleX = fraction.coerceIn(0f, 1f)
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
            }.clip(CircleShape).background(color),
        )
    }
}

/** Etiqueta de cuenta atrás ("En 5 días", "En curso", "Terminado"). */
@Composable
fun PhaseChip(phase: TripPhase?, modifier: Modifier = Modifier, onDark: Boolean = false) {
    val label = TripTiming.label(phase) ?: return
    val c = EvTheme.colors
    val dot = when (phase) {
        is TripPhase.Ongoing -> c.success
        is TripPhase.Upcoming -> c.accent
        else -> c.inkMuted
    }
    val bg = if (onDark) c.onInk.copy(alpha = 0.14f) else c.surfaceAlt
    val fg = if (onDark) c.onInk else c.ink
    Row(
        modifier.clip(CircleShape).background(bg).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        Text(label, style = EvType.caption, color = fg, maxLines = 1)
    }
}

// ───────────── Checklist ─────────────

/** Casilla redonda con muelle: el fondo se rellena y el check entra con rebote. Zona táctil de 44 dp. */
@Composable
fun AnimatedCheck(
    checked: Boolean,
    onToggle: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 28.dp,
) {
    val c = EvTheme.colors
    val fill by animateColorAsState(if (checked) c.ink else Color.Transparent, tween(200), label = "checkFill")
    val border by animateColorAsState(if (checked) c.ink else c.inkMuted, tween(200), label = "checkBorder")
    val tick by animateFloatAsState(if (checked) 1f else 0f, EvMotion.bouncy(), label = "checkTick")
    val pop = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(checked) {
        if (first) { first = false; return@LaunchedEffect }
        pop.snapTo(0.72f)
        pop.animateTo(1f, EvMotion.bouncy())
    }
    val haptics = rememberEvHaptics()
    Box(
        modifier
            .size(44.dp)
            .semantics {
                contentDescription = label
                role = Role.Checkbox
                stateDescription = if (checked) "Hecho" else "Pendiente"
            }
            .evClickable(enabled = enabled, haptic = false, role = Role.Checkbox) {
                haptics.toggle(!checked)
                onToggle()
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                .clip(CircleShape)
                .background(fill)
                .border(2.dp, border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Check, null, tint = c.onInk,
                modifier = Modifier.size(size * 0.66f).graphicsLayer { scaleX = tick; scaleY = tick; alpha = tick.coerceIn(0f, 1f) },
            )
        }
    }
}

/** Texto cuyo tachado se dibuja de izquierda a derecha (también en varias líneas). */
@Composable
fun StrikeText(
    text: String,
    struck: Boolean,
    modifier: Modifier = Modifier,
    style: TextStyle = EvType.subtitle,
) {
    val c = EvTheme.colors
    val progress by animateFloatAsState(if (struck) 1f else 0f, tween(340, easing = EvMotion.emphasized), label = "strike")
    val color by animateColorAsState(if (struck) c.inkMuted else c.ink, tween(260), label = "strikeColor")
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val lineColor = c.inkMuted
    Text(
        text, style = style, color = color, onTextLayout = { layout = it },
        modifier = modifier.drawWithContent {
            drawContent()
            val l = layout ?: return@drawWithContent
            if (progress <= 0f) return@drawWithContent
            var total = 0f
            for (i in 0 until l.lineCount) total += l.getLineRight(i) - l.getLineLeft(i)
            var remaining = total * progress
            for (i in 0 until l.lineCount) {
                val left = l.getLineLeft(i)
                val w = l.getLineRight(i) - left
                val len = min(w, remaining)
                if (len <= 0f) break
                val y = (l.getLineTop(i) + l.getLineBottom(i)) / 2f
                drawLine(lineColor, Offset(left, y), Offset(left + len, y), strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
                remaining -= len
            }
        },
        maxLines = 3, overflow = TextOverflow.Ellipsis,
    )
}

// ───────────── Botones pequeños ─────────────

/** Píldora compacta (para tarjetas estrechas). */
@Composable
fun MiniPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: EvButtonStyle = EvButtonStyle.Primary,
    icon: ImageVector? = null,
    loading: Boolean = false,
    enabled: Boolean = true,
) {
    val c = EvTheme.colors
    val (bg, fg, border) = when (style) {
        EvButtonStyle.Primary -> Triple(c.ink, c.onInk, Color.Transparent)
        EvButtonStyle.Danger -> Triple(c.danger, Color.White, Color.Transparent)
        else -> Triple(c.surface, c.ink, c.outline)
    }
    val animatedBg by animateColorAsState(if (enabled) bg else bg.copy(alpha = 0.4f), tween(200), label = "miniBg")
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .background(animatedBg)
            .border(1.dp, border, CircleShape)
            .evClickable(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) EvLoadingDots(color = fg) else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
                Text(text, style = EvType.label, color = fg, maxLines = 1)
            }
        }
    }
}

// ───────────── Diálogo de confirmación ─────────────

@Composable
fun SuitcaseConfirmDialog(
    open: Boolean,
    title: String,
    message: String,
    confirmLabel: String,
    icon: ImageVector,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    loading: Boolean = false,
    destructive: Boolean = true,
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
                Box(Modifier.size(64.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(30.dp))
                }
                Text(title, style = EvType.title, color = c.ink, textAlign = TextAlign.Center)
                Text(message, style = EvType.body, color = c.inkSoft, textAlign = TextAlign.Center)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    EvPillButton("Cancelar", onDismiss, Modifier.weight(1f), style = EvButtonStyle.Secondary, enabled = !loading, height = 52.dp)
                    EvPillButton(
                        confirmLabel, onConfirm, Modifier.weight(1f),
                        style = if (destructive) EvButtonStyle.Danger else EvButtonStyle.Primary, loading = loading, height = 52.dp,
                    )
                }
            }
        }
    }
}

// ───────────── Prendas ─────────────

/** Prenda seleccionable: el check entra con rebote y la foto se encoge un poco al elegirla. */
@Composable
fun GarmentPickTile(
    garment: Garment,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
) {
    val c = EvTheme.colors
    val border by animateColorAsState(if (selected) c.ink else Color.Transparent, tween(200), label = "tileBorder")
    val inset by animateFloatAsState(if (selected) 0.94f else 1f, EvMotion.bouncy(), label = "tileInset")
    val badge by animateFloatAsState(if (selected) 1f else 0f, EvMotion.bouncy(), label = "tileBadge")
    val label = garment.name.ifBlank { garment.type }
    Box(
        modifier
            .aspectRatio(0.82f)
            .clip(EvShapes.cardSmall)
            .background(c.surface)
            .border(2.5.dp, border, EvShapes.cardSmall)
            .semantics { stateDescription = if (selected) "En la maleta" else "No está en la maleta" }
            .evClickable(pressedScale = 0.95f, role = Role.Checkbox, onClick = onClick),
    ) {
        EvImage(
            garment.imageUrl, label,
            Modifier.fillMaxSize().graphicsLayer { scaleX = inset; scaleY = inset }.clip(EvShapes.cardSmall),
        )
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xAA000000))))
                .padding(start = 8.dp, end = 8.dp, top = 22.dp, bottom = 8.dp),
        ) { Text(label, style = EvType.caption, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        if (badge > 0.01f) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(8.dp).size(26.dp)
                    .graphicsLayer { scaleX = badge; scaleY = badge }
                    .clip(CircleShape).background(c.ink),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Check, "Seleccionada", tint = c.onInk, modifier = Modifier.size(16.dp)) }
        }
        if (onRemove != null) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(4.dp).size(44.dp)
                    .evClickable(pressedScale = 0.85f, onClick = onRemove)
                    .semantics { contentDescription = "Quitar $label de la maleta" },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, null, tint = Color.White, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}

/** Rejilla de prendas dentro de un `LazyColumn` (filas de [columns]). */
fun LazyListScope.garmentRows(
    garments: List<Garment>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    columns: Int = 3,
    keyPrefix: String = "g",
) {
    val rows = garments.chunked(columns)
    items(rows.size, key = { "$keyPrefix-${rows[it].first().id}" }) { index ->
        val row = rows[index]
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { g ->
                GarmentPickTile(g, selected = g.id in selected, onClick = { onToggle(g.id) }, modifier = Modifier.weight(1f))
            }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

// ───────────── Ilustración ─────────────

/** Maleta dibujada con asas, correas y pegatinas que flota suavemente. */
@Composable
fun SuitcaseIllustration(modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    val body = c.ink
    val strap = c.onInk.copy(alpha = 0.18f)
    val accent = c.accent
    Canvas(modifier.size(150.dp).floatingBob(amplitudePx = 7f)) {
        val w = size.width
        val h = size.height
        val bodyTop = h * 0.26f
        val bodyH = h * 0.66f
        // Asa
        drawRoundRect(
            body, topLeft = Offset(w * 0.34f, h * 0.08f), size = Size(w * 0.32f, h * 0.3f),
            cornerRadius = CornerRadius(w * 0.08f), style = Stroke(width = w * 0.05f),
        )
        // Cuerpo
        drawRoundRect(body, Offset(w * 0.08f, bodyTop), Size(w * 0.84f, bodyH), CornerRadius(w * 0.14f))
        // Correas
        drawRoundRect(strap, Offset(w * 0.26f, bodyTop), Size(w * 0.07f, bodyH), CornerRadius(0f))
        drawRoundRect(strap, Offset(w * 0.67f, bodyTop), Size(w * 0.07f, bodyH), CornerRadius(0f))
        // Pegatinas
        drawCircle(accent, radius = w * 0.075f, center = Offset(w * 0.46f, h * 0.52f))
        drawCircle(Color(0xFFFFC145), radius = w * 0.055f, center = Offset(w * 0.6f, h * 0.7f))
        drawRoundRect(Color(0xFF60A5FA), Offset(w * 0.38f, h * 0.68f), Size(w * 0.1f, w * 0.1f), CornerRadius(w * 0.03f))
        // Ruedas
        drawCircle(c.inkSoft, radius = w * 0.04f, center = Offset(w * 0.24f, h * 0.95f))
        drawCircle(c.inkSoft, radius = w * 0.04f, center = Offset(w * 0.76f, h * 0.95f))
    }
}

// ───────────── Selector de fechas ─────────────

/** Campo "Fechas" que abre el selector de rango. */
@Composable
fun DateRangeField(
    start: String,
    end: String,
    today: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    val c = EvTheme.colors
    val border by animateColorAsState(if (error != null) c.danger else c.outline, tween(200), label = "dateBorder")
    val hasDates = start.isNotBlank()
    val effectiveEnd = end.ifBlank { start }
    val length = TripDates.length(start, effectiveEnd)
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(EvShapes.field).background(c.surface)
                .border(1.dp, border, EvShapes.field)
                .evClickable(pressedScale = 0.98f, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = if (hasDates) "Fechas: ${TripDates.range(start, effectiveEnd, today)}. Toca para cambiar" else "Elegir fechas" }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.CalendarMonth, null, tint = if (hasDates) c.ink else c.inkMuted, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                if (hasDates) {
                    Text("Fechas del viaje", style = EvType.caption, color = c.inkMuted)
                    Text(TripDates.range(start, effectiveEnd, today), style = EvType.subtitle, color = c.ink)
                } else {
                    Text("Elige las fechas del viaje", style = EvType.body, color = c.inkMuted)
                }
            }
            if (length != null) {
                Text(
                    if (length == 1) "1 día" else "$length días", style = EvType.caption, color = c.inkSoft,
                    modifier = Modifier.clip(CircleShape).background(c.surfaceAlt).padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
        if (error != null) Text(error, style = EvType.caption, color = c.danger, modifier = Modifier.padding(start = 16.dp, top = 6.dp))
    }
}

private fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** El selector de Material devuelve el día elegido como medianoche UTC: se lee en UTC para no correr un día. */
private fun Long.toPickerDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** Hoja con el selector de rango de Material 3 vestido con los tokens Ev. Fin ≥ inicio lo garantiza el propio selector. */
@Composable
fun DateRangeSheet(
    initialStart: LocalDate?,
    initialEnd: LocalDate?,
    today: LocalDate,
    onConfirm: (start: LocalDate, end: LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = EvTheme.colors
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStart?.toPickerMillis(),
        initialSelectedEndDateMillis = initialEnd?.toPickerMillis(),
        initialDisplayedMonthMillis = (initialStart ?: today).toPickerMillis(),
    )
    val start = state.selectedStartDateMillis?.toPickerDate()
    val end = state.selectedEndDateMillis?.toPickerDate()
    val colors = DatePickerDefaults.colors(
        containerColor = c.bg,
        titleContentColor = c.ink,
        headlineContentColor = c.ink,
        weekdayContentColor = c.inkMuted,
        subheadContentColor = c.inkSoft,
        navigationContentColor = c.ink,
        yearContentColor = c.ink,
        currentYearContentColor = c.ink,
        selectedYearContainerColor = c.ink,
        selectedYearContentColor = c.onInk,
        dayContentColor = c.ink,
        disabledDayContentColor = c.inkMuted,
        selectedDayContainerColor = c.ink,
        selectedDayContentColor = c.onInk,
        todayContentColor = c.ink,
        todayDateBorderColor = c.ink,
        dayInSelectionRangeContainerColor = c.surfaceAlt,
        dayInSelectionRangeContentColor = c.ink,
        dividerColor = c.outline,
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = c.bg,
        contentColor = c.ink,
        shape = EvShapes.sheet,
        dragHandle = {
            Box(Modifier.padding(top = 12.dp, bottom = 4.dp).size(width = 44.dp, height = 5.dp).clip(CircleShape).background(c.outline))
        },
    ) {
        Column(Modifier.fillMaxWidth().fillMaxSize().navigationBarsPadding()) {
            DateRangePicker(
                state = state,
                modifier = Modifier.weight(1f),
                colors = colors,
                title = {
                    Text(
                        "¿Cuándo viajas?", style = EvType.title, color = c.ink,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp),
                    )
                },
                headline = {
                    val label = when {
                        start == null -> "Toca el día de ida"
                        end == null || end == start -> "${TripDates.format(TripDates.iso(start), today, start.year != today.year)} · toca el día de vuelta"
                        else -> TripDates.range(TripDates.iso(start), TripDates.iso(end), today)
                    }
                    Text(label, style = EvType.subtitle, color = c.inkSoft, modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp))
                },
                showModeToggle = false,
            )
            EvPillButton(
                text = when {
                    start == null -> "Elige la ida"
                    end == null || end == start -> "Viajo un solo día"
                    else -> "Confirmar fechas"
                },
                onClick = {
                    val s = start ?: return@EvPillButton
                    scope.launch { sheet.hide() }.invokeOnCompletion { onConfirm(s, end ?: s) }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = EvSpace.screen, vertical = 12.dp),
                enabled = start != null,
            )
        }
    }
}
