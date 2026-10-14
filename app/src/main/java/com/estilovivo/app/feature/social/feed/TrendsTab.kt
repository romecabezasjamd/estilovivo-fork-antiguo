package com.estilovivo.app.feature.social.feed

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.estilovivo.app.core.data.model.Trend
import com.estilovivo.app.core.design.EvMotion
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvSpace
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.LocalEvToast
import com.estilovivo.app.core.design.components.EvButtonStyle
import com.estilovivo.app.core.design.components.EvChipRow
import com.estilovivo.app.core.design.components.EvEmptyState
import com.estilovivo.app.core.design.components.EvHeadline
import com.estilovivo.app.core.design.components.EvImage
import com.estilovivo.app.core.design.components.EvPillButton
import com.estilovivo.app.core.design.components.EvShimmerBox
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.staggeredEntrance
import org.koin.androidx.compose.koinViewModel

/** Tendencias de moda: lista editorial con categorías, pull-to-refresh y enlace a la fuente. */
@Composable
fun TrendsTab() {
    val vm: TrendsViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val toast = LocalEvToast.current
    val context = LocalContext.current
    LaunchedEffect(vm) { vm.errors.collect { toast.error(it) } }

    val visible = state.visible
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 6.dp, bottom = 132.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = "header") {
                Column(Modifier.padding(horizontal = EvSpace.screen).staggeredEntrance(0), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    EvHeadline("Tendencias\n*del momento*")
                    Text("Lo que se lleva ahora, seleccionado para ti.", style = EvType.body, color = EvTheme.colors.inkSoft)
                }
            }
            if (state.categories.size > 2) {
                item(key = "chips") {
                    EvChipRow(
                        items = state.categories, selected = state.category, onSelect = vm::selectCategory, label = { it },
                        modifier = Modifier.fillMaxWidth().staggeredEntrance(1),
                    )
                }
            }
            when {
                state.loading && state.trends.isEmpty() -> items(3) { TrendSkeleton(Modifier.padding(horizontal = EvSpace.screen)) }
                state.error != null && state.trends.isEmpty() -> item(key = "error") {
                    EvEmptyState(Icons.Rounded.CloudOff, "No pudimos cargar las tendencias", state.error.orEmpty(), actionText = "Reintentar", onAction = vm::load)
                }
                visible.isEmpty() -> item(key = "empty") {
                    EvEmptyState(Icons.Rounded.Newspaper, "Aún no hay tendencias", "Vuelve más tarde: se actualizan cada pocos días.")
                }
                else -> itemsIndexed(visible, key = { _, t -> t.id }) { i, trend ->
                    TrendCard(
                        trend = trend,
                        onRead = {
                            if (!openWebUrl(context, trend.link)) toast.error("No se pudo abrir el enlace.")
                        },
                        modifier = Modifier.padding(horizontal = EvSpace.screen).staggeredEntrance(i + 2).animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun TrendCard(trend: Trend, onRead: () -> Unit, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    var expanded by rememberSaveable(trend.id) { mutableStateOf(false) }
    val canOpen = isSafeWebUrl(trend.link)
    Column(modifier.fillMaxWidth().clip(EvShapes.hero).background(c.surface).animateContentSize(EvMotion.smooth())) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.15f).background(c.surfaceAlt)) {
            EvImage(trend.image, trend.title, Modifier.fillMaxSize())
            Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (trend.source.isNotBlank()) EvPill(trend.source, container = androidx.compose.ui.graphics.Color(0xCC000000), content = androidx.compose.ui.graphics.Color.White)
                if (trend.category.isNotBlank()) EvPill(trend.category, container = c.surface, content = c.ink)
            }
        }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(trend.title, style = EvType.title, color = c.ink)
            if (trend.description.isNotBlank()) {
                Text(
                    trend.description, style = EvType.body, color = c.inkSoft,
                    maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clip(EvShapes.field).let { m -> if (trend.description.length > 140) m.evClickable(role = null, haptic = false) { expanded = !expanded } else m },
                )
            }
            val tags = trend.tags.filter { it.isNotBlank() }.take(6)
            if (tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.forEach { EvPill("#${it.trim().trimStart('#')}") }
                }
            }
            if (canOpen) {
                EvPillButton("Leer más", onRead, Modifier.padding(top = 4.dp), style = EvButtonStyle.Primary, icon = Icons.AutoMirrored.Rounded.OpenInNew, height = 48.dp)
            }
        }
    }
}

@Composable
private fun TrendSkeleton(modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    Column(modifier.fillMaxWidth().clip(EvShapes.hero).background(c.surface), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EvShimmerBox(Modifier.fillMaxWidth().aspectRatio(1.15f), EvShapes.hero)
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EvShimmerBox(Modifier.fillMaxWidth(0.7f).padding(vertical = 8.dp).aspectRatio(14f))
            EvShimmerBox(Modifier.fillMaxWidth().aspectRatio(10f))
        }
    }
}
