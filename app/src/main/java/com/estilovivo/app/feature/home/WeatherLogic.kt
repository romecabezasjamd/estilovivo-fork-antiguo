package com.estilovivo.app.feature.home

import androidx.compose.runtime.Immutable

enum class WeatherCondition { Sunny, Cloudy, Rainy, Snowy, Windy, Hot, Cold }

@Immutable
data class WeatherData(
    val temp: Int,
    val condition: WeatherCondition,
    val humidity: Int,
    val description: String,
    val place: String,
    /** true si se devuelve una lectura anterior porque la red ha fallado. */
    val stale: Boolean = false,
)

/** Rangos de temperatura como `TEMP_RANGES` de la web (límite superior exclusivo). */
enum class TempRange { VeryHot, Hot, Warm, Mild, Cool, Cold, Freezing }

@Immutable
data class OutfitTip(val category: String, val reason: String, val essential: Boolean)

object WeatherLogic {
    fun tempRange(temp: Int): TempRange = when {
        temp >= 30 -> TempRange.VeryHot
        temp >= 25 -> TempRange.Hot
        temp >= 20 -> TempRange.Warm
        temp >= 15 -> TempRange.Mild
        temp >= 10 -> TempRange.Cool
        temp >= 0 -> TempRange.Cold
        else -> TempRange.Freezing
    }

    /**
     * Código WMO de Open-Meteo -> condición. Corrige a la web: la niebla ya no cuenta como "despejado" y
     * calor/frío extremos sólo cambian el icono cuando no llueve ni nieva.
     */
    fun condition(code: Int, temp: Double, windKmh: Double = 0.0): WeatherCondition {
        val base = when (code) {
            in 51..67, in 80..82, in 95..99 -> WeatherCondition.Rainy
            in 71..77, in 85..86 -> WeatherCondition.Snowy
            2, 3, 45, 48 -> WeatherCondition.Cloudy
            else -> WeatherCondition.Sunny
        }
        if (base != WeatherCondition.Sunny && base != WeatherCondition.Cloudy) return base
        return when {
            windKmh >= 40.0 -> WeatherCondition.Windy
            base == WeatherCondition.Sunny && temp > 30 -> WeatherCondition.Hot
            base == WeatherCondition.Sunny && temp < 5 -> WeatherCondition.Cold
            else -> base
        }
    }

    fun description(code: Int): String = when {
        code == 0 -> "Despejado"
        code == 1 -> "Poco nuboso"
        code in 2..3 -> "Nublado"
        code in 4..49 -> "Niebla"
        code in 50..57 -> "Llovizna"
        code in 58..67 -> "Lluvia"
        code in 68..77 -> "Nieve"
        code in 78..82 -> "Chubascos"
        code in 83..86 -> "Nevada"
        code in 87..99 -> "Tormenta"
        else -> "Despejado"
    }

    fun advice(temp: Int): String = when (tempRange(temp)) {
        TempRange.VeryHot -> "Hace mucho calor. Usa ropa ligera y transpirable."
        TempRange.Hot -> "Calor agradable. La ropa ligera es ideal."
        TempRange.Warm -> "Temperatura agradable. Con una sola capa vas bien."
        TempRange.Mild -> "Temperatura fresca. Lleva una capa ligera."
        TempRange.Cool -> "Empieza a refrescar. Una chaqueta te vendrá bien."
        TempRange.Cold -> "Hace frío. Abrígate bien con capas."
        TempRange.Freezing -> "Bajo cero. Vístete por capas y no olvides los accesorios térmicos."
    }

    private fun tip(category: String, reason: String, essential: Boolean = true) = OutfitTip(category, reason, essential)

    private fun rulesFor(range: TempRange): List<OutfitTip> = when (range) {
        TempRange.VeryHot -> listOf(
            tip("top", "Camiseta ligera de algodón o lino"), tip("bottom", "Shorts o falda ligera"),
            tip("shoes", "Sandalias o zapatillas transpirables"), tip("acc", "Gafas de sol y protección solar", false),
        )
        TempRange.Hot -> listOf(
            tip("top", "Camiseta o camisa ligera"), tip("bottom", "Pantalón ligero o shorts"), tip("shoes", "Zapatillas cómodas"),
        )
        TempRange.Warm -> listOf(
            tip("top", "Camiseta o polo"), tip("bottom", "Vaquero ligero o pantalón chino"), tip("shoes", "Zapatillas o mocasines"),
        )
        TempRange.Mild -> listOf(
            tip("top", "Camisa o camiseta con una capa ligera"), tip("outer", "Chaqueta ligera o sudadera", false),
            tip("bottom", "Vaqueros o pantalón normal"), tip("shoes", "Zapatillas cerradas"),
        )
        TempRange.Cool -> listOf(
            tip("top", "Camisa con jersey o capa media"), tip("outer", "Chaqueta o blazer"),
            tip("bottom", "Vaqueros o pantalón largo"), tip("shoes", "Zapatillas cerradas o botas bajas"),
        )
        TempRange.Cold -> listOf(
            tip("top", "Jersey grueso"), tip("outer", "Chaqueta abrigada o parka"),
            tip("bottom", "Pantalón grueso o vaqueros oscuros"), tip("shoes", "Botas o zapatos cerrados"),
            tip("acc", "Bufanda y guantes", false),
        )
        TempRange.Freezing -> listOf(
            tip("top", "Capas térmicas y jersey grueso"), tip("outer", "Chaqueta de invierno o abrigo"),
            tip("bottom", "Pantalón térmico o vaqueros gruesos"), tip("shoes", "Botas impermeables"),
            tip("acc", "Bufanda, guantes y gorro"),
        )
    }

    private fun rulesFor(condition: WeatherCondition): List<OutfitTip> = when (condition) {
        WeatherCondition.Rainy -> listOf(
            tip("outer", "Chaqueta impermeable o de lluvia"), tip("shoes", "Zapatos impermeables o botas"),
            tip("acc", "Paraguas", false),
        )
        WeatherCondition.Snowy -> listOf(
            tip("outer", "Chaqueta térmica impermeable"), tip("shoes", "Botas con buen agarre"),
            tip("acc", "Guantes, bufanda y gorro"),
        )
        WeatherCondition.Windy -> listOf(tip("outer", "Chaqueta que corte el viento"))
        else -> emptyList()
    }

    /** Consejos de prenda (los de condición se añaden si su categoría no está ya, como en la web). */
    fun tips(temp: Int, condition: WeatherCondition): List<OutfitTip> {
        val result = rulesFor(tempRange(temp)).toMutableList()
        rulesFor(condition).forEach { extra -> if (result.none { it.category == extra.category }) result += extra }
        return result
    }

    /** Hasta 3 consejos esenciales, como la sugerencia del widget de la web. */
    fun essentialTips(temp: Int, condition: WeatherCondition): List<OutfitTip> =
        tips(temp, condition).filter { it.essential }.take(3)

    fun seasonForTemp(temp: Int): String = when {
        temp >= 25 -> "summer"
        temp >= 15 -> "transition"
        else -> "winter"
    }
}
