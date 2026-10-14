package com.estilovivo.app.feature.planner

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.estilovivo.app.core.data.model.Look
import com.estilovivo.app.core.design.EvMotion
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvSpace
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.EvButtonStyle
import com.estilovivo.app.core.design.components.EvImage
import com.estilovivo.app.core.design.components.EvPillButton
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.staggeredEntrance
import java.time.LocalDate
import java.time.YearMonth

/** Descripción accesible de un día: "lunes 13 de octubre, hoy, con look Oficina, nota: Reunión, día de tu periodo". */
internal fun describeDay(
    date: LocalDate,
    today: LocalDate,
    look: Look?,
    hasMissingLook: Boolean,
    note: String?,
    cycleDay: Boolean,
): String = buildString {
    append(PlannerCalendar.dayLabel(date))
    if (date == today) append(", hoy")
    when {
        look != null -> append(", con look ${look.name}")
        hasMissingLook -> append(", con un look que ya no está disponible")
        else -> append(", sin planificar")
    }
    if (!note.isNullOrBlank()) append(", nota: $note")
    if (cycleDay) append(", día de tu periodo")
}

private fun String.capitalized() = replaceFirstChar { it.uppercase() }

// ─────────────────────────────────────────────────────────────────────────────
// Cuerpo de un día (compartido por la tarjeta semanal y la vista previa mensual)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DayBody(date: LocalDate, state: PlannerUiState, a: PlannerActions, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    val entry = state.entry(date)
    val look = state.lookOf(entry)
    val missing = entry?.lookId != null && look == null
    val note = entry?.eventNote?.takeIf { it.isNotBlank() }
    val busy = PlannerCalendar.iso(date) in state.busyDates
    val thumb = 68.dp
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (look != null || missing) {
            if (look != null) LookThumb(look, Modifier.size(thumb)) else Box(Modifier.size(thumb).clip(RoundedCornerShape(18.dp)).background(c.surfaceAlt), Alignment.Center) {
                Icon(Icons.Rounded.Close, null, tint = c.inkMuted)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(look?.name ?: "Look no disponible", style = EvType.subtitle, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val n = look?.garmentCount() ?: 0
                if (look != null && n > 0) Text("$n ${if (n == 1) "prenda" else "prendas"}", style = EvType.caption, color = c.inkMuted)
                if (missing) Text("Quítalo o elige otro", style = EvType.caption, color = c.inkMuted)
                NoteLine(note) { a.onEditNote(date) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CardAction(Icons.Rounded.SwapHoriz, "Cambiar look del ${PlannerCalendar.dayLabel(date)}", { a.onAssign(date) })
                CardAction(Icons.Rounded.Close, "Quitar look del ${PlannerCalendar.dayLabel(date)}", { a.onClearLook(date) }, tint = c.danger)
            }
        } else {
            DashedAddBox(thumb)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Sin planificar", style = EvType.subtitle, color = c.inkMuted)
                NoteLine(note) { a.onEditNote(date) }
                EvPillButton(
                    "Asignar", { a.onAssign(date) }, style = EvButtonStyle.Secondary, height = 40.dp,
                    modifier = Modifier.semantics { contentDescription = "Asignar look al ${PlannerCalendar.dayLabel(date)}" },
                )
            }
        }
    }
    if (busy) Box(Modifier.fillMaxWidth().height(2.dp).clip(CircleShape).background(c.ink.copy(alpha = 0.15f)))
}

@Composable
private fun NoteLine(note: String?, onClick: () -> Unit) {
    val c = EvTheme.colors
    if (note != null) {
        NoteChip(note, onClick = onClick)
    } else {
        Row(
            Modifier.heightIn(min = 44.dp).evClickable(pressedScale = 0.96f, onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Rounded.EditNote, null, tint = c.inkMuted, modifier = Modifier.size(16.dp))
            Text("Añadir nota", style = EvType.caption, color = c.inkMuted)
        }
    }
}



// ─────────────────────────────────────────────────────────────────────────────
// SEMANAL
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun WeekPage(weekStart: LocalDate, state: PlannerUiState, a: PlannerActions, pulse: State<Float>, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = EvSpace.screen).padding(top = 6.dp),
    ) {
        val days = PlannerCalendar.weekDays(weekStart)
        days.forEachIndexed { i, date ->
            val isToday = date == state.today
            val isPast = date.isBefore(state.today)
            val cycle = state.isCycleDay(date)
            val entry = state.entry(date)
            val look = state.lookOf(entry)
            val desc = describeDay(date, state.today, look, entry?.lookId != null && look == null, entry?.eventNote, cycle)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).staggeredEntrance(i)) {
                // Columna de fecha + línea de tiempo
                Column(
                    Modifier.width(52.dp).fillMaxHeight().padding(top = 6.dp).semantics(mergeDescendants = true) { contentDescription = desc },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        PlannerCalendar.SHORT_DAYS[i].uppercase(), style = EvType.caption,
                        color = if (isToday) c.ink else c.inkMuted,
                    )
                    Spacer(Modifier.height(4.dp))
                    val circleBg by animateColorAsState(
                        when { isToday -> c.ink; cycle -> CycleRoseSoft; else -> androidx.compose.ui.graphics.Color.Transparent },
                        tween(250), label = "dayBg",
                    )
                    Box(Modifier.size(38.dp).clip(CircleShape).background(circleBg), contentAlignment = Alignment.Center) {
                        Text(
                            "${date.dayOfMonth}", style = EvType.title,
                            color = when { isToday -> c.onInk; isPast -> c.inkMuted; else -> c.ink },
                        )
                        if (cycle && !isToday) CycleDrop(pulse, Modifier.align(Alignment.BottomEnd), size = 11.dp)
                    }
                    if (i != 6) Box(Modifier.padding(vertical = 6.dp).width(1.5.dp).weight(1f).background(c.outline))
                }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f).padding(bottom = 14.dp)) {
                    val shape = EvShapes.card
                    Column(
                        Modifier.fillMaxWidth().clip(shape).background(c.surface)
                            .border(1.dp, if (isToday) c.ink.copy(alpha = 0.35f) else c.outline.copy(alpha = 0f), shape)
                            .evClickable(pressedScale = 0.98f, role = null) { a.onOpenDay(date) }
                            .padding(12.dp),
                    ) { DayBody(date, state, a) }
                }
            }
        }
        Spacer(Modifier.height(24.dp).navigationBarsPadding())
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MENSUAL
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MonthPage(month: YearMonth, state: PlannerUiState, a: PlannerActions, pulse: State<Float>, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = EvSpace.screen).padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().clip(EvShapes.card).background(c.surface).padding(horizontal = 8.dp, vertical = 12.dp),
        ) {
            Row(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
                PlannerCalendar.SHORT_DAYS.forEach { d ->
                    Text(
                        d.uppercase(), style = EvType.caption, color = c.inkMuted,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(bottom = 8.dp),
                    )
                }
            }
            PlannerCalendar.monthGrid(month).chunked(7).forEachIndexed { row, week ->
                Row(Modifier.fillMaxWidth().staggeredEntrance(row, maxAnimated = 6, offsetPx = 18f)) {
                    week.forEach { date ->
                        Box(Modifier.weight(1f).aspectRatio(0.84f).padding(2.dp)) {
                            if (date != null) MonthCell(date, state, a, pulse)
                        }
                    }
                }
            }
        }
        // Vista previa del día seleccionado
        AnimatedContent(
            targetState = state.selected,
            transitionSpec = {
                (slideInVertically(EvMotion.smooth()) { it / 5 } + fadeIn(tween(220))) togetherWith
                    (slideOutVertically(tween(160)) { -it / 8 } + fadeOut(tween(120)))
            },
            label = "monthPreview",
        ) { date ->
            DayPreview(date, state, a)
        }
        Spacer(Modifier.height(24.dp).navigationBarsPadding())
    }
}

@Composable
private fun MonthCell(date: LocalDate, state: PlannerUiState, a: PlannerActions, pulse: State<Float>) {
    val c = EvTheme.colors
    val isToday = date == state.today
    val selected = date == state.selected
    val cycle = state.isCycleDay(date)
    val entry = state.entry(date)
    val look = state.lookOf(entry)
    val hasLook = entry?.lookId != null
    val hasNote = !entry?.eventNote.isNullOrBlank()
    val ring by animateFloatAsState(if (selected) 1f else 0f, EvMotion.snappy(), label = "cellRing")
    val pop by animateFloatAsState(if (selected) 1.04f else 1f, EvMotion.bouncy(), label = "cellPop")
    val bg by animateColorAsState(if (cycle) CycleRoseSoft else androidx.compose.ui.graphics.Color.Transparent, tween(250), label = "cellBg")
    val shape = RoundedCornerShape(16.dp)
    val desc = describeDay(date, state.today, look, hasLook && look == null, entry?.eventNote, cycle)
    Box(
        Modifier.fillMaxSize().scale(pop).clip(shape).background(bg)
            .border(2.dp, c.ink.copy(alpha = ring), shape)
            .evClickable(pressedScale = 0.9f, role = androidx.compose.ui.semantics.Role.Button) { a.onTapDay(date) }
            .semantics(mergeDescendants = true) { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(if (isToday) c.ink else androidx.compose.ui.graphics.Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${date.dayOfMonth}", style = EvType.label,
                    color = if (isToday) c.onInk else c.ink,
                )
            }
            Row(Modifier.height(10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                if (hasLook) Box(Modifier.size(6.dp).clip(CircleShape).background(c.ink))
                if (hasNote) Icon(Icons.Rounded.EditNote, null, tint = c.accent, modifier = Modifier.size(11.dp))
            }
        }
        if (cycle) CycleDrop(pulse, Modifier.align(Alignment.TopEnd).padding(top = 3.dp, end = 4.dp), size = 10.dp)
    }
}

@Composable
private fun DayPreview(date: LocalDate, state: PlannerUiState, a: PlannerActions) {
    val c = EvTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(EvShapes.card).background(c.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                PlannerCalendar.dayLabel(date).capitalized(), style = EvType.title, color = c.ink,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Box(
                Modifier.clip(CircleShape).background(c.surfaceAlt).evClickable(pressedScale = 0.94f) { a.onOpenDay(date) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) { Text("Ver día completo", style = EvType.caption, color = c.ink) }
        }
        DayBody(date, state, a)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DIARIO
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DayPage(date: LocalDate, state: PlannerUiState, a: PlannerActions, pulse: State<Float>, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    val entry = state.entry(date)
    val look = state.lookOf(entry)
    val missing = entry?.lookId != null && look == null
    val note = entry?.eventNote?.takeIf { it.isNotBlank() }
    val cycleMsg = if (state.cycleEnabled) state.cyclePeriod.messageFor(date) else null
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = EvSpace.screen).padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (cycleMsg != null) {
            Row(
                Modifier.fillMaxWidth().clip(EvShapes.card).background(CycleRoseSoft).padding(16.dp).staggeredEntrance(0)
                    .semantics(mergeDescendants = true) { contentDescription = "Día de tu periodo. $cycleMsg" },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CycleDrop(pulse, size = 24.dp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Día de tu periodo", style = EvType.caption, color = c.inkSoft)
                    Text(cycleMsg, style = EvType.subtitle, color = c.ink)
                }
            }
        }

        // Look del día
        Column(
            Modifier.fillMaxWidth().clip(EvShapes.hero).background(c.surface)
                .border(1.dp, if (date == state.today) c.ink.copy(alpha = 0.3f) else androidx.compose.ui.graphics.Color.Transparent, EvShapes.hero)
                .padding(14.dp).staggeredEntrance(1),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (look != null) {
                LookThumb(look, Modifier.fillMaxWidth().aspectRatio(1.05f), shape = EvShapes.card)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
                    Text(look.name, style = EvType.title, color = c.ink)
                    val n = look.garmentCount()
                    if (n > 0) Text("$n ${if (n == 1) "prenda" else "prendas"}", style = EvType.bodySmall, color = c.inkMuted)
                }
                val pieces = look.garments
                if (pieces.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pieces.forEach { g ->
                            Column(Modifier.width(84.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                EvImage(g.imageUrl, g.name, Modifier.size(84.dp).clip(EvShapes.cardSmall).background(c.surfaceAlt))
                                Text(g.name, style = EvType.caption, color = c.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    EvPillButton("Cambiar look", { a.onAssign(date) }, Modifier.weight(1f), style = EvButtonStyle.Primary, height = 48.dp)
                    EvPillButton("Quitar", { a.onClearLook(date) }, Modifier.weight(1f), style = EvButtonStyle.Secondary, height = 48.dp)
                }
            } else {
                Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DashedAddBox(96.dp)
                    Text(if (missing) "Look no disponible" else "Sin planificar", style = EvType.subtitle, color = c.inkMuted)
                    if (missing) Text("El look de este día ya no existe. Elige otro o quítalo.", style = EvType.bodySmall, color = c.inkMuted, textAlign = TextAlign.Center)
                    EvPillButton("Asignar look", { a.onAssign(date) }, height = 48.dp)
                    if (missing) EvPillButton("Quitar", { a.onClearLook(date) }, style = EvButtonStyle.Ghost, height = 44.dp)
                }
            }
        }

        // Nota del evento
        Column(
            Modifier.fillMaxWidth().clip(EvShapes.card).background(c.surface)
                .evClickable(pressedScale = 0.98f, role = null) { a.onEditNote(date) }
                .padding(16.dp).staggeredEntrance(2),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.EditNote, null, tint = c.inkSoft, modifier = Modifier.size(20.dp))
                Text("Nota del evento", style = EvType.label, color = c.ink, modifier = Modifier.weight(1f))
                Text(if (note == null) "Añadir" else "Editar", style = EvType.caption, color = c.inkSoft)
            }
            Text(
                note ?: "Apunta aquí el plan del día: cena, reunión, viaje…",
                style = EvType.body, color = if (note == null) c.inkMuted else c.ink,
            )
        }
        Spacer(Modifier.height(24.dp).navigationBarsPadding())
    }
}
