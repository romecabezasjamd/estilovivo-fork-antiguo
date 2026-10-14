package com.estilovivo.app.feature.social.feed

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.estilovivo.app.core.data.model.Look
import com.estilovivo.app.core.design.EvMotion
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.SharedKey
import com.estilovivo.app.core.design.components.EvImage
import com.estilovivo.app.core.design.components.EvLikeButton
import com.estilovivo.app.core.design.components.EvLoadingDots
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.rememberEvHaptics
import com.estilovivo.app.core.design.sharedElementIfAvailable

/** Tarjeta de una publicación del feed. */
@Composable
fun FeedPostCard(
    post: Look,
    isMine: Boolean,
    authorLevel: Int?,
    shareAvatar: Boolean,
    deleting: Boolean,
    onAuthor: () -> Unit,
    onToggleLike: () -> Unit,
    onDoubleTapLike: () -> Unit,
    onComments: () -> Unit,
    onShare: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = EvTheme.colors
    val name = post.userName?.takeIf { it.isNotBlank() } ?: "Usuaria"
    Column(
        modifier
            .fillMaxWidth()
            .alpha(if (deleting) 0.5f else 1f)
            .clip(EvShapes.card)
            .background(c.surface)
            .padding(bottom = 14.dp),
    ) {
        // Autora
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.weight(1f).clip(CircleShape).evClickable(pressedScale = 0.97f, onClick = onAuthor),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                EvAvatar(
                    post.userAvatar, name, 42.dp,
                    Modifier.let { if (shareAvatar && post.userId != null) it.sharedElementIfAvailable(SharedKey(post.userId!!, "avatar")) else it },
                    contentDescription = "Perfil de $name",
                )
                Column(Modifier.weight(1f)) {
                    Text(name, style = EvType.label.copy(fontSize = 14.sp), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(relativeTime(post.createdAt), style = EvType.caption, color = c.inkMuted)
                        if (authorLevel != null) EvPill("Nv. $authorLevel", container = c.surfaceAlt, content = c.inkSoft)
                    }
                }
            }
            if (isMine) PostMenu(onDelete = onDelete, enabled = !deleting)
        }

        // Foto (doble toque = me gusta)
        PostImage(post, onDoubleTap = onDoubleTapLike)

        // Acciones
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionPill {
                Box(
                    Modifier.size(34.dp).clearAndSetSemantics {
                        role = Role.Checkbox
                        contentDescription = if (post.isLiked) "Quitar me gusta" else "Me gusta"
                        stateDescription = "${post.likesCount} me gusta"
                        onClick { onToggleLike(); true }
                    },
                ) { EvLikeButton(liked = post.isLiked, onToggle = onToggleLike, size = 34.dp, container = Color.White) }
                AnimatedCount(post.likesCount)
            }
            ActionPill(onTap = onComments, description = "Ver comentarios (${post.commentsCount})") {
                Icon(Icons.Rounded.ChatBubbleOutline, null, tint = c.ink, modifier = Modifier.size(20.dp))
                AnimatedCount(post.commentsCount)
            }
            ActionPill(onTap = onShare, description = "Compartir publicación") {
                Icon(Icons.Rounded.IosShare, null, tint = c.ink, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.weight(1f))
            val haptics = rememberEvHaptics()
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .evClickable(role = Role.Checkbox, haptic = false) { haptics.toggle(!post.isFavorited); onFavorite() }
                    .clearAndSetSemantics {
                        role = Role.Checkbox
                        contentDescription = if (post.isFavorited) "Quitar de guardados" else "Guardar publicación"
                        onClick { onFavorite(); true }
                    },
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = post.isFavorited,
                    transitionSpec = { (fadeIn(tween(160)) + androidx.compose.animation.scaleIn(EvMotion.bouncy(), initialScale = 0.6f)) togetherWith fadeOut(tween(100)) },
                    label = "bookmark",
                ) { fav ->
                    Icon(
                        if (fav) Icons.Rounded.Bookmark else Icons.Outlined.BookmarkBorder, null,
                        tint = if (fav) c.ink else c.inkSoft, modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        // Texto
        val tags = post.tags.filter { it.isNotBlank() && !it.equals(post.mood, ignoreCase = true) }.take(6)
        if (post.name.isNotBlank() || !post.mood.isNullOrBlank() || tags.isNotEmpty()) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (post.name.isNotBlank()) Text(post.name, style = EvType.subtitle, color = c.ink)
                if (!post.mood.isNullOrBlank()) Text(post.mood!!, style = EvType.body, color = c.inkSoft)
                if (tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        tags.forEach { EvPill("#${it.trim().trimStart('#')}") }
                    }
                }
            }
        }

        if (deleting) Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) { EvLoadingDots() }
    }
}


@Composable
private fun PostImage(post: Look, onDoubleTap: () -> Unit) {
    val c = EvTheme.colors
    val heart = remember { Animatable(0f) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) {
        if (tick > 0) {
            heart.snapTo(0f)
            heart.animateTo(1f, tween(750))
        }
    }
    Box(
        Modifier
            .padding(horizontal = 10.dp)
            .fillMaxWidth()
            .aspectRatio(0.9f)
            .clip(EvShapes.cardSmall)
            .background(c.surfaceAlt)
            .pointerInput(post.id) {
                detectTapGestures(onDoubleTap = { tick++; onDoubleTap() })
            },
        contentAlignment = Alignment.Center,
    ) {
        if (post.imageUrl.isNullOrBlank()) {
            Icon(Icons.Rounded.Checkroom, "Publicación sin foto", tint = c.inkMuted, modifier = Modifier.size(56.dp))
        } else {
            EvImage(post.imageUrl, post.name.ifBlank { "Foto de la publicación" }, Modifier.fillMaxSize())
        }
        val p = heart.value
        if (p > 0f && p < 1f) {
            // Sube con un rebote y se desvanece
            val s = if (p < 0.3f) 0.4f + (p / 0.3f) * 1.0f else 1.4f - (p - 0.3f) * 0.3f
            Icon(
                Icons.Rounded.Favorite, null, tint = Color.White,
                modifier = Modifier.size(96.dp).scale(s).alpha(if (p < 0.6f) 1f else (1f - (p - 0.6f) / 0.4f)),
            )
        }
    }
}

@Composable
private fun ActionPill(
    onTap: (() -> Unit)? = null,
    description: String? = null,
    content: @Composable () -> Unit,
) {
    val c = EvTheme.colors
    Row(
        Modifier
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .background(c.surfaceAlt)
            .let { m ->
                if (onTap != null) m.evClickable(pressedScale = 0.94f, onClick = onTap).clearAndSetSemantics {
                    if (description != null) contentDescription = description
                    role = Role.Button
                    onClick { onTap(); true }
                } else m
            }
            .padding(horizontal = 6.dp)
            .let { if (onTap != null) it.padding(horizontal = 6.dp) else it },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

/** Contador que "rueda" al cambiar. */
@Composable
private fun AnimatedCount(count: Int) {
    val c = EvTheme.colors
    AnimatedContent(
        targetState = count,
        transitionSpec = {
            val up = targetState > initialState
            (slideInVertically(tween(220)) { if (up) it else -it } + fadeIn(tween(180))) togetherWith
                (slideOutVertically(tween(220)) { if (up) -it else it } + fadeOut(tween(120))) using SizeTransform(clip = false)
        },
        label = "count",
    ) { n -> Text(n.toString(), style = EvType.label, color = c.ink, modifier = Modifier.padding(end = 6.dp)) }
}

@Composable
private fun PostMenu(onDelete: () -> Unit, enabled: Boolean) {
    val c = EvTheme.colors
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.size(44.dp).clip(CircleShape).evClickable(enabled = enabled) { open = true }
                .clearAndSetSemantics { contentDescription = "Más opciones de la publicación"; role = Role.Button; onClick { open = true; true } },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.MoreHoriz, null, tint = c.inkSoft, modifier = Modifier.size(22.dp)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = c.surface, shape = EvShapes.cardSmall) {
            DropdownMenuItem(
                text = { Text("Eliminar publicación", style = EvType.label, color = c.danger) },
                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = c.danger) },
                onClick = { open = false; onDelete() },
            )
        }
    }
}

/** Esqueleto de una tarjeta mientras carga. */
@Composable
fun FeedPostSkeleton(modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    Column(modifier.fillMaxWidth().clip(EvShapes.card).background(c.surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            com.estilovivo.app.core.design.components.EvShimmerBox(Modifier.size(42.dp), CircleShape)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                com.estilovivo.app.core.design.components.EvShimmerBox(Modifier.size(120.dp, 12.dp))
                com.estilovivo.app.core.design.components.EvShimmerBox(Modifier.size(70.dp, 10.dp))
            }
        }
        com.estilovivo.app.core.design.components.EvShimmerBox(Modifier.fillMaxWidth().aspectRatio(0.9f), EvShapes.cardSmall)
        com.estilovivo.app.core.design.components.EvShimmerBox(Modifier.size(180.dp, 14.dp))
    }
}
