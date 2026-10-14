package com.estilovivo.app.core.data.net

import com.estilovivo.app.core.data.model.Achievement
import com.estilovivo.app.core.data.model.AppNotification
import com.estilovivo.app.core.data.model.Badge
import com.estilovivo.app.core.data.model.Challenge
import com.estilovivo.app.core.data.model.ChallengeHistory
import com.estilovivo.app.core.data.model.ChallengeSubmission
import com.estilovivo.app.core.data.model.Comment
import com.estilovivo.app.core.data.model.Conversation
import com.estilovivo.app.core.data.model.DailyLoginResult
import com.estilovivo.app.core.data.model.Favorite
import com.estilovivo.app.core.data.model.FollowRequest
import com.estilovivo.app.core.data.model.Garment
import com.estilovivo.app.core.data.model.GamificationProgress
import com.estilovivo.app.core.data.model.LastMessage
import com.estilovivo.app.core.data.model.Look
import com.estilovivo.app.core.data.model.Message
import com.estilovivo.app.core.data.model.PastChallenge
import com.estilovivo.app.core.data.model.PlannerEntry
import com.estilovivo.app.core.data.model.PublicProfile
import com.estilovivo.app.core.data.model.ShopItem
import com.estilovivo.app.core.data.model.Stats
import com.estilovivo.app.core.data.model.Story
import com.estilovivo.app.core.data.model.SubmitResult
import com.estilovivo.app.core.data.model.TopUser
import com.estilovivo.app.core.data.model.Trend
import com.estilovivo.app.core.data.model.Trip
import com.estilovivo.app.core.data.model.TripItem
import com.estilovivo.app.core.data.model.TryonPreset
import com.estilovivo.app.core.data.model.User
import com.estilovivo.app.core.data.model.UserAchievement
import com.estilovivo.app.core.data.model.UserBadge
import com.estilovivo.app.core.data.model.UserStreak
import com.estilovivo.app.core.data.model.UserSummary
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Texto no vacío o null (equivale al `||` de JavaScript sobre cadenas). */
private fun String?.orNull(): String? = this?.takeIf { it.isNotEmpty() }

/**
 * Mapeos DTO -> dominio. Réplica de los de `web/services/api.ts` (`mapProductToGarment`, `mapLook`, `mapTrip`, ...).
 * Todas las imágenes salen como URLs absolutas.
 */
internal class Mappers(val assets: AssetUrls) {

    fun userLite(dto: UserLiteDto?): UserSummary? =
        dto?.id?.let { UserSummary(it, dto.name ?: "Usuario", assets.resolve(dto.avatar)) }

    // ───── usuario ─────

    /** Convierte un campo "lista" del servidor (string JSON `["a","b"]`, o CSV) en lista. */
    fun parseStringList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val trimmed = raw.trim()
        if (trimmed.startsWith("[")) {
            runCatching {
                (ApiJson.parseToJsonElement(trimmed) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            }.getOrNull()?.let { return it }
        }
        return trimmed.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** [previous] aporta los contadores que algunas respuestas (preferencias, refresh) no incluyen. */
    fun user(dto: UserDto, previous: User? = null): User = User(
        id = dto.id ?: previous?.id.orEmpty(),
        email = dto.email ?: previous?.email,
        name = dto.name,
        bio = dto.bio ?: "",
        avatar = assets.resolve(dto.avatar),
        fullBodyAvatar = assets.resolve(dto.fullBodyAvatar),
        mood = dto.mood,
        gender = dto.gender,
        birthDate = dto.birthDate,
        cycleTracking = dto.cycleTracking ?: false,
        musicSync = dto.musicSync ?: false,
        emailNotifications = dto.emailNotifications ?: true,
        emailChat = dto.emailChat ?: true,
        emailFollows = dto.emailFollows ?: true,
        emailWashing = dto.emailWashing ?: true,
        emailChallenges = dto.emailChallenges ?: true,
        isVerified = dto.isVerified ?: true,
        experiencePoints = dto.experiencePoints ?: 0,
        level = dto.level ?: 1,
        themePreset = dto.themePreset,
        customColor = dto.customColor,
        isProfilePublic = dto.isProfilePublic ?: true,
        locationName = dto.locationName,
        darkModeSetting = dto.darkModeSetting,
        fontSize = dto.fontSize,
        highContrast = dto.highContrast ?: false,
        language = dto.language,
        dialect = dto.dialect,
        styleColors = parseStringList(dto.styleColors),
        styleStyles = parseStringList(dto.styleStyles),
        styleOccasions = parseStringList(dto.styleOccasions),
        styleFabrics = parseStringList(dto.styleFabrics),
        followersCount = dto.followersCount ?: previous?.followersCount ?: 0,
        followingCount = dto.followingCount ?: previous?.followingCount ?: 0,
        garmentCount = dto.garmentCount ?: previous?.garmentCount ?: 0,
        lookCount = dto.lookCount ?: previous?.lookCount ?: 0,
    )

    fun topUser(dto: TopUserDto) = TopUser(dto.id, dto.name, assets.resolve(dto.avatar), dto.experiencePoints ?: 0, dto.level ?: 1)

    fun publicProfile(dto: PublicProfileDto): PublicProfile {
        val avatar = assets.resolve(dto.avatar)
        return PublicProfile(
            id = dto.id,
            name = dto.name,
            avatar = avatar,
            fullBodyAvatar = assets.resolve(dto.fullBodyAvatar),
            bio = dto.bio ?: "",
            mood = dto.mood,
            gender = dto.gender,
            locationName = dto.locationName,
            experiencePoints = dto.experiencePoints ?: 0,
            level = dto.level ?: 1,
            isProfilePublic = dto.isProfilePublic ?: true,
            followersCount = dto.followersCount ?: 0,
            followingCount = dto.followingCount ?: 0,
            garmentCount = dto.garmentCount ?: 0,
            lookCount = dto.lookCount ?: 0,
            isFollowing = dto.isFollowing ?: false,
            hasPendingRequest = dto.hasPendingRequest ?: false,
            pendingRequestId = dto.pendingRequestId,
            garmentsForSale = dto.products.orEmpty().mapNotNull { garment(it)?.copy(userId = it.userId ?: dto.id, userName = dto.name, userAvatar = avatar) },
            // Los looks del perfil no traen `user`: se completa con el dueño del perfil
            looks = dto.looks.orEmpty().mapNotNull { look(it)?.copy(userId = it.userId ?: dto.id, userName = dto.name, userAvatar = avatar) },
        )
    }

    fun followRequest(dto: FollowRequestDto) = FollowRequest(
        id = dto.id,
        requester = userLite(dto.requester) ?: UserSummary("", "Usuario", null),
        status = dto.status ?: "pending",
        createdAt = dto.createdAt.orEmpty(),
    )

    // ───── prendas y looks ─────

    /** `mapProductToGarment` de la web. */
    fun garment(p: ProductDto?): Garment? {
        if (p == null) return null
        return Garment(
            id = p.id,
            imageUrl = assets.resolve(p.images?.firstOrNull()?.url.orNull()) ?: assets.placeholder,
            name = p.name.orNull() ?: p.category.orEmpty(),
            type = p.category.orNull() ?: "top",
            color = p.color.orNull() ?: "varios",
            season = p.season.orNull() ?: "all",
            usageCount = p.usageCount ?: 0,
            lastWorn = p.lastWorn.orNull(),
            forSale = p.forSale ?: false,
            price = p.price ?: 0.0,
            brand = p.brand.orNull(),
            size = p.size.orNull(),
            condition = p.condition.orNull() ?: "new",
            description = p.description.orNull(),
            isWashing = p.isWashing ?: false,
            userId = p.userId.orNull() ?: p.user?.id,
            userName = p.user?.name,
            userAvatar = assets.resolve(p.user?.avatar),
            createdAt = p.createdAt,
        )
    }

    private fun parseTags(raw: JsonElement?): List<String> = when (raw) {
        null, JsonNull -> emptyList()
        is JsonArray -> raw.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        is JsonPrimitive -> if (raw.isString && raw.content.trim().startsWith("[")) parseStringList(raw.content) else emptyList()
        else -> emptyList()
    }

    /** `mapLook` de la web (el `mood` se antepone a las etiquetas). */
    fun look(l: LookDto?): Look? {
        if (l == null) return null
        var tags = parseTags(l.tags)
        val mood = l.mood.orNull()
        if (mood != null && mood !in tags) tags = listOf(mood) + tags
        val products = l.products ?: l.garments
        val garments = products.orEmpty().mapNotNull { garment(it) }
        return Look(
            id = l.id,
            name = l.title.orNull() ?: l.name.orEmpty(),
            garmentIds = garments.map { it.id },
            garments = garments,
            tags = tags,
            mood = l.mood,
            createdAt = l.createdAt.orEmpty(),
            isPublic = l.isPublic ?: false,
            imageUrl = assets.resolve(l.images?.firstOrNull()?.url.orNull() ?: l.products?.firstOrNull()?.images?.firstOrNull()?.url),
            userId = l.userId.orNull() ?: l.user?.id,
            userName = l.user?.name,
            userAvatar = assets.resolve(l.user?.avatar),
            likesCount = l.likesCount ?: l.count?.likes ?: 0,
            commentsCount = l.commentsCount ?: l.count?.comments ?: 0,
            isLiked = l.isLiked ?: false,
            isFavorited = l.isFavorited ?: false,
        )
    }

    fun plannerEntry(e: PlannerEntryDto) = PlannerEntry(
        date = e.date,
        lookId = e.lookId,
        look = look(e.look),
        eventNote = e.eventNote.orNull(),
    )

    fun tripItem(i: TripItemDto) = TripItem(i.id, i.label, i.checked ?: false, i.isEssential ?: false)

    /** `mapTrip` de la web. */
    fun trip(t: TripDto) = Trip(
        id = t.id,
        destination = t.destination,
        dateStart = t.dateStart,
        dateEnd = t.dateEnd,
        items = t.items.orEmpty().map(::tripItem),
        garments = t.garments.orEmpty().mapNotNull { garment(it) },
    )

    fun shopItem(p: ProductDto): ShopItem {
        val sellerName = p.user?.name
        return ShopItem(
            id = p.id,
            user = sellerName ?: "Vendedor",
            userId = p.user?.id ?: "",
            avatar = assets.resolve(p.user?.avatar)
                ?: "https://ui-avatars.com/api/?name=${java.net.URLEncoder.encode(sellerName ?: "V", "UTF-8")}&background=0F4C5C&color=fff",
            image = assets.resolve(p.images?.firstOrNull()?.url.orNull()) ?: assets.placeholder,
            title = p.name.orNull() ?: p.category.orEmpty(),
            price = p.price ?: 0.0,
            size = p.size.orNull() ?: "Única",
            brand = p.brand.orNull() ?: "Sin marca",
            condition = p.condition.orNull() ?: "new",
            images = p.images.orEmpty().mapNotNull { assets.resolve(it.url.orNull()) },
            color = p.color.orNull(),
            description = p.description.orNull(),
            category = p.category.orEmpty(),
            createdAt = p.createdAt.orEmpty(),
        )
    }

    fun favorite(f: FavoriteDto): Favorite = Favorite(
        id = f.id,
        look = look(f.look)?.copy(isFavorited = true),
        garment = garment(f.product),
        createdAt = f.createdAt.orEmpty(),
    )

    fun stats(s: StatsDto) = Stats(
        garmentCount = s.garmentCount ?: 0,
        lookCount = s.lookCount ?: 0,
        salesCount = s.salesCount ?: 0,
        tripsCount = s.tripsCount ?: 0,
        totalUsage = s.totalUsage ?: 0,
        mostWorn = garment(s.mostWorn),
        leastWornCount = s.leastWornCount ?: 0,
        leastWorn = s.leastWorn.orEmpty().mapNotNull { garment(it) },
    )

    // ───── social ─────

    /** `getComments`/`addComment` de la web. */
    fun comment(c: CommentDto): Comment = Comment(
        id = c.id,
        content = c.content,
        userId = c.user?.id ?: c.userId.orEmpty(),
        userName = c.user?.name ?: "Usuario",
        userAvatar = assets.resolve(c.user?.avatar),
        parentId = c.parentId,
        replies = c.replies.orEmpty().map { r ->
            Comment(
                id = r.id,
                content = r.content,
                userId = r.user?.id ?: r.userId.orEmpty(),
                userName = r.user?.name ?: "Usuario",
                userAvatar = assets.resolve(r.user?.avatar),
                parentId = r.parentId,
                createdAt = r.createdAt.orEmpty(),
            )
        },
        createdAt = c.createdAt.orEmpty(),
    )

    fun message(m: MessageDto): Message = Message(
        id = m.id,
        conversationId = m.conversationId,
        senderId = m.senderId,
        content = m.content.orEmpty(),
        createdAt = m.createdAt.orEmpty(),
        imageUrl = assets.resolve(m.imageUrl),
        productId = m.productId,
        sender = userLite(m.sender),
    )

    /** [currentUserId] sirve para resolver `otherUser` cuando el servidor no lo envía (p. ej. al crear). */
    fun conversation(c: ConversationDto, currentUserId: String?): Conversation {
        val participants = c.participants.orEmpty().mapNotNull { p ->
            userLite(p.user ?: p.userId?.let { UserLiteDto(id = it) })
        }
        val other = userLite(c.otherUser)
            ?: participants.firstOrNull { it.id != currentUserId }.takeIf { currentUserId != null }
        val last = c.messages?.firstOrNull()
        return Conversation(
            id = c.id,
            itemId = c.itemId,
            itemTitle = c.itemTitle,
            itemImage = assets.resolve(c.itemImage),
            itemOwnerId = c.itemOwnerId,
            updatedAt = c.updatedAt ?: c.createdAt.orEmpty(),
            lastMessage = last?.let { LastMessage(it.id, it.content.orEmpty(), it.createdAt.orEmpty(), userLite(it.sender)) },
            participants = participants,
            otherUser = other,
        )
    }

    fun story(s: StoryDto, currentUserId: String?) = Story(
        id = s.id,
        userId = s.userId,
        userName = s.user?.name ?: "Usuario",
        userAvatar = assets.resolve(s.user?.avatar),
        type = s.type,
        text = s.text.orNull(),
        imageUrl = assets.resolve(s.imageUrl),
        views = s.views ?: 0,
        createdAt = s.createdAt.orEmpty(),
        expiresAt = s.expiresAt.orEmpty(),
        isOwn = currentUserId != null && s.userId == currentUserId,
    )

    fun notification(n: NotificationDto) = AppNotification(
        id = n.id,
        type = n.type,
        content = n.content.orEmpty(),
        relatedId = n.relatedId,
        isRead = n.isRead ?: false,
        createdAt = n.createdAt.orEmpty(),
    )

    // ───── retos / gamificación ─────

    fun challenge(c: ChallengeDto) = Challenge(
        id = c.id,
        title = c.title,
        description = c.description.orEmpty(),
        reward = c.reward ?: 0,
        tag = c.tag,
        startDate = c.startDate.orEmpty(),
        endDate = c.endDate.orEmpty(),
        active = c.active ?: true,
    )

    fun submission(s: SubmissionDto, challengeId: String? = null) = ChallengeSubmission(
        id = s.id,
        challengeId = s.challengeId ?: challengeId.orEmpty(),
        imageUrl = assets.resolve(s.imageUrl),
        description = s.description.orEmpty(),
        verified = s.verified ?: false,
        awarded = s.awarded ?: false,
        createdAt = s.createdAt.orEmpty(),
        challenge = s.challenge?.let(::challenge),
    )

    fun submitResult(s: SubmissionDto) = SubmitResult(
        submission = submission(s),
        experiencePoints = s.experiencePoints ?: 0,
        level = s.level ?: 1,
        leveledUp = s.leveledUp ?: false,
        validationMessage = s.validationMessage.orNull(),
    )

    fun challengeHistory(h: ChallengeHistoryDto) = ChallengeHistory(
        challenges = h.challenges.map { c -> PastChallenge(challenge(c), c.submissions?.firstOrNull()?.let { submission(it, c.id) }) },
        totalXp = h.totalXp,
    )

    fun progress(p: ProgressDto) = GamificationProgress(
        experiencePoints = p.experiencePoints,
        level = p.level,
        xpCurrent = p.xpCurrent,
        xpNeeded = p.xpNeeded,
        xpPercentage = p.xpPercentage,
        streak = UserStreak(p.streak?.loginCount ?: 0, p.streak?.lastDate),
        achievements = p.achievements.orEmpty().map { UserAchievement(it.id, it.achievementKey, it.title, it.description, it.icon, it.xpReward, it.unlockedAt) },
        badges = p.badges.orEmpty().map { UserBadge(it.id, it.badgeKey, it.title, it.description, it.icon, it.unlockedAt) },
        challengeCount = p.challengeCount,
    )

    fun achievement(c: CatalogItemDto) = Achievement(c.key, c.title, c.description, c.icon, c.xpReward, c.unlocked, c.unlockedAt)

    fun badge(c: CatalogItemDto) = Badge(c.key, c.title, c.description, c.icon, c.category, c.unlocked, c.unlockedAt)

    fun dailyLogin(d: DailyLoginDto) = DailyLoginResult(d.streakCount, d.xpAwarded, d.newBadges.map(::badge))

    // ───── varios ─────

    fun trend(t: TrendDto) = Trend(
        id = t.id,
        title = t.title,
        category = t.category.orEmpty(),
        description = t.description.orEmpty(),
        image = assets.resolve(t.image).orEmpty(),
        tags = t.tags.orEmpty(),
        source = t.source.orEmpty(),
        link = t.link.orNull(),
    )

    fun tryonPreset(p: TryonPresetDto) = TryonPreset(
        id = p.id,
        name = p.name,
        thumbnail = assets.resolve(p.thumbnail),
        layers = p.layers.orEmpty().map { it.copy(url = assets.resolve(it.url) ?: it.url) },
        rating = p.rating,
        occasion = p.occasion,
    )
}
