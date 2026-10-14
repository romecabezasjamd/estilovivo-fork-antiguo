package com.estilovivo.app.feature.planner

import androidx.lifecycle.SavedStateHandle
import com.estilovivo.app.core.data.AppScope
import com.estilovivo.app.core.data.session.SessionManager
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Módulo Koin de `planner` (ViewModels y dependencias propias). */
val plannerModule = module {
    single<CycleGateway> { CycleStoreGateway(get()) }
    viewModel {
        PlannerViewModel(
            planner = get(),
            lookRepo = get(),
            garments = get(),
            user = get<SessionManager>().user,
            cycle = get(),
            backgroundScope = get<AppScope>().scope,
            handle = get<SavedStateHandle>(),
        )
    }
}
