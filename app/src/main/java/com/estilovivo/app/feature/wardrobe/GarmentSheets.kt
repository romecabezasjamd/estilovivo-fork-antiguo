package com.estilovivo.app.feature.wardrobe

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.estilovivo.app.core.data.ImageUpload
import com.estilovivo.app.core.data.model.Garment
import com.estilovivo.app.core.design.EvMotion
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.EvButtonStyle
import com.estilovivo.app.core.design.components.EvChip
import com.estilovivo.app.core.design.components.EvHeadline
import com.estilovivo.app.core.design.components.EvImage
import com.estilovivo.app.core.design.components.EvPillButton
import com.estilovivo.app.core.design.components.EvSegmented
import com.estilovivo.app.core.design.components.EvTextField
import com.estilovivo.app.core.design.evClickable
import kotlinx.coroutines.launch

// ── Esqueleto común de los formularios ───────────────────────────────────────────────────────

@Composable
private fun FormScaffold(
    headline: String,
    primaryLabel: String,
    loading: Boolean,
    onPrimary: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().imePadding()) {
        EvHeadline(headline, Modifier.padding(horizontal = EvSheetPadding, vertical = 6.dp))
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = EvSheetPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { content() }
        Row(Modifier.fillMaxWidth().padding(horizontal = EvSheetPadding, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EvPillButton("Cancelar", onCancel, Modifier.weight(1f), style = EvButtonStyle.Secondary, enabled = !loading, height = 54.dp)
            EvPillButton(primaryLabel, onPrimary, Modifier.weight(2f), loading = loading, height = 54.dp)
        }
    }
}

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(label, Modifier.padding(start = 4.dp))
        content()
    }
}

@Composable
private fun PhotoField(
    photoUri: String?,
    currentImage: Any?,
    error: String?,
    onUri: (String?) -> Unit,
    onProblem: (String) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Añade una foto de la prenda",
) {
    val c = EvTheme.colors
    val picker = rememberPhotoPicker(onPicked = { onUri(it.toString()) }, onProblem = onProblem)
    val shown: Any? = photoUri?.let { Uri.parse(it) } ?: currentImage
    Column(modifier.animateContentSize(EvMotion.smooth()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (shown == null) {
            Column(
                Modifier.fillMaxWidth().aspectRatio(4f / 3f).dashedBorder(if (error != null) c.danger else c.outline)
                    .clip(EvShapes.card).background(c.surface).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            ) {
                Icon(Icons.Rounded.AddAPhoto, null, tint = c.inkMuted, modifier = Modifier.size(34.dp))
                Text(title, style = EvType.label, color = c.inkSoft)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    EvPillButton("Cámara", picker.camera, icon = Icons.Rounded.CameraAlt, height = 46.dp)
                    EvPillButton("Galería", picker.gallery, style = EvButtonStyle.Secondary, icon = Icons.Rounded.PhotoLibrary, height = 46.dp)
                }
            }
        } else {
            Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(EvShapes.card).background(c.surfaceAlt)) {
                AnimatedContent(shown, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) }, label = "photo") { m ->
                    EvImage(m, "Foto de la prenda", Modifier.fillMaxWidth().aspectRatio(4f / 3f), contentScale = ContentScale.Fit)
                }
                Row(Modifier.align(Alignment.BottomEnd).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallRoundButton(Icons.Rounded.CameraAlt, "Hacer otra foto", picker.camera)
                    SmallRoundButton(Icons.Rounded.PhotoLibrary, "Elegir otra de la galería", picker.gallery)
                }
                if (photoUri != null) {
                    SmallRoundButton(Icons.Rounded.Close, "Quitar la foto", { onUri(null) }, Modifier.align(Alignment.TopEnd).padding(10.dp))
                }
            }
        }
        ErrorBanner(error)
    }
}

@Composable
private fun SmallRoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    Box(
        modifier.size(44.dp).clip(CircleShape).background(c.surface.copy(alpha = 0.92f)).evClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = c.ink, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun ColorSuggestions(current: String, onPick: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(WardrobeCatalog.suggestedColors, key = { it }) { name ->
            val selected = WardrobeCatalog.fold(current) == WardrobeCatalog.fold(name)
            EvChip(text = name.replaceFirstChar { it.uppercase() }, selected = selected, onClick = { onPick(if (selected) "" else name) })
        }
    }
}

private fun filterPrice(raw: String): String {
    val out = StringBuilder()
    var sep = false
    var decimals = 0
    for (ch in raw) {
        when {
            ch.isDigit() -> if (!sep) { if (out.length < 6) out.append(ch) } else if (decimals < 2) { out.append(ch); decimals++ }
            (ch == ',' || ch == '.') && !sep && out.isNotEmpty() -> { out.append(ch); sep = true }
        }
    }
    return out.toString()
}

@Composable
private fun PriceField(value: String, onChange: (String) -> Unit, error: String?) {
    EvTextField(
        value = value, onValueChange = { onChange(filterPrice(it)) }, label = "Precio de venta (€)", error = error,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

@Composable
private fun LimitedField(value: String, onChange: (String) -> Unit, label: String, max: Int, modifier: Modifier = Modifier, error: String? = null, singleLine: Boolean = true, minLines: Int = 1) {
    EvTextField(
        value = value, onValueChange = { onChange(it.take(max)) }, label = label, modifier = modifier, error = error,
        singleLine = singleLine, minLines = minLines,
    )
}

private const val READ_ERROR = "No se pudo leer la foto. Vuelve a elegirla."

// ── Nueva prenda ─────────────────────────────────────────────────────────────────────────────

/** Alta de prenda: la foto es obligatoria; al confirmar se cierra y la prenda aparece al instante. */
@Composable
fun AddGarmentSheet(
    initial: GarmentForm,
    initialError: String?,
    onSubmit: (GarmentForm, ImageUpload) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var form by rememberSaveable(stateSaver = GarmentForm.Saver) { mutableStateOf(initial) }
    var errors by remember { mutableStateOf(FormErrors()) }
    var photoProblem by remember { mutableStateOf<String?>(null) }
    var converting by remember { mutableStateOf(false) }
    var serverError by remember { mutableStateOf(initialError) }
    val cutout = rememberPhotoCutout(form.photoUri)

    FormScaffold(
        headline = "Nueva *prenda*", primaryLabel = "Añadir al armario", loading = converting,
        onPrimary = {
            val e = GarmentFormRules.validate(form, requirePhoto = true, requirePrice = false)
            errors = e
            if (e.any) return@FormScaffold
            scope.launch {
                converting = true
                val chosen = cutout.effectiveUri(form.photoUri)
                val up = readGarmentUpload(context, chosen, form.photoUri)
                converting = false
                if (up == null) photoProblem = READ_ERROR else { onSubmit(form.copy(photoUri = chosen), up); onClose() }
            }
        },
        onCancel = onClose,
    ) {
        ErrorBanner(serverError)
        PhotoField(form.photoUri, null, photoProblem ?: errors.photo, { form = form.copy(photoUri = it); photoProblem = null; errors = errors.copy(photo = null) }, { photoProblem = it })
        CutoutSection(cutout, form.photoUri)
        LimitedField(form.name, { form = form.copy(name = it); serverError = null; if (errors.name != null) errors = errors.copy(name = null) }, "Nombre (p. ej. Camiseta de rayas)", GarmentLimits.NAME, error = errors.name)
        Field("Categoría") { OptionChips(WardrobeCatalog.categories, form.category, { form = form.copy(category = it) }) }
        Field("Color") {
            LimitedField(form.color, { form = form.copy(color = it) }, "Color", GarmentLimits.COLOR)
            ColorSuggestions(form.color) { form = form.copy(color = it) }
        }
        LimitedField(form.brand, { form = form.copy(brand = it) }, "Marca (opcional)", GarmentLimits.BRAND)
        Field("Temporada") { OptionChips(WardrobeCatalog.seasons, form.season, { form = form.copy(season = it) }) }
    }
}

// ── Editar prenda ────────────────────────────────────────────────────────────────────────────

/** Edición completa. El resultado (y el error del servidor) llega desde fuera; la hoja la cierra quien la abrió. */
@Composable
fun EditGarmentSheet(
    garment: Garment,
    submitting: Boolean,
    serverError: String?,
    onSubmit: (GarmentForm, ImageUpload?) -> Unit,
    onClose: () -> Unit,
    onClearServerError: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var form by rememberSaveable(garment.id, stateSaver = GarmentForm.Saver) { mutableStateOf(GarmentForm.of(garment)) }
    var errors by remember { mutableStateOf(FormErrors()) }
    var photoProblem by remember { mutableStateOf<String?>(null) }
    var converting by remember { mutableStateOf(false) }
    val cutout = rememberPhotoCutout(form.photoUri)

    FormScaffold(
        headline = "Editar *prenda*", primaryLabel = "Guardar cambios", loading = submitting || converting,
        onPrimary = {
            val e = GarmentFormRules.validate(form, requirePhoto = false, requirePrice = form.forSale)
            errors = e
            if (e.any) return@FormScaffold
            scope.launch {
                var up: ImageUpload? = null
                var chosen = form.photoUri
                if (form.photoUri != null) {
                    converting = true
                    chosen = cutout.effectiveUri(form.photoUri)
                    up = readGarmentUpload(context, chosen, form.photoUri)
                    converting = false
                    if (up == null) { photoProblem = READ_ERROR; return@launch }
                }
                onSubmit(form.copy(photoUri = chosen), up)
            }
        },
        onCancel = onClose,
    ) {
        ErrorBanner(serverError)
        PhotoField(form.photoUri, garment.imageUrl, photoProblem, { form = form.copy(photoUri = it); photoProblem = null }, { photoProblem = it }, title = "Cambia la foto")
        CutoutSection(cutout, form.photoUri)
        LimitedField(form.name, { form = form.copy(name = it); onClearServerError(); if (errors.name != null) errors = errors.copy(name = null) }, "Nombre", GarmentLimits.NAME, error = errors.name)
        Field("Categoría") { OptionChips(WardrobeCatalog.categories, form.category, { form = form.copy(category = it) }) }
        Field("Color") {
            LimitedField(form.color, { form = form.copy(color = it) }, "Color", GarmentLimits.COLOR)
            ColorSuggestions(form.color) { form = form.copy(color = it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LimitedField(form.brand, { form = form.copy(brand = it) }, "Marca", GarmentLimits.BRAND, Modifier.weight(1f))
            LimitedField(form.size, { form = form.copy(size = it) }, "Talla", GarmentLimits.SIZE, Modifier.weight(0.7f))
        }
        Field("Temporada") { OptionChips(WardrobeCatalog.seasons, form.season, { form = form.copy(season = it) }) }
        Field("Estado") { OptionChips(WardrobeCatalog.conditions, form.condition, { form = form.copy(condition = it) }) }
        LimitedField(form.description, { form = form.copy(description = it) }, "Descripción", GarmentLimits.DESCRIPTION, singleLine = false, minLines = 3)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("En venta", style = EvType.subtitle, color = EvTheme.colors.ink)
                Text("Al quitarla de la venta se borra el precio.", style = EvType.caption, color = EvTheme.colors.inkMuted)
            }
            WardrobeSwitch(form.forSale, { form = form.copy(forSale = it); onClearServerError() }, "Prenda en venta")
        }
        AnimatedVisibility(form.forSale, enter = expandVertically(EvMotion.smooth()) + fadeIn(), exit = shrinkVertically(EvMotion.snappy()) + fadeOut()) {
            PriceField(form.price, { form = form.copy(price = it); errors = errors.copy(price = null) }, errors.price)
        }
    }
}

// ── Poner en venta ───────────────────────────────────────────────────────────────────────────

private enum class SaleMode(val label: String) { New("Prenda nueva"), Existing("De mi armario") }

/**
 * Flujo de venta. Con [preselected] vende esa prenda; sin ella permite subir una prenda nueva directamente
 * a la venta o elegir una de [eligible]. Las ventas de prendas existentes devuelven su resultado desde fuera
 * ([submitting]/[serverError]); las nuevas usan el alta optimista.
 */
@Composable
fun SaleSheet(
    preselected: Garment?,
    eligible: List<Garment>,
    initial: GarmentForm,
    initialError: String?,
    submitting: Boolean,
    serverError: String?,
    onSubmitNew: (GarmentForm, ImageUpload) -> Unit,
    onSubmitExisting: (Garment, GarmentForm) -> Unit,
    onClose: () -> Unit,
    onClearServerError: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by rememberSaveable { mutableStateOf(if (preselected != null) SaleMode.Existing else SaleMode.New) }
    var pickedId by rememberSaveable { mutableStateOf(preselected?.id) }
    val chosen = preselected ?: eligible.firstOrNull { it.id == pickedId }
    var form by rememberSaveable(stateSaver = GarmentForm.Saver) {
        mutableStateOf(
            if (preselected != null) saleFormFor(preselected) else initial.copy(forSale = true),
        )
    }
    var errors by remember { mutableStateOf(FormErrors()) }
    var photoProblem by remember { mutableStateOf<String?>(null) }
    var converting by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf(initialError) }
    val cutout = rememberPhotoCutout(if (mode == SaleMode.New) form.photoUri else null)

    val existingMode = mode == SaleMode.Existing
    FormScaffold(
        headline = "Poner en *venta*", primaryLabel = "Publicar ahora", loading = (existingMode && submitting) || converting,
        onPrimary = {
            localError = null
            if (existingMode) {
                val g = chosen
                if (g == null) { localError = "Elige la prenda que quieres vender."; return@FormScaffold }
                val e = GarmentFormRules.validate(form.copy(name = g.name), requirePhoto = false, requirePrice = true)
                errors = e
                if (e.any) return@FormScaffold
                onSubmitExisting(g, form)
            } else {
                val e = GarmentFormRules.validate(form, requirePhoto = true, requirePrice = true)
                errors = e
                if (e.any) return@FormScaffold
                scope.launch {
                    converting = true
                    val chosen = cutout.effectiveUri(form.photoUri)
                    val up = readGarmentUpload(context, chosen, form.photoUri)
                    converting = false
                    if (up == null) photoProblem = READ_ERROR else { onSubmitNew(form.copy(photoUri = chosen), up); onClose() }
                }
            }
        },
        onCancel = onClose,
    ) {
        ErrorBanner(localError ?: serverError)
        if (preselected == null) {
            EvSegmented(SaleMode.entries, mode, { mode = it; localError = null }, { it.label }, Modifier.fillMaxWidth())
        }
        AnimatedContent(existingMode, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "saleMode") { existing ->
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (existing) {
                    if (preselected != null) {
                        GarmentSummary(preselected)
                    } else if (eligible.isEmpty()) {
                        Text(
                            "No tienes prendas libres para vender. Sube una nueva o retira alguna de la venta.",
                            style = EvType.body, color = EvTheme.colors.inkSoft,
                        )
                    } else {
                        Field("Elige una prenda") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(eligible, key = { it.id }) { g ->
                                    EligibleThumb(g, selected = g.id == pickedId) {
                                        pickedId = g.id
                                        form = saleFormFor(g).copy(price = form.price)
                                        localError = null
                                    }
                                }
                            }
                        }
                    }
                } else {
                    PhotoField(form.photoUri, null, photoProblem ?: errors.photo, { form = form.copy(photoUri = it); photoProblem = null; errors = errors.copy(photo = null) }, { photoProblem = it }, title = "Foto de la prenda")
                    CutoutSection(cutout, form.photoUri)
                    LimitedField(form.name, { form = form.copy(name = it); if (errors.name != null) errors = errors.copy(name = null) }, "Nombre (p. ej. Vestido floral)", GarmentLimits.NAME, error = errors.name)
                    Field("Categoría") { OptionChips(WardrobeCatalog.categories, form.category, { form = form.copy(category = it) }) }
                }
                if (!existing || preselected != null || chosen != null) {
                    PriceField(form.price, { form = form.copy(price = it); errors = errors.copy(price = null); onClearServerError() }, errors.price)
                    Field("Estado") { OptionChips(WardrobeCatalog.conditions, form.condition, { form = form.copy(condition = it) }) }
                    LimitedField(form.size, { form = form.copy(size = it) }, "Talla (M, 42…)", GarmentLimits.SIZE)
                    LimitedField(form.description, { form = form.copy(description = it) }, "Descripción: estado, estilo, uso…", GarmentLimits.DESCRIPTION, singleLine = false, minLines = 3)
                }
            }
        }
    }
}

/** Valores iniciales del formulario de venta para una prenda del armario. */
fun saleFormFor(g: Garment) = GarmentForm(
    name = g.name, category = WardrobeCatalog.categoryOf(g.type), size = g.size.orEmpty(),
    condition = g.condition.takeIf { it != "new" } ?: "good", description = g.description.orEmpty(),
    price = g.price.takeIf { it > 0 }?.let(::formatPriceInput).orEmpty(), forSale = true,
)

@Composable
private fun GarmentSummary(g: Garment) {
    val c = EvTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(EvShapes.cardSmall).background(c.surface).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EvImage(g.imageUrl, g.name, Modifier.size(56.dp).clip(EvShapes.field))
        Column {
            Text(g.name, style = EvType.subtitle, color = c.ink, maxLines = 1)
            Text(
                listOfNotNull(g.color.takeIf { it.isNotBlank() }, g.brand ?: "Sin marca").joinToString(" · "),
                style = EvType.caption, color = c.inkMuted, maxLines = 1,
            )
        }
    }
}

@Composable
private fun EligibleThumb(g: Garment, selected: Boolean, onClick: () -> Unit) {
    val c = EvTheme.colors
    val ring = if (selected) c.ink else androidx.compose.ui.graphics.Color.Transparent
    Column(
        Modifier.size(width = 92.dp, height = 132.dp).clip(EvShapes.cardSmall).background(c.surface)
            .evClickable(pressedScale = 0.94f, onClick = onClick)
            .then(Modifier.padding(4.dp)),
    ) {
        Box(
            Modifier.fillMaxWidth().weight(1f).clip(EvShapes.field).background(ring).padding(if (selected) 2.dp else 0.dp),
        ) { EvImage(g.imageUrl, g.name, Modifier.matchParentSize().clip(EvShapes.field)) }
        Text(g.name, style = EvType.caption, color = c.ink, maxLines = 1, modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp))
    }
}
