package com.estilovivo.app.feature.tryon

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import android.util.LruCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size as CoilSize
import coil3.toBitmap
import com.estilovivo.app.core.data.ImageUpload
import com.estilovivo.app.core.image.AlphaMask
import com.estilovivo.app.core.image.Cutout
import com.estilovivo.app.core.image.CutoutCache
import com.estilovivo.app.core.image.CutoutModelStatus
import com.estilovivo.app.core.image.CutoutOptions
import com.estilovivo.app.core.image.CutoutResult
import com.estilovivo.app.core.image.FallbackGarmentCutout
import com.estilovivo.app.core.image.FloodFillGarmentCutout
import com.estilovivo.app.core.image.MlKitGarmentCutout
import com.estilovivo.app.core.image.awaitResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Implementación Android de [TryOnAssets].
 *  - Foto base: se reduce a ≤ 2048 px, se corrige el EXIF y se guarda como JPEG en `filesDir/tryon/`.
 *  - Prendas: Coil las descarga a ≤ 1280 px; los recortes se calculan sobre ≤ 900 px. Caché LRU acotada (≈ 56 MB).
 *  - Export: se dibuja con el mismo [drawScene] que la pantalla, sobre un [ImageBitmap] fuera de pantalla.
 */
class AndroidTryOnAssets(context: Context) : TryOnAssets {
    private val app = context.applicationContext
    private val dir = File(app.filesDir, "tryon").apply { mkdirs() }
    private val imagesDir get() = File(app.cacheDir, "images").apply { mkdirs() }

    private val _revision = MutableStateFlow(0)
    override val revision: StateFlow<Int> = _revision

    @Volatile private var bodyCache: Pair<String, ImageBitmap>? = null
    private val garmentCache = object : LruCache<String, ImageBitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }
    private val failedUrls = ConcurrentHashMap.newKeySet<String>()
    private val computeLock = Mutex()
    private val masks = ConcurrentHashMap<String, AlphaMask>()
    private val notes = ConcurrentHashMap<String, String>()
    private val okKeys = ConcurrentHashMap.newKeySet<String>()
    private val cache = CutoutCache(app)
    private val mlKit = MlKitGarmentCutout(app)
    private val flood = FloodFillGarmentCutout()
    private val smart = FallbackGarmentCutout(mlKit, flood)
    override val cutoutStatus: StateFlow<CutoutModelStatus> = mlKit.status

    // ── Foto base ─────────────────────────────────────────────────────────────────────────────

    override suspend fun importBody(source: String): BodyInfo? = withContext(Dispatchers.IO) {
        try {
            val bmp = decodeBody(source) ?: return@withContext null
            val old = dir.listFiles { f -> f.name.startsWith("body_") }.orEmpty()
            val file = File(dir, "body_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            val aspect = Scene.clampAspect(bmp.width.toFloat() / bmp.height)
            bmp.recycle()
            old.forEach { it.delete() }
            bodyCache = null
            BodyInfo(file.absolutePath, aspect)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun decodeBody(source: String): Bitmap? {
        if (source.startsWith("http://") || source.startsWith("https://")) {
            val req = ImageRequest.Builder(app).data(source).size(CoilSize(MAX_BODY, MAX_BODY)).allowHardware(false).build()
            val res = SingletonImageLoader.get(app).execute(req) as? SuccessResult ?: return null
            // Copia propia: el bitmap de Coil pertenece a su caché y no se debe reciclar.
            return res.image.toBitmap().copy(Bitmap.Config.ARGB_8888, false)
        }
        val bytes: ByteArray = if (source.startsWith("content://") || source.startsWith("file://")) {
            app.contentResolver.openInputStream(Uri.parse(source))?.use { it.readBytes() } ?: return null
        } else File(source).readBytes()
        return decodeOriented(bytes, MAX_BODY)
    }

    override suspend fun loadBody(path: String): Boolean = withContext(Dispatchers.IO) {
        if (bodyCache?.first == path) return@withContext true
        val bmp = runCatching { BitmapFactory.decodeFile(path) }.getOrNull() ?: return@withContext false
        bodyCache = path to bmp.asImageBitmap()
        _revision.value++
        true
    }

    override fun body(path: String?): ImageBitmap? = path?.let { p -> bodyCache?.takeIf { it.first == p }?.second }

    // ── Prendas ───────────────────────────────────────────────────────────────────────────────

    private fun key(url: String, cutout: Int) = "$url#$cutout"

    private suspend fun original(url: String): ImageBitmap? {
        garmentCache.get(key(url, 0))?.let { return it }
        if (url.isBlank()) { failedUrls += url; return null }
        return try {
            val req = ImageRequest.Builder(app).data(url).size(CoilSize(MAX_GARMENT, MAX_GARMENT)).allowHardware(false).build()
            val res = SingletonImageLoader.get(app).execute(req) as? SuccessResult
            if (res == null) { failedUrls += url; null } else {
                var bmp = res.image.toBitmap()
                if (bmp.config != Bitmap.Config.ARGB_8888) bmp = bmp.copy(Bitmap.Config.ARGB_8888, false)
                val ib = bmp.asImageBitmap()
                garmentCache.put(key(url, 0), ib)
                failedUrls -= url
                _revision.value++
                ib
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            failedUrls += url
            null
        }
    }

    private val poseDetector by lazy {
        com.google.mlkit.vision.pose.PoseDetection.getClient(
            com.google.mlkit.vision.pose.defaults.PoseDetectorOptions.Builder()
                .setDetectorMode(com.google.mlkit.vision.pose.defaults.PoseDetectorOptions.SINGLE_IMAGE_MODE).build(),
        )
    }

    override suspend fun detectPose(path: String): BodyLandmarks? = withContext(Dispatchers.Default) {
        var bmp: Bitmap? = null
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 1024 && bounds.outHeight / (sample * 2) >= 1024) sample *= 2
            bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@withContext null
            val pose = poseDetector.process(com.google.mlkit.vision.common.InputImage.fromBitmap(bmp, 0)).awaitResult()
            val w = bmp.width.toFloat()
            val h = bmp.height.toFloat()
            fun p(type: Int): Pt? = pose.getPoseLandmark(type)?.let { Pt(it.position.x / w, it.position.y / h, it.inFrameLikelihood) }
            val lm = run {
                BodyLandmarks(
                    nose = p(com.google.mlkit.vision.pose.PoseLandmark.NOSE), lShoulder = p(com.google.mlkit.vision.pose.PoseLandmark.LEFT_SHOULDER), rShoulder = p(com.google.mlkit.vision.pose.PoseLandmark.RIGHT_SHOULDER),
                    lHip = p(com.google.mlkit.vision.pose.PoseLandmark.LEFT_HIP), rHip = p(com.google.mlkit.vision.pose.PoseLandmark.RIGHT_HIP), lKnee = p(com.google.mlkit.vision.pose.PoseLandmark.LEFT_KNEE), rKnee = p(com.google.mlkit.vision.pose.PoseLandmark.RIGHT_KNEE),
                    lAnkle = p(com.google.mlkit.vision.pose.PoseLandmark.LEFT_ANKLE), rAnkle = p(com.google.mlkit.vision.pose.PoseLandmark.RIGHT_ANKLE), lWrist = p(com.google.mlkit.vision.pose.PoseLandmark.LEFT_WRIST), rWrist = p(com.google.mlkit.vision.pose.PoseLandmark.RIGHT_WRIST),
                )
            }
            lm.takeIf { it.usable }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        } finally {
            bmp?.recycle()
        }
    }

    override suspend fun garmentInfo(url: String): GarmentInfo? {
        val ib = original(url) ?: return null
        return GarmentInfo((ib.height.toFloat() / ib.width).coerceIn(0.1f, 6f), 0)
    }

    private fun pixels(ib: ImageBitmap, maxSide: Int): Triple<IntArray, Int, Int> {
        val src = ib.asAndroidBitmap()
        val s = minOf(1f, maxSide.toFloat() / max(src.width, src.height))
        val work = if (s < 1f) Bitmap.createScaledBitmap(src, (src.width * s).roundToInt().coerceAtLeast(1), (src.height * s).roundToInt().coerceAtLeast(1), true) else src
        val px = IntArray(work.width * work.height)
        work.getPixels(px, 0, work.width, 0, 0, work.width, work.height)
        val w = work.width
        val h = work.height
        if (work !== src) work.recycle()
        return Triple(px, w, h)
    }

    private fun storeMask(k: String, ib: ImageBitmap) {
        if (masks.containsKey(k)) return
        val (px, w, h) = pixels(ib, 128)
        masks[k] = AlphaMask.fromPixels(px, w, h)
    }

    override suspend fun ensure(url: String, cutout: Int): Boolean {
        val orig = original(url) ?: return false
        storeMask(key(url, 0), orig)
        if (cutout == 0 || garmentCache.get(key(url, cutout)) != null) return true
        val k = key(url, cutout)
        computeLock.withLock {
            if (garmentCache.get(k) != null) return true
            val cached = if (cutout < 0) cache.get(k) else null
            if (cached != null) {
                val ib = cached.asImageBitmap()
                garmentCache.put(k, ib); storeMask(k, ib); okKeys += k; _revision.value++
                return true
            }
            val src = orig.asAndroidBitmap()
            val opts = CutoutOptions(tolerance = if (cutout > 0) cutout else Cutout.DEFAULT_TOLERANCE, maxSide = 1280, crop = false)
            val r = if (cutout < 0) smart.cutOut(src, opts) else flood.cutOut(src, opts)
            when (r) {
                is CutoutResult.Success -> {
                    val ib = r.bitmap.asImageBitmap()
                    garmentCache.put(k, ib); storeMask(k, ib); okKeys += k; notes.remove(k)
                    if (cutout < 0) cache.put(k, r.bitmap)
                }
                is CutoutResult.Rejected -> { notes[k] = r.message; garmentCache.put(k, orig) }
                is CutoutResult.Failed -> { notes[k] = r.message; garmentCache.put(k, orig) }
            }
            _revision.value++
        }
        return true
    }

    override suspend fun autoCutout(url: String): Boolean {
        if (!ensure(url, -1)) return false
        return key(url, -1) in okKeys
    }

    override fun cutoutNote(url: String, cutout: Int): String? = notes[key(url, cutout)]
    override fun alphaMask(url: String, cutout: Int): AlphaMask? = masks[key(url, cutout)] ?: masks[key(url, 0)]

    override fun garment(url: String, cutout: Int): ImageBitmap? =
        garmentCache.get(key(url, cutout)) ?: garmentCache.get(key(url, 0))

    override fun failed(url: String): Boolean = url in failedUrls

    // ── Render / export ───────────────────────────────────────────────────────────────────────

    private suspend fun renderBitmap(spec: RenderSpec, maxSide: Int, quality: FilterQuality): Bitmap? {
        spec.bodyPath?.let { loadBody(it) }
        for (l in spec.layers) if (!ensure(l.url, l.cutout)) return null
        return withContext(Dispatchers.Default) {
            val aspect = Scene.clampAspect(spec.sceneAspect)
            val (w, h) = if (aspect >= 1f) maxSide to (maxSide / aspect).roundToInt() else (maxSide * aspect).roundToInt() to maxSide
            val ib = ImageBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1))
            val body = body(spec.bodyPath)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(ib), Size(ib.width.toFloat(), ib.height.toFloat())) {
                drawScene(
                    aspect, spec.darkBackground, spec.mirror, body, spec.layers,
                    bitmapOf = { garment(it.url, it.cutout) }, quality = quality,
                )
            }
            ib.asAndroidBitmap()
        }
    }

    override suspend fun render(spec: RenderSpec, maxSide: Int, jpeg: Boolean): File? {
        val bmp = renderBitmap(spec, maxSide.coerceIn(256, MAX_EXPORT), FilterQuality.High) ?: return null
        return withContext(Dispatchers.IO) {
            try {
                trimCache()
                val file = File(imagesDir, "tryon_${System.currentTimeMillis()}.${if (jpeg) "jpg" else "png"}")
                file.outputStream().use { bmp.compress(if (jpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG, if (jpeg) 90 else 100, it) }
                file
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                null
            } finally {
                bmp.recycle()
            }
        }
    }

    private fun trimCache() {
        imagesDir.listFiles { f -> f.name.startsWith("tryon_") }.orEmpty().sortedByDescending { it.lastModified() }.drop(6).forEach { it.delete() }
    }

    override suspend fun thumbnail(spec: RenderSpec): String? {
        var side = 360
        var q = 72
        val bmp = renderBitmap(spec, side, FilterQuality.Medium) ?: return null
        return withContext(Dispatchers.Default) {
            try {
                var bytes: ByteArray
                var cur = bmp
                while (true) {
                    val out = ByteArrayOutputStream()
                    cur.compress(Bitmap.CompressFormat.JPEG, q, out)
                    bytes = out.toByteArray()
                    if (bytes.size * 4 / 3 <= PresetRules.MAX_THUMB_BYTES * 0.8 || side <= 120) break
                    side = (side * 0.75f).toInt()
                    q = (q - 8).coerceAtLeast(40)
                    val scaled = Bitmap.createScaledBitmap(cur, (cur.width * 0.75f).roundToInt().coerceAtLeast(1), (cur.height * 0.75f).roundToInt().coerceAtLeast(1), true)
                    if (cur !== bmp) cur.recycle()
                    cur = scaled
                }
                if (cur !== bmp) cur.recycle()
                "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
            } finally {
                bmp.recycle()
            }
        }
    }

    override val canSaveToGallery: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    override suspend fun saveToGallery(file: File): Boolean = withContext(Dispatchers.IO) {
        if (!canSaveToGallery) return@withContext false
        try {
            val resolver = app.contentResolver
            val isPng = file.extension.equals("png", true)
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "EstiloVivo_${System.currentTimeMillis()}.${file.extension}")
                put(MediaStore.Images.Media.MIME_TYPE, if (isPng) "image/png" else "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/EstiloVivo")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
            try {
                resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: error("sin salida")
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                true
            } catch (e: Throwable) {
                runCatching { resolver.delete(uri, null, null) }
                false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            false
        }
    }

    override suspend fun upload(file: File): ImageUpload? = ImageUpload.fromFile(file)
    override suspend fun bodyUpload(path: String): ImageUpload? = ImageUpload.fromFile(File(path))

    override fun release() {
        runCatching { poseDetector.close() }
        garmentCache.evictAll()
        masks.clear(); notes.clear(); okKeys.clear()
        bodyCache = null
        failedUrls.clear()
    }

    private companion object {
        const val MAX_BODY = 2048
        const val MAX_GARMENT = 1280
        const val MAX_EXPORT = 2048
        const val AUTO_PROBE = 400
        const val CUTOUT_WORK = 900
        const val CACHE_BYTES = 56 * 1024 * 1024
    }
}

/** Decodifica reduciendo a ≤ [maxSide] y aplicando la orientación EXIF. */
internal fun decodeOriented(bytes: ByteArray, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxSide && bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val orientation = runCatching {
        ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val m = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
    }
    val longest = max(decoded.width, decoded.height)
    if (longest > maxSide) {
        val s = maxSide.toFloat() / longest
        m.postScale(s, s)
    }
    val out = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
    if (out !== decoded) decoded.recycle()
    return out
}

/** Borrador en `filesDir/tryon/draft.json` (escritura atómica). */
class FileTryOnDraftStore(context: Context) : TryOnDraftStore {
    private val dir = File(context.applicationContext.filesDir, "tryon").apply { mkdirs() }
    private val file = File(dir, "draft.json")

    override fun load(): String? = runCatching { file.takeIf { it.exists() }?.readText() }.getOrNull()

    override fun save(json: String) {
        runCatching {
            val tmp = File(dir, "draft.json.tmp")
            tmp.writeText(json)
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        }
    }

    override fun clear() { runCatching { file.delete() } }
}
