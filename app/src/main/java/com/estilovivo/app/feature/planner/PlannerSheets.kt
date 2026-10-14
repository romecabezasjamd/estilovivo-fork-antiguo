package com.estilovivo.app.feature.planner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDateRangePickerState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.estilovivo.app.core.data.model.Look
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvSpace
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.EvButtonStyle
import com.estilovivo.app.core.design.components.EvHeadline
import com.estilovivo.app.core.design.components.EvPillButton
import com.estilovivo.app.core.design.components.EvSearchBar
import com.estilovivo.app.core.design.components.EvTextField
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.staggeredEntrance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Cierra la hoja con su animación y ejecuta [after] al terminar. */
private fun SheetState.hideThen(scope: CoroutineScope, after: () -> Unit) {
    scope.launch { hide() }.invokeOnCompletion { after() }
}

// ─────────────────────────────────────────────────────────────────────────────
// Asignar look
// ─────────────────────────────────────────────────────────────────────────────

/** Filtra los looks por nombre, etiquetas o nombre de prenda (sin distinguir mayúsculas ni espacios sobrantes). */
internal fun filterLooks(looks: List<Look>, query: String): List<Look> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return looks
    return looks.filter { l ->
        l.name.lowercase().contains(q) || l.tags.any { it.lowercase().contains(q) } || l.garments.any { it.name.lowercase().contains(q) }
    }
}

@Composable
fun AssignLookSheet(
    date: LocalDate,
    looks: List<Look>,
    currentLookId: String?,
    onPick: (Look) -> Unit,
    onRemove: () -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = EvTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    val visible = remember(looks, query) { filterLooks(looks, query) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.bg, shape = EvShapes.sheet) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(horizontal = EvSpace.screen).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                EvHeadline(if (currentLookId == null) "Elige un *look*" else "Cambia el *look*")
                Text("Para el ${PlannerCalendar.dayLabel(date)}", style = EvType.bodySmall, color = c.inkSoft)
            }
            if (looks.size > 4) EvSearchBar(query, { query = it.take(60) }, placeholder = "Buscar looks…")
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                item(key = "create") {
                    SheetRow(
                        icon = Icons.Rounded.Add, title = "Crear look nuevo", subtitle = "Combina tus prendas y vuelve aquí",
                        onClick = { sheetState.hideThen(scope, onCreate) },
                        modifier = Modifier.staggeredEntrance(0),
                    )
                }
                if (currentLookId != null) item(key = "remove") {
                    SheetRow(
                        icon = Icons.Rounded.SearchOff, title = "Dejar el día sin look", subtitle = "Conserva la nota si la hay",
                        onClick = { sheetState.hideThen(scope, onRemove) }, danger = true,
                        modifier = Modifier.staggeredEntrance(1),
                    )
                }
                when {
                    looks.isEmpty() -> item(key = "none") {
                        Text(
                            "Aún no tienes looks guardados. Crea el primero y vuelve a planificar.",
                            style = EvType.body, color = c.inkSoft, modifier = Modifier.padding(vertical = 16.dp, horizontal = 4.dp),
                        )
                    }
                    visible.isEmpty() -> item(key = "nomatch") {
                        Text(
                            "Ningún look coincide con «${query.trim()}».",
                            style = EvType.body, color = c.inkSoft, modifier = Modifier.padding(vertical = 16.dp, horizontal = 4.dp),
                        )
                    }
                    else -> itemsIndexed(visible, key = { _, l -> l.id }) { i, look ->
                        LookRow(look, selected = look.id == currentLookId, index = i + 2) {
                            sheetState.hideThen(scope) { onPick(look) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
) {
    val c = EvTheme.colors
    Row(
        modifier.fillMaxWidth().clip(EvShapes.cardSmall).background(c.surface).evClickable(pressedScale = 0.97f, onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(56.dp).clip(EvShapes.field).background(if (danger) c.danger.copy(alpha = 0.12f) else c.ink), Alignment.Center) {
            Icon(icon, null, tint = if (danger) c.danger else c.onInk)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = EvType.subtitle, color = if (danger) c.danger else c.ink)
            Text(subtitle, style = EvType.caption, color = c.inkMuted)
        }
    }
}

@Composable
private fun LookRow(look: Look, selected: Boolean, index: Int, onClick: () -> Unit) {
    val c = EvTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(EvShapes.cardSmall).background(c.surface)
            .border(if (selected) 2.dp else 0.dp, if (selected) c.ink else Color.Transparent, EvShapes.cardSmall)
            .evClickable(pressedScale = 0.97f, onClick = onClick).padding(12.dp).staggeredEntrance(index),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LookThumb(look, Modifier.size(56.dp), shape = EvShapes.field)
        Column(Modifier.weight(1f)) {
            Text(look.name, style = EvType.subtitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val n = look.garmentCount()
            val sub = when {
                n > 0 -> "$n ${if (n == 1) "prenda" else "prendas"}"
                look.tags.isNotEmpty() -> look.tags.joinToString(", ")
                else -> "Look personalizado"
            }
            Text(sub, style = EvType.caption, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (selected) Box(Modifier.size(28.dp).clip(CircleShape).background(c.ink), Alignment.Center) {
            Icon(Icons.Rounded.Check, "Look actual", tint = c.onInk, modifier = Modifier.size(16.dp))
        } else Icon(Icons.Rounded.Checkroom, null, tint = c.inkMuted, modifier = Modifier.size(20.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Nota del evento
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun NoteSheet(
    date: LocalDate,
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = EvTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var text by rememberSaveable(date) { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    val changed = text.trim() != initial.trim()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.bg, shape = EvShapes.sheet) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = EvSpace.screen)
                .padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            EvHeadline("Nota del *evento*")
            Text(PlannerCalendar.dayLabel(date).replaceFirstChar { it.uppercase() }, style = EvType.bodySmall, color = c.inkSoft)
            EvTextField(
                value = text, onValueChange = { text = it.take(MAX_NOTE_LENGTH) }, label = "Cena, reunión, viaje…",
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                singleLine = false, minLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            )
            Text("${text.length}/$MAX_NOTE_LENGTH", style = EvType.caption, color = if (text.length >= MAX_NOTE_LENGTH) c.danger else c.inkMuted, modifier = Modifier.padding(start = 16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                if (initial.isNotBlank()) {
                    EvPillButton("Quitar nota", { sheetState.hideThen(scope) { onSave("") } }, Modifier.weight(1f), style = EvButtonStyle.Secondary)
                }
                EvPillButton(
                    "Guardar", { sheetState.hideThen(scope) { onSave(text.trim()) } }, Modifier.weight(1f),
                    enabled = changed,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Ciclo: selector de rango de fechas
// ─────────────────────────────────────────────────────────────────────────────

/** El selector de Material trabaja en milisegundos UTC a medianoche: se convierte siempre con UTC para no desplazar el día. */
internal fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
internal fun Long.fromPickerMillis(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
fun CycleSheet(
    current: com.estilovivo.app.core.util.CyclePeriod?,
    today: LocalDate,
    onSave: (LocalDate, LocalDate) -> String?,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = EvTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val start0 = PlannerCalendar.parse(current?.startDate)
    val end0 = PlannerCalendar.parse(current?.endDate)
    val picker = rememberDateRangePickerState(
        initialSelectedStartDateMillis = start0?.toPickerMillis(),
        initialSelectedEndDateMillis = end0?.toPickerMillis(),
        initialDisplayedMonthMillis = (start0 ?: today).toPickerMillis(),
    )
    var error by remember { mutableStateOf<String?>(null) }
    val start = picker.selectedStartDateMillis?.fromPickerMillis()
    val end = picker.selectedEndDateMillis?.fromPickerMillis()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.bg, shape = EvShapes.sheet) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(horizontal = EvSpace.screen).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            EvHeadline("Tu *periodo*")
            Text(
                "Elige el primer y el último día. Se guarda cifrado sólo en este dispositivo y nunca se envía a ningún servidor.",
                style = EvType.bodySmall, color = c.inkSoft,
            )
            DateRangePicker(
                state = picker,
                modifier = Modifier.weight(1f),
                title = null,
                headline = {
                    Text(
                        when {
                            start == null -> "Toca el primer día"
                            end == null -> "Desde el ${PlannerCalendar.rangeLabel(start, start)} · elige el último día (o guarda un solo día)"
                            else -> PlannerCalendar.rangeLabel(start, end)
                        },
                        style = EvType.subtitle, color = c.ink, modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 8.dp),
                    )
                },
                showModeToggle = false,
                colors = DatePickerDefaults.colors(
                    containerColor = Color.Transparent,
                    titleContentColor = c.inkSoft,
                    headlineContentColor = c.ink,
                    weekdayContentColor = c.inkMuted,
                    subheadContentColor = c.ink,
                    navigationContentColor = c.ink,
                    yearContentColor = c.ink,
                    currentYearContentColor = c.ink,
                    selectedYearContentColor = c.onInk,
                    selectedYearContainerColor = c.ink,
                    dayContentColor = c.ink,
                    selectedDayContentColor = c.onInk,
                    selectedDayContainerColor = c.ink,
                    todayContentColor = c.ink,
                    todayDateBorderColor = c.ink,
                    dayInSelectionRangeContentColor = c.ink,
                    dayInSelectionRangeContainerColor = CycleRoseSoft,
                ),
            )
            AnimatedVisibility(error != null, enter = fadeIn(), exit = fadeOut()) {
                Text(error.orEmpty(), style = EvType.caption, color = c.danger, modifier = Modifier.animateContentSize())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                if (current != null) {
                    EvPillButton("Borrar", { sheetState.hideThen(scope, onClear) }, Modifier.weight(1f), style = EvButtonStyle.Secondary)
                }
                EvPillButton(
                    "Guardar", {
                        val s = start
                        if (s != null) {
                            val e = end ?: s
                            val problem = onSave(s, e)
                            if (problem == null) sheetState.hideThen(scope, onDismiss) else error = problem
                        }
                    },
                    Modifier.weight(1f), enabled = start != null,
                )
            }
        }
    }
}
