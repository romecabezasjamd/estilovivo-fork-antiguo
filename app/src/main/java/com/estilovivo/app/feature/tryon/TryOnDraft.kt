package com.estilovivo.app.feature.tryon

import com.estilovivo.app.core.image.Cutout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Borrador del montaje: se guarda en disco y se recupera si el sistema mata el proceso. */
@Serializable
data class TryDraft(
    val version: Int = 1,
    val bodyPath: String? = null,
    val bodyAspect: Float = Scene.DEFAULT_ASPECT,
    val layers: List<TryLayer> = emptyList(),
    val darkBackground: Boolean = false,
    val mirror: Boolean = false,
    val grid: Boolean = false,
)

object DraftCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }

    fun encode(d: TryDraft): String = json.encodeToString(TryDraft.serializer(), d)

    /** Decodifica y sanea: un borrador corrupto o de otra versión devuelve null (se ignora, nunca falla). */
    fun decode(text: String?): TryDraft? {
        if (text.isNullOrBlank()) return null
        val d = runCatching { json.decodeFromString(TryDraft.serializer(), text) }.getOrNull() ?: return null
        if (d.version != 1) return null
        val aspect = Scene.clampAspect(d.bodyAspect)
        val h = Scene.height(aspect)
        val layers = d.layers.asSequence()
            .filter { it.id.isNotBlank() && it.url.isNotBlank() && it.width.isFinite() && it.cx.isFinite() && it.cy.isFinite() && it.aspect.isFinite() && it.aspect > 0f }
            .map { LayerMath.clampToScene(it.copy(aspect = it.aspect.coerceIn(0.1f, 6f), cutout = it.cutout.coerceIn(-1, Cutout.MAX_TOLERANCE), baseWidth = if (it.baseWidth > 0f) it.baseWidth else it.width), h) }
            .take(LayerMath.MAX_LAYERS)
            .toList()
        return d.copy(bodyAspect = aspect, layers = layers)
    }
}
