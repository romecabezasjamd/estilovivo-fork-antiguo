package com.estilovivo.app.feature.social.feed

import com.estilovivo.app.core.data.ApiError
import com.estilovivo.app.core.data.ApiErrorKind
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.ImageUpload
import com.estilovivo.app.core.data.Page
import com.estilovivo.app.core.data.model.Challenge
import com.estilovivo.app.core.data.model.ChallengeHistory
import com.estilovivo.app.core.data.model.ChallengeSubmission
import com.estilovivo.app.core.data.model.Comment
import com.estilovivo.app.core.data.model.Conversation
import com.estilovivo.app.core.data.model.Favorite
import com.estilovivo.app.core.data.model.FollowRequest
import com.estilovivo.app.core.data.model.FollowResult
import com.estilovivo.app.core.data.model.LikeResult
import com.estilovivo.app.core.data.model.Look
import com.estilovivo.app.core.data.model.LookChanges
import com.estilovivo.app.core.data.model.Message
import com.estilovivo.app.core.data.model.PublicProfile
import com.estilovivo.app.core.data.model.Story
import com.estilovivo.app.core.data.model.StoryReactionResult
import com.estilovivo.app.core.data.model.SubmitResult
import com.estilovivo.app.core.data.model.TopUser
import com.estilovivo.app.core.data.model.Trend
import com.estilovivo.app.core.data.model.UploadedImage
import com.estilovivo.app.core.data.model.User
import com.estilovivo.app.core.data.repo.ChallengeRepository
import com.estilovivo.app.core.data.repo.ChatRepository
import com.estilovivo.app.core.data.repo.LookRepository
import com.estilovivo.app.core.data.repo.SocialRepository
import com.estilovivo.app.core.data.repo.StoryRepository
import com.estilovivo.app.core.data.repo.TrendsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(UnconfinedTestDispatcher())
    override fun finished(description: Description) = Dispatchers.resetMain()
}

fun failure(msg: String = "Sin conexión", kind: ApiErrorKind = ApiErrorKind.Network) = ApiResult.Failure(ApiError(msg, kind))

fun look(
    id: String, userId: String? = "u-other", liked: Boolean = false, likes: Int = 0, comments: Int = 0,
    mood: String? = null, tags: List<String> = emptyList(), fav: Boolean = false,
) = Look(
    id = id, name = "Look $id", garmentIds = emptyList(), garments = emptyList(), tags = tags, mood = mood,
    createdAt = "2026-10-01T10:00:00.000Z", isPublic = true, imageUrl = "https://x/$id.jpg",
    userId = userId, userName = "Ana", likesCount = likes, commentsCount = comments, isLiked = liked, isFavorited = fav,
)

fun comment(id: String, userId: String = "u-other", parentId: String? = null, replies: List<Comment> = emptyList()) =
    Comment(id = id, content = "c$id", userId = userId, userName = "User $userId", parentId = parentId, replies = replies)

fun story(id: String, userId: String = "u-other", own: Boolean = false, created: String = "2026-10-13T10:00:00.000Z", expires: String = "2099-01-01T00:00:00.000Z") =
    Story(id, userId, "Nombre $userId", null, "text", "hola", null, 0, created, expires, own)

fun user(id: String = "me", name: String = "Yo", level: Int = 1, xp: Int = 0) = User(id = id, name = name, level = level, experiencePoints = xp)

class FakeMe(initial: User? = user()) : MeProvider {
    override val me = MutableStateFlow(initial)
    var refreshCount = 0
    override suspend fun refresh() { refreshCount++ }
}

class FakeLookRepository : LookRepository {
    override val myLooks: StateFlow<List<Look>> = MutableStateFlow(emptyList())
    val feedCursors = mutableListOf<String?>()
    var feedHandler: suspend (String?) -> ApiResult<Page<Look>> = { ApiResult.Success(Page(emptyList())) }
    var createHandler: suspend () -> ApiResult<Look> = { error("unused") }
    var deleteHandler: suspend (String) -> ApiResult<Unit> = { ApiResult.Success(Unit) }
    var lastCreatedName: String? = null

    override suspend fun getMine() = ApiResult.Success(emptyList<Look>())
    override suspend fun feed(cursor: String?, limit: Int): ApiResult<Page<Look>> { feedCursors += cursor; return feedHandler(cursor) }
    override suspend fun create(name: String, garmentIds: List<String>, isPublic: Boolean, mood: String?, tags: List<String>): ApiResult<Look> = error("unused")
    override suspend fun createWithImage(name: String, garmentIds: List<String>, image: ImageUpload, isPublic: Boolean, mood: String?, tags: List<String>): ApiResult<Look> {
        lastCreatedName = name
        return createHandler()
    }
    override suspend fun update(id: String, changes: LookChanges): ApiResult<Look> = error("unused")
    override suspend fun delete(id: String) = deleteHandler(id)
}

class FakeSocialRepository : SocialRepository {
    var likeHandler: suspend (String) -> ApiResult<LikeResult> = { ApiResult.Success(LikeResult(true, 1)) }
    var favoriteHandler: suspend (String) -> ApiResult<Boolean> = { ApiResult.Success(true) }
    var commentsHandler: suspend (String) -> ApiResult<List<Comment>> = { ApiResult.Success(emptyList()) }
    var addCommentHandler: suspend (String, String, String?) -> ApiResult<Comment> = { _, c, p -> ApiResult.Success(comment("new", "me", p).copy(content = c)) }
    var deleteCommentHandler: suspend (String) -> ApiResult<Unit> = { ApiResult.Success(Unit) }
    var followHandler: suspend (String) -> ApiResult<FollowResult> = { ApiResult.Success(FollowResult(true, false)) }
    var profileHandler: suspend (String) -> ApiResult<PublicProfile> = { error("unused") }
    var topUsers: List<TopUser> = emptyList()
    var likeCalls = 0
    val followCalls = mutableListOf<String>()

    override suspend fun toggleLike(lookId: String): ApiResult<LikeResult> { likeCalls++; return likeHandler(lookId) }
    override suspend fun getComments(lookId: String) = commentsHandler(lookId)
    override suspend fun addComment(lookId: String, content: String, parentId: String?) = addCommentHandler(lookId, content, parentId)
    override suspend fun deleteComment(commentId: String) = deleteCommentHandler(commentId)
    override suspend fun toggleLookFavorite(lookId: String) = favoriteHandler(lookId)
    override suspend fun toggleGarmentFavorite(garmentId: String): ApiResult<Boolean> = error("unused")
    override suspend fun getFavorites(): ApiResult<List<Favorite>> = error("unused")
    override suspend fun toggleFollow(userId: String): ApiResult<FollowResult> { followCalls += userId; return followHandler(userId) }
    override suspend fun getFollowRequests(): ApiResult<List<FollowRequest>> = error("unused")
    override suspend fun approveFollowRequest(requestId: String): ApiResult<Unit> = error("unused")
    override suspend fun rejectFollowRequest(requestId: String): ApiResult<Unit> = error("unused")
    override suspend fun getUserProfile(userId: String) = profileHandler(userId)
    override suspend fun getTopUsers(forceRefresh: Boolean): ApiResult<List<TopUser>> = ApiResult.Success(topUsers)
}

class FakeStoryRepository : StoryRepository {
    var stories: List<Story> = emptyList()
    val viewed = mutableListOf<String>()
    var reactHandler: suspend (String, String) -> ApiResult<StoryReactionResult> = { _, _ -> ApiResult.Success(StoryReactionResult(null, "conv")) }
    var deleteHandler: suspend (String) -> ApiResult<Unit> = { ApiResult.Success(Unit) }
    var createHandler: suspend () -> ApiResult<Story> = { error("unused") }

    override suspend fun getStories() = ApiResult.Success(stories)
    override suspend fun create(type: String, text: String?, image: ImageUpload?, existingImageUrl: String?) = createHandler()
    override suspend fun markViewed(storyId: String): ApiResult<Unit> { viewed += storyId; return ApiResult.Success(Unit) }
    override suspend fun delete(storyId: String) = deleteHandler(storyId)
    override suspend fun react(storyId: String, emoji: String) = reactHandler(storyId, emoji)
}

class FakeChallengeRepository : ChallengeRepository {
    var current = Challenge("ch1", "Color Block", "desc", 50, "Color Block", "2026-10-12T00:00:00Z", "2026-10-18T23:59:59Z", true)
    var mine: List<ChallengeSubmission> = emptyList()
    var submitHandler: suspend (String, String) -> ApiResult<SubmitResult> = { _, _ -> error("unused") }
    var deleteHandler: suspend (String) -> ApiResult<Unit> = { ApiResult.Success(Unit) }
    var submitCalls = 0

    override suspend fun getCurrent() = ApiResult.Success(current)
    override suspend fun getMySubmissions() = ApiResult.Success(mine)
    override suspend fun getHistory(): ApiResult<ChallengeHistory> = ApiResult.Success(ChallengeHistory(emptyList(), 0))
    override suspend fun submit(challengeId: String, description: String, image: ImageUpload): ApiResult<SubmitResult> {
        submitCalls++
        return submitHandler(challengeId, description)
    }
    override suspend fun deleteSubmission(submissionId: String) = deleteHandler(submissionId)
}

class FakeChatRepository : ChatRepository {
    var createHandler: suspend (String) -> ApiResult<Conversation> = { ApiResult.Success(Conversation(id = "conv-1")) }
    override suspend fun getConversations(): ApiResult<List<Conversation>> = error("unused")
    override suspend fun createConversation(targetUserId: String, itemId: String?, itemTitle: String?, itemImage: String?, initialMessage: String?) = createHandler(targetUserId)
    override suspend fun getMessages(conversationId: String): ApiResult<List<Message>> = error("unused")
    override suspend fun sendMessage(conversationId: String, content: String, imagePath: String?, productId: String?): ApiResult<Message> = error("unused")
    override suspend fun uploadImage(image: ImageUpload): ApiResult<UploadedImage> = error("unused")
    override suspend fun sendImage(conversationId: String, image: ImageUpload, caption: String): ApiResult<Message> = error("unused")
}

class FakeTrendsRepository : TrendsRepository {
    var result: ApiResult<List<Trend>> = ApiResult.Success(emptyList())
    val forceFlags = mutableListOf<Boolean>()
    override suspend fun getTrends(forceRefresh: Boolean): ApiResult<List<Trend>> { forceFlags += forceRefresh; return result }
}

fun profile(
    id: String = "u-other", isPublic: Boolean = true, following: Boolean = false, pending: Boolean = false, followers: Int = 10,
) = PublicProfile(
    id = id, name = "Laura García", avatar = null, fullBodyAvatar = null, bio = "", mood = null, gender = null, locationName = null,
    experiencePoints = 100, level = 2, isProfilePublic = isPublic, followersCount = followers, followingCount = 3,
    garmentCount = 4, lookCount = 2, isFollowing = following, hasPendingRequest = pending, pendingRequestId = if (pending) "req" else null,
    garmentsForSale = emptyList(), looks = emptyList(),
)

fun upload() = ImageUpload("a.jpg", "image/jpeg", ByteArray(4))
