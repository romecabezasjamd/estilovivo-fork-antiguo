package com.estilovivo.app.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.navigation.NavHostController

/**
 * Acceso a la navegación desde cualquier pantalla sin pasar el NavController:
 * `val nav = LocalNavigator.current; nav.navigate(ProductDetail(id))`.
 */
@Stable
class EvNavigator(
    private val controller: NavHostController?,
    private val selectTab: (MainTab) -> Unit,
) {
    /** Navega a una ruta serializable de [Routes.kt]. */
    fun navigate(route: Any) { tryNavigate(route) }

    /** Como [navigate] pero devuelve `false` (sin crashear) si la ruta no existe o la navegación no es posible. */
    fun tryNavigate(route: Any): Boolean = try {
        controller?.navigate(route)
        controller != null
    } catch (_: IllegalArgumentException) {
        false
    } catch (_: IllegalStateException) {
        false
    }

    fun back() { controller?.popBackStack() }

    /** Cambia de pestaña dentro de [Main] (y vuelve a ella si hay pantallas apiladas). */
    fun goToTab(tab: MainTab) {
        selectTab(tab)
        controller?.popBackStack(Main, inclusive = false)
    }

    /** Cierra toda la sesión visual y vuelve a la bienvenida (logout / sesión caducada). */
    fun resetToWelcome() {
        selectTab(MainTab.Home) // la próxima sesión empieza siempre en Inicio
        controller?.navigate(Welcome) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }

    /** Entra en la app principal limpiando la pila de autenticación. */
    fun enterMain() {
        selectTab(MainTab.Home)
        controller?.navigate(Main) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }
}

val LocalNavigator = compositionLocalOf { EvNavigator(null) {} }
