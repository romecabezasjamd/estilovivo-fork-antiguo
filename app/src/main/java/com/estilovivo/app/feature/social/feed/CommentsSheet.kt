package com.estilovivo.app.feature.social.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.estilovivo.app.core.data.model.Comment
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.EvEmptyState
import com.estilovivo.app.core.design.components.EvShimmerBox
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.staggeredEntrance

/** Hoja de comentarios con hilos (respuestas de un nivel), enviar y borrar. */
@Composable
fun CommentsSheet(
    state: CommentsUiState,
    canDelete: (Comment) -> Boolean,
    clearSignal: Int,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onSend: (String) -> Unit,
    onReply: (ReplyTarget?) -> Unit,
    onDelete: (Comment) -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    val c = EvTheme.colors
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by rememberSaveable { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<Comment?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(clearSignal) { if (clearSignal > 0) text = "" }
    LaunchedEffect(state.replyTo?.rootId) { if (state.replyTo != null) runCatching { focus.requestFocus() } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.bg, shape = EvShapes.sheet) {
        Column(Modifier.fillMaxHeight(0.88f).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Comentarios", style = EvType.title, color = c.ink, modifier = Modifier.weight(1f))
                val total = countAllComments(state.comments)
                if (!state.loading && state.error == null) Text("$total", style = EvType.label, color = c.inkMuted)
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        repeat(4) { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            EvShimmerBox(Modifier.size(36.dp), CircleShape)
                            EvShimmerBox(Modifier.fillMaxWidth().heightIn(min = 56.dp), EvShapes.cardSmall)
                        } }
                    }
                    state.error != null -> EvEmptyState(
                        Icons.Rounded.CloudOff, "No se pudieron cargar", state.error, Modifier.align(Alignment.Center),
                        actionText = "Reintentar", onAction = onRetry,
                    )
                    state.comments.isEmpty() -> EvEmptyState(
                        Icons.Rounded.ChatBubbleOutline, "Sin comentarios todavía", "Sé la primera en decir algo bonito.",
                        Modifier.align(Alignment.Center),
                    )
                    else -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(state.comments, key = { it.id }) { root ->
                            Column(Modifier.staggeredEntrance(0).animateContentSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                CommentRow(
                                    root, canDelete(root), state.deletingId == root.id,
                                    onReply = { onReply(ReplyTarget(root.id, root.userName)) },
                                    onDelete = { toDelete = root },
                                    onProfile = { onOpenProfile(root.userId) },
                                )
                                root.replies.forEach { reply ->
                                    CommentRow(
                                        reply, canDelete(reply), state.deletingId == reply.id,
                                        onReply = { onReply(ReplyTarget(root.id, reply.userName)); if (text.isBlank()) text = "@${reply.userName.substringBefore(' ')} " },
                                        onDelete = { toDelete = reply },
                                        onProfile = { onOpenProfile(reply.userId) },
                                        modifier = Modifier.padding(start = 46.dp), compact = true,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Barra de escritura
            Column(Modifier.fillMaxWidth().background(c.surface).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
                AnimatedVisibility(
                    visible = state.replyTo != null,
                    enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically(),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(CircleShape).background(c.surfaceAlt).padding(start = 14.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Respondiendo a ${state.replyTo?.userName.orEmpty()}", style = EvType.caption, color = c.inkSoft, modifier = Modifier.weight(1f))
                        Box(
                            Modifier.size(44.dp).evClickable { onReply(null) }.semantics { contentDescription = "Cancelar respuesta" },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Close, null, tint = c.inkSoft, modifier = Modifier.size(18.dp)) }
                    }
                }
                val valid = commentError(text) == null
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.weight(1f).heightIn(min = 48.dp, max = 140.dp).clip(EvShapes.field).background(c.surfaceAlt)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (text.isEmpty()) Text("Añade un comentario…", style = EvType.body, color = c.inkMuted)
                        BasicTextField(
                            value = text,
                            onValueChange = { if (it.length <= COMMENT_MAX + 20) text = it },
                            textStyle = EvType.body.copy(color = c.ink),
                            cursorBrush = SolidColor(c.ink),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Default),
                            keyboardActions = KeyboardActions(),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                    val enabled = valid && !state.sending && !state.loading
                    Box(
                        Modifier.size(48.dp).clip(CircleShape)
                            .background(if (enabled) c.ink else c.inkMuted.copy(alpha = 0.35f))
                            .evClickable(enabled = enabled) { onSend(text) }
                            .semantics { contentDescription = "Enviar comentario" },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.sending) com.estilovivo.app.core.design.components.EvLoadingDots(color = c.onInk, modifier = Modifier.size(24.dp, 10.dp))
                        else Icon(Icons.Rounded.ArrowUpward, null, tint = c.onInk, modifier = Modifier.size(22.dp))
                    }
                }
                if (text.length > COMMENT_MAX - 60) {
                    Text(
                        "${text.length}/$COMMENT_MAX", style = EvType.caption,
                        color = if (text.length > COMMENT_MAX) c.danger else c.inkMuted,
                        modifier = Modifier.padding(start = 6.dp, top = 4.dp),
                    )
                }
            }
        }
    }

    toDelete?.let { comment ->
        EvConfirmDialog(
            title = "¿Borrar comentario?",
            message = if (comment.replies.isNotEmpty()) "También se borrarán sus ${comment.replies.size} respuestas." else "Esta acción no se puede deshacer.",
            confirmText = "Borrar",
            onConfirm = { toDelete = null; onDelete(comment) },
            onDismiss = { toDelete = null },
        )
    }
}

@Composable
private fun CommentRow(
    comment: Comment,
    canDelete: Boolean,
    deleting: Boolean,
    onReply: () -> Unit,
    onDelete: () -> Unit,
    onProfile: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val c = EvTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        EvAvatar(
            comment.userAvatar, comment.userName, if (compact) 30.dp else 36.dp,
            Modifier.clip(CircleShape).evClickable(onClick = onProfile),
            contentDescription = "Perfil de ${comment.userName}",
        )
        Column(Modifier.weight(1f)) {
            Column(Modifier.clip(EvShapes.cardSmall).background(c.surface).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(comment.userName, style = EvType.label, color = c.ink)
                    Text(relativeTime(comment.createdAt), style = EvType.caption, color = c.inkMuted)
                }
                Text(comment.content, style = EvType.body, color = c.ink)
            }
            Row(Modifier.padding(start = 6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Responder", style = EvType.caption, color = c.inkSoft,
                    modifier = Modifier.heightIn(min = 44.dp).clip(CircleShape).evClickable(onClick = onReply).padding(horizontal = 8.dp).wrapCenter(),
                )
                if (canDelete) {
                    Box(
                        Modifier.size(44.dp).evClickable(enabled = !deleting, onClick = onDelete).semantics { contentDescription = "Borrar comentario" },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (deleting) com.estilovivo.app.core.design.components.EvLoadingDots(color = c.inkMuted, modifier = Modifier.size(24.dp, 8.dp))
                        else Icon(Icons.Rounded.DeleteOutline, null, tint = c.inkMuted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

private fun Modifier.wrapCenter(): Modifier = this.then(Modifier.padding(vertical = 12.dp))
