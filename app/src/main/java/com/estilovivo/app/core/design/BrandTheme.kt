package com.estilovivo.app.core.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Tema de color por perfil. Sólo cambia el color de MARCA (botón primario, selección, indicador de la barra,
 * progreso, enlaces, interruptores); lienzo, tarjetas, texto, formas y movimiento no se tocan.
 * Todo el cálculo trabaja con enteros RGB (0xRRGGBB) para poder probarse en JVM sin Compose.
 */
object BrandPresets {
    const val CLASSIC = "classic"

    @Immutable
    data class Preset(val id: String, val label: String, val rgb: Int?)

    /** `rgb == null` en el clásico: la marca es la tinta (negro editorial). Los ids coinciden con la web. */
    val all: List<Preset> = listOf(
        Preset(CLASSIC, "Clásico", null),
        Preset("pink", "Rosa", 0xEC4899),
        Preset("blue", "Azul", 0x3B82F6),
        Preset("green", "Verde", 0x10B981),
        Preset("lavender", "Lavanda", 0x8B5CF6),
        Preset("amber", "Ámbar", 0xF59E0B),
        Preset("rose", "Rosé", 0xFB7185),
        Preset("indigo", "Índigo", 0x6366F1),
        Preset("teal", "Turquesa", 0x14B8A6),
        Preset("forest", "Bosque", 0x166534),
        Preset("midnight", "Medianoche", 0x1E293B),
        Preset("petrol", "Petróleo", 0x083344),
        Preset("coral", "Coral", 0xF43F5E),
    )

    fun find(id: String?): Preset? = id?.trim()?.lowercase()?.let { key -> all.firstOrNull { it.id == key } }

    /** Preset desconocido o vacío → clásico. */
    fun normalizeId(id: String?): String = find(id)?.id ?: CLASSIC
}

/** Elección guardada del usuario: preset + color propio opcional (gana si es válido, como en la web). */
@Immutable
data class BrandChoice(val preset: String = BrandPresets.CLASSIC, val customColor: String? = null) {
    /** Semilla RGB resuelta; null = clásico. Hex inválido → se ignora; preset desconocido → clásico. */
    val seed: Int?
        get() = BrandMath.parseHex(customColor) ?: BrandPresets.find(preset)?.rgb
}

/** Colores de marca ya derivados para un modo (claro/oscuro), como enteros RGB. */
@Immutable
data class BrandColors(
    val brand: Int,
    val onBrand: Int,
    val brandSoft: Int,
    val brandInk: Int,
    val brandOnInk: Int,
    val navBrand: Int,
    val onNavBrand: Int,
)

object BrandMath {
    private const val WHITE = 0xFFFFFF
    private const val BLACK = 0x000000
    private const val NAV_BG = 0x0B0B0F
    private val HEX = Regex("^#?([0-9a-fA-F]{6})$")

    /** `#RRGGBB` / `RRGGBB` → RGB. Cualquier otra cosa (incluido `#RGB`, alfa o vacío) → null. */
    fun parseHex(raw: String?): Int? = raw?.trim()?.let { HEX.matchEntire(it)?.groupValues?.get(1)?.toInt(16) }

    /** Normaliza a `#RRGGBB` en mayúsculas o null si no es válido. */
    fun normalizeHex(raw: String?): String? = parseHex(raw)?.let(::toHex)

    fun toHex(rgb: Int): String = "#%06X".format(rgb and 0xFFFFFF)

    private fun channel(c: Int): Double {
        val v = c / 255.0
        return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
    }

    fun luminance(rgb: Int): Double =
        0.2126 * channel((rgb shr 16) and 0xFF) + 0.7152 * channel((rgb shr 8) and 0xFF) + 0.0722 * channel(rgb and 0xFF)

    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** Texto sobre la marca: blanco o negro puro, el que mejor contraste dé (siempre ≥ 4,5:1). */
    fun onColor(bg: Int): Int = if (contrast(bg, WHITE) >= contrast(bg, BLACK)) WHITE else BLACK

    fun mix(from: Int, to: Int, t: Double): Int {
        fun ch(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * t).toInt().coerceIn(0, 255)
        }
        return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /**
     * Acerca [seed] al blanco (fondo oscuro) o al negro (fondo claro) en pasos pequeños hasta alcanzar [min]
     * de contraste con [bg]. Determinista: la misma entrada da siempre la misma salida.
     */
    fun fit(seed: Int, bg: Int, min: Double): Int {
        if (contrast(seed, bg) >= min) return seed
        val toward = if (luminance(bg) < 0.4) WHITE else BLACK
        var t = 0.0
        var out = seed
        while (t < 1.0 && contrast(out, bg) < min) {
            t += 0.02
            out = mix(seed, toward, t)
        }
        return out
    }

    /** Compone [fg] con opacidad [alpha] sobre [bg]. */
    fun over(fg: Int, bg: Int, alpha: Double): Int = mix(bg, fg, alpha)

    /**
     * Deriva la marca para un modo. [seed] null = clásico (tinta editorial, aspecto idéntico al original).
     * Claro: la semilla se oscurece sólo si hace falta para ≥3:1 sobre la tarjeta; oscuro: se aclara.
     */
    fun derive(seed: Int?, surface: Int, ink: Int, onInk: Int): BrandColors {
        if (seed == null) {
            return BrandColors(
                brand = ink, onBrand = onInk, brandSoft = over(ink, surface, 0.12), brandInk = ink,
                brandOnInk = onInk, navBrand = WHITE, onNavBrand = NAV_BG,
            )
        }
        val brand = fit(seed, surface, 3.0)
        val nav = fit(seed, NAV_BG, 3.0)
        return BrandColors(
            brand = brand,
            onBrand = onColor(brand),
            brandSoft = over(brand, surface, 0.12),
            brandInk = fit(seed, surface, 4.5),
            brandOnInk = fit(seed, ink, 3.0),
            navBrand = nav,
            onNavBrand = onColor(nav),
        )
    }

    /** Resultado de corregir un color propio. */
    data class Fix(val rgb: Int, val changed: Boolean)

    /**
     * Un color propio demasiado claro desaparece sobre las tarjetas blancas: se oscurece hasta 1,6:1 como mínimo
     * (en modo oscuro [derive] ya lo aclara por su cuenta). `changed` = hay que avisar al usuario.
     */
    fun fixCustom(rgb: Int): Fix {
        val fixed = fit(rgb, WHITE, 1.6)
        return Fix(fixed, fixed != rgb)
    }

    // ───────── HSV (selector de color propio) ─────────

    /** Devuelve (h 0..360, s 0..1, v 0..1). */
    fun toHsv(rgb: Int): Triple<Float, Float, Float> {
        val r = ((rgb shr 16) and 0xFF) / 255f
        val g = ((rgb shr 8) and 0xFF) / 255f
        val b = (rgb and 0xFF) / 255f
        val mx = maxOf(r, g, b)
        val mn = minOf(r, g, b)
        val d = mx - mn
        val h = when {
            d == 0f -> 0f
            mx == r -> 60f * (((g - b) / d) % 6f)
            mx == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }.let { if (it < 0f) it + 360f else it }
        return Triple(h, if (mx == 0f) 0f else d / mx, mx)
    }

    fun fromHsv(h: Float, s: Float, v: Float): Int {
        val hh = ((h % 360f) + 360f) % 360f
        val c = v * s
        val x = c * (1f - kotlin.math.abs((hh / 60f) % 2f - 1f))
        val m = v - c
        val (r, g, b) = when {
            hh < 60f -> Triple(c, x, 0f)
            hh < 120f -> Triple(x, c, 0f)
            hh < 180f -> Triple(0f, c, x)
            hh < 240f -> Triple(0f, x, c)
            hh < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun to8(f: Float) = Math.round((f + m) * 255f).coerceIn(0, 255)
        return (to8(r) shl 16) or (to8(g) shl 8) or to8(b)
    }
}

internal fun Int.toComposeColor(): Color = Color(0xFF000000.toInt() or this)

private fun Color.rgbInt(): Int = ((red * 255f + 0.5f).toInt() shl 16) or ((green * 255f + 0.5f).toInt() shl 8) or (blue * 255f + 0.5f).toInt()

/** Aplica la marca derivada a la paleta base (claro u oscuro). Con el clásico devuelve exactamente la tinta original. */
internal fun EvColors.withBrand(choice: BrandChoice): EvColors {
    val seed = choice.seed
    if (seed == null) {
        return copy(
            brand = ink, onBrand = onInk, brandSoft = ink.copy(alpha = 0.12f), brandInk = ink,
            brandOnInk = onInk, navBrand = Color.White, onNavBrand = Color(0xFF0B0B0F),
        )
    }
    val b = BrandMath.derive(seed, surface.rgbInt(), ink.rgbInt(), onInk.rgbInt())
    return copy(
        brand = b.brand.toComposeColor(), onBrand = b.onBrand.toComposeColor(), brandSoft = b.brandSoft.toComposeColor(),
        brandInk = b.brandInk.toComposeColor(), brandOnInk = b.brandOnInk.toComposeColor(),
        navBrand = b.navBrand.toComposeColor(), onNavBrand = b.onNavBrand.toComposeColor(),
    )
}
