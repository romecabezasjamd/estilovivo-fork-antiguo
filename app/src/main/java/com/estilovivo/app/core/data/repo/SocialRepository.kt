package com.estilovivo.app.core.data.repo

import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.model.Comment
import com.estilovivo.app.core.data.model.Favorite
import com.estilovivo.app.core.data.model.FollowRequest
import com.estilovivo.app.core.data.model.FollowResult
import com.estilovivo.app.core.data.model.LikeResult
import com.estilovivo.app.core.data.model.PublicProfile
import com.estilovivo.app.core.data.model.TopUser
import com.estilovivo.app.core.data.net.ApiService
import com.estilovivo.app.core.data.net.CommentRequest
import com.estilovivo.app.core.data.net.FavoriteRequest
import com.estilovivo.app.core.data.net.FollowRequestBody
import com.estilovivo.app.core.data.net.LikeRequest
import com.estilovivo.app.core.data.net.Mappers
import java.util.concurrent.atomic.AtomicReference

/** Interacción social: likes, comentarios, favoritos, seguimiento y perfiles ajenos. */
interface SocialRepository {
    suspend fun toggleLike(lookId: String): ApiResult<LikeResult>

    /** Comentarios raíz (más nuevos primero), cada uno con sus `replies`. */
    suspend fun getComments(lookId: String): ApiResult<List<Comment>>

    /** [parentId] para responder a un comentario. Máx. 500 caracteres. */
    suspend fun addComment(lookId: String, content: String, parentId: String? = null): ApiResult<Comment>

    /** Puede borrar el autor del comentario o el dueño del look. */
    suspend fun deleteComment(commentId: String): ApiResult<Unit>

    /** Devuelve `true` si el look queda en favoritos. */
    suspend fun toggleLookFavorite(lookId: String): ApiResult<Boolean>

    /** Devuelve `true` si la prenda queda en favoritos. */
    suspend fun toggleGarmentFavorite(garmentId: String): ApiResult<Boolean>

    suspend fun getFavorites(): ApiResult<List<Favorite>>

    /**
     * Seguir/dejar de seguir. En perfiles privados crea (o cancela) una solicitud: mira [FollowResult.requesting].
     */
    suspend fun toggleFollow(userId: String): ApiResult<FollowResult>

    suspend fun getFollowRequests(): ApiResult<List<FollowRequest>>
    suspend fun approveFollowRequest(requestId: String): ApiResult<Unit>
    suspend fun rejectFollowRequest(requestId: String): ApiResult<Unit>

    /** Perfil de otra persona con `isFollowing`/`hasPendingRequest`. */
    suspend fun getUserProfile(userId: String): ApiResult<PublicProfile>

    /** Ranking de XP (top 5). Cacheado 5 min salvo [forceRefresh]. */
    suspend fun getTopUsers(forceRefresh: Boolean = false): ApiResult<List<TopUser>>
}

internal class SocialRepositoryImpl(private val api: ApiService, private val mappers: Mappers) : SocialRepository {
    private data class Cached(val at: Long, val users: List<TopUser>)

    private val topCache = AtomicReference<Cached?>(null)

    override suspend fun toggleLike(lookId: String) = apiCall {
        api.like(LikeRequest(lookId)).let { LikeResult(it.liked, it.likesCount) }
    }

    override suspend fun getComments(lookId: String) = apiCall { api.comments(lookId).map(mappers::comment) }

    override suspend fun addComment(lookId: String, content: String, parentId: String?) =
        apiCall { mappers.comment(api.addComment(CommentRequest(lookId, content.trim(), parentId))) }

    override suspend fun deleteComment(commentId: String) = apiCall { api.deleteComment(commentId); Unit }

    override suspend fun toggleLookFavorite(lookId: String) = apiCall { api.favorite(FavoriteRequest(lookId = lookId)).favorited }

    override suspend fun toggleGarmentFavorite(garmentId: String) = apiCall { api.favorite(FavoriteRequest(productId = garmentId)).favorited }

    override suspend fun getFavorites() = apiCall { api.favorites().map(mappers::favorite) }

    override suspend fun toggleFollow(userId: String) = apiCall {
        api.follow(FollowRequestBody(userId)).let { FollowResult(following = it.following ?: false, requesting = it.requesting ?: false) }
    }

    override suspend fun getFollowRequests() = apiCall { api.followRequests().map(mappers::followRequest) }

    override suspend fun approveFollowRequest(requestId: String) = apiCall { api.approveFollowRequest(requestId); Unit }

    override suspend fun rejectFollowRequest(requestId: String) = apiCall { api.rejectFollowRequest(requestId); Unit }

    override suspend fun getUserProfile(userId: String) = apiCall { mappers.publicProfile(api.userProfile(userId)) }

    override suspend fun getTopUsers(forceRefresh: Boolean): ApiResult<List<TopUser>> {
        val cached = topCache.get()
        if (!forceRefresh && cached != null && System.currentTimeMillis() - cached.at < CACHE_TTL_MS) return ApiResult.Success(cached.users)
        return apiCall {
            val users = api.topUsers().map(mappers::topUser)
            topCache.set(Cached(System.currentTimeMillis(), users))
            users
        }
    }

    private companion object {
        const val CACHE_TTL_MS = 5 * 60 * 1000L
    }
}
