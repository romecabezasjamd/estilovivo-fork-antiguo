package com.estilovivo.app.core.util

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Utilidades compartidas entre features. */
val utilModule = module {
    single { CycleStore(androidContext()) }
}
