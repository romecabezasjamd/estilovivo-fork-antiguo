package com.estilovivo.app.feature.auth

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.estilovivo.app.app.Auth
import com.estilovivo.app.app.Onboarding
import com.estilovivo.app.app.SharedScope

/** Registra `Auth` y `Onboarding` (Welcome está en EvApp). */
fun NavGraphBuilder.authRoutes() {
    composable<Auth> { SharedScope { AuthRoute() } }
    composable<Onboarding>(
        enterTransition = { fadeIn(tween(420)) + scaleIn(tween(420), initialScale = 0.96f) },
        exitTransition = { fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 1.04f) },
    ) { SharedScope { OnboardingRoute() } }
}
