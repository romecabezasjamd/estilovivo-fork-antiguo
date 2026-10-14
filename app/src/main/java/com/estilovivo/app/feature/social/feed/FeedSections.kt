package com.estilovivo.app.feature.social.feed

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.estilovivo.app.core.data.model.TopUser
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvSpace
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.EvPillButton
import com.estilovivo.app.core.design.components.EvShimmerBox
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.staggeredEntrance

private val RingBrush = Brush.sweepGradient(listOf(Color(0xFFFF3B5C), Color(0xFFFFC145), Color(0xFFA78BFA), Color(0xFFFF3B5C)))

/** Tarjeta "Historias": aros por persona (propia primero), añadir y estados de carga/error. */
@Composable
fun StoriesCard(
    state: StoriesUiState,
    viewed: Set<String>,
    myAvatar: String?,
    myName: String,
    onAdd: () -> Unit,
    onOpen: (storyId: String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = EvTheme.colors
    val groups = remember(state.stories, viewed) { groupStories(state.stories, viewed) }
    val mine = groups.firstOrNull { it.isOwn }
    val others = groups.filterNot { it.isOwn }
    val unseen = others.count { !it.allViewed }
    Column(
        modifier.fillMaxWidth().clip(EvShapes.card).background(c.surface).padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Historias", style = EvType.title, color = c.ink, modifier = Modifier.weight(1f))
            if (unseen > 0) EvPill(if (unseen == 1) "1 nueva" else "$unseen nuevas", container = c.ink, content = c.onInk)
        }
        when {
            state.loading && state.stories.isEmpty() -> Row(Modifier.padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(5) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    EvShimmerBox(Modifier.size(68.dp), CircleShape); EvShimmerBox(Modifier.size(48.dp, 10.dp))
                } }
            }
            state.error != null && state.stories.isEmpty() -> Row(
                Modifier.padding(horizontal = 18.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(state.error, style = EvType.bodySmall, color = c.inkSoft, modifier = Modifier.weight(1f))
                EvPillButton("Reintentar", onRetry, height = 40.dp, style = com.estilovivo.app.core.design.components.EvButtonStyle.Secondary)
            }
            else -> LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                item(key = "mine") {
                    StoryBubble(
                        name = "Tu historia", avatar = myAvatar, fallbackName = myName, hasStory = mine != null,
                        seen = true, isOwn = true, onClick = { if (mine != null) onOpen(mine.entryStoryId(viewed)) else onAdd() },
                        onAdd = onAdd, modifier = Modifier.staggeredEntrance(0),
                    )
                }
                itemsIndexed(others, key = { _, g -> g.userId }) { i, g ->
                    StoryBubble(
                        name = g.userName.substringBefore(' '), avatar = g.userAvatar, fallbackName = g.userName, hasStory = true,
                        seen = g.allViewed, isOwn = false, onClick = { onOpen(g.entryStoryId(viewed)) }, onAdd = null,
                        modifier = Modifier.staggeredEntrance(i + 1),
                    )
                }
                if (others.isEmpty()) item(key = "empty") {
                    Text(
                        "Cuando alguien publique una historia aparecerá aquí.",
                        style = EvType.bodySmall, color = c.inkMuted, modifier = Modifier.width(190.dp).padding(top = 14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StoryBubble(
    name: String,
    avatar: String?,
    fallbackName: String,
    hasStory: Boolean,
    seen: Boolean,
    isOwn: Boolean,
    onClick: () -> Unit,
    onAdd: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val c = EvTheme.colors
    Column(
        modifier.width(76.dp).clip(EvShapes.cardSmall)
            .evClickable(pressedScale = 0.93f, onClick = onClick)
            .semantics {
                contentDescription = when {
                    isOwn && !hasStory -> "Crear tu historia"
                    isOwn -> "Ver tu historia"
                    seen -> "Historia de $fallbackName, ya vista"
                    else -> "Historia nueva de $fallbackName"
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
            val ringModifier = when {
                !hasStory -> Modifier.size(70.dp).drawBehind {
                    drawCircle(c.inkMuted, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
                }
                seen -> Modifier.size(70.dp).border(2.dp, c.outline, CircleShape)
                else -> Modifier.size(70.dp).border(3.dp, RingBrush, CircleShape)
            }
            Box(ringModifier, contentAlignment = Alignment.Center) {
                if (!hasStory && avatar == null) {
                    Icon(Icons.Rounded.CameraAlt, null, tint = c.inkSoft, modifier = Modifier.size(26.dp))
                } else {
                    EvAvatar(avatar, fallbackName, 60.dp, contentDescription = null)
                }
            }
            if (onAdd != null) {
                Box(
                    Modifier.align(Alignment.BottomEnd).size(26.dp).clip(CircleShape).background(c.ink).border(2.dp, c.surface, CircleShape)
                        .evClickable(onClick = onAdd).semantics { contentDescription = "Añadir historia" },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Add, null, tint = c.onInk, modifier = Modifier.size(16.dp)) }
            }
        }
        Text(name, style = EvType.caption, color = if (seen && !isOwn) c.inkMuted else c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

private val Gold = Color(0xFFFFC145)
private val Silver = Color(0xFFB8B8C4)
private val Bronze = Color(0xFFCD7F32)

/** "Iconos de estilo": ranking por XP (top 5) con nivel. */
@Composable
fun TopUsersRow(users: List<TopUser>, onClick: (TopUser) -> Unit, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.padding(horizontal = 4.dp)) {
            EvOverline("Ranking de la semana")
            Text("Iconos de estilo", style = EvType.title, color = c.ink)
        }
        LazyRow(contentPadding = PaddingValues(end = EvSpace.screen), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(users, key = { _, u -> u.id }) { rank, u ->
                val ring = when (rank) { 0 -> Gold; 1 -> Silver; 2 -> Bronze; else -> c.outline }
                Column(
                    Modifier.width(104.dp).clip(EvShapes.cardSmall).background(c.surface)
                        .evClickable(pressedScale = 0.95f) { onClick(u) }
                        .semantics { contentDescription = "${u.name}, puesto ${rank + 1}, nivel ${u.level}, ${u.experiencePoints} XP" }
                        .padding(vertical = 14.dp, horizontal = 8.dp).staggeredEntrance(rank),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(Modifier.size(64.dp).border(2.5.dp, ring, CircleShape).padding(4.dp)) { EvAvatar(u.avatar, u.name, 56.dp, contentDescription = null) }
                        Box(
                            Modifier.align(Alignment.BottomEnd).size(24.dp).clip(CircleShape).background(c.ink).border(2.dp, c.surface, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Text("${u.level}", style = EvType.caption.copy(fontSize = 10.sp), color = c.onInk) }
                        if (rank == 0) Text("👑", fontSize = 16.sp, modifier = Modifier.align(Alignment.TopEnd).padding(end = 0.dp))
                    }
                    Text(u.name.substringBefore(' '), style = EvType.label, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${u.experiencePoints} XP", style = EvType.caption, color = c.inkMuted)
                }
            }
        }
    }
}
