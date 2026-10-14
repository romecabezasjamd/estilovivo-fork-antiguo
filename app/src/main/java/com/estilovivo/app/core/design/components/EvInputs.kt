package com.estilovivo.app.core.design.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.estilovivo.app.core.design.EvMotion
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvSpace
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.evClickable

/** Chip de filtro: seleccionado = tinta (negro) con texto claro. El color y la escala se animan. */
@Composable
fun EvChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
) {
    val c = EvTheme.colors
    val bg by animateColorAsState(if (selected) c.brand else c.surface, tween(220), label = "chipBg")
    val fg by animateColorAsState(if (selected) c.onBrand else c.inkSoft, tween(220), label = "chipFg")
    val borderColor by animateColorAsState(if (selected) c.brand else c.outline, tween(220), label = "chipBorder")
    Row(
        modifier
            .heightIn(min = 40.dp)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, borderColor, CircleShape)
            .evClickable(pressedScale = 0.92f, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (leading != null) Icon(leading, null, tint = fg, modifier = Modifier.size(16.dp))
        Text(text, style = EvType.label, color = fg)
    }
}

/** Fila horizontal de chips con selección única. */
@Composable
fun <T> EvChipRow(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = EvSpace.screen),
) {
    LazyRow(modifier, contentPadding = contentPadding, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items, key = { label(it) }) { item ->
            EvChip(text = label(item), selected = item == selected, onClick = { onSelect(item) })
        }
    }
}

/** Píldora de búsqueda con botón circular oscuro de filtros (como en la referencia). */
@Composable
fun EvSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Buscar",
    onSearch: (() -> Unit)? = null,
    onFilterClick: (() -> Unit)? = null,
    filterActive: Boolean = false,
) {
    val c = EvTheme.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val borderColor by animateColorAsState(if (focused) c.ink else c.outline, tween(200), label = "searchBorder")
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier
                .weight(1f)
                .height(52.dp)
                .clip(CircleShape)
                .background(c.surface)
                .border(1.dp, borderColor, CircleShape)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.Search, null, tint = c.inkMuted, modifier = Modifier.size(20.dp))
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Text(placeholder, style = EvType.body, color = c.inkMuted)
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    interactionSource = source,
                    textStyle = EvType.body.copy(color = c.ink),
                    cursorBrush = SolidColor(c.ink),
                    keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (value.isNotEmpty()) {
                Icon(
                    Icons.Rounded.Close, "Borrar", tint = c.inkMuted,
                    modifier = Modifier.size(20.dp).evClickable(pressedScale = 0.8f) { onValueChange("") },
                )
            }
        }
        if (onFilterClick != null) {
            EvCircleButton(
                icon = Icons.Rounded.Tune, onClick = onFilterClick, contentDescription = "Filtros",
                size = 52.dp, container = c.ink, tint = c.onInk, badge = filterActive,
            )
        }
    }
}

/** Campo de texto con etiqueta flotante, borde que reacciona al foco y mensaje de error. */
@Composable
fun EvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    error: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    enabled: Boolean = true,
    textStyle: TextStyle = EvType.body,
) {
    val c = EvTheme.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val floating = focused || value.isNotEmpty()
    val borderColor by animateColorAsState(
        when { error != null -> c.danger; focused -> c.ink; else -> c.outline }, tween(200), label = "fieldBorder",
    )
    val labelScale by animateFloatAsState(if (floating) 0.78f else 1f, EvMotion.snappy(), label = "labelScale")
    val labelY by animateDpAsState(if (floating) 0.dp else 12.dp, EvMotion.snappy(), label = "labelY")
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .clip(EvShapes.field)
                .background(c.surface)
                .border(BorderStroke(if (focused || error != null) 1.6.dp else 1.dp, borderColor), EvShapes.field)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (leadingIcon != null) Icon(leadingIcon, null, tint = if (focused) c.ink else c.inkMuted, modifier = Modifier.size(20.dp))
            Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    label,
                    style = EvType.body,
                    color = if (focused) c.ink else c.inkMuted,
                    modifier = Modifier
                        .padding(top = labelY)
                        .graphicsLayer {
                            scaleX = labelScale; scaleY = labelScale
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        },
                )
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = singleLine,
                    minLines = minLines,
                    interactionSource = source,
                    visualTransformation = visualTransformation,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    textStyle = textStyle.copy(color = c.ink),
                    cursorBrush = SolidColor(c.ink),
                    modifier = Modifier.fillMaxWidth().padding(top = if (floating) 18.dp else 0.dp),
                )
            }
            trailing?.invoke()
        }
        if (error != null) {
            Text(error, style = EvType.caption, color = c.danger, modifier = Modifier.padding(start = 16.dp, top = 6.dp))
        }
    }
}

/** Selector segmentado (Mensual / Semanal / Diario...). El indicador se desliza con un muelle. */
@Composable
fun <T> EvSegmented(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    val c = EvTheme.colors
    Row(
        modifier.clip(CircleShape).background(c.surfaceAlt).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { option ->
            val isSel = option == selected
            val bg by animateColorAsState(if (isSel) c.brand else androidx.compose.ui.graphics.Color.Transparent, tween(220), label = "segBg")
            val fg by animateColorAsState(if (isSel) c.onBrand else c.inkSoft, tween(220), label = "segFg")
            Box(
                Modifier.weight(1f).height(38.dp).clip(CircleShape).background(bg)
                    .evClickable(pressedScale = 0.95f) { onSelect(option) }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label(option), style = EvType.label, color = fg) }
        }
    }
}
