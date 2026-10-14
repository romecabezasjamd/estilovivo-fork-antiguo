package com.estilovivo.app.feature.profile

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.estilovivo.app.app.ChangePassword
import com.estilovivo.app.app.EditProfile
import com.estilovivo.app.app.FollowRequests
import com.estilovivo.app.app.Premium
import com.estilovivo.app.app.Privacy
import com.estilovivo.app.app.SharedScope

/** Destinos del módulo `profile`. */
fun NavGraphBuilder.profileRoutes() {
    composable<Premium> { SharedScope { PremiumScreen() } }
    composable<Privacy> { SharedScope { PrivacyScreen() } }
    composable<EditProfile> { SharedScope { EditProfileScreen() } }
    composable<ChangePassword> { SharedScope { ChangePasswordScreen() } }
    composable<FollowRequests> { SharedScope { FollowRequestsScreen() } }
}
