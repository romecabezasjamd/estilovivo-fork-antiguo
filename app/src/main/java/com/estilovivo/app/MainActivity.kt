package com.estilovivo.app

import android.content.Intent
import android.os.Bundle
import com.estilovivo.app.feature.notifications.NotificationLinks
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.estilovivo.app.app.EvApp
import com.estilovivo.app.app.Main
import com.estilovivo.app.app.Welcome
import com.estilovivo.app.core.data.session.AppPrefs
import com.estilovivo.app.feature.auth.DeepLinkHub
import com.estilovivo.app.core.data.session.SessionEvent
import com.estilovivo.app.core.data.session.SessionManager
import com.estilovivo.app.core.data.session.ThemeMode
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val session: SessionManager by inject()
    private val prefs: AppPrefs by inject()
    private val deepLinks: DeepLinkHub by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Si se abrió desde una notificación del sistema, EvApp navega al destino cuando haya sesión
        if (savedInstanceState == null) NotificationLinks.offer(intent)

        // Arranque en frío con enlace. Tras muerte del proceso el intent original se reentrega: no se repite
        if (savedInstanceState == null) handleLink(intent)

        // Destino inicial: si hay tokens guardados (access o refresh) se entra directo; el refresh es transparente.
        val start: Any = if (session.hasSession) Main else Welcome
        // Lectura puntual de ajustes (DataStore es rápido) para no parpadear de tema tras la splash
        val initialSettings = runBlocking { prefs.current() }
        val expired = session.events
            .filterIsInstance<SessionEvent.SessionExpired>()
            .onEach { session.acknowledgeExpired() }
            .map { }

        setContent {
            val settings by prefs.settings.collectAsStateWithLifecycle(initialSettings)
            val dark = when (settings.themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, density.fontScale * settings.fontScale),
            ) {
                EvApp(startDestination = start, darkTheme = dark, sessionExpired = expired)
            }
        }
    }

    /** Arranque en caliente (la actividad ya existe): llega un enlace nuevo. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        NotificationLinks.offer(intent)
        handleLink(intent)
    }

    /** Sólo `ACTION_VIEW`; el host/esquema y el formato del token los valida `DeepLinkParser`. */
    private fun handleLink(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        deepLinks.submit(intent.dataString)
    }
}
