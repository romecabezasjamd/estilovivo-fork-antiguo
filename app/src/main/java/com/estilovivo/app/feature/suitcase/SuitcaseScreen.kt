package com.estilovivo.app.feature.suitcase

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.estilovivo.app.app.LocalNavigator
import com.estilovivo.app.app.TripDetail
import com.estilovivo.app.core.data.model.Trip
import com.estilovivo.app.core.design.EvMotion
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvSpace
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.EvCircleButton
import com.estilovivo.app.core.design.components.EvEmptyState
import com.estilovivo.app.core.design.components.EvHeadline
import com.estilovivo.app.core.design.components.EvImage
import com.estilovivo.app.core.design.components.EvPillButton
import com.estilovivo.app.core.design.components.EvShimmerBox
import com.estilovivo.app.core.design.components.LocalEvToast
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.staggeredEntrance
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import kotlin.math.roundToInt

/** Lista de viajes + hoja de creación a pantalla completa. */
@Composable
fun SuitcaseScreen(modifier: Modifier = Modifier) {
    val vm: TripsViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val toast = LocalEvToast.current
    val c = EvTheme.colors
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf<String?>(null) }

    LifecycleResumeEffect(vm) {
        vm.onResume()
        onPauseOrDispose { }
    }
    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is SuitcaseEvent.Message -> if (e.error) toast.error(e.text) else toast.success(e.text)
            }
        }
    }

    Box(modifier.fillMaxSize().background(c.bg)) {
        TripsContent(
            state = state,
            onBack = nav::back,
            onNew = { showCreate = true },
            onOpen = { nav.navigate(TripDetail(it)) },
            onAskDelete = { confirmDelete = it },
            onRetry = vm::load,
            onRefresh = vm::refresh,
        )
        AnimatedVisibility(
            visible = showCreate,
            enter = slideInVertically(tween(420, easing = EvMotion.emphasized)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(300, easing = EvMotion.emphasized)) { it } + fadeOut(tween(200)),
        ) {
            CreateTripScreen(
                today = state.today,
                onClose = { showCreate = false },
                onCreated = { id ->
                    showCreate = false
                    nav.navigate(TripDetail(id))
                },
            )
        }
    }

    val pending = confirmDelete?.let { id -> state.trips.firstOrNull { it.id == id } }
    // Si el viaje desaparece mientras se pregunta (p. ej. tras refrescar), se cierra el diálogo.
    LaunchedEffect(pending == null && confirmDelete != null) { if (pending == null) confirmDelete = null }
    SuitcaseConfirmDialog(
        open = pending != null,
        title = "¿Eliminar este viaje?",
        message = "Se borrará el viaje a ${pending?.destination.orEmpty()} con su checklist. No se puede deshacer.",
        confirmLabel = "Eliminar",
        icon = Icons.Rounded.DeleteOutline,
        onConfirm = {
            pending?.let { vm.delete(it.id) }
            confirmDelete = null
        },
        onDismiss = { confirmDelete = null },
    )
    BackHandler(enabled = showCreate) { showCreate = false }
}

@Composable
private fun TripsContent(
    state: TripsUiState,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onOpen: (String) -> Unit,
    onAskDelete: (String) -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
) {
    val c = EvTheme.colors
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = EvSpace.screen, end = EvSpace.screen, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "header") {
                Column(Modifier.padding(top = 12.dp).staggeredEntrance(0), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        EvCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, onBack, contentDescription = "Volver")
                        EvHeadline("Mis *viajes*", Modifier.weight(1f))
                        EvCircleButton(
                            Icons.Rounded.Add, onNew, contentDescription = "Crear viaje nuevo",
                            container = c.ink, tint = c.onInk, size = 48.dp,
                        )
                    }
                    if (state.trips.isNotEmpty()) {
                        val n = state.trips.size
                        Text(
                            if (n == 1) "1 aventura planeada" else "$n aventuras planeadas",
                            style = EvType.body, color = c.inkSoft, modifier = Modifier.padding(start = 58.dp),
                        )
                    }
                }
            }
            when {
                state.loading -> items(3, key = { "sk$it" }) {
                    EvShimmerBox(Modifier.fillMaxWidth().height(170.dp), EvShapes.card)
                }
                state.error != null -> item(key = "error") {
                    EvEmptyState(Icons.Rounded.CloudOff, "No pudimos cargar tus viajes", state.error, actionText = "Reintentar", onAction = onRetry)
                }
                state.trips.isEmpty() -> item(key = "empty") { EmptyTrips(onNew) }
                else -> items(state.trips, key = { it.id }) { trip ->
                    val index = state.trips.indexOf(trip)
                    TripCard(
                        trip = trip,
                        today = state.today,
                        deleting = trip.id in state.deleting,
                        onOpen = { onOpen(trip.id) },
                        onDelete = { onAskDelete(trip.id) },
                        modifier = Modifier.animateItem().staggeredEntrance(index + 1),
                    )
                }
            }
            item(key = "bottom") { Spacer(Modifier.navigationBarsPadding()) }
        }
    }
}

@Composable
private fun EmptyTrips(onNew: () -> Unit) {
    val c = EvTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(top = 36.dp).staggeredEntrance(1),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SuitcaseIllustration()
        EvHeadline("¿A dónde *vamos*?", Modifier.padding(top = 8.dp))
        Text(
            "Crea tu primer viaje, elige las prendas del armario y ve tachando el checklist hasta que la maleta esté llena.",
            style = EvType.body, color = c.inkSoft, modifier = Modifier.padding(horizontal = 24.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        EvPillButton("Crear mi primer viaje", onNew, Modifier.padding(top = 6.dp), icon = Icons.Rounded.Add)
    }
}

@Composable
private fun TripCard(
    trip: Trip,
    today: LocalDate,
    deleting: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = EvTheme.colors
    val swipe = rememberSwipeToDismissBoxState(
        confirmValueChange = { target ->
            // Nunca se descarta solo: pide confirmación y la tarjeta vuelve a su sitio.
            if (target == SwipeToDismissBoxValue.EndToStart) onDelete()
            false
        },
    )
    SwipeToDismissBox(
        state = swipe,
        modifier = modifier.graphicsLayer { alpha = if (deleting) 0.5f else 1f },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = !deleting,
        backgroundContent = {
            val swiping = swipe.dismissDirection == SwipeToDismissBoxValue.EndToStart
            Box(
                Modifier.fillMaxSize().clip(EvShapes.card).background(if (swiping) c.danger else androidx.compose.ui.graphics.Color.Transparent)
                    .padding(end = 28.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { if (swiping) Icon(Icons.Rounded.DeleteOutline, "Eliminar viaje", tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(28.dp)) }
        },
    ) {
        TripCardBody(trip, today, onOpen, onDelete)
    }
}

@Composable
private fun TripCardBody(trip: Trip, today: LocalDate, onOpen: () -> Unit, onDelete: () -> Unit) {
    val c = EvTheme.colors
    val phase = TripTiming.phase(trip.dateStart, trip.dateEnd, today)
    val fraction = rememberAnimatedFraction(Checklist.fraction(trip.items), delayMs = 150)
    val percent = (fraction * 100).roundToInt()
    var menu by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxWidth().clip(EvShapes.card).background(c.surface)
            .evClickable(pressedScale = 0.97f, role = null, onClick = onOpen)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append("Viaje a ${trip.destination}, ${TripDates.range(trip.dateStart, trip.dateEnd, today)}")
                    TripTiming.label(phase)?.let { append(", $it") }
                    append(", maleta al ${Checklist.percent(trip.items)} por ciento")
                }
            },
    ) {
        Box(Modifier.align(Alignment.TopEnd).offset(x = 36.dp, y = (-36).dp).size(120.dp).clip(CircleShape).background(c.surfaceAlt))
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(trip.destination, style = EvType.headline.copy(fontSize = 24.sp), color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.CalendarMonth, null, tint = c.inkMuted, modifier = Modifier.size(15.dp))
                        Text(TripDates.range(trip.dateStart, trip.dateEnd, today), style = EvType.bodySmall, color = c.inkSoft)
                    }
                }
                Box {
                    EvCircleButton(Icons.Rounded.MoreHoriz, { menu = true }, contentDescription = "Más opciones", container = c.surfaceAlt)
                    DropdownMenu(
                        expanded = menu, onDismissRequest = { menu = false },
                        containerColor = c.surface, shape = EvShapes.cardSmall,
                    ) {
                        DropdownMenuItem(
                            text = { Text("Abrir", style = EvType.body, color = c.ink) },
                            leadingIcon = { Icon(Icons.Rounded.OpenInNew, null, tint = c.ink) },
                            onClick = { menu = false; onOpen() },
                        )
                        DropdownMenuItem(
                            text = { Text("Eliminar", style = EvType.body, color = c.danger) },
                            leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = c.danger) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        if (trip.items.isEmpty()) "SIN CHECKLIST" else "MALETA · ${Checklist.checked(trip.items)}/${trip.items.size}",
                        style = EvType.caption, color = c.inkMuted,
                    )
                    Text("$percent%", style = EvType.label, color = c.ink)
                }
                TripProgressBar(fraction)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                GarmentStack(trip, Modifier.weight(1f))
                PhaseChip(phase)
            }
        }
    }
}

/** Miniaturas solapadas de las prendas del viaje + recuento. */
@Composable
private fun GarmentStack(trip: Trip, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val shown = trip.garments.take(3)
        if (shown.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                shown.forEach { g ->
                    EvImage(
                        g.imageUrl, null,
                        Modifier.size(34.dp).clip(CircleShape).background(c.surfaceAlt).border(2.dp, c.surface, CircleShape),
                    )
                }
            }
            Spacer(Modifier.size(10.dp))
        }
        Text(
            when (val n = trip.garments.size) { 0 -> "Sin prendas aún"; 1 -> "1 prenda"; else -> "$n prendas" },
            style = EvType.bodySmall, color = c.inkSoft,
        )
    }
}
