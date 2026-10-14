package com.estilovivo.app.feature.suitcase

import androidx.lifecycle.SavedStateHandle
import com.estilovivo.app.core.data.AppScope
import com.estilovivo.app.core.data.session.SessionManager
import okhttp3.OkHttpClient
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

/** Módulo Koin de `suitcase` (viajes y wishlist). */
val suitcaseModule = module {
    // Los cambios de viaje se completan (o se deshacen) aunque se salga de la pantalla: viven en el ámbito de la app.
    single { TripStore(get(), get<AppScope>().scope) }
    // Las fotos de las prendas son públicas: cliente propio, sin credenciales.
    single<PhotoDownloader> {
        OkHttpPhotoDownloader(
            OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build(),
        )
    }

    viewModel { TripsViewModel(get()) }
    viewModel { (tripId: String) -> TripDetailViewModel(tripId, get(), get()) }
    viewModel { CreateTripViewModel(get(), get(), SavedStateDraftStore(get<SavedStateHandle>())) }
    viewModel { WishlistViewModel(get(), get(), get(), get()) { get<SessionManager>().userId } }
}
