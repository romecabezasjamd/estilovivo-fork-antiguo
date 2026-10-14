package com.estilovivo.app.core.data.model

import androidx.compose.runtime.Immutable

/** Prenda (producto del backend). Mismo mapeo que `mapProductToGarment` de la web. */
@Immutable
data class Garment(
    val id: String,
    val imageUrl: String,
    val name: String,
    /** Categoría: top, bottom, dress, outer, shoes, acc... */
    val type: String,
    val color: String,
    /** summer | winter | spring | autumn | transition | all */
    val season: String,
    val usageCount: Int,
    val lastWorn: String? = null,
    val forSale: Boolean = false,
    val price: Double = 0.0,
    val brand: String? = null,
    val size: String? = null,
    /** new | good | fair | worn */
    val condition: String = "new",
    val description: String? = null,
    val isWashing: Boolean = false,
    val userId: String? = null,
    val userName: String? = null,
    val userAvatar: String? = null,
    val createdAt: String? = null,
)

/** Datos editables de una prenda (alta o edición). [name] vacío => se usa [type]. */
@Immutable
data class GarmentDraft(
    val type: String,
    val name: String? = null,
    val color: String? = null,
    val season: String? = null,
    val brand: String? = null,
    val size: String? = null,
    val forSale: Boolean? = null,
    /** null = sin precio (en edición se envía vacío para borrar el precio). */
    val price: Double? = null,
    val condition: String? = null,
    val description: String? = null,
    val isWashing: Boolean? = null,
)

/** Borrador a partir de una prenda existente (úsalo con `.copy(isWashing = true)` para ediciones parciales). */
fun Garment.toDraft(): GarmentDraft = GarmentDraft(
    type = type, name = name, color = color, season = season, brand = brand, size = size,
    forSale = forSale, price = price.takeIf { it > 0.0 }, condition = condition,
    description = description, isWashing = isWashing,
)

@Immutable
data class Look(
    val id: String,
    val name: String,
    val garmentIds: List<String>,
    val garments: List<Garment>,
    val tags: List<String>,
    val mood: String? = null,
    val createdAt: String = "",
    val isPublic: Boolean = false,
    val imageUrl: String? = null,
    val userId: String? = null,
    val userName: String? = null,
    val userAvatar: String? = null,
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val isLiked: Boolean = false,
    val isFavorited: Boolean = false,
)

/** Cambios de un look (null = no tocar). */
@Immutable
data class LookChanges(
    val name: String? = null,
    val isPublic: Boolean? = null,
    val mood: String? = null,
    val garmentIds: List<String>? = null,
    val tags: List<String>? = null,
)

/** `date` en formato `YYYY-MM-DD`. */
@Immutable
data class PlannerEntry(
    val date: String,
    val lookId: String?,
    val look: Look? = null,
    val eventNote: String? = null,
)

@Immutable
data class TripItem(val id: String, val label: String, val checked: Boolean, val isEssential: Boolean)

@Immutable
data class Trip(
    val id: String,
    val destination: String,
    val dateStart: String,
    val dateEnd: String,
    val items: List<TripItem>,
    val garments: List<Garment>,
)

/** Elemento de la lista de equipaje al crear un viaje. */
@Immutable
data class NewTripItem(val label: String, val checked: Boolean = false, val isEssential: Boolean = false)

/** Prenda en venta en la tienda de la comunidad. */
@Immutable
data class ShopItem(
    val id: String,
    val user: String,
    val userId: String,
    val avatar: String,
    val image: String,
    val title: String,
    val price: Double,
    val size: String,
    val brand: String,
    val condition: String,
    /** Todas las fotos (la primera es [image]). */
    val images: List<String> = emptyList(),
    val color: String? = null,
    val description: String? = null,
    /** Categoría del servidor (top, bottom, dress, outerwear...). */
    val category: String = "",
    val createdAt: String = "",
)

/** Favorito: look o prenda (uno de los dos). */
@Immutable
data class Favorite(
    val id: String,
    val look: Look?,
    val garment: Garment?,
    val createdAt: String,
)

@Immutable
data class Stats(
    val garmentCount: Int,
    val lookCount: Int,
    val salesCount: Int,
    val tripsCount: Int,
    val totalUsage: Int,
    val mostWorn: Garment?,
    val leastWornCount: Int,
    val leastWorn: List<Garment>,
)

/** Capa del probador virtual (coordenadas normalizadas por la web: x/y/w/h en px sobre la foto del cuerpo). */
@Immutable
@kotlinx.serialization.Serializable
data class TryonLayer(
    @kotlinx.serialization.EncodeDefault val id: String = "",
    @kotlinx.serialization.EncodeDefault val garmentId: String = "",
    @kotlinx.serialization.EncodeDefault val url: String = "",
    @kotlinx.serialization.EncodeDefault val x: Float = 0f,
    @kotlinx.serialization.EncodeDefault val y: Float = 0f,
    @kotlinx.serialization.EncodeDefault val w: Float = 0f,
    @kotlinx.serialization.EncodeDefault val h: Float = 0f,
    @kotlinx.serialization.EncodeDefault val rotation: Float = 0f,
    @kotlinx.serialization.EncodeDefault val opacity: Float = 1f,
    @kotlinx.serialization.EncodeDefault val flipX: Boolean = false,
    @kotlinx.serialization.EncodeDefault val flipY: Boolean = false,
    @kotlinx.serialization.EncodeDefault val locked: Boolean = false,
)

@Immutable
data class TryonPreset(
    val id: String,
    val name: String,
    /** data URL (`data:image/jpeg;base64,...`) o URL absoluta. */
    val thumbnail: String?,
    val layers: List<TryonLayer>,
    val rating: Int?,
    val occasion: String?,
)

@Immutable
data class Trend(
    val id: String,
    val title: String,
    val category: String,
    val description: String,
    val image: String,
    val tags: List<String>,
    val source: String,
    val link: String?,
)
