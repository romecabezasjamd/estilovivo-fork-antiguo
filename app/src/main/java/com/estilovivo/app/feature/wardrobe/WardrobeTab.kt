package com.estilovivo.app.feature.wardrobe

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.estilovivo.app.app.CreateLook
import com.estilovivo.app.app.GarmentDetail
import com.estilovivo.app.app.LocalNavigator
import com.estilovivo.app.app.TryOn
import com.estilovivo.app.app.Washing
import com.estilovivo.app.core.design.EvMotion
import com.estilovivo.app.core.design.EvSpace
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.EvHeadline
import com.estilovivo.app.core.design.components.EvSegmented
import com.estilovivo.app.core.design.components.LocalEvToast
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.staggeredEntrance
import org.koin.androidx.compose.koinViewModel

/** Pestaña Armario: Armario · Looks · Ventas. */
@Composable
fun WardrobeTab() {
    val vm: WardrobeViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val toast = LocalEvToast.current
    val host = rememberSheetHost()
    var sheetError by remember { mutableStateOf<String?>(null) }
    var prefill by rememberSaveable(stateSaver = GarmentForm.Saver) { mutableStateOf(GarmentForm()) }
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }
    var lastConfirm by remember { mutableStateOf<String?>(null) }
    if (confirm != null) lastConfirm = confirm

    // Filtros y vista sobreviven a la muerte del proceso
    var savedFilters by rememberSaveable { mutableStateOf<String?>(null) }
    var savedView by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        WardrobeFilters.decode(savedFilters)?.let(vm::setFilters)
        WardrobeView.entries.firstOrNull { it.name == savedView }?.let(vm::setView)
    }
    LaunchedEffect(state.filters, state.view) {
        savedFilters = state.filters.encode()
        savedView = state.view.name
    }

    CollectWardrobeEvents(vm.events, toast) { e ->
        when (e) {
            is WardrobeEvent.AddRolledBack -> {
                // Se deshizo el alta: se reabre el formulario con lo que había escrito
                prefill = e.request.form
                sheetError = e.message
                toast.error(e.message)
                host.open(if (e.request.kind == AddKind.Sale) "sale" else "add")
            }
            is WardrobeEvent.OperationResult -> {
                if (e.key == confirmKey(confirm)) confirm = null
                if (e.success) {
                    val k = host.key
                    if (k != null && (k == "sale" || k.endsWith(":${e.key}"))) host.close()
                } else {
                    sheetError = e.message
                }
            }
            else -> Unit
        }
    }

    fun openAdd() { prefill = GarmentForm(); sheetError = null; host.open("add") }
    fun openSale() { prefill = GarmentForm(condition = "good", forSale = true); sheetError = null; host.open("sale") }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            WardrobeHeader(state, onView = vm::setView)
            AnimatedContent(
                targetState = state.view,
                transitionSpec = {
                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally(tween(320, easing = EvMotion.emphasized)) { dir * it / 6 } + fadeIn(tween(260))) togetherWith
                        (slideOutHorizontally(tween(260, easing = EvMotion.emphasized)) { -dir * it / 6 } + fadeOut(tween(140)))
                },
                label = "wardrobeView",
                modifier = Modifier.weight(1f),
            ) { view ->
                // Mientras dos vistas conviven en la transición, ninguna comparte elementos (la misma prenda puede estar en Armario y Ventas)
                val settled = transition.currentState == transition.targetState
                when (view) {
                    WardrobeView.Closet -> ClosetView(
                        state = state,
                        onFilters = vm::setFilters,
                        onOpenFilters = { host.open("filters") },
                        onRefresh = vm::refresh,
                        onOpen = { nav.navigate(GarmentDetail(it.id)) },
                        onMenu = { sheetError = null; host.open("menu:${it.id}") },
                        onAdd = ::openAdd,
                        onWashing = { nav.navigate(Washing) },
                        sharedEnabled = settled,
                    )
                    WardrobeView.Looks -> LooksView(
                        state = state,
                        onRefresh = vm::refresh,
                        onCreate = { nav.navigate(CreateLook()) },
                        onOpen = { nav.navigate(CreateLook(it.id)) },
                        onMenu = { host.open("lookmenu:${it.id}") },
                        onTogglePublic = { vm.toggleLookPublic(it.id) },
                    )
                    WardrobeView.Sales -> SalesView(
                        state = state,
                        onRefresh = vm::refresh,
                        onNewSale = ::openSale,
                        onOpen = { nav.navigate(GarmentDetail(it.id)) },
                        onMenu = { sheetError = null; host.open("menu:${it.id}") },
                        onCancelSale = { vm.cancelSale(it.id) },
                        sharedEnabled = settled,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = state.view == WardrobeView.Closet && host.key == null,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = EvSpace.screen, bottom = 100.dp),
            enter = scaleIn(EvMotion.bouncy(), initialScale = 0.4f) + fadeIn(),
            exit = scaleOut(tween(140), targetScale = 0.4f) + fadeOut(tween(100)),
        ) {
            val c = EvTheme.colors
            Box(
                Modifier.size(60.dp).clip(CircleShape).background(c.ink)
                    .evClickable(pressedScale = 0.88f, onClick = ::openAdd)
                    .semantics { contentDescription = "Añadir prenda" },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Add, null, tint = c.onInk, modifier = Modifier.size(28.dp)) }
        }
    }

    SheetHostContent(host) { key ->
        val id = key.substringAfter(':', "")
        val garment = state.all.firstOrNull { it.id == id }
        when {
            key == "add" -> AddGarmentSheet(
                initial = prefill, initialError = sheetError,
                onSubmit = { f, up -> vm.add(AddRequest(AddKind.Closet, f, up)); vm.setView(WardrobeView.Closet) },
                onClose = host::close,
            )
            key == "sale" -> SaleSheet(
                preselected = null,
                eligible = state.all.filter { !it.forSale && it.id !in state.pendingIds },
                initial = prefill, initialError = sheetError,
                submitting = state.busy.any { !it.startsWith("look:") }, serverError = sheetError,
                onSubmitNew = { f, up -> vm.add(AddRequest(AddKind.Sale, f, up)); vm.setView(WardrobeView.Sales) },
                onSubmitExisting = { g, f -> sheetError = null; vm.sell(g.id, f) },
                onClose = host::close,
                onClearServerError = { sheetError = null },
            )
            key == "filters" -> FiltersSheet(
                filters = state.filters, colors = state.availableColors, resultCount = state.closet.size,
                onChange = vm::setFilters, onClear = vm::clearAdvancedFilters, onClose = host::close,
            )
            key.startsWith("menu:") && garment != null -> GarmentMenuSheet(
                garment = garment, busy = garment.id in state.busy,
                onWash = { vm.setWashing(garment.id, !garment.isWashing); host.close() },
                onTry = { host.close(); nav.navigate(TryOn(garment.id)) },
                onSell = { sheetError = null; host.open("sell:${garment.id}") },
                onCancelSale = { vm.cancelSale(garment.id); host.close() },
                onEdit = { sheetError = null; host.open("edit:${garment.id}") },
                onDelete = { host.close(); confirm = "g:${garment.id}" },
            )
            key.startsWith("sell:") && garment != null -> SaleSheet(
                preselected = garment, eligible = emptyList(), initial = GarmentForm(),
                initialError = null, submitting = garment.id in state.busy, serverError = sheetError,
                onSubmitNew = { _, _ -> }, onSubmitExisting = { g, f -> sheetError = null; vm.sell(g.id, f) },
                onClose = host::close, onClearServerError = { sheetError = null },
            )
            key.startsWith("edit:") && garment != null -> EditGarmentSheet(
                garment = garment, submitting = garment.id in state.busy, serverError = sheetError,
                onSubmit = { f, up -> sheetError = null; vm.edit(garment.id, f, up) },
                onClose = host::close, onClearServerError = { sheetError = null },
            )
            key.startsWith("lookmenu:") -> {
                val look = state.looks.firstOrNull { it.id == id }
                if (look != null) LookMenuSheet(
                    look = look, busy = "look:$id" in state.busy,
                    onEdit = { host.close(); nav.navigate(CreateLook(look.id)) },
                    onTogglePublic = { vm.toggleLookPublic(look.id); host.close() },
                    onDelete = { host.close(); confirm = "l:$id" },
                ) else LaunchedEffect(Unit) { host.close() }
            }
            else -> LaunchedEffect(key, garment) { host.close() }
        }
    }

    // Confirmaciones de borrado
    val confirmId = lastConfirm?.substringAfter(':')
    val isLook = lastConfirm?.startsWith("l:") == true
    val target = if (isLook) state.looks.firstOrNull { it.id == confirmId }?.name else state.all.firstOrNull { it.id == confirmId }?.name
    val deleting = confirmId != null && (if (isLook) "look:$confirmId" else confirmId) in state.busy
    WardrobeConfirmDialog(
        open = confirm != null,
        title = if (isLook) "Eliminar look" else "Eliminar prenda",
        message = (if (isLook) "¿Seguro que quieres eliminar" else "¿Seguro que quieres eliminar") +
            (target?.let { " “$it”" } ?: "") + "? Esta acción no se puede deshacer.",
        confirmLabel = "Eliminar",
        icon = Icons.Rounded.DeleteOutline,
        loading = deleting,
        onConfirm = { if (confirmId != null) { if (isLook) vm.deleteLook(confirmId) else vm.delete(confirmId) } },
        onDismiss = { confirm = null },
    )
}

/** Clave de operación del VM que corresponde a una confirmación abierta. */
private fun confirmKey(confirm: String?): String? = when {
    confirm == null -> null
    confirm.startsWith("l:") -> "look:${confirm.substringAfter(':')}"
    else -> confirm.substringAfter(':')
}

@Composable
private fun WardrobeHeader(state: WardrobeUiState, onView: (WardrobeView) -> Unit) {
    val c = EvTheme.colors
    Column(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = EvSpace.screen, end = EvSpace.screen, top = 12.dp, bottom = 12.dp),
    ) {
        Column(Modifier.padding(end = 56.dp).staggeredEntrance(0)) {
            EvHeadline("Tu armario, *a mano*.")
            val summary = "${state.all.size} ${if (state.all.size == 1) "prenda" else "prendas"} · " +
                "${state.looks.size} ${if (state.looks.size == 1) "look" else "looks"} · ${state.sales.size} en venta"
            AnimatedContent(summary, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "summary") { s ->
                Text(s, style = EvType.caption, color = c.inkMuted, modifier = Modifier.padding(top = 2.dp))
            }
        }
        EvSegmented(
            options = WardrobeView.entries, selected = state.view, onSelect = onView, label = { it.label },
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp).staggeredEntrance(1),
        )
    }
}
