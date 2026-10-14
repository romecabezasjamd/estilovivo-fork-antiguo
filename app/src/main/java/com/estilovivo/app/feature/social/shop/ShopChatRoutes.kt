package com.estilovivo.app.feature.social.shop

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.estilovivo.app.app.Chat
import com.estilovivo.app.app.LocalNavigator
import com.estilovivo.app.app.ProductDetail
import com.estilovivo.app.app.SharedScope
import com.estilovivo.app.app.UserProfile
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.components.EvCircleButton
import com.estilovivo.app.core.design.components.EvEmptyState
import com.estilovivo.app.core.design.components.EvShimmerBox
import com.estilovivo.app.core.design.components.LocalEvToast
import com.estilovivo.app.feature.social.chat.ChatScreen
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private const val SHARE_BASE = "https://estilovivo.xyoncloud.win"

/** Rutas de Tienda y Chat: detalle de artículo (con foto compartida desde la tarjeta) y conversación. */
fun NavGraphBuilder.shopChatRoutes() {
    composable<ProductDetail> { entry ->
        val route = entry.toRoute<ProductDetail>()
        SharedScope { ProductDetailRoute(route.id) }
    }
    composable<Chat> { entry ->
        ChatScreen(entry.toRoute<Chat>().conversationId)
    }
}

@Composable
private fun ProductDetailRoute(id: String) {
    val vm = koinViewModel<ProductDetailViewModel>(key = "product-$id", parameters = { parametersOf(id) })
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val toast = LocalEvToast.current
    val context = LocalContext.current

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is ProductEvent.OpenChat -> nav.navigate(Chat(e.conversationId))
                is ProductEvent.Error -> toast.error(e.message)
            }
        }
    }

    val item = state.item
    when {
        item != null -> {
            val ui = remember(item, state.isOwn) { item.toDetailUi(isOwn = state.isOwn) }
            ProductDetailScreen(
                item = ui.copy(liked = state.liked),
                onBack = { nav.back() },
                onToggleLike = vm::toggleFavorite,
                onContact = vm::contactSeller,
                onSellerClick = { if (item.userId.isNotEmpty()) nav.navigate(UserProfile(item.userId)) },
                onShare = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, item.title)
                        putExtra(Intent.EXTRA_TEXT, "Mira este ${item.title} en Estilovivo $SHARE_BASE/?product=${item.id}")
                    }
                    runCatching { context.startActivity(Intent.createChooser(send, "Compartir enlace")) }
                        .onFailure { toast.error("No se pudo abrir el menú de compartir.") }
                },
                contacting = state.contacting,
            )
        }
        state.loading -> Box(Modifier.fillMaxSize().background(EvTheme.colors.bg).statusBarsPadding()) {
            EvShimmerBox(Modifier.fillMaxSize().padding(20.dp), RoundedCornerShape(32.dp))
        }
        else -> Box(Modifier.fillMaxSize().background(EvTheme.colors.bg).statusBarsPadding(), contentAlignment = Alignment.Center) {
            EvEmptyState(
                icon = Icons.Rounded.CloudOff,
                title = "No se pudo abrir el artículo",
                message = state.error ?: "Inténtalo de nuevo en unos segundos.",
                actionText = "Reintentar",
                onAction = vm::load,
            )
            EvCircleButton(
                Icons.AutoMirrored.Rounded.ArrowBack, { nav.back() },
                contentDescription = "Volver", modifier = Modifier.align(Alignment.TopStart).padding(20.dp),
            )
        }
    }
}

internal fun com.estilovivo.app.core.data.model.ShopItem.toDetailUi(isOwn: Boolean): ProductDetailUi {
    val gallery = images.ifEmpty { listOf(image) }
    return ProductDetailUi(
        id = id,
        title = title,
        images = gallery,
        price = formatEuro(price),
        brand = brand.takeUnless { it.isBlank() || it.equals("Sin marca", ignoreCase = true) },
        color = color?.takeUnless { it.isBlank() || it.equals("varios", ignoreCase = true) },
        condition = conditionLabel(condition),
        size = if (isUniqueSize(size)) "Única" else size,
        description = description,
        sellerName = user,
        sellerAvatar = avatar.ifBlank { null },
        isOwn = isOwn,
    )
}
