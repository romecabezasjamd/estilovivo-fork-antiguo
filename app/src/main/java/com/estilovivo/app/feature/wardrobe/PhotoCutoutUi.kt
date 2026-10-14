package com.estilovivo.app.feature.wardrobe

import android.content.Context
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.estilovivo.app.core.data.ImageUpload
import com.estilovivo.app.core.design.EvMotion
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.EvButtonStyle
import com.estilovivo.app.core.design.components.EvImage
import com.estilovivo.app.core.design.components.EvLoadingDots
import com.estilovivo.app.core.design.components.EvPillButton
import com.estilovivo.app.core.image.CutoutModelStatus
import org.koin.compose.koinInject

/**
 * Estado del recorte de la foto elegida en un formulario. Se recalcula con `LaunchedEffect(photoUri, enabled)`:
 * cambiar de foto (o el interruptor) cancela el trabajo anterior.
 */
@Stable
class PhotoCutoutUi(
    val enabled: Boolean,
    val onEnabledChange: (Boolean) -> Unit,
    /** null = trabajando (o sin foto). */
    val outcome: CutoutOutcome?,
    val preparing: Boolean,
    /** La foto ya es un recorte guardado (p. ej. al reabrir tras un fallo del alta): no hay interruptor. */
    val alreadyPrepared: Boolean,
) {
    val working: Boolean get() = enabled && outcome == null && !alreadyPrepared

    /** URI que hay que subir: el recorte si existe y está activo; si no (desactivado, rechazo, fallo o en curso) la original. */
    fun effectiveUri(photoUri: String?): String? = (outcome as? CutoutOutcome.Applied)?.uri ?: photoUri
}

@Composable
fun rememberPhotoCutout(photoUri: String?): PhotoCutoutUi {
    val processor: AndroidPhotoCutout = koinInject()
    var enabled by rememberSaveable { mutableStateOf(true) }
    var outcome by remember(photoUri) { mutableStateOf<CutoutOutcome?>(null) }
    val status by processor.status.collectAsStateWithLifecycle()
    LaunchedEffect(photoUri, enabled) {
        outcome = null
        if (photoUri != null) outcome = processor.decider.prepare(photoUri, enabled)
    }
    val prepared = PreparedPhoto.isPrepared(photoUri)
    return remember(enabled, outcome, status, prepared) {
        PhotoCutoutUi(enabled, { enabled = it }, outcome, status == CutoutModelStatus.Preparing, prepared)
    }
}

/** Lee la foto a subir: el recorte (PNG con alfa) si lo hay; si falla, la original. */
suspend fun readGarmentUpload(context: Context, chosenUri: String?, originalUri: String?): ImageUpload? {
    if (chosenUri == null) return null
    if (PreparedPhoto.isPrepared(chosenUri)) {
        val path = Uri.parse(chosenUri).path
        val up = path?.let { ImageUpload.fromFile(java.io.File(it)) }
        if (up != null) return up
        return originalUri?.takeIf { it != chosenUri }?.let { ImageUpload.fromUri(context, Uri.parse(it)) }
    }
    return ImageUpload.fromUri(context, Uri.parse(chosenUri))
}

/** Interruptor "Quitar fondo", estado ("Preparando el recorte…"), vista previa antes/después y "Usar original". */
@Composable
fun CutoutSection(ui: PhotoCutoutUi, originalUri: String?, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    if (originalUri == null) return
    Column(modifier.fillMaxWidth().animateContentSize(EvMotion.smooth()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!ui.alreadyPrepared) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Quitar fondo", style = EvType.subtitle, color = c.ink)
                    Text("Recorta la prenda para verla limpia en tu armario.", style = EvType.caption, color = c.inkMuted)
                }
                WardrobeSwitch(ui.enabled, ui.onEnabledChange, "Quitar fondo")
            }
        }
        AnimatedContent(
            targetState = when {
                ui.alreadyPrepared -> CutoutView.Done
                !ui.enabled -> CutoutView.Off
                ui.outcome == null -> CutoutView.Busy
                ui.outcome is CutoutOutcome.Applied -> CutoutView.Done
                else -> CutoutView.Note
            },
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
            label = "cutoutView",
        ) { view ->
            when (view) {
                CutoutView.Off -> Unit
                CutoutView.Busy -> Row(
                    Modifier.fillMaxWidth().clip(EvShapes.cardSmall).background(c.surface).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    EvLoadingDots()
                    Column {
                        Text(if (ui.preparing) "Preparando el recorte…" else "Recortando la prenda…", style = EvType.label, color = c.ink)
                        Text("Puedes seguir: si no esperas, se usará la foto original.", style = EvType.caption, color = c.inkMuted)
                    }
                }
                CutoutView.Note -> Text(
                    (ui.outcome as? CutoutOutcome.Original)?.note ?: CUTOUT_UNAVAILABLE_NOTE,
                    style = EvType.caption, color = c.inkMuted, modifier = Modifier.padding(horizontal = 4.dp),
                )
                CutoutView.Done -> {
                    val result = (ui.outcome as? CutoutOutcome.Applied)?.uri ?: originalUri
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (!ui.alreadyPrepared) PreviewTile("Antes", originalUri, false, Modifier.weight(1f))
                            PreviewTile(if (ui.alreadyPrepared) "Recortada" else "Después", result, true, Modifier.weight(1f))
                        }
                        if (!ui.alreadyPrepared) {
                            EvPillButton("Usar original", { ui.onEnabledChange(false) }, style = EvButtonStyle.Secondary, height = 44.dp)
                        }
                    }
                }
            }
        }
    }
}

private enum class CutoutView { Off, Busy, Note, Done }

@Composable
private fun PreviewTile(label: String, model: Any, checker: Boolean, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(EvShapes.cardSmall).background(c.surfaceAlt)
                .then(if (checker) Modifier.checkerboard(c.surface, c.surfaceAlt) else Modifier),
        ) {
            EvImage(model, "$label: foto de la prenda", Modifier.matchParentSize().padding(6.dp), contentScale = ContentScale.Fit)
        }
        Text(label, style = EvType.caption, color = c.inkMuted, modifier = Modifier.padding(start = 4.dp))
    }
}

/** Damero para que la transparencia del recorte se aprecie tanto en tema claro como oscuro. */
private fun Modifier.checkerboard(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color, cell: Float = 24f): Modifier =
    drawBehind {
        drawRect(b)
        val cols = (size.width / cell).toInt() + 1
        val rows = (size.height / cell).toInt() + 1
        for (y in 0 until rows) for (x in 0 until cols) {
            if ((x + y) % 2 == 0) drawRect(a, Offset(x * cell, y * cell), Size(cell, cell))
        }
    }
