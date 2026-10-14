package com.estilovivo.app.feature.auth

import com.estilovivo.app.core.data.model.User

enum class PostAuthDestination { Onboarding, Main }

/**
 * Tras iniciar sesión: onboarding sólo si no se completó antes en este dispositivo y la cuenta es "vacía"
 * (sin preferencias de estilo ni prendas/looks). Una cuenta con datos que entra en un móvil nuevo va directa.
 */
fun decidePostAuth(user: User?, onboardingDone: Boolean): PostAuthDestination {
    if (onboardingDone || user == null) return PostAuthDestination.Main
    val hasStyle = user.styleColors.isNotEmpty() || user.styleStyles.isNotEmpty() ||
        user.styleOccasions.isNotEmpty() || user.styleFabrics.isNotEmpty()
    val hasContent = user.garmentCount > 0 || user.lookCount > 0
    return if (hasStyle || hasContent) PostAuthDestination.Main else PostAuthDestination.Onboarding
}
