package com.estilovivo.app.feature.profile

import com.estilovivo.app.core.design.asPanel
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.estilovivo.app.app.CreateLook
import com.estilovivo.app.app.GarmentDetail
import com.estilovivo.app.app.LocalNavigator
import com.estilovivo.app.app.Wishlist
import com.estilovivo.app.core.data.model.Achievement
import com.estilovivo.app.core.data.model.Badge
import com.estilovivo.app.core.data.model.Garment
import com.estilovivo.app.core.design.EvMotion
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvSpace
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.EvButtonStyle
import com.estilovivo.app.core.design.components.EvEmptyState
import com.estilovivo.app.core.design.components.EvImage
import com.estilovivo.app.core.design.components.EvPillButton
import com.estilovivo.app.core.design.components.EvSectionHeader
import com.estilovivo.app.core.design.components.EvShimmerBox
import com.estilovivo.app.core.design.components.EvStatTile
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.staggeredEntrance
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Muestra esqueleto / error con reintento / contenido según el estado de carga. */
@Composable
internal fun <T> LoadableBox(state: Loadable<T>, onRetry: () -> Unit, content: @Composable (T) -> Unit) {
    when (state) {
        Loadable.Loading -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            EvShimmerBox(Modifier.fillMaxWidth().height(110.dp), EvShapes.card)
            EvShimmerBox(Modifier.fillMaxWidth().height(180.dp), EvShapes.card)
            EvShimmerBox(Modifier.fillMaxWidth().height(110.dp), EvShapes.card)
        }
        is Loadable.Error -> EvEmptyState(Icons.Rounded.CloudOff, "No pudimos cargarlo", state.message, actionText = "Reintentar", onAction = onRetry)
        is Loadable.Ready -> content(state.value)
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) =
    Box(modifier.fillMaxWidth().clip(EvShapes.card).background(EvTheme.colors.surface).padding(18.dp)) { content() }

// ─────────────────────────────── Estadísticas ───────────────────────────────

@Composable
internal fun StatsSection(state: Loadable<StatsUi>, onRetry: () -> Unit) {
    LoadableBox(state, onRetry) { ui -> StatsContent(ui) }
}

@Composable
private fun StatsContent(ui: StatsUi) {
    val c = EvTheme.colors
    val nav = LocalNavigator.current
    val st = ui.stats
    Column(verticalArrangement = Arrangement.spacedBy(EvSpace.lg)) {
        Row(Modifier.staggeredEntrance(0), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EvStatTile("${st.garmentCount}", "Prendas", Modifier.weight(1f))
            EvStatTile("${st.lookCount}", "Looks", Modifier.weight(1f))
        }
        Row(Modifier.staggeredEntrance(1), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EvStatTile("${st.salesCount}", "A la venta", Modifier.weight(1f))
            EvStatTile("${st.tripsCount}", "Viajes", Modifier.weight(1f))
        }
        Row(Modifier.staggeredEntrance(2), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EvStatTile("${st.totalUsage}", "Usos totales", Modifier.weight(1f))
            EvStatTile("${ui.averageUsage}×", "Uso medio", Modifier.weight(1f))
        }

        if (ui.categories.isNotEmpty()) {
            Card(Modifier.staggeredEntrance(3)) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Por categoría", style = EvType.title, color = c.ink)
                    DonutChart(ui.categories, "prendas")
                }
            }
        }
        if (ui.seasons.size > 1) {
            Card(Modifier.staggeredEntrance(4)) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Por temporada", style = EvType.title, color = c.ink)
                    DonutChart(ui.seasons, "prendas")
                }
            }
        }

        st.mostWorn?.let { g ->
            Card(Modifier.staggeredEntrance(5)) {
                Row(
                    Modifier.evClickable(pressedScale = 0.98f, role = null) { nav.navigate(GarmentDetail(g.id)) },
                    horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    EvImage(g.imageUrl, g.name, Modifier.size(84.dp).clip(EvShapes.cardSmall))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.Star, null, tint = c.warning, modifier = Modifier.size(16.dp))
                            Text("Tu prenda más usada", style = EvType.caption, color = c.inkSoft)
                        }
                        Text(g.name.ifBlank { ProfileRules.categoryLabel(g.type) }, style = EvType.title, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(if (g.usageCount == 1) "1 uso" else "${g.usageCount} usos", style = EvType.label, color = c.inkSoft)
                    }
                }
            }
        }

        if (st.leastWornCount > 0) {
            Column(Modifier.staggeredEntrance(6), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (st.leastWornCount == 1) "1 prenda con menos de 2 usos" else "${st.leastWornCount} prendas con menos de 2 usos",
                    style = EvType.title, color = c.ink,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 8.dp)) {
                    items(st.leastWorn, key = { it.id }) { g -> LeastWornCard(g) { nav.navigate(GarmentDetail(g.id)) } }
                }
                EvPillButton("Crear un look con ellas", { nav.navigate(CreateLook()) }, Modifier.fillMaxWidth(), style = EvButtonStyle.Secondary, height = 52.dp)
            }
        }

        if (ui.recommendations.isNotEmpty()) {
            Card(Modifier.staggeredEntrance(7)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Lightbulb, null, tint = c.warning)
                        Text("Para ti", style = EvType.title, color = c.ink)
                    }
                    ui.recommendations.forEach { Text("•  $it", style = EvType.body, color = c.inkSoft) }
                }
            }
        }
    }
}

@Composable
private fun LeastWornCard(g: Garment, onClick: () -> Unit) {
    val c = EvTheme.colors
    Column(Modifier.size(width = 116.dp, height = 156.dp).clip(EvShapes.cardSmall).background(c.surface).evClickable(pressedScale = 0.96f, role = null, onClick = onClick)) {
        EvImage(g.imageUrl, g.name, Modifier.fillMaxWidth().height(112.dp))
        Text(
            "${g.name.ifBlank { ProfileRules.categoryLabel(g.type) }} · ${g.usageCount}×", style = EvType.caption, color = c.ink,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(8.dp),
        )
    }
}

// ─────────────────────────────── Progreso ───────────────────────────────

private fun shortDate(iso: String?): String? =
    ProfileRules.parseDate(iso)?.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es", "ES")))

@Composable
internal fun ProgressSection(state: Loadable<ProgressUi>, onRetry: () -> Unit) {
    LoadableBox(state, onRetry) { ui -> ProgressContent(ui) }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ProgressContent(ui: ProgressUi) {
    val c = EvTheme.colors
    val lv = ui.level
    val bar = remember { Animatable(0f) }
    LaunchedEffect(lv.fraction) { bar.animateTo(lv.fraction, tween(900, easing = EvMotion.emphasized)) }
    Column(verticalArrangement = Arrangement.spacedBy(EvSpace.lg)) {
        Column(
            Modifier.fillMaxWidth().staggeredEntrance(0).clip(EvShapes.hero).background(c.asPanel().ink).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val c = c.asPanel()
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("NIVEL", style = EvType.caption, color = c.onInk.copy(alpha = 0.7f))
                    Text("${lv.level}", style = EvType.bigNumber.copy(fontSize = 52.sp, lineHeight = 54.sp), color = c.onInk)
                }
                Text("${ui.progress.experiencePoints} XP", style = EvType.title, color = c.onInk)
            }
            Box(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(c.onInk.copy(alpha = 0.2f))) {
                Box(Modifier.fillMaxWidth(bar.value.coerceIn(0.02f, 1f)).height(12.dp).clip(CircleShape).background(c.brandOnInk))
            }
            Text("${lv.current} / ${lv.needed} XP · te faltan ${lv.remaining} para el nivel ${lv.level + 1}", style = EvType.bodySmall, color = c.onInk.copy(alpha = 0.75f))
        }

        Row(Modifier.staggeredEntrance(1), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EvStatTile("${ui.progress.streak.loginCount}", if (ui.progress.streak.loginCount == 1) "día de racha" else "días de racha", Modifier.weight(1f))
            EvStatTile("${ui.progress.challengeCount}", "retos completados", Modifier.weight(1f))
        }

        val unlocked = ui.achievements.count { it.unlocked }
        EvSectionHeader("Logros · $unlocked/${ui.achievements.size}", Modifier.padding(horizontal = 0.dp))
        if (ui.achievements.isEmpty()) {
            Text("Aún no hay logros que mostrar.", style = EvType.body, color = c.inkSoft)
        }
        ui.achievements.forEachIndexed { i, a -> AchievementRow(a, Modifier.staggeredEntrance(i + 2)) }

        if (ui.badges.isNotEmpty()) {
            EvSectionHeader("Insignias", Modifier.padding(horizontal = 0.dp))
            ui.badges.groupBy { it.category }.forEach { (cat, list) ->
                Text(badgeCategory(cat), style = EvType.label, color = c.inkSoft)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    list.forEach { BadgeChip(it) }
                }
            }
        }
    }
}

private fun badgeCategory(key: String) = when (key) {
    "retos" -> "Retos"
    "nivel" -> "Nivel"
    "social" -> "Comunidad"
    "rachas" -> "Rachas"
    else -> key.replaceFirstChar { it.uppercase() }
}

@Composable
private fun AchievementRow(a: Achievement, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    Row(
        modifier.fillMaxWidth().clip(EvShapes.cardSmall).background(c.surface).padding(14.dp).alpha(if (a.unlocked) 1f else 0.6f),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(c.surfaceAlt), contentAlignment = Alignment.Center) {
            if (a.unlocked) Text(a.icon, fontSize = 26.sp) else Icon(Icons.Rounded.Lock, "Bloqueado", tint = c.inkMuted)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(a.title, style = EvType.label, color = c.ink)
            Text(a.description, style = EvType.bodySmall, color = c.inkSoft)
            Text(
                if (a.unlocked) "+${a.xpReward} XP" + (shortDate(a.unlockedAt)?.let { " · $it" } ?: "") else "+${a.xpReward} XP al desbloquear",
                style = EvType.caption, color = if (a.unlocked) c.success else c.inkMuted,
            )
        }
    }
}

@Composable
private fun BadgeChip(b: Badge) {
    val c = EvTheme.colors
    Column(
        Modifier.size(width = 98.dp, height = 104.dp).clip(EvShapes.cardSmall).background(c.surface).padding(8.dp).alpha(if (b.unlocked) 1f else 0.45f),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        if (b.unlocked) Text(b.icon, fontSize = 30.sp) else Icon(Icons.Rounded.Lock, "Bloqueada", tint = c.inkMuted, modifier = Modifier.size(30.dp))
        Text(b.title, style = EvType.caption, color = c.ink, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

// ─────────────────────────────── Favoritos ───────────────────────────────

@Composable
internal fun FavoritesSection(state: Loadable<FavoritesUi>, onRetry: () -> Unit) {
    val nav = LocalNavigator.current
    val c = EvTheme.colors
    LoadableBox(state, onRetry) { ui ->
        Column(verticalArrangement = Arrangement.spacedBy(EvSpace.lg)) {
            Row(Modifier.staggeredEntrance(0), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EvStatTile("${ui.looks}", "Looks guardados", Modifier.weight(1f))
                EvStatTile("${ui.garments}", "Prendas guardadas", Modifier.weight(1f))
            }
            if (ui.previews.isEmpty()) {
                EvEmptyState(Icons.Rounded.Favorite, "Aún no tienes favoritos", "Toca el corazón en un look o una prenda y la verás aquí.")
            } else {
                LazyRow(Modifier.staggeredEntrance(1), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(ui.previews, key = { it }) { url ->
                        EvImage(url, "Favorito", Modifier.size(96.dp).clip(EvShapes.cardSmall), contentScale = ContentScale.Crop)
                    }
                }
            }
            EvPillButton("Ver mi lista de deseos", { nav.navigate(Wishlist) }, Modifier.fillMaxWidth().staggeredEntrance(2), icon = Icons.Rounded.Bookmark)
            Text("Aquí tienes un resumen; tu lista de deseos reúne todo lo que quieres conseguir.", style = EvType.bodySmall, color = c.inkSoft, modifier = Modifier.staggeredEntrance(3))
        }
    }
}
