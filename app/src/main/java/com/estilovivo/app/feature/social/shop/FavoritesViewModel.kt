package com.estilovivo.app.feature.social.shop

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.model.Favorite
import com.estilovivo.app.core.data.model.Garment
import com.estilovivo.app.core.data.model.GarmentDraft
import com.estilovivo.app.core.data.model.Look
import com.estilovivo.app.core.data.repo.GarmentRepository
import com.estilovivo.app.core.data.repo.SocialRepository
import com.estilovivo.app.feature.social.chat.ChatLauncher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Un favorito: artículo (prenda de otra persona) o look. */
@Immutable
sealed interface FavoriteEntry {
    val id: String

    @Immutable
    data class Product(override val id: String, val garment: Garment, val compatibility: String) : FavoriteEntry

    @Immutable
    data class LookEntry(override val id: String, val look: Look) : FavoriteEntry
}

@Immutable
data class FavoritesUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val entries: List<FavoriteEntry> = emptyList(),
    /** Favoritos con una acción en curso (botón en estado de carga, sin dobles toques). */
    val busy: Set<String> = emptySet(),
    val meId: String? = null,
)

sealed interface FavoritesEvent {
    data class Success(val message: String) : FavoritesEvent
    data class Error(val message: String) : FavoritesEvent
    data class OpenChat(val conversationId: String) : FavoritesEvent
}

class FavoritesViewModel(
    private val catalog: ShopCatalog,
    private val social: SocialRepository,
    private val garments: GarmentRepository,
    private val chatLauncher: ChatLauncher,
    private val fetcher: ImageFetcher,
    private val meId: () -> String?,
) : ViewModel() {

    private val _state = MutableStateFlow(FavoritesUiState(meId = meId()))
    val state: StateFlow<FavoritesUiState> = _state.asStateFlow()

    private val eventChannel = Channel<FavoritesEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        load(silent = false)
    }

    fun load(silent: Boolean = false) {
        _state.update { it.copy(loading = !silent && it.entries.isEmpty(), error = if (silent) it.error else null, meId = meId()) }
        viewModelScope.launch {
            when (val r = catalog.refreshFavorites()) {
                is ApiResult.Success -> {
                    val wardrobe = garments.garments.value
                    _state.update { it.copy(loading = false, error = null, entries = r.value.mapNotNull { f -> f.toEntry(wardrobe) }) }
                }
                is ApiResult.Failure -> {
                    val hadItems = _state.value.entries.isNotEmpty()
                    _state.update { it.copy(loading = false, error = if (hadItems) it.error else r.error.message) }
                    if (hadItems && silent.not()) eventChannel.trySend(FavoritesEvent.Error(r.error.message))
                }
            }
        }
    }

    /** Quita un favorito (artículo o look) con salida optimista y rollback si el servidor falla. */
    fun remove(entryId: String, purchased: Boolean = false) {
        val entry = _state.value.entries.firstOrNull { it.id == entryId } ?: return
        if (!markBusy(entryId)) return
        viewModelScope.launch {
            val index = _state.value.entries.indexOf(entry)
            _state.update { it.copy(entries = it.entries - entry) }
            val failure = unfavorite(entry)
            if (failure != null) {
                _state.update { s ->
                    if (s.entries.any { it.id == entry.id }) s
                    else s.copy(entries = s.entries.toMutableList().apply { add(index.coerceIn(0, size), entry) })
                }
                eventChannel.trySend(FavoritesEvent.Error(failure))
            } else if (purchased) {
                eventChannel.trySend(FavoritesEvent.Success("Marcada como comprada. La hemos quitado de tus favoritos."))
            }
            clearBusy(entryId)
        }
    }

    /** Copia la prenda al armario propio CON su foto y la quita de favoritos. */
    fun moveToWardrobe(entryId: String) {
        val entry = _state.value.entries.firstOrNull { it.id == entryId } as? FavoriteEntry.Product ?: return
        if (!markBusy(entryId)) return
        viewModelScope.launch {
            val g = entry.garment
            val image = fetcher.fetch(g.imageUrl)
            if (image == null) {
                eventChannel.trySend(FavoritesEvent.Error("No se pudo descargar la foto de la prenda. Revisa tu conexión e inténtalo de nuevo."))
                clearBusy(entryId)
                return@launch
            }
            val draft = GarmentDraft(
                type = g.type, name = g.name, color = g.color, season = g.season, brand = g.brand, size = g.size,
                forSale = false, condition = g.condition, description = g.description,
            )
            when (val r = garments.add(draft, image)) {
                is ApiResult.Success -> {
                    // La prenda ya está en el armario: quitarla de favoritos es secundario y no debe verse como un fallo
                    val unfavorited = unfavorite(entry) == null
                    if (unfavorited) _state.update { it.copy(entries = it.entries - entry) }
                    eventChannel.trySend(FavoritesEvent.Success("«${g.name}» está ya en tu armario."))
                }
                is ApiResult.Failure -> eventChannel.trySend(FavoritesEvent.Error(r.error.message))
            }
            clearBusy(entryId)
        }
    }

    /** Abre un chat con la persona que vende el artículo, con el artículo como contexto. */
    fun contact(entryId: String) {
        val entry = _state.value.entries.firstOrNull { it.id == entryId } as? FavoriteEntry.Product ?: return
        val g = entry.garment
        val sellerId = g.userId ?: return
        if (sellerId == meId() || !markBusy(entryId)) return
        viewModelScope.launch {
            val r = chatLauncher.open(sellerId, g.id, g.name, g.imageUrl, interestMessage(g.name))
            clearBusy(entryId)
            when (r) {
                is ApiResult.Success -> eventChannel.trySend(FavoritesEvent.OpenChat(r.value))
                is ApiResult.Failure -> eventChannel.trySend(FavoritesEvent.Error(r.error.message))
            }
        }
    }

    /** `null` si salió bien; si no, el mensaje de error. */
    private suspend fun unfavorite(entry: FavoriteEntry): String? = when (entry) {
        is FavoriteEntry.Product -> {
            val id = entry.garment.id
            // Es un favorito: el cambio lo quita. Si el estado local no lo sabía, se alinea antes para no invertirlo.
            catalog.setFavorite(id, true)
            when (val r = catalog.toggleFavorite(id)) {
                is ApiResult.Failure -> r.error.message
                else -> null
            }
        }
        is FavoriteEntry.LookEntry -> when (val r = social.toggleLookFavorite(entry.look.id)) {
            is ApiResult.Failure -> r.error.message
            else -> null
        }
    }

    private fun markBusy(id: String): Boolean {
        if (id in _state.value.busy) return false
        _state.update { it.copy(busy = it.busy + id) }
        return true
    }

    private fun clearBusy(id: String) = _state.update { it.copy(busy = it.busy - id) }
}

private fun Favorite.toEntry(wardrobe: List<Garment>): FavoriteEntry? = when {
    garment != null -> FavoriteEntry.Product(id, garment, compatibilityLabel(garment, wardrobe))
    look != null -> FavoriteEntry.LookEntry(id, look)
    else -> null
}

/** Mismo criterio que la web: cuántas prendas tuyas coinciden en categoría o color. */
internal fun compatibilityLabel(garment: Garment, wardrobe: List<Garment>): String {
    val score = wardrobe.count { it.type == garment.type } + wardrobe.count { it.color.equals(garment.color, ignoreCase = true) }
    return when {
        score >= 6 -> "Combina con 6+ prendas"
        score >= 3 -> "Combina con 3+ prendas"
        score >= 1 -> "Combina con tu armario"
        else -> "Sin combinaciones claras"
    }
}
