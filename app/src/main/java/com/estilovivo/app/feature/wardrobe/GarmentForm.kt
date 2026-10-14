package com.estilovivo.app.feature.wardrobe

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.saveable.listSaver
import com.estilovivo.app.core.data.model.Garment
import com.estilovivo.app.core.data.model.GarmentDraft
import com.estilovivo.app.core.data.model.toDraft

/** Límites del servidor (`productSchema`). */
object GarmentLimits {
    const val NAME = 100
    const val COLOR = 50
    const val BRAND = 80
    const val SIZE = 20
    const val DESCRIPTION = 1000
    const val MAX_PRICE = 100_000.0
    const val MAX_IMAGE_BYTES = 10L * 1024 * 1024
    val IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp")
}

/** Formulario de prenda (alta, edición y venta). Todo en texto para sobrevivir a la muerte del proceso. */
@Immutable
data class GarmentForm(
    val name: String = "",
    val category: String = "top",
    val color: String = "",
    val season: String = "all",
    val brand: String = "",
    val size: String = "",
    val condition: String = "good",
    val description: String = "",
    val price: String = "",
    val forSale: Boolean = false,
    /** URI (content:// o file://) de la foto elegida; null = conserva la actual (edición). */
    val photoUri: String? = null,
) {
    companion object {
        val Saver = listSaver<GarmentForm, Any?>(
            save = { listOf(it.name, it.category, it.color, it.season, it.brand, it.size, it.condition, it.description, it.price, it.forSale, it.photoUri) },
            restore = {
                GarmentForm(
                    name = it[0] as String, category = it[1] as String, color = it[2] as String, season = it[3] as String,
                    brand = it[4] as String, size = it[5] as String, condition = it[6] as String, description = it[7] as String,
                    price = it[8] as String, forSale = it[9] as Boolean, photoUri = it[10] as String?,
                )
            },
        )

        /** Formulario de edición a partir de una prenda existente. */
        fun of(g: Garment) = GarmentForm(
            name = g.name, category = WardrobeCatalog.categoryOf(g.type), color = g.color.takeUnless { it == "varios" || it == "sin color" }.orEmpty(),
            season = g.season, brand = g.brand.orEmpty(), size = g.size.orEmpty(), condition = g.condition,
            description = g.description.orEmpty(), price = g.price.takeIf { it > 0 }?.let(::formatPriceInput).orEmpty(), forSale = g.forSale,
        )
    }
}

/** Errores por campo (null = correcto). */
@Immutable
data class FormErrors(val name: String? = null, val price: String? = null, val photo: String? = null) {
    val any: Boolean get() = name != null || price != null || photo != null
}

object GarmentFormRules {
    /** Interpreta "12,5" / "12.50". Devuelve null si no es un número finito. */
    fun parsePrice(raw: String): Double? {
        val v = raw.trim().replace(',', '.').toDoubleOrNull() ?: return null
        return if (v.isFinite()) v else null
    }

    /** Precio de venta válido: > 0 y ≤ 100.000, redondeado a céntimos. */
    fun validSalePrice(raw: String): Double? {
        val v = parsePrice(raw) ?: return null
        if (v <= 0 || v > GarmentLimits.MAX_PRICE) return null
        return Math.round(v * 100) / 100.0
    }

    const val PRICE_ERROR = "Introduce un precio válido mayor que 0 € (máx. 100.000 €)."
    const val NAME_ERROR = "Ponle un nombre a la prenda."
    const val PHOTO_ERROR = "Añade una foto de la prenda."

    /**
     * @param requirePhoto alta de prenda nueva (la foto es obligatoria).
     * @param requirePrice la prenda se pone en venta ahora.
     */
    fun validate(form: GarmentForm, requirePhoto: Boolean, requirePrice: Boolean): FormErrors = FormErrors(
        name = if (form.name.isBlank()) NAME_ERROR else null,
        price = if (requirePrice && validSalePrice(form.price) == null) PRICE_ERROR else null,
        photo = if (requirePhoto && form.photoUri == null) PHOTO_ERROR else null,
    )

    /** Mensaje si la imagen no es válida (tipo o tamaño), igual que la web. */
    fun imageProblem(mime: String?, sizeBytes: Long?): String? {
        if (sizeBytes != null && sizeBytes > GarmentLimits.MAX_IMAGE_BYTES) return "La imagen es demasiado grande. El tamaño máximo es 10 MB."
        if (mime == null || mime.lowercase() !in GarmentLimits.IMAGE_TYPES) return "Formato no válido. Usa JPEG, PNG o WebP."
        return null
    }

    /** Alta: los campos vacíos no se envían. */
    fun draftForAdd(form: GarmentForm, forSale: Boolean = form.forSale): GarmentDraft = GarmentDraft(
        type = form.category,
        name = form.name.trim().take(GarmentLimits.NAME),
        color = form.color.trim().take(GarmentLimits.COLOR).ifBlank { null },
        season = form.season,
        brand = form.brand.trim().take(GarmentLimits.BRAND).ifBlank { null },
        size = form.size.trim().take(GarmentLimits.SIZE).ifBlank { null },
        forSale = forSale,
        price = if (forSale) validSalePrice(form.price) else null,
        condition = if (forSale) form.condition else null,
        description = form.description.trim().take(GarmentLimits.DESCRIPTION).ifBlank { null },
    )

    /**
     * Edición: parte de la prenda y envía cadenas vacías para BORRAR campos (el servidor ignora los nulos).
     * Si no está en venta el precio va a null (el repositorio lo envía vacío y el servidor lo borra).
     */
    fun draftForEdit(original: Garment, form: GarmentForm): GarmentDraft = original.toDraft().copy(
        type = form.category,
        name = form.name.trim().take(GarmentLimits.NAME),
        color = form.color.trim().take(GarmentLimits.COLOR),
        season = form.season,
        brand = form.brand.trim().take(GarmentLimits.BRAND),
        size = form.size.trim().take(GarmentLimits.SIZE),
        condition = form.condition,
        description = form.description.trim().take(GarmentLimits.DESCRIPTION),
        forSale = form.forSale,
        price = if (form.forSale) validSalePrice(form.price) else null,
    )

    /** Poner en venta una prenda existente. */
    fun draftForSale(original: Garment, form: GarmentForm): GarmentDraft = original.toDraft().copy(
        forSale = true,
        price = validSalePrice(form.price),
        condition = form.condition,
        size = form.size.trim().take(GarmentLimits.SIZE),
        description = form.description.trim().take(GarmentLimits.DESCRIPTION),
        isWashing = false,
    )

    /** Retirar de la venta: se borra el precio. */
    fun draftCancelSale(original: Garment): GarmentDraft = original.toDraft().copy(forSale = false, price = null)
}

/** Texto de entrada para un precio (sin ".0" sobrante). */
fun formatPriceInput(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else String.format(java.util.Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')

/** "25 €" / "24,50 €" para mostrar. */
fun formatPrice(v: Double): String {
    val s = if (v % 1.0 == 0.0) v.toLong().toString() else String.format(java.util.Locale.forLanguageTag("es-ES"), "%.2f", v)
    return "$s €"
}
