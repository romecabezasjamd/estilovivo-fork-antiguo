package com.estilovivo.app.core.data.net

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/*
 * DTOs tal y como los devuelve el servidor (ver web/server/src/index.ts). Todos los campos tienen valor por
 * defecto/nulos: el servidor es laxo (campos opcionales, relaciones que a veces no se incluyen).
 * Nunca salen del módulo: los repositorios los convierten a modelos de dominio con [Mappers].
 */

@Serializable
internal data class OkDto(val success: Boolean? = null)

@Serializable
internal data class PageDto<T>(
    val items: List<T> = emptyList(),
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
)

@Serializable
internal data class UserLiteDto(
    val id: String? = null,
    val name: String? = null,
    val avatar: String? = null,
)

@Serializable
internal data class UserDto(
    val id: String? = null,
    val email: String? = null,
    val name: String = "",
    val avatar: String? = null,
    val fullBodyAvatar: String? = null,
    val bio: String? = null,
    val mood: String? = null,
    val gender: String? = null,
    val birthDate: String? = null,
    val cycleTracking: Boolean? = null,
    val musicSync: Boolean? = null,
    val emailNotifications: Boolean? = null,
    val emailChat: Boolean? = null,
    val emailFollows: Boolean? = null,
    val emailWashing: Boolean? = null,
    val emailChallenges: Boolean? = null,
    val isVerified: Boolean? = null,
    val experiencePoints: Int? = null,
    val level: Int? = null,
    val themePreset: String? = null,
    val customColor: String? = null,
    val isProfilePublic: Boolean? = null,
    val locationName: String? = null,
    val darkModeSetting: String? = null,
    val fontSize: String? = null,
    val highContrast: Boolean? = null,
    val language: String? = null,
    val dialect: String? = null,
    val styleColors: String? = null,
    val styleStyles: String? = null,
    val styleOccasions: String? = null,
    val styleFabrics: String? = null,
    val followersCount: Int? = null,
    val followingCount: Int? = null,
    val garmentCount: Int? = null,
    val lookCount: Int? = null,
)

@Serializable
internal data class AuthResponseDto(
    val user: UserDto? = null,
    val token: String? = null,
    val refreshToken: String? = null,
    val requiresVerification: Boolean = false,
    val message: String? = null,
    val success: Boolean? = null,
)

@Serializable internal data class LoginRequest(val email: String, val password: String)

@Serializable
internal data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String,
    val gender: String? = null,
    val birthDate: String? = null,
)

@Serializable internal data class RefreshRequest(val refreshToken: String)
@Serializable internal data class EmailRequest(val email: String)
@Serializable internal data class TokenRequest(val token: String)
@Serializable internal data class ResetPasswordRequest(val token: String, val newPassword: String)
@Serializable internal data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

// ───────────── Prendas / looks ─────────────

@Serializable internal data class ImageDto(val url: String? = null)

@Serializable
internal data class ProductDto(
    val id: String = "",
    val name: String? = null,
    val description: String? = null,
    val price: Double? = null,
    val brand: String? = null,
    val category: String? = null,
    val size: String? = null,
    val color: String? = null,
    val condition: String? = null,
    val season: String? = null,
    val usageCount: Int? = null,
    val lastWorn: String? = null,
    val forSale: Boolean? = null,
    val isWashing: Boolean? = null,
    val createdAt: String? = null,
    val userId: String? = null,
    val user: UserLiteDto? = null,
    val images: List<ImageDto>? = null,
)

@Serializable internal data class CountDto(val likes: Int? = null, val comments: Int? = null)

@Serializable
internal data class LookDto(
    val id: String = "",
    val title: String? = null,
    val name: String? = null,
    val isPublic: Boolean? = null,
    val mood: String? = null,
    /** String JSON (`"[\"a\",\"b\"]"`) o array. */
    val tags: JsonElement? = null,
    val createdAt: String? = null,
    val userId: String? = null,
    val user: UserLiteDto? = null,
    val products: List<ProductDto>? = null,
    val garments: List<ProductDto>? = null,
    val images: List<ImageDto>? = null,
    val likesCount: Int? = null,
    val commentsCount: Int? = null,
    @kotlinx.serialization.SerialName("_count") val count: CountDto? = null,
    val isLiked: Boolean? = null,
    val isFavorited: Boolean? = null,
)

@Serializable
internal data class PlannerEntryDto(
    val date: String = "",
    val lookId: String? = null,
    val look: LookDto? = null,
    val eventNote: String? = null,
)

@Serializable internal data class PlannerRequest(val date: String, val lookId: String? = null, val eventNote: String = "")

@Serializable
internal data class TripItemDto(
    val id: String = "",
    val label: String = "",
    val checked: Boolean? = null,
    val isEssential: Boolean? = null,
)

@Serializable
internal data class TripDto(
    val id: String = "",
    val destination: String = "",
    val dateStart: String = "",
    val dateEnd: String = "",
    val items: List<TripItemDto>? = null,
    val garments: List<ProductDto?>? = null,
)

@Serializable
internal data class TripItemRequest(val label: String, val checked: Boolean = false, val isEssential: Boolean = false)

@Serializable
internal data class TripRequest(
    val destination: String,
    val dateStart: String,
    val dateEnd: String,
    val items: List<TripItemRequest>? = null,
    val garmentIds: List<String>? = null,
)

// ───────────── Social ─────────────

@Serializable
internal data class CommentDto(
    val id: String = "",
    val content: String = "",
    val userId: String? = null,
    val user: UserLiteDto? = null,
    val parentId: String? = null,
    val replies: List<CommentDto>? = null,
    val createdAt: String? = null,
)

@Serializable internal data class LikeRequest(val lookId: String)
@Serializable internal data class LikeResponseDto(val liked: Boolean = false, val likesCount: Int = 0)
@Serializable internal data class FavoriteRequest(val lookId: String? = null, val productId: String? = null)
@Serializable internal data class FavoriteResponseDto(val favorited: Boolean = false)
@Serializable internal data class FollowRequestBody(val targetUserId: String)
@Serializable internal data class FollowResponseDto(val following: Boolean? = null, val requesting: Boolean? = null)
@Serializable internal data class CommentRequest(val lookId: String, val content: String, val parentId: String? = null)

@Serializable
internal data class FavoriteDto(
    val id: String = "",
    val lookId: String? = null,
    val productId: String? = null,
    val createdAt: String? = null,
    val look: LookDto? = null,
    val product: ProductDto? = null,
)

@Serializable
internal data class FollowRequestDto(
    val id: String = "",
    val status: String? = null,
    val createdAt: String? = null,
    val requester: UserLiteDto? = null,
)

@Serializable
internal data class TopUserDto(
    val id: String = "",
    val name: String = "",
    val avatar: String? = null,
    val experiencePoints: Int? = null,
    val level: Int? = null,
)

@Serializable
internal data class PublicProfileDto(
    val id: String = "",
    val name: String = "",
    val avatar: String? = null,
    val fullBodyAvatar: String? = null,
    val bio: String? = null,
    val mood: String? = null,
    val gender: String? = null,
    val locationName: String? = null,
    val experiencePoints: Int? = null,
    val level: Int? = null,
    val isProfilePublic: Boolean? = null,
    val followersCount: Int? = null,
    val followingCount: Int? = null,
    val garmentCount: Int? = null,
    val lookCount: Int? = null,
    val isFollowing: Boolean? = null,
    val hasPendingRequest: Boolean? = null,
    val pendingRequestId: String? = null,
    val products: List<ProductDto>? = null,
    val looks: List<LookDto>? = null,
)

@Serializable
internal data class StatsDto(
    val garmentCount: Int? = null,
    val lookCount: Int? = null,
    val salesCount: Int? = null,
    val tripsCount: Int? = null,
    val totalUsage: Int? = null,
    val mostWorn: ProductDto? = null,
    val leastWornCount: Int? = null,
    val leastWorn: List<ProductDto>? = null,
)

// ───────────── Historias / chat / notificaciones ─────────────

@Serializable
internal data class StoryDto(
    val id: String = "",
    val userId: String = "",
    val user: UserLiteDto? = null,
    val type: String = "text",
    val text: String? = null,
    val imageUrl: String? = null,
    val views: Int? = null,
    val createdAt: String? = null,
    val expiresAt: String? = null,
)

@Serializable internal data class EmojiRequest(val emoji: String)

@Serializable
internal data class MessageDto(
    val id: String = "",
    val conversationId: String = "",
    val senderId: String = "",
    val content: String? = null,
    val createdAt: String? = null,
    val imageUrl: String? = null,
    val productId: String? = null,
    val sender: UserLiteDto? = null,
)

@Serializable
internal data class ReactionResponseDto(val message: MessageDto? = null, val conversationId: String? = null)

@Serializable
internal data class ParticipantDto(val userId: String? = null, val user: UserLiteDto? = null)

@Serializable
internal data class ConversationDto(
    val id: String = "",
    val itemId: String? = null,
    val itemTitle: String? = null,
    val itemImage: String? = null,
    val itemOwnerId: String? = null,
    val updatedAt: String? = null,
    val createdAt: String? = null,
    val participants: List<ParticipantDto>? = null,
    val messages: List<MessageDto>? = null,
    val otherUser: UserLiteDto? = null,
)

@Serializable
internal data class ConversationRequest(
    val targetUserId: String,
    val itemId: String? = null,
    val itemTitle: String? = null,
    val itemImage: String? = null,
    val initialMessage: String? = null,
)

@Serializable
internal data class SendMessageRequest(val content: String? = null, val imageUrl: String? = null, val productId: String? = null)

@Serializable internal data class UploadResponseDto(val imageUrl: String = "")

@Serializable
internal data class NotificationDto(
    val id: String = "",
    val type: String = "",
    val content: String? = null,
    val relatedId: String? = null,
    val isRead: Boolean? = null,
    val createdAt: String? = null,
)

// ───────────── Retos y gamificación ─────────────

@Serializable
internal data class ChallengeDto(
    val id: String = "",
    val title: String = "",
    val description: String? = null,
    val reward: Int? = null,
    val tag: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val active: Boolean? = null,
    val submissions: List<SubmissionDto>? = null,
)

@Serializable
internal data class SubmissionDto(
    val id: String = "",
    val userId: String? = null,
    val challengeId: String? = null,
    val imageUrl: String? = null,
    val description: String? = null,
    val verified: Boolean? = null,
    val awarded: Boolean? = null,
    val createdAt: String? = null,
    val challenge: ChallengeDto? = null,
    // sólo en la respuesta de /challenges/submit
    val experiencePoints: Int? = null,
    val level: Int? = null,
    val leveledUp: Boolean? = null,
    val validationMessage: String? = null,
)

@Serializable
internal data class ChallengeHistoryDto(val challenges: List<ChallengeDto> = emptyList(), val totalXp: Int = 0)

@Serializable
internal data class StreakDto(val loginCount: Int = 0, val lastDate: String? = null)

@Serializable
internal data class UserAchievementDto(
    val id: String = "",
    val achievementKey: String = "",
    val title: String = "",
    val description: String = "",
    val icon: String = "",
    val xpReward: Int = 0,
    val unlockedAt: String = "",
)

@Serializable
internal data class UserBadgeDto(
    val id: String = "",
    val badgeKey: String = "",
    val title: String = "",
    val description: String = "",
    val icon: String = "",
    val unlockedAt: String = "",
)

@Serializable
internal data class ProgressDto(
    val experiencePoints: Int = 0,
    val level: Int = 1,
    val xpCurrent: Int = 0,
    val xpNeeded: Int = 1,
    val xpPercentage: Int = 0,
    val streak: StreakDto? = null,
    val achievements: List<UserAchievementDto>? = null,
    val badges: List<UserBadgeDto>? = null,
    val challengeCount: Int = 0,
)

/** Elemento del catálogo de logros/insignias (`category` sólo en insignias, `xpReward` sólo en logros). */
@Serializable
internal data class CatalogItemDto(
    val key: String = "",
    val title: String = "",
    val description: String = "",
    val icon: String = "",
    val xpReward: Int = 0,
    val category: String = "",
    val unlocked: Boolean = false,
    val unlockedAt: String? = null,
)

@Serializable internal data class CheckAchievementsDto(val newlyUnlocked: List<CatalogItemDto> = emptyList())

@Serializable
internal data class DailyLoginDto(
    val streakCount: Int = 0,
    val xpAwarded: Int = 0,
    val newBadges: List<CatalogItemDto> = emptyList(),
)

// ───────────── Varios ─────────────

@Serializable
internal data class TrendDto(
    val id: String = "",
    val title: String = "",
    val category: String? = null,
    val description: String? = null,
    val image: String? = null,
    val tags: List<String>? = null,
    val source: String? = null,
    val link: String? = null,
)

@Serializable
internal data class TryonPresetDto(
    val id: String = "",
    val name: String = "",
    val thumbnail: String? = null,
    val layers: List<com.estilovivo.app.core.data.model.TryonLayer>? = null,
    val rating: Int? = null,
    val occasion: String? = null,
)

@Serializable
internal data class TryonPresetRequest(
    val name: String? = null,
    val thumbnail: String? = null,
    val layers: List<com.estilovivo.app.core.data.model.TryonLayer>? = null,
    val rating: Int? = null,
    val occasion: String? = null,
)
