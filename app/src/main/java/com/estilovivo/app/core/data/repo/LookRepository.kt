package com.estilovivo.app.core.data.repo

import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.AppScope
import com.estilovivo.app.core.data.ImageUpload
import com.estilovivo.app.core.data.Page
import com.estilovivo.app.core.data.model.Look
import com.estilovivo.app.core.data.model.LookChanges
import com.estilovivo.app.core.data.net.ApiService
import com.estilovivo.app.core.data.net.Mappers
import com.estilovivo.app.core.data.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Looks propios y feed de la comunidad. */
interface LookRepository {
    /** Caché de mis looks, compartida por las pantallas. */
    val myLooks: StateFlow<List<Look>>

    /** TODOS mis looks (cursor hasta agotar); refresca [myLooks]. */
    suspend fun getMine(): ApiResult<List<Look>>

    /** Una página del feed público (`cursor` = `nextCursor` de la anterior; null para la primera). */
    suspend fun feed(cursor: String? = null, limit: Int = 20): ApiResult<Page<Look>>

    /** Look sin imagen (sólo composición de prendas). */
    suspend fun create(
        name: String,
        garmentIds: List<String>,
        isPublic: Boolean = false,
        mood: String? = null,
        tags: List<String> = emptyList(),
    ): ApiResult<Look>

    /** Look con imagen (multipart). */
    suspend fun createWithImage(
        name: String,
        garmentIds: List<String>,
        image: ImageUpload,
        isPublic: Boolean = false,
        mood: String? = null,
        tags: List<String> = emptyList(),
    ): ApiResult<Look>

    suspend fun update(id: String, changes: LookChanges): ApiResult<Look>
    suspend fun delete(id: String): ApiResult<Unit>
}

internal class LookRepositoryImpl(
    private val api: ApiService,
    private val mappers: Mappers,
    session: SessionManager,
    appScope: AppScope,
) : LookRepository {
    private val _mine = MutableStateFlow<List<Look>>(emptyList())
    override val myLooks: StateFlow<List<Look>> = _mine.asStateFlow()

    init {
        appScope.scope.onSessionEnded(session) { _mine.value = emptyList() }
    }

    override suspend fun getMine(): ApiResult<List<Look>> = apiCall {
        val list = fetchAllPages { limit, cursor -> api.looks(limit, cursor) }.mapNotNull { mappers.look(it) }
        _mine.value = list
        list
    }

    override suspend fun feed(cursor: String?, limit: Int): ApiResult<Page<Look>> = apiCall {
        val page = api.feed(limit, cursor)
        Page(page.items.mapNotNull { mappers.look(it) }, page.nextCursor, page.hasMore)
    }

    override suspend fun create(name: String, garmentIds: List<String>, isPublic: Boolean, mood: String?, tags: List<String>): ApiResult<Look> =
        apiCall {
            val dto = api.createLook(
                buildJsonObject {
                    put("title", name.ifBlank { "Sin título" })
                    // La API espera `productIds` como string JSON (igual que la web)
                    put("productIds", JsonArray(garmentIds.map(::JsonPrimitive)).toString())
                    put("isPublic", isPublic)
                    mood?.let { put("mood", it) }
                    put("tags", JsonArray(tags.map(::JsonPrimitive)))
                },
            )
            val look = mappers.look(dto) ?: missing("el look")
            _mine.value = listOf(look) + _mine.value
            look
        }

    override suspend fun createWithImage(
        name: String,
        garmentIds: List<String>,
        image: ImageUpload,
        isPublic: Boolean,
        mood: String?,
        tags: List<String>,
    ): ApiResult<Look> = apiCall {
        val dto = api.createLookMultipart(
            fields = textParts(
                "title" to name.ifBlank { "Sin título" },
                "productIds" to JsonArray(garmentIds.map(::JsonPrimitive)).toString(),
                "isPublic" to isPublic,
                "mood" to mood,
                "tags" to if (tags.isEmpty()) null else JsonArray(tags.map(::JsonPrimitive)).toString(),
            ),
            images = image.toPart("images"),
        )
        val look = mappers.look(dto) ?: missing("el look")
        _mine.value = listOf(look) + _mine.value
        look
    }

    override suspend fun update(id: String, changes: LookChanges): ApiResult<Look> = apiCall {
        val dto = api.updateLook(
            id,
            buildJsonObject {
                changes.name?.let { put("title", it) }
                changes.isPublic?.let { put("isPublic", it) }
                changes.mood?.let { put("mood", it) }
                changes.garmentIds?.let { put("productIds", JsonArray(it.map(::JsonPrimitive)).toString()) }
                changes.tags?.let { put("tags", JsonArray(it.map(::JsonPrimitive))) }
            },
        )
        val fresh = mappers.look(dto) ?: missing("el look")
        // La respuesta de edición no trae contadores ni "me gusta": se conservan los conocidos
        val old = _mine.value.firstOrNull { it.id == id }
        val look = if (old == null) fresh else fresh.copy(
            likesCount = old.likesCount, commentsCount = old.commentsCount, isLiked = old.isLiked, isFavorited = old.isFavorited,
        )
        _mine.value = _mine.value.map { if (it.id == id) look else it }
        look
    }

    override suspend fun delete(id: String): ApiResult<Unit> = apiCall {
        api.deleteLook(id)
        _mine.value = _mine.value.filterNot { it.id == id }
    }
}
