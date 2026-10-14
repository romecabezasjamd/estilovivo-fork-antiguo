package com.estilovivo.app.feature.social.feed

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.estilovivo.app.core.data.ImageUpload
import com.estilovivo.app.core.data.model.Challenge
import com.estilovivo.app.core.data.model.User
import com.estilovivo.app.core.design.EvMotion
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.LocalEvToast
import com.estilovivo.app.core.design.components.EvButtonStyle
import com.estilovivo.app.core.design.components.EvCircleButton
import com.estilovivo.app.core.design.components.EvHeadline
import com.estilovivo.app.core.design.components.EvImage
import com.estilovivo.app.core.design.components.EvPillButton
import com.estilovivo.app.core.design.components.EvShimmerBox
import com.estilovivo.app.core.design.components.EvTextField
import com.estilovivo.app.core.design.evClickable
import kotlinx.coroutines.launch

private val CardTop = Color(0xFF0B0B0F)
private val CardBottom = Color(0xFF2A1C3D)

/** Tarjeta del reto semanal (siempre oscura: se lee igual en claro y oscuro). */
@Composable
fun ChallengeCard(
    state: ChallengeUiState,
    me: User?,
    onParticipate: () -> Unit,
    onDeleteSubmission: () -> Unit,
    onHistory: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = EvTheme.colors
    val challenge = state.challenge
    when {
        state.loading && challenge == null -> EvShimmerBox(modifier.fillMaxWidth().height(210.dp), EvShapes.hero)
        challenge == null -> Row(
            modifier.fillMaxWidth().clip(EvShapes.card).background(c.surface).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.CloudOff, null, tint = c.inkMuted)
            Column(Modifier.weight(1f)) {
                Text("Reto semanal", style = EvType.label, color = c.ink)
                Text(state.error ?: "No se pudo cargar el reto.", style = EvType.bodySmall, color = c.inkSoft)
            }
            EvPillButton("Reintentar", onRetry, height = 40.dp, style = EvButtonStyle.Secondary)
        }
        else -> ChallengeCardContent(challenge, state, me, onParticipate, onDeleteSubmission, onHistory, modifier)
    }
}

@Composable
private fun ChallengeCardContent(
    challenge: Challenge,
    state: ChallengeUiState,
    me: User?,
    onParticipate: () -> Unit,
    onDeleteSubmission: () -> Unit,
    onHistory: () -> Unit,
    modifier: Modifier,
) {
    val c = EvTheme.colors
    val done = state.submission != null
    var menu by remember { mutableStateOf(false) }
    val tips = remember(challenge.tag) { challengeTips(challenge.tag) }
    val left = remember(challenge.endDate) { challengeTimeLeft(challenge.endDate) }
    Column(
        modifier
            .fillMaxWidth()
            .clip(EvShapes.hero)
            .background(Brush.verticalGradient(listOf(CardTop, CardBottom)))
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(listOf(c.accent.copy(alpha = 0.55f), Color.Transparent), center = Offset(size.width * 0.92f, 0f), radius = size.width * 0.6f),
                    radius = size.width * 0.6f, center = Offset(size.width * 0.92f, 0f),
                )
            }
            .padding(20.dp)
            .animateContentSize(EvMotion.smooth()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(16.dp))
            EvOverline("Reto semanal", color = Color.White.copy(alpha = 0.75f), modifier = Modifier.weight(1f))
            if (left != null) EvPill(left, container = Color.White.copy(alpha = 0.14f), content = Color.White)
        }
        Text(
            challenge.title, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = EvType.headlineAccent.copy(fontSize = androidx.compose.ui.unit.TextUnit(38f, androidx.compose.ui.unit.TextUnitType.Sp), lineHeight = androidx.compose.ui.unit.TextUnit(40f, androidx.compose.ui.unit.TextUnitType.Sp)),
        )
        if (challenge.description.isNotBlank()) {
            Text(challenge.description, style = EvType.body, color = Color.White.copy(alpha = 0.78f), maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 8.dp)) {
            items(tips, key = { it }) { tip -> EvPill(tip, container = Color.White.copy(alpha = 0.12f), content = Color.White.copy(alpha = 0.9f)) }
        }

        if (me != null) {
            val p = remember(me.experiencePoints, me.level) { xpProgress(me.experiencePoints, me.level) }
            val fill by animateFloatAsState(p.percentage / 100f, tween(900, easing = EvMotion.emphasized), label = "xpFill")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Nv. ${me.level} · ${levelTitle(me.level)}", style = EvType.caption, color = Color.White.copy(alpha = 0.8f))
                    Text("${p.current} / ${p.needed} XP", style = EvType.caption, color = Color.White.copy(alpha = 0.8f))
                }
                Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(fill.coerceIn(0.02f, 1f)).clip(CircleShape).background(Color.White))
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (done) {
                Row(
                    Modifier.clip(CircleShape).background(c.success.copy(alpha = 0.2f)).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF4ADE80), modifier = Modifier.size(18.dp))
                    Text("Completado · +${challenge.reward} XP", style = EvType.label, color = Color.White)
                }
                Box {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).evClickable(enabled = !state.deleting) { menu = true }
                            .semantics { contentDescription = "Opciones de tu participación" },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.MoreHoriz, null, tint = Color.White) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = c.surface, shape = EvShapes.cardSmall) {
                        DropdownMenuItem(
                            text = { Text("Eliminar participación", style = EvType.label, color = c.danger) },
                            leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = c.danger) },
                            onClick = { menu = false; onDeleteSubmission() },
                        )
                    }
                }
            } else {
                EvPillButton("Participar · +${challenge.reward} XP", onParticipate, Modifier.weight(1f, fill = false), style = EvButtonStyle.Light, height = 50.dp, loading = state.deleting)
            }
            Box(Modifier.weight(1f))
            Text(
                "Historial", style = EvType.label, color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.clip(CircleShape).evClickable(onClick = onHistory).padding(horizontal = 12.dp, vertical = 12.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Hoja de participación
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ChallengeSheet(
    challenge: Challenge,
    photo: String?,
    description: String,
    submitting: Boolean,
    error: String?,
    onPhoto: (String?) -> Unit,
    onDescription: (String) -> Unit,
    onSubmit: (ImageUpload?, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = EvTheme.colors
    val context = LocalContext.current
    val toast = LocalEvToast.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val picker = rememberPhotoPicker { onPhoto(it.toString()) }
    val scope = rememberCoroutineScope()
    var preparing by remember { mutableStateOf(false) }
    val tips = remember(challenge.tag) { challengeTips(challenge.tag) }
    val tooShort = description.isNotBlank() && description.trim().length < CHALLENGE_DESC_MIN

    ModalBottomSheet(onDismissRequest = { if (!submitting) onDismiss() }, sheetState = sheet, containerColor = c.bg, shape = EvShapes.sheet) {
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    EvOverline("Reto semanal · +${challenge.reward} XP")
                    EvHeadline("*${challenge.title}*")
                }
            }
            Text(challenge.description, style = EvType.body, color = c.inkSoft)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tips.forEach { EvPill(it, container = c.surface, content = c.inkSoft) }
            }

            Text("Foto de tu look *", style = EvType.label, color = c.ink)
            if (photo == null) {
                PhotoSourceButtons(picker)
            } else {
                Box(Modifier.fillMaxWidth().aspectRatio(0.9f).clip(EvShapes.card).background(c.surfaceAlt)) {
                    EvImage(photo, "Vista previa de tu participación", Modifier.fillMaxSize())
                    EvCircleButton(
                        Icons.Rounded.Close, { onPhoto(null) }, Modifier.align(Alignment.TopEnd).padding(12.dp),
                        contentDescription = "Quitar foto", size = 40.dp, container = Color(0x99000000), tint = Color.White,
                    )
                }
                PhotoSourceButtons(picker, height = 46.dp)
            }

            EvTextField(
                value = description, onValueChange = { if (it.length <= CHALLENGE_DESC_MAX) onDescription(it) },
                label = "Cuéntanos cómo creaste este look", singleLine = false, minLines = 3,
                error = if (tooShort) "Mínimo $CHALLENGE_DESC_MIN caracteres" else null,
            )
            Text(
                "${description.trim().length}/$CHALLENGE_DESC_MAX", style = EvType.caption,
                color = if (tooShort) c.warning else c.inkMuted, modifier = Modifier.align(Alignment.End),
            )

            AnimatedVisibility(visible = error != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Row(
                    Modifier.fillMaxWidth().clip(EvShapes.cardSmall).background(c.danger.copy(alpha = 0.12f))
                        .border(1.dp, c.danger.copy(alpha = 0.4f), EvShapes.cardSmall).padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Rounded.WarningAmber, null, tint = c.danger)
                    Text(error.orEmpty(), style = EvType.bodySmall, color = c.ink)
                }
            }

            EvPillButton(
                "Subir mi look", onClick = {
                    scope.launch {
                        val uri = photo?.let(Uri::parse)
                        if (uri == null) { onSubmit(null, description); return@launch }
                        preparing = true
                        val upload = ImageUpload.fromUri(context, uri)
                        preparing = false
                        if (upload == null) { toast.error("No se pudo leer la imagen. Prueba con otra foto."); return@launch }
                        onSubmit(upload, description)
                    }
                },
                modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.AutoAwesome, loading = submitting || preparing,
            )
        }
    }
}
