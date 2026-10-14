package com.estilovivo.app.feature.notifications

import com.estilovivo.app.app.Chat
import com.estilovivo.app.app.EvNavigator
import com.estilovivo.app.app.FollowRequests
import com.estilovivo.app.app.UserProfile
import com.estilovivo.app.app.Washing

/** Navega al destino de una notificación. Si no se puede abrir (ruta inexistente) llama a [onFail] y se queda donde está. */
fun EvNavigator.open(destination: NotificationDestination, onFail: () -> Unit) {
    val ok = when (destination) {
        is NotificationDestination.Chat -> tryNavigate(Chat(destination.conversationId))
        is NotificationDestination.UserProfile -> tryNavigate(UserProfile(destination.userId))
        is NotificationDestination.Tab -> { goToTab(destination.tab); true }
        NotificationDestination.FollowRequests -> tryNavigate(FollowRequests)
        NotificationDestination.Washing -> tryNavigate(Washing)
        NotificationDestination.None -> true
    }
    if (!ok) onFail()
}
