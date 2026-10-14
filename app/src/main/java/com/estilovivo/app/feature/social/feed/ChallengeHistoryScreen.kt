package com.estilovivo.app.feature.social.feed

import com.estilovivo.app.core.design.asPanel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.estilovivo.app.app.LocalNavigator
import com.estilovivo.app.core.data.model.PastChallenge
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvSpace
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.EvCircleButton
import com.estilovivo.app.core.design.components.EvEmptyState
import com.estilovivo.app.core.design.components.EvHeadline
import com.estilovivo.app.core.design.components.EvImage
import com.estilovivo.app.core.design.components.EvShimmerBox
import com.estilovivo.app.core.design.staggeredEntrance
import org.koin.androidx.compose.koinViewModel

/** Historial de retos pasados con mi participación y el XP total ganado. */
@Composable
fun ChallengeHistoryScreen(modifier: Modifier = Modifier) {
    val vm: ChallengeHistoryViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val c = EvTheme.colors
    val history = state.history

    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = EvSpace.screen, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            EvCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, nav::back, contentDescription = "Volver")
        }
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { vm.load(refresh = true) }, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = EvSpace.screen, end = EvSpace.screen, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "header") {
                    Column(Modifier.staggeredEntrance(0), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        EvHeadline("Historial de\n*retos*")
                        if (history != null) XpTotalCard(history.totalXp, history.challenges.count { it.mySubmission != null })
                    }
                }
                when {
                    state.loading && history == null -> items(3) { EvShimmerBox(Modifier.fillMaxWidth().height(96.dp), EvShapes.card) }
                    state.error != null && history == null -> item(key = "error") {
                        EvEmptyState(Icons.Rounded.CloudOff, "No pudimos cargar el historial", state.error.orEmpty(), actionText = "Reintentar", onAction = { vm.load() })
                    }
                    history == null || history.challenges.isEmpty() -> item(key = "empty") {
                        EvEmptyState(Icons.Rounded.EmojiEvents, "Aún no hay retos pasados", "Participa en el reto semanal y aquí verás tu recorrido.")
                    }
                    else -> itemsIndexed(history.challenges, key = { _, p -> p.challenge.id }) { i, p ->
                        PastChallengeRow(p, Modifier.staggeredEntrance(i + 1))
                    }
                }
            }
        }
    }
}

@Composable
private fun XpTotalCard(totalXp: Int, completed: Int) {
    val c = EvTheme.colors.asPanel()
    Row(
        Modifier.fillMaxWidth().clip(EvShapes.card).background(c.ink).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(Icons.Rounded.EmojiEvents, null, tint = c.onInk, modifier = Modifier.size(36.dp))
        Column(Modifier.weight(1f)) {
            Text("XP ganado en retos", style = EvType.caption, color = c.onInk.copy(alpha = 0.7f))
            Text("$totalXp XP", style = EvType.bigNumber, color = c.onInk)
        }
        EvPill(if (completed == 1) "1 reto" else "$completed retos", container = c.onInk.copy(alpha = 0.15f), content = c.onInk)
    }
}

@Composable
private fun PastChallengeRow(p: PastChallenge, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    val sub = p.mySubmission
    Row(
        modifier.fillMaxWidth().clip(EvShapes.card).background(c.surface).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(72.dp).clip(EvShapes.cardSmall).background(c.surfaceAlt), contentAlignment = Alignment.Center) {
            if (sub?.imageUrl != null) EvImage(sub.imageUrl, "Tu participación en ${p.challenge.title}", Modifier.fillMaxSize())
            else Icon(Icons.Rounded.AutoAwesome, null, tint = c.inkMuted)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(p.challenge.title, style = EvType.subtitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) {
                Text(sub.description, style = EvType.bodySmall, color = c.inkSoft, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = c.success, modifier = Modifier.size(16.dp))
                    Text(if (sub.awarded) "+${p.challenge.reward} XP" else "Completado", style = EvType.caption, color = c.success)
                    Text(relativeTime(sub.createdAt), style = EvType.caption, color = c.inkMuted)
                }
            } else {
                Text("No participaste en este reto", style = EvType.bodySmall, color = c.inkMuted)
            }
        }
    }
}
