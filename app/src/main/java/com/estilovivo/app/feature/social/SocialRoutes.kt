package com.estilovivo.app.feature.social

import androidx.navigation.NavGraphBuilder
import com.estilovivo.app.feature.social.feed.feedRoutes
import com.estilovivo.app.feature.social.shop.shopChatRoutes

/** Agrega las rutas de Social. Cada sub-módulo registra las suyas en su propio fichero. */
fun NavGraphBuilder.socialRoutes() {
    feedRoutes()
    shopChatRoutes()
}
