package com.estilovivo.app.feature.social.shop

import androidx.compose.runtime.Immutable
import com.estilovivo.app.core.data.model.ShopItem
import com.estilovivo.app.core.design.components.ProductCardUi
import java.util.Locale

/** Categorías del servidor (mismos ids que el armario de la web) con su etiqueta. */
@Immutable
data class ShopCategory(val id: String, val label: String)

val ShopCategories = listOf(
    ShopCategory("all", "Todo"),
    ShopCategory("top", "Tops"),
    ShopCategory("bottom", "Pantalones"),
    ShopCategory("dress", "Vestidos"),
    ShopCategory("outerwear", "Abrigos"),
    ShopCategory("shoes", "Zapatos"),
    ShopCategory("accessories", "Accesorios"),
    ShopCategory("set", "Conjuntos"),
    ShopCategory("swimwear", "Baño"),
    ShopCategory("activewear", "Deporte"),
)

fun categoryLabel(id: String): String =
    ShopCategories.firstOrNull { it.id == id }?.label ?: id.replaceFirstChar { it.uppercase() }

/** Estados de conservación del servidor y su texto (igual que el armario de la web). */
val ConditionOptions = listOf("new" to "Nuevo", "good" to "Buen estado", "fair" to "Como nuevo", "worn" to "Usado")

fun conditionLabel(raw: String): String = ConditionOptions.firstOrNull { it.first == raw }?.second ?: raw

/** `45 €`, `45,50 €`. Determinista (no depende del idioma del dispositivo). */
fun formatEuro(price: Double): String {
    val cents = Math.round(price * 100)
    return if (cents % 100 == 0L) "${cents / 100} €"
    else String.format(Locale.US, "%.2f", cents / 100.0).replace('.', ',') + " €"
}

/** Filtros del bottom sheet. Los vacíos (null) no filtran. */
@Immutable
data class ShopFilters(
    val minPrice: Double? = null,
    val maxPrice: Double? = null,
    val condition: String? = null,
    val size: String? = null,
) {
    val activeCount: Int
        get() = listOf(minPrice, maxPrice, condition, size).count { it != null }
    val isEmpty: Boolean get() = activeCount == 0
}

internal fun isUniqueSize(size: String) =
    size.isBlank() || size.equals("Única", ignoreCase = true) || size.equals("Unica", ignoreCase = true)

internal fun List<ShopItem>.applyFilters(filters: ShopFilters, favoritesOnly: Boolean, favoriteIds: Set<String>): List<ShopItem> =
    filter { item ->
        (!favoritesOnly || item.id in favoriteIds) &&
            (filters.minPrice == null || item.price >= filters.minPrice) &&
            (filters.maxPrice == null || item.price <= filters.maxPrice) &&
            (filters.condition == null || item.condition == filters.condition) &&
            (filters.size == null || item.size.equals(filters.size, ignoreCase = true))
    }

private val SIZE_ORDER = listOf("XXS", "XS", "S", "M", "L", "XL", "XXL", "XXXL")

/** Tallas presentes en los resultados, de la más pequeña a la más grande (letras primero, luego números). */
internal fun List<ShopItem>.availableSizes(): List<String> =
    map { it.size.trim() }
        .filterNot { isUniqueSize(it) }
        .distinctBy { it.uppercase() }
        .sortedWith(
            compareBy<String> { if (SIZE_ORDER.indexOf(it.uppercase()) >= 0) 0 else 1 }
                .thenBy {
                    val i = SIZE_ORDER.indexOf(it.uppercase())
                    if (i >= 0) i.toDouble() else it.toDoubleOrNull() ?: Double.MAX_VALUE
                }
                .thenBy { it.lowercase() },
        )

/** Aspectos alternados para la rejilla escalonada (deterministas por id para que no salten al recomponer). */
private val ASPECTS = floatArrayOf(0.78f, 0.95f, 0.86f, 0.82f)

internal fun ShopItem.toCard(liked: Boolean): ProductCardUi {
    val meta = listOfNotNull(
        categoryLabel(category).takeIf { category.isNotBlank() },
        size.takeUnless { isUniqueSize(it) }?.let { "Talla $it" },
    )
    return ProductCardUi(
        id = id,
        title = title,
        subtitle = (meta + user).joinToString(" · "),
        price = formatEuro(price),
        image = image,
        liked = liked,
        aspect = ASPECTS[Math.floorMod(id.hashCode(), ASPECTS.size)],
    )
}
