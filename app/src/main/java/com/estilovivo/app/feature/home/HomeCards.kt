package com.estilovivo.app.feature.home

import com.estilovivo.app.core.design.asPanel
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle

import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.estilovivo.app.core.data.model.Garment
import com.estilovivo.app.core.data.model.Look
import com.estilovivo.app.core.design.EvMotion
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.EvButtonStyle
import com.estilovivo.app.core.design.components.EvChip
import com.estilovivo.app.core.design.components.EvEmptyState
import com.estilovivo.app.core.design.components.EvImage
import com.estilovivo.app.core.design.components.EvPillButton
import com.estilovivo.app.core.design.components.EvShimmerBox
import com.estilovivo.app.core.design.evClickable

// ───────────────────────── Nivel / XP ─────────────────────────

@Composable
fun LevelCard(section: Section<LevelUi>, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = section, contentKey = { it::class }, modifier = modifier, label = "level",
        transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) },
    ) { target ->
        when (target) {
            Section.Loading -> EvShimmerBox(Modifier.fillMaxWidth().height(112.dp), EvShapes.card)
            is Section.Failed -> SectionError(target.message, onRetry)
            is Section.Ready -> LevelCardContent(target.value)
        }
    }
}

@Composable
private fun LevelCardContent(level: LevelUi) {
    val c = EvTheme.colors.asPanel()
    var target by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(level.progress.fraction) { target = level.progress.fraction }
    val fraction by animateFloatAsState(target, EvMotion.smooth(), label = "xpBar")
    val xp by animateIntAsState(level.progress.current, tween(900), label = "xpCount")
    val summary = "Nivel ${level.progress.level}, ${level.title}. ${level.progress.current} de ${level.progress.needed} XP" +
        if (level.streak > 1) ". Racha de ${level.streak} días" else ""
    Column(
        Modifier.fillMaxWidth().clip(EvShapes.card).background(c.ink).padding(18.dp)
            .semantics(mergeDescendants = true) { contentDescription = summary },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(
                Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(c.onInk.copy(alpha = 0.14f)),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Text("NV.", style = EvType.caption, color = c.onInk.copy(alpha = 0.7f))
                Text("${level.progress.level}", style = EvType.title, color = c.onInk)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(level.title, style = EvType.title, color = c.onInk)
                Text(
                    if (!level.hasXp) "¡Sube una prenda para ganar tus primeros XP!" else "${level.progress.toNext} XP para el siguiente nivel",
                    style = EvType.bodySmall, color = c.onInk.copy(alpha = 0.7f),
                )
            }
            if (level.streak > 0) StreakPill(level.streak)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(c.onInk.copy(alpha = 0.16f))) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0.02f, 1f)).clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(c.accent, Color(0xFFFFB86B)))),
                )
            }
            Text("$xp / ${level.progress.needed} XP", style = EvType.caption, color = c.onInk.copy(alpha = 0.75f))
        }
    }
}

@Composable
private fun StreakPill(days: Int) {
    val c = EvTheme.colors
    Row(
        Modifier.clip(CircleShape).background(c.onInk.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Rounded.LocalFireDepartment, null, tint = Color(0xFFFFB86B), modifier = Modifier.size(16.dp))
        Text("$days", style = EvType.label, color = c.onInk)
    }
}

// ───────────────────────── Tiempo ─────────────────────────

private fun gradientFor(condition: WeatherCondition): List<Color> = when (condition) {
    WeatherCondition.Sunny -> listOf(Color(0xFFF59E0B), Color(0xFFEF6C3A))
    WeatherCondition.Hot -> listOf(Color(0xFFEF4444), Color(0xFFEA580C))
    WeatherCondition.Cloudy -> listOf(Color(0xFF64748B), Color(0xFF3B5B8C))
    WeatherCondition.Rainy -> listOf(Color(0xFF3B6FB6), Color(0xFF1E3A8A))
    WeatherCondition.Snowy -> listOf(Color(0xFF5B8DB8), Color(0xFF3B5B8C))
    WeatherCondition.Windy -> listOf(Color(0xFF0F8A8A), Color(0xFF1E5F8C))
    WeatherCondition.Cold -> listOf(Color(0xFF4F8FD0), Color(0xFF2D4F9C))
}

@Composable
fun WeatherCard(
    weather: WeatherUi,
    onRetry: () -> Unit,
    onAskLocation: () -> Unit,
    onOpenWardrobe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = weather.section, contentKey = { it::class }, modifier = modifier.animateContentSize(), label = "weather",
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(160)) },
    ) { target ->
        when (val s = target) {
            Section.Loading -> EvShimmerBox(Modifier.fillMaxWidth().height(150.dp), EvShapes.card)
            is Section.Failed -> NoWeatherCard(onRetry, if (weather.canAskLocation) onAskLocation else null)
            is Section.Ready -> WeatherContent(s.value, weather, onRetry, onAskLocation, onOpenWardrobe)
        }
    }
}

@Composable
private fun NoWeatherCard(onRetry: () -> Unit, onAskLocation: (() -> Unit)?) {
    val c = EvTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(EvShapes.card).background(c.surface).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.CloudOff, null, tint = c.inkMuted, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text("Sin datos del tiempo", style = EvType.subtitle, color = c.ink)
                Text("No hemos podido consultar el tiempo ahora mismo.", style = EvType.bodySmall, color = c.inkSoft)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EvChip("Reintentar", false, onRetry, leading = Icons.Rounded.Refresh)
            if (onAskLocation != null) EvChip("Usar mi ubicación", false, onAskLocation, leading = Icons.Rounded.MyLocation)
        }
    }
}

@Composable
private fun WeatherContent(
    w: WeatherData,
    ui: WeatherUi,
    onRetry: () -> Unit,
    onAskLocation: () -> Unit,
    onOpenWardrobe: () -> Unit,
) {
    val temp by animateIntAsState(w.temp, tween(700), label = "temp")
    val desc = "${w.temp} grados, ${w.description}, ${w.place}. ${ui.advice.orEmpty()}"
    Column(
        Modifier.fillMaxWidth().clip(EvShapes.card).background(Brush.linearGradient(gradientFor(w.condition))).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            Modifier.semantics(mergeDescendants = true) { contentDescription = desc },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            WeatherGlyph(w.condition, Modifier.size(76.dp))
            Column(Modifier.weight(1f)) {
                Text("$temp°", style = EvType.bigNumber, color = Color.White)
                Text(w.description, style = EvType.subtitle, color = Color.White.copy(alpha = 0.92f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Icon(Icons.Rounded.LocationOn, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
                    Text(
                        buildString { append(w.place); if (w.humidity > 0) append(" · ${w.humidity}% hum.") },
                        style = EvType.caption, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f))
                    .evClickable(onClick = onRetry).semantics { contentDescription = "Actualizar el tiempo" },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Refresh, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
        }
        if (w.stale) {
            Text("Sin conexión: mostrando la última lectura.", style = EvType.caption, color = Color.White.copy(alpha = 0.85f))
        }
        ui.advice?.let { Text(it, style = EvType.body, color = Color.White) }
        if (ui.tips.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("SUGERENCIA PARA HOY", style = EvType.caption, color = Color.White.copy(alpha = 0.75f))
                ui.tips.forEach { tip ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
                        Text(tip.reason, style = EvType.bodySmall, color = Color.White)
                    }
                }
            }
        }
        if (ui.picks.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("DE TU ARMARIO PARA HOY", style = EvType.caption, color = Color.White.copy(alpha = 0.75f))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ui.picks, key = { it.id }) { g ->
                        EvImage(
                            g.imageUrl, g.name,
                            Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.2f))
                                .evClickable(onClick = onOpenWardrobe),
                        )
                    }
                }
            }
        }
        if (ui.canAskLocation) {
            EvChip("Usar mi ubicación", false, onAskLocation, leading = Icons.Rounded.MyLocation)
        }
    }
}

// ───────────────────────── Look de hoy planeado ─────────────────────────

@Composable
fun TodayLookCard(look: Look, note: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = EvTheme.colors.asPanel()
    val garments = look.garments.filter { it.imageUrl.isNotBlank() }
    val count = look.garments.size
    Row(
        modifier.fillMaxWidth().clip(EvShapes.card).background(c.ink)
            .evClickable(pressedScale = 0.97f, onClick = onClick)
            .padding(14.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Look de hoy: ${look.name}. Abrir planificador" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(84.dp).clip(RoundedCornerShape(22.dp)).background(c.onInk.copy(alpha = 0.12f))) {
            when {
                garments.size >= 2 -> GarmentCollage(garments, Modifier.fillMaxSize(), gap = 2.dp, corner = 0.dp)
                look.imageUrl != null -> EvImage(look.imageUrl, look.name, Modifier.fillMaxSize())
                garments.size == 1 -> EvImage(garments[0].imageUrl, look.name, Modifier.fillMaxSize())
                else -> Icon(Icons.Rounded.Checkroom, null, tint = c.onInk.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.Center).size(30.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("LOOK DE HOY", style = EvType.caption, color = c.onInk.copy(alpha = 0.65f))
            Text(look.name, style = EvType.title, color = c.onInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                if (count == 1) "1 prenda" else if (count > 1) "$count prendas" else "Sin prendas",
                style = EvType.bodySmall, color = c.onInk.copy(alpha = 0.75f),
            )
            if (note != null) Text(note, style = EvType.caption, color = c.onInk.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(40.dp).clip(CircleShape).background(c.onInk.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.ChevronRight, null, tint = c.onInk)
        }
    }
}

// ───────────────────────── Recomendador ─────────────────────────

@Composable
fun RecommendationCard(
    reco: RecoUi,
    mood: String?,
    hasPlannedToday: Boolean,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    val c = EvTheme.colors
    Column(
        modifier.fillMaxWidth().clip(EvShapes.card).background(c.surface).padding(16.dp).animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val moodLabel = Moods.byId(mood)?.let { " · ${it.emoji}" }.orEmpty()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(c.accent.copy(alpha = if (c.isDark) 0.22f else 0.12f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = c.accent, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(if (hasPlannedToday) "Otra idea para hoy" else "Crear look de hoy", style = EvType.title, color = c.ink)
                Text("Según tu mood y el tiempo$moodLabel", style = EvType.bodySmall, color = c.inkSoft)
            }
            if (reco is RecoUi.Ready && reco.total > 1) {
                Text("${reco.position}/${reco.total}", style = EvType.caption, color = c.inkMuted)
            }
        }
        AnimatedContent(
            targetState = reco, contentKey = { it::class }, label = "recoState",
            transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) },
        ) { target ->
            when (target) {
                RecoUi.Loading -> EvShimmerBox(Modifier.fillMaxWidth().height(280.dp), EvShapes.cardSmall)
                RecoUi.NotEnoughGarments -> EvEmptyState(
                    Icons.Rounded.Checkroom, "Añade tus primeras prendas",
                    "Con al menos 2 prendas te proponemos un look nuevo cada día.",
                    actionText = "Ir a mi armario", onAction = actions.onOpenWardrobe,
                )
                is RecoUi.NoCombination -> EvEmptyState(
                    Icons.Rounded.Style, "Aún no podemos combinar", target.message,
                    actionText = "Ir a mi armario", onAction = actions.onOpenWardrobe,
                )
                is RecoUi.Failed -> SectionError(target.message, actions.onRetryGarments)
                is RecoUi.Ready -> ProposalContent(target, actions)
            }
        }
    }
}

@Composable
private fun ProposalContent(reco: RecoUi.Ready, actions: HomeActions) {
    val c = EvTheme.colors
    val spin = remember { Animatable(0f) }
    LaunchedEffect(reco.shuffleTick) {
        if (reco.shuffleTick > 0) {
            spin.snapTo(0f)
            spin.animateTo(360f, tween(520))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AnimatedContent(
            targetState = reco.outfit,
            contentKey = { it.key },
            label = "proposal",
            transitionSpec = {
                (slideInHorizontally(EvMotion.smooth()) { it / 3 } + fadeIn(tween(240)) + scaleIn(EvMotion.smooth(), 0.9f)) togetherWith
                    (slideOutHorizontally(tween(220)) { -it / 3 } + fadeOut(tween(160)) + scaleOut(tween(220), 0.9f))
            },
        ) { outfit ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GarmentCollage(
                    outfit.garments, Modifier.fillMaxWidth().height(280.dp).clip(EvShapes.cardSmall),
                    gap = 6.dp, corner = 18.dp,
                )
                Text(
                    outfit.garments.joinToString(" · ") { it.name }, style = EvType.bodySmall, color = c.inkSoft,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (outfit.reasons.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(outfit.reasons, key = { it }) { r ->
                            Text(
                                r, style = EvType.caption, color = c.inkSoft,
                                modifier = Modifier.clip(CircleShape).background(c.surfaceAlt).padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EvPillButton(
                text = if (reco.savedLookId != null) "Guardado" else "Guardar look",
                onClick = actions.onSaveLook,
                modifier = Modifier.weight(1f),
                style = EvButtonStyle.Secondary,
                icon = if (reco.savedLookId != null) Icons.Rounded.Check else Icons.Rounded.BookmarkAdd,
                loading = reco.saving,
                enabled = reco.savedLookId == null && !reco.planning,
                height = 52.dp,
            )
            EvPillButton(
                text = if (reco.plannedToday) "Planeado" else "Planear hoy",
                onClick = actions.onPlanToday,
                modifier = Modifier.weight(1f),
                icon = if (reco.plannedToday) Icons.Rounded.Check else Icons.Rounded.EventAvailable,
                loading = reco.planning,
                enabled = !reco.plannedToday && !reco.saving,
                height = 52.dp,
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).heightIn48().clip(CircleShape).evClickable(onClick = actions.onNextProposal)
                    .semantics { contentDescription = "Otra propuesta de look" },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.Shuffle, null, tint = c.ink, modifier = Modifier.padding(start = 6.dp).size(20.dp).rotate(spin.value))
                Text("Otra propuesta", style = EvType.label, color = c.ink)
            }
            EvChip("A mano", false, actions.onCreateLook, leading = Icons.Rounded.Palette)
            EvChip("Probador", false, actions.onTryOn, leading = Icons.Rounded.Style)
        }
    }
}

private fun Modifier.heightIn48(): Modifier = this.then(Modifier.height(48.dp))

// ───────────────────────── Collage de prendas ─────────────────────────

/** Collage adaptativo: 1 prenda a pantalla completa, 2 en columnas, 3 con una grande, 4+ en cuadrícula (+N). */
@Composable
fun GarmentCollage(garments: List<Garment>, modifier: Modifier = Modifier, gap: androidx.compose.ui.unit.Dp = 6.dp, corner: androidx.compose.ui.unit.Dp = 18.dp) {
    val c = EvTheme.colors
    val shape = RoundedCornerShape(corner)
    @Composable
    fun Tile(g: Garment, tileModifier: Modifier, extra: Int = 0) {
        Box(tileModifier.clip(shape).background(c.surfaceAlt)) {
            EvImage(g.imageUrl, g.name, Modifier.fillMaxSize())
            if (extra > 0) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                    Text("+$extra", style = EvType.title, color = Color.White)
                }
            }
        }
    }
    val shown = garments.take(4)
    when (shown.size) {
        0 -> Box(modifier.background(c.surfaceAlt), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Checkroom, null, tint = c.inkMuted, modifier = Modifier.size(32.dp))
        }
        1 -> Tile(shown[0], modifier)
        2 -> Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap)) {
            shown.forEach { Tile(it, Modifier.weight(1f).fillMaxHeight()) }
        }
        3 -> Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap)) {
            Tile(shown[0], Modifier.weight(1.1f).fillMaxHeight())
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                Tile(shown[1], Modifier.weight(1f).fillMaxWidth())
                Tile(shown[2], Modifier.weight(1f).fillMaxWidth())
            }
        }
        else -> Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Tile(shown[0], Modifier.weight(1f).fillMaxHeight())
                Tile(shown[1], Modifier.weight(1f).fillMaxHeight())
            }
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Tile(shown[2], Modifier.weight(1f).fillMaxHeight())
                Tile(shown[3], Modifier.weight(1f).fillMaxHeight(), extra = (garments.size - 4).coerceAtLeast(0))
            }
        }
    }
}
