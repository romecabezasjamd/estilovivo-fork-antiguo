package com.estilovivo.app.feature.profile

import com.estilovivo.app.core.data.socket.SocketManager
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Módulo Koin de `profile` (ViewModels y dependencias propias). */
val profileModule = module {
    single<LocalSettings> { AppPrefsLocalSettings(get()) }
    single<CycleStorage> { CycleStoreStorage(get()) }
    viewModel { ProfileViewModel(get(), get(), get(), get(), get(), get<SocketManager>().events, get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get()) }
    viewModel { EditProfileViewModel(get(), get(), get()) }
    viewModel { ChangePasswordViewModel(get()) }
    viewModel { FollowRequestsViewModel(get(), get()) }
}
