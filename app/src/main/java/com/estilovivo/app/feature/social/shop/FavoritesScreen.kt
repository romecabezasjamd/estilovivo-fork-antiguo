package com.estilovivo.app.feature.social.shop

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvSpace
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.EvCircleButton
import com.estilovivo.app.core.design.components.EvEmptyState
import com.estilovivo.app.core.design.components.EvHeadline
import com.estilovivo.app.core.design.components.EvImage
import com.estilovivo.app.core.design.components.EvLikeButton
import com.estilovivo.app.core.design.components.EvLoadingDots
import com.estilovivo.app.core.design.components.EvSegmented
import com.estilovivo.app.core.design.components.EvShimmerBox
import com.estilovivo.app.core.design.components.LocalEvToast
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.staggeredEntrance
import org.koin.compose.viewmodel.koinViewModel

private enum class FavFilter(val label: String) { All("Todo"), Products("Artículos"), Looks("Looks") }

/** Conecta la pantalla de favoritos con su ViewModel y gestiona los avisos. */
@Composable
fun FavoritesRoute(
    onBack: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onOpenChat: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm = koinViewModel<FavoritesViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    val toast = LocalEvToast.current
    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is FavoritesEvent.Success -> toast.success(e.message)
                is FavoritesEvent.Error -> toast.error(e.message)
                is FavoritesEvent.OpenChat -> onOpenChat(e.conversationId)
            }
        }
    }
    FavoritesScreen(
        state = state,
        onBack = onBack,
        onRefresh = { vm.load(silent = true) },
        onOpenProduct = onOpenProduct,
        onRemove = { vm.remove(it) },
        onPurchased = { vm.remove(it, purchased = true) },
        onMoveToWardrobe = vm::moveToWardrobe,
        onContact = vm::contact,
        modifier = modifier,
    )
}

@Composable
fun FavoritesScreen(
    state: FavoritesUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onRemove: (String) -> Unit,
    onPurchased: (String) -> Unit,
    onMoveToWardrobe: (String) -> Unit,
    onContact: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by rememberSaveable { mutableStateOf(FavFilter.All) }
    val shown = when (filter) {
        FavFilter.All -> state.entries
        FavFilter.Products -> state.entries.filterIsInstance<FavoriteEntry.Product>()
        FavFilter.Looks -> state.entries.filterIsInstance<FavoriteEntry.LookEntry>()
    }
    PullToRefreshBox(isRefreshing = false, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = EvSpace.screen, end = EvSpace.screen, bottom = 132.dp),
            verticalItemSpacing = 14.dp,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "header", span = StaggeredGridItemSpan.FullLine) {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.staggeredEntrance(0), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        EvCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, onBack, contentDescription = "Volver a la tienda")
                        EvHeadline("Tus *favoritos*", Modifier.padding(end = 56.dp))
                    }
                    EvSegmented(
                        options = FavFilter.entries, selected = filter, onSelect = { filter = it }, label = { it.label },
                        modifier = Modifier.fillMaxWidth().staggeredEntrance(1),
                    )
                }
            }
            when {
                state.loading -> items(listOf(0.8f, 0.95f, 0.88f, 0.8f), key = { "sk$it" }) { aspect ->
                    Column(Modifier.clip(EvShapes.card).background(EvTheme.colors.surface).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        EvShimmerBox(Modifier.fillMaxWidth().aspectRatio(aspect), EvShapes.cardSmall)
                        EvShimmerBox(Modifier.fillMaxWidth(0.7f).height(12.dp))
                    }
                }
                state.error != null && state.entries.isEmpty() -> item(key = "error", span = StaggeredGridItemSpan.FullLine) {
                    EvEmptyState(Icons.Rounded.CloudOff, "No se pudieron cargar tus favoritos", state.error, actionText = "Reintentar", onAction = onRefresh)
                }
                shown.isEmpty() -> item(key = "empty", span = StaggeredGridItemSpan.FullLine) {
                    EvEmptyState(
                        icon = Icons.Rounded.FavoriteBorder,
                        title = "Aún no tienes favoritos",
                        message = "Guarda prendas o looks que te inspiren y los tendrás aquí.",
                        actionText = "Explorar la tienda",
                        onAction = onBack,
                    )
                }
                else -> items(shown, key = { it.id }) { entry ->
                    val busy = entry.id in state.busy
                    when (entry) {
                        is FavoriteEntry.Product -> ProductFavoriteCard(
                            entry = entry, busy = busy, isOwn = entry.garment.userId == state.meId,
                            onOpen = { onOpenProduct(entry.garment.id) },
                            onRemove = { onRemove(entry.id) },
                            onPurchased = { onPurchased(entry.id) },
                            onMove = { onMoveToWardrobe(entry.id) },
                            onContact = { onContact(entry.id) },
                            modifier = Modifier.animateItem(),
                        )
                        is FavoriteEntry.LookEntry -> LookFavoriteCard(entry, busy, { onRemove(entry.id) }, Modifier.animateItem())
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductFavoriteCard(
    entry: FavoriteEntry.Product,
    busy: Boolean,
    isOwn: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onPurchased: () -> Unit,
    onMove: () -> Unit,
    onContact: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = EvTheme.colors
    val g = entry.garment
    Column(
        modifier.staggeredEntrance(2).clip(EvShapes.card).background(c.surface).animateContentSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.85f)) {
            EvImage(g.imageUrl, g.name, Modifier.matchParentSize().clip(EvShapes.cardSmall).evClickable(role = null, onClick = onOpen))
            EvLikeButton(liked = true, onToggle = onRemove, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
            if (g.price > 0.0) {
                Text(
                    formatEuro(g.price), style = EvType.caption, color = c.onInk,
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).clip(CircleShape).background(c.ink).padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
        Column(Modifier.padding(horizontal = 4.dp)) {
            Text(g.name, style = EvType.label, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.compatibility, style = EvType.bodySmall, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!isOwn) {
            SmallPill("Mover a mi armario", Icons.Rounded.Checkroom, busy, filled = true, onClick = onMove, Modifier.fillMaxWidth())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallPill("Comprada", Icons.Rounded.CheckCircle, busy, filled = false, onClick = onPurchased, Modifier.weight(1f))
            if (!isOwn && g.userId != null) {
                SmallPill("Contactar", Icons.Rounded.ChatBubbleOutline, busy, filled = false, onClick = onContact, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LookFavoriteCard(
    entry: FavoriteEntry.LookEntry,
    busy: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = EvTheme.colors
    val look = entry.look
    val image = look.imageUrl ?: look.garments.firstOrNull()?.imageUrl
    Column(
        modifier.staggeredEntrance(2).clip(EvShapes.card).background(c.surface).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.85f)) {
            EvImage(image, look.name, Modifier.matchParentSize().clip(EvShapes.cardSmall))
            EvLikeButton(liked = true, onToggle = { if (!busy) onRemove() }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
        Column(Modifier.padding(horizontal = 4.dp).padding(bottom = 4.dp)) {
            Text(look.name, style = EvType.label, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull("Look guardado", look.userName).joinToString(" · "),
                style = EvType.bodySmall, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Botón pequeño en píldora para las tarjetas (más compacto que [com.estilovivo.app.core.design.components.EvPillButton]). */
@Composable
private fun SmallPill(
    text: String,
    icon: ImageVector,
    busy: Boolean,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = EvTheme.colors
    val bg = if (filled) c.ink else c.surfaceAlt
    val fg = if (filled) c.onInk else c.ink
    Row(
        modifier
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .background(if (busy) bg.copy(alpha = 0.55f) else bg)
            .evClickable(enabled = !busy, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (busy && filled) {
            EvLoadingDots(color = fg)
        } else {
            Icon(icon, null, tint = fg, modifier = Modifier.size(15.dp))
            Text(text, style = EvType.caption, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
