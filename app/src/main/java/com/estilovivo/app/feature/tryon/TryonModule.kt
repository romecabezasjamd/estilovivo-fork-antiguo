package com.estilovivo.app.feature.tryon

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Módulo Koin de `tryon` (ViewModel, caché de imágenes y borrador en disco). */
val tryonModule = module {
    // garmentId vacío = abrir sin prenda concreta
    viewModel { (garmentId: String) ->
        TryOnViewModel(
            initialGarmentId = garmentId.ifBlank { null },
            garmentRepo = get(), looks = get(), presetRepo = get(), auth = get(),
            assets = AndroidTryOnAssets(androidContext()),
            drafts = FileTryOnDraftStore(androidContext()),
        )
    }
}
