package com.estilovivo.app.feature.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.estilovivo.app.app.EditProfile
import com.estilovivo.app.app.LocalNavigator
import com.estilovivo.app.core.data.model.User
import com.estilovivo.app.core.design.EvMotion
import com.estilovivo.app.core.design.EvSpace
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.EvChipRow
import com.estilovivo.app.core.design.components.EvCircleButton
import com.estilovivo.app.core.design.components.EvConfetti
import com.estilovivo.app.core.design.components.EvHeadline
import com.estilovivo.app.core.design.components.EvImage
import com.estilovivo.app.core.design.components.EvStatTile
import com.estilovivo.app.core.design.staggeredEntrance
import org.koin.androidx.compose.koinViewModel

@Composable
fun ProfileTab() {
    val vm: ProfileViewModel = koinViewModel()
    val s by vm.state.collectAsStateWithLifecycle()
    val c = EvTheme.colors
    val nav = LocalNavigator.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshUser() }
    CollectProfileEvents(vm.events)

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 132.dp),
            verticalArrangement = Arrangement.spacedBy(EvSpace.lg),
        ) {
            ProfileHeader(s.user, s.level, onEdit = { nav.navigate(EditProfile) })
            EvChipRow(
                items = ProfileSection.entries, selected = s.section, onSelect = vm::selectSection, label = { it.label },
                modifier = Modifier.staggeredEntrance(4),
            )
            AnimatedContent(
                targetState = s.section,
                transitionSpec = {
                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (fadeIn(tween(260)) + slideInHorizontally(tween(320, easing = EvMotion.emphasized)) { it / 6 * dir }) togetherWith
                        (fadeOut(tween(150)) + slideOutHorizontally(tween(220)) { -it / 8 * dir })
                },
                modifier = Modifier.padding(horizontal = EvSpace.screen),
                label = "profileSection",
            ) { section ->
                when (section) {
                    ProfileSection.Stats -> StatsSection(s.stats, vm::reload)
                    ProfileSection.Progress -> ProgressSection(s.progress, vm::reload)
                    ProfileSection.Favorites -> FavoritesSection(s.favorites, vm::reload)
                    ProfileSection.Settings -> SettingsSection()
                }
            }
        }
        EvConfetti(s.confetti, Modifier.fillMaxSize())
    }

    s.celebrate.firstOrNull()?.let { a ->
        EvConfirmDialog(
            title = "¡Logro desbloqueado!", message = "${a.icon}  ${a.title}\n${a.description}\n+${a.xpReward} XP",
            confirmText = "¡Genial!", onConfirm = vm::dismissCelebration, onDismiss = vm::dismissCelebration, dismissText = "Cerrar",
        )
    }
    s.levelUp?.let { lvl ->
        EvConfirmDialog(
            title = "¡Subes de nivel!", message = "Ya eres nivel $lvl. Sigue creando looks y completando retos.",
            confirmText = "¡Vamos!", onConfirm = vm::dismissLevelUp, onDismiss = vm::dismissLevelUp, dismissText = "Cerrar",
        )
    }
}

@Composable
private fun ProfileHeader(user: User?, level: LevelProgress, onEdit: () -> Unit) {
    val c = EvTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(EvSpace.md)) {
        // La campana global del shell ocupa ~56 dp a la derecha
        Row(
            Modifier.fillMaxWidth().padding(start = EvSpace.screen, end = 56.dp + EvSpace.screen, top = EvSpace.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EvHeadline("Mi *perfil*", Modifier.weight(1f).staggeredEntrance(0))
            EvCircleButton(Icons.Rounded.Edit, onEdit, contentDescription = "Editar perfil")
        }
        Column(Modifier.fillMaxWidth().staggeredEntrance(1), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LevelAvatar(user?.avatar, user?.name.orEmpty(), level)
            Text(user?.name.orEmpty().ifBlank { "Tu nombre" }, style = EvType.headline, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = EvSpace.screen))
            user?.bio?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = EvType.body, color = c.inkSoft, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 40.dp))
            }
            user?.locationName?.takeIf { it.isNotBlank() }?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.LocationOn, null, tint = c.inkMuted, modifier = Modifier.size(16.dp))
                    Text(it, style = EvType.caption, color = c.inkSoft)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = EvSpace.screen).staggeredEntrance(2), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EvStatTile("${user?.garmentCount ?: 0}", "Prendas", Modifier.weight(1f))
            EvStatTile("${user?.lookCount ?: 0}", "Looks", Modifier.weight(1f))
            EvStatTile("${user?.followersCount ?: 0}", "Seguidores", Modifier.weight(1f))
            EvStatTile("${user?.followingCount ?: 0}", "Seguidos", Modifier.weight(1f))
        }
    }
}

/** Avatar grande con un anillo que se dibuja hasta el progreso del nivel y una chapa con el nivel. */
@Composable
private fun LevelAvatar(avatar: String?, name: String, level: LevelProgress) {
    val c = EvTheme.colors
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(level.fraction) { sweep.animateTo(level.fraction, tween(1100, easing = EvMotion.emphasized)) }
    val desc = "Nivel ${level.level}, ${level.current} de ${level.needed} puntos de experiencia"
    Box(Modifier.size(148.dp).semantics { contentDescription = desc }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(148.dp)) {
            val stroke = 6.dp.toPx()
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(c.outline, 0f, 360f, false, Offset(stroke / 2, stroke / 2), arcSize, style = Stroke(stroke))
            drawArc(c.brand, -90f, 360f * sweep.value, false, Offset(stroke / 2, stroke / 2), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Box(Modifier.size(124.dp).clip(CircleShape).background(c.surface), contentAlignment = Alignment.Center) {
            if (avatar != null) EvImage(avatar, "Foto de $name", Modifier.size(124.dp))
            else Icon(Icons.Rounded.Person, null, tint = c.inkMuted, modifier = Modifier.size(52.dp))
        }
        Text(
            "Nv ${level.level}", style = EvType.caption.copy(fontSize = 12.sp), color = c.onInk,
            modifier = Modifier.align(Alignment.BottomCenter).clip(CircleShape).background(c.ink).padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}
