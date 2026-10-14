package com.estilovivo.app.core.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class Comment(
    val id: String,
    val content: String,
    val userId: String,
    val userName: String,
    val userAvatar: String? = null,
    val parentId: String? = null,
    val replies: List<Comment> = emptyList(),
    val createdAt: String = "",
)

@Immutable
data class Message(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val content: String,
    val createdAt: String,
    val imageUrl: String? = null,
    val productId: String? = null,
    val sender: UserSummary? = null,
)

@Immutable
data class LastMessage(val id: String, val content: String, val createdAt: String, val sender: UserSummary?)

/** Conversación de chat. [otherUser] ya viene resuelto (el otro participante). */
@Immutable
data class Conversation(
    val id: String,
    val itemId: String? = null,
    val itemTitle: String? = null,
    val itemImage: String? = null,
    val itemOwnerId: String? = null,
    val updatedAt: String = "",
    val lastMessage: LastMessage? = null,
    val participants: List<UserSummary> = emptyList(),
    val otherUser: UserSummary? = null,
)

/** Imagen subida al chat. Para enviarla en un mensaje usa [path] (ruta relativa); para mostrarla, [url]. */
@Immutable
data class UploadedImage(val path: String, val url: String)

/** Historia (24 h). */
@Immutable
data class Story(
    val id: String,
    val userId: String,
    val userName: String,
    val userAvatar: String?,
    /** "image" | "text" */
    val type: String,
    val text: String?,
    val imageUrl: String?,
    val views: Int,
    val createdAt: String,
    val expiresAt: String,
    val isOwn: Boolean,
)

/** Respuesta a reaccionar a una historia: se abre/continúa un chat con su autor. */
@Immutable
data class StoryReactionResult(val message: Message?, val conversationId: String?)

object NotificationTypes {
    const val CHAT = "CHAT"
    const val STORY_REACTION = "STORY_REACTION"
    const val LIKE = "LIKE"
    const val COMMENT = "COMMENT"
    const val FOLLOW = "FOLLOW"
    const val WASHING = "WASHING"
}

@Immutable
data class AppNotification(
    val id: String,
    /** Ver [NotificationTypes]. */
    val type: String,
    val content: String,
    val relatedId: String?,
    val isRead: Boolean,
    val createdAt: String,
)

// ───────────── Retos ─────────────

@Immutable
data class Challenge(
    val id: String,
    val title: String,
    val description: String,
    /** XP que otorga. */
    val reward: Int,
    val tag: String?,
    val startDate: String,
    val endDate: String,
    val active: Boolean,
)

@Immutable
data class ChallengeSubmission(
    val id: String,
    val challengeId: String,
    val imageUrl: String?,
    val description: String,
    val verified: Boolean,
    val awarded: Boolean,
    val createdAt: String,
    val challenge: Challenge? = null,
)

/** Reto pasado + mi participación (si la hubo). */
@Immutable
data class PastChallenge(val challenge: Challenge, val mySubmission: ChallengeSubmission?)

@Immutable
data class ChallengeHistory(val challenges: List<PastChallenge>, val totalXp: Int)

@Immutable
data class SubmitResult(
    val submission: ChallengeSubmission,
    val experiencePoints: Int,
    val level: Int,
    val leveledUp: Boolean,
    /** Aviso no bloqueante (p. ej. color no uniforme). */
    val validationMessage: String?,
)

// ───────────── Gamificación ─────────────

@Immutable
data class UserStreak(val loginCount: Int, val lastDate: String?)

/** Logro desbloqueado por el usuario. */
@Immutable
data class UserAchievement(
    val id: String,
    val achievementKey: String,
    val title: String,
    val description: String,
    val icon: String,
    val xpReward: Int,
    val unlockedAt: String,
)

@Immutable
data class UserBadge(
    val id: String,
    val badgeKey: String,
    val title: String,
    val description: String,
    val icon: String,
    val unlockedAt: String,
)

@Immutable
data class GamificationProgress(
    val experiencePoints: Int,
    val level: Int,
    val xpCurrent: Int,
    val xpNeeded: Int,
    /** 0..100 */
    val xpPercentage: Int,
    val streak: UserStreak,
    val achievements: List<UserAchievement>,
    val badges: List<UserBadge>,
    val challengeCount: Int,
)

/** Catálogo de logros con su estado de desbloqueo. */
@Immutable
data class Achievement(
    val key: String,
    val title: String,
    val description: String,
    val icon: String,
    val xpReward: Int,
    val unlocked: Boolean,
    val unlockedAt: String?,
)

/** Catálogo de insignias con su estado. [category]: retos | nivel | social | rachas. */
@Immutable
data class Badge(
    val key: String,
    val title: String,
    val description: String,
    val icon: String,
    val category: String,
    val unlocked: Boolean,
    val unlockedAt: String?,
)

@Immutable
data class DailyLoginResult(val streakCount: Int, val xpAwarded: Int, val newBadges: List<Badge>)
