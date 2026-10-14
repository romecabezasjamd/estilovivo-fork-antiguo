package com.estilovivo.app.feature.wardrobe

import androidx.compose.runtime.Immutable
import com.estilovivo.app.core.data.model.Garment
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Opción con id (el del servidor) y etiqueta en español. */
@Immutable
data class Option(val id: String, val label: String)

@Immutable
data class Mood(val id: String, val emoji: String, val label: String)

object WardrobeCatalog {
    const val ALL = "all"

    /** Categorías que se pueden asignar a una prenda. */
    val categories: List<Option> = listOf(
        Option("top", "Tops"),
        Option("bottom", "Bottoms"),
        Option("shoes", "Zapatos"),
        Option("outerwear", "Abrigos"),
        Option("accessories", "Accesorios"),
        Option("dress", "Vestidos"),
        Option("set", "Conjuntos"),
        Option("swimwear", "Traje de baño"),
        Option("activewear", "Ropa deportiva"),
    )

    /** Orden de las capas en un look (de arriba abajo). */
    val lookOrder: List<String> = listOf("top", "outerwear", "dress", "set", "bottom", "shoes", "accessories", "swimwear", "activewear")

    val seasons: List<Option> = listOf(
        Option("all", "Todo el año"),
        Option("spring", "Primavera"),
        Option("summer", "Verano"),
        Option("autumn", "Otoño"),
        Option("winter", "Invierno"),
        Option("transition", "Entretiempo"),
    )

    /** Estados de venta (ids del servidor; etiquetas como en la web). */
    val conditions: List<Option> = listOf(
        Option("new", "Nuevo"),
        Option("good", "Buen estado"),
        Option("fair", "Como nuevo"),
        Option("worn", "Usado"),
    )

    val moods: List<Mood> = listOf(
        Mood("happy", "😊", "Feliz"),
        Mood("bold", "🔥", "Atrevido"),
        Mood("chill", "🌿", "Relajado"),
        Mood("romantic", "🌸", "Romántico"),
        Mood("elegant", "✨", "Elegante"),
        Mood("casual", "☕", "Casual"),
        Mood("sport", "👟", "Deportivo"),
    )

    val suggestedColors: List<String> = listOf(
        "negro", "blanco", "gris", "beige", "marrón", "rojo", "rosa", "naranja", "amarillo", "verde", "azul", "morado",
    )

    fun categoryLabel(id: String): String = categories.firstOrNull { it.id == id }?.label ?: id.replaceFirstChar { it.uppercase() }
    fun seasonLabel(id: String): String = seasons.firstOrNull { it.id == id }?.label ?: id
    fun conditionLabel(id: String): String = conditions.firstOrNull { it.id == id }?.label ?: id
    fun moodOf(id: String?): Mood? = moods.firstOrNull { it.id == id }

    private val SHOES = Regex("shoe|zapat|bota|sandal|boot|sneaker")
    private val DRESS = Regex("dress|vestido|enterizo|\\bmono\\b")
    private val OUTER = Regex("outer|chaqueta|abrigo|saco|jacket|coat|blazer|cazadora")
    private val ACC = Regex("accesorio|\\bacc\\b|sombrero|gorra|bolso|gafas|collar|cinturon|bufanda")
    private val SWIM = Regex("bano|swim|bikini")
    private val SET = Regex("conjunto|\\bset\\b")
    private val ACTIVE = Regex("deport|sport|activ|legging")
    private val BOTTOM = Regex("\\bbottom|pantal|falda|short|jean|trouser|vaquero")
    private val TOP = Regex("\\btop|camis|blusa|shirt|polo|sweater|jersey|sudadera")

    /**
     * Categoría canónica de una prenda. El servidor guarda texto libre (datos antiguos: "camiseta", "outer"...),
     * así que se normaliza igual para filtrar y para contar (la web contaba con regex pero filtraba por igualdad).
     */
    fun categoryOf(type: String?): String {
        val t = fold(type.orEmpty())
        if (t.isEmpty()) return "top"
        categories.firstOrNull { it.id == t }?.let { return it.id }
        return when {
            SWIM.containsMatchIn(t) -> "swimwear"
            SHOES.containsMatchIn(t) -> "shoes"
            DRESS.containsMatchIn(t) -> "dress"
            OUTER.containsMatchIn(t) -> "outerwear"
            ACC.containsMatchIn(t) -> "accessories"
            SET.containsMatchIn(t) -> "set"
            ACTIVE.containsMatchIn(t) -> "activewear"
            BOTTOM.containsMatchIn(t) -> "bottom"
            TOP.containsMatchIn(t) -> "top"
            else -> "top"
        }
    }

    /** Minúsculas y sin tildes: búsquedas y colores comparables. */
    fun fold(s: String): String =
        Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
}

enum class SortOrder(val label: String) {
    Recent("Recientes"), MostUsed("Más usadas"), LeastUsed("Menos usadas")
}

/** Filtros de la vista Armario. */
@Immutable
data class WardrobeFilters(
    val query: String = "",
    val category: String = WardrobeCatalog.ALL,
    val season: String = WardrobeCatalog.ALL,
    val color: String = WardrobeCatalog.ALL,
    val sort: SortOrder = SortOrder.Recent,
) {
    /** Filtros "avanzados" activos (los del bottom sheet). */
    val advancedActive: Boolean get() = season != WardrobeCatalog.ALL || color != WardrobeCatalog.ALL || sort != SortOrder.Recent

    fun encode(): String = listOf(query, category, season, color, sort.name).joinToString("\u0001")

    companion object {
        fun decode(raw: String?): WardrobeFilters? {
            val p = raw?.split("\u0001") ?: return null
            if (p.size != 5) return null
            return WardrobeFilters(p[0], p[1], p[2], p[3], SortOrder.entries.firstOrNull { it.name == p[4] } ?: SortOrder.Recent)
        }
    }
}

object WardrobeLogic {
    /** Prendas visibles en el armario (las de la lavadora quedan fuera) tras los filtros. */
    fun closet(all: List<Garment>, f: WardrobeFilters, ignoreCategory: Boolean = false): List<Garment> {
        val q = WardrobeCatalog.fold(f.query)
        val color = WardrobeCatalog.fold(f.color)
        val filtered = all.asSequence()
            .filter { !it.isWashing }
            .filter { ignoreCategory || f.category == WardrobeCatalog.ALL || WardrobeCatalog.categoryOf(it.type) == f.category }
            .filter { f.season == WardrobeCatalog.ALL || it.season == f.season }
            .filter { f.color == WardrobeCatalog.ALL || WardrobeCatalog.fold(it.color) == color }
            .filter { g ->
                q.isEmpty() || listOf(
                    g.name, g.type, WardrobeCatalog.categoryLabel(WardrobeCatalog.categoryOf(g.type)), g.color, g.brand.orEmpty(),
                ).any { WardrobeCatalog.fold(it).contains(q) }
            }
            .toList()
        return when (f.sort) {
            SortOrder.Recent -> filtered
            SortOrder.MostUsed -> filtered.sortedByDescending { it.usageCount }
            SortOrder.LeastUsed -> filtered.sortedBy { it.usageCount }
        }
    }

    /** Contador por categoría coherente con lo que se verá en la rejilla (clave [WardrobeCatalog.ALL] = total). */
    fun categoryCounts(all: List<Garment>, f: WardrobeFilters): Map<String, Int> {
        val base = closet(all, f, ignoreCategory = true)
        val counts = HashMap<String, Int>()
        counts[WardrobeCatalog.ALL] = base.size
        base.forEach { g -> WardrobeCatalog.categoryOf(g.type).let { counts[it] = (counts[it] ?: 0) + 1 } }
        return counts
    }

    fun availableColors(all: List<Garment>): List<String> =
        all.mapNotNull { WardrobeCatalog.fold(it.color).takeIf { c -> c.isNotEmpty() } }.distinct().sorted()

    fun washing(all: List<Garment>): List<Garment> = all.filter { it.isWashing }

    fun sales(all: List<Garment>): List<Garment> = all.filter { it.forSale }

    fun salesTotal(sales: List<Garment>): Double = sales.sumOf { it.price }

    /** "Sorpréndeme": un look completo (vestido o top + bottom), con abrigo/accesorio opcional y calzado. */
    fun surprise(all: List<Garment>, random: kotlin.random.Random = kotlin.random.Random.Default): List<Garment> {
        val by = all.groupBy { WardrobeCatalog.categoryOf(it.type) }
        fun pick(cat: String): Garment? = by[cat]?.takeIf { it.isNotEmpty() }?.let { it[random.nextInt(it.size)] }
        val picks = ArrayList<Garment>()
        val dress = pick("dress")
        val top = pick("top")
        val bottom = pick("bottom")
        val set = pick("set")
        when {
            dress != null && (top == null || bottom == null || random.nextBoolean()) -> picks += dress
            set != null && (top == null || bottom == null) -> picks += set
            else -> {
                top?.let(picks::add)
                bottom?.let(picks::add)
            }
        }
        if (random.nextBoolean()) pick("outerwear")?.let(picks::add)
        pick("shoes")?.let(picks::add)
        if (random.nextBoolean()) pick("accessories")?.let(picks::add)
        return picks
    }

    /** Orden de capas para componer la vista previa de un look. */
    fun lookOrder(items: List<Garment>): List<Garment> =
        items.sortedBy { WardrobeCatalog.lookOrder.indexOf(WardrobeCatalog.categoryOf(it.type)).let { i -> if (i == -1) 99 else i } }

    /** "Hoy", "Ayer", "Hace 3 días"... a partir de una fecha ISO (`lastWorn`). */
    fun lastWornLabel(iso: String?, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): String? {
        if (iso.isNullOrBlank()) return null
        val date = runCatching { Instant.parse(iso).atZone(zone).toLocalDate() }.getOrNull()
            ?: runCatching { LocalDate.parse(iso.take(10)) }.getOrNull()
            ?: return null
        val days = ChronoUnit.DAYS.between(date, today)
        return when {
            days <= 0 -> "Hoy"
            days == 1L -> "Ayer"
            days < 7 -> "Hace $days días"
            days < 30 -> "Hace ${days / 7} ${if (days / 7 == 1L) "semana" else "semanas"}"
            days < 365 -> "Hace ${days / 30} ${if (days / 30 == 1L) "mes" else "meses"}"
            else -> "Hace más de un año"
        }
    }
}
