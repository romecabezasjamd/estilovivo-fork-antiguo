package com.estilovivo.app.feature.suitcase

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.ImageUpload
import com.estilovivo.app.core.data.model.Favorite
import com.estilovivo.app.core.data.model.Garment
import com.estilovivo.app.core.data.model.GarmentDraft
import com.estilovivo.app.core.data.model.Look
import com.estilovivo.app.core.data.repo.ChatRepository
import com.estilovivo.app.core.data.repo.GarmentRepository
import com.estilovivo.app.core.data.repo.SocialRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Descarga la foto de una prenda para volver a subirla al copiarla al armario. */
interface PhotoDownloader {
    suspend fun download(url: String): ImageUpload?
}

/** Las fotos de las prendas son públicas: se piden sin credenciales (no se envía el token a ningún host). */
class OkHttpPhotoDownloader(private val client: OkHttpClient) : PhotoDownloader {
    override suspend fun download(url: String): ImageUpload? = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://") && !url.startsWith("http://")) return@withContext null
        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val mime = response.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase().orEmpty()
                if (!mime.startsWith("image/")) return@use null
                val bytes = response.body.bytes()
                if (bytes.isEmpty() || bytes.size > MAX_BYTES) return@use null
                val ext = when (mime) {
                    "image/png" -> "png"
                    "image/webp" -> "webp"
                    else -> "jpg"
                }
                ImageUpload("garment-${System.currentTimeMillis()}.$ext", mime, bytes)
            }
        }.getOrNull()
    }

    private companion object { const val MAX_BYTES = 12 * 1024 * 1024 }
}

/** Reglas de la wishlist (sin Android, testeables). */
object WishlistLogic {
    /** Igual que la web: prendas tuyas que coinciden con la categoría + las que coinciden con el color. */
    fun compatibilityScore(article: Garment, wardrobe: List<Garment>): Int =
        wardrobe.count { it.type == article.type } + wardrobe.count { it.color.equals(article.color, ignoreCase = true) }

    fun compatibilityLabel(article: Garment, wardrobe: List<Garment>): String {
        val score = compatibilityScore(article, wardrobe)
        return when {
            score >= 6 -> "Combina con 6+ prendas"
            score >= 3 -> "Combina con 3+ prendas"
            score >= 1 -> "Combina con tu armario"
            else -> "Sin combinaciones claras"
        }
    }

    fun interestMessage(name: String) = "Hola, me interesa «$name». ¿Sigue disponible?"
}

@Immutable
data class WishArticle(val favoriteId: String, val garment: Garment, val compatibility: String, val isMine: Boolean)

@Immutable
data class WishLook(val favoriteId: String, val look: Look)

@Immutable
data class WishlistUiState(
    val articles: List<WishArticle> = emptyList(),
    val looks: List<WishLook> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    /** Favoritos con una acción en curso. */
    val busy: Set<String> = emptySet(),
    /** Artículos ya copiados al armario en esta sesión (no se vuelven a copiar). */
    val moved: Set<String> = emptySet(),
) {
    val total get() = articles.size + looks.size
}

sealed interface WishlistEvent {
    data class Message(val text: String, val error: Boolean = false) : WishlistEvent
    data class OpenChat(val conversationId: String) : WishlistEvent
}

class WishlistViewModel(
    private val social: SocialRepository,
    private val garments: GarmentRepository,
    private val chat: ChatRepository,
    private val photos: PhotoDownloader,
    private val meId: () -> String?,
) : ViewModel() {

    private data class Aux(
        val favorites: List<Favorite> = emptyList(),
        val loading: Boolean = true,
        val refreshing: Boolean = false,
        val error: String? = null,
        val busy: Set<String> = emptySet(),
        val moved: Set<String> = emptySet(),
    )

    private val aux = MutableStateFlow(Aux())
    private val _events = Channel<WishlistEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val state: StateFlow<WishlistUiState> = combine(garments.garments, aux) { wardrobe, a ->
        val me = meId()
        WishlistUiState(
            articles = a.favorites.mapNotNull { f ->
                f.garment?.let { g -> WishArticle(f.id, g, WishlistLogic.compatibilityLabel(g, wardrobe), isMine = me != null && g.userId == me) }
            },
            looks = a.favorites.mapNotNull { f -> if (f.garment == null) f.look?.let { WishLook(f.id, it) } else null },
            loading = a.loading && a.favorites.isEmpty(),
            refreshing = a.refreshing,
            error = a.error.takeIf { a.favorites.isEmpty() },
            busy = a.busy,
            moved = a.moved,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WishlistUiState())

    init {
        load(pull = false)
        if (garments.garments.value.isEmpty()) viewModelScope.launch { garments.getAll() }
    }

    fun load(pull: Boolean = false) {
        if (aux.value.refreshing) return
        aux.update { if (pull) it.copy(refreshing = true) else it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = social.getFavorites()) {
                is ApiResult.Success -> aux.update { it.copy(favorites = r.value, loading = false, refreshing = false, error = null) }
                is ApiResult.Failure -> {
                    val hadItems = aux.value.favorites.isNotEmpty()
                    aux.update { it.copy(loading = false, refreshing = false, error = r.error.message) }
                    if (hadItems) _events.send(WishlistEvent.Message(r.error.message, error = true))
                }
            }
        }
    }

    /** Quita un favorito (corazón o "Comprado"): sale al instante y vuelve a su sitio si el servidor falla. */
    fun remove(favoriteId: String, purchased: Boolean = false) {
        val fav = aux.value.favorites.firstOrNull { it.id == favoriteId } ?: return
        if (!markBusy(favoriteId)) return
        val index = aux.value.favorites.indexOf(fav)
        aux.update { it.copy(favorites = it.favorites - fav) }
        viewModelScope.launch {
            val failure = unfavorite(fav)
            if (failure != null) {
                aux.update { a ->
                    if (a.favorites.any { it.id == fav.id }) a
                    else a.copy(favorites = a.favorites.toMutableList().apply { add(index.coerceIn(0, size), fav) })
                }
                _events.send(WishlistEvent.Message(failure, error = true))
            } else if (purchased) {
                _events.send(WishlistEvent.Message("¡Enhorabuena por la compra! La hemos quitado de tu wishlist."))
            }
            clearBusy(favoriteId)
        }
    }

    /** Copia el artículo a tu armario CON su foto; si la foto falla no se crea nada. */
    fun moveToWardrobe(favoriteId: String) {
        val fav = aux.value.favorites.firstOrNull { it.id == favoriteId } ?: return
        val g = fav.garment ?: return
        if (favoriteId in aux.value.moved || !markBusy(favoriteId)) return
        viewModelScope.launch {
            val image = photos.download(g.imageUrl)
            if (image == null) {
                _events.send(WishlistEvent.Message("No se pudo descargar la foto de la prenda. Revisa tu conexión e inténtalo de nuevo.", error = true))
                clearBusy(favoriteId)
                return@launch
            }
            val draft = GarmentDraft(
                type = g.type, name = g.name, color = g.color, season = g.season, brand = g.brand, size = g.size,
                forSale = false, condition = g.condition, description = g.description,
            )
            when (val r = garments.add(draft, image)) {
                is ApiResult.Success -> {
                    // Ya está en tu armario: quitarla de favoritos es secundario y no debe verse como un fallo.
                    val unfavorited = unfavorite(fav) == null
                    aux.update {
                        it.copy(
                            favorites = if (unfavorited) it.favorites - fav else it.favorites,
                            moved = if (unfavorited) it.moved else it.moved + favoriteId,
                        )
                    }
                    _events.send(WishlistEvent.Message("«${g.name}» ya está en tu armario."))
                }
                is ApiResult.Failure -> _events.send(WishlistEvent.Message(r.error.message, error = true))
            }
            clearBusy(favoriteId)
        }
    }

    /** Abre (o continúa) un chat con quien vende el artículo, con el artículo como contexto. */
    fun contact(favoriteId: String) {
        val g = aux.value.favorites.firstOrNull { it.id == favoriteId }?.garment ?: return
        val sellerId = g.userId?.takeIf { it.isNotBlank() } ?: return
        if (sellerId == meId() || !markBusy(favoriteId)) return
        viewModelScope.launch {
            val r = chat.createConversation(sellerId, g.id, g.name.take(200), g.imageUrl, WishlistLogic.interestMessage(g.name).take(1000))
            clearBusy(favoriteId)
            when (r) {
                is ApiResult.Success -> _events.send(WishlistEvent.OpenChat(r.value.id))
                is ApiResult.Failure -> _events.send(WishlistEvent.Message(r.error.message, error = true))
            }
        }
    }

    /**
     * `null` si salió bien; si no, el mensaje de error. El servidor ALTERNA el favorito: si la respuesta dice que ahora
     * SÍ es favorito (alguien lo había quitado desde otro dispositivo), se alterna otra vez para dejarlo quitado.
     */
    private suspend fun unfavorite(fav: Favorite): String? {
        suspend fun toggle(): ApiResult<Boolean> =
            fav.garment?.let { social.toggleGarmentFavorite(it.id) }
                ?: fav.look?.let { social.toggleLookFavorite(it.id) }
                ?: ApiResult.Success(false)
        var r = toggle()
        if (r is ApiResult.Success && r.value) r = toggle()
        return when (r) {
            is ApiResult.Failure -> r.error.message
            is ApiResult.Success -> if (r.value) "No se pudo quitar de tu wishlist. Inténtalo de nuevo." else null
        }
    }

    private fun markBusy(id: String): Boolean {
        if (id in aux.value.busy) return false
        aux.update { it.copy(busy = it.busy + id) }
        return true
    }

    private fun clearBusy(id: String) = aux.update { it.copy(busy = it.busy - id) }
}
