package com.estilovivo.app.feature.tryon

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import com.estilovivo.app.core.design.EvMotion
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.EvButtonStyle
import com.estilovivo.app.core.design.components.EvCircleButton
import com.estilovivo.app.core.design.components.EvPillButton
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.rememberEvHaptics
import kotlinx.coroutines.delay
import java.io.File

/** Guía de pose superpuesta: silueta con marcas de cabeza, hombros y pies, y el consejo de la foto. */
@Composable
internal fun PoseGuideOverlay(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "poseGuide")
    val alpha by t.animateFloat(0.55f, 0.95f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "a")
    val dash by t.animateFloat(0f, 52f, infiniteRepeatable(tween(2600, easing = androidx.compose.animation.core.LinearEasing)), label = "d")
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val bw = h * 0.46f
        translate((w - bw) / 2f, h * 0.04f) {
            drawPath(
                silhouettePath(Size(bw, h * 0.92f)), Color.White.copy(alpha = alpha),
                style = Stroke(2.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(26f, 26f), dash)),
            )
        }
        val mark = Color.White.copy(alpha = 0.55f)
        val lines = listOf(0.07f, 0.2f, 0.95f)
        lines.forEach { y ->
            drawLine(mark, Offset(w * 0.06f, h * y), Offset(w * 0.22f, h * y), 2.dp.toPx())
            drawLine(mark, Offset(w * 0.78f, h * y), Offset(w * 0.94f, h * y), 2.dp.toPx())
        }
    }
}

/**
 * Cámara a pantalla completa con guía de pose, temporizador y cambio de cámara. Devuelve el fichero JPEG.
 * Si no hay permiso, ofrece la galería como alternativa.
 */
@Composable
internal fun TryOnCameraScreen(onCaptured: (File) -> Unit, onGallery: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val haptics = rememberEvHaptics()
    BackHandler(onBack = onClose)
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked = true }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    var front by rememberSaveable { mutableStateOf(false) }
    var timer by rememberSaveable { mutableStateOf(true) }
    var countdown by remember { mutableIntStateOf(0) }
    var shooting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var captured by remember { mutableStateOf<File?>(null) }

    val controller = remember { LifecycleCameraController(context).apply { setEnabledUseCases(CameraController.IMAGE_CAPTURE) } }
    DisposableEffect(granted, owner) {
        if (granted) runCatching { controller.bindToLifecycle(owner) }.onFailure { error = "No se pudo iniciar la cámara." }
        onDispose { runCatching { controller.unbind() } }
    }
    LaunchedEffect(front, granted) {
        if (granted) controller.cameraSelector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
    }

    fun shoot() {
        if (shooting) return
        shooting = true
        val dir = File(context.cacheDir, "images").apply { mkdirs() }
        val file = File(dir, "tryon_cam_${System.currentTimeMillis()}.jpg")
        val meta = ImageCapture.Metadata().apply { isReversedHorizontal = front }
        val opts = ImageCapture.OutputFileOptions.Builder(file).setMetadata(meta).build()
        controller.takePicture(opts, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) { shooting = false; haptics.confirm(); captured = file }
            override fun onError(e: ImageCaptureException) { shooting = false; haptics.reject(); error = "No se pudo hacer la foto. Inténtalo de nuevo." }
        })
    }

    LaunchedEffect(countdown) {
        if (countdown > 0) { delay(1000); countdown -= 1; if (countdown == 0) shoot() else haptics.tick() }
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        val file = captured
        if (file != null) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = Uri.fromFile(file), contentDescription = "Foto hecha",
                        modifier = Modifier.clip(RoundedCornerShape(28.dp)),
                    )
                }
                Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EvPillButton("Repetir", { file.delete(); captured = null }, Modifier.weight(1f), style = EvButtonStyle.Secondary)
                    EvPillButton("Usar foto", { onCaptured(file) }, Modifier.weight(1f), style = EvButtonStyle.Light)
                }
            }
        } else if (!granted) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().padding(32.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Necesitamos la cámara", style = EvType.title, color = Color.White, textAlign = TextAlign.Center)
                Text(
                    if (asked) "Sin permiso no podemos hacer la foto. Puedes activarlo en Ajustes o elegir una foto de la galería."
                    else "Para que la guía de pose te ayude a hacerte una foto de cuerpo entero.",
                    style = EvType.body, color = Color.White.copy(alpha = 0.75f), textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 12.dp),
                )
                EvPillButton("Elegir de la galería", onGallery, style = EvButtonStyle.Light, icon = Icons.Rounded.PhotoLibrary)
                if (!asked) EvPillButton("Permitir cámara", { launcher.launch(Manifest.permission.CAMERA) }, Modifier.padding(top = 10.dp), style = EvButtonStyle.Secondary)
            }
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.aspectRatio(0.75f).clip(RoundedCornerShape(28.dp))) {
                        AndroidView(
                            factory = { ctx -> PreviewView(ctx).apply { this.controller = controller; scaleType = PreviewView.ScaleType.FIT_CENTER } },
                            modifier = Modifier.fillMaxSize(),
                        )
                        PoseGuideOverlay(Modifier.fillMaxSize())
                        Text(
                            "De pie, cuerpo entero, fondo liso",
                            style = EvType.label, color = Color.White, textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp)
                                .clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        AnimatedContent(
                            countdown, modifier = Modifier.align(Alignment.Center),
                            transitionSpec = { (fadeIn(tween(150)) + scaleIn(initialScale = 1.8f)) togetherWith (fadeOut(tween(150)) + scaleOut(targetScale = 0.6f)) },
                            label = "count",
                        ) { n -> if (n > 0) Text("$n", color = Color.White, fontSize = 120.sp, style = EvType.giant) }
                        error?.let {
                            Text(it, style = EvType.label, color = Color.White, textAlign = TextAlign.Center,
                                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).clip(CircleShape).background(Color(0xCCE11D48)).padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    EvCircleButton(Icons.Rounded.PhotoLibrary, onGallery, contentDescription = "Elegir de la galería", container = Color(0x33FFFFFF), tint = Color.White)
                    // Obturador
                    Box(
                        Modifier.size(78.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)).padding(6.dp)
                            .clip(CircleShape).background(Color.White)
                            .evClickable(enabled = !shooting && countdown == 0, pressedScale = 0.9f) { if (timer) countdown = 3 else shoot() },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        EvCircleButton(
                            Icons.Rounded.Timer, { timer = !timer }, contentDescription = if (timer) "Temporizador activado, 3 segundos" else "Temporizador desactivado",
                            container = if (timer) Color.White else Color(0x33FFFFFF), tint = if (timer) Color.Black else Color.White,
                        )
                        EvCircleButton(Icons.Rounded.Cameraswitch, { front = !front }, contentDescription = "Cambiar de cámara", container = Color(0x33FFFFFF), tint = Color.White)
                    }
                }
            }
        }
        EvCircleButton(
            Icons.Rounded.Close, onClose, contentDescription = "Cerrar cámara",
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp), container = Color(0x66000000), tint = Color.White,
        )
    }
}
