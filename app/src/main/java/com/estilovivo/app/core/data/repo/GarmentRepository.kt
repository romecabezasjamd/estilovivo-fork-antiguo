package com.estilovivo.app.core.data.repo

import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.AppScope
import com.estilovivo.app.core.data.ImageUpload
import com.estilovivo.app.core.data.model.Garment
import com.estilovivo.app.core.data.model.GarmentDraft
import com.estilovivo.app.core.data.model.ShopItem
import com.estilovivo.app.core.data.net.ApiService
import com.estilovivo.app.core.data.net.FavoriteRequest
import com.estilovivo.app.core.data.net.Mappers
import com.estilovivo.app.core.data.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Armario del usuario y tienda de la comunidad. */
interface GarmentRepository {
    /** Caché del armario propio, compartida por todas las pantallas. Se actualiza con cada operación de este repositorio. */
    val garments: StateFlow<List<Garment>>

    /** Descarga TODAS las prendas (pagina con cursor hasta agotar) y refresca [garments]. */
    suspend fun getAll(): ApiResult<List<Garment>>

    /** Alta (multipart). [image] es opcional pero lo normal es subir la foto. */
    suspend fun add(draft: GarmentDraft, image: ImageUpload? = null): ApiResult<Garment>

    /**
     * Edición completa (el servidor exige nombre y categoría): parte de `garment.toDraft()` y modifica con `copy`.
     * Con [image] se reemplaza la foto (multipart); sin ella se envía JSON.
     */
    suspend fun update(id: String, draft: GarmentDraft, image: ImageUpload? = null): ApiResult<Garment>

    suspend fun delete(id: String): ApiResult<Unit>

    /** "Me lo he puesto": suma un uso y actualiza `lastWorn`. */
    suspend fun markWorn(id: String): ApiResult<Garment>

    /** Prendas en venta de otras personas. [category] = null/"all" para todas. */
    suspend fun shop(search: String? = null, category: String? = null): ApiResult<List<ShopItem>>

    /** Añade/quita la prenda de favoritos. Devuelve `true` si ahora es favorita. */
    suspend fun toggleFavorite(garmentId: String): ApiResult<Boolean>
}

internal class GarmentRepositoryImpl(
    private val api: ApiService,
    private val mappers: Mappers,
    session: SessionManager,
    appScope: AppScope,
) : GarmentRepository {
    private val _garments = MutableStateFlow<List<Garment>>(emptyList())
    override val garments: StateFlow<List<Garment>> = _garments.asStateFlow()

    init {
        appScope.scope.onSessionEnded(session) { _garments.value = emptyList() }
    }

    override suspend fun getAll(): ApiResult<List<Garment>> = apiCall {
        val list = fetchAllPages { limit, cursor -> api.products(limit, cursor) }.mapNotNull { mappers.garment(it) }
        _garments.value = list
        list
    }

    override suspend fun add(draft: GarmentDraft, image: ImageUpload?): ApiResult<Garment> = apiCall {
        val dto = api.addProduct(
            fields = fields(draft, forUpdate = false),
            images = image?.toPart("images"),
        )
        val garment = mappers.garment(dto) ?: missing("la prenda")
        _garments.value = listOf(garment) + _garments.value
        garment
    }

    override suspend fun update(id: String, draft: GarmentDraft, image: ImageUpload?): ApiResult<Garment> = apiCall {
        val dto = if (image != null) {
            api.updateProductMultipart(id, fields(draft, forUpdate = true), image.toPart("images"))
        } else {
            api.updateProduct(
                id,
                buildJsonObject {
                    put("name", draft.name?.takeIf { it.isNotBlank() } ?: draft.type)
                    put("category", draft.type)
                    draft.color?.let { put("color", it) }
                    draft.season?.let { put("season", it) }
                    // El servidor interpreta "" como "sin precio"
                    put("price", draft.price?.toString() ?: "")
                    draft.forSale?.let { put("forSale", it) }
                    draft.isWashing?.let { put("isWashing", it) }
                    draft.brand?.let { put("brand", it) }
                    draft.size?.let { put("size", it) }
                    draft.condition?.let { put("condition", it) }
                    draft.description?.let { put("description", it) }
                },
            )
        }
        val garment = mappers.garment(dto) ?: missing("la prenda")
        _garments.value = _garments.value.map { if (it.id == id) garment else it }
        garment
    }

    override suspend fun delete(id: String): ApiResult<Unit> = apiCall {
        api.deleteProduct(id)
        _garments.value = _garments.value.filterNot { it.id == id }
    }

    override suspend fun markWorn(id: String): ApiResult<Garment> = apiCall {
        val garment = mappers.garment(api.wearProduct(id)) ?: missing("la prenda")
        _garments.value = _garments.value.map { if (it.id == id) garment else it }
        garment
    }

    override suspend fun shop(search: String?, category: String?): ApiResult<List<ShopItem>> = apiCall {
        api.shop(search?.trim()?.takeIf { it.isNotEmpty() }, category?.takeIf { it != "all" && it.isNotEmpty() }).map(mappers::shopItem)
    }

    override suspend fun toggleFavorite(garmentId: String): ApiResult<Boolean> =
        apiCall { api.favorite(FavoriteRequest(productId = garmentId)).favorited }

    private fun fields(d: GarmentDraft, forUpdate: Boolean) = textParts(
        "name" to (d.name?.takeIf { it.isNotBlank() } ?: d.type),
        "category" to d.type,
        "color" to d.color,
        "season" to d.season,
        "brand" to d.brand,
        "size" to d.size,
        "forSale" to d.forSale,
        "price" to (d.price?.toString() ?: if (forUpdate) "" else null),
        "condition" to d.condition,
        "description" to d.description,
        "isWashing" to d.isWashing,
    )
}
