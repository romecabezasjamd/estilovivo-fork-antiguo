package com.estilovivo.app.core.image

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/*
 * Recorte de prendas reutilizable (Probador y, después, subida de prendas del Armario).
 *
 *   GarmentCutout.cutOut(bitmap, options) -> CutoutResult
 *
 *  - [MlKitGarmentCutout]: ML Kit Subject Segmentation (módulo de Google Play Services, se descarga la primera vez).
 *  - [FloodFillGarmentCutout]: relleno desde los bordes con tolerancia ajustable (sin red, sin dependencias).
 *  - [FallbackGarmentCutout]: IA primero y, si falla o se rechaza, el flood-fill.
 * Todas pasan por el mismo post-proceso ([MaskOps]): islas, huecos, suavizado, recuadro y PNG con alfa.
 */

@Immutable
data class CutoutOptions(
    /** Sólo para el flood-fill (1..100). */
    val tolerance: Int = Cutout.DEFAULT_TOLERANCE,
    /** Lado máximo del resultado (≤ 1600). */
    val maxSide: Int = 1600,
    /** Recortar al recuadro de la prenda (con [margin]). El probador lo desactiva para conservar proporciones. */
    val crop: Boolean = true,
    val margin: Float = 0.03f,
    val feather: Int = 1,
)

enum class CutoutMethod(val label: String) { MlKit("Recorte inteligente"), FloodFill("Recorte básico") }

sealed interface CutoutResult {
    @Immutable
    class Success(val bitmap: Bitmap, val method: CutoutMethod, val foregroundFraction: Float) : CutoutResult

    /** Resultado descartado por poco fiable (casi vacío o casi entero). */
    @Immutable
    data class Rejected(val message: String, val foregroundFraction: Float) : CutoutResult

    @Immutable
    data class Failed(val message: String) : CutoutResult
}

enum class CutoutModelStatus { Unknown, Checking, Preparing, Ready, Unavailable }

interface GarmentCutout {
    suspend fun cutOut(src: Bitmap, options: CutoutOptions = CutoutOptions()): CutoutResult
}

/** Límites de fiabilidad: se rechaza un recorte con <3 % o >97 % de píxeles de primer plano. */
object CutoutLimits {
    const val MIN_FRACTION = 0.03f
    const val MAX_FRACTION = 0.97f

    fun reject(fraction: Float): String? = when {
        fraction < MIN_FRACTION -> "El recorte quedaría casi vacío. Prueba con una foto con más contraste entre la prenda y el fondo."
        fraction > MAX_FRACTION -> "No se distingue la prenda del fondo (el recorte quedaría casi entero). Prueba con un fondo liso."
        else -> null
    }
}

/** Convierte una máscara 0..1 (mismo tamaño que [work]) en el resultado final. */
internal fun finishCutout(work: Bitmap, mask: FloatArray, o: CutoutOptions, method: CutoutMethod): CutoutResult {
    val w = work.width
    val h = work.height
    val refined = MaskOps.refine(mask, w, h, feather = o.feather)
    CutoutLimits.reject(refined.fraction)?.let { return CutoutResult.Rejected(it, refined.fraction) }
    val px = IntArray(w * h)
    work.getPixels(px, 0, w, 0, 0, w, h)
    for (i in px.indices) {
        val a = (refined.mask[i] * (px[i] ushr 24) ).toInt().coerceIn(0, 255)
        px[i] = (a shl 24) or (px[i] and 0x00FFFFFF)
    }
    val box = if (o.crop) MaskOps.bounds(refined.mask, w, h, o.margin) else null
    val full = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    if (box == null) return CutoutResult.Success(full, method, refined.fraction)
    val cropped = Bitmap.createBitmap(full, box[0], box[1], box[2] - box[0], box[3] - box[1])
    if (cropped !== full) full.recycle()
    return CutoutResult.Success(cropped, method, refined.fraction)
}

internal fun scaledWork(src: Bitmap, maxSide: Int): Bitmap {
    val side = maxSide.coerceIn(64, 1600)
    val longest = maxOf(src.width, src.height)
    val base = if (src.config == Bitmap.Config.ARGB_8888) src else src.copy(Bitmap.Config.ARGB_8888, false)
    if (longest <= side) return base
    val s = side.toFloat() / longest
    return Bitmap.createScaledBitmap(base, (src.width * s).toInt().coerceAtLeast(1), (src.height * s).toInt().coerceAtLeast(1), true)
}

class FloodFillGarmentCutout : GarmentCutout {
    override suspend fun cutOut(src: Bitmap, options: CutoutOptions): CutoutResult = withContext(Dispatchers.Default) {
        try {
            val work = scaledWork(src, options.maxSide)
            val w = work.width
            val h = work.height
            val px = IntArray(w * h)
            work.getPixels(px, 0, w, 0, 0, w, h)
            val r = Cutout.apply(px, w, h, options.tolerance)
            if (!r.ok) {
                return@withContext if (r.removedFraction in 0.0001f..1f && r.removedFraction > 0f) {
                    CutoutResult.Rejected(
                        if (r.removedFraction < 0.5f) "No se distingue un fondo uniforme que quitar. Prueba a subir la tolerancia o usa una foto con fondo liso."
                        else "El fondo y la prenda se parecen demasiado. Baja la tolerancia o usa una foto con fondo liso.",
                        1f - r.removedFraction,
                    )
                } else CutoutResult.Failed("La foto no tiene un fondo uniforme que se pueda quitar de forma fiable.")
            }
            val mask = FloatArray(w * h) { (r.pixels[it] ushr 24) / 255f }
            finishCutout(work, mask, options, CutoutMethod.FloodFill)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            CutoutResult.Failed("No se pudo recortar la imagen.")
        }
    }
}

class MlKitGarmentCutout(context: Context) : GarmentCutout {
    private val app = context.applicationContext
    private val lock = Mutex()
    private val segmenter: SubjectSegmenter by lazy {
        SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder().enableForegroundConfidenceMask().build())
    }
    private val _status = MutableStateFlow(CutoutModelStatus.Unknown)

    /** `Preparing` mientras se descarga el módulo (la UI muestra "Preparando el recorte…"). */
    val status: StateFlow<CutoutModelStatus> = _status
    private var unavailableUntil = 0L

    /** Comprueba/instala el módulo. `false` si no hay red o Play Services no puede instalarlo. */
    suspend fun ensureModel(): Boolean = lock.withLock {
        if (_status.value == CutoutModelStatus.Ready) return true
        if (System.currentTimeMillis() < unavailableUntil) return false
        try {
            _status.value = CutoutModelStatus.Checking
            val client = ModuleInstall.getClient(app)
            if (client.areModulesAvailable(segmenter).awaitResult().areModulesAvailable()) {
                _status.value = CutoutModelStatus.Ready; return true
            }
            _status.value = CutoutModelStatus.Preparing
            val req = ModuleInstallRequest.newBuilder().addApi(segmenter).build()
            val resp = withTimeoutOrNull(INSTALL_TIMEOUT_MS) { client.installModules(req).awaitResult() }
            val ok = resp != null && (resp.areModulesAlreadyInstalled() || client.areModulesAvailable(segmenter).awaitResult().areModulesAvailable())
            _status.value = if (ok) CutoutModelStatus.Ready else CutoutModelStatus.Unavailable
            if (!ok) unavailableUntil = System.currentTimeMillis() + RETRY_AFTER_MS
            ok
        } catch (e: CancellationException) {
            _status.value = CutoutModelStatus.Unknown
            throw e
        } catch (_: Throwable) {
            _status.value = CutoutModelStatus.Unavailable
            unavailableUntil = System.currentTimeMillis() + RETRY_AFTER_MS
            false
        }
    }

    override suspend fun cutOut(src: Bitmap, options: CutoutOptions): CutoutResult {
        if (!ensureModel()) return CutoutResult.Failed("El recorte inteligente no está disponible ahora (sin conexión o sin Google Play Services).")
        return try {
            val work = withContext(Dispatchers.Default) { scaledWork(src, minOf(options.maxSide, ML_MAX)) }
            val result = segmenter.process(InputImage.fromBitmap(work, 0)).awaitResult()
            val buf = result.foregroundConfidenceMask ?: return CutoutResult.Failed("No se detectó ninguna prenda.")
            withContext(Dispatchers.Default) {
                val n = work.width * work.height
                if (buf.remaining() < n) return@withContext CutoutResult.Failed("Respuesta inesperada del recorte inteligente.")
                val mask = FloatArray(n)
                buf.rewind()
                buf.get(mask, 0, n)
                finishCutout(work, mask, options, CutoutMethod.MlKit)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            CutoutResult.Failed("No se pudo usar el recorte inteligente.")
        }
    }

    private companion object {
        const val INSTALL_TIMEOUT_MS = 60_000L
        const val RETRY_AFTER_MS = 90_000L
        const val ML_MAX = 1280
    }
}

/** IA primero; si no está disponible, falla o se rechaza, relleno desde los bordes. */
class FallbackGarmentCutout(private val primary: GarmentCutout, private val fallback: GarmentCutout) : GarmentCutout {
    override suspend fun cutOut(src: Bitmap, options: CutoutOptions): CutoutResult {
        val first = primary.cutOut(src, options)
        if (first is CutoutResult.Success) return first
        val second = fallback.cutOut(src, options)
        if (second is CutoutResult.Success) return second
        return if (first is CutoutResult.Rejected) first else second
    }
}

/** Caché de recortes en `cacheDir/cutouts` (PNG con alfa), por clave de prenda. */
class CutoutCache(context: Context) {
    private val dir = File(context.applicationContext.cacheDir, "cutouts").apply { mkdirs() }

    private fun file(key: String): File {
        val h = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "$h.png")
    }

    fun get(key: String): Bitmap? = runCatching {
        file(key).takeIf { it.exists() }?.let { android.graphics.BitmapFactory.decodeFile(it.absolutePath) }
    }.getOrNull()

    fun put(key: String, bitmap: Bitmap) {
        runCatching {
            val f = file(key)
            f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            dir.listFiles().orEmpty().sortedByDescending { it.lastModified() }.drop(MAX_FILES).forEach { it.delete() }
        }
    }

    private companion object { const val MAX_FILES = 80 }
}

suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { if (cont.isActive) cont.resume(it) }
    addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
