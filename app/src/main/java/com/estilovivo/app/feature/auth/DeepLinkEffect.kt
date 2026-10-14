package com.estilovivo.app.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import com.estilovivo.app.app.Auth
import com.estilovivo.app.app.LocalNavigator
import com.estilovivo.app.app.Main
import com.estilovivo.app.app.UserProfile
import com.estilovivo.app.core.data.onFailure
import com.estilovivo.app.core.data.onSuccess
import com.estilovivo.app.core.data.repo.AuthRepository
import com.estilovivo.app.core.data.session.SessionManager
import com.estilovivo.app.core.design.components.LocalEvToast
import org.koin.compose.koinInject

/**
 * Lleva los enlaces entrantes (cold/warm start, `onNewIntent`) al sitio correcto. Colocar dentro del tema, una vez,
 * junto al `NavHost` (ver `EvApp`). Reset y verificación sin sesión abren `Auth`, donde los toma [AuthViewModel];
 * la verificación con sesión abierta se resuelve aquí (toast y seguir); perfiles ajenos sólo con sesión.
 */
@Composable
fun DeepLinkEffect(nav: NavHostController) {
    val hub: DeepLinkHub = koinInject()
    val session: SessionManager = koinInject()
    val auth: AuthRepository = koinInject()
    val toast = LocalEvToast.current
    val navigator = LocalNavigator.current
    val pending by hub.pending.collectAsStateWithLifecycle()
    val sessionState by session.state.collectAsStateWithLifecycle()
    val entry by nav.currentBackStackEntryFlow.collectAsState(initial = null)
    val onAuth = entry?.destination?.hasRoute<Auth>() == true
    val onMain = entry?.destination?.hasRoute<Main>() == true
    val navigatedSeq = remember { mutableLongStateOf(0L) }

    LaunchedEffect(pending, sessionState, onAuth, onMain) {
        val p = pending ?: return@LaunchedEffect
        val hasSession = session.hasSession
        when (val link = p.link) {
            is AppLink.VerifyEmail -> if (hasSession) {
                hub.consume(p.seq)
                auth.verifyEmail(link.token)
                    .onSuccess { toast.success("Tu correo ha sido verificado") }
                    .onFailure { toast.error(it.message) }
            } else if (!onAuth && navigatedSeq.longValue != p.seq) {
                navigatedSeq.longValue = p.seq
                navigator.navigate(Auth)
            }
            is AppLink.ResetPassword, is AppLink.AuthError ->
                if (!onAuth && navigatedSeq.longValue != p.seq) {
                    navigatedSeq.longValue = p.seq
                    navigator.navigate(Auth)
                }
            is AppLink.User -> if (hasSession && onMain) {
                hub.consume(p.seq)
                navigator.navigate(UserProfile(link.id))
            }
            // No existe pantalla de look suelta: con sesión se queda en Inicio
            is AppLink.Look -> if (hasSession && onMain) hub.consume(p.seq)
        }
    }
}
