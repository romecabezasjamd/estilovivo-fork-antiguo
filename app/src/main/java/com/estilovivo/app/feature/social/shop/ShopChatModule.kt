package com.estilovivo.app.feature.social.shop

import com.estilovivo.app.core.data.session.SessionManager
import com.estilovivo.app.feature.social.chat.ChatLauncher
import com.estilovivo.app.feature.social.chat.ChatOutbox
import com.estilovivo.app.feature.social.chat.ChatSocket
import com.estilovivo.app.feature.social.chat.ChatViewModel
import com.estilovivo.app.feature.social.chat.ConversationsViewModel
import com.estilovivo.app.feature.social.chat.SocketManagerChatSocket
import okhttp3.OkHttpClient
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

/** Tienda (segunda mano), favoritos y chat. */
val shopChatModule = module {
    single { ShopCatalog(get(), get()) }
    single { ChatLauncher(get()) }
    single { ChatOutbox() }
    single<ChatSocket> { SocketManagerChatSocket(get()) }
    // Las fotos de las prendas son públicas: cliente propio, sin credenciales
    single<ImageFetcher> {
        OkHttpImageFetcher(
            OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build(),
        )
    }

    viewModel { ShopViewModel(get(), get<SessionManager>().user) }
    viewModel { (productId: String) ->
        ProductDetailViewModel(productId, get(), get()) { get<SessionManager>().userId }
    }
    viewModel { FavoritesViewModel(get(), get(), get(), get(), get()) { get<SessionManager>().userId } }
    viewModel { ConversationsViewModel(get(), get(), get()) { get<SessionManager>().userId } }
    viewModel { (conversationId: String) ->
        ChatViewModel(conversationId, get(), get(), get(), meId = { get<SessionManager>().userId })
    }
}
