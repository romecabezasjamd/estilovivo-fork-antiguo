package com.estilovivo.app.feature.notifications

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.estilovivo.app.app.Notifications
import com.estilovivo.app.app.SharedScope

/** Destinos del módulo `notifications`. */
fun NavGraphBuilder.notificationsRoutes() {
    composable<Notifications> { SharedScope { NotificationsScreen() } }
}
