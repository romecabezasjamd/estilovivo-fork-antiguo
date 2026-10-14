package com.estilovivo.app.app.di

import com.estilovivo.app.core.data.dataModule
import com.estilovivo.app.core.util.utilModule
import com.estilovivo.app.feature.auth.authModule
import com.estilovivo.app.feature.home.homeModule
import com.estilovivo.app.feature.notifications.notificationsModule
import com.estilovivo.app.feature.planner.plannerModule
import com.estilovivo.app.feature.profile.profileModule
import com.estilovivo.app.feature.social.shop.shopChatModule
import com.estilovivo.app.feature.social.socialModule
import com.estilovivo.app.feature.suitcase.suitcaseModule
import com.estilovivo.app.feature.tryon.tryonModule
import com.estilovivo.app.feature.wardrobe.wardrobeModule
import com.estilovivo.app.feature.social.feed.feedModule

/** Todos los módulos Koin de la app. Cada módulo vive en su paquete (sin tocar este fichero). */
val appModules = listOf(
    dataModule, utilModule,
    authModule, homeModule, wardrobeModule, plannerModule, suitcaseModule,
    tryonModule, socialModule, profileModule, notificationsModule,
    shopChatModule, feedModule,
)
