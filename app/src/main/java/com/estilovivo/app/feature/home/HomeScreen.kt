package com.estilovivo.app.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.estilovivo.app.core.design.EvMotion
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvSpace
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.EvChip
import com.estilovivo.app.core.design.components.EvHeadline
import com.estilovivo.app.core.design.components.EvImage
import com.estilovivo.app.core.design.components.EvSectionHeader
import com.estilovivo.app.core.design.components.EvShimmerBox
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.staggeredEntrance

@androidx.compose.runtime.Immutable
class HomeActions(
    val onRefresh: () -> Unit,
    val onMood: (String) -> Unit,
    val onRetryLevel: () -> Unit,
    val onRetryWeek: () -> Unit,
    val onRetryWeather: () -> Unit,
    val onRetryGarments: () -> Unit,
    val onAskLocation: () -> Unit,
    val onNextProposal: () -> Unit,
    val onSaveLook: () -> Unit,
    val onPlanToday: () -> Unit,
    val onOpenPlanner: (String?) -> Unit,
    val onCreateLook: () -> Unit,
    val onTryOn: () -> Unit,
    val onOpenWardrobe: () -> Unit,
    val onOpenWishlist: () -> Unit,
    val onOpenSuitcase: () -> Unit,
    val onOpenCommunity: () -> Unit,
)

/** Pantalla Inicio (sin estado propio: lo gobierna [HomeViewModel] a través de [HomeTab]). */
@Composable
fun HomeScreen(
    state: HomeUiState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
    bottomPadding: androidx.compose.ui.unit.Dp = 132.dp,
) {
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = actions.onRefresh, modifier = modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 12.dp, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "header") { HomeHeader(state, Modifier.staggeredEntrance(0)) }
            item(key = "level") {
                LevelCard(state.level, actions.onRetryLevel, Modifier.padding(horizontal = EvSpace.screen).staggeredEntrance(1))
            }
            if (state.showCycle && state.cycleMessage != null) {
                item(key = "cycle") { CycleBanner(state.cycleMessage, Modifier.padding(horizontal = EvSpace.screen)) }
            }
            item(key = "mood") {
                MoodSelector(state.mood, state.gender, state.showCycle && state.cycleToday, actions.onMood, Modifier.staggeredEntrance(2))
            }
            item(key = "weather") {
                WeatherCard(
                    state.weather, actions.onRetryWeather, actions.onAskLocation, actions.onOpenWardrobe,
                    Modifier.padding(horizontal = EvSpace.screen).staggeredEntrance(3),
                )
            }
            if (state.todayLook != null) {
                item(key = "today") {
                    TodayLookCard(
                        state.todayLook, state.todayNote, { actions.onOpenPlanner(state.todayIso) },
                        Modifier.padding(horizontal = EvSpace.screen),
                    )
                }
            }
            item(key = "reco") {
                RecommendationCard(
                    reco = state.reco, mood = state.mood, hasPlannedToday = state.todayLook != null,
                    actions = actions, modifier = Modifier.padding(horizontal = EvSpace.screen).staggeredEntrance(4),
                )
            }
            item(key = "week") {
                WeekSection(state.week, actions.onOpenPlanner, actions.onRetryWeek, Modifier.staggeredEntrance(5))
            }
            item(key = "quick") {
                QuickAccess(state.counts, state.countsReady, actions, Modifier.padding(horizontal = EvSpace.screen).staggeredEntrance(6))
            }
        }
    }
}

@Composable
private fun HomeHeader(state: HomeUiState, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    Column(
        // ~56 dp a la derecha reservados para la campana global del shell
        modifier.fillMaxWidth().padding(start = EvSpace.screen, end = 76.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(state.greeting.uppercase(), style = EvType.caption, color = c.inkMuted)
        val title = if (state.name.isBlank()) "Hola, *de nuevo*" else "Hola, *${state.name}*"
        EvHeadline(title, modifier = Modifier.semantics { contentDescription = title.replace("*", "") })
        Text("¿Cómo te sientes hoy?", style = EvType.body, color = c.inkSoft)
    }
}

// ───────────────────────── Estado de ánimo ─────────────────────────

@Composable
private fun MoodSelector(
    selected: String?,
    gender: String?,
    cycleHighlight: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = EvSpace.screen, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(Moods.all, key = { it.id }) { mood ->
            MoodChip(
                mood = mood, label = mood.label(gender), selected = mood.id == selected,
                suggested = cycleHighlight && mood.id == "confident" && mood.id != selected,
                onClick = { onSelect(mood.id) },
            )
        }
    }
}

@Composable
private fun MoodChip(mood: Mood, label: String, selected: Boolean, suggested: Boolean, onClick: () -> Unit) {
    val c = EvTheme.colors
    val bg by animateColorAsState(if (selected) c.ink else c.surface, tween(240), label = "moodBg")
    val fg by animateColorAsState(if (selected) c.onInk else c.inkSoft, tween(240), label = "moodFg")
    val border by animateColorAsState(
        when { selected -> c.ink; suggested -> c.accent; else -> c.outline }, tween(240), label = "moodBorder",
    )
    val scale by animateFloatAsState(if (selected) 1.06f else 1f, EvMotion.bouncy(), label = "moodScale")
    val emojiScale by animateFloatAsState(if (selected) 1.25f else 1f, EvMotion.bouncy(), label = "moodEmoji")
    Row(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(bg)
            .border(if (suggested) 2.dp else 1.dp, border, CircleShape)
            .evClickable(role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp)
            .semantics { this.selected = selected; contentDescription = "$label${if (suggested) ", sugerido hoy" else ""}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(mood.emoji, style = EvType.title, modifier = Modifier.graphicsLayer { scaleX = emojiScale; scaleY = emojiScale }.clearAndSetSemantics { })
        Text(label, style = EvType.label, color = fg, modifier = Modifier.clearAndSetSemantics { })
    }
}

// ───────────────────────── Ciclo ─────────────────────────

@Composable
private fun CycleBanner(message: String, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    val moonTransition = rememberInfiniteTransition(label = "moon")
    val pulse by moonTransition.animateFloat(
        0.55f, 1f,
        infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "moonAlpha",
    )
    Row(
        modifier.fillMaxWidth().clip(EvShapes.cardSmall).background(c.accent.copy(alpha = if (c.isDark) 0.18f else 0.10f))
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Mensaje de tu ciclo: $message" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("🌙", style = EvType.title, modifier = Modifier.alpha(pulse))
        Text(message, style = EvType.subtitle, color = c.ink, modifier = Modifier.weight(1f))
    }
}

// ───────────────────────── Tu semana ─────────────────────────

@Composable
private fun WeekSection(
    section: Section<List<WeekDayUi>>,
    onOpenPlanner: (String?) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EvSectionHeader("Tu semana", action = "Ver todo", onAction = { onOpenPlanner(null) })
        AnimatedContent(
            targetState = section, contentKey = { it::class }, label = "week",
            transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) },
        ) { target ->
            when (target) {
                Section.Loading -> Row(
                    Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) { repeat(7) { EvShimmerBox(Modifier.weight(1f).height(96.dp), RoundedCornerShape(16.dp)) } }
                is Section.Failed -> SectionError(target.message, onRetry, Modifier.padding(horizontal = EvSpace.screen))
                is Section.Ready -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        target.value.forEach { day ->
                            DayCell(day, { onOpenPlanner(day.info.iso) }, Modifier.weight(1f))
                        }
                    }
                    if (target.value.none { it.hasEntry }) {
                        Text(
                            "Aún no has planeado esta semana. Toca un día para elegir tu look.",
                            style = EvType.bodySmall, color = EvTheme.colors.inkMuted,
                            modifier = Modifier.padding(horizontal = EvSpace.screen),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: WeekDayUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    val today = day.info.isToday
    val label = buildString {
        append(day.info.letter).append(' ').append(day.info.dayOfMonth)
        if (today) append(", hoy")
        append(if (day.look != null) ", con ${day.look.name}" else if (day.hasEntry) ", con nota" else ", sin plan")
        if (day.isCycle) append(", día de ciclo")
    }
    val tileShape = RoundedCornerShape(16.dp)
    Column(
        modifier.evClickable(pressedScale = 0.9f, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label }.padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(day.info.letter, style = EvType.caption, color = if (today) c.ink else c.inkMuted)
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.62f).clip(tileShape)
                .background(if (day.isCycle) c.accent.copy(alpha = if (c.isDark) 0.22f else 0.12f) else c.surface)
                .let {
                    when {
                        today -> it.border(2.dp, c.ink, tileShape)
                        day.image != null -> it.border(1.dp, c.outline, tileShape)
                        else -> it.border(BorderStroke(1.dp, c.outline), tileShape)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (day.image != null) {
                EvImage(day.image, null, Modifier.fillMaxSize().alpha(if (day.isCycle) 0.55f else 1f))
            } else if (!day.isCycle) {
                Box(Modifier.size(if (today) 8.dp else 5.dp).clip(CircleShape).background(if (today) c.ink else c.outline))
            }
            if (day.isCycle) Icon(Icons.Rounded.WaterDrop, null, tint = c.accent, modifier = Modifier.size(22.dp))
            if (day.hasEntry && day.look == null && !day.isCycle) {
                Icon(Icons.Rounded.CalendarMonth, null, tint = c.inkSoft, modifier = Modifier.size(18.dp))
            }
        }
        Text(
            "${day.info.dayOfMonth}", style = EvType.caption,
            color = if (today) c.ink else c.inkMuted,
        )
    }
}

// ───────────────────────── Accesos rápidos ─────────────────────────

@Composable
private fun QuickAccess(counts: QuickCounts, countsReady: Boolean, actions: HomeActions, modifier: Modifier = Modifier) {
    val closetText = when {
        !countsReady -> null
        else -> plural(counts.garments, "prenda", "prendas") + if (counts.looks > 0) " · " + plural(counts.looks, "look", "looks") else ""
    }
    val wishText = counts.favorites?.let { if (it == 0) "Vacía" else plural(it, "favorito", "favoritos") }
    val tripText = counts.trips?.let { if (it == 0) "Planifica tu maleta" else plural(it, "viaje", "viajes") }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickTile(Icons.Rounded.Checkroom, "Mi Armario", closetText, Color(0xFF10B981), actions.onOpenWardrobe, Modifier.weight(1f))
            QuickTile(Icons.Rounded.Favorite, "Wishlist", wishText, Color(0xFFF43F5E), actions.onOpenWishlist, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickTile(Icons.Rounded.Luggage, "Mis Viajes", tripText, Color(0xFF3B82F6), actions.onOpenSuitcase, Modifier.weight(1f))
            QuickTile(Icons.Rounded.Groups, "Comunidad", "Inspírate con looks", Color(0xFFF59E0B), actions.onOpenCommunity, Modifier.weight(1f))
        }
    }
}

private fun plural(n: Int, one: String, many: String) = if (n == 1) "1 $one" else "$n $many"

@Composable
private fun QuickTile(
    icon: ImageVector, title: String, subtitle: String?, tint: Color, onClick: () -> Unit, modifier: Modifier = Modifier,
) {
    val c = EvTheme.colors
    Column(
        modifier.clip(EvShapes.card).background(c.surface)
            .evClickable(pressedScale = 0.96f, onClick = onClick)
            .padding(16.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$title. ${subtitle.orEmpty()}" },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = if (c.isDark) 0.22f else 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp)) }
            Icon(Icons.Rounded.ChevronRight, null, tint = c.inkMuted, modifier = Modifier.size(20.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = EvType.subtitle, color = c.ink)
            Box(Modifier.animateContentSize()) {
                if (subtitle == null) EvShimmerBox(Modifier.width(90.dp).height(12.dp), RoundedCornerShape(6.dp))
                else Text(subtitle, style = EvType.bodySmall, color = c.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ───────────────────────── Compartidos ─────────────────────────

/** Error de una sección con reintento (el resto de la pantalla sigue usable). */
@Composable
fun SectionError(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    Row(
        modifier.fillMaxWidth().clip(EvShapes.cardSmall).background(c.surface).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.CloudOff, null, tint = c.inkMuted, modifier = Modifier.size(24.dp))
        Text(message, style = EvType.bodySmall, color = c.inkSoft, modifier = Modifier.weight(1f), maxLines = 3)
        EvChip(text = "Reintentar", selected = false, onClick = onRetry)
    }
}
