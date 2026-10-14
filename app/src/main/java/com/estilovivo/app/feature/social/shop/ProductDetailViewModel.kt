package com.estilovivo.app.feature.social.shop

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.model.ShopItem
import com.estilovivo.app.feature.social.chat.ChatLauncher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class ProductDetailState(
    val loading: Boolean = true,
    val error: String? = null,
    val item: ShopItem? = null,
    val isOwn: Boolean = false,
    val liked: Boolean = false,
    /** Abriendo la conversación (el botón muestra puntos y no admite más toques). */
    val contacting: Boolean = false,
)

sealed interface ProductEvent {
    data class OpenChat(val conversationId: String) : ProductEvent
    data class Error(val message: String) : ProductEvent
}

/** Mensaje inicial que se envía al vendedor al abrir el chat desde un artículo. */
fun interestMessage(title: String): String = "Hola, me interesa «${title.trim().take(150)}». ¿Sigue disponible?"

class ProductDetailViewModel(
    private val productId: String,
    private val catalog: ShopCatalog,
    private val chatLauncher: ChatLauncher,
    private val meId: () -> String?,
) : ViewModel() {

    private data class Core(val loading: Boolean = true, val error: String? = null, val item: ShopItem? = null, val contacting: Boolean = false)

    private val core = MutableStateFlow(Core())
    private val eventChannel = Channel<ProductEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    val state: StateFlow<ProductDetailState> = combine(core, catalog.favoriteIds) { c, favs ->
        ProductDetailState(
            loading = c.loading,
            error = c.error,
            item = c.item,
            isOwn = c.item?.userId?.let { it.isNotEmpty() && it == meId() } ?: false,
            liked = productId in favs,
            contacting = c.contacting,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ProductDetailState())

    init {
        load()
        viewModelScope.launch { catalog.refreshFavorites() }
    }

    fun load() {
        core.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = catalog.find(productId)) {
                is ApiResult.Success -> core.update { it.copy(loading = false, item = r.value) }
                is ApiResult.Failure -> core.update { it.copy(loading = false, error = r.error.message) }
            }
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val result = catalog.toggleFavorite(productId) ?: return@launch
            if (result is ApiResult.Failure) eventChannel.trySend(ProductEvent.Error(result.error.message))
        }
    }

    /** Crea (o recupera) la conversación con la vendedora ligada a este artículo y avisa para navegar a ella. */
    fun contactSeller() {
        val item = core.value.item ?: return
        if (core.value.contacting || state.value.isOwn || item.userId.isEmpty()) return
        core.update { it.copy(contacting = true) }
        viewModelScope.launch {
            val r = chatLauncher.open(
                userId = item.userId,
                itemId = item.id,
                itemTitle = item.title,
                itemImage = item.image,
                initialMessage = interestMessage(item.title),
            )
            core.update { it.copy(contacting = false) }
            when (r) {
                is ApiResult.Success -> eventChannel.trySend(ProductEvent.OpenChat(r.value))
                is ApiResult.Failure -> eventChannel.trySend(ProductEvent.Error(r.error.message))
            }
        }
    }
}
