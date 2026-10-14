package com.estilovivo.app

import android.app.Application
import com.estilovivo.app.app.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class EstiloVivoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@EstiloVivoApp)
            modules(appModules)
        }
    }
}
