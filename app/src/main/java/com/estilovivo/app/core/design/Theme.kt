package com.estilovivo.app.core.design

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.estilovivo.app.core.data.session.AppPrefs
import com.estilovivo.app.core.data.session.AppSettings
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext

/** Formas del sistema: esquinas muy redondeadas, botones y chips en píldora. */
object EvShapes {
    val pill = CircleShape
    val card = RoundedCornerShape(26.dp)
    val cardSmall = RoundedCornerShape(20.dp)
    val hero = RoundedCornerShape(32.dp)
    val sheet = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    val field = RoundedCornerShape(18.dp)
}

/** Espaciado en dp (escala de 4). */
object EvSpace {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val huge = 32.dp
    /** Margen horizontal estándar de las pantallas. */
    val screen = 20.dp
}

object EvTheme {
    val colors: EvColors
        @Composable get() = LocalEvColors.current
}

/** Alto contraste: textos secundarios casi a tinta plena y bordes bien visibles (claro y oscuro). */
internal fun EvColors.withHighContrast(): EvColors =
    if (isDark) copy(
        inkSoft = Color(0xFFE4E4EC), inkMuted = Color(0xFFC2C2CE), outline = Color(0xFF9A9AA8),
        surfaceAlt = Color(0xFF2A2A33), success = Color(0xFF4ADE80), danger = Color(0xFFFF8FA3),
    ) else copy(
        inkSoft = Color(0xFF26262E), inkMuted = Color(0xFF45454F), outline = Color(0xFF6B6B78),
        surfaceAlt = Color(0xFFE8E8EE), success = Color(0xFF0F7A37), danger = Color(0xFFBE123C),
    )

@Composable
private fun EvColors.animated(): EvColors {
    val spec = tween<Color>(durationMillis = 320)
    return EvColors(
        bg = animateColorAsState(bg, spec, label = "evBg").value,
        surface = animateColorAsState(surface, spec, label = "evSurface").value,
        surfaceAlt = animateColorAsState(surfaceAlt, spec, label = "evSurfaceAlt").value,
        ink = animateColorAsState(ink, spec, label = "evInk").value,
        onInk = animateColorAsState(onInk, spec, label = "evOnInk").value,
        inkSoft = animateColorAsState(inkSoft, spec, label = "evInkSoft").value,
        inkMuted = animateColorAsState(inkMuted, spec, label = "evInkMuted").value,
        outline = animateColorAsState(outline, spec, label = "evOutline").value,
        accent = animateColorAsState(accent, spec, label = "evAccent").value,
        success = animateColorAsState(success, spec, label = "evSuccess").value,
        warning = animateColorAsState(warning, spec, label = "evWarning").value,
        danger = animateColorAsState(danger, spec, label = "evDanger").value,
        scrim = animateColorAsState(scrim, spec, label = "evScrim").value,
        isDark = isDark,
        brand = animateColorAsState(brand, spec, label = "evBrand").value,
        onBrand = animateColorAsState(onBrand, spec, label = "evOnBrand").value,
        brandSoft = animateColorAsState(brandSoft, spec, label = "evBrandSoft").value,
        brandInk = animateColorAsState(brandInk, spec, label = "evBrandInk").value,
        brandOnInk = animateColorAsState(brandOnInk, spec, label = "evBrandOnInk").value,
        navBrand = animateColorAsState(navBrand, spec, label = "evNavBrand").value,
        onNavBrand = animateColorAsState(onNavBrand, spec, label = "evOnNavBrand").value,
    )
}

private fun EvColors.toMaterial(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = ink, onPrimary = onInk,
        primaryContainer = surfaceAlt, onPrimaryContainer = ink,
        secondary = inkSoft, onSecondary = onInk,
        secondaryContainer = surfaceAlt, onSecondaryContainer = ink,
        tertiary = accent, onTertiary = onInk,
        background = bg, onBackground = ink,
        surface = surface, onSurface = ink,
        surfaceVariant = surfaceAlt, onSurfaceVariant = inkSoft,
        surfaceContainer = surface, surfaceContainerLow = surface, surfaceContainerHigh = surface,
        surfaceContainerHighest = surfaceAlt, surfaceContainerLowest = bg,
        outline = outline, outlineVariant = outline,
        error = danger, onError = onInk,
        scrim = scrim,
    )
}

/**
 * Tema raíz. Los componentes de Material heredan la paleta editorial; el movimiento propio
 * (muelles, entradas, pulsaciones) está en [EvMotion].
 */
@Composable
fun EvTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    // Ajustes de accesibilidad en vivo (alto contraste y háptica). Sin Koin (previews/tests) se usan los valores por defecto.
    val prefsFlow = remember { GlobalContext.getOrNull()?.getOrNull<AppPrefs>()?.settings ?: flowOf(AppSettings()) }
    // Lectura puntual síncrona (DataStore ya está en caché tras MainActivity): el tema pinta bien desde el primer frame, sin red.
    val initial = remember {
        GlobalContext.getOrNull()?.getOrNull<AppPrefs>()?.let { runCatching { runBlocking { it.current() } }.getOrNull() } ?: AppSettings()
    }
    val settings by prefsFlow.collectAsStateWithLifecycle(initial)
    val choice = remember(settings.themePreset, settings.customColor) { BrandChoice(settings.themePreset, settings.customColor) }
    val base = remember(darkTheme, choice) { (if (darkTheme) DarkEvColors else LightEvColors).withBrand(choice) }
    // El cambio claro/oscuro/contraste/color de marca se funde en vez de saltar.
    val colors = (if (settings.highContrast) base.withHighContrast() else base).animated()
    CompositionLocalProvider(LocalEvColors provides colors, LocalHapticsEnabled provides settings.haptics) {
        MaterialTheme(
            colorScheme = colors.toMaterial(),
            typography = EvTypography,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(10.dp),
                small = RoundedCornerShape(14.dp),
                medium = RoundedCornerShape(20.dp),
                large = RoundedCornerShape(26.dp),
                extraLarge = RoundedCornerShape(32.dp),
            ),
            content = content,
        )
    }
}
