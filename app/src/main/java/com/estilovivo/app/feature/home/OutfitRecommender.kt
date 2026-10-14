package com.estilovivo.app.feature.home

import androidx.compose.runtime.Immutable
import com.estilovivo.app.core.data.model.Garment
import java.time.LocalDate
import java.util.Locale
import kotlin.random.Random

/** Hueco que ocupa una prenda dentro de un outfit. */
enum class Slot { Top, Bottom, Full, Outer, Shoes, Accessory }

/** Un outfit propuesto. [key] identifica la combinación (ids ordenados) para saber si ya está guardada. */
@Immutable
data class Outfit(val garments: List<Garment>, val score: Double, val reasons: List<String>) {
    val key: String get() = garments.map { it.id }.sorted().joinToString("+")
}

@Immutable
data class RecommenderInput(
    val garments: List<Garment>,
    val mood: String?,
    /** null = sin datos del tiempo. */
    val temp: Int?,
    val condition: WeatherCondition?,
    val today: LocalDate,
)

sealed interface RecommendResult {
    data class Ready(val outfits: List<Outfit>) : RecommendResult

    /** Menos de 2 prendas utilizables. */
    data object NotEnoughGarments : RecommendResult

    /** Hay prendas pero no se puede formar un outfit (p. ej. sólo camisetas). */
    data class NoCombination(val message: String) : RecommendResult
}

/**
 * Recomendador de outfits (equivalente funcional a `useLookRecommender` + `moodRecommendations` de la web, pero
 * sobre las prendas): mood + temperatura + temporada + colores combinables + prendas menos usadas.
 * Es determinista para una misma semilla.
 */
object OutfitRecommender {
    private data class Profile(val colors: List<String>, val avoid: List<String>)

    private val profiles = mapOf(
        "confident" to Profile(listOf("rojo", "negro", "dorado", "blanco", "burdeos", "red", "black"), emptyList()),
        "Sport" to Profile(listOf("negro", "gris", "blanco", "azul", "verde"), emptyList()),
        "creative" to Profile(listOf("morado", "naranja", "rosa", "amarillo", "turquesa", "multicolor", "estampado"), emptyList()),
        "relaxed" to Profile(listOf("beige", "verde", "azul claro", "blanco", "gris", "crema"), listOf("negro")),
        "powerful" to Profile(listOf("negro", "gris oscuro", "azul marino", "blanco", "burdeos"), emptyList()),
        "elegant" to Profile(listOf("negro", "blanco", "beige", "dorado", "plateado", "burdeos", "crema"), listOf("neón", "neon", "multicolor")),
        "casual" to Profile(listOf("azul", "verde", "beige", "gris", "blanco", "vaquero", "denim"), emptyList()),
    )

    // ── Clasificación ──
    private val reTop = Regex("camis|polo|shirt|blusa|\\btop\\b|jersey|suéter|sueter|sweater|body|sudadera|hoodie|cardigan|chaleco")
    private val reBottom = Regex("pantal|jean|vaquer|falda|short|trouser|bermuda|legging|malla|jogger|chino")
    private val reOuter = Regex("chaqueta|abrigo|\\bsaco\\b|jacket|coat|parka|blazer|gabardina|cazadora|anorak|chubasquero|impermeable")
    private val reFull = Regex("vestido|dress|enterizo|mono\\b|jumpsuit|conjunto")
    private val reShoes = Regex("zapat|bota|sandal|shoe|boot|mocas|tenis|zapatill|sneaker|deportiv")
    private val reAcc = Regex("gorr|sombr|bolso|gafas|bufand|guant|paraguas|reloj|cintur|collar|pendient|pañuel")

    fun slotOf(g: Garment): Slot? {
        val type = g.type.lowercase(Locale.ROOT)
        val text = "$type ${g.name.lowercase(Locale.ROOT)}"
        return when (type) {
            "top" -> Slot.Top
            "bottom" -> Slot.Bottom
            "dress", "set" -> Slot.Full
            "outer", "outerwear" -> Slot.Outer
            "shoes" -> Slot.Shoes
            "acc", "accessory", "accessories" -> Slot.Accessory
            "swimwear" -> null
            "activewear" -> if (reBottom.containsMatchIn(text)) Slot.Bottom else Slot.Top
            else -> when {
                reFull.containsMatchIn(text) -> Slot.Full
                reOuter.containsMatchIn(text) -> Slot.Outer
                reShoes.containsMatchIn(text) -> Slot.Shoes
                reBottom.containsMatchIn(text) -> Slot.Bottom
                reTop.containsMatchIn(text) -> Slot.Top
                reAcc.containsMatchIn(text) -> Slot.Accessory
                else -> null
            }
        }
    }

    // ── Color ──
    private enum class Family { Neutral, Red, Pink, Orange, Yellow, Green, Blue, Purple, Turquoise, Brown, Multi, Unknown }

    private fun familyOf(color: String): Family {
        val c = color.lowercase(Locale.ROOT).trim()
        if (c.isBlank() || c in setOf("sin color", "varios", "n/a")) return Family.Unknown
        return when {
            Regex("multi|estamp|print|cuadros|rayas|flores|leopardo").containsMatchIn(c) -> Family.Multi
            Regex("negro|black|blanco|white|gris|gray|grey|beige|crema|hueso|marfil|plata|silver|dorado|gold|camel|marino|navy|vaquero|denim|topo").containsMatchIn(c) -> Family.Neutral
            Regex("rojo|red|burdeos|granate|vino|coral").containsMatchIn(c) -> Family.Red
            Regex("rosa|pink|fucsia|magenta|salmón|salmon").containsMatchIn(c) -> Family.Pink
            Regex("naranja|orange|mostaza|teja|terracota").containsMatchIn(c) -> Family.Orange
            Regex("amarillo|yellow|dorad").containsMatchIn(c) -> Family.Yellow
            Regex("turquesa|turquoise|aqua|cian|menta").containsMatchIn(c) -> Family.Turquoise
            Regex("verde|green|oliva|kaki|khaki|lima").containsMatchIn(c) -> Family.Green
            Regex("azul|blue|celeste|cobalto").containsMatchIn(c) -> Family.Blue
            Regex("morado|violeta|purple|lila|lavanda|púrpura|purpura").containsMatchIn(c) -> Family.Purple
            Regex("marr|brown|chocolate|cafe|café|tostado").containsMatchIn(c) -> Family.Brown
            else -> Family.Unknown
        }
    }

    private val analogous = setOf(
        Family.Red to Family.Pink, Family.Red to Family.Orange, Family.Orange to Family.Yellow, Family.Yellow to Family.Green,
        Family.Green to Family.Blue, Family.Blue to Family.Purple, Family.Purple to Family.Pink, Family.Green to Family.Turquoise,
        Family.Blue to Family.Turquoise, Family.Brown to Family.Orange, Family.Brown to Family.Green, Family.Brown to Family.Yellow,
        Family.Brown to Family.Red, Family.Brown to Family.Blue,
    ).flatMap { listOf(it, it.second to it.first) }.toSet()

    /** Compatibilidad entre dos colores: neutros con todo, mismo tono bien, tonos análogos bien, el resto choca. */
    internal fun harmony(a: Garment, b: Garment, mood: String?): Double {
        val fa = familyOf(a.color)
        val fb = familyOf(b.color)
        val neutral = setOf(Family.Neutral, Family.Unknown)
        return when {
            fa in neutral || fb in neutral -> 0.6
            fa == Family.Multi && fb == Family.Multi -> -2.0
            fa == Family.Multi || fb == Family.Multi -> if (mood == "creative") -0.4 else -1.5
            fa == fb -> 0.4
            (fa to fb) in analogous -> 0.2
            mood == "creative" -> -0.4
            else -> -1.5
        }
    }

    private fun colorMood(g: Garment, mood: String?): Double {
        val p = profiles[mood] ?: return 0.0
        val c = g.color.lowercase(Locale.ROOT)
        if (c.isBlank()) return 0.0
        if (p.avoid.any { c.contains(it) }) return -2.0
        return if (p.colors.any { c.contains(it) }) 1.5 else 0.0
    }

    private fun garmentSeason(g: Garment): String = when (g.season.lowercase(Locale.ROOT)) {
        "spring", "autumn", "fall", "transition" -> "transition"
        "summer" -> "summer"
        "winter" -> "winter"
        else -> "all"
    }

    private fun calendarSeason(d: LocalDate): String = when (d.monthValue) {
        6, 7, 8, 9 -> "summer"
        11, 12, 1, 2 -> "winter"
        else -> "transition"
    }

    /** null = incompatible (prenda de verano con frío o de invierno con calor); si no, puntos. */
    private fun seasonScore(g: Garment, target: String): Double? {
        val s = garmentSeason(g)
        return when {
            s == "all" -> 0.4
            s == "transition" -> if (target == "transition") 1.5 else 0.8
            s == target -> 2.0
            else -> null
        }
    }

    private fun parseDay(raw: String?): LocalDate? =
        raw?.takeIf { it.length >= 10 }?.let { runCatching { LocalDate.parse(it.substring(0, 10)) }.getOrNull() }

    private fun usable(g: Garment) = !g.isWashing && !g.forSale && g.imageUrl.isNotBlank()

    /** Prendas sueltas sugeridas para hoy (mood + tiempo), como el bloque "Para hoy · Mood" de la web. */
    fun moodPicks(input: RecommenderInput, limit: Int = 6): List<Garment> {
        val target = input.temp?.let(WeatherLogic::seasonForTemp) ?: calendarSeason(input.today)
        return input.garments.asSequence().filter(::usable)
            .filter { slotOf(it) != null }
            .mapNotNull { g -> seasonScore(g, target)?.let { g to (it + colorMood(g, input.mood)) } }
            .sortedWith(compareByDescending<Pair<Garment, Double>> { it.second }.thenBy { it.first.id })
            .take(limit).map { it.first }.toList()
    }

    /** Genera hasta [maxOutfits] propuestas distintas, ordenadas por puntuación (con ligera variación de [random]). */
    fun recommend(input: RecommenderInput, random: Random, maxOutfits: Int = 8): RecommendResult {
        val pool = input.garments.filter(::usable).distinctBy { it.id }
        if (pool.size < 2) return RecommendResult.NotEnoughGarments

        val target = input.temp?.let(WeatherLogic::seasonForTemp) ?: calendarSeason(input.today)
        val maxUsage = (pool.maxOfOrNull { it.usageCount } ?: 0).coerceAtLeast(1)
        val sport = input.mood == "Sport"

        // Puntuación individual (jitter calculado una vez por prenda => determinista por semilla).
        val scored = pool.mapNotNull { g ->
            val slot = slotOf(g) ?: return@mapNotNull null
            if (g.type.equals("activewear", true) && !sport) return@mapNotNull null
            val season = seasonScore(g, target) ?: return@mapNotNull null
            var s = season + colorMood(g, input.mood)
            s += 1.2 * (1.0 - g.usageCount.toDouble() / (maxUsage + 1)) + if (g.usageCount == 0) 0.4 else 0.0
            val worn = parseDay(g.lastWorn)
            if (worn != null && !worn.isBefore(input.today.minusDays(1))) s -= 1.5
            s += weatherBonus(g, slot, input)
            s += random.nextDouble() * 0.6
            Triple(g, slot, s)
        }.sortedWith(compareByDescending<Triple<Garment, Slot, Double>> { it.third }.thenBy { it.first.id })

        fun top(slot: Slot, n: Int) = scored.filter { it.second == slot }.take(n)
        val tops = top(Slot.Top, 6)
        val bottoms = top(Slot.Bottom, 6)
        val fulls = top(Slot.Full, 6)
        val cores = ArrayList<Pair<List<Triple<Garment, Slot, Double>>, Double>>()
        for (f in fulls) cores += listOf(f) to 0.3
        for (t in tops) for (b in bottoms) cores += listOf(t, b) to 0.0
        if (cores.isEmpty()) {
            return RecommendResult.NoCombination(
                "Para formar un look necesitas una parte de arriba y una de abajo (o un vestido) aptas para esta temporada.",
            )
        }

        val temp = input.temp
        val wet = input.condition == WeatherCondition.Rainy || input.condition == WeatherCondition.Snowy
        val needOuter = (temp != null && temp < 15) || wet || input.condition == WeatherCondition.Windy
        val wantOuter = needOuter || (temp != null && temp in 15..19)
        val outers = if (wantOuter) top(Slot.Outer, 3) else emptyList()
        val shoes = top(Slot.Shoes, 3)
        val wantAcc = (temp != null && (temp < 10 || temp >= 25)) ||
            input.mood in setOf("elegant", "creative", "powerful", "confident")
        val accs = if (wantAcc) top(Slot.Accessory, 2) else emptyList()

        data class Candidate(val pieces: List<Triple<Garment, Slot, Double>>, val score: Double)

        val candidates = ArrayList<Candidate>()
        val outerOptions: List<Triple<Garment, Slot, Double>?> = if (outers.isEmpty()) listOf(null) else if (needOuter) outers else outers + listOf(null)
        val shoeOptions: List<Triple<Garment, Slot, Double>?> = if (shoes.isEmpty()) listOf(null) else shoes
        val accOptions: List<Triple<Garment, Slot, Double>?> = listOf<Triple<Garment, Slot, Double>?>(null) + accs
        for ((core, coreBonus) in cores) for (o in outerOptions) for (sh in shoeOptions) for (ac in accOptions) {
            val pieces = core + listOfNotNull(o, sh, ac)
            var score = coreBonus + pieces.sumOf { it.third }
            // Armonía de color entre todas las parejas.
            for (i in pieces.indices) for (j in i + 1 until pieces.size) {
                score += harmony(pieces[i].first, pieces[j].first, input.mood)
            }
            if (needOuter && o == null && outers.isEmpty()) score -= 1.0 // hace falta abrigo y no hay
            if (sh == null) score -= 0.8
            candidates += Candidate(pieces, score)
        }
        candidates.sortWith(compareByDescending<Candidate> { it.score }.thenBy { c -> c.pieces.joinToString { it.first.id } })

        // Diversidad: primero una propuesta por núcleo (top+bottom / vestido), luego variantes.
        val chosen = ArrayList<Candidate>()
        val usedCores = HashSet<String>()
        fun coreKey(c: Candidate) = c.pieces.filter { it.second == Slot.Top || it.second == Slot.Bottom || it.second == Slot.Full }
            .joinToString("+") { it.first.id }
        for (c in candidates) if (chosen.size < maxOutfits && usedCores.add(coreKey(c))) chosen += c
        if (chosen.size < 3) {
            val seen = chosen.map { c -> c.pieces.map { it.first.id }.toSet() }.toMutableList()
            for (c in candidates) {
                if (chosen.size >= maxOutfits.coerceAtMost(4)) break
                val ids = c.pieces.map { it.first.id }.toSet()
                if (seen.none { it == ids }) { chosen += c; seen += ids }
            }
        }

        val outfits = chosen.map { c ->
            Outfit(
                garments = c.pieces.sortedBy { order(it.second) }.map { it.first },
                score = c.score,
                reasons = reasonsFor(c.pieces.map { it.first }, input),
            )
        }
        return RecommendResult.Ready(outfits)
    }

    private fun order(slot: Slot) = when (slot) {
        Slot.Outer -> 0; Slot.Top -> 1; Slot.Full -> 1; Slot.Bottom -> 2; Slot.Shoes -> 3; Slot.Accessory -> 4
    }

    private fun weatherBonus(g: Garment, slot: Slot, input: RecommenderInput): Double {
        val text = "${g.type} ${g.name}".lowercase(Locale.ROOT)
        val temp = input.temp
        var b = 0.0
        if (input.condition == WeatherCondition.Rainy) {
            if (slot == Slot.Outer && Regex("impermeable|rain|parka|chubasquero|gabardina|anorak").containsMatchIn(text)) b += 1.5
            if (slot == Slot.Shoes && Regex("bota|boot|impermeable|rain").containsMatchIn(text)) b += 1.0
            if (slot == Slot.Shoes && Regex("sandal|alpargat|tela|lona").containsMatchIn(text)) b -= 1.5
        }
        if (temp != null) {
            if (temp >= 25 && slot == Slot.Shoes && Regex("sandal|alpargat|zapatill|tenis").containsMatchIn(text)) b += 0.8
            if (temp >= 25 && slot == Slot.Shoes && Regex("bota|boot").containsMatchIn(text)) b -= 1.2
            if (temp < 12 && slot == Slot.Shoes && Regex("sandal|alpargat").containsMatchIn(text)) b -= 2.0
            if (temp < 12 && (slot == Slot.Top) && Regex("jersey|suéter|sueter|sweater|sudadera|lana").containsMatchIn(text)) b += 0.8
            if (temp >= 25 && (slot == Slot.Top || slot == Slot.Full) && Regex("lino|algod|ligera|tirantes|manga corta").containsMatchIn(text)) b += 0.6
            if (temp >= 25 && slot == Slot.Bottom && Regex("short|bermuda|falda|lino").containsMatchIn(text)) b += 0.8
        }
        return b
    }

    private fun reasonsFor(pieces: List<Garment>, input: RecommenderInput): List<String> {
        val reasons = ArrayList<String>()
        val mood = Moods.byId(input.mood)
        if (mood != null && pieces.count { colorMood(it, input.mood) > 0 } >= 1) reasons += "Va con tu mood ${mood.emoji}"
        input.temp?.let { reasons += "Pensado para ${it}°C" }
        if (pieces.any { it.usageCount == 0 }) reasons += "Estrena prendas sin usar"
        else if (pieces.map { it.usageCount }.average() < 3.0) reasons += "Prendas poco usadas"
        var clash = false
        for (i in pieces.indices) for (j in i + 1 until pieces.size) if (harmony(pieces[i], pieces[j], input.mood) < 0) clash = true
        if (!clash) reasons += "Colores que combinan"
        return reasons.take(3)
    }
}
