package com.estilovivo.app.feature.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.estilovivo.app.R
import com.estilovivo.app.app.Auth
import com.estilovivo.app.app.LocalNavigator
import com.estilovivo.app.core.design.EvMotion
import com.estilovivo.app.core.design.EvSpace
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.WelcomeSlate
import com.estilovivo.app.core.design.WelcomeSlateDeep
import com.estilovivo.app.core.design.components.EvHeadline
import com.estilovivo.app.core.design.components.EvSlideToStart
import kotlinx.coroutines.delay

/** Destino `Welcome`: ver [WelcomeScreen]. Si ya hay sesión, el arranque salta directamente a `Main`. */
@Composable
fun WelcomeRoute() {
    val nav = LocalNavigator.current
    WelcomeScreen(onStart = { nav.navigate(Auth) })
}

/**
 * Bienvenida a pantalla completa: foto con zoom lento (efecto Ken Burns), degradado pizarra,
 * letras gigantes que suben una a una, titular editorial con serif cursiva y deslizador.
 */
@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    val kenBurns = rememberInfiniteTransition(label = "kenBurns")
    val zoom by kenBurns.animateFloat(
        initialValue = 1.04f, targetValue = 1.14f,
        animationSpec = infiniteRepeatable(tween(14000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "zoom",
    )
    val drift by kenBurns.animateFloat(
        initialValue = -12f, targetValue = 12f,
        animationSpec = infiniteRepeatable(tween(14000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "drift",
    )

    Box(Modifier.fillMaxSize().background(WelcomeSlate)) {
        Image(
            painter = painterResource(R.drawable.fashion_hero),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = drift },
        )
        // Velo: aclara arriba (pizarra) y oscurece abajo para el texto
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to WelcomeSlate.copy(alpha = 0.55f),
                    0.45f to Color.Transparent,
                    0.62f to WelcomeSlateDeep.copy(alpha = 0.55f),
                    1f to WelcomeSlateDeep.copy(alpha = 0.97f),
                ),
            ),
        )

        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = EvSpace.screen, vertical = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 36.dp)) {
                GiantWord("ESTILO", delayStart = 250)
                Box(Modifier.padding(top = 96.dp)) { GiantWord("VIVO", delayStart = 700, indent = true) }
            }

            Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                Reveal(delayMs = 1100) {
                    EvHeadline(
                        raw = "Eleva tu *estilo*,\nvive tu *armario*.",
                        color = Color.White,
                        accentColor = Color.White,
                    )
                }
                Reveal(delayMs = 1350) {
                    Text(
                        "Organiza tu ropa, crea looks, pruébatelos y comparte tu estilo con la comunidad.",
                        style = EvType.body, color = Color.White.copy(alpha = 0.78f),
                    )
                }
                Reveal(delayMs = 1600) { EvSlideToStart(label = "Empezar", onComplete = onStart) }
            }
        }
    }
}

/** Palabra gigante cuyas letras suben y aparecen una a una con rebote. */
@Composable
private fun GiantWord(word: String, delayStart: Int, indent: Boolean = false) {
    Row(Modifier.padding(start = if (indent) 56.dp else 0.dp)) {
        word.forEachIndexed { i, ch ->
            val p = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                delay((delayStart + i * 90).toLong())
                p.animateTo(1f, EvMotion.bouncy())
            }
            Text(
                ch.toString(),
                style = EvType.giant.copy(fontSize = 104.sp, lineHeight = 96.sp),
                color = Color.White.copy(alpha = 0.97f),
                modifier = Modifier.graphicsLayer {
                    alpha = p.value.coerceIn(0f, 1f)
                    translationY = (1f - p.value) * 140f
                    rotationZ = (1f - p.value) * 8f
                },
            )
        }
    }
}

/** Aparición con fundido + subida suave tras un retardo. */
@Composable
private fun Reveal(delayMs: Int, content: @Composable () -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        p.animateTo(1f, tween(600, easing = EvMotion.emphasized))
    }
    Box(Modifier.graphicsLayer { alpha = p.value; translationY = (1f - p.value) * 36f }) { content() }
}
