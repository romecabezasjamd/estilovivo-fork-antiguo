package com.estilovivo.app.core.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.estilovivo.app.core.design.EvSpace
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.evClickable

/**
 * Titular editorial: las palabras entre asteriscos se pintan en serif cursiva.
 * `EvHeadline("Tailored Trends,\n*Handpicked* for you.")`
 */
@Composable
fun EvHeadline(
    raw: String,
    modifier: Modifier = Modifier,
    color: Color = EvTheme.colors.ink,
    accentColor: Color = color,
    base: TextStyle = EvType.headline,
    accent: TextStyle = EvType.headlineAccent,
) {
    val text = remember(raw, color, accentColor, base, accent) { buildEvHeadline(raw, color, accentColor, accent) }
    Text(text = text, modifier = modifier, style = base)
}

fun buildEvHeadline(raw: String, color: Color, accentColor: Color, accent: TextStyle = EvType.headlineAccent): AnnotatedString =
    buildAnnotatedString {
        raw.split('*').forEachIndexed { i, part ->
            if (part.isEmpty()) return@forEachIndexed
            if (i % 2 == 1) withStyle(accent.toSpanStyle().copy(color = accentColor)) { append(part) }
            else withStyle(SpanStyle(color = color)) { append(part) }
        }
    }

/** Cabecera de sección: título a la izquierda y acción ("Ver todo") opcional. */
@Composable
fun EvSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = EvSpace.screen),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = EvType.title, color = EvTheme.colors.ink)
        if (action != null && onAction != null) {
            Text(
                action,
                style = EvType.label,
                color = EvTheme.colors.inkSoft,
                modifier = Modifier.evClickable(onClick = onAction).padding(vertical = 6.dp, horizontal = 4.dp),
            )
        }
    }
}
