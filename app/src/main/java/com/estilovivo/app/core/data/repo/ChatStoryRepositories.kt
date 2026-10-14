package com.estilovivo.app.core.data.repo

import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.ImageUpload
import com.estilovivo.app.core.data.model.Conversation
import com.estilovivo.app.core.data.model.Message
import com.estilovivo.app.core.data.model.Story
import com.estilovivo.app.core.data.model.StoryReactionResult
import com.estilovivo.app.core.data.model.UploadedImage
import com.estilovivo.app.core.data.net.ApiService
import com.estilovivo.app.core.data.net.ConversationRequest
import com.estilovivo.app.core.data.net.EmojiRequest
import com.estilovivo.app.core.data.net.Mappers
import com.estilovivo.app.core.data.net.SendMessageRequest
import com.estilovivo.app.core.data.session.SessionManager

/** Chat 1 a 1 (compraventa y social). Los mensajes entrantes llegan en tiempo real por `SocketManager`. */
interface ChatRepository {
    /** Conversaciones del usuario, la más reciente primero, con `otherUser` y `lastMessage`. */
    suspend fun getConversations(): ApiResult<List<Conversation>>

    /**
     * Abre (o recupera) la conversación con [targetUserId]. Con [itemId] queda ligada a una prenda de la tienda.
     * [initialMessage] sólo se usa si la conversación se crea nueva.
     */
    suspend fun createConversation(
        targetUserId: String,
        itemId: String? = null,
        itemTitle: String? = null,
        itemImage: String? = null,
        initialMessage: String? = null,
    ): ApiResult<Conversation>

    /** Últimos 500 mensajes, del más antiguo al más nuevo. */
    suspend fun getMessages(conversationId: String): ApiResult<List<Message>>

    /** [imagePath] = `UploadedImage.path` devuelto por [uploadImage]. */
    suspend fun sendMessage(
        conversationId: String,
        content: String,
        imagePath: String? = null,
        productId: String? = null,
    ): ApiResult<Message>

    suspend fun uploadImage(image: ImageUpload): ApiResult<UploadedImage>

    /** Sube la imagen y la envía en un solo paso. */
    suspend fun sendImage(conversationId: String, image: ImageUpload, caption: String = ""): ApiResult<Message>
}

internal class ChatRepositoryImpl(
    private val api: ApiService,
    private val mappers: Mappers,
    private val session: SessionManager,
) : ChatRepository {
    override suspend fun getConversations() = apiCall {
        val me = session.userId
        api.conversations().map { mappers.conversation(it, me) }
    }

    override suspend fun createConversation(targetUserId: String, itemId: String?, itemTitle: String?, itemImage: String?, initialMessage: String?) =
        apiCall {
            val dto = api.createConversation(
                ConversationRequest(
                    targetUserId, itemId, itemTitle,
                    itemImage = itemImage?.let { mappers.assets.toRelative(it) },
                    initialMessage = initialMessage?.takeIf { it.isNotBlank() },
                ),
            )
            mappers.conversation(dto, session.userId)
        }

    override suspend fun getMessages(conversationId: String) = apiCall { api.messages(conversationId).map(mappers::message) }

    override suspend fun sendMessage(conversationId: String, content: String, imagePath: String?, productId: String?) = apiCall {
        mappers.message(api.sendMessage(conversationId, SendMessageRequest(content.takeIf { it.isNotEmpty() }, imagePath, productId)))
    }

    override suspend fun uploadImage(image: ImageUpload) = apiCall {
        val path = api.uploadChatImage(image.toPart("image")).imageUrl.ifEmpty { missing("la imagen") }
        UploadedImage(path, mappers.assets.resolve(path).orEmpty())
    }

    override suspend fun sendImage(conversationId: String, image: ImageUpload, caption: String): ApiResult<Message> = apiCall {
        val path = api.uploadChatImage(image.toPart("image")).imageUrl.ifEmpty { missing("la imagen") }
        mappers.message(api.sendMessage(conversationId, SendMessageRequest(caption.takeIf { it.isNotEmpty() }, path, null)))
    }
}

/** Historias de 24 h. */
interface StoryRepository {
    /** Historias activas visibles (propias, de perfiles públicos y de gente a la que sigues). */
    suspend fun getStories(): ApiResult<List<Story>>

    /**
     * Publica una historia. `type = "text"` requiere [text]; `type = "image"` requiere [image] **o** [existingImageUrl]
     * (una imagen ya alojada en el servidor, p. ej. la foto de una prenda).
     */
    suspend fun create(
        type: String,
        text: String? = null,
        image: ImageUpload? = null,
        existingImageUrl: String? = null,
    ): ApiResult<Story>

    /** Registra una visualización (las propias no cuentan). */
    suspend fun markViewed(storyId: String): ApiResult<Unit>
    suspend fun delete(storyId: String): ApiResult<Unit>

    /** Reacciona con un emoji (máx. 4 caracteres): envía un mensaje al autor y devuelve la conversación. */
    suspend fun react(storyId: String, emoji: String): ApiResult<StoryReactionResult>
}

internal class StoryRepositoryImpl(
    private val api: ApiService,
    private val mappers: Mappers,
    private val session: SessionManager,
) : StoryRepository {
    override suspend fun getStories() = apiCall {
        val me = session.userId
        api.stories().map { mappers.story(it, me) }
    }

    override suspend fun create(type: String, text: String?, image: ImageUpload?, existingImageUrl: String?) = apiCall {
        val dto = api.createStory(
            fields = textParts(
                "type" to type,
                "text" to text?.takeIf { it.isNotBlank() },
                "imageUrl" to if (image == null) existingImageUrl?.let { mappers.assets.toRelative(it) } else null,
            ),
            image = image?.toPart("image"),
        )
        mappers.story(dto, session.userId)
    }

    override suspend fun markViewed(storyId: String) = apiCall { api.viewStory(storyId); Unit }

    override suspend fun delete(storyId: String) = apiCall { api.deleteStory(storyId); Unit }

    override suspend fun react(storyId: String, emoji: String) = apiCall {
        val res = api.reactToStory(storyId, EmojiRequest(emoji))
        StoryReactionResult(res.message?.let(mappers::message), res.conversationId)
    }
}
