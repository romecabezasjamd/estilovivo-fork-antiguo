package com.estilovivo.app.feature.notifications

import android.app.Application
import com.estilovivo.app.core.data.AppScope
import com.estilovivo.app.core.data.repo.NotificationRepository
import com.estilovivo.app.core.data.socket.SocketEvent
import com.estilovivo.app.core.data.socket.SocketManager
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Módulo Koin de `notifications`. */
val notificationsModule = module {
    single(createdAtStart = true) { AppForeground(androidContext() as Application) }
    single<SystemNotifier> { AndroidSystemNotifier(androidContext()) }
    single(createdAtStart = true) {
        val socket = get<SocketManager>()
        val repo = get<NotificationRepository>()
        // Al (re)conectar el socket se descargan las notificaciones: cubre el inicio de sesión y lo perdido sin conexión
        socket.connected.filter { it }.onEach { repo.refresh() }.launchIn(get<AppScope>().scope)
        val foreground = get<AppForeground>()
        NotificationAlerts(
            incoming = socket.events.filterIsInstance<SocketEvent.Notification>().map { it.notification },
            isForeground = { foreground.isForeground },
            system = get(),
            scope = get<AppScope>().scope,
        )
    }
    viewModel { NotificationsViewModel(get(), get()) }
}
