package com.estilovivo.app.feature.tryon

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.FlipToBack
import androidx.compose.material.icons.rounded.FlipToFront
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RotateLeft
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.estilovivo.app.core.data.model.Garment
import com.estilovivo.app.core.design.EvMotion
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvSpace
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.EvChip
import com.estilovivo.app.core.design.components.EvEmptyState
import com.estilovivo.app.core.design.components.EvImage
import com.estilovivo.app.core.design.components.EvShimmerBox
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.staggeredEntrance
import kotlin.math.roundToInt

/** Resumen de una capa SIN posición: cambia al editar propiedades, no al arrastrar (el panel no se recompone por fotograma). */
@androidx.compose.runtime.Immutable
data class LayerBrief(
    val id: String, val url: String, val name: String, val locked: Boolean, val opacity: Float,
    val flipX: Boolean, val flipY: Boolean, val cutout: Int, val category: TryCategory,
)

fun TryLayer.brief() = LayerBrief(id, url, name, locked, opacity, flipX, flipY, cutout, category)

/** Acciones del panel de capa (agrupadas para pasar una sola referencia estable). */
@Stable
class LayerActions(
    val select: (String?) -> Unit,
    val flipH: () -> Unit, val flipV: () -> Unit,
    val front: () -> Unit, val back: () -> Unit, val forward: () -> Unit, val backward: () -> Unit,
    val lock: () -> Unit, val duplicate: () -> Unit, val delete: () -> Unit,
    val fit: () -> Unit, val cutout: () -> Unit,
    val opacity: (id: String, value: Float, commit: Boolean) -> Unit,
    val scale: (Float) -> Unit, val rotate: (Float) -> Unit, val nudge: (Float, Float) -> Unit,
)

// ─────────────────────────────────────────────────────────────────────────────────────────────
// Bandeja de prendas
// ─────────────────────────────────────────────────────────────────────────────────────────────

@Composable
internal fun GarmentTray(
    ui: TryOnUi,
    placedIds: Set<String>,
    onPick: (Garment) -> Unit,
    onCategory: (TryCategory?) -> Unit,
    onRetry: () -> Unit,
    onGoWardrobe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = EvTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (ui.categories.size > 1) {
            LazyRow(contentPadding = PaddingValues(horizontal = EvSpace.screen), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "all") { EvChip("Todo", ui.category == null, { onCategory(null) }) }
                items(ui.categories, key = { it.name }) { cat -> EvChip(cat.label, ui.category == cat, { onCategory(cat) }) }
            }
        }
        when {
            ui.garmentsLoading && ui.garments.isEmpty() -> LazyRow(
                contentPadding = PaddingValues(horizontal = EvSpace.screen), horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) { items(5) { EvShimmerBox(Modifier.size(92.dp, 124.dp), RoundedCornerShape(22.dp)) } }
            ui.garmentsError != null -> EvEmptyState(Icons.Rounded.Checkroom, "No pudimos cargar tu armario", ui.garmentsError, actionText = "Reintentar", onAction = onRetry)
            ui.garments.isEmpty() -> EvEmptyState(
                Icons.Rounded.Checkroom, "Tu armario está vacío", "Añade prendas para probártelas aquí.",
                actionText = "Ir al armario", onAction = onGoWardrobe,
            )
            else -> LazyRow(
                contentPadding = PaddingValues(horizontal = EvSpace.screen), horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(ui.visibleGarments, key = { it.id }) { g ->
                    val index = ui.visibleGarments.indexOf(g)
                    GarmentCard(g, g.id in placedIds, { onPick(g) }, Modifier.staggeredEntrance(index))
                }
            }
        }
    }
}

@Composable
private fun GarmentCard(g: Garment, placed: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    val border by animateColorAsState(if (placed) c.ink else c.outline, label = "cardBorder")
    Column(modifier.width(92.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.size(92.dp, 112.dp).clip(RoundedCornerShape(22.dp)).background(c.surface)
                .border(if (placed) 2.dp else 1.dp, border, RoundedCornerShape(22.dp))
                .evClickable(onClick = onClick)
                .semantics { contentDescription = "Probar ${g.name}" },
        ) {
            EvImage(g.imageUrl, g.name, Modifier.matchParentSize().padding(6.dp), ContentScale.Fit)
            androidx.compose.animation.AnimatedVisibility(
                placed, Modifier.align(Alignment.TopEnd).padding(6.dp), enter = scaleIn(EvMotion.bouncy()) + fadeIn(), exit = fadeOut(),
            ) {
                Box(Modifier.size(20.dp).clip(CircleShape).background(c.ink), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, null, tint = c.onInk, modifier = Modifier.size(13.dp))
                }
            }
        }
        Text(g.name, style = EvType.caption, color = c.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

// ─────────────────────────────────────────────────────────────────────────────────────────────
// Panel de capas
// ─────────────────────────────────────────────────────────────────────────────────────────────

@Composable
internal fun LayerPanel(
    briefs: List<LayerBrief>,
    selectedId: String?,
    cutoutBusy: Set<String>,
    cutoutStatusText: String?,
    poseText: String?,
    actions: LayerActions,
    modifier: Modifier = Modifier,
) {
    val c = EvTheme.colors
    val sel = briefs.firstOrNull { it.id == selectedId }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (briefs.isEmpty()) {
            EvEmptyState(Icons.Rounded.Checkroom, "Aún no hay prendas", "Toca una prenda en «Prendas» para probártela.")
            return@Column
        }
        LazyRow(contentPadding = PaddingValues(horizontal = EvSpace.screen), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(briefs.asReversed(), key = { it.id }) { b ->
                val selected = b.id == selectedId
                val ring by animateColorAsState(if (selected) c.ink else c.outline, label = "ring")
                Box(
                    Modifier.size(58.dp).clip(RoundedCornerShape(18.dp)).background(c.surface)
                        .border(if (selected) 2.dp else 1.dp, ring, RoundedCornerShape(18.dp))
                        .evClickable { actions.select(if (selected) null else b.id) }
                        .semantics { contentDescription = "Capa ${b.name}${if (selected) ", seleccionada" else ""}" },
                ) {
                    EvImage(b.url, null, Modifier.matchParentSize().padding(5.dp), ContentScale.Fit)
                    if (b.locked) Icon(Icons.Rounded.Lock, null, tint = c.ink, modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(14.dp))
                    if (b.id in cutoutBusy) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.35f)))
                }
            }
        }
        AnimatedVisibility(sel != null, enter = fadeIn() + expandVertically(EvMotion.smooth()), exit = fadeOut() + shrinkVertically()) {
            if (sel != null) SelectedControls(sel, sel.id in cutoutBusy, cutoutStatusText, poseText, actions)
        }
        if (sel == null) Text(
            "Toca una capa para editarla. Con un dedo la mueves; con dos la escalas y giras.",
            style = EvType.caption, color = c.inkMuted, modifier = Modifier.padding(horizontal = EvSpace.screen),
        )
    }
}

@Composable
private fun SelectedControls(b: LayerBrief, busy: Boolean, statusText: String?, poseText: String?, a: LayerActions) {
    val c = EvTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = EvSpace.screen), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(b.name.ifBlank { b.category.label }, style = EvType.label, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (busy) Text(statusText ?: "Recortando…", style = EvType.caption, color = c.inkSoft)
        }
        poseText?.let { Text(it, style = EvType.caption, color = c.inkMuted) }
        // Opacidad
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Opacidad", style = EvType.caption, color = c.inkSoft, modifier = Modifier.width(72.dp))
            Slider(
                value = b.opacity, onValueChange = { a.opacity(b.id, it, false) }, onValueChangeFinished = { a.opacity(b.id, b.opacity, true) },
                valueRange = 0.05f..1f, modifier = Modifier.weight(1f).semantics { contentDescription = "Opacidad ${(b.opacity * 100).roundToInt()} por ciento" },
                colors = SliderDefaults.colors(thumbColor = c.ink, activeTrackColor = c.ink, inactiveTrackColor = c.outline),
            )
            Text("${(b.opacity * 100).roundToInt()}%", style = EvType.caption, color = c.inkSoft, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
        }
        // Herramientas
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolButton(Icons.Rounded.Accessibility, "Ajustar", a.fit)
            ToolButton(Icons.Rounded.AutoFixHigh, "Recorte", a.cutout)
            ToolButton(Icons.Rounded.Flip, "Voltear", a.flipH, active = b.flipX)
            ToolButton(Icons.Rounded.SwapVert, "Invertir", a.flipV, active = b.flipY)
            ToolButton(Icons.Rounded.FlipToFront, "Al frente", a.front)
            ToolButton(Icons.Rounded.FlipToBack, "Al fondo", a.back)
            ToolButton(Icons.Rounded.KeyboardArrowUp, "Subir", a.forward)
            ToolButton(Icons.Rounded.KeyboardArrowDown, "Bajar", a.backward)
            ToolButton(if (b.locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen, if (b.locked) "Desbloquear" else "Bloquear", a.lock, active = b.locked)
            ToolButton(Icons.Rounded.ContentCopy, "Duplicar", a.duplicate)
            ToolButton(Icons.Rounded.Delete, "Quitar", a.delete, danger = true)
        }
        // Ajuste fino (alternativa accesible a los gestos)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FineRow("Tamaño", Icons.Rounded.Remove, "Reducir", { a.scale(1f / 1.08f) }, Icons.Rounded.Add, "Agrandar", { a.scale(1.08f) })
            FineRow("Giro", Icons.Rounded.RotateLeft, "Girar a la izquierda", { a.rotate(-5f) }, Icons.AutoMirrored.Rounded.RotateRight, "Girar a la derecha", { a.rotate(5f) })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mover", style = EvType.caption, color = c.inkSoft, modifier = Modifier.width(72.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StepButton(Icons.Rounded.ChevronLeft, "Mover a la izquierda") { a.nudge(-0.015f, 0f) }
                    StepButton(Icons.Rounded.ArrowUpward, "Mover arriba") { a.nudge(0f, -0.015f) }
                    StepButton(Icons.Rounded.ArrowDownward, "Mover abajo") { a.nudge(0f, 0.015f) }
                    StepButton(Icons.Rounded.ChevronRight, "Mover a la derecha") { a.nudge(0.015f, 0f) }
                }
            }
        }
    }
}

@Composable
private fun FineRow(label: String, i1: ImageVector, d1: String, f1: () -> Unit, i2: ImageVector, d2: String, f2: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = EvType.caption, color = EvTheme.colors.inkSoft, modifier = Modifier.width(72.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { StepButton(i1, d1, f1); StepButton(i2, d2, f2) }
    }
}

@Composable
internal fun StepButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    val c = EvTheme.colors
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(c.surfaceAlt).evClickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = c.ink, modifier = Modifier.size(20.dp)) }
}

@Composable
internal fun ToolButton(icon: ImageVector, label: String, onClick: () -> Unit, active: Boolean = false, danger: Boolean = false) {
    val c = EvTheme.colors
    val bg by animateColorAsState(if (active) c.ink else c.surfaceAlt, label = "toolBg")
    val fg by animateColorAsState(if (active) c.onInk else if (danger) c.danger else c.ink, label = "toolFg")
    Column(
        Modifier.width(62.dp).evClickable(onClick = onClick).semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp))
        }
        Text(label, style = EvType.caption, color = c.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
