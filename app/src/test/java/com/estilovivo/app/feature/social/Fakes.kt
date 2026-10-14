package com.estilovivo.app.feature.social

import com.estilovivo.app.core.data.ApiError
import com.estilovivo.app.core.data.ApiErrorKind
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.ImageUpload
import com.estilovivo.app.core.data.model.Comment
import com.estilovivo.app.core.data.model.Conversation
import com.estilovivo.app.core.data.model.Favorite
import com.estilovivo.app.core.data.model.FollowRequest
import com.estilovivo.app.core.data.model.FollowResult
import com.estilovivo.app.core.data.model.Garment
import com.estilovivo.app.core.data.model.GarmentDraft
import com.estilovivo.app.core.data.model.LikeResult
import com.estilovivo.app.core.data.model.Message
import com.estilovivo.app.core.data.model.PublicProfile
import com.estilovivo.app.core.data.model.ShopItem
import com.estilovivo.app.core.data.model.TopUser
import com.estilovivo.app.core.data.model.UploadedImage
import com.estilovivo.app.core.data.model.UserSummary
import com.estilovivo.app.core.data.repo.ChatRepository
import com.estilovivo.app.core.data.repo.GarmentRepository
import com.estilovivo.app.core.data.repo.SocialRepository
import com.estilovivo.app.core.data.socket.SocketEvent
import com.estilovivo.app.feature.social.chat.ChatSocket
import com.estilovivo.app.feature.social.shop.ImageFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

fun failure(message: String = "Sin conexión", kind: ApiErrorKind = ApiErrorKind.Network) = ApiResult.Failure(ApiError(message, kind))

fun shopItem(
    id: String,
    title: String = "Prenda $id",
    price: Double = 20.0,
    size: String = "M",
    condition: String = "good",
    userId: String = "seller",
    category: String = "top",
) = ShopItem(
    id = id, user = "Vendedora Uno", userId = userId, avatar = "https://x/a.jpg", image = "https://x/$id.jpg",
    title = title, price = price, size = size, brand = "Zara", condition = condition,
    images = listOf("https://x/$id.jpg"), category = category,
)

fun message(
    id: String,
    at: String,
    content: String = "hola $id",
    sender: String = "other",
    conversation: String = "c1",
    imageUrl: String? = null,
) = Message(id, conversation, sender, content, at, imageUrl, sender = UserSummary(sender, sender))

fun garment(id: String, userId: String? = "seller", price: Double = 30.0) = Garment(
    id = id, imageUrl = "https://x/$id.jpg", name = "Chaqueta $id", type = "outerwear", color = "negro", season = "all",
    usageCount = 0, forSale = true, price = price, userId = userId, userName = "Vendedora Uno",
)

class FakeGarmentRepository : GarmentRepository {
    private val _garments = MutableStateFlow<List<Garment>>(emptyList())
    override val garments: StateFlow<List<Garment>> = _garments.asStateFlow()

    val shopCalls = mutableListOf<Pair<String?, String?>>()
    var shopHandler: suspend (String?, String?) -> ApiResult<List<ShopItem>> = { _, _ -> ApiResult.Success(emptyList()) }
    var toggleHandler: suspend (String) -> ApiResult<Boolean> = { ApiResult.Success(true) }
    val added = mutableListOf<Pair<GarmentDraft, ImageUpload?>>()
    var addResult: ApiResult<Garment>? = null

    override suspend fun getAll() = ApiResult.Success(_garments.value)
    override suspend fun add(draft: GarmentDraft, image: ImageUpload?): ApiResult<Garment> {
        added += draft to image
        return addResult ?: ApiResult.Success(garment("new"))
    }
    override suspend fun update(id: String, draft: GarmentDraft, image: ImageUpload?): ApiResult<Garment> = error("no usado")
    override suspend fun delete(id: String): ApiResult<Unit> = error("no usado")
    override suspend fun markWorn(id: String): ApiResult<Garment> = error("no usado")
    override suspend fun shop(search: String?, category: String?): ApiResult<List<ShopItem>> {
        shopCalls += search to category
        return shopHandler(search, category)
    }
    override suspend fun toggleFavorite(garmentId: String): ApiResult<Boolean> = toggleHandler(garmentId)
}

class FakeSocialRepository : SocialRepository {
    var favorites: ApiResult<List<Favorite>> = ApiResult.Success(emptyList())
    var lookToggle: ApiResult<Boolean> = ApiResult.Success(false)
    val toggledLooks = mutableListOf<String>()

    override suspend fun getFavorites() = favorites
    override suspend fun toggleLookFavorite(lookId: String): ApiResult<Boolean> {
        toggledLooks += lookId
        return lookToggle
    }
    override suspend fun toggleLike(lookId: String): ApiResult<LikeResult> = error("no usado")
    override suspend fun getComments(lookId: String): ApiResult<List<Comment>> = error("no usado")
    override suspend fun addComment(lookId: String, content: String, parentId: String?): ApiResult<Comment> = error("no usado")
    override suspend fun deleteComment(commentId: String): ApiResult<Unit> = error("no usado")
    override suspend fun toggleGarmentFavorite(garmentId: String): ApiResult<Boolean> = error("no usado")
    override suspend fun toggleFollow(userId: String): ApiResult<FollowResult> = error("no usado")
    override suspend fun getFollowRequests(): ApiResult<List<FollowRequest>> = error("no usado")
    override suspend fun approveFollowRequest(requestId: String): ApiResult<Unit> = error("no usado")
    override suspend fun rejectFollowRequest(requestId: String): ApiResult<Unit> = error("no usado")
    override suspend fun getUserProfile(userId: String): ApiResult<PublicProfile> = error("no usado")
    override suspend fun getTopUsers(forceRefresh: Boolean): ApiResult<List<TopUser>> = error("no usado")
}

class FakeChatRepository : ChatRepository {
    var conversations: ApiResult<List<Conversation>> = ApiResult.Success(emptyList())
    var messages: ApiResult<List<Message>> = ApiResult.Success(emptyList())
    var sendHandler: suspend (String, String) -> ApiResult<Message> = { conv, content ->
        ApiResult.Success(message("srv-${content.hashCode()}", "2026-05-12T10:00:00.000Z", content, sender = "me", conversation = conv))
    }
    var sendImageHandler: suspend (String) -> ApiResult<Message> = { conv ->
        ApiResult.Success(message("srv-img", "2026-05-12T10:00:00.000Z", "", sender = "me", conversation = conv, imageUrl = "https://x/i.jpg"))
    }
    var createResult: ApiResult<Conversation> = ApiResult.Success(Conversation(id = "conv-new"))
    val created = mutableListOf<List<String?>>()
    var getMessagesCalls = 0
    var getConversationsCalls = 0
    val sent = mutableListOf<String>()

    override suspend fun getConversations(): ApiResult<List<Conversation>> {
        getConversationsCalls++
        return conversations
    }
    override suspend fun createConversation(
        targetUserId: String, itemId: String?, itemTitle: String?, itemImage: String?, initialMessage: String?,
    ): ApiResult<Conversation> {
        created += listOf(targetUserId, itemId, itemTitle, itemImage, initialMessage)
        return createResult
    }
    override suspend fun getMessages(conversationId: String): ApiResult<List<Message>> {
        getMessagesCalls++
        return messages
    }
    override suspend fun sendMessage(conversationId: String, content: String, imagePath: String?, productId: String?): ApiResult<Message> {
        sent += content
        return sendHandler(conversationId, content)
    }
    override suspend fun uploadImage(image: ImageUpload): ApiResult<UploadedImage> = error("no usado")
    override suspend fun sendImage(conversationId: String, image: ImageUpload, caption: String): ApiResult<Message> =
        sendImageHandler(conversationId)
}

class FakeChatSocket : ChatSocket {
    val flow = MutableSharedFlow<SocketEvent>(extraBufferCapacity = 64)
    override val events = flow
    private val _connected = MutableStateFlow(true)
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()
    val joined = mutableListOf<String>()
    val left = mutableListOf<String>()
    val typingCalls = mutableListOf<Pair<String, Boolean>>()

    fun setConnected(up: Boolean) { _connected.value = up }
    override fun joinRoom(conversationId: String) { joined += conversationId }
    override fun leaveRoom(conversationId: String) { left += conversationId }
    override fun typing(conversationId: String, isTyping: Boolean) { typingCalls += conversationId to isTyping }
}

class FakeImageFetcher(var result: ImageUpload? = ImageUpload("g.jpg", "image/jpeg", byteArrayOf(1, 2, 3))) : ImageFetcher {
    val urls = mutableListOf<String>()
    override suspend fun fetch(url: String): ImageUpload? {
        urls += url
        return result
    }
}
