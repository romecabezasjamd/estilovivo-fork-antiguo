package com.estilovivo.app.feature.social.shop

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.estilovivo.app.app.Chat
import com.estilovivo.app.app.LocalNavigator
import com.estilovivo.app.app.ProductDetail
import com.estilovivo.app.core.design.components.LocalEvToast
import org.koin.compose.viewmodel.koinViewModel

/**
 * Pestaña Tienda de Social: artículos reales de otras personas (segunda mano), buscador, categorías, filtros y favoritos.
 * El selector de secciones de Social ya ocupa la zona de la barra de estado, por eso aquí no se añade ese margen.
 */
@Composable
fun ShopTab(modifier: Modifier = Modifier) {
    val vm = koinViewModel<ShopViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val toast = LocalEvToast.current
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showFavorites by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(vm) { vm.errors.collect { toast.error(it) } }
    BackHandler(enabled = showFavorites) { showFavorites = false }

    AnimatedContent(
        targetState = showFavorites,
        modifier = modifier,
        transitionSpec = {
            if (targetState) {
                (slideInHorizontally(tween(320)) { it / 4 } + fadeIn(tween(260))) togetherWith (fadeOut(tween(160)))
            } else {
                fadeIn(tween(260)) togetherWith (slideOutHorizontally(tween(260)) { it / 4 } + fadeOut(tween(200)))
            }
        },
        label = "shopFavorites",
    ) { favorites ->
        if (favorites) {
            FavoritesRoute(
                onBack = { showFavorites = false },
                onOpenProduct = { nav.navigate(ProductDetail(it)) },
                onOpenChat = { nav.navigate(Chat(it)) },
            )
        } else {
            ShopScreen(
                state = state,
                onQueryChange = vm::onQueryChange,
                onCategory = vm::onCategory,
                onFavoritesOnly = vm::onFavoritesOnly,
                onItemClick = { nav.navigate(ProductDetail(it)) },
                onLike = vm::onToggleFavorite,
                onFilterClick = { showFilters = true },
                onClearFilters = { vm.onFilters(ShopFilters()) },
                onOpenFavorites = { showFavorites = true },
                onRefresh = vm::refresh,
            )
        }
    }

    if (showFilters) {
        ShopFiltersSheet(
            filters = state.filters,
            availableSizes = state.availableSizes,
            onApply = vm::onFilters,
            onDismiss = { showFilters = false },
        )
    }
}
