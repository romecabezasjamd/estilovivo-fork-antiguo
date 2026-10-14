package com.estilovivo.app.feature.wardrobe

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import com.estilovivo.app.core.image.CutoutMethod
import com.estilovivo.app.core.image.CutoutOptions
import com.estilovivo.app.core.image.CutoutResult
import com.estilovivo.app.core.image.GarmentCutout
import kotlinx.coroutines.CancellationException
import kotlin.math.sqrt

/*
 * Lógica de decisión del recorte al subir/editar una prenda (pura: sin Android más allá del tipo Bitmap,
 * que aquí sólo se pasa de mano en mano). Regla de oro: el recorte NUNCA bloquea el alta. Si está desactivado,
 * se rechaza o falla, se usa la foto original (con un aviso discreto cuando procede).
 */

@Immutable
sealed interface CutoutOutcome {
    /** Recorte listo: [uri] es un PNG con alfa en caché. */
    data class Applied(val uri: String, val method: CutoutMethod?) : CutoutOutcome

    /** Se usa la foto original. [note] explica por qué (null = el usuario lo desactivó). */
    data class Original(val note: String?) : CutoutOutcome
}

/** Opciones del recorte para el Armario: recuadro de la prenda con un pequeño margen. */
val WardrobeCutoutOptions = CutoutOptions(maxSide = 1600, crop = true, margin = 0.04f, feather = 1)

/** Límite de peso del PNG recortado (por debajo de los 2 MiB que `ImageUpload` deja pasar sin recodificar). */
object CutoutSize {
    const val MAX_BYTES = 1_900_000

    /** Factor de reescalado (0.3..0.95) para que un PNG de [bytes] quepa en [max]; null si ya cabe. */
    fun scaleFor(bytes: Int, max: Int = MAX_BYTES): Float? {
        if (bytes <= max) return null
        return (sqrt(max.toDouble() / bytes) * 0.9).toFloat().coerceIn(0.3f, 0.95f)
    }
}

/** Los recortes ya hechos se guardan como `file://.../cutout_<hash>.png`; no hay que volver a recortarlos. */
object PreparedPhoto {
    const val PREFIX = "cutout_"
    fun isPrepared(uri: String?): Boolean =
        uri != null && uri.startsWith("file:") && uri.substringAfterLast('/').startsWith(PREFIX)
}

const val CUTOUT_UNAVAILABLE_NOTE = "No se pudo quitar el fondo; usaremos la foto original."

class PhotoCutoutDecider(
    private val load: suspend (String) -> Bitmap?,
    private val cutout: GarmentCutout,
    private val store: suspend (String, Bitmap) -> String?,
    private val cached: (String) -> String? = { null },
    private val options: CutoutOptions = WardrobeCutoutOptions,
) {
    /** Devuelve siempre un resultado utilizable; sólo propaga la cancelación de la corrutina. */
    suspend fun prepare(sourceUri: String, enabled: Boolean): CutoutOutcome {
        if (!enabled) return CutoutOutcome.Original(null)
        if (PreparedPhoto.isPrepared(sourceUri)) return CutoutOutcome.Applied(sourceUri, null)
        return try {
            cached(sourceUri)?.let { return CutoutOutcome.Applied(it, null) }
            val src = load(sourceUri) ?: return CutoutOutcome.Original(CUTOUT_UNAVAILABLE_NOTE)
            when (val r = cutout.cutOut(src, options)) {
                is CutoutResult.Success -> {
                    val saved = store(sourceUri, r.bitmap)
                    if (saved != null) CutoutOutcome.Applied(saved, r.method) else CutoutOutcome.Original(CUTOUT_UNAVAILABLE_NOTE)
                }
                is CutoutResult.Rejected -> CutoutOutcome.Original(r.message)
                is CutoutResult.Failed -> CutoutOutcome.Original(r.message)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            CutoutOutcome.Original(CUTOUT_UNAVAILABLE_NOTE)
        }
    }
}
