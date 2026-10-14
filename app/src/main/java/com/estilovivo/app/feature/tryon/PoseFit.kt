package com.estilovivo.app.feature.tryon

import androidx.compose.runtime.Immutable
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.cos
import kotlin.math.hypot

/** Punto de la pose con confianza 0..1. Coordenadas normalizadas (0..1) sobre la foto, o en unidades de escena. */
@Immutable
data class Pt(val x: Float, val y: Float, val conf: Float = 1f) {
    val ok: Boolean get() = conf >= 0.5f && x.isFinite() && y.isFinite()
}

/** Landmarks que usa el ajuste (todos opcionales). */
@Immutable
data class BodyLandmarks(
    val nose: Pt? = null,
    val lShoulder: Pt? = null, val rShoulder: Pt? = null,
    val lHip: Pt? = null, val rHip: Pt? = null,
    val lKnee: Pt? = null, val rKnee: Pt? = null,
    val lAnkle: Pt? = null, val rAnkle: Pt? = null,
    val lWrist: Pt? = null, val rWrist: Pt? = null,
) {
    /** ¿Hay datos suficientes (hombros) para fiarse de la pose? */
    val usable: Boolean get() = (lShoulder?.ok == true && rShoulder?.ok == true) && (lHip?.ok == true || rHip?.ok == true)

    fun all(): List<Pt?> = listOf(nose, lShoulder, rShoulder, lHip, rHip, lKnee, rKnee, lAnkle, rAnkle, lWrist, rWrist)

    fun map(f: (Pt) -> Pt): BodyLandmarks = BodyLandmarks(
        nose?.let(f), lShoulder?.let(f), rShoulder?.let(f), lHip?.let(f), rHip?.let(f), lKnee?.let(f), rKnee?.let(f),
        lAnkle?.let(f), rAnkle?.let(f), lWrist?.let(f), rWrist?.let(f),
    )

    /**
     * De coordenadas de la foto (0..1) a unidades de escena: la foto se dibuja "ajustada" (fit) y centrada en el lienzo.
     * [photoAspect] = ancho/alto real de la foto; [sceneAspect] = ancho/alto del lienzo.
     */
    fun toScene(photoAspect: Float, sceneAspect: Float): BodyLandmarks {
        val sh = Scene.height(sceneAspect)
        val s = minOf(1f / photoAspect, sh)
        return map { p -> Pt(0.5f + (p.x - 0.5f) * photoAspect * s, sh / 2f + (p.y - 0.5f) * s, p.conf) }
    }
}

@Immutable
data class Fit(val cx: Float, val cy: Float, val width: Float, val rotation: Float)

/** Coloca prendas sobre el cuerpo a partir de los landmarks (en unidades de escena). */
object PoseFitter {
    private val HAT = Regex("sombrero|gorra|gorro|hat|cap\\b|beanie")
    private val BAG = Regex("bolso|bag|mochila|cartera|clutch")
    private val GLASSES = Regex("gafa|glass|sunglass")

    private fun mid(a: Pt?, b: Pt?): Pt? = when {
        a?.ok == true && b?.ok == true -> Pt((a.x + b.x) / 2f, (a.y + b.y) / 2f)
        a?.ok == true -> a
        b?.ok == true -> b
        else -> null
    }

    private fun dist(a: Pt, b: Pt) = hypot(a.x - b.x, a.y - b.y)

    /** [aspect] = alto/ancho de la imagen de la prenda. Devuelve null si la pose no basta para esa categoría. */
    fun fit(category: TryCategory, name: String, aspect: Float, lm: BodyLandmarks, sceneH: Float): Fit? {
        if (!lm.usable) return null
        val a = aspect.coerceIn(0.1f, 6f)
        val sc = mid(lm.lShoulder, lm.rShoulder) ?: return null
        val hc = mid(lm.lHip, lm.rHip) ?: return null
        val sw = if (lm.lShoulder?.ok == true && lm.rShoulder?.ok == true) dist(lm.lShoulder, lm.rShoulder) else return null
        val hw = if (lm.lHip?.ok == true && lm.rHip?.ok == true) dist(lm.lHip, lm.rHip) else sw * 0.75f
        val tl = dist(sc, hc)
        if (sw < 0.03f || tl < 0.05f) return null
        val dx = (hc.x - sc.x) / tl
        val dy = (hc.y - sc.y) / tl
        val tilt = (-Math.toDegrees(atan2(dx.toDouble(), dy.toDouble())).toFloat()).coerceIn(-25f, 25f)
        val lowerLeg = mid(lm.lAnkle, lm.rAnkle)
        val kneeMid = mid(lm.lKnee, lm.rKnee)
        val name0 = name.lowercase()

        fun trunk(mult: Float, maxLen: Float): Fit {
            var w = sw * mult
            val maxH = tl * maxLen
            if (w * a > maxH) w = maxH / a
            val h = w * a
            return Fit(sc.x + dx * (h / 2f - 0.12f * tl), sc.y + dy * (h / 2f - 0.12f * tl), w, tilt)
        }

        val f = when (category) {
            TryCategory.Top -> trunk(1.3f, 1.45f)
            TryCategory.Outer -> trunk(1.5f, 1.7f)
            TryCategory.Dress -> {
                val len = when {
                    kneeMid != null -> dist(sc, kneeMid) * 1.08f
                    lowerLeg != null -> dist(sc, lowerLeg) * 0.8f
                    else -> tl * 2.3f
                }
                var w = sw * 1.5f
                if (w * a > len) w = (len / a).coerceAtLeast(sw * 1.1f)
                val h = w * a
                Fit(sc.x + dx * (h / 2f - 0.1f * tl), sc.y + dy * (h / 2f - 0.1f * tl), w, tilt)
            }
            TryCategory.Bottom -> {
                val avail = when {
                    lowerLeg != null -> dist(hc, lowerLeg)
                    kneeMid != null -> dist(hc, kneeMid) * 1.9f
                    else -> tl * 1.9f
                }
                var w = maxOf(hw * 1.5f, sw * 0.8f)
                if (w * a > avail * 1.12f) w = avail * 1.12f / a
                val h = w * a
                Fit(hc.x, hc.y + (h / 2f - 0.08f * tl), w, tilt.coerceIn(-10f, 10f))
            }
            TryCategory.Shoes -> {
                val am = lowerLeg ?: return null
                val sep = if (lm.lAnkle?.ok == true && lm.rAnkle?.ok == true) dist(lm.lAnkle, lm.rAnkle) else 0f
                val w = (sep * 2.4f).coerceIn(sw * 0.55f, sw * 1.2f)
                val h = w * a
                Fit(am.x, am.y + h * 0.2f, w, 0f)
            }
            TryCategory.Accessory -> when {
                HAT.containsMatchIn(name0) && lm.nose?.ok == true -> {
                    val w = sw * 0.9f
                    Fit(lm.nose.x, lm.nose.y - tl * 0.3f - w * a * 0.1f, w, 0f)
                }
                GLASSES.containsMatchIn(name0) && lm.nose?.ok == true -> Fit(lm.nose.x, lm.nose.y - tl * 0.04f, sw * 0.55f, 0f)
                BAG.containsMatchIn(name0) -> {
                    val wr = (if (lm.rWrist?.ok == true) lm.rWrist else lm.lWrist) ?: return null
                    val w = sw * 0.55f
                    Fit(wr.x, wr.y + w * a * 0.3f, w, 0f)
                }
                else -> return null
            }
            TryCategory.Other -> trunk(1.2f, 1.3f)
        }
        return f.copy(cy = f.cy.coerceIn(0f, sceneH), cx = f.cx.coerceIn(0f, 1f), width = f.width.coerceIn(LayerMath.MIN_WIDTH, LayerMath.MAX_WIDTH))
    }
}

/** Curva de muelle con ligero rebote: 0 -> ~1.1 -> 1. */
object FitSpring {
    fun value(t: Float): Float = if (t <= 0f) 0f else if (t >= 0.8f) 1f else (1f - exp(-7f * t) * cos(16f * t))
}
