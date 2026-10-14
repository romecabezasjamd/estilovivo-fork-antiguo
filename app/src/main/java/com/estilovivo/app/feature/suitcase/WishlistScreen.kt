package com.estilovivo.app.feature.suitcase

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.FavoriteBorder
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.estilovivo.app.app.Chat
import com.estilovivo.app.app.LocalNavigator
import com.estilovivo.app.app.MainTab
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvSpace
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.EvButtonStyle
import com.estilovivo.app.core.design.components.EvCircleButton
import com.estilovivo.app.core.design.components.EvEmptyState
import com.estilovivo.app.core.design.components.EvHeadline
import com.estilovivo.app.core.design.components.EvImage
import com.estilovivo.app.core.design.components.EvLikeButton
import com.estilovivo.app.core.design.components.EvSegmented
import com.estilovivo.app.core.design.components.EvShimmerBox
import com.estilovivo.app.core.design.components.LocalEvToast
import com.estilovivo.app.core.design.staggeredEntrance
import org.koin.androidx.compose.koinViewModel
import java.util.Locale

private enum class WishTab(val label: String) { Articles("Artículos"), Looks("Looks") }

private fun price(value: Double): String {
    if (value <= 0.0) return ""
    return if (value % 1.0 == 0.0) "${value.toLong()} €" else String.format(Locale("es", "ES"), "%.2f €", value)
}

@Composable
fun WishlistScreen(modifier: Modifier = Modifier) {
    val vm: WishlistViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val toast = LocalEvToast.current
    val c = EvTheme.colors
    var tab by rememberSaveable { mutableStateOf(WishTab.Articles) }
    var chosenOnce by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is WishlistEvent.Message -> if (e.error) toast.error(e.text) else toast.success(e.text)
                is WishlistEvent.OpenChat -> nav.navigate(Chat(e.conversationId))
            }
        }
    }
    // Si sólo hay looks guardados, se abre directamente en esa pestaña.
    LaunchedEffect(state.loading, state.articles.isEmpty(), state.looks.isEmpty()) {
        if (!chosenOnce && !state.loading) {
            chosenOnce = true
            if (state.articles.isEmpty() && state.looks.isNotEmpty()) tab = WishTab.Looks
        }
    }

    PullToRefreshBox(
        isRefreshing = state.refreshing, onRefresh = { vm.load(pull = true) },
        modifier = modifier.fillMaxSize().background(c.bg).statusBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal = EvSpace.screen).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.staggeredEntrance(0), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    EvCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, nav::back, contentDescription = "Volver")
                    Column(Modifier.weight(1f)) {
                        EvHeadline("Tu *wishlist*")
                        if (state.total > 0) {
                            Text(if (state.total == 1) "1 favorito guardado" else "${state.total} favoritos guardados", style = EvType.bodySmall, color = c.inkSoft)
                        }
                    }
                    MiniPill("Explorar", { nav.goToTab(MainTab.Social) }, style = EvButtonStyle.Secondary)
                }
                if (state.total > 0) {
                    EvSegmented(
                        WishTab.entries, tab, { tab = it },
                        label = { if (it == WishTab.Articles) "Artículos · ${state.articles.size}" else "Looks · ${state.looks.size}" },
                        modifier = Modifier.fillMaxWidth().staggeredEntrance(1),
                    )
                }
            }
            when {
                state.loading -> Column(Modifier.padding(EvSpace.screen), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(3) { EvShimmerBox(Modifier.fillMaxWidth().height(150.dp), EvShapes.card) }
                }
                state.error != null -> EvEmptyState(
                    Icons.Rounded.CloudOff, "No pudimos cargar tu wishlist", state.error.orEmpty(),
                    actionText = "Reintentar", onAction = { vm.load() },
                )
                state.total == 0 -> EvEmptyState(
                    Icons.Rounded.FavoriteBorder, "Tu wishlist está vacía",
                    "Toca el corazón en los artículos y looks que te gusten y los tendrás aquí.",
                    modifier = Modifier.padding(top = 40.dp), actionText = "Ir a la tienda", onAction = { nav.goToTab(MainTab.Social) },
                )
                else -> AnimatedContent(
                    targetState = tab,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                    label = "wishTab",
                ) { t ->
                    if (t == WishTab.Articles) ArticleList(state, vm) else LookGrid(state, vm)
                }
            }
        }
    }
}

@Composable
private fun ArticleList(state: WishlistUiState, vm: WishlistViewModel) {
    val c = EvTheme.colors
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = EvSpace.screen, end = EvSpace.screen, top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (state.articles.isEmpty()) item(key = "empty") {
            Text("No tienes artículos guardados. Explora la tienda y pulsa el corazón.", style = EvType.body, color = c.inkSoft, modifier = Modifier.padding(vertical = 24.dp))
        }
        items(state.articles, key = { it.favoriteId }) { a ->
            ArticleCard(
                a, busy = a.favoriteId in state.busy, moved = a.favoriteId in state.moved,
                onRemove = { vm.remove(a.favoriteId) }, onPurchased = { vm.remove(a.favoriteId, purchased = true) },
                onMove = { vm.moveToWardrobe(a.favoriteId) }, onContact = { vm.contact(a.favoriteId) },
                modifier = Modifier.animateItem(fadeOutSpec = tween(200)).staggeredEntrance(state.articles.indexOf(a)),
            )
        }
        item(key = "nav") { Spacer(Modifier.navigationBarsPadding()) }
    }
}

@Composable
private fun ArticleCard(
    a: WishArticle,
    busy: Boolean,
    moved: Boolean,
    onRemove: () -> Unit,
    onPurchased: () -> Unit,
    onMove: () -> Unit,
    onContact: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = EvTheme.colors
    val g = a.garment
    Column(modifier.fillMaxWidth().clip(EvShapes.card).background(c.surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(width = 104.dp, height = 136.dp)) {
                EvImage(g.imageUrl, g.name, Modifier.fillMaxSize().clip(EvShapes.cardSmall))
                EvLikeButton(true, onRemove, Modifier.align(Alignment.TopEnd).padding(6.dp), size = 34.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(g.name, style = EvType.subtitle, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val sub = listOfNotNull(g.brand, g.size?.let { "Talla $it" }, price(g.price).takeIf { it.isNotEmpty() }).joinToString(" · ")
                if (sub.isNotEmpty()) Text(sub, style = EvType.bodySmall, color = c.inkSoft, maxLines = 2, overflow = TextOverflow.Ellipsis)
                g.userName?.takeIf { !a.isMine }?.let { Text("de $it", style = EvType.caption, color = c.inkMuted) }
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.clip(CircleShape).background(c.surfaceAlt).padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = c.accent, modifier = Modifier.size(14.dp))
                    Text(a.compatibility, style = EvType.caption, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!a.isMine) {
                MiniPill(if (moved) "Ya en tu armario" else "Mover a mi armario", onMove, icon = Icons.Rounded.Checkroom, loading = busy, enabled = !moved)
                if (!g.userId.isNullOrBlank()) MiniPill("Contactar", onContact, style = EvButtonStyle.Secondary, icon = Icons.Rounded.ChatBubbleOutline, enabled = !busy)
            }
            MiniPill("Comprado", onPurchased, style = EvButtonStyle.Secondary, icon = Icons.Rounded.CheckCircle, enabled = !busy)
        }
    }
}

@Composable
private fun LookGrid(state: WishlistUiState, vm: WishlistViewModel) {
    val c = EvTheme.colors
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = EvSpace.screen, end = EvSpace.screen, top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (state.looks.isEmpty()) item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
            Text("No tienes looks guardados todavía.", style = EvType.body, color = c.inkSoft, modifier = Modifier.padding(vertical = 24.dp))
        }
        items(state.looks, key = { it.favoriteId }) { w ->
            val look = w.look
            val image = look.imageUrl ?: look.garments.firstOrNull()?.imageUrl
            Column(
                Modifier.animateItem(fadeOutSpec = tween(200)).staggeredEntrance(state.looks.indexOf(w))
                    .clip(EvShapes.card).background(c.surface).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.fillMaxWidth().aspectRatio(0.78f)) {
                    EvImage(image, look.name, Modifier.fillMaxSize().clip(EvShapes.cardSmall))
                    EvLikeButton(true, { vm.remove(w.favoriteId) }, Modifier.align(Alignment.TopEnd).padding(6.dp), size = 34.dp)
                }
                Column(Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text(look.name, style = EvType.label, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(look.userName?.let { "por $it" } ?: "Look guardado", style = EvType.caption, color = c.inkMuted, maxLines = 1)
                }
            }
        }
        item(key = "nav", span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.navigationBarsPadding()) }
    }
}
