package com.estilovivo.app.feature.suitcase

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.estilovivo.app.app.LocalNavigator
import com.estilovivo.app.app.MainTab
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvSpace
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.EvButtonStyle
import com.estilovivo.app.core.design.components.EvCircleButton
import com.estilovivo.app.core.design.components.EvHeadline
import com.estilovivo.app.core.design.components.EvPillButton
import com.estilovivo.app.core.design.components.EvTextField
import com.estilovivo.app.core.design.components.LocalEvToast
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.staggeredEntrance
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate

/**
 * Pantalla completa de "Nuevo viaje": destino, rango de fechas, prendas del armario y checklist inicial.
 * El borrador sobrevive a la rotación y a la muerte del proceso (se borra al crear).
 */
@Composable
fun CreateTripScreen(
    today: LocalDate,
    onClose: () -> Unit,
    onCreated: (tripId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: CreateTripViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val toast = LocalEvToast.current
    val nav = LocalNavigator.current
    val c = EvTheme.colors
    val focus = LocalFocusManager.current
    val created = rememberUpdatedState(onCreated)
    var showDates by rememberSaveable { mutableStateOf(false) }
    var newItem by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is CreateTripEvent.Created -> {
                    toast.success("¡Maleta creada! A preparar el viaje a ${e.destination}.")
                    created.value(e.tripId)
                }
                is CreateTripEvent.Failed -> toast.error(e.message)
            }
        }
    }
    BackHandler { if (!state.submitting) onClose() }

    val form = state.form
    val wardrobe = remember(state.wardrobe, search) {
        val q = search.trim()
        if (q.isEmpty()) state.wardrobe else state.wardrobe.filter { it.name.contains(q, true) || it.type.contains(q, true) || it.color.contains(q, true) }
    }
    val selected = remember(form.garmentIds) { form.garmentIds.toSet() }

    Box(modifier.fillMaxSize().background(c.bg)) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = EvSpace.screen, end = EvSpace.screen, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item(key = "top") {
                Row(
                    Modifier.padding(top = 12.dp, bottom = 18.dp).staggeredEntrance(0),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    EvCircleButton(Icons.Rounded.Close, { if (!state.submitting) onClose() }, contentDescription = "Cerrar sin crear")
                    EvHeadline("Nuevo *viaje*", Modifier.weight(1f))
                    AnimatedVisibility(form.isDirty && !state.submitting, enter = fadeIn(), exit = fadeOut()) {
                        Text(
                            "Borrar todo", style = EvType.label, color = c.inkSoft,
                            modifier = Modifier.evClickable(onClick = vm::discard).padding(horizontal = 6.dp, vertical = 12.dp),
                        )
                    }
                }
            }
            item(key = "dest") {
                EvTextField(
                    value = form.destination,
                    onValueChange = vm::setDestination,
                    label = "Destino",
                    leadingIcon = Icons.Rounded.Place,
                    error = state.errors.destination,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    modifier = Modifier.padding(bottom = 14.dp).staggeredEntrance(1),
                )
            }
            item(key = "dates") {
                DateRangeField(
                    start = form.dateStart, end = form.dateEnd, today = today,
                    error = state.errors.dates,
                    onClick = { focus.clearFocus(); showDates = true },
                    modifier = Modifier.padding(bottom = 26.dp).staggeredEntrance(2),
                )
            }

            // ─── Prendas ───
            item(key = "garments-h") {
                SectionHeader(
                    title = "Prendas para el viaje",
                    trailing = if (form.garmentIds.isEmpty()) null else "${form.garmentIds.size} elegidas",
                )
            }
            if (state.wardrobe.isEmpty()) {
                item(key = "garments-empty") {
                    Column(
                        Modifier.fillMaxWidth().padding(bottom = 26.dp).clip(EvShapes.cardSmall).background(c.surface).padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Rounded.Checkroom, null, tint = c.inkSoft)
                            Text("Todavía no tienes prendas", style = EvType.subtitle, color = c.ink)
                        }
                        Text(
                            "Añade ropa a tu armario y podrás elegirla aquí para la maleta. Puedes crear el viaje ahora y añadirla después.",
                            style = EvType.bodySmall, color = c.inkSoft,
                        )
                        MiniPill("Ir al armario", { nav.goToTab(MainTab.Wardrobe) }, style = EvButtonStyle.Secondary)
                    }
                }
            } else {
                if (state.wardrobe.size > 8) {
                    item(key = "garments-search") {
                        com.estilovivo.app.core.design.components.EvSearchBar(
                            value = search, onValueChange = { search = it }, placeholder = "Buscar prenda",
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                }
                if (wardrobe.isEmpty()) {
                    item(key = "garments-none") {
                        Text("Ninguna prenda coincide con «$search».", style = EvType.body, color = c.inkSoft, modifier = Modifier.padding(bottom = 22.dp))
                    }
                } else {
                    garmentRows(wardrobe, selected, vm::toggleGarment)
                }
                item(key = "garments-gap") { Box(Modifier.size(16.dp)) }
            }

            // ─── Checklist inicial ───
            item(key = "list-h") {
                SectionHeader(
                    title = "Checklist inicial",
                    trailing = "${form.items.size} elementos",
                    action = if (form.items != TripFormRules.suggested) "Restaurar" else null,
                    onAction = vm::resetItems,
                )
            }
            itemsIndexed(form.items, key = { _, it -> "item-${it.label}" }) { index, item ->
                FormItemRow(
                    item = item,
                    onToggleEssential = { vm.toggleItemEssential(index) },
                    onRemove = { vm.removeItem(index) },
                    modifier = Modifier.animateItem().padding(bottom = 8.dp),
                )
            }
            item(key = "list-add") {
                val requester = remember { FocusRequester() }
                Column(Modifier.padding(top = 6.dp)) {
                    EvTextField(
                        value = newItem,
                        onValueChange = { newItem = it.take(Checklist.MAX_LABEL); vm.clearItemError() },
                        label = "Añadir a la lista",
                        leadingIcon = Icons.Rounded.Add,
                        error = state.itemError ?: state.errors.items,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (vm.addItem(newItem)) newItem = "" }),
                        modifier = Modifier.focusRequester(requester),
                        trailing = {
                            AnimatedVisibility(newItem.isNotBlank(), enter = fadeIn(), exit = fadeOut()) {
                                MiniPill("Añadir", { if (vm.addItem(newItem)) newItem = "" })
                            }
                        },
                    )
                }
            }
        }

        // Barra inferior con el botón de crear
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(c.bg)
                .navigationBarsPadding().imePadding().padding(horizontal = EvSpace.screen, vertical = 12.dp),
        ) {
            EvPillButton(
                text = if (state.submitting) "Creando…" else "Crear maleta",
                onClick = { focus.clearFocus(); vm.submit() },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Luggage,
                loading = state.submitting,
            )
        }
    }

    if (showDates) {
        DateRangeSheet(
            initialStart = TripDates.parse(form.dateStart),
            initialEnd = TripDates.parse(form.dateEnd),
            today = today,
            onConfirm = { s, e -> vm.setDates(s, e); showDates = false },
            onDismiss = { showDates = false },
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = EvTheme.colors
    Row(
        modifier.fillMaxWidth().padding(bottom = 12.dp).animateContentSize(),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = EvType.title, color = c.ink, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = EvType.caption, color = c.inkMuted)
        if (action != null && onAction != null) {
            Text(
                action, style = EvType.label, color = c.ink,
                modifier = Modifier.evClickable(onClick = onAction).padding(horizontal = 6.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun FormItemRow(item: FormItem, onToggleEssential: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    Row(
        modifier.fillMaxWidth().clip(EvShapes.cardSmall).background(c.surface).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(item.label, style = EvType.subtitle, color = c.ink, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
        EssentialStar(item.isEssential, onToggleEssential, item.label)
        Box(
            Modifier.size(44.dp).evClickable(pressedScale = 0.85f, onClick = onRemove)
                .semantics { contentDescription = "Quitar ${item.label}" },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Close, null, tint = c.inkMuted, modifier = Modifier.size(20.dp)) }
    }
}

/** Estrella de "esencial" (llena = esencial). */
@Composable
fun EssentialStar(essential: Boolean, onToggle: () -> Unit, label: String, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = EvTheme.colors
    Box(
        modifier.size(44.dp).evClickable(enabled = enabled, pressedScale = 0.8f, onClick = onToggle)
            .semantics { contentDescription = if (essential) "Quitar «$label» de esenciales" else "Marcar «$label» como esencial" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (essential) Icons.Rounded.Star else Icons.Rounded.StarBorder, null,
            tint = if (essential) c.warning else c.inkMuted, modifier = Modifier.size(22.dp),
        )
    }
}
