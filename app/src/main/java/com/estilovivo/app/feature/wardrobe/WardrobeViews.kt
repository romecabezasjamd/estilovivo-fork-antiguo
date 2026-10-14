package com.estilovivo.app.feature.wardrobe

import com.estilovivo.app.core.design.asPanel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.estilovivo.app.core.data.model.Garment
import com.estilovivo.app.core.data.model.Look
import com.estilovivo.app.core.design.EvMotion
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvSpace
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.SharedKey
import com.estilovivo.app.core.design.components.EvEmptyState
import com.estilovivo.app.core.design.components.EvImage
import com.estilovivo.app.core.design.components.EvLoadingDots
import com.estilovivo.app.core.design.components.EvSearchBar
import com.estilovivo.app.core.design.components.EvShimmerBox
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.pressScale
import com.estilovivo.app.core.design.sharedElementIfAvailable
import com.estilovivo.app.core.design.staggeredEntrance
import kotlin.math.roundToInt

private val BottomBarSpace = 132.dp

private fun LazyGridScope.fullSpan(key: Any, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    EvEmptyState(Icons.Rounded.CloudOff, "No pudimos cargarlo", message, actionText = "Reintentar", onAction = onRetry)
}

// ── ARMARIO ──────────────────────────────────────────────────────────────────────────────────

@Composable
fun ClosetView(
    state: WardrobeUiState,
    onFilters: (WardrobeFilters) -> Unit,
    onOpenFilters: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (Garment) -> Unit,
    onMenu: (Garment) -> Unit,
    onAdd: () -> Unit,
    onWashing: () -> Unit,
    modifier: Modifier = Modifier,
    sharedEnabled: Boolean = true,
) {
    val f = state.filters
    // El texto se edita en local (el campo no espera al ViewModel); si los filtros cambian desde fuera, se sincroniza
    var queryText by rememberSaveable { mutableStateOf(f.query) }
    LaunchedEffect(f.query) { if (f.query != queryText) queryText = f.query }
    PullToRefreshBox(isRefreshing = state.refreshing && !state.loading, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = EvSpace.screen, end = EvSpace.screen, top = 4.dp, bottom = BottomBarSpace),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            fullSpan("search") {
                EvSearchBar(
                    value = queryText, onValueChange = { queryText = it; onFilters(f.copy(query = it)) },
                    placeholder = "Buscar prenda, color, marca…",
                    onFilterClick = onOpenFilters, filterActive = f.advancedActive,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            fullSpan("chips") {
                val visible = remember(state.categoryCounts, f.category) {
                    WardrobeCatalog.categories.filter { (state.categoryCounts[it.id] ?: 0) > 0 || it.id == f.category }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 0.dp)) {
                    item(key = WardrobeCatalog.ALL) {
                        CountChip("Todo", state.categoryCounts[WardrobeCatalog.ALL] ?: 0, f.category == WardrobeCatalog.ALL, { onFilters(f.copy(category = WardrobeCatalog.ALL)) })
                    }
                    items(visible, key = { it.id }) { cat ->
                        CountChip(cat.label, state.categoryCounts[cat.id] ?: 0, f.category == cat.id, { onFilters(f.copy(category = cat.id)) })
                    }
                }
            }
            fullSpan("info") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "${state.closet.size} ${if (state.closet.size == 1) "prenda" else "prendas"}",
                        style = EvType.caption, color = EvTheme.colors.inkMuted,
                    )
                    Text(f.sort.label, style = EvType.caption, color = EvTheme.colors.inkMuted)
                }
            }
            if (state.washingCount > 0) {
                fullSpan("washing") { WashingBanner(state.washingCount, onWashing) }
            }
            when {
                state.loading -> items(6, key = { "sk$it" }) { GarmentCardSkeleton() }
                state.error != null -> fullSpan("error") { ErrorState(state.error, onRefresh) }
                state.closet.isEmpty() -> fullSpan("empty") {
                    ClosetEmpty(state, onClearQuery = { onFilters(f.copy(query = "")) }, onClearFilters = {
                        onFilters(WardrobeFilters())
                    }, onAllCategories = { onFilters(f.copy(category = WardrobeCatalog.ALL)) }, onAdd = onAdd, onWashing = onWashing)
                }
                else -> itemsIndexed(state.closet, key = { _, g -> g.id }) { index, g ->
                    GarmentCard(
                        garment = g, index = index,
                        pending = g.id in state.pendingIds,
                        busy = g.id in state.busy,
                        inTrip = g.id in state.tripGarmentIds,
                        shared = sharedEnabled,
                        onClick = { onOpen(g) }, onLongClick = { onMenu(g) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun WashingBanner(count: Int, onClick: () -> Unit) {
    val c = EvTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(EvShapes.cardSmall).background(c.surface)
            .evClickable(pressedScale = 0.97f, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Color3b82f6.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.LocalLaundryService, null, tint = Color3b82f6, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                if (count == 1) "1 prenda en la lavadora" else "$count prendas en la lavadora",
                style = EvType.label, color = c.ink,
            )
            Text("No aparecen en el armario hasta que las saques", style = EvType.caption, color = c.inkMuted)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = c.inkMuted)
    }
}

private val Color3b82f6 = androidx.compose.ui.graphics.Color(0xFF3B82F6)

@Composable
private fun ClosetEmpty(
    state: WardrobeUiState,
    onClearQuery: () -> Unit,
    onClearFilters: () -> Unit,
    onAllCategories: () -> Unit,
    onAdd: () -> Unit,
    onWashing: () -> Unit,
) {
    val f = state.filters
    when {
        state.all.isEmpty() -> EvEmptyState(
            Icons.Rounded.Checkroom, "Tu armario está vacío", "Añade tu primera prenda para empezar a crear looks.",
            actionText = "Añadir prenda", onAction = onAdd,
        )
        f.query.isNotBlank() -> EvEmptyState(
            Icons.Rounded.SearchOff, "Sin resultados", "No encontramos prendas para “${f.query.trim()}”.",
            actionText = "Limpiar búsqueda", onAction = onClearQuery,
        )
        f.advancedActive -> EvEmptyState(
            Icons.Rounded.SearchOff, "Nada con estos filtros", "Prueba a quitar algún filtro para ver más prendas.",
            actionText = "Quitar filtros", onAction = onClearFilters,
        )
        f.category != WardrobeCatalog.ALL -> EvEmptyState(
            Icons.Rounded.Checkroom, "Sin prendas en esta categoría", "Añade prendas de tipo “${WardrobeCatalog.categoryLabel(f.category)}” o prueba otra categoría.",
            actionText = "Ver todas", onAction = onAllCategories,
        )
        state.washingCount > 0 -> EvEmptyState(
            Icons.Rounded.LocalLaundryService, "Todo está en la lavadora", "Saca algo de la lavadora para verlo aquí.",
            actionText = "Ir a la lavadora", onAction = onWashing,
        )
        else -> EvEmptyState(Icons.Rounded.Checkroom, "Nada por aquí", "Añade prendas para verlas en tu armario.", actionText = "Añadir prenda", onAction = onAdd)
    }
}

// ── LOOKS ────────────────────────────────────────────────────────────────────────────────────

@Composable
fun LooksView(
    state: WardrobeUiState,
    onRefresh: () -> Unit,
    onCreate: () -> Unit,
    onOpen: (Look) -> Unit,
    onMenu: (Look) -> Unit,
    onTogglePublic: (Look) -> Unit,
    modifier: Modifier = Modifier,
) {
    PullToRefreshBox(isRefreshing = state.refreshing && !state.looksLoading, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = EvSpace.screen, end = EvSpace.screen, top = 4.dp, bottom = BottomBarSpace),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            fullSpan("head") {
                SectionRow("Tus looks", "Crear look", onCreate)
            }
            when {
                state.looksLoading -> items2(4)
                state.looksError != null -> fullSpan("error") { ErrorState(state.looksError, onRefresh) }
                state.looks.isEmpty() -> fullSpan("empty") {
                    EvEmptyState(
                        Icons.Rounded.AutoAwesome, "Aún no tienes looks", "Combina tus prendas y guarda tu primer look en minutos.",
                        actionText = "Crear look", onAction = onCreate,
                    )
                }
                else -> itemsIndexed(state.looks, key = { _, l -> l.id }) { index, look ->
                    LookCard(
                        look = look, index = index, busy = "look:${look.id}" in state.busy,
                        onClick = { onOpen(look) }, onMenu = { onMenu(look) }, onTogglePublic = { onTogglePublic(look) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

private fun LazyGridScope.items2(count: Int) {
    items(count, key = { "lsk$it" }) { EvShimmerBox(Modifier.fillMaxWidth().aspectRatio(0.72f), EvShapes.card) }
}

@Composable
private fun SectionRow(title: String, action: String, onAction: () -> Unit) {
    val c = EvTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp).staggeredEntrance(0),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = EvType.title, color = c.ink)
        Row(
            Modifier.heightIn(min = 44.dp).clip(CircleShape).background(c.ink).evClickable(onClick = onAction).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Rounded.Add, null, tint = c.onInk, modifier = Modifier.size(18.dp))
            Text(action, style = EvType.label, color = c.onInk)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LookCard(
    look: Look,
    index: Int,
    busy: Boolean,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    onTogglePublic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = EvTheme.colors
    val source = remember { MutableInteractionSource() }
    val haptics = rememberWardrobeHaptics()
    Column(
        modifier
            .staggeredEntrance(index)
            .pressScale(source, 0.96f)
            .clip(EvShapes.card)
            .background(c.surface)
            .combinedClickable(
                interactionSource = source, indication = null, role = Role.Button,
                onClickLabel = "Editar ${look.name}", onLongClickLabel = "Más acciones",
                onClick = { haptics.click(); onClick() }, onLongClick = { haptics.heavy(); onMenu() },
            )
            .padding(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.84f).clip(EvShapes.cardSmall)) {
            LookCollage(look, Modifier.matchParentSize())
            Box(
                Modifier.align(Alignment.TopStart).padding(8.dp).size(44.dp).clip(CircleShape).background(c.surface.copy(alpha = 0.92f))
                    .evClickable(onClick = onMenu).semantics { contentDescription = "Más acciones de ${look.name}" },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.MoreHoriz, null, tint = c.ink, modifier = Modifier.size(20.dp)) }
            Row(
                Modifier.align(Alignment.TopEnd).padding(8.dp).heightIn(min = 36.dp).clip(CircleShape)
                    .background(if (look.isPublic) c.ink else c.surface.copy(alpha = 0.92f))
                    .evClickable(enabled = !busy, onClick = onTogglePublic)
                    .semantics { contentDescription = if (look.isPublic) "Público. Toca para hacerlo privado" else "Privado. Toca para publicarlo" }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (busy) EvLoadingDots(color = if (look.isPublic) c.onInk else c.ink)
                else {
                    val tint = if (look.isPublic) c.onInk else c.ink
                    Icon(if (look.isPublic) Icons.Rounded.Language else Icons.Rounded.Lock, null, tint = tint, modifier = Modifier.size(14.dp))
                    Text(if (look.isPublic) "Público" else "Privado", style = EvType.caption, color = tint)
                }
            }
        }
        Text(
            look.name, style = EvType.label, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp),
        )
        val mood = WardrobeCatalog.moodOf(look.mood)
        Text(
            "${look.garments.size} ${if (look.garments.size == 1) "prenda" else "prendas"}" + (mood?.let { " · ${it.emoji} ${it.label}" } ?: ""),
            style = EvType.bodySmall, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp, top = 2.dp),
        )
    }
}

/** Foto del look; si no tiene, un collage con sus prendas. */
@Composable
fun LookCollage(look: Look, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    val imgs = look.garments.map { it.imageUrl }.take(4)
    Box(modifier.background(c.surfaceAlt)) {
        when {
            look.imageUrl != null -> EvImage(look.imageUrl, look.name, Modifier.matchParentSize())
            imgs.isEmpty() -> Icon(Icons.Rounded.AutoAwesome, null, tint = c.inkMuted, modifier = Modifier.align(Alignment.Center).size(32.dp))
            imgs.size == 1 -> EvImage(imgs[0], look.name, Modifier.matchParentSize())
            imgs.size == 2 -> Row(Modifier.matchParentSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                imgs.forEach { EvImage(it, null, Modifier.weight(1f).fillMaxSize()) }
            }
            imgs.size == 3 -> Row(Modifier.matchParentSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                EvImage(imgs[0], null, Modifier.weight(1f).fillMaxSize())
                Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    EvImage(imgs[1], null, Modifier.weight(1f).fillMaxWidth())
                    EvImage(imgs[2], null, Modifier.weight(1f).fillMaxWidth())
                }
            }
            else -> Column(Modifier.matchParentSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    EvImage(imgs[0], null, Modifier.weight(1f).fillMaxSize())
                    EvImage(imgs[1], null, Modifier.weight(1f).fillMaxSize())
                }
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    EvImage(imgs[2], null, Modifier.weight(1f).fillMaxSize())
                    EvImage(imgs[3], null, Modifier.weight(1f).fillMaxSize())
                }
            }
        }
    }
}

// ── VENTAS ───────────────────────────────────────────────────────────────────────────────────

@Composable
fun SalesView(
    state: WardrobeUiState,
    onRefresh: () -> Unit,
    onNewSale: () -> Unit,
    onOpen: (Garment) -> Unit,
    onMenu: (Garment) -> Unit,
    onCancelSale: (Garment) -> Unit,
    modifier: Modifier = Modifier,
    sharedEnabled: Boolean = true,
) {
    val c = EvTheme.colors
    PullToRefreshBox(isRefreshing = state.refreshing && !state.loading, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = EvSpace.screen, end = EvSpace.screen, top = 4.dp, bottom = BottomBarSpace),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            fullSpan("summary") {
                val animated by animateFloatAsState(state.salesTotal.toFloat(), tween(900), label = "salesTotal")
                Column(
                    Modifier.fillMaxWidth().staggeredEntrance(0).clip(EvShapes.hero).background(c.asPanel().ink).padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val c = c.asPanel()
                    Text("Valor en escaparate", style = EvType.caption, color = c.onInk.copy(alpha = 0.7f))
                    Text("${animated.roundToInt()} €", style = EvType.bigNumber.copy(fontSize = 44.sp), color = c.onInk)
                    Text(
                        "${state.sales.size} ${if (state.sales.size == 1) "prenda en venta" else "prendas en venta"}",
                        style = EvType.label, color = c.onInk.copy(alpha = 0.85f),
                        modifier = Modifier.padding(top = 8.dp).clip(CircleShape).background(c.onInk.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            fullSpan("head") { SectionRow("En tu escaparate", "Nueva venta", onNewSale) }
            when {
                state.loading -> items2(4)
                state.sales.isEmpty() -> fullSpan("empty") {
                    EvEmptyState(
                        Icons.Rounded.ShoppingBag, "Aún no vendes nada",
                        "Pon en venta las prendas que ya no usas y dales una segunda vida.",
                        actionText = "Nueva venta", onAction = onNewSale,
                    )
                }
                else -> itemsIndexed(state.sales, key = { _, g -> g.id }) { index, g ->
                    SaleCard(
                        garment = g, index = index, pending = g.id in state.pendingIds, busy = g.id in state.busy, shared = sharedEnabled,
                        onClick = { onOpen(g) }, onMenu = { onMenu(g) }, onCancelSale = { onCancelSale(g) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SaleCard(
    garment: Garment,
    index: Int,
    pending: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    onCancelSale: () -> Unit,
    modifier: Modifier = Modifier,
    shared: Boolean = true,
) {
    val c = EvTheme.colors
    val source = remember { MutableInteractionSource() }
    val haptics = rememberWardrobeHaptics()
    Column(
        modifier
            .staggeredEntrance(index)
            .pressScale(source, 0.96f)
            .clip(EvShapes.card)
            .background(c.surface)
            .combinedClickable(
                interactionSource = source, indication = null, enabled = !pending, role = Role.Button,
                onClickLabel = "Ver ${garment.name}", onLongClickLabel = "Más acciones",
                onClick = { haptics.click(); onClick() }, onLongClick = { haptics.heavy(); onMenu() },
            )
            .padding(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            EvImage(
                garment.imageUrl, garment.name,
                Modifier.matchParentSize().let { if (pending || !shared) it else it.sharedElementIfAvailable(SharedKey(garment.id)) }
                    .clip(EvShapes.cardSmall).background(c.surfaceAlt),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            )
            Text(
                formatPrice(garment.price), style = EvType.caption, color = c.onInk,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clip(CircleShape).background(c.ink).padding(horizontal = 10.dp, vertical = 5.dp),
            )
            androidx.compose.animation.AnimatedVisibility(pending || busy, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.matchParentSize()) {
                Box(Modifier.matchParentSize().clip(EvShapes.cardSmall).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.38f)), contentAlignment = Alignment.Center) {
                    EvLoadingDots(color = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
        Text(garment.name, style = EvType.label, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp))
        Text(
            listOfNotNull(WardrobeCatalog.conditionLabel(garment.condition), garment.size?.let { "Talla $it" }).joinToString(" · "),
            style = EvType.bodySmall, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp),
        )
        Box(
            Modifier.padding(top = 4.dp).heightIn(min = 44.dp).clip(CircleShape)
                .evClickable(enabled = !pending && !busy, onClick = onCancelSale).padding(horizontal = 4.dp),
            contentAlignment = Alignment.CenterStart,
        ) { Text("Retirar de la venta", style = EvType.caption, color = c.danger) }
    }
}
