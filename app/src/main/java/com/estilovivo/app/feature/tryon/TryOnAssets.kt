package com.estilovivo.app.feature.tryon

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import com.estilovivo.app.core.data.ImageUpload
import com.estilovivo.app.core.image.AlphaMask
import com.estilovivo.app.core.image.CutoutModelStatus
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** Foto base ya importada: fichero local reducido y con la rotación EXIF aplicada. */
@Immutable
data class BodyInfo(val path: String, val aspect: Float)

/** Datos de una prenda recién cargada: proporciones y recorte automático (0 = no fiable, se deja la original). */
@Immutable
data class GarmentInfo(val aspect: Float, val autoCutout: Int)

/** Lo necesario para dibujar la composición fuera de pantalla (exportar, miniatura). */
@Immutable
data class RenderSpec(
    val layers: List<TryLayer>,
    val bodyPath: String?,
    val sceneAspect: Float,
    val darkBackground: Boolean,
    val mirror: Boolean,
)

/**
 * Todo lo que toca bitmaps / disco / MediaStore. El ViewModel sólo ve esta interfaz (así se prueba con un doble).
 * La implementación real es [AndroidTryOnAssets]: cachés acotadas por tamaño y liberación al salir.
 */
interface TryOnAssets {
    /** Sube cuando llega un bitmap nuevo: la capa de dibujo vuelve a leer. */
    val revision: StateFlow<Int>

    /** Importa una foto de cuerpo: `content://`, ruta de fichero o URL http(s). */
    suspend fun importBody(source: String): BodyInfo?

    /** Carga la foto base ya guardada (para pintarla). */
    suspend fun loadBody(path: String): Boolean
    fun body(path: String?): ImageBitmap?

    suspend fun garmentInfo(url: String): GarmentInfo?

    /** Landmarks de pose (ML Kit, modelo incluido en el APK) en coordenadas normalizadas de la foto; null si no se detecta. */
    suspend fun detectPose(path: String): BodyLandmarks?

    /** Garantiza en caché el bitmap de la prenda con ese recorte; devuelve false si no se pudo cargar. */
    suspend fun ensure(url: String, cutout: Int): Boolean
    fun garment(url: String, cutout: Int): ImageBitmap?
    fun failed(url: String): Boolean

    /** Recorte inteligente (ML Kit con respaldo flood-fill) cacheado; true si se obtuvo un recorte real. */
    suspend fun autoCutout(url: String): Boolean
    /** Motivo por el que un recorte se rechazó o no fue posible (para mostrarlo). */
    fun cutoutNote(url: String, cutout: Int): String?
    /** Máscara de opacidad para el hit-test (null = usar el rectángulo). */
    fun alphaMask(url: String, cutout: Int): AlphaMask?
    /** "Preparando el recorte…" mientras se descarga el módulo de ML Kit. */
    val cutoutStatus: StateFlow<CutoutModelStatus>

    /** PNG/JPEG de la composición en la caché `images/` (lado largo ≤ [maxSide]). */
    suspend fun render(spec: RenderSpec, maxSide: Int, jpeg: Boolean): File?

    /** Miniatura `data:image/jpeg;base64,...` de ≤ ~300 KB. */
    suspend fun thumbnail(spec: RenderSpec): String?

    /** Guarda en la galería (Pictures/EstiloVivo). `false` si no es posible. */
    suspend fun saveToGallery(file: File): Boolean
    val canSaveToGallery: Boolean

    suspend fun upload(file: File): ImageUpload?
    suspend fun bodyUpload(path: String): ImageUpload?

    fun release()
}

/** Persistencia del borrador (sobrevive a la muerte del proceso). */
interface TryOnDraftStore {
    fun load(): String?
    fun save(json: String)
    fun clear()
}
