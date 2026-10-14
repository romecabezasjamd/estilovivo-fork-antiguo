package com.estilovivo.app.feature.suitcase

import com.estilovivo.app.core.design.asPanel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FlightTakeoff
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.estilovivo.app.app.LocalNavigator
import com.estilovivo.app.app.MainTab
import com.estilovivo.app.core.data.model.Garment
import com.estilovivo.app.core.data.model.Trip
import com.estilovivo.app.core.data.model.TripItem
import com.estilovivo.app.core.design.EvMotion
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvSpace
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.EvButtonStyle
import com.estilovivo.app.core.design.components.EvCircleButton
import com.estilovivo.app.core.design.components.EvEmptyState
import com.estilovivo.app.core.design.components.EvPillButton
import com.estilovivo.app.core.design.components.EvSearchBar
import com.estilovivo.app.core.design.components.EvShimmerBox
import com.estilovivo.app.core.design.components.EvTextField
import com.estilovivo.app.core.design.components.LocalEvToast
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.floatingBob
import com.estilovivo.app.core.design.staggeredEntrance
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun TripDetailScreen(tripId: String, modifier: Modifier = Modifier) {
    val vm: TripDetailViewModel = koinViewModel(parameters = { parametersOf(tripId) })
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val toast = LocalEvToast.current
    val c = EvTheme.colors
    var editing by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(vm) {
        vm.onResume()
        onPauseOrDispose { }
    }
    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is TripDetailEvent.Message -> if (e.error) toast.error(e.text) else toast.success(e.text)
                TripDetailEvent.Deleted -> {
                    toast.success("Viaje eliminado.")
                    nav.back()
                }
            }
        }
    }

    val trip = state.trip
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = EvSpace.screen, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            EvCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, nav::back, contentDescription = "Volver a mis viajes")
            Text("Preparando viaje", style = EvType.subtitle, color = c.inkSoft, modifier = Modifier.weight(1f).padding(start = 4.dp))
            if (trip != null) {
                EvCircleButton(Icons.Rounded.Edit, { editing = true }, contentDescription = "Editar destino y fechas")
                EvCircleButton(Icons.Rounded.DeleteOutline, { confirmDelete = true }, contentDescription = "Eliminar viaje", tint = c.danger)
            }
        }
        when {
            trip != null -> TripBody(
                trip = trip, state = state,
                onToggle = vm::toggleItem, onEssential = vm::toggleEssential, onDeleteItem = vm::deleteItem,
                onAddItem = { vm.addItem(it) },
                onEditGarments = { picking = true },
                onRemoveGarment = vm::removeGarment,
                onDelete = { confirmDelete = true },
            )
            state.loading -> Column(Modifier.padding(horizontal = EvSpace.screen), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                EvShimmerBox(Modifier.fillMaxWidth().height(220.dp), EvShapes.hero)
                repeat(4) { EvShimmerBox(Modifier.fillMaxWidth().height(56.dp), EvShapes.cardSmall) }
            }
            state.error != null -> EvEmptyState(
                Icons.Rounded.CloudOff, "No pudimos cargar el viaje", state.error.orEmpty(),
                actionText = "Reintentar", onAction = vm::load,
            )
            state.notFound -> EvEmptyState(
                Icons.Rounded.FlightTakeoff, "Este viaje ya no existe",
                "Puede que lo hayas eliminado desde otro dispositivo.", actionText = "Volver a mis viajes", onAction = nav::back,
            )
        }
    }

    if (trip != null && editing) {
        EditTripSheet(
            trip = trip, today = state.today, saving = state.savingMeta,
            onSave = vm::saveBasics,
            onDismiss = { editing = false },
        )
    }
    if (trip != null && picking) {
        TripGarmentSheet(
            wardrobe = state.wardrobe, current = trip.garments, saving = state.savingMeta,
            onSave = vm::saveGarments,
            onDismiss = { picking = false },
            onGoWardrobe = { picking = false; nav.goToTab(MainTab.Wardrobe) },
        )
    }
    SuitcaseConfirmDialog(
        open = confirmDelete && trip != null,
        title = "¿Eliminar este viaje?",
        message = "Se borrará el viaje a ${trip?.destination.orEmpty()} con su checklist. No se puede deshacer.",
        confirmLabel = "Eliminar",
        icon = Icons.Rounded.DeleteOutline,
        loading = state.deleting,
        onConfirm = vm::deleteTrip,
        onDismiss = { confirmDelete = false },
    )
}

@Composable
private fun TripBody(
    trip: Trip,
    state: TripDetailUiState,
    onToggle: (String) -> Unit,
    onEssential: (String) -> Unit,
    onDeleteItem: (String) -> Unit,
    onAddItem: (String) -> String?,
    onEditGarments: () -> Unit,
    onRemoveGarment: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val c = EvTheme.colors
    var adding by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = EvSpace.screen, end = EvSpace.screen, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "hero") { TripHero(trip, state.today, state.phase, Modifier.staggeredEntrance(0)) }

        item(key = "check-h") {
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 4.dp).staggeredEntrance(1),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Checklist", style = EvType.title, color = c.ink)
                    Text(
                        when {
                            trip.items.isEmpty() -> "Aún no hay nada en la lista"
                            Checklist.checked(trip.items) == trip.items.size -> "¡Todo listo en la maleta!"
                            else -> "${Checklist.checked(trip.items)} de ${trip.items.size} preparados"
                        },
                        style = EvType.bodySmall, color = c.inkSoft,
                    )
                }
                EvCircleButton(
                    if (adding) Icons.Rounded.Close else Icons.Rounded.Add, { adding = !adding },
                    contentDescription = if (adding) "Cerrar" else "Añadir elemento a la lista",
                    container = if (adding) c.surface else c.ink, tint = if (adding) c.ink else c.onInk,
                )
            }
        }
        item(key = "check-add") {
            AnimatedVisibility(
                visible = adding,
                enter = expandVertically(EvMotion.smooth()) + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) { AddItemField(onAddItem) }
        }
        if (trip.items.isEmpty()) {
            item(key = "check-empty") {
                Text(
                    "Toca + para añadir lo que no te puede faltar: documentación, cargadores, neceser…",
                    style = EvType.body, color = c.inkMuted, modifier = Modifier.padding(vertical = 14.dp),
                )
            }
        }
        items(trip.items, key = { it.id }) { item ->
            ChecklistRow(
                item = item,
                onToggle = { onToggle(item.id) }, onEssential = { onEssential(item.id) }, onDelete = { onDeleteItem(item.id) },
                modifier = Modifier.animateItem(),
            )
        }

        item(key = "garments-h") {
            Row(
                Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Prendas", style = EvType.title, color = c.ink)
                    Text(
                        when (val n = trip.garments.size) { 0 -> "Aún no has elegido ropa"; 1 -> "1 prenda en la maleta"; else -> "$n prendas en la maleta" },
                        style = EvType.bodySmall, color = c.inkSoft,
                    )
                }
                MiniPill(
                    if (trip.garments.isEmpty()) "Elegir" else "Editar", onEditGarments,
                    style = EvButtonStyle.Secondary, icon = Icons.Rounded.Checkroom, enabled = !state.savingMeta,
                )
            }
        }
        if (trip.garments.isEmpty()) {
            item(key = "garments-empty") {
                Column(
                    Modifier.fillMaxWidth().clip(EvShapes.cardSmall).background(c.surface)
                        .evClickable(pressedScale = 0.98f, role = null, onClick = onEditGarments).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.Checkroom, null, tint = c.inkMuted, modifier = Modifier.size(30.dp).floatingBob(5f))
                    Text("No has añadido ropa aún", style = EvType.subtitle, color = c.ink)
                    Text("Elige prendas de tu armario para tenerlas a mano.", style = EvType.bodySmall, color = c.inkSoft, textAlign = TextAlign.Center)
                }
            }
        } else {
            val rows = trip.garments.chunked(3)
            items(rows.size, key = { "grow-${rows[it].first().id}" }) { index ->
                val row = rows[index]
                Row(Modifier.fillMaxWidth().animateContentSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { g ->
                        GarmentPickTile(
                            g, selected = false, onClick = {}, modifier = Modifier.weight(1f),
                            onRemove = if (state.savingMeta) null else ({ onRemoveGarment(g.id) }),
                        )
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        item(key = "delete") {
            EvPillButton(
                "Eliminar viaje", onDelete, Modifier.fillMaxWidth().padding(top = 26.dp),
                style = EvButtonStyle.Secondary, icon = Icons.Rounded.DeleteOutline, height = 52.dp,
            )
        }
        item(key = "nav") { Spacer(Modifier.navigationBarsPadding()) }
    }
}

// ───────────── Hero ─────────────

@Composable
private fun TripHero(trip: Trip, today: LocalDate, phase: TripPhase?, modifier: Modifier = Modifier) {
    val c = EvTheme.colors.asPanel()
    val fraction = rememberAnimatedFraction(Checklist.fraction(trip.items), delayMs = 250)
    val percent = (fraction * 100).roundToInt()
    val length = TripDates.length(trip.dateStart, trip.dateEnd)
    Box(
        modifier.fillMaxWidth().clip(EvShapes.hero).background(c.ink)
            .semantics(mergeDescendants = true) {
                contentDescription = "Viaje a ${trip.destination}, ${TripDates.range(trip.dateStart, trip.dateEnd, today)}. " +
                    "${TripTiming.label(phase).orEmpty()}. Maleta al ${Checklist.percent(trip.items)} por ciento."
            },
    ) {
        Canvas(Modifier.matchParentSize().floatingBob(6f)) {
            drawCircle(c.accent.copy(alpha = 0.28f), radius = size.width * 0.34f, center = Offset(size.width * 0.96f, size.height * 0.04f))
            drawCircle(c.onInk.copy(alpha = 0.07f), radius = size.width * 0.22f, center = Offset(size.width * 0.06f, size.height * 0.98f))
        }
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PhaseChip(phase, onDark = true)
                    Text(
                        trip.destination,
                        style = EvType.headlineAccent.copy(fontSize = 44.sp, lineHeight = 46.sp),
                        color = c.onInk, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.CalendarMonth, null, tint = c.onInk.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                        Text(
                            TripDates.range(trip.dateStart, trip.dateEnd, today) + (length?.let { if (it > 1) " · $it días" else "" } ?: ""),
                            style = EvType.body, color = c.onInk.copy(alpha = 0.8f),
                        )
                    }
                }
                Countdown(phase)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text("Maleta lista", style = EvType.label, color = c.onInk.copy(alpha = 0.75f))
                    Text("$percent%", style = EvType.bigNumber.copy(fontSize = 30.sp), color = c.onInk)
                }
                TripProgressBar(fraction, height = 10.dp, track = c.onInk.copy(alpha = 0.2f), fill = c.onInk)
            }
        }
    }
}

@Composable
private fun Countdown(phase: TripPhase?) {
    val c = EvTheme.colors
    val (big, small) = when (phase) {
        is TripPhase.Upcoming -> phase.days.toString() to (if (phase.days == 1) "día para salir" else "días para salir")
        is TripPhase.Ongoing -> "${phase.day}/${phase.total}" to "día del viaje"
        is TripPhase.Finished -> "✓" to "terminado"
        null -> return
    }
    Column(
        Modifier.padding(start = 12.dp).clip(EvShapes.cardSmall).background(c.onInk.copy(alpha = 0.12f)).padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(big, style = EvType.bigNumber, color = c.onInk)
        Text(small, style = EvType.caption, color = c.onInk.copy(alpha = 0.75f), textAlign = TextAlign.Center)
    }
}

// ───────────── Checklist ─────────────

@Composable
private fun ChecklistRow(
    item: TripItem,
    onToggle: () -> Unit,
    onEssential: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = EvTheme.colors
    val pending = TripStore.isTemp(item.id)
    Row(
        modifier.fillMaxWidth().graphicsLayer { alpha = if (pending) 0.55f else 1f }
            .clip(EvShapes.cardSmall).background(c.surface).padding(start = 6.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedCheck(item.checked, onToggle, label = item.label, enabled = !pending)
        StrikeText(item.label, struck = item.checked, modifier = Modifier.weight(1f).padding(vertical = 12.dp, horizontal = 2.dp))
        EssentialStar(item.isEssential, onEssential, item.label, enabled = !pending)
        Box(
            Modifier.size(44.dp).evClickable(enabled = !pending, pressedScale = 0.85f, onClick = onDelete)
                .semantics { contentDescription = "Quitar ${item.label} de la lista" },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Close, null, tint = c.inkMuted, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
private fun AddItemField(onAdd: (String) -> String?) {
    var text by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val requester = remember { FocusRequester() }
    val focus = LocalFocusManager.current
    LaunchedEffect(Unit) { requester.requestFocus() }
    fun submit() {
        val e = onAdd(text)
        error = e
        // Si se añadió, el campo queda listo para el siguiente elemento.
        if (e == null) text = ""
    }
    EvTextField(
        value = text,
        onValueChange = { text = it.take(Checklist.MAX_LABEL); error = null },
        label = "Nuevo elemento",
        leadingIcon = Icons.Rounded.Add,
        error = error,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (text.isBlank()) focus.clearFocus() else submit() }),
        modifier = Modifier.padding(vertical = 6.dp).focusRequester(requester),
        trailing = { AnimatedVisibility(text.isNotBlank(), enter = fadeIn(), exit = fadeOut()) { MiniPill("Añadir", ::submit) } },
    )
}

// ───────────── Hojas ─────────────

@Composable
private fun EditTripSheet(
    trip: Trip,
    today: LocalDate,
    saving: Boolean,
    onSave: (String, String, String, (Boolean) -> Unit) -> TripFormErrors,
    onDismiss: () -> Unit,
) {
    val c = EvTheme.colors
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var destination by rememberSaveable(trip.id) { mutableStateOf(trip.destination) }
    // Los viajes antiguos traen texto ya formateado ("13 oct"): obligan a elegir fechas nuevas.
    var start by rememberSaveable(trip.id) { mutableStateOf(trip.dateStart.takeIf { TripDates.parse(it) != null }.orEmpty()) }
    var end by rememberSaveable(trip.id) { mutableStateOf(trip.dateEnd.takeIf { TripDates.parse(it) != null }.orEmpty()) }
    var errors by remember { mutableStateOf(TripFormErrors()) }
    var showDates by rememberSaveable { mutableStateOf(false) }
    var waiting by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    fun close() {
        scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss() }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.bg, contentColor = c.ink, shape = EvShapes.sheet,
        dragHandle = { Box(Modifier.padding(top = 12.dp, bottom = 4.dp).size(width = 44.dp, height = 5.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.outline)) },
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = EvSpace.screen).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Editar viaje", style = EvType.title, color = c.ink)
            EvTextField(
                value = destination, onValueChange = { destination = it; errors = errors.copy(destination = null) }, label = "Destino",
                leadingIcon = Icons.Rounded.Place, error = errors.destination,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            )
            DateRangeField(start, end, today, onClick = { focus.clearFocus(); showDates = true }, error = errors.dates)
            EvPillButton(
                "Guardar cambios",
                onClick = {
                    focus.clearFocus()
                    errors = onSave(destination, start, end) { ok -> waiting = false; if (ok) close() }
                    waiting = !errors.any
                },
                modifier = Modifier.fillMaxWidth(),
                loading = saving && waiting,
                enabled = !saving,
            )
        }
    }
    if (showDates) {
        DateRangeSheet(
            initialStart = TripDates.parse(start), initialEnd = TripDates.parse(end), today = today,
            onConfirm = { s, e -> start = TripDates.iso(s); end = TripDates.iso(e); errors = errors.copy(dates = null); showDates = false },
            onDismiss = { showDates = false },
        )
    }
}

@Composable
private fun TripGarmentSheet(
    wardrobe: List<Garment>,
    current: List<Garment>,
    saving: Boolean,
    onSave: (List<String>, (Boolean) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onGoWardrobe: () -> Unit,
) {
    val c = EvTheme.colors
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val initial = remember { current.map { it.id } }
    var selected by rememberSaveable { mutableStateOf(ArrayList(initial)) }
    var search by rememberSaveable { mutableStateOf("") }
    var waiting by remember { mutableStateOf(false) }
    // Prendas del viaje que ya no están en el armario también se listan (para poder quitarlas).
    val pool = remember(wardrobe, current) { wardrobe + current.filter { g -> wardrobe.none { it.id == g.id } } }
    val shown = remember(pool, search) {
        val q = search.trim()
        if (q.isEmpty()) pool else pool.filter { it.name.contains(q, true) || it.type.contains(q, true) || it.color.contains(q, true) }
    }
    val selectedSet = remember(selected) { selected.toSet() }
    val changed = selectedSet != initial.toSet()

    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.bg, contentColor = c.ink, shape = EvShapes.sheet,
        dragHandle = { Box(Modifier.padding(top = 12.dp, bottom = 4.dp).size(width = 44.dp, height = 5.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.outline)) },
    ) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = EvSpace.screen).padding(top = 4.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Prendas del viaje", style = EvType.title, color = c.ink)
                    Text("Toca para añadir o quitar de la maleta", style = EvType.bodySmall, color = c.inkSoft)
                }
                Text("${selected.size}", style = EvType.bigNumber.copy(fontSize = 26.sp), color = c.ink)
            }
            if (pool.size > 8) {
                EvSearchBar(search, { search = it }, Modifier.padding(horizontal = EvSpace.screen).padding(bottom = 12.dp), placeholder = "Buscar prenda")
            }
            if (pool.isEmpty()) {
                EvEmptyState(
                    Icons.Rounded.Checkroom, "Tu armario está vacío", "Añade prendas a tu armario y podrás meterlas en la maleta.",
                    modifier = Modifier.weight(1f), actionText = "Ir al armario", onAction = onGoWardrobe,
                )
            } else {
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = EvSpace.screen, vertical = 4.dp),
                ) {
                    if (shown.isEmpty()) item(key = "none") { Text("Ninguna prenda coincide con «$search».", style = EvType.body, color = c.inkSoft) }
                    garmentRows(shown, selectedSet, { id ->
                        selected = ArrayList(if (id in selectedSet) selected - id else selected + id)
                    })
                }
            }
            EvPillButton(
                text = if (changed) "Guardar prendas" else "Sin cambios",
                onClick = {
                    waiting = true
                    onSave(selected.toList()) { ok ->
                        waiting = false
                        if (ok) scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss() }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = EvSpace.screen, vertical = 12.dp),
                enabled = changed && !saving,
                loading = saving && waiting,
            )
        }
    }
}
