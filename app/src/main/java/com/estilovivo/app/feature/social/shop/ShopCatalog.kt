package com.estilovivo.app.feature.social.shop

import com.estilovivo.app.core.data.ApiError
import com.estilovivo.app.core.data.ApiErrorKind
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.map
import com.estilovivo.app.core.data.model.Favorite
import com.estilovivo.app.core.data.model.Garment
import com.estilovivo.app.core.data.model.ShopItem
import com.estilovivo.app.core.data.repo.GarmentRepository
import com.estilovivo.app.core.data.repo.SocialRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

/**
 * Fuente única de la tienda: catálogo (con caché para el detalle) y estado de favoritos compartido por
 * la rejilla, el detalle y la pantalla de favoritos, de forma que un corazón cambia en todas partes a la vez.
 */
class ShopCatalog(
    private val garments: GarmentRepository,
    private val social: SocialRepository,
) {
    private val cache = ConcurrentHashMap<String, ShopItem>()
    private val inFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())

    /** Ids de prendas (artículos) marcadas como favoritas. */
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    /** Artículos a la venta de otras personas. Igual que la web, sólo se muestran los que tienen precio. */
    suspend fun search(query: String?, category: String?): ApiResult<List<ShopItem>> =
        garments.shop(query, category).map { list ->
            val priced = list.filter { it.price > 0.0 }
            priced.forEach { cache[it.id] = it }
            priced
        }

    /**
     * Artículo por id: primero la caché (lo que se acaba de ver en la rejilla), después el catálogo completo y, por último,
     * los favoritos (por si el artículo ya no sale en el catálogo pero la persona lo guardó). El servidor no tiene
     * un endpoint de detalle de artículo ajeno.
     */
    suspend fun find(id: String): ApiResult<ShopItem> {
        cache[id]?.let { return ApiResult.Success(it) }
        val fresh = search(null, null)
        fresh.getOrNull()?.firstOrNull { it.id == id }?.let { return ApiResult.Success(it) }
        val favorites = social.getFavorites()
        favorites.getOrNull()?.firstNotNullOfOrNull { fav -> fav.garment?.takeIf { it.id == id } }?.let { g ->
            return ApiResult.Success(g.toShopItem().also { cache[id] = it })
        }
        // Si todo falló por la red, el error de red es más útil que "ya no está"
        val netError = (fresh as? ApiResult.Failure)?.error?.takeIf { it.kind == ApiErrorKind.Network || it.kind == ApiErrorKind.Timeout }
        return ApiResult.Failure(netError ?: ApiError("Este artículo ya no está disponible.", ApiErrorKind.NotFound))
    }

    /** Descarga los favoritos y sincroniza [favoriteIds] (sin pisar un cambio optimista aún en vuelo). */
    suspend fun refreshFavorites(): ApiResult<List<Favorite>> {
        val result = social.getFavorites()
        result.getOrNull()?.let { list ->
            val serverIds = list.mapNotNull { it.garment?.id }.toSet()
            _favoriteIds.update { local ->
                val pendingLocal = inFlight.toSet()
                (serverIds - pendingLocal) + (local intersect pendingLocal)
            }
        }
        return result
    }

    /**
     * Cambia el favorito de forma optimista. Devuelve `null` si ya hay un cambio en curso para ese artículo
     * (doble toque ignorado); si falla, deshace el cambio y devuelve el error.
     */
    suspend fun toggleFavorite(garmentId: String): ApiResult<Boolean>? {
        if (!inFlight.add(garmentId)) return null
        val was = garmentId in _favoriteIds.value
        setFavorite(garmentId, !was)
        try {
            val result = garments.toggleFavorite(garmentId)
            when (result) {
                is ApiResult.Success -> setFavorite(garmentId, result.value)
                is ApiResult.Failure -> setFavorite(garmentId, was)
            }
            return result
        } catch (e: CancellationException) {
            setFavorite(garmentId, was)
            throw e
        } finally {
            inFlight.remove(garmentId)
        }
    }

    /** Fuerza el estado local (p. ej. tras quitar un favorito desde la pantalla de favoritos). */
    fun setFavorite(garmentId: String, favorite: Boolean) {
        _favoriteIds.update { if (favorite) it + garmentId else it - garmentId }
    }
}

/** Una prenda guardada como favorita (con una sola foto) como artículo de tienda. */
internal fun Garment.toShopItem(): ShopItem = ShopItem(
    id = id,
    user = userName ?: "Vendedor",
    userId = userId.orEmpty(),
    avatar = userAvatar.orEmpty(),
    image = imageUrl,
    title = name,
    price = price,
    size = size ?: "Única",
    brand = brand ?: "Sin marca",
    condition = condition,
    images = listOf(imageUrl),
    color = color,
    description = description,
    category = type,
    createdAt = createdAt.orEmpty(),
)
