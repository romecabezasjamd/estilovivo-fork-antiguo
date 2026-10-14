package com.estilovivo.app.feature.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.estilovivo.app.core.data.session.AppSettings
import com.estilovivo.app.core.design.BrandChoice
import com.estilovivo.app.core.design.BrandMath
import com.estilovivo.app.core.design.BrandPresets
import com.estilovivo.app.core.design.EvColors
import com.estilovivo.app.core.design.EvMotion
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.EvButtonStyle
import com.estilovivo.app.core.design.components.EvHeadline
import com.estilovivo.app.core.design.components.EvPillButton
import com.estilovivo.app.core.design.components.EvTextField
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.rememberEvHaptics
import com.estilovivo.app.core.design.toComposeColor
import com.estilovivo.app.core.design.withBrand

private const val CUSTOM_ID = "custom"

/**
 * Tema de color (Perfil → Ajustes → Tema): carrusel de muestras, vista previa en vivo y color propio.
 * Sólo cambia el color de marca; el resto de la estética editorial queda igual.
 */
@Composable
internal fun ThemePickerSection(
    local: AppSettings,
    onApply: (preset: String, customColor: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = EvTheme.colors
    val haptics = rememberEvHaptics()
    val applied = BrandChoice(local.themePreset, local.customColor)
    val selectedId = if (BrandMath.parseHex(local.customColor) != null) CUSTOM_ID else BrandPresets.normalizeId(local.themePreset)
    var editing by rememberSaveable { mutableStateOf(false) }
    // Borrador del color propio (la vista previa lo enseña antes de aplicarlo)
    var draft by remember { mutableStateOf<BrandChoice?>(null) }
    val previewChoice = if (editing) draft ?: applied else applied

    Column(modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Color de marca", style = EvType.label, color = c.ink)

        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
            items(BrandPresets.all, key = { it.id }) { p ->
                val choice = BrandChoice(p.id)
                val swatch = remember(c.isDark, p.id) { c.withBrand(choice).brand }
                Swatch(
                    label = p.label, color = swatch, selected = selectedId == p.id,
                    onClick = {
                        haptics.confirm()
                        editing = false
                        onApply(p.id, null)
                    },
                )
            }
            item(key = CUSTOM_ID) {
                val custom = BrandMath.parseHex(local.customColor)
                Swatch(
                    label = "Propio",
                    color = custom?.toComposeColor() ?: Color.Transparent,
                    selected = selectedId == CUSTOM_ID,
                    rainbow = custom == null,
                    icon = if (selectedId == CUSTOM_ID) null else Icons.Rounded.Colorize,
                    onClick = {
                        haptics.click()
                        if (editing) draft = null
                        editing = !editing
                    },
                )
            }
        }

        ThemePreview(c.withBrand(previewChoice))

        AnimatedVisibility(
            visible = editing,
            enter = fadeIn() + expandVertically(EvMotion.smooth()),
            exit = fadeOut() + shrinkVertically(EvMotion.snappy()),
        ) {
            CustomColorEditor(
                initial = BrandMath.parseHex(local.customColor) ?: applied.seed ?: 0xEC4899,
                onDraft = { draft = BrandChoice(applied.preset, BrandMath.toHex(it)) },
                onApply = { rgb ->
                    haptics.confirm()
                    editing = false
                    draft = null
                    onApply(applied.preset, BrandMath.toHex(rgb))
                },
            )
        }
    }
}

@Composable
private fun Swatch(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    rainbow: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val c = EvTheme.colors
    val ring by animateColorAsState(if (selected) c.ink else Color.Transparent, tween(220), label = "swatchRing")
    val checkScale by animateFloatAsState(if (selected) 1f else 0f, EvMotion.bouncy(), label = "swatchCheck")
    val checkOn = color.let { BrandMath.onColor(((it.red * 255f).toInt() shl 16) or ((it.green * 255f).toInt() shl 8) or (it.blue * 255f).toInt()) }
    val checkColor = if (rainbow) c.ink else if (checkOn == 0xFFFFFF) Color.White else Color.Black
    Column(
        Modifier.semantics { contentDescription = "Tema $label"; this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(52.dp).border(2.dp, ring, CircleShape).padding(5.dp).clip(CircleShape)
                .then(
                    if (rainbow) Modifier.background(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                    else Modifier.background(color),
                )
                .border(1.dp, c.outline, CircleShape)
                .evClickable(pressedScale = 0.88f, haptic = false, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Check, null, tint = checkColor,
                modifier = Modifier.size(24.dp).graphicsLayer { scaleX = checkScale; scaleY = checkScale; alpha = checkScale.coerceIn(0f, 1f) },
            )
            if (!selected && icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Text(label, style = EvType.caption, color = if (selected) c.ink else c.inkSoft, maxLines = 1)
    }
}

/** Mini pantalla: cabecera serif cursiva, botón, chip y barra de nivel con la paleta de la elección. */
@Composable
private fun ThemePreview(p: EvColors) {
    val c = EvTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(EvShapes.cardSmall).background(c.surfaceAlt).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EvHeadline("Tu estilo,\n*tu color*", color = c.ink, accentColor = p.brandInk)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.height(44.dp).clip(CircleShape).background(p.brand).padding(horizontal = 22.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Guardar look", style = EvType.label, color = p.onBrand) }
            Box(
                Modifier.height(36.dp).clip(CircleShape).background(p.brand).padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Casual", style = EvType.label, color = p.onBrand) }
            Box(
                Modifier.height(36.dp).clip(CircleShape).background(c.surface).border(1.dp, c.outline, CircleShape).padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Formal", style = EvType.label, color = c.inkSoft) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Nivel 4 · 340 / 500 XP", style = EvType.caption, color = c.inkSoft)
            Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(p.brandSoft)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(0.68f).clip(CircleShape).background(p.brand))
            }
        }
    }
}

@Composable
private fun CustomColorEditor(initial: Int, onDraft: (Int) -> Unit, onApply: (Int) -> Unit) {
    val c = EvTheme.colors
    val start = remember { BrandMath.toHsv(initial) }
    var hue by remember { mutableFloatStateOf(start.first) }
    var sat by remember { mutableFloatStateOf(start.second.coerceAtLeast(0.05f)) }
    var value by remember { mutableFloatStateOf(start.third.coerceAtLeast(0.05f)) }
    var hexText by remember { mutableStateOf(BrandMath.toHex(initial)) }

    val rgb = BrandMath.fromHsv(hue, sat, value)
    val fix = BrandMath.fixCustom(rgb)

    fun push(newHue: Float = hue, newSat: Float = sat, newValue: Float = value) {
        hue = newHue; sat = newSat; value = newValue
        val color = BrandMath.fromHsv(hue, sat, value)
        hexText = BrandMath.toHex(color)
        onDraft(BrandMath.fixCustom(color).rgb)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Saturación (horizontal) y brillo (vertical)
        Canvas(
            Modifier.fillMaxWidth().height(150.dp).clip(EvShapes.field)
                .pointerInput(Unit) {
                    detectTapGestures { o ->
                        push(newSat = (o.x / size.width).coerceIn(0f, 1f), newValue = 1f - (o.y / size.height).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        push(newSat = (change.position.x / size.width).coerceIn(0f, 1f), newValue = 1f - (change.position.y / size.height).coerceIn(0f, 1f))
                    }
                }
                .semantics { contentDescription = "Saturación y brillo" },
        ) {
            val pure = BrandMath.fromHsv(hue, 1f, 1f).toComposeColor()
            drawRect(Brush.horizontalGradient(listOf(Color.White, pure)))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val pos = Offset(sat * size.width, (1f - value) * size.height)
            drawCircle(Color.White, 11.dp.toPx(), pos, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
            drawCircle(Color.Black.copy(alpha = 0.35f), 12.5.dp.toPx(), pos, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        }
        // Tono
        Canvas(
            Modifier.fillMaxWidth().height(28.dp).clip(CircleShape)
                .pointerInput(Unit) { detectTapGestures { o -> push(newHue = (o.x / size.width).coerceIn(0f, 1f) * 360f) } }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        push(newHue = (change.position.x / size.width).coerceIn(0f, 1f) * 360f)
                    }
                }
                .semantics { contentDescription = "Tono" },
        ) {
            drawRect(Brush.horizontalGradient(List(7) { BrandMath.fromHsv(it * 60f, 1f, 1f).toComposeColor() }))
            val x = hue / 360f * size.width
            drawCircle(Color.White, 12.dp.toPx(), Offset(x, size.height / 2), style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            EvTextField(
                value = hexText,
                onValueChange = { raw ->
                    val t = raw.take(7).uppercase()
                    hexText = t
                    BrandMath.parseHex(t)?.let { parsed ->
                        val hsv = BrandMath.toHsv(parsed)
                        hue = hsv.first; sat = hsv.second; value = hsv.third
                        onDraft(BrandMath.fixCustom(parsed).rgb)
                    }
                },
                label = "Hex (#RRGGBB)",
                modifier = Modifier.weight(1f),
                error = if (BrandMath.parseHex(hexText) == null) "Usa el formato #RRGGBB" else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            )
            Box(Modifier.size(52.dp).clip(CircleShape).background(fix.rgb.toComposeColor()).border(1.dp, c.outline, CircleShape))
        }
        AnimatedVisibility(fix.changed) {
            Text(
                "Ese color era demasiado claro y se perdería sobre las tarjetas. Lo hemos oscurecido un poco para que se lea bien.",
                style = EvType.bodySmall, color = c.inkSoft,
            )
        }
        EvPillButton(
            "Usar este color", onClick = { onApply(fix.rgb) }, modifier = Modifier.fillMaxWidth(),
            enabled = BrandMath.parseHex(hexText) != null,
        )
    }
}
