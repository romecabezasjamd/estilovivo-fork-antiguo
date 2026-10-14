package com.estilovivo.app.feature.auth

import com.estilovivo.app.core.data.AppScope
import com.estilovivo.app.core.data.session.SessionManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Módulo Koin de `auth`. */
val authModule = module {
    single { DeepLinkHub() }
    single<AuthStore> { DataStoreAuthStore(androidContext(), get()) }
    viewModel {
        val session = get<SessionManager>()
        AuthViewModel(get(), get(), get(), get(), get<AppScope>().scope, hasSession = { session.hasSession })
    }
    viewModel { OnboardingViewModel(get(), get()) }
}
