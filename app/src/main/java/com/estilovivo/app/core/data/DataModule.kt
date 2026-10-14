package com.estilovivo.app.core.data

import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.estilovivo.app.BuildConfig
import com.estilovivo.app.core.data.net.ApiConfig
import com.estilovivo.app.core.data.net.ApiService
import com.estilovivo.app.core.data.net.AssetUrls
import com.estilovivo.app.core.data.net.Mappers
import com.estilovivo.app.core.data.net.NetworkFactory
import com.estilovivo.app.core.data.repo.AuthRepository
import com.estilovivo.app.core.data.repo.AuthRepositoryImpl
import com.estilovivo.app.core.data.repo.ChallengeRepository
import com.estilovivo.app.core.data.repo.ChallengeRepositoryImpl
import com.estilovivo.app.core.data.repo.ChatRepository
import com.estilovivo.app.core.data.repo.ChatRepositoryImpl
import com.estilovivo.app.core.data.repo.GamificationRepository
import com.estilovivo.app.core.data.repo.GamificationRepositoryImpl
import com.estilovivo.app.core.data.repo.GarmentRepository
import com.estilovivo.app.core.data.repo.GarmentRepositoryImpl
import com.estilovivo.app.core.data.repo.LookRepository
import com.estilovivo.app.core.data.repo.LookRepositoryImpl
import com.estilovivo.app.core.data.repo.NotificationRepository
import com.estilovivo.app.core.data.repo.NotificationRepositoryImpl
import com.estilovivo.app.core.data.repo.PlannerRepository
import com.estilovivo.app.core.data.repo.PlannerRepositoryImpl
import com.estilovivo.app.core.data.repo.SocialRepository
import com.estilovivo.app.core.data.repo.SocialRepositoryImpl
import com.estilovivo.app.core.data.repo.StatsRepository
import com.estilovivo.app.core.data.repo.StatsRepositoryImpl
import com.estilovivo.app.core.data.repo.StoryRepository
import com.estilovivo.app.core.data.repo.StoryRepositoryImpl
import com.estilovivo.app.core.data.repo.TrendsRepository
import com.estilovivo.app.core.data.repo.TrendsRepositoryImpl
import com.estilovivo.app.core.data.repo.TripRepository
import com.estilovivo.app.core.data.repo.TripRepositoryImpl
import com.estilovivo.app.core.data.repo.TryonPresetRepository
import com.estilovivo.app.core.data.repo.TryonPresetRepositoryImpl
import com.estilovivo.app.core.data.session.AppPrefs
import com.estilovivo.app.core.data.session.AuthSession
import com.estilovivo.app.core.data.session.SessionManager
import com.estilovivo.app.core.data.socket.SocketManager
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

/**
 * Capa de datos: red, sesión, repositorios, sockets y cargador de imágenes. Todo es singleton.
 * Las features sólo inyectan interfaces de repositorio (`get<GarmentRepository>()`), [SessionManager], [AppPrefs],
 * [SocketManager] o [AppScope].
 */
val dataModule = module {
    // ── base ──
    single { AppScope() }
    single { ApiConfig(BuildConfig.API_BASE) }
    single { Mappers(AssetUrls(get<ApiConfig>().origin)) }
    single { SessionManager(androidContext()) }
    single<AuthSession> { get<SessionManager>() }
    single { AppPrefs(androidContext()) }

    // ── red ──
    single<OkHttpClient> { NetworkFactory.apiClient(get(), get(), debug = BuildConfig.DEBUG) }
    single<ApiService> { NetworkFactory.service(NetworkFactory.retrofit(get(), get())) }

    // ── tiempo real (se conecta al iniciar sesión, createdAtStart para engancharse a la sesión desde el arranque) ──
    single(createdAtStart = true) { SocketManager(get(), get(), get(), get(), get()) }

    // ── repositorios ──
    single<AuthRepository> { AuthRepositoryImpl(get(), get(), get(), get()) }
    single<GarmentRepository> { GarmentRepositoryImpl(get(), get(), get(), get()) }
    single<LookRepository> { LookRepositoryImpl(get(), get(), get(), get()) }
    single<PlannerRepository> { PlannerRepositoryImpl(get(), get()) }
    single<TripRepository> { TripRepositoryImpl(get(), get()) }
    single<SocialRepository> { SocialRepositoryImpl(get(), get()) }
    single<ChatRepository> { ChatRepositoryImpl(get(), get(), get()) }
    single<StoryRepository> { StoryRepositoryImpl(get(), get(), get()) }
    single<ChallengeRepository> { ChallengeRepositoryImpl(get(), get()) }
    single<GamificationRepository> { GamificationRepositoryImpl(get(), get()) }
    single<NotificationRepository> { NotificationRepositoryImpl(get(), get(), get(), get(), get()) }
    single<TrendsRepository> { TrendsRepositoryImpl(get(), get()) }
    single<TryonPresetRepository> { TryonPresetRepositoryImpl(get(), get()) }
    single<StatsRepository> { StatsRepositoryImpl(get(), get()) }

    // ── imágenes (Coil 3, caché en disco 100 MB; se registra como cargador global) ──
    single(createdAtStart = true) {
        buildImageLoader(androidContext()).also { loader -> SingletonImageLoader.setSafe { loader } }
    }
}

private fun buildImageLoader(context: Context): ImageLoader {
    // Cliente propio y sin credenciales: las imágenes son públicas y pueden venir de otros hosts
    val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    return ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.25).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                .maxSizeBytes(100L * 1024 * 1024)
                .build()
        }
        .build()
}
