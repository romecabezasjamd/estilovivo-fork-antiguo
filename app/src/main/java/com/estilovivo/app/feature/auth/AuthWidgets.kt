package com.estilovivo.app.feature.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.estilovivo.app.core.design.EvMotion
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.EvTextField
import com.estilovivo.app.core.design.evClickable
import kotlin.math.roundToInt

/**
 * Campo del formulario de acceso: el error sólo aparece tras salir del campo (o al intentar enviar),
 * así no se grita "email inválido" mientras se está escribiendo. Soporta autorrelleno del gestor de contraseñas.
 */
@Composable
internal fun AuthField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    error: String?,
    attempted: Boolean,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    contentType: ContentType? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    var hadFocus by remember { mutableStateOf(false) }
    var touched by rememberSaveable { mutableStateOf(false) }
    EvTextField(
        value = value,
        onValueChange = onChange,
        label = label,
        leadingIcon = icon,
        trailing = trailing,
        error = if ((touched || attempted) && error != null) error else null,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        enabled = enabled,
        modifier = modifier
            .animateContentSize(EvMotion.snappy())
            .onFocusChanged { if (it.hasFocus) hadFocus = true else if (hadFocus) touched = true }
            .then(if (contentType != null) Modifier.semantics { this.contentType = contentType } else Modifier),
    )
}

/** Campo de contraseña con ojo animado para mostrar/ocultar. */
@Composable
internal fun PasswordField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    error: String?,
    attempted: Boolean,
    modifier: Modifier = Modifier,
    newPassword: Boolean,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    enabled: Boolean = true,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    val c = EvTheme.colors
    AuthField(
        value = value, onChange = onChange, label = label, error = error, attempted = attempted, modifier = modifier,
        icon = Icons.Rounded.Lock,
        contentType = if (newPassword) ContentType.NewPassword else ContentType.Password,
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, enabled = enabled,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailing = {
            AnimatedContent(
                targetState = visible,
                transitionSpec = { (fadeIn(tween(140)) + scaleIn(initialScale = 0.6f)) togetherWith (fadeOut(tween(100)) + scaleOut(targetScale = 0.6f)) },
                modifier = Modifier.clip(CircleShape).evClickable(pressedScale = 0.85f) { visible = !visible }.padding(6.dp),
                label = "eye",
            ) { v ->
                Icon(
                    if (v) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (v) "Ocultar contraseña" else "Mostrar contraseña",
                    tint = c.inkMuted, modifier = Modifier.size(22.dp),
                )
            }
        },
    )
}

/** Selector Entrar / Registro con indicador deslizante. */
@Composable
internal fun AuthTabs(mode: AuthMode, onChange: (AuthMode) -> Unit, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    BoxWithConstraints(
        modifier.fillMaxWidth().height(54.dp).clip(CircleShape).background(c.surfaceAlt).padding(4.dp),
    ) {
        val half = maxWidth / 2
        val x by animateDpAsState(if (mode == AuthMode.Login) 0.dp else half, EvMotion.snappy(), label = "tabX")
        Box(
            Modifier
                .offset { IntOffset(x.roundToPx(), 0) }
                .width(half).fillMaxHeight().clip(CircleShape).background(c.ink),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            AuthMode.entries.forEach { m ->
                val sel = m == mode
                val fg by animateColorAsState(if (sel) c.onInk else c.inkSoft, tween(220), label = "tabFg")
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                        .evClickable(pressedScale = 0.96f, role = androidx.compose.ui.semantics.Role.Tab) { onChange(m) },
                    contentAlignment = Alignment.Center,
                ) { Text(if (m == AuthMode.Login) "Entrar" else "Registro", style = EvType.label, color = fg) }
            }
        }
    }
}

/** Aviso de error con animación de entrada. */
@Composable
internal fun ErrorBanner(message: String?, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    var last by remember { mutableStateOf(message) }
    if (message != null) last = message
    AnimatedVisibility(
        visible = message != null,
        enter = expandVertically(EvMotion.snappy()) + fadeIn(),
        exit = shrinkVertically(tween(180)) + fadeOut(tween(120)),
        modifier = modifier,
    ) {
        Row(
            Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                .background(c.danger.copy(alpha = 0.12f)).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.ErrorOutline, null, tint = c.danger, modifier = Modifier.size(20.dp))
            Text(last.orEmpty(), style = EvType.bodySmall, color = c.danger)
        }
    }
}

/** Casilla redonda animada ("Recordarme"). */
@Composable
internal fun CheckRow(checked: Boolean, label: String, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    val bg by animateColorAsState(if (checked) c.ink else Color.Transparent, tween(200), label = "checkBg")
    val border by animateColorAsState(if (checked) c.ink else c.outline, tween(200), label = "checkBorder")
    Row(
        modifier.clip(CircleShape).evClickable(pressedScale = 0.97f, role = androidx.compose.ui.semantics.Role.Checkbox) { onChange(!checked) }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(bg)
                .border(1.5.dp, border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.animation.AnimatedVisibility(checked, enter = scaleIn(EvMotion.bouncy()) + fadeIn(), exit = scaleOut() + fadeOut()) {
                Icon(Icons.Rounded.Check, null, tint = c.onInk, modifier = Modifier.size(14.dp))
            }
        }
        Text(label, style = EvType.body, color = c.inkSoft)
    }
}

/** Círculo con cuenta atrás: el anillo se vacía en [total] s y el número se desliza. */
@Composable
internal fun CooldownRing(seconds: Int, total: Int = RESEND_COOLDOWN_SECONDS, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    val frac by animateFloatAsState(
        if (total == 0) 0f else (seconds.coerceAtLeast(0).toFloat() / total), tween(1000, easing = LinearEasing), label = "ring",
    )
    Box(modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(64.dp)) {
            val stroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2
            val sz = Size(size.width - stroke.width, size.height - stroke.width)
            drawArc(c.outline, 0f, 360f, false, Offset(inset, inset), sz, style = stroke)
            drawArc(c.brand, -90f, 360f * frac, false, Offset(inset, inset), sz, style = stroke)
        }
        AnimatedContent(
            targetState = seconds,
            transitionSpec = { (slideInVertically { it / 2 } + fadeIn(tween(160))) togetherWith (slideOutVertically { -it / 2 } + fadeOut(tween(120))) },
            label = "ringNumber",
        ) { s -> Text(s.toString(), style = EvType.title, color = c.ink) }
    }
}

/** Círculo con check que se dibuja (verificación / éxito). */
@Composable
internal fun AnimatedCheck(modifier: Modifier = Modifier, color: Color = EvTheme.colors.success) {
    val scale = remember { Animatable(0f) }
    val draw = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, EvMotion.bouncy())
        draw.animateTo(1f, tween(380))
    }
    Canvas(modifier.size(96.dp)) {
        val s = scale.value
        drawCircle(color.copy(alpha = 0.16f), radius = size.minDimension / 2 * s)
        drawCircle(color, radius = size.minDimension / 2 * 0.72f * s)
        val p = draw.value
        val a = Offset(size.width * 0.34f, size.height * 0.52f)
        val b = Offset(size.width * 0.45f, size.height * 0.63f)
        val d = Offset(size.width * 0.67f, size.height * 0.39f)
        val stroke = 6.dp.toPx()
        val first = (p / 0.4f).coerceIn(0f, 1f)
        val second = ((p - 0.4f) / 0.6f).coerceIn(0f, 1f)
        drawLine(Color.White, a, Offset(a.x + (b.x - a.x) * first, a.y + (b.y - a.y) * first), stroke, StrokeCap.Round)
        if (second > 0f) drawLine(Color.White, b, Offset(b.x + (d.x - b.x) * second, b.y + (d.y - b.y) * second), stroke, StrokeCap.Round)
    }
}
