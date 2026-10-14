package com.estilovivo.app.feature.profile

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.estilovivo.app.core.design.EvMotion
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvSpace
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.EvButtonStyle
import com.estilovivo.app.core.design.components.EvCircleButton
import com.estilovivo.app.core.design.components.EvHeadline
import com.estilovivo.app.core.design.components.EvPillButton
import com.estilovivo.app.core.design.components.LocalEvToast
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.rememberEvHaptics
import com.estilovivo.app.core.design.staggeredEntrance
import kotlinx.coroutines.flow.Flow

/** Marco de las pantallas secundarias del perfil: botón atrás, titular editorial y contenido desplazable. */
@Composable
internal fun ProfileScreenFrame(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().background(EvTheme.colors.bg).statusBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = EvSpace.screen, vertical = EvSpace.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EvSpace.md),
        ) {
            EvCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, onBack, contentDescription = "Volver")
            EvHeadline(title, base = EvType.headline, modifier = Modifier.staggeredEntrance(0))
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = EvSpace.screen).navigationBarsPadding().padding(bottom = EvSpace.huge),
            verticalArrangement = Arrangement.spacedBy(EvSpace.lg),
            content = content,
        )
    }
}

/** Recoge los avisos de un view model: toasts de éxito/error y reacciones de navegación. */
@Composable
internal fun CollectProfileEvents(
    events: Flow<ProfileEvent>,
    onSaved: () -> Unit = {},
    onLoggedOut: () -> Unit = {},
    onAccountDeleted: () -> Unit = {},
) {
    val toast = LocalEvToast.current
    val haptics = rememberEvHaptics()
    val saved by rememberUpdatedState(onSaved)
    val out by rememberUpdatedState(onLoggedOut)
    val deleted by rememberUpdatedState(onAccountDeleted)
    LaunchedEffect(events) {
        events.collect { e ->
            when (e) {
                is ProfileEvent.Success -> toast.success(e.text)
                is ProfileEvent.Error -> { haptics.reject(); toast.error(e.text) }
                ProfileEvent.Saved -> saved()
                ProfileEvent.LoggedOut -> out()
                ProfileEvent.AccountDeleted -> deleted()
            }
        }
    }
}

/** Interruptor en píldora con muelle. [saving] enseña un indicador y bloquea el toque. */
@Composable
internal fun EvSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    saving: Boolean = false,
) {
    val c = EvTheme.colors
    val haptics = rememberEvHaptics()
    val track by animateColorAsState(if (checked) c.brand else c.outline, tween(220), label = "swTrack")
    val thumb by animateColorAsState(if (checked) c.onBrand else c.surface, tween(220), label = "swThumb")
    val x by animateDpAsState(if (checked) 22.dp else 0.dp, EvMotion.bouncy(), label = "swX")
    Box(
        modifier
            .size(width = 54.dp, height = 32.dp)
            .clip(CircleShape)
            .background(track)
            .semantics { role = Role.Switch; stateDescription = if (checked) "Activado" else "Desactivado" }
            .evClickable(enabled = enabled && !saving, haptic = false, role = Role.Switch, pressedScale = 0.95f) {
                haptics.toggle(!checked); onCheckedChange(!checked)
            }
            .padding(4.dp),
    ) {
        Box(
            Modifier.offset(x = x).size(24.dp).shadow(2.dp, CircleShape).background(thumb, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (saving) CircularProgressIndicator(Modifier.size(14.dp), color = track, strokeWidth = 2.dp)
        }
    }
}

/** Diálogo de confirmación con la estética de la app. */
@Composable
internal fun EvConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    loading: Boolean = false,
    confirmEnabled: Boolean = true,
    dismissText: String = "Cancelar",
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val c = EvTheme.colors
    Dialog(onDismissRequest = { if (!loading) onDismiss() }, properties = DialogProperties(dismissOnBackPress = !loading, dismissOnClickOutside = !loading)) {
        Column(
            modifier.fillMaxWidth().clip(EvShapes.card).background(c.surface).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = EvType.title, color = c.ink)
            Text(message, style = EvType.body, color = c.inkSoft)
            extra?.invoke(this)
            Spacer(8.dp)
            EvPillButton(
                confirmText, onConfirm, Modifier.fillMaxWidth(),
                style = if (danger) EvButtonStyle.Danger else EvButtonStyle.Primary,
                loading = loading, enabled = confirmEnabled, height = 52.dp,
            )
            EvPillButton(dismissText, onDismiss, Modifier.fillMaxWidth(), style = EvButtonStyle.Ghost, enabled = !loading, height = 48.dp)
        }
    }
}

@Composable
internal fun Spacer(height: Dp) = androidx.compose.foundation.layout.Spacer(Modifier.size(height))

/** Tarjeta de ajustes agrupados. */
@Composable
internal fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(EvShapes.card).background(EvTheme.colors.surface).padding(horizontal = 18.dp, vertical = 8.dp),
        content = content,
    )
}

@Composable
internal fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(EvTheme.colors.outline))
}
