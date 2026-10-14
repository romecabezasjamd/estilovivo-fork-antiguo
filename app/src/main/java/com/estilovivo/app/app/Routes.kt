package com.estilovivo.app.app

import kotlinx.serialization.Serializable

/**
 * Todas las rutas de la app (Navigation Compose con tipos seguros).
 * Las pestañas principales (Inicio, Armario, Social, Perfil) viven dentro de [Main];
 * el resto se apila por encima con transiciones de deslizamiento / elemento compartido.
 */
@Serializable data object Welcome
@Serializable data object Auth
@Serializable data object Onboarding
@Serializable data object Main

@Serializable data object Premium
@Serializable data object Privacy
@Serializable data object Notifications

// Armario
@Serializable data class GarmentDetail(val id: String)
@Serializable data class CreateLook(val lookId: String? = null)
@Serializable data object Washing

// Planificador, viajes, wishlist
@Serializable data class Planner(val date: String? = null)
@Serializable data object Suitcase
@Serializable data class TripDetail(val tripId: String)
@Serializable data object Wishlist

// Probador virtual
@Serializable data class TryOn(val garmentId: String? = null)

// Comunidad
@Serializable data class ProductDetail(val id: String)
@Serializable data class UserProfile(val userId: String)
@Serializable data class Chat(val conversationId: String)
@Serializable data class StoryViewer(val storyId: String)
@Serializable data object ChallengeHistory


// Perfil (ajustes)
@Serializable data object EditProfile
@Serializable data object ChangePassword
@Serializable data object FollowRequests

/** Pestañas de la barra inferior. */
enum class MainTab(val id: String) { Home("home"), Wardrobe("wardrobe"), Social("social"), Profile("profile") }
