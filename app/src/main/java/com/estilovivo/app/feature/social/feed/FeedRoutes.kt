package com.estilovivo.app.feature.social.feed

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.estilovivo.app.app.ChallengeHistory
import com.estilovivo.app.app.SharedScope
import com.estilovivo.app.app.StoryViewer
import com.estilovivo.app.app.UserProfile

/** Perfil de otra usuaria, visor de historias e historial de retos. */
fun NavGraphBuilder.feedRoutes() {
    composable<UserProfile> { entry ->
        val route = entry.toRoute<UserProfile>()
        SharedScope { UserProfileScreen(userId = route.userId) }
    }
    // El visor entra con fundido + zoom suave (no desliza como el resto de pantallas)
    composable<StoryViewer>(
        enterTransition = { fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.92f) },
        popEnterTransition = { fadeIn(tween(160)) },
        exitTransition = { fadeOut(tween(160)) },
        popExitTransition = { fadeOut(tween(200)) + scaleOut(tween(220), targetScale = 0.94f) },
    ) { entry ->
        val route = entry.toRoute<StoryViewer>()
        SharedScope { StoryViewerScreen(storyId = route.storyId) }
    }
    composable<ChallengeHistory> {
        SharedScope { ChallengeHistoryScreen() }
    }
}
