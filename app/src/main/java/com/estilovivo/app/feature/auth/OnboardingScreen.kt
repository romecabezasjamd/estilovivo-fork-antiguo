package com.estilovivo.app.feature.auth

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Camera
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.estilovivo.app.app.LocalNavigator
import com.estilovivo.app.core.design.EvMotion
import com.estilovivo.app.core.design.EvSpace
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.EvButtonStyle
import com.estilovivo.app.core.design.components.EvChip
import com.estilovivo.app.core.design.components.EvCircleButton
import com.estilovivo.app.core.design.components.EvConfetti
import com.estilovivo.app.core.design.components.EvHeadline
import com.estilovivo.app.core.design.components.EvPillButton
import com.estilovivo.app.core.design.components.LocalEvToast
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.floatingBob
import com.estilovivo.app.core.design.rememberEvHaptics
import com.estilovivo.app.core.design.staggeredEntrance
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import kotlin.math.absoluteValue

private class IntroPage(
    val title: String,
    val body: String,
    val main: ImageVector,
    val satA: ImageVector,
    val satB: ImageVector,
)

private val INTRO = listOf(
    IntroPage("Tu armario *digital*.", "Sube tus prendas, organízalas por categoría y sabe siempre qué tienes y qué llevas menos.", Icons.Rounded.Checkroom, Icons.Rounded.Favorite, Icons.Rounded.Today),
    IntroPage("Looks y *probador*.", "Combina prendas en looks y pruébatelos virtualmente con IA antes de ponerte nada.", Icons.Rounded.Style, Icons.Rounded.Camera, Icons.Rounded.AutoAwesome),
    IntroPage("Una *comunidad* con estilo.", "Comparte tus looks, sigue a otras personas y descubre inspiración cada día.", Icons.Rounded.Groups, Icons.Rounded.Favorite, Icons.Rounded.AutoAwesome),
)

/** Destino `Onboarding`: carrusel de bienvenida + quiz de estilo + confeti al terminar. */
@Composable
fun OnboardingRoute() {
    val vm: OnboardingViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val c = EvTheme.colors
    val context = LocalContext.current
    val haptics = rememberEvHaptics()
    var confetti by rememberSaveable { mutableIntStateOf(0) }
    var celebrating by rememberSaveable { mutableStateOf(false) }

    // Permiso de notificaciones en contexto, al final: pase lo que pase se entra a la app
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { nav.enterMain() }

    LaunchedEffect(vm) {
        vm.effects.collect {
            celebrating = true
            confetti++
            haptics.confirm()
            delay(1500)
            val needs = Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            if (needs) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else nav.enterMain()
        }
    }
    val toast = LocalEvToast.current
    LaunchedEffect(state.error) { state.error?.let { toast.error(it); haptics.reject() } }

    // Con sesión abierta no se vuelve al formulario: atrás sólo retrocede dentro del onboarding
    BackHandler(enabled = true) { if (!state.finished) vm.back() }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = EvSpace.screen),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (state.step > 0 && !state.finished) {
                    EvCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, onClick = vm::back, contentDescription = "Atrás")
                } else Box(Modifier.size(44.dp))
                if (!state.finished) {
                    Text(
                        "Saltar", style = EvType.label, color = c.inkSoft,
                        modifier = Modifier.clip(CircleShape)
                            .evClickable(pressedScale = 0.93f) { if (state.inQuiz) vm.skipAll() else vm.skipIntro() }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                }
            }

            AnimatedContent(
                targetState = state.inQuiz,
                transitionSpec = { (slideInHorizontally(tween(380, easing = EvMotion.emphasized)) { it / 4 } + fadeIn(tween(300))) togetherWith (slideOutHorizontally(tween(260)) { -it / 5 } + fadeOut(tween(160))) },
                modifier = Modifier.weight(1f),
                label = "onbPhase",
            ) { quiz ->
                if (!quiz) IntroPager(state, vm) else QuizStep(state, vm)
            }
        }

        // Celebración final
        AnimatedVisibility(celebrating, enter = fadeIn(tween(300)), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(c.bg), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(96.dp).clip(CircleShape).background(c.ink), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Check, null, tint = c.onInk, modifier = Modifier.size(44.dp))
                    }
                    EvHeadline("Todo *listo*.")
                    Text("Vamos a vestir tu día.", style = EvType.body, color = c.inkSoft)
                }
            }
        }
        EvConfetti(confetti)
    }
}

@Composable
private fun IntroPager(state: OnboardingState, vm: OnboardingViewModel) {
    val c = EvTheme.colors
    val pager = rememberPagerState(initialPage = state.step.coerceIn(0, INTRO_PAGES - 1)) { INTRO_PAGES }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager) {
        androidx.compose.runtime.snapshotFlow { pager.settledPage }.collect { vm.goTo(it) }
    }
    val page = pager.currentPage

    Column(Modifier.fillMaxSize()) {
        HorizontalPager(pager, Modifier.weight(1f)) { index ->
            val offset = ((pager.currentPage - index) + pager.currentPageOffsetFraction)
            IntroPageContent(INTRO[index], offset)
        }
        Column(Modifier.padding(horizontal = EvSpace.screen, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                repeat(INTRO_PAGES) { i ->
                    val w by animateDpAsState(if (i == page) 28.dp else 8.dp, EvMotion.snappy(), label = "dotW")
                    val col by animateColorAsState(if (i == page) c.ink else c.outline, tween(220), label = "dotC")
                    Box(Modifier.padding(horizontal = 4.dp).width(w).height(8.dp).clip(CircleShape).background(col))
                }
            }
            EvPillButton(
                text = if (page < INTRO_PAGES - 1) "Siguiente" else "Personalizar mi estilo",
                onClick = { if (page < INTRO_PAGES - 1) scope.launch { pager.animateScrollToPage(page + 1) } else vm.next() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Una página del carrusel; [offset] (−1..1) mueve cada capa a distinta velocidad (parallax). */
@Composable
private fun IntroPageContent(page: IntroPage, offset: Float) {
    val c = EvTheme.colors
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(300.dp).graphicsLayer { alpha = 1f - offset.absoluteValue.coerceAtMost(1f) * 0.6f },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(250.dp).graphicsLayer { translationX = offset * 60f }
                    .clip(CircleShape).background(c.surface).border(1.dp, c.outline, CircleShape),
            )
            Box(
                Modifier.size(150.dp).graphicsLayer { translationX = offset * 140f }.floatingBob(6f)
                    .clip(CircleShape).background(c.ink),
                contentAlignment = Alignment.Center,
            ) { Icon(page.main, null, tint = c.onInk, modifier = Modifier.size(70.dp)) }
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = (-10).dp, y = 14.dp).graphicsLayer { translationX = offset * 260f }.floatingBob(9f, 3000)
                    .size(64.dp).clip(CircleShape).background(c.surface).border(1.dp, c.outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(page.satA, null, tint = c.accent, modifier = Modifier.size(28.dp)) }
            Box(
                Modifier.align(Alignment.BottomStart).offset(x = 8.dp, y = (-18).dp).graphicsLayer { translationX = offset * 200f }.floatingBob(7f, 4200)
                    .size(56.dp).clip(CircleShape).background(c.surface).border(1.dp, c.outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(page.satB, null, tint = c.ink, modifier = Modifier.size(24.dp)) }
        }
        Column(
            Modifier.padding(top = 28.dp).graphicsLayer { translationX = offset * 80f; alpha = 1f - offset.absoluteValue.coerceAtMost(1f) },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EvHeadline(page.title)
            Text(page.body, style = EvType.body, color = c.inkSoft, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun QuizStep(state: OnboardingState, vm: OnboardingViewModel) {
    val c = EvTheme.colors
    val kind = state.kind ?: return
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = EvSpace.screen), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            QUIZ_STEPS.forEachIndexed { i, _ ->
                val filled = i <= state.step - INTRO_PAGES
                val col by animateColorAsState(if (filled) c.ink else c.outline, tween(260), label = "qBar")
                Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(col))
            }
        }
        AnimatedContent(
            targetState = kind,
            transitionSpec = { (slideInHorizontally(tween(340, easing = EvMotion.emphasized)) { it / 4 } + fadeIn(tween(260))) togetherWith (slideOutHorizontally(tween(200)) { -it / 5 } + fadeOut(tween(140))) },
            modifier = Modifier.weight(1f),
            label = "quizKind",
        ) { k ->
            val picks = state.picks(k)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = EvSpace.screen, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                EvHeadline(k.title)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(k.subtitle, style = EvType.body, color = c.inkSoft)
                    Text("${picks.size}/${k.max}", style = EvType.label, color = c.inkMuted)
                }
                Box(Modifier.height(10.dp))
                val options = QuizCatalog.options(k)
                if (k == QuizKind.Colors) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        options.forEachIndexed { i, o ->
                            ColorSwatch(o, selected = o.id in picks, onClick = { vm.toggle(k, o.id) }, modifier = Modifier.staggeredEntrance(i))
                        }
                    }
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        options.forEachIndexed { i, o ->
                            EvChip(o.label, selected = o.id in picks, onClick = { vm.toggle(k, o.id) }, modifier = Modifier.staggeredEntrance(i))
                        }
                    }
                }
            }
        }
        Column(Modifier.padding(horizontal = EvSpace.screen, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            EvPillButton(
                text = when {
                    state.error != null -> "Reintentar"
                    state.isLast -> "Completar"
                    else -> "Siguiente"
                },
                onClick = vm::next, loading = state.saving, modifier = Modifier.fillMaxWidth(),
            )
            if (state.error != null) {
                EvPillButton("Seguir sin guardar", onClick = vm::skipAll, style = EvButtonStyle.Ghost, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ColorSwatch(option: QuizOption, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    val ring by animateColorAsState(if (selected) c.ink else c.outline, tween(200), label = "swatchRing")
    val pad by animateDpAsState(if (selected) 4.dp else 0.dp, EvMotion.bouncy(), label = "swatchPad")
    val color = Color(option.hex ?: 0xFF888888)
    Column(modifier.evClickable(pressedScale = 0.9f, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(58.dp).border(if (selected) 2.dp else 1.dp, ring, CircleShape).padding(pad + 4.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            androidx.compose.animation.AnimatedVisibility(selected, enter = scaleIn(EvMotion.bouncy()) + fadeIn(), exit = fadeOut()) {
                Icon(Icons.Rounded.Check, null, tint = if (option.id == "white" || option.id == "beige") Color.Black else Color.White, modifier = Modifier.size(22.dp))
            }
        }
        Text(option.label, style = EvType.caption, color = if (selected) c.ink else c.inkMuted)
    }
}
