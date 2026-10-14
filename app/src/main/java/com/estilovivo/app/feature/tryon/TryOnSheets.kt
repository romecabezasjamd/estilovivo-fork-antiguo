package com.estilovivo.app.feature.tryon

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Help
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.estilovivo.app.core.data.model.TryonPreset
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvSpace
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.EvButtonStyle
import com.estilovivo.app.core.design.components.EvChip
import com.estilovivo.app.core.design.components.EvEmptyState
import com.estilovivo.app.core.design.components.EvHeadline
import com.estilovivo.app.core.design.components.EvImage
import com.estilovivo.app.core.design.components.EvPillButton
import com.estilovivo.app.core.design.components.EvShimmerBox
import com.estilovivo.app.core.design.components.EvTextField
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.rememberEvHaptics
import com.estilovivo.app.core.design.staggeredEntrance
import com.estilovivo.app.core.image.Cutout
import com.estilovivo.app.core.image.CutoutModelStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun TrySheet(onDismiss: () -> Unit, skipPartially: Boolean = true, content: @Composable () -> Unit) {
    val c = EvTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartially),
        containerColor = c.surface, contentColor = c.ink, shape = EvShapes.sheet,
    ) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(bottom = 16.dp)) { content() }
    }
}

@Composable
internal fun SheetRow(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit, enabled: Boolean = true, trailing: (@Composable () -> Unit)? = null) {
    val c = EvTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = EvSpace.screen, vertical = 4.dp)
            .clip(EvShapes.cardSmall).background(c.surfaceAlt)
            .evClickable(enabled = enabled, pressedScale = 0.97f, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(c.surface), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (enabled) c.ink else c.inkMuted, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = EvType.subtitle, color = if (enabled) c.ink else c.inkMuted)
            if (subtitle != null) Text(subtitle, style = EvType.bodySmall, color = c.inkSoft)
        }
        trailing?.invoke()
    }
}

@Composable
private fun SheetTitle(raw: String, sub: String? = null) {
    Column(Modifier.padding(horizontal = EvSpace.screen).padding(bottom = 12.dp)) {
        EvHeadline(raw, base = EvType.headline.copy(fontSize = androidx.compose.ui.unit.TextUnit(26f, androidx.compose.ui.unit.TextUnitType.Sp)))
        if (sub != null) Text(sub, style = EvType.body, color = EvTheme.colors.inkSoft)
    }
}

// ── Foto base ────────────────────────────────────────────────────────────────────────────────

@Composable
internal fun BodySheet(
    ui: TryOnUi, onCamera: () -> Unit, onGallery: () -> Unit, onProfile: () -> Unit, onSaveProfile: () -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit,
) {
    TrySheet(onDismiss) {
        SheetTitle("Tu *foto* base", "De pie, cuerpo entero y fondo liso para un mejor ajuste.")
        SheetRow(Icons.Rounded.CameraAlt, "Hacer una foto", "Con guía de pose y temporizador", { onDismiss(); onCamera() })
        SheetRow(Icons.Rounded.PhotoLibrary, "Elegir de la galería", null, { onDismiss(); onGallery() })
        if (ui.profileBodyUrl != null) SheetRow(
            Icons.Rounded.Person, "Usar mi foto de perfil", "Tu foto de cuerpo entero guardada",
            { onDismiss(); onProfile() },
            trailing = { EvImage(ui.profileBodyUrl, null, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))) },
        )
        if (ui.body.path != null) {
            SheetRow(Icons.Rounded.Save, "Guardar como mi foto de cuerpo entero", "Se usará en tu perfil y la próxima vez", { onDismiss(); onSaveProfile() })
            SheetRow(Icons.Rounded.Delete, "Quitar la foto", null, { onDismiss(); onRemove() })
        }
    }
}

// ── Exportar ─────────────────────────────────────────────────────────────────────────────────

@Composable
internal fun ExportSheet(ui: TryOnUi, onShare: () -> Unit, onGallery: () -> Unit, onLook: () -> Unit, onPreset: () -> Unit, onDismiss: () -> Unit) {
    TrySheet(onDismiss) {
        SheetTitle("Guardar tu *look*")
        SheetRow(Icons.Rounded.IosShare, "Compartir imagen", "PNG en alta calidad (hasta 2048 px)", { onDismiss(); onShare() })
        SheetRow(
            Icons.Rounded.SaveAlt, "Guardar en la galería",
            if (ui.canSaveToGallery) "Carpeta Pictures/EstiloVivo" else "Requiere Android 10 o superior: usa «Compartir»",
            { onDismiss(); onGallery() }, enabled = ui.canSaveToGallery,
        )
        SheetRow(Icons.Rounded.Checkroom, "Guardar como look", "En tu armario, con opción de publicarlo", { onDismiss(); onLook() })
        SheetRow(Icons.Rounded.Bookmarks, "Guardar en «Mis looks»", "El montaje para retocarlo luego", { onDismiss(); onPreset() })
    }
}

// ── Menú ─────────────────────────────────────────────────────────────────────────────────────

@Composable
internal fun MenuSheet(
    ui: TryOnUi, showPose: Boolean, onPose: (Boolean) -> Unit, onPresets: () -> Unit, onBody: () -> Unit,
    onReplace: (Boolean) -> Unit, onClear: () -> Unit, onHelp: () -> Unit, onDismiss: () -> Unit,
) {
    val c = EvTheme.colors
    TrySheet(onDismiss) {
        SheetTitle("Más *opciones*")
        SheetRow(Icons.Rounded.Bookmarks, "Mis looks", "Montajes guardados", { onDismiss(); onPresets() })
        SheetRow(Icons.Rounded.Person, "Foto base", "Cámara, galería o tu foto de perfil", { onDismiss(); onBody() })
        SheetRow(
            Icons.Rounded.Accessibility, "Ver ajuste al cuerpo",
            when (ui.pose.status) {
                PoseStatus.Found -> "Muestra los puntos detectados"
                PoseStatus.Detecting -> "Detectando tu pose…"
                PoseStatus.NotFound -> "No se detectó el cuerpo en esta foto"
                PoseStatus.Idle -> "Añade una foto de cuerpo entero"
            },
            { onPose(!showPose) }, enabled = ui.pose.status == PoseStatus.Found,
            trailing = { Switch(showPose, null, colors = SwitchDefaults.colors(checkedTrackColor = c.ink, checkedThumbColor = c.onInk)) },
        )
        SheetRow(
            Icons.Rounded.Layers, "Sustituir prenda de la misma zona", "Probar otra camiseta cambia la anterior",
            { onReplace(!ui.replaceSameCategory) },
            trailing = { Switch(ui.replaceSameCategory, null, colors = SwitchDefaults.colors(checkedTrackColor = c.ink, checkedThumbColor = c.onInk)) },
        )
        SheetRow(Icons.Rounded.Help, "Cómo se usa", "Gestos y atajos", { onDismiss(); onHelp() })
        SheetRow(Icons.Rounded.Delete, "Vaciar el lienzo", "Quita todas las prendas (se puede deshacer)", { onDismiss(); onClear() })
    }
}

@Composable
internal fun HelpSheet(onDismiss: () -> Unit) {
    TrySheet(onDismiss) {
        SheetTitle("Cómo *probarte* ropa")
        val tips = listOf(
            "Toca una prenda" to "Se coloca sola sobre tu cuerpo. Puedes retocarla después.",
            "Un dedo" to "Arrastra la prenda seleccionada.",
            "Dos dedos" to "Pellizca para escalar y gira para rotar, a la vez.",
            "Fuera de la prenda" to "Dos dedos hacen zoom y mueven el lienzo. Doble toque lo centra.",
            "Asas de la prenda" to "La esquina inferior escala y gira; la roja la quita.",
            "Doble toque en una prenda" to "La recoloca según tu cuerpo.",
            "Sin gestos" to "Pestaña «Capas»: botones de tamaño, giro y movimiento.",
        )
        tips.forEach { (t, d) ->
            Column(Modifier.padding(horizontal = EvSpace.screen, vertical = 6.dp)) {
                Text(t, style = EvType.label, color = EvTheme.colors.ink)
                Text(d, style = EvType.bodySmall, color = EvTheme.colors.inkSoft)
            }
        }
    }
}

// ── Guardar como look / preset ───────────────────────────────────────────────────────────────

@Composable
internal fun SaveLookSheet(busy: Boolean, onSave: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    val c = EvTheme.colors
    var name by rememberSaveable { mutableStateOf("") }
    var public by rememberSaveable { mutableStateOf(false) }
    TrySheet(onDismiss) {
        SheetTitle("Guardar como *look*", "Se añade a tu armario con la imagen de tu prueba.")
        Column(Modifier.padding(horizontal = EvSpace.screen), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            EvTextField(name, { name = it.take(PresetRules.MAX_NAME) }, "Nombre del look")
            Row(
                Modifier.fillMaxWidth().clip(EvShapes.cardSmall).background(c.surfaceAlt).evClickable(pressedScale = 0.98f) { public = !public }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Publicar en la comunidad", style = EvType.subtitle, color = c.ink)
                    Text("Otras personas podrán verlo y darle me gusta", style = EvType.bodySmall, color = c.inkSoft)
                }
                Switch(public, null, colors = SwitchDefaults.colors(checkedTrackColor = c.ink, checkedThumbColor = c.onInk))
            }
            EvPillButton("Guardar look", { onSave(name, public) }, Modifier.fillMaxWidth(), loading = busy, icon = Icons.Rounded.Save)
        }
    }
}

@Composable
private fun StarRating(value: Int, onChange: (Int) -> Unit) {
    val haptics = rememberEvHaptics()
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (1..5).forEach { i ->
            val on = i <= value
            val scale by androidx.compose.animation.core.animateFloatAsState(if (on) 1.1f else 1f, com.estilovivo.app.core.design.EvMotion.bouncy(), label = "star")
            Icon(
                if (on) Icons.Rounded.Star else Icons.Rounded.StarBorder, "$i ${if (i == 1) "estrella" else "estrellas"}",
                tint = if (on) EvTheme.colors.warning else EvTheme.colors.inkMuted,
                modifier = Modifier.size(40.dp).padding(4.dp).graphicsLayer { scaleX = scale; scaleY = scale }.evClickable(haptic = false) { haptics.tick(); onChange(if (value == i) 0 else i) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SavePresetSheet(busy: Boolean, count: Int, onSave: (String, Int?, String?) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var rating by rememberSaveable { mutableIntStateOf(0) }
    var occasion by rememberSaveable { mutableStateOf<String?>(null) }
    TrySheet(onDismiss) {
        SheetTitle("Guardar en *Mis looks*", "Se guardan las prendas y su posición ($count/${PresetRules.MAX_PRESETS}). La foto base no se sube.")
        Column(Modifier.padding(horizontal = EvSpace.screen), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            EvTextField(name, { name = it.take(PresetRules.MAX_NAME) }, "Nombre del montaje")
            StarRating(rating) { rating = it }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PresetRules.occasions.forEach { (id, label) -> EvChip(label, occasion == id, { occasion = if (occasion == id) null else id }) }
            }
            EvPillButton("Guardar montaje", { onSave(name, rating.takeIf { it > 0 }, occasion) }, Modifier.fillMaxWidth(), loading = busy, icon = Icons.Rounded.Bookmarks)
        }
    }
}

// ── Mis looks ────────────────────────────────────────────────────────────────────────────────

@Composable
private fun rememberThumb(src: String?): State<ImageBitmap?> = produceState<ImageBitmap?>(null, src) {
    value = if (src != null && src.startsWith("data:")) withContext(Dispatchers.Default) {
        runCatching {
            val bytes = Base64.decode(src.substringAfter(','), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    } else null
}

@Composable
internal fun PresetsSheet(
    ui: TryOnUi, canSaveCurrent: Boolean, onSaveCurrent: () -> Unit, onLoad: (TryonPreset) -> Unit, onRename: (TryonPreset, String) -> Unit,
    onOverwrite: (TryonPreset) -> Unit, onDelete: (TryonPreset) -> Unit, onRetry: () -> Unit, onDismiss: () -> Unit,
) {
    val c = EvTheme.colors
    var renaming by remember { mutableStateOf<TryonPreset?>(null) }
    var deleting by remember { mutableStateOf<TryonPreset?>(null) }
    var menuFor by remember { mutableStateOf<TryonPreset?>(null) }
    TrySheet(onDismiss, skipPartially = false) {
        Row(Modifier.fillMaxWidth().padding(horizontal = EvSpace.screen).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                EvHeadline("Mis *looks*", base = EvType.headline.copy(fontSize = androidx.compose.ui.unit.TextUnit(26f, androidx.compose.ui.unit.TextUnitType.Sp)))
                Text("${ui.presets.size}/${PresetRules.MAX_PRESETS} guardados", style = EvType.caption, color = c.inkSoft)
            }
            EvPillButton("Guardar actual", onSaveCurrent, height = 44.dp, enabled = canSaveCurrent, icon = Icons.Rounded.Save)
        }
        when {
            ui.presetsLoading && ui.presets.isEmpty() -> LazyVerticalGrid(
                GridCells.Fixed(2), Modifier.padding(horizontal = EvSpace.screen).heightIn(max = 360.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { items(4) { EvShimmerBox(Modifier.aspectRatio(0.8f), RoundedCornerShape(24.dp)) } }
            ui.presetsError != null && ui.presets.isEmpty() -> EvEmptyState(Icons.Rounded.Bookmarks, "No pudimos cargar tus looks", ui.presetsError, actionText = "Reintentar", onAction = onRetry)
            ui.presets.isEmpty() -> EvEmptyState(Icons.Rounded.Bookmarks, "Aún no tienes looks guardados", "Crea un montaje y pulsa «Guardar actual».")
            else -> LazyVerticalGrid(
                GridCells.Fixed(2), Modifier.padding(horizontal = EvSpace.screen).heightIn(max = 420.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(ui.presets, key = { it.id }) { p ->
                    val busy = ui.presetBusyId == p.id
                    val thumb by rememberThumb(p.thumbnail)
                    Column(
                        Modifier.staggeredEntrance(ui.presets.indexOf(p)).clip(RoundedCornerShape(24.dp)).background(c.surfaceAlt)
                            .border(if (ui.loadedPresetId == p.id) 2.dp else 0.dp, c.ink, RoundedCornerShape(24.dp))
                            .evClickable(enabled = !busy, pressedScale = 0.97f) { onLoad(p); onDismiss() },
                    ) {
                        Box(Modifier.fillMaxWidth().aspectRatio(0.8f).background(c.surface)) {
                            val t = thumb
                            when {
                                t != null -> androidx.compose.foundation.Image(t, p.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                p.thumbnail != null && !p.thumbnail.startsWith("data:") -> EvImage(p.thumbnail, p.name, Modifier.fillMaxSize())
                                else -> Icon(Icons.Rounded.Checkroom, null, tint = c.inkMuted, modifier = Modifier.align(Alignment.Center).size(36.dp))
                            }
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(8.dp).size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
                                    .evClickable { menuFor = p }.semantics { contentDescription = "Opciones de ${p.name}" },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.MoreHoriz, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                            if (busy) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                                com.estilovivo.app.core.design.components.EvLoadingDots(Color.White)
                            }
                        }
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Text(p.name, style = EvType.label, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                val r = p.rating ?: 0
                                if (r > 0) repeat(r.coerceAtMost(5)) { Icon(Icons.Rounded.Star, null, tint = c.warning, modifier = Modifier.size(13.dp)) }
                                PresetRules.occasionLabel(p.occasion)?.let { Text(it, style = EvType.caption, color = c.inkSoft, modifier = Modifier.padding(start = if (r > 0) 6.dp else 0.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }
    menuFor?.let { p ->
        TrySheet({ menuFor = null }) {
            SheetTitle(p.name.replace("*", ""))
            SheetRow(Icons.Rounded.Edit, "Renombrar", null, { menuFor = null; renaming = p })
            SheetRow(Icons.Rounded.Refresh, "Actualizar con el montaje actual", "Sustituye sus prendas y miniatura", { menuFor = null; onOverwrite(p) }, enabled = canSaveCurrent)
            SheetRow(Icons.Rounded.Delete, "Eliminar", null, { menuFor = null; deleting = p })
        }
    }
    renaming?.let { p ->
        var text by remember(p.id) { mutableStateOf(p.name) }
        TrySheet({ renaming = null }) {
            SheetTitle("Renombrar *look*")
            Column(Modifier.padding(horizontal = EvSpace.screen), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                EvTextField(text, { text = it.take(PresetRules.MAX_NAME) }, "Nombre")
                EvPillButton("Guardar nombre", { onRename(p, text); renaming = null }, Modifier.fillMaxWidth(), enabled = PresetRules.cleanName(text).isNotBlank())
            }
        }
    }
    deleting?.let { p ->
        ConfirmDialog(
            "¿Eliminar «${p.name}»?", "Se borrará de Mis looks. No se puede deshacer.", "Eliminar",
            onConfirm = { deleting = null; onDelete(p) }, onDismiss = { deleting = null },
        )
    }
}

private fun Modifier.fillMaxSize(): Modifier = this.fillMaxSize()

@Composable
internal fun ConfirmDialog(title: String, message: String, confirmText: String, onConfirm: () -> Unit, onDismiss: () -> Unit, danger: Boolean = true) {
    val c = EvTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(EvShapes.card).background(c.surface).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = EvType.title, color = c.ink)
            Text(message, style = EvType.body, color = c.inkSoft)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EvPillButton("Cancelar", onDismiss, Modifier.weight(1f), style = EvButtonStyle.Secondary, height = 48.dp)
                EvPillButton(confirmText, onConfirm, Modifier.weight(1f), style = if (danger) EvButtonStyle.Danger else EvButtonStyle.Primary, height = 48.dp)
            }
        }
    }
}

// ── Recorte ──────────────────────────────────────────────────────────────────────────────────

@Composable
internal fun CutoutSheet(
    layer: LayerBrief, assets: TryOnAssets, revision: State<Int>, busy: Boolean,
    onSmart: () -> Unit, onBasic: (tolerance: Int, commit: Boolean) -> Unit, onRestore: () -> Unit, onDismiss: () -> Unit,
) {
    val c = EvTheme.colors
    val status by assets.cutoutStatus.collectAsStateWithLifecycle()
    var split by remember { mutableFloatStateOf(0.5f) }
    var tolerance by remember(layer.id) { mutableIntStateOf(if (layer.cutout > 0) layer.cutout else Cutout.DEFAULT_TOLERANCE) }
    val note = assets.cutoutNote(layer.url, layer.cutout).takeIf { layer.cutout != 0 }
    TrySheet(onDismiss) {
        SheetTitle("Recorte de *fondo*", "Compara antes y después. Puedes volver a la original cuando quieras.")
        Box(
            Modifier.padding(horizontal = EvSpace.screen).fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp))
                .pointerInput(Unit) { detectHorizontalDragGestures { change, _ -> split = (change.position.x / size.width).coerceIn(0.02f, 0.98f) } },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                revision.value
                // Tablero de ajedrez
                val cell = 14.dp.toPx()
                var y = 0f; var row = 0
                while (y < size.height) {
                    var x = 0f; var col = row % 2
                    while (x < size.width) {
                        drawRect(if (col % 2 == 0) Color(0xFF3A3A44) else Color(0xFF2A2A32), Offset(x, y), Size(cell, cell)); x += cell; col++
                    }
                    y += cell; row++
                }
                fun fit(img: ImageBitmap?): Triple<Offset, Size, ImageBitmap>? {
                    img ?: return null
                    val s = minOf(size.width / img.width, size.height / img.height) * 0.92f
                    val w = img.width * s; val h = img.height * s
                    return Triple(Offset((size.width - w) / 2f, (size.height - h) / 2f), Size(w, h), img)
                }
                val after = fit(assets.garment(layer.url, layer.cutout))
                val before = fit(assets.garment(layer.url, 0))
                after?.let { (o, sz, img) -> drawImage(img, dstOffset = IntOffset(o.x.roundToInt(), o.y.roundToInt()), dstSize = IntSize(sz.width.roundToInt(), sz.height.roundToInt())) }
                if (layer.cutout != 0) before?.let { (o, sz, img) ->
                    clipRect(0f, 0f, size.width * split, size.height) {
                        drawRect(Color(0xFFE7E7EE), Offset.Zero, size)
                        drawImage(img, dstOffset = IntOffset(o.x.roundToInt(), o.y.roundToInt()), dstSize = IntSize(sz.width.roundToInt(), sz.height.roundToInt()))
                    }
                    val x = size.width * split
                    drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), 3.dp.toPx())
                    drawCircle(Color.White, 14.dp.toPx(), Offset(x, size.height / 2f))
                }
            }
            if (layer.cutout != 0) {
                Text("Antes", style = EvType.caption, color = Color.Black, modifier = Modifier.align(Alignment.TopStart).padding(10.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.85f)).padding(horizontal = 10.dp, vertical = 4.dp))
                Text("Después", style = EvType.caption, color = Color.Black, modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.85f)).padding(horizontal = 10.dp, vertical = 4.dp))
            }
            if (busy || status == CutoutModelStatus.Preparing) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                Text(if (status == CutoutModelStatus.Preparing) "Preparando el recorte…" else "Recortando…", style = EvType.label, color = Color.White)
            }
        }
        Column(Modifier.padding(horizontal = EvSpace.screen).padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            note?.let { Text(it, style = EvType.bodySmall, color = c.warning) }
            if (status == CutoutModelStatus.Unavailable) Text("El recorte inteligente no está disponible ahora (sin conexión): usa el recorte básico.", style = EvType.bodySmall, color = c.inkSoft)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EvPillButton("Inteligente", onSmart, Modifier.weight(1f), height = 48.dp, icon = Icons.Rounded.AutoAwesome, loading = busy, style = if (layer.cutout < 0) EvButtonStyle.Primary else EvButtonStyle.Secondary)
                EvPillButton("Básico", { onBasic(tolerance, true) }, Modifier.weight(1f), height = 48.dp, icon = Icons.Rounded.AutoFixHigh, style = if (layer.cutout > 0) EvButtonStyle.Primary else EvButtonStyle.Secondary)
            }
            if (layer.cutout > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tolerancia", style = EvType.caption, color = c.inkSoft, modifier = Modifier.padding(end = 10.dp))
                Slider(
                    tolerance.toFloat(), { tolerance = it.roundToInt(); onBasic(tolerance, false) }, onValueChangeFinished = { onBasic(tolerance, true) },
                    valueRange = 1f..Cutout.MAX_TOLERANCE.toFloat(), modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = c.ink, activeTrackColor = c.ink, inactiveTrackColor = c.outline),
                )
            }
            EvPillButton("Restaurar original", onRestore, Modifier.fillMaxWidth(), style = EvButtonStyle.Ghost, height = 46.dp, enabled = layer.cutout != 0)
        }
    }
}
