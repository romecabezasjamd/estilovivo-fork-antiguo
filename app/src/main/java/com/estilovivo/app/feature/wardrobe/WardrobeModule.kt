package com.estilovivo.app.feature.wardrobe

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Módulo Koin de `wardrobe` (ViewModels y dependencias propias). */
val wardrobeModule = module {
    single { GarmentActions(get()) }
    single { AndroidPhotoCutout(androidContext()) }
    viewModel { WardrobeViewModel(get(), get(), get(), get()) }
    viewModel { (id: String) -> GarmentDetailViewModel(id, get(), get(), get(), get()) }
    viewModel { WashingViewModel(get(), get()) }
    // lookId vacío = look nuevo
    viewModel { (lookId: String) -> CreateLookViewModel(lookId.ifBlank { null }, get(), get()) }
}
