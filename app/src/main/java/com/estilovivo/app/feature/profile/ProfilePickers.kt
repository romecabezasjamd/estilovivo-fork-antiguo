package com.estilovivo.app.feature.profile

import androidx.compose.foundation.layout.height
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.components.EvButtonStyle
import com.estilovivo.app.core.design.components.EvPillButton
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

internal fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
internal fun Long.toPickerDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** Selector de una fecha (fecha de nacimiento): no admite fechas futuras. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EvDatePickerDialog(
    initial: LocalDate?,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    val limit = today.toPickerMillis()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initial ?: today.minusYears(25)).toPickerMillis(),
        yearRange = 1900..today.year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= limit
            override fun isSelectableYear(year: Int) = year <= today.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            EvPillButton("Aceptar", { state.selectedDateMillis?.let { onPick(it.toPickerDate()) } ?: onDismiss() }, height = 44.dp,
                enabled = state.selectedDateMillis != null)
        },
        dismissButton = { EvPillButton("Cancelar", onDismiss, style = EvButtonStyle.Ghost, height = 44.dp) },
        colors = androidx.compose.material3.DatePickerDefaults.colors(containerColor = EvTheme.colors.surface),
    ) { DatePicker(state) }
}

/** Selector de rango (periodo del ciclo). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EvRangePickerDialog(
    initial: Pair<LocalDate, LocalDate>?,
    onPick: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initial?.first?.toPickerMillis(),
        initialSelectedEndDateMillis = initial?.second?.toPickerMillis(),
    )
    val start = state.selectedStartDateMillis
    val end = state.selectedEndDateMillis
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            EvPillButton("Guardar", { if (start != null) onPick(start.toPickerDate(), (end ?: start).toPickerDate()) }, height = 44.dp, enabled = start != null)
        },
        dismissButton = { EvPillButton("Cancelar", onDismiss, style = EvButtonStyle.Ghost, height = 44.dp) },
        colors = androidx.compose.material3.DatePickerDefaults.colors(containerColor = EvTheme.colors.surface),
    ) { DateRangePicker(state, modifier = Modifier.height(480.dp)) }
}
