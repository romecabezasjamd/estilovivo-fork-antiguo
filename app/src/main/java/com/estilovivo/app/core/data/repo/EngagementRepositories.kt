package com.estilovivo.app.core.data.repo

import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.AppScope
import com.estilovivo.app.core.data.ImageUpload
import com.estilovivo.app.core.data.model.Achievement
import com.estilovivo.app.core.data.model.AppNotification
import com.estilovivo.app.core.data.model.Badge
import com.estilovivo.app.core.data.model.Challenge
import com.estilovivo.app.core.data.model.ChallengeHistory
import com.estilovivo.app.core.data.model.ChallengeSubmission
import com.estilovivo.app.core.data.model.DailyLoginResult
import com.estilovivo.app.core.data.model.GamificationProgress
import com.estilovivo.app.core.data.model.Stats
import com.estilovivo.app.core.data.model.SubmitResult
import com.estilovivo.app.core.data.model.Trend
import com.estilovivo.app.core.data.model.TryonLayer
import com.estilovivo.app.core.data.model.TryonPreset
import com.estilovivo.app.core.data.net.ApiService
import com.estilovivo.app.core.data.net.Mappers
import com.estilovivo.app.core.data.net.TryonPresetRequest
import com.estilovivo.app.core.data.session.SessionManager
import com.estilovivo.app.core.data.socket.SocketEvent
import com.estilovivo.app.core.data.socket.SocketManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.atomic.AtomicReference

/** Notificaciones. [notifications] y [unreadCount] se mantienen al día con las llamadas y con los eventos del socket. */
interface NotificationRepository {
    val notifications: StateFlow<List<AppNotification>>

    /** Nº de no leídas (para la campana). Es 0 hasta la primera llamada a [refresh] o la llegada de un evento. */
    val unreadCount: StateFlow<Int>

    /** Descarga las últimas 50 y actualiza [notifications]. */
    suspend fun refresh(): ApiResult<List<AppNotification>>
    suspend fun markRead(id: String): ApiResult<Unit>
    suspend fun markAllRead(): ApiResult<Unit>
}

internal class NotificationRepositoryImpl(
    private val api: ApiService,
    private val mappers: Mappers,
    session: SessionManager,
    socket: SocketManager,
    appScope: AppScope,
) : NotificationRepository {
    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    override val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    private val _unread = MutableStateFlow(0)
    override val unreadCount: StateFlow<Int> = _unread.asStateFlow()

    init {
        appScope.scope.onSessionEnded(session) { publish(emptyList()) }
        socket.events.onEach { e ->
            if (e is SocketEvent.Notification) publish(listOf(e.notification) + _notifications.value.filterNot { it.id == e.notification.id })
        }.launchIn(appScope.scope)
    }

    private fun publish(list: List<AppNotification>) {
        _notifications.value = list
        _unread.value = list.count { !it.isRead }
    }

    override suspend fun refresh(): ApiResult<List<AppNotification>> = apiCall {
        val list = api.notifications().map(mappers::notification)
        publish(list)
        list
    }

    override suspend fun markRead(id: String): ApiResult<Unit> = apiCall {
        api.markNotificationRead(id)
        publish(_notifications.value.map { if (it.id == id) it.copy(isRead = true) else it })
    }

    override suspend fun markAllRead(): ApiResult<Unit> = apiCall {
        api.markAllNotificationsRead()
        publish(_notifications.value.map { it.copy(isRead = true) })
    }
}

/** Retos semanales. */
interface ChallengeRepository {
    /** Reto de esta semana (el servidor lo crea si no existe). */
    suspend fun getCurrent(): ApiResult<Challenge>
    suspend fun getMySubmissions(): ApiResult<List<ChallengeSubmission>>
    suspend fun getHistory(): ApiResult<ChallengeHistory>

    /** Participa: [description] entre 10 y 500 caracteres. Otorga XP; mira [SubmitResult.leveledUp] para el confeti. */
    suspend fun submit(challengeId: String, description: String, image: ImageUpload): ApiResult<SubmitResult>

    /** Retira la participación (y el XP concedido). */
    suspend fun deleteSubmission(submissionId: String): ApiResult<Unit>
}

internal class ChallengeRepositoryImpl(private val api: ApiService, private val mappers: Mappers) : ChallengeRepository {
    override suspend fun getCurrent() = apiCall { mappers.challenge(api.currentChallenge()) }

    override suspend fun getMySubmissions() = apiCall { api.mySubmissions().map { mappers.submission(it) } }

    override suspend fun getHistory() = apiCall { mappers.challengeHistory(api.challengeHistory()) }

    override suspend fun submit(challengeId: String, description: String, image: ImageUpload) = apiCall {
        mappers.submitResult(
            api.submitChallenge(
                fields = textParts("challengeId" to challengeId, "description" to description.trim()),
                image = image.toPart("image"),
            ),
        )
    }

    override suspend fun deleteSubmission(submissionId: String) = apiCall { api.deleteSubmission(submissionId); Unit }
}

/** XP, nivel, logros, insignias y racha. */
interface GamificationRepository {
    suspend fun getProgress(): ApiResult<GamificationProgress>

    /** Catálogo completo de logros (con `unlocked`). */
    suspend fun getAchievements(): ApiResult<List<Achievement>>

    /** Catálogo completo de insignias (con `unlocked`). */
    suspend fun getBadges(): ApiResult<List<Badge>>

    /** Evalúa y desbloquea logros pendientes; devuelve los recién conseguidos. */
    suspend fun checkAchievements(): ApiResult<List<Achievement>>

    /** Registra el acceso diario (racha + 5 XP la primera vez del día). Llámalo al abrir la app con sesión. */
    suspend fun dailyLogin(): ApiResult<DailyLoginResult>
}

internal class GamificationRepositoryImpl(private val api: ApiService, private val mappers: Mappers) : GamificationRepository {
    override suspend fun getProgress() = apiCall { mappers.progress(api.gamificationProgress()) }
    override suspend fun getAchievements() = apiCall { api.achievements().map(mappers::achievement) }
    override suspend fun getBadges() = apiCall { api.badges().map(mappers::badge) }
    override suspend fun checkAchievements() = apiCall { api.checkAchievements().newlyUnlocked.map(mappers::achievement) }
    override suspend fun dailyLogin() = apiCall { mappers.dailyLogin(api.gamificationLogin()) }
}

/** Tendencias de moda (Vogue/GQ + respaldo). */
interface TrendsRepository {
    /** Cacheado 5 min salvo [forceRefresh]. */
    suspend fun getTrends(forceRefresh: Boolean = false): ApiResult<List<Trend>>
}

internal class TrendsRepositoryImpl(private val api: ApiService, private val mappers: Mappers) : TrendsRepository {
    private data class Cached(val at: Long, val trends: List<Trend>)

    private val cache = AtomicReference<Cached?>(null)

    override suspend fun getTrends(forceRefresh: Boolean): ApiResult<List<Trend>> {
        val c = cache.get()
        if (!forceRefresh && c != null && System.currentTimeMillis() - c.at < TTL_MS) return ApiResult.Success(c.trends)
        return apiCall {
            val trends = api.trends().map(mappers::trend)
            cache.set(Cached(System.currentTimeMillis(), trends))
            trends
        }
    }

    private companion object {
        const val TTL_MS = 5 * 60 * 1000L
    }
}

/** Looks guardados del probador virtual (máx. 50 por usuario). */
interface TryonPresetRepository {
    suspend fun getAll(): ApiResult<List<TryonPreset>>

    /** [thumbnail]: data URL `data:image/jpeg;base64,...` (la API limita el cuerpo a 3 MB). [rating] 1..5. */
    suspend fun save(
        name: String,
        thumbnail: String?,
        layers: List<TryonLayer>,
        rating: Int? = null,
        occasion: String? = null,
    ): ApiResult<TryonPreset>

    /** Cambios parciales (null = no tocar). */
    suspend fun update(
        id: String,
        name: String? = null,
        thumbnail: String? = null,
        layers: List<TryonLayer>? = null,
        rating: Int? = null,
        occasion: String? = null,
    ): ApiResult<TryonPreset>

    suspend fun delete(id: String): ApiResult<Unit>
}

internal class TryonPresetRepositoryImpl(private val api: ApiService, private val mappers: Mappers) : TryonPresetRepository {
    override suspend fun getAll() = apiCall { api.tryonPresets().map(mappers::tryonPreset) }

    override suspend fun save(name: String, thumbnail: String?, layers: List<TryonLayer>, rating: Int?, occasion: String?) = apiCall {
        mappers.tryonPreset(api.createTryonPreset(TryonPresetRequest(name, thumbnail, layers, rating, occasion)))
    }

    override suspend fun update(id: String, name: String?, thumbnail: String?, layers: List<TryonLayer>?, rating: Int?, occasion: String?) =
        apiCall { mappers.tryonPreset(api.updateTryonPreset(id, TryonPresetRequest(name, thumbnail, layers, rating, occasion))) }

    override suspend fun delete(id: String) = apiCall { api.deleteTryonPreset(id); Unit }
}

/** Estadísticas del armario. */
interface StatsRepository {
    suspend fun getStats(): ApiResult<Stats>
}

internal class StatsRepositoryImpl(private val api: ApiService, private val mappers: Mappers) : StatsRepository {
    override suspend fun getStats() = apiCall { mappers.stats(api.stats()) }
}
