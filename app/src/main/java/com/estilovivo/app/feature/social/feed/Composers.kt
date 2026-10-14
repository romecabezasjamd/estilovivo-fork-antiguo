package com.estilovivo.app.feature.social.feed

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.estilovivo.app.core.data.ImageUpload
import com.estilovivo.app.core.data.model.Garment
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.InstrumentSerif
import com.estilovivo.app.core.design.components.LocalEvToast
import com.estilovivo.app.core.design.components.EvButtonStyle
import com.estilovivo.app.core.design.components.EvCircleButton
import com.estilovivo.app.core.design.components.EvHeadline
import com.estilovivo.app.core.design.components.EvImage
import com.estilovivo.app.core.design.components.EvPillButton
import com.estilovivo.app.core.design.components.EvSegmented
import com.estilovivo.app.core.design.components.EvTextField
import com.estilovivo.app.core.design.evClickable
import kotlinx.coroutines.launch

/** Convierte un `Uri` en `ImageUpload` avisando si no se puede leer. */
@Composable
private fun rememberUploadPreparer(): suspend (Uri) -> ImageUpload? {
    val context = LocalContext.current
    val toast = LocalEvToast.current
    return remember(context) {
        { uri: Uri ->
            ImageUpload.fromUri(context, uri) ?: run { toast.error("No se pudo leer la imagen. Prueba con otra foto."); null }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Publicar en el feed
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PublishSheet(
    photo: String?,
    text: String,
    publishing: Boolean,
    onPhoto: (String?) -> Unit,
    onText: (String) -> Unit,
    onPublish: (ImageUpload, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = EvTheme.colors
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val picker = rememberPhotoPicker { onPhoto(it.toString()) }
    val prepare = rememberUploadPreparer()
    val scope = rememberCoroutineScope()
    var preparing by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = { if (!publishing) onDismiss() }, sheetState = sheet, containerColor = c.bg, shape = EvShapes.sheet) {
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            EvHeadline("Comparte tu *look*")
            Text("Tu foto aparecerá en el feed de la comunidad.", style = EvType.body, color = c.inkSoft)

            AnimatedContent(
                targetState = photo, label = "publishPhoto",
                transitionSpec = { fadeIn() togetherWith fadeOut() }, modifier = Modifier.animateContentSize(),
            ) { p ->
                if (p == null) {
                    Column(
                        Modifier.fillMaxWidth().aspectRatio(1.2f).clip(EvShapes.card).background(c.surface)
                            .border(1.dp, c.outline, EvShapes.card).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Añade una foto de tu outfit", style = EvType.subtitle, color = c.ink, textAlign = TextAlign.Center)
                        PhotoSourceButtons(picker)
                    }
                } else {
                    Box(Modifier.fillMaxWidth().aspectRatio(0.9f).clip(EvShapes.card).background(c.surfaceAlt)) {
                        EvImage(p, "Vista previa de tu publicación", Modifier.fillMaxSize())
                        EvCircleButton(
                            Icons.Rounded.Close, { onPhoto(null) }, Modifier.align(Alignment.TopEnd).padding(12.dp),
                            contentDescription = "Quitar foto", size = 40.dp, container = Color(0x99000000), tint = Color.White,
                        )
                    }
                }
            }
            if (photo != null) PhotoSourceButtons(picker, height = 48.dp)

            EvTextField(
                value = text, onValueChange = { if (it.length <= FeedViewModel.TITLE_MAX) onText(it) },
                label = "Descripción (opcional)", singleLine = false, minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Text("${text.length}/${FeedViewModel.TITLE_MAX}", style = EvType.caption, color = c.inkMuted, modifier = Modifier.align(Alignment.End))

            EvPillButton(
                "Publicar", onClick = {
                    val uri = photo?.let(Uri::parse) ?: return@EvPillButton
                    scope.launch {
                        preparing = true
                        val upload = prepare(uri)
                        preparing = false
                        if (upload != null) onPublish(upload, text)
                    }
                },
                modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.Send,
                loading = publishing || preparing, enabled = photo != null,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Crear historia
// ─────────────────────────────────────────────────────────────────────────────

private enum class StoryMode(val label: String) { Photo("Foto"), Text("Texto") }

@Composable
fun StoryComposerSheet(
    creating: Boolean,
    garments: List<Garment>,
    onLoadGarments: () -> Unit,
    onPublish: (type: String, text: String?, image: ImageUpload?, existingUrl: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = EvTheme.colors
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var mode by rememberSaveable { mutableStateOf(StoryMode.Photo) }
    var photo by rememberSaveable { mutableStateOf<String?>(null) }
    var garmentUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var overlay by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    val picker = rememberPhotoPicker { photo = it.toString(); garmentUrl = null }
    val prepare = rememberUploadPreparer()
    val scope = rememberCoroutineScope()
    var preparing by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { onLoadGarments() }

    val imageModel: Any? = photo ?: garmentUrl
    val canPublish = when (mode) {
        StoryMode.Photo -> imageModel != null
        StoryMode.Text -> body.isNotBlank()
    }

    ModalBottomSheet(onDismissRequest = { if (!creating) onDismiss() }, sheetState = sheet, containerColor = c.bg, shape = EvShapes.sheet) {
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            EvHeadline("Nueva *historia*")
            Text("Se queda 24 horas y la ven quienes te siguen y los perfiles públicos.", style = EvType.body, color = c.inkSoft)
            EvSegmented(StoryMode.entries, mode, { mode = it }, { it.label }, Modifier.fillMaxWidth())

            AnimatedContent(targetState = mode, label = "storyMode", transitionSpec = { fadeIn() togetherWith fadeOut() }, modifier = Modifier.animateContentSize()) { m ->
                when (m) {
                    StoryMode.Photo -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (imageModel == null) {
                            PhotoSourceButtons(picker)
                        } else {
                            Box(Modifier.fillMaxWidth().aspectRatio(0.72f).clip(EvShapes.card).background(Color.Black)) {
                                EvImage(imageModel, "Vista previa de la historia", Modifier.fillMaxSize())
                                if (overlay.isNotBlank()) {
                                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(scrimBrush(false)).padding(20.dp).padding(top = 30.dp)) {
                                        Text(overlay, style = EvType.title.copy(color = Color.White), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                    }
                                }
                                EvCircleButton(
                                    Icons.Rounded.Close, { photo = null; garmentUrl = null }, Modifier.align(Alignment.TopEnd).padding(12.dp),
                                    contentDescription = "Quitar foto", size = 40.dp, container = Color(0x99000000), tint = Color.White,
                                )
                            }
                            PhotoSourceButtons(picker, height = 46.dp)
                            EvTextField(
                                overlay, { if (it.length <= 120) overlay = it }, "Texto sobre la foto (opcional)",
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            )
                        }
                        if (garments.isNotEmpty()) {
                            Text("O desde tu armario", style = EvType.label, color = c.inkSoft)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 4.dp)) {
                                items(garments, key = { it.id }) { g ->
                                    val selected = garmentUrl == g.imageUrl
                                    Box(
                                        Modifier.size(78.dp).clip(EvShapes.cardSmall).background(c.surface)
                                            .border(if (selected) 2.dp else 1.dp, if (selected) c.ink else c.outline, EvShapes.cardSmall)
                                            .evClickable { garmentUrl = g.imageUrl; photo = null }
                                            .semantics { contentDescription = "Usar ${g.name} en la historia" },
                                    ) { EvImage(g.imageUrl, g.name, Modifier.fillMaxSize()) }
                                }
                            }
                        }
                    }

                    StoryMode.Text -> Box(
                        Modifier.fillMaxWidth().aspectRatio(0.8f).clip(EvShapes.card)
                            .background(Brush.linearGradient(listOf(Color(0xFF0B0B0F), Color(0xFF3B3B4F)))).padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (body.isEmpty()) Text("Escribe una frase o reflexión…", style = StoryTextStyle.copy(color = Color.White.copy(alpha = 0.5f)), textAlign = TextAlign.Center)
                        BasicTextField(
                            value = body, onValueChange = { if (it.length <= STORY_TEXT_MAX) body = it },
                            textStyle = StoryTextStyle.copy(color = Color.White, textAlign = TextAlign.Center),
                            cursorBrush = SolidColor(Color.White),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            if (mode == StoryMode.Text) Text("${body.length}/$STORY_TEXT_MAX", style = EvType.caption, color = c.inkMuted, modifier = Modifier.align(Alignment.End))

            EvPillButton(
                "Publicar historia",
                onClick = {
                    scope.launch {
                        when (mode) {
                            StoryMode.Text -> onPublish("text", body, null, null)
                            StoryMode.Photo -> {
                                val p = photo
                                if (p != null) {
                                    preparing = true
                                    val upload = prepare(Uri.parse(p))
                                    preparing = false
                                    if (upload != null) onPublish("image", overlay, upload, null)
                                } else if (garmentUrl != null) onPublish("image", overlay, null, garmentUrl)
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.Send, loading = creating || preparing, enabled = canPublish,
            )
        }
    }
}

val StoryTextStyle: TextStyle = TextStyle(
    fontFamily = InstrumentSerif, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
    fontSize = androidx.compose.ui.unit.TextUnit(30f, androidx.compose.ui.unit.TextUnitType.Sp),
    lineHeight = androidx.compose.ui.unit.TextUnit(36f, androidx.compose.ui.unit.TextUnitType.Sp),
)
