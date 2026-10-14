package com.estilovivo.app.feature.tryon

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.estilovivo.app.app.SharedScope
import com.estilovivo.app.app.TryOn

/** Destinos del módulo `tryon`: el probador virtual a pantalla completa. */
fun NavGraphBuilder.tryOnRoutes() {
    composable<TryOn> { entry ->
        val route = entry.toRoute<TryOn>()
        SharedScope { TryOnScreen(route.garmentId) }
    }
}
