package com.estilovivo.app.feature.wardrobe

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.estilovivo.app.app.CreateLook
import com.estilovivo.app.app.GarmentDetail
import com.estilovivo.app.app.SharedScope
import com.estilovivo.app.app.Washing

/** Destinos del módulo `wardrobe`: detalle de prenda, creador de looks y lavadora. */
fun NavGraphBuilder.wardrobeRoutes() {
    composable<GarmentDetail> { entry ->
        val route = entry.toRoute<GarmentDetail>()
        SharedScope { GarmentDetailScreen(route.id) }
    }
    composable<CreateLook> { entry ->
        val route = entry.toRoute<CreateLook>()
        SharedScope { CreateLookScreen(route.lookId) }
    }
    composable<Washing> { SharedScope { WashingScreen() } }
}
