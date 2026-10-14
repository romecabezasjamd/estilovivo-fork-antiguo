package com.estilovivo.app.feature.wardrobe

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.estilovivo.app.core.data.model.Garment
import com.estilovivo.app.core.data.model.Look
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.EvButtonStyle
import com.estilovivo.app.core.design.components.EvHeadline
import com.estilovivo.app.core.design.components.EvPillButton
import com.estilovivo.app.core.design.evClickable

// ── Filtros ──────────────────────────────────────────────────────────────────────────────────

@Composable
fun FiltersSheet(
    filters: WardrobeFilters,
    colors: List<String>,
    resultCount: Int,
    onChange: (WardrobeFilters) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        EvHeadline("Afina tu *búsqueda*", Modifier.padding(horizontal = EvSheetPadding, vertical = 6.dp))
        Column(Modifier.padding(horizontal = EvSheetPadding, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            SectionLabel("Temporada")
            OptionChips(WardrobeCatalog.seasons, filters.season, { onChange(filters.copy(season = it)) })
            SectionLabel("Ordenar por")
            OptionChips(SortOrder.entries.map { Option(it.name, it.label) }, filters.sort.name, { id ->
                onChange(filters.copy(sort = SortOrder.entries.first { it.name == id }))
            })
            if (colors.isNotEmpty()) {
                SectionLabel("Color")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DotChip("Todos", null, filters.color == WardrobeCatalog.ALL) { onChange(filters.copy(color = WardrobeCatalog.ALL)) }
                    colors.forEach { name ->
                        DotChip(name.replaceFirstChar { it.uppercase() }, name, filters.color == name) { onChange(filters.copy(color = name)) }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = EvSheetPadding, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EvPillButton("Limpiar", onClear, Modifier.weight(1f), style = EvButtonStyle.Secondary, enabled = filters.advancedActive, height = 54.dp)
            EvPillButton(
                if (resultCount == 1) "Ver 1 prenda" else "Ver $resultCount prendas", onClose, Modifier.weight(2f), height = 54.dp,
            )
        }
    }
}

@Composable
private fun DotChip(text: String, colorName: String?, selected: Boolean, onClick: () -> Unit) {
    val c = EvTheme.colors
    val bg by animateColorAsState(if (selected) c.ink else c.surface, tween(220), label = "dcBg")
    val fg by animateColorAsState(if (selected) c.onInk else c.inkSoft, tween(220), label = "dcFg")
    val border by animateColorAsState(if (selected) c.ink else c.outline, tween(220), label = "dcBorder")
    Row(
        Modifier.heightIn(min = 40.dp).clip(CircleShape).background(bg).border(1.dp, border, CircleShape)
            .evClickable(pressedScale = 0.92f, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (colorName != null) ColorDot(colorName)
        Text(text, style = EvType.label, color = fg)
    }
}

// ── Menú contextual de una prenda ────────────────────────────────────────────────────────────

@Composable
fun GarmentMenuSheet(
    garment: Garment,
    busy: Boolean,
    onWash: () -> Unit,
    onTry: () -> Unit,
    onSell: () -> Unit,
    onCancelSale: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SheetHeader(garment.name, listOfNotNull(WardrobeCatalog.categoryLabel(WardrobeCatalog.categoryOf(garment.type)), garment.color.takeIf { it.isNotBlank() }).joinToString(" · "), garment.imageUrl)
        Column(Modifier.padding(horizontal = EvSheetPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (garment.isWashing) {
                ActionRow(Icons.Rounded.LocalLaundryService, "Sacar de la lavadora", onWash, enabled = !busy)
            } else {
                ActionRow(
                    Icons.Rounded.LocalLaundryService, "Lavar", onWash, enabled = !busy && !garment.forSale,
                    detail = if (garment.forSale) "Retírala de la venta para lavarla" else null,
                )
            }
            ActionRow(Icons.Rounded.AutoAwesome, "Probar", onTry)
            if (garment.forSale) ActionRow(Icons.Rounded.ShoppingBag, "Retirar de la venta", onCancelSale, enabled = !busy)
            else ActionRow(Icons.Rounded.Sell, "Vender", onSell)
            ActionRow(Icons.Rounded.Edit, "Editar", onEdit)
            ActionRow(Icons.Rounded.DeleteOutline, "Eliminar", onDelete, danger = true)
        }
    }
}

// ── Menú de un look ──────────────────────────────────────────────────────────────────────────

@Composable
fun LookMenuSheet(
    look: Look,
    busy: Boolean,
    onEdit: () -> Unit,
    onTogglePublic: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SheetHeader(look.name, "${look.garments.size} prendas · " + if (look.isPublic) "Público" else "Privado", look.imageUrl ?: look.garments.firstOrNull()?.imageUrl)
        Column(Modifier.padding(horizontal = EvSheetPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionRow(Icons.Rounded.Edit, "Editar look", onEdit)
            if (look.isPublic) ActionRow(Icons.Rounded.Lock, "Hacer privado", onTogglePublic, enabled = !busy, detail = "Dejará de verse en la comunidad")
            else ActionRow(Icons.Rounded.Language, "Publicar en la comunidad", onTogglePublic, enabled = !busy)
            ActionRow(Icons.Rounded.DeleteOutline, "Eliminar", onDelete, danger = true)
        }
    }
}
