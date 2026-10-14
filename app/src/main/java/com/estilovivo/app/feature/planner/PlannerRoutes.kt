package com.estilovivo.app.feature.planner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.estilovivo.app.app.CreateLook
import com.estilovivo.app.app.LocalNavigator
import com.estilovivo.app.app.Planner
import com.estilovivo.app.app.SharedScope
import com.estilovivo.app.core.design.components.LocalEvToast
import com.estilovivo.app.core.design.rememberEvHaptics
import org.koin.compose.viewmodel.koinViewModel
import java.time.LocalDate

fun NavGraphBuilder.plannerRoutes() {
    composable<Planner> { entry ->
        val args = entry.toRoute<Planner>()
        SharedScope { PlannerRoute(initialDate = args.date) }
    }
}

@Composable
fun PlannerRoute(initialDate: String?, modifier: Modifier = Modifier) {
    val vm = koinViewModel<PlannerViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val toast = LocalEvToast.current
    val haptics = rememberEvHaptics()

    LaunchedEffect(initialDate) { vm.openInitialDate(initialDate) }
    LaunchedEffect(vm) {
        vm.events.collect { e ->
            if (e.isError) { haptics.reject(); toast.error(e.message) } else { haptics.confirm(); toast.success(e.message) }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResume() }

    // Hojas abiertas: sobreviven a la rotación y a la muerte del proceso (se guardan como texto ISO).
    var assignFor by rememberSaveable { mutableStateOf<String?>(null) }
    var noteFor by rememberSaveable { mutableStateOf<String?>(null) }
    var showCycle by rememberSaveable { mutableStateOf(false) }

    val actions = remember(vm, nav) {
        PlannerActions(
            onBack = nav::back,
            onMode = vm::selectMode,
            onPrevious = vm::previous,
            onNext = vm::next,
            onToday = vm::goToday,
            onTapDay = vm::tapDay,
            onOpenDay = vm::openDay,
            onAssign = { assignFor = PlannerCalendar.iso(it) },
            onClearLook = vm::clearLook,
            onEditNote = { noteFor = PlannerCalendar.iso(it) },
            onRefresh = { vm.refresh() },
            onCycle = { showCycle = true },
        )
    }

    PlannerScreen(state, actions, modifier)

    PlannerCalendar.parse(assignFor)?.let { date ->
        AssignLookSheet(
            date = date,
            looks = state.looks,
            currentLookId = state.entry(date)?.lookId,
            onPick = { look -> assignFor = null; vm.assignLook(date, look.id) },
            onRemove = { assignFor = null; vm.clearLook(date) },
            onCreate = { assignFor = null; nav.navigate(CreateLook()) },
            onDismiss = { assignFor = null },
        )
    }
    PlannerCalendar.parse(noteFor)?.let { date ->
        NoteSheet(
            date = date,
            initial = state.entry(date)?.eventNote.orEmpty(),
            onSave = { text -> noteFor = null; vm.saveNote(date, text) },
            onDismiss = { noteFor = null },
        )
    }
    if (showCycle && state.cycleEnabled) {
        CycleSheet(
            current = state.cyclePeriod,
            today = state.today,
            onSave = vm::saveCycle,
            onClear = { showCycle = false; vm.clearCycle() },
            onDismiss = { showCycle = false },
        )
    }
}
