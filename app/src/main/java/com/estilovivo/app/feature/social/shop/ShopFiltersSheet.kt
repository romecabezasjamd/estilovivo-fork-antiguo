package com.estilovivo.app.feature.social.shop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvSpace
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.EvButtonStyle
import com.estilovivo.app.core.design.components.EvChip
import com.estilovivo.app.core.design.components.EvHeadline
import com.estilovivo.app.core.design.components.EvPillButton
import com.estilovivo.app.core.design.components.EvTextField
import kotlinx.coroutines.launch

private fun parsePrice(raw: String): Double? =
    raw.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0.0 }

/** Filtros de la tienda: precio mínimo/máximo (€), estado y talla. Se aplican al pulsar "Aplicar". */
@Composable
fun ShopFiltersSheet(
    filters: ShopFilters,
    availableSizes: List<String>,
    onApply: (ShopFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = EvTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var min by remember { mutableStateOf(filters.minPrice?.let(::formatPlain).orEmpty()) }
    var max by remember { mutableStateOf(filters.maxPrice?.let(::formatPlain).orEmpty()) }
    var condition by remember { mutableStateOf(filters.condition) }
    var size by remember { mutableStateOf(filters.size) }

    val minValue = parsePrice(min)
    val maxValue = parsePrice(max)
    val minInvalid = min.isNotBlank() && minValue == null
    val maxInvalid = max.isNotBlank() && maxValue == null
    val rangeInvalid = minValue != null && maxValue != null && minValue > maxValue

    fun close(after: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { after() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.bg,
        shape = EvShapes.sheet,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = EvSpace.screen).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            EvHeadline("Afina tu *búsqueda*")

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Precio (€)", style = EvType.label, color = c.ink)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EvTextField(
                        value = min, onValueChange = { min = it.take(7) }, label = "Mínimo",
                        modifier = Modifier.weight(1f),
                        error = if (minInvalid) "Número no válido" else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    EvTextField(
                        value = max, onValueChange = { max = it.take(7) }, label = "Máximo",
                        modifier = Modifier.weight(1f),
                        error = when {
                            maxInvalid -> "Número no válido"
                            rangeInvalid -> "Debe ser mayor que el mínimo"
                            else -> null
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Estado", style = EvType.label, color = c.ink)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ConditionOptions.forEach { (id, label) ->
                        EvChip(text = label, selected = condition == id, onClick = { condition = if (condition == id) null else id })
                    }
                }
            }

            if (availableSizes.isNotEmpty() || size != null) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Talla", style = EvType.label, color = c.ink)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val sizes = if (size != null && availableSizes.none { it.equals(size, true) }) listOf(size!!) + availableSizes else availableSizes
                        sizes.forEach { s ->
                            EvChip(text = s, selected = size.equals(s, true), onClick = { size = if (size.equals(s, true)) null else s })
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                EvPillButton(
                    "Limpiar", onClick = { close { onApply(ShopFilters()); onDismiss() } },
                    style = EvButtonStyle.Secondary, modifier = Modifier.weight(1f),
                )
                EvPillButton(
                    "Aplicar",
                    onClick = { close { onApply(ShopFilters(minValue, maxValue, condition, size)); onDismiss() } },
                    enabled = !minInvalid && !maxInvalid && !rangeInvalid,
                    modifier = Modifier.weight(1.4f),
                )
            }
        }
    }
}

private fun formatPlain(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
