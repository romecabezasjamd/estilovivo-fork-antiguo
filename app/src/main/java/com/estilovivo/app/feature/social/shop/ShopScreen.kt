package com.estilovivo.app.feature.social.shop

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvSpace
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.EvChip
import com.estilovivo.app.core.design.components.EvCircleButton
import com.estilovivo.app.core.design.components.EvEmptyState
import com.estilovivo.app.core.design.components.EvHeadline
import com.estilovivo.app.core.design.components.EvImage
import com.estilovivo.app.core.design.components.EvProductCard
import com.estilovivo.app.core.design.components.EvSearchBar
import com.estilovivo.app.core.design.components.EvShimmerBox
import com.estilovivo.app.core.design.staggeredEntrance

/**
 * Catálogo de la comunidad (pantalla "Home" de la referencia): saludo, titular editorial con serif
 * cursiva, buscador en píldora con botón de filtros, chips de categoría y rejilla escalonada
 * de tarjetas con corazón. Sin estado propio: lo gobierna [ShopTab] a través de [ShopViewModel].
 */
@Composable
fun ShopScreen(
    state: ShopUiState,
    onQueryChange: (String) -> Unit,
    onCategory: (String) -> Unit,
    onFavoritesOnly: (Boolean) -> Unit,
    onItemClick: (String) -> Unit,
    onLike: (String) -> Unit,
    onFilterClick: () -> Unit,
    onClearFilters: () -> Unit,
    onOpenFavorites: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 132.dp,
) {
    val c = EvTheme.colors
    val showSkeleton = state.loading && state.visible.isEmpty()
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = EvSpace.screen, end = EvSpace.screen, bottom = bottomPadding),
            verticalItemSpacing = 14.dp,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "header", span = StaggeredGridItemSpan.FullLine) {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(
                        Modifier.padding(end = 56.dp).fillMaxWidth().staggeredEntrance(0),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier.size(46.dp).clip(CircleShape).background(c.ink),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (state.avatar != null) {
                                EvImage(state.avatar, state.greetingName, Modifier.size(46.dp))
                            } else {
                                Text(state.greetingName.take(1).uppercase().ifEmpty { "·" }, style = EvType.title, color = c.onInk)
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Hola,", style = EvType.caption, color = c.inkMuted)
                            Text(state.greetingName.ifEmpty { "bienvenida" }, style = EvType.subtitle, color = c.ink)
                        }
                        EvCircleButton(Icons.Rounded.FavoriteBorder, onOpenFavorites, contentDescription = "Tus favoritos")
                    }
                    EvHeadline(
                        raw = "Tendencias a tu medida,\n*seleccionadas* para ti.",
                        modifier = Modifier.staggeredEntrance(1),
                    )
                    EvSearchBar(
                        value = state.query, onValueChange = onQueryChange, placeholder = "Buscar prendas, marcas…",
                        onFilterClick = onFilterClick, filterActive = state.hasActiveFilters,
                        modifier = Modifier.staggeredEntrance(2),
                    )
                    LazyRow(
                        Modifier.fillMaxWidth().staggeredEntrance(3),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(key = "favorites") {
                            EvChip(
                                text = "Favoritos", selected = state.favoritesOnly,
                                onClick = { onFavoritesOnly(!state.favoritesOnly) },
                                leading = if (state.favoritesOnly) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            )
                        }
                        items(ShopCategories, key = { it.id }) { cat ->
                            EvChip(text = cat.label, selected = cat.id == state.category, onClick = { onCategory(cat.id) })
                        }
                    }
                    Box(Modifier.size(2.dp))
                }
            }
            when {
                showSkeleton -> items(SKELETON_ASPECTS, key = { "sk$it" }) { aspect ->
                    ShopSkeletonCard(aspect)
                }
                state.error != null && state.visible.isEmpty() -> item(key = "error", span = StaggeredGridItemSpan.FullLine) {
                    EvEmptyState(
                        icon = Icons.Rounded.CloudOff,
                        title = "No hemos podido cargar la tienda",
                        message = state.error,
                        actionText = "Reintentar",
                        onAction = onRefresh,
                    )
                }
                state.visible.isEmpty() -> item(key = "empty", span = StaggeredGridItemSpan.FullLine) {
                    val filtered = state.hasActiveFilters || state.favoritesOnly || state.query.isNotBlank() || state.category != "all"
                    EvEmptyState(
                        icon = Icons.Rounded.SearchOff,
                        title = if (state.favoritesOnly) "Aún no tienes favoritos aquí" else "Nada por aquí",
                        message = when {
                            state.favoritesOnly -> "Toca el corazón de un artículo para guardarlo."
                            filtered -> "Prueba con otra búsqueda, categoría o filtro."
                            else -> "Todavía no hay artículos a la venta. Vuelve pronto."
                        },
                        actionText = if (state.hasActiveFilters) "Quitar filtros" else null,
                        onAction = if (state.hasActiveFilters) onClearFilters else null,
                    )
                }
                else -> itemsIndexed(state.visible, key = { _, it -> it.id }) { index, item ->
                    val liked = item.id in state.favoriteIds
                    EvProductCard(
                        item = item.toCard(liked),
                        onClick = { onItemClick(item.id) },
                        onLike = { onLike(item.id) },
                        index = index + 4,
                    )
                }
            }
        }
    }
}

private val SKELETON_ASPECTS = listOf(0.78f, 0.95f, 0.9f, 0.8f, 0.85f, 0.98f)

@Composable
private fun ShopSkeletonCard(aspect: Float) {
    val c = EvTheme.colors
    Column(
        Modifier.clip(EvShapes.card).background(c.surface).padding(8.dp).semantics { contentDescription = "Cargando artículo" },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EvShimmerBox(Modifier.fillMaxWidth().aspectRatio(aspect), EvShapes.cardSmall)
        EvShimmerBox(Modifier.fillMaxWidth(0.7f).height(12.dp))
        EvShimmerBox(Modifier.fillMaxWidth(0.45f).height(10.dp))
    }
}
