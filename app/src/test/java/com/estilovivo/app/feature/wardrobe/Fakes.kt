package com.estilovivo.app.feature.wardrobe

import com.estilovivo.app.core.data.ApiError
import com.estilovivo.app.core.data.ApiErrorKind
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.ImageUpload
import com.estilovivo.app.core.data.Page
import com.estilovivo.app.core.data.model.Garment
import com.estilovivo.app.core.data.model.GarmentDraft
import com.estilovivo.app.core.data.model.Look
import com.estilovivo.app.core.data.model.LookChanges
import com.estilovivo.app.core.data.model.NewTripItem
import com.estilovivo.app.core.data.model.ShopItem
import com.estilovivo.app.core.data.model.Trip
import com.estilovivo.app.core.data.model.TripItem
import com.estilovivo.app.core.data.repo.GarmentRepository
import com.estilovivo.app.core.data.repo.LookRepository
import com.estilovivo.app.core.data.repo.TripRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

fun garment(
    id: String,
    name: String = "Prenda $id",
    type: String = "top",
    color: String = "negro",
    season: String = "all",
    usage: Int = 0,
    washing: Boolean = false,
    forSale: Boolean = false,
    price: Double = 0.0,
    brand: String? = null,
) = Garment(
    id = id, imageUrl = "https://img/$id.jpg", name = name, type = type, color = color, season = season,
    usageCount = usage, isWashing = washing, forSale = forSale, price = price, brand = brand,
)

fun failure(message: String, kind: ApiErrorKind = ApiErrorKind.Validation) = ApiResult.Failure(ApiError(message, kind))

class FakeGarmentRepo(initial: List<Garment> = emptyList()) : GarmentRepository {
    val state = MutableStateFlow(initial)
    override val garments: StateFlow<List<Garment>> = state

    var getAllResult: ApiResult<List<Garment>>? = null
    var addGate: CompletableDeferred<ApiResult<Garment>>? = null
    var addCalls = 0
    val addedDrafts = mutableListOf<GarmentDraft>()
    var updateResult: ((String, GarmentDraft) -> ApiResult<Garment>)? = null
    val updates = mutableListOf<Pair<String, GarmentDraft>>()
    var updateGate: CompletableDeferred<Unit>? = null
    var deleteResult: ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun getAll(): ApiResult<List<Garment>> = getAllResult ?: ApiResult.Success(state.value)

    override suspend fun add(draft: GarmentDraft, image: ImageUpload?): ApiResult<Garment> {
        addCalls++
        addedDrafts += draft
        val r = addGate?.await() ?: ApiResult.Success(
            garment("server-$addCalls", name = draft.name.orEmpty(), type = draft.type, forSale = draft.forSale == true, price = draft.price ?: 0.0),
        )
        if (r is ApiResult.Success) state.value = listOf(r.value) + state.value
        return r
    }

    override suspend fun update(id: String, draft: GarmentDraft, image: ImageUpload?): ApiResult<Garment> {
        updates += id to draft
        updateGate?.await()
        val r = updateResult?.invoke(id, draft) ?: run {
            val old = state.value.first { it.id == id }
            ApiResult.Success(
                old.copy(
                    name = draft.name ?: old.name, type = draft.type, forSale = draft.forSale ?: old.forSale,
                    price = draft.price ?: 0.0, isWashing = draft.isWashing ?: old.isWashing,
                ),
            )
        }
        if (r is ApiResult.Success) state.value = state.value.map { if (it.id == id) r.value else it }
        return r
    }

    override suspend fun delete(id: String): ApiResult<Unit> {
        if (deleteResult is ApiResult.Success) state.value = state.value.filterNot { it.id == id }
        return deleteResult
    }

    override suspend fun markWorn(id: String): ApiResult<Garment> {
        val old = state.value.first { it.id == id }
        val g = old.copy(usageCount = old.usageCount + 1)
        state.value = state.value.map { if (it.id == id) g else it }
        return ApiResult.Success(g)
    }

    override suspend fun shop(search: String?, category: String?): ApiResult<List<ShopItem>> = ApiResult.Success(emptyList())
    override suspend fun toggleFavorite(garmentId: String): ApiResult<Boolean> = ApiResult.Success(true)
}

class FakeLookRepo(initial: List<Look> = emptyList()) : LookRepository {
    val state = MutableStateFlow(initial)
    override val myLooks: StateFlow<List<Look>> = state
    var createResult: ApiResult<Look>? = null
    var createCalls = mutableListOf<List<String>>()
    var lastTags: List<String>? = null
    var lastMood: String? = null
    var updates = mutableListOf<Pair<String, LookChanges>>()

    override suspend fun getMine(): ApiResult<List<Look>> = ApiResult.Success(state.value)
    override suspend fun feed(cursor: String?, limit: Int): ApiResult<Page<Look>> = ApiResult.Success(Page(emptyList()))

    override suspend fun create(name: String, garmentIds: List<String>, isPublic: Boolean, mood: String?, tags: List<String>): ApiResult<Look> {
        createCalls += garmentIds
        lastTags = tags
        lastMood = mood
        val r = createResult ?: ApiResult.Success(Look("l-${createCalls.size}", name, garmentIds, emptyList(), tags, mood, isPublic = isPublic))
        if (r is ApiResult.Success) state.value = listOf(r.value) + state.value
        return r
    }

    override suspend fun createWithImage(name: String, garmentIds: List<String>, image: ImageUpload, isPublic: Boolean, mood: String?, tags: List<String>) =
        create(name, garmentIds, isPublic, mood, tags)

    override suspend fun update(id: String, changes: LookChanges): ApiResult<Look> {
        updates += id to changes
        val old = state.value.first { it.id == id }
        val l = old.copy(
            name = changes.name ?: old.name, isPublic = changes.isPublic ?: old.isPublic, mood = changes.mood ?: old.mood,
            garmentIds = changes.garmentIds ?: old.garmentIds, tags = changes.tags ?: old.tags,
        )
        state.value = state.value.map { if (it.id == id) l else it }
        return ApiResult.Success(l)
    }

    override suspend fun delete(id: String): ApiResult<Unit> {
        state.value = state.value.filterNot { it.id == id }
        return ApiResult.Success(Unit)
    }
}

class FakeTripRepo(var trips: List<Trip> = emptyList()) : TripRepository {
    override suspend fun getAll(): ApiResult<List<Trip>> = ApiResult.Success(trips)
    override suspend fun create(destination: String, dateStart: String, dateEnd: String, items: List<NewTripItem>, garmentIds: List<String>) =
        ApiResult.Success(Trip("t-new", destination, dateStart, dateEnd, emptyList(), emptyList()))
    override suspend fun updateMeta(tripId: String, destination: String, dateStart: String, dateEnd: String, garmentIds: List<String>) =
        ApiResult.Success(Trip(tripId, destination, dateStart, dateEnd, emptyList(), garmentIds.map { garment(it) }))
    override suspend fun delete(tripId: String) = ApiResult.Success(Unit)
    override suspend fun addItem(tripId: String, label: String, isEssential: Boolean) = ApiResult.Success(TripItem("i", label, false, isEssential))
    override suspend fun updateItem(tripId: String, itemId: String, checked: Boolean?, label: String?, isEssential: Boolean?) =
        ApiResult.Success(TripItem(itemId, label.orEmpty(), checked ?: false, isEssential ?: false))
    override suspend fun deleteItem(tripId: String, itemId: String) = ApiResult.Success(Unit)
}
