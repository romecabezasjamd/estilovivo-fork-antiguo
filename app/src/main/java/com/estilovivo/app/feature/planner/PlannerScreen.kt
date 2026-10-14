package com.estilovivo.app.feature.planner

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.estilovivo.app.core.design.EvMotion
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvSpace
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.EvEmptyState
import com.estilovivo.app.core.design.components.EvHeadline
import com.estilovivo.app.core.design.components.EvSegmented
import com.estilovivo.app.core.design.components.EvShimmerBox
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.staggeredEntrance
import java.time.LocalDate
import java.time.YearMonth

/** Eventos de la pantalla (agrupados para no arrastrar 14 lambdas por cada composable). */
@Stable
class PlannerActions(
    val onBack: () -> Unit,
    val onMode: (PlannerMode) -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onToday: () -> Unit,
    val onTapDay: (LocalDate) -> Unit,
    val onOpenDay: (LocalDate) -> Unit,
    val onAssign: (LocalDate) -> Unit,
    val onClearLook: (LocalDate) -> Unit,
    val onEditNote: (LocalDate) -> Unit,
    val onRefresh: () -> Unit,
    val onCycle: () -> Unit,
)

@Composable
fun PlannerScreen(state: PlannerUiState, a: PlannerActions, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    val pulse = rememberCyclePulse()
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        // Cabecera editorial
        Row(
            Modifier.fillMaxWidth().padding(horizontal = EvSpace.screen).padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(a.onBack)
            Box(Modifier.weight(1f))
        }
        EvHeadline(
            "Planifica tu\n*semana* con estilo",
            modifier = Modifier.padding(horizontal = EvSpace.screen).padding(top = 12.dp).staggeredEntrance(0),
        )
        EvSegmented(
            options = PlannerMode.entries, selected = state.mode, onSelect = a.onMode, label = { it.label },
            modifier = Modifier.fillMaxWidth().padding(horizontal = EvSpace.screen, vertical = 14.dp).staggeredEntrance(1),
        )
        AnimatedVisibility(
            visible = state.cycleEnabled,
            enter = fadeIn() + expandVertically(EvMotion.smooth()),
            exit = fadeOut() + shrinkVertically(),
        ) {
            CycleBanner(state, a, pulse, Modifier.padding(horizontal = EvSpace.screen).padding(bottom = 12.dp))
        }
        PeriodNavigatorFor(state, a, Modifier.padding(horizontal = EvSpace.screen).padding(bottom = 12.dp))

        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = a.onRefresh, modifier = Modifier.weight(1f).fillMaxWidth()) {
            val key = when (state.mode) {
                PlannerMode.Weekly -> PlannerCalendar.weekStart(state.selected)
                PlannerMode.Monthly -> state.selected.withDayOfMonth(1)
                PlannerMode.Daily -> state.selected
            }
            when {
                !state.loaded && state.loading -> PlannerSkeleton()
                !state.loaded && state.error != null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    EvEmptyState(
                        icon = Icons.Rounded.CloudOff, title = "No hemos podido cargar tu planificador",
                        message = state.error, actionText = "Reintentar", onAction = a.onRefresh,
                    )
                }
                else -> AnimatedContent(
                    targetState = state.mode,
                    modifier = Modifier.fillMaxSize().swipeToNavigate(state.mode, a.onPrevious, a.onNext),
                    transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(140)) },
                    label = "plannerMode",
                ) { mode ->
                    AnimatedContent(
                        targetState = key,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = { directionalSlide(state.direction) },
                        label = "plannerPeriod",
                    ) { period ->
                        when (mode) {
                            PlannerMode.Weekly -> WeekPage(period, state, a, pulse)
                            PlannerMode.Monthly -> MonthPage(YearMonth.from(period), state, a, pulse)
                            PlannerMode.Daily -> DayPage(period, state, a, pulse)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodNavigatorFor(state: PlannerUiState, a: PlannerActions, modifier: Modifier) {
    val today = state.today
    val (label, showToday, prev, next) = when (state.mode) {
        PlannerMode.Weekly -> {
            val start = PlannerCalendar.weekStart(state.selected)
            NavInfo(PlannerCalendar.weekLabel(start), start != PlannerCalendar.weekStart(today), "Semana anterior", "Semana siguiente")
        }
        PlannerMode.Monthly -> NavInfo(
            PlannerCalendar.monthLabel(state.month), state.month != YearMonth.from(today), "Mes anterior", "Mes siguiente",
        )
        PlannerMode.Daily -> NavInfo(
            PlannerCalendar.dayLabelFull(state.selected).replaceFirstChar { it.uppercase() }, state.selected != today,
            "Día anterior", "Día siguiente",
        )
    }
    PeriodNavigator(
        label = label, direction = state.direction, showToday = showToday,
        onPrevious = a.onPrevious, onNext = a.onNext, onToday = a.onToday,
        previousDescription = prev, nextDescription = next, modifier = modifier,
    )
}

private data class NavInfo(val label: String, val showToday: Boolean, val prev: String, val next: String)

@Composable
private fun CycleBanner(state: PlannerUiState, a: PlannerActions, pulse: androidx.compose.runtime.State<Float>, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    val period = state.cyclePeriod
    val start = PlannerCalendar.parse(period?.startDate)
    val end = PlannerCalendar.parse(period?.endDate)
    Row(
        modifier.fillMaxWidth().clip(EvShapes.cardSmall).background(CycleRoseSoft)
            .evClickable(pressedScale = 0.98f, onClick = a.onCycle).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CycleDrop(pulse, size = 20.dp)
        Column(Modifier.weight(1f)) {
            Text("Tu periodo", style = EvType.caption, color = c.inkSoft)
            AnimatedContent(targetState = if (start != null && end != null) PlannerCalendar.rangeLabel(start, end) else "Márcalo para verlo en el calendario", label = "cycleText") { t ->
                Text(t, style = EvType.label, color = c.ink)
            }
        }
        Text(if (period == null) "Añadir" else "Editar", style = EvType.label, color = c.ink)
    }
}

@Composable
private fun PlannerSkeleton() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = EvSpace.screen),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        repeat(4) { i ->
            EvShimmerBox(Modifier.fillMaxWidth().height(104.dp).staggeredEntrance(i), EvShapes.card)
        }
    }
}
