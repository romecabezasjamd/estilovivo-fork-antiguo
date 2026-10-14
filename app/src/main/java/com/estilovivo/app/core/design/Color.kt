package com.estilovivo.app.core.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Paleta editorial en blanco/negro con un único acento (corazón / alertas).
 * Referencia visual: lienzo gris suave, tarjetas blancas, tinta casi negra,
 * chips y botones en píldora.
 */
@Immutable
data class EvColors(
    /** Lienzo de fondo de las pantallas. */
    val bg: Color,
    /** Tarjetas, hojas, barra de búsqueda. */
    val surface: Color,
    /** Superficie secundaria (chips sin seleccionar, campos). */
    val surfaceAlt: Color,
    /** Tinta principal: texto, botones primarios, chip seleccionado. */
    val ink: Color,
    /** Texto sobre `ink`. */
    val onInk: Color,
    /** Texto secundario. */
    val inkSoft: Color,
    /** Texto terciario / placeholders. */
    val inkMuted: Color,
    /** Bordes y separadores. */
    val outline: Color,
    /** Acento cálido (me gusta, destacados). */
    val accent: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    /** Oscurecido tras modales. */
    val scrim: Color,
    val isDark: Boolean,
    /** Color de marca (botón primario, selección, progreso, interruptores). Clásico = `ink`. */
    val brand: Color = ink,
    /** Texto/icono sobre `brand` (blanco o negro automático, contraste ≥ 4,5:1). */
    val onBrand: Color = onInk,
    /** Marca al ~12 % sobre la superficie (fondos suaves, vista previa). */
    val brandSoft: Color = ink.copy(alpha = 0.12f),
    /** Marca legible como texto sobre `surface` (enlaces, ≥ 4,5:1). */
    val brandInk: Color = ink,
    /** Marca sobre paneles de fondo `ink` (barra de XP del panel oscuro). */
    val brandOnInk: Color = onInk,
    /** Cápsula activa de la barra inferior (la barra es siempre oscura). */
    val navBrand: Color = Color.White,
    val onNavBrand: Color = Color(0xFF0B0B0F),
)

val LightEvColors = EvColors(
    bg = Color(0xFFF1F1F4),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFF4F4F7),
    ink = Color(0xFF0B0B0F),
    onInk = Color(0xFFFFFFFF),
    inkSoft = Color(0xFF5E5E6B),
    inkMuted = Color(0xFF9A9AA8),
    outline = Color(0xFFE3E3E9),
    accent = Color(0xFFFF3B5C),
    success = Color(0xFF16A34A),
    warning = Color(0xFFF59E0B),
    danger = Color(0xFFE11D48),
    scrim = Color(0x990B0B0F),
    isDark = false,
)

val DarkEvColors = EvColors(
    bg = Color(0xFF0A0A0D),
    surface = Color(0xFF17171C),
    surfaceAlt = Color(0xFF212128),
    ink = Color(0xFFF5F5F8),
    onInk = Color(0xFF0B0B0F),
    inkSoft = Color(0xFFA2A2B0),
    inkMuted = Color(0xFF6C6C7A),
    outline = Color(0xFF2B2B34),
    accent = Color(0xFFFF5C7A),
    success = Color(0xFF34D399),
    warning = Color(0xFFFBBF24),
    danger = Color(0xFFFB7185),
    scrim = Color(0xB3000000),
    isDark = true,
)

/** Gris azulado de la pantalla de bienvenida (degradado del hero). */
val WelcomeSlate = Color(0xFF7D848F)
val WelcomeSlateDeep = Color(0xFF14171D)

val LocalEvColors = staticCompositionLocalOf { LightEvColors }

/** Fondo de los paneles grandes (nivel, "look de hoy", cabeceras). En oscuro `ink` es casi blanco: aquí queda un panel oscuro. */
val PanelDark = Color(0xFF25252E)

/**
 * Colores para pintar DENTRO de un panel oscuro: en claro no cambia nada (ink negro / onInk blanco);
 * en oscuro `ink` pasa a panel oscuro y `onInk` a texto claro, de modo que `background(c.ink)` + `c.onInk` siguen siendo coherentes.
 */
fun EvColors.asPanel(): EvColors =
    if (!isDark) this else copy(ink = PanelDark, onInk = Color(0xFFF5F5F8))
