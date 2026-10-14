package com.estilovivo.app.feature.social.feed

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.estilovivo.app.app.Chat
import com.estilovivo.app.app.LocalNavigator
import com.estilovivo.app.app.MainTab
import com.estilovivo.app.app.ProductDetail
import com.estilovivo.app.core.data.model.Garment
import com.estilovivo.app.core.data.model.Look
import com.estilovivo.app.core.data.model.PublicProfile
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvSpace
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.LocalEvToast
import com.estilovivo.app.core.design.SharedKey
import com.estilovivo.app.core.design.components.EvButtonStyle
import com.estilovivo.app.core.design.components.EvCircleButton
import com.estilovivo.app.core.design.components.EvEmptyState
import com.estilovivo.app.core.design.components.EvImage
import com.estilovivo.app.core.design.components.EvPillButton
import com.estilovivo.app.core.design.components.EvSegmented
import com.estilovivo.app.core.design.components.EvShimmerBox
import com.estilovivo.app.core.design.components.EvStatTile
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.sharedElementIfAvailable
import com.estilovivo.app.core.design.staggeredEntrance
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** Perfil de otra usuaria: cabecera, contadores, seguir / solicitud, mensaje y pestañas Looks / En venta. */
@Composable
fun UserProfileScreen(userId: String, modifier: Modifier = Modifier) {
    val vm: UserProfileViewModel = koinViewModel(key = "profile-$userId") { parametersOf(userId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val toast = LocalEvToast.current
    val c = EvTheme.colors
    var preview by remember { mutableStateOf<Look?>(null) }

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is ProfileEvent.Toast -> if (e.isError) toast.error(e.text) else toast.success(e.text)
                is ProfileEvent.OpenChat -> nav.navigate(Chat(e.conversationId))
            }
        }
    }

    Box(modifier.fillMaxSize().background(c.bg)) {
        val profile = state.profile
        when {
            profile != null -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { vm.load(refresh = true) }, modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = EvSpace.screen, end = EvSpace.screen, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item(span = { GridItemSpan(2) }, key = "header") {
                        ProfileHeader(
                            profile = profile, state = state,
                            onBack = nav::back, onFollow = vm::toggleFollow, onMessage = vm::message,
                            onMyProfile = { nav.goToTab(MainTab.Profile) }, onTab = vm::selectTab,
                        )
                    }
                    if (state.locked) {
                        item(span = { GridItemSpan(2) }, key = "locked") {
                            EvEmptyState(
                                Icons.Rounded.Lock, "Perfil privado",
                                "Envía una solicitud de seguimiento para ver sus looks y prendas en venta.",
                            )
                        }
                    } else if (state.tab == ProfileTab.Looks) {
                        if (profile.looks.isEmpty()) item(span = { GridItemSpan(2) }, key = "noLooks") {
                            EvEmptyState(Icons.Rounded.Checkroom, "Sin looks públicos", "Cuando comparta alguno aparecerá aquí.")
                        } else {
                            itemsIndexed(profile.looks, key = { _, l -> "look-${l.id}" }) { i, look ->
                                LookTile(look, onClick = { preview = look }, modifier = Modifier.staggeredEntrance(i))
                            }
                            if (profile.lookCount > profile.looks.size) item(span = { GridItemSpan(2) }, key = "moreLooks") {
                                Text(
                                    "Mostrando sus ${profile.looks.size} looks públicos más recientes.",
                                    style = EvType.caption, color = c.inkMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    } else {
                        if (profile.garmentsForSale.isEmpty()) item(span = { GridItemSpan(2) }, key = "noSale") {
                            EvEmptyState(Icons.Rounded.Checkroom, "Nada en venta", "No tiene prendas a la venta ahora mismo.")
                        } else {
                            itemsIndexed(profile.garmentsForSale, key = { _, g -> "garment-${g.id}" }) { i, g ->
                                GarmentTile(g, onClick = { nav.navigate(ProductDetail(g.id)) }, modifier = Modifier.staggeredEntrance(i))
                            }
                        }
                    }
                }
            }
            state.loading -> ProfileSkeleton(onBack = nav::back)
            else -> Column(Modifier.fillMaxSize().statusBarsPadding().padding(EvSpace.screen)) {
                EvCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, nav::back, contentDescription = "Volver")
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EvEmptyState(
                        if (state.notFound) Icons.Rounded.PersonOff else Icons.Rounded.Schedule,
                        if (state.notFound) "Perfil no disponible" else "No pudimos cargar el perfil",
                        state.error ?: "Inténtalo de nuevo.",
                        actionText = if (state.notFound) "Volver" else "Reintentar",
                        onAction = if (state.notFound) nav::back else { { vm.load() } },
                    )
                }
            }
        }
    }

    preview?.let { look -> LookPreviewDialog(look, onDismiss = { preview = null }) }
}

@Composable
private fun ProfileHeader(
    profile: PublicProfile,
    state: ProfileUiState,
    onBack: () -> Unit,
    onFollow: () -> Unit,
    onMessage: () -> Unit,
    onMyProfile: () -> Unit,
    onTab: (ProfileTab) -> Unit,
) {
    val c = EvTheme.colors
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        EvCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, onBack, contentDescription = "Volver")

        Column(Modifier.fillMaxWidth().staggeredEntrance(0), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EvAvatar(
                profile.avatar, profile.name, 104.dp,
                Modifier.sharedElementIfAvailable(SharedKey(profile.id, "avatar")),
                contentDescription = "Foto de ${profile.name}",
            )
            Text(profile.name, style = EvType.headline, color = c.ink, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                EvPill("Nv. ${profile.level} · ${levelTitle(profile.level)}", container = c.ink, content = c.onInk)
                EvPill("${profile.experiencePoints} XP")
            }
            if (profile.bio.isNotBlank()) Text(profile.bio, style = EvType.body, color = c.inkSoft, textAlign = TextAlign.Center)
            if (!profile.locationName.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.Place, null, tint = c.inkMuted, modifier = Modifier.size(16.dp))
                    Text(profile.locationName!!, style = EvType.caption, color = c.inkMuted)
                }
            }
        }

        Row(Modifier.fillMaxWidth().staggeredEntrance(1), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EvStatTile("${profile.lookCount}", "Looks", Modifier.weight(1f))
            AnimatedStat("${state.followersCount}", "Seguidores", Modifier.weight(1f))
            EvStatTile("${profile.followingCount}", "Siguiendo", Modifier.weight(1f))
        }

        Row(Modifier.fillMaxWidth().staggeredEntrance(2), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.isMe) {
                EvPillButton("Este es tu perfil · Ver el mío", onMyProfile, Modifier.weight(1f), style = EvButtonStyle.Secondary, height = 52.dp)
            } else {
                val b = state.button
                val following = b == FollowButton.Following
                val requested = b == FollowButton.Requested
                EvPillButton(
                    text = b.label, onClick = onFollow, modifier = Modifier.weight(1f),
                    style = if (following || requested) EvButtonStyle.Secondary else EvButtonStyle.Primary,
                    icon = when { following -> Icons.Rounded.Check; requested -> Icons.Rounded.Schedule; else -> Icons.Rounded.PersonAdd },
                    loading = state.followBusy, height = 52.dp,
                )
                EvPillButton("Mensaje", onMessage, style = EvButtonStyle.Secondary, icon = Icons.Rounded.ChatBubbleOutline, loading = state.messageBusy, height = 52.dp)
            }
        }

        if (!state.locked) {
            EvSegmented(
                options = ProfileTab.entries, selected = state.tab, onSelect = onTab,
                label = { if (it == ProfileTab.Looks) "Looks (${profile.looks.size})" else "En venta (${profile.garmentsForSale.size})" },
                modifier = Modifier.fillMaxWidth().staggeredEntrance(3),
            )
        }
    }
}

@Composable
private fun AnimatedStat(value: String, label: String, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    Column(modifier.clip(EvShapes.cardSmall).background(c.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        AnimatedContent(
            targetState = value, label = "stat",
            transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) },
        ) { v -> Text(v, style = EvType.bigNumber, color = c.ink) }
        Text(label, style = EvType.caption, color = c.inkSoft)
    }
}

@Composable
private fun LookTile(look: Look, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    Box(modifier.fillMaxWidth().aspectRatio(0.82f).clip(EvShapes.card).background(c.surface).evClickable(pressedScale = 0.96f, onClick = onClick)) {
        if (look.imageUrl.isNullOrBlank()) {
            Icon(Icons.Rounded.Checkroom, null, tint = c.inkMuted, modifier = Modifier.align(Alignment.Center).size(40.dp))
        } else {
            EvImage(look.imageUrl, look.name, Modifier.fillMaxSize())
        }
        if (look.name.isNotBlank()) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(scrimBrush(false)).padding(12.dp).padding(top = 16.dp)) {
                Text(look.name, style = EvType.label, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun GarmentTile(g: Garment, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    Column(modifier.fillMaxWidth().clip(EvShapes.card).background(c.surface).evClickable(pressedScale = 0.96f, onClick = onClick)) {
        EvImage(g.imageUrl, g.name, Modifier.fillMaxWidth().aspectRatio(0.9f))
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(g.name, style = EvType.label, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (g.price > 0) Text("%.2f €".format(java.util.Locale("es", "ES"), g.price), style = EvType.price, color = c.ink)
        }
    }
}

@Composable
private fun LookPreviewDialog(look: Look, onDismiss: () -> Unit) {
    val c = EvTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(EvShapes.card).background(c.surface)) {
            Box(Modifier.fillMaxWidth().aspectRatio(0.85f).background(c.surfaceAlt)) {
                if (!look.imageUrl.isNullOrBlank()) EvImage(look.imageUrl, look.name, Modifier.fillMaxSize())
                EvCircleButton(
                    Icons.Rounded.Close, onDismiss, Modifier.align(Alignment.TopEnd).padding(12.dp),
                    contentDescription = "Cerrar", size = 40.dp, container = Color(0x99000000), tint = Color.White,
                )
            }
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (look.name.isNotBlank()) Text(look.name, style = EvType.subtitle, color = c.ink)
                if (!look.mood.isNullOrBlank()) Text(look.mood!!, style = EvType.body, color = c.inkSoft)
                val tags = look.tags.filter { it.isNotBlank() && !it.equals(look.mood, true) }.take(6)
                if (tags.isNotEmpty()) Text(tags.joinToString("  ") { "#${it.trim().trimStart('#')}" }, style = EvType.caption, color = c.inkMuted)
                Text(relativeTime(look.createdAt), style = EvType.caption, color = c.inkMuted)
            }
        }
    }
}

@Composable
private fun ProfileSkeleton(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(EvSpace.screen), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        EvCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, onBack, contentDescription = "Volver")
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EvShimmerBox(Modifier.size(104.dp), CircleShape)
            EvShimmerBox(Modifier.size(180.dp, 24.dp))
            EvShimmerBox(Modifier.size(120.dp, 14.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(3) { EvShimmerBox(Modifier.weight(1f).aspectRatio(1.4f), EvShapes.cardSmall) }
        }
        EvShimmerBox(Modifier.fillMaxWidth().aspectRatio(8f), CircleShape)
    }
}
