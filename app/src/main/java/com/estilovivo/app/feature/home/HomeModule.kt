package com.estilovivo.app.feature.home

import com.estilovivo.app.core.util.CycleStore
import kotlinx.coroutines.flow.Flow
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import java.time.LocalDate

/** Adaptador de [CycleStore] (datos de salud cifrados en el dispositivo) a la interfaz que usa Inicio. */
class CycleStoreInfo(private val store: CycleStore) : CycleInfo {
    override val changes: Flow<Int> get() = store.changes
    override fun isInCycle(userId: String?, date: LocalDate) = store.isInCycle(userId, date)
    override fun messageFor(userId: String?, date: LocalDate) = store.messageFor(userId, date)
}

/** Módulo Koin de `home` (ViewModels y dependencias propias). */
val homeModule = module {
    single<WeatherApi> { OpenMeteoApi() }
    single { WeatherRepository(get()) }
    single<DeviceLocationProvider> { AndroidLocationProvider(androidContext()) }
    single<CycleInfo> { CycleStoreInfo(get()) }
    viewModel { HomeViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
}
