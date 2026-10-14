package com.estilovivo.app.feature.suitcase

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.estilovivo.app.app.SharedScope
import com.estilovivo.app.app.Suitcase
import com.estilovivo.app.app.TripDetail
import com.estilovivo.app.app.Wishlist

/** Destinos del módulo `suitcase`: lista de viajes, detalle del viaje y wishlist. */
fun NavGraphBuilder.suitcaseRoutes() {
    composable<Suitcase> { SharedScope { SuitcaseScreen() } }
    composable<TripDetail> { entry ->
        val route = entry.toRoute<TripDetail>()
        SharedScope { TripDetailScreen(route.tripId) }
    }
    composable<Wishlist> { SharedScope { WishlistScreen() } }
}
