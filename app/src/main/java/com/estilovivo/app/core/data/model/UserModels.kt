package com.estilovivo.app.core.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/** Usuario autenticado (perfil propio). Las imágenes ya son URLs absolutas. */
@Serializable
@Immutable
data class User(
    val id: String,
    val email: String? = null,
    val name: String = "",
    val bio: String = "",
    val avatar: String? = null,
    val fullBodyAvatar: String? = null,
    val mood: String? = null,
    /** "male" | "female" | "other" */
    val gender: String? = null,
    /** ISO-8601 (`2000-01-31T00:00:00.000Z`) o null. */
    val birthDate: String? = null,
    val cycleTracking: Boolean = false,
    val musicSync: Boolean = false,
    val emailNotifications: Boolean = true,
    val emailChat: Boolean = true,
    val emailFollows: Boolean = true,
    val emailWashing: Boolean = true,
    val emailChallenges: Boolean = true,
    val isVerified: Boolean = true,
    val experiencePoints: Int = 0,
    val level: Int = 1,
    val themePreset: String? = null,
    val customColor: String? = null,
    val isProfilePublic: Boolean = true,
    val locationName: String? = null,
    /** "off" | "on" | "auto" (ajuste del servidor; la app usa [com.estilovivo.app.core.data.session.AppPrefs.themeMode]). */
    val darkModeSetting: String? = null,
    val fontSize: String? = null,
    val highContrast: Boolean = false,
    val language: String? = null,
    val dialect: String? = null,
    val styleColors: List<String> = emptyList(),
    val styleStyles: List<String> = emptyList(),
    val styleOccasions: List<String> = emptyList(),
    val styleFabrics: List<String> = emptyList(),
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val garmentCount: Int = 0,
    val lookCount: Int = 0,
)

/** Resumen mínimo de un usuario (id, nombre, avatar). Equivale a `ChatParticipant` de la web. */
@Serializable
@Immutable
data class UserSummary(val id: String, val name: String, val avatar: String? = null)

/** Entrada del ranking (`/users/top`). */
@Immutable
data class TopUser(val id: String, val name: String, val avatar: String?, val experiencePoints: Int, val level: Int)

/** Perfil de otra persona (`/users/:id`). */
@Immutable
data class PublicProfile(
    val id: String,
    val name: String,
    val avatar: String?,
    val fullBodyAvatar: String?,
    val bio: String,
    val mood: String?,
    val gender: String?,
    val locationName: String?,
    val experiencePoints: Int,
    val level: Int,
    val isProfilePublic: Boolean,
    val followersCount: Int,
    val followingCount: Int,
    val garmentCount: Int,
    val lookCount: Int,
    val isFollowing: Boolean,
    val hasPendingRequest: Boolean,
    val pendingRequestId: String?,
    /** Prendas en venta (máx. 6). */
    val garmentsForSale: List<Garment>,
    /** Looks públicos (vacío si el perfil es privado y no le sigues). */
    val looks: List<Look>,
)

/** Solicitud de seguimiento recibida (perfil privado). */
@Immutable
data class FollowRequest(
    val id: String,
    val requester: UserSummary,
    val status: String,
    val createdAt: String,
)

/** Resultado de seguir/dejar de seguir. [requesting] = true cuando se ha creado una solicitud (perfil privado). */
@Immutable
data class FollowResult(val following: Boolean, val requesting: Boolean)

@Immutable
data class LikeResult(val liked: Boolean, val likesCount: Int)

/** Cambios de perfil (null = no tocar). Con imágenes se envía como multipart. */
@Immutable
data class ProfileUpdate(
    val name: String? = null,
    val bio: String? = null,
    val mood: String? = null,
    val gender: String? = null,
    val birthDate: String? = null,
    val avatar: com.estilovivo.app.core.data.ImageUpload? = null,
    val fullBodyAvatar: com.estilovivo.app.core.data.ImageUpload? = null,
)

/** Preferencias/ajustes de cuenta (null = no tocar). Endpoint `/auth/preferences`. */
@Immutable
data class PreferencesUpdate(
    val cycleTracking: Boolean? = null,
    val musicSync: Boolean? = null,
    val emailNotifications: Boolean? = null,
    val emailChat: Boolean? = null,
    val emailFollows: Boolean? = null,
    val emailWashing: Boolean? = null,
    val emailChallenges: Boolean? = null,
    val isProfilePublic: Boolean? = null,
    val highContrast: Boolean? = null,
    val themePreset: String? = null,
    val customColor: String? = null,
    val locationName: String? = null,
    val darkModeSetting: String? = null,
    val fontSize: String? = null,
    val language: String? = null,
    val dialect: String? = null,
    val styleColors: List<String>? = null,
    val styleStyles: List<String>? = null,
    val styleOccasions: List<String>? = null,
    val styleFabrics: List<String>? = null,
)

/** Resultado de login. */
@Immutable
data class AuthOutcome(val user: User, val requiresVerification: Boolean)

/** Resultado de registro. [loggedIn] = false cuando hay que verificar el email antes de entrar. */
@Immutable
data class RegisterOutcome(val user: User?, val loggedIn: Boolean, val requiresVerification: Boolean)
