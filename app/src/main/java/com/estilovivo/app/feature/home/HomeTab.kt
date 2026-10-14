package com.estilovivo.app.feature.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.estilovivo.app.app.CreateLook
import com.estilovivo.app.app.LocalNavigator
import com.estilovivo.app.app.MainTab
import com.estilovivo.app.app.Planner
import com.estilovivo.app.app.Suitcase
import com.estilovivo.app.app.TryOn
import com.estilovivo.app.app.Wishlist
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.EvButtonStyle
import com.estilovivo.app.core.design.components.EvConfetti
import com.estilovivo.app.core.design.components.EvPillButton
import com.estilovivo.app.core.design.components.LocalEvToast
import org.koin.compose.viewmodel.koinViewModel

/** Pestaña Inicio: conecta el [HomeViewModel] con la UI, la navegación, el permiso de ubicación y los eventos. */
@Composable
fun HomeTab(modifier: Modifier = Modifier) {
    val vm = koinViewModel<HomeViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val toast = LocalEvToast.current
    val context = LocalContext.current
    var confetti by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.onLocationPermission(granted, userAction = true)
    }

    LifecycleResumeEffect(vm) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        vm.onLocationPermission(granted, userAction = false)
        vm.onResume()
        onPauseOrDispose { }
    }

    LaunchedEffect(vm) {
        vm.eventFlow.collect { event ->
            when (event) {
                is HomeEvent.Message -> if (event.error) toast.error(event.text) else if (event.text.isNotBlank()) toast.success(event.text)
                HomeEvent.Celebrate -> confetti++
            }
        }
    }

    val actions = remember(vm, nav) {
        HomeActions(
            onRefresh = vm::refresh,
            onMood = vm::selectMood,
            onRetryLevel = vm::retryProgress,
            onRetryWeek = vm::retryPlanner,
            onRetryWeather = vm::retryWeather,
            onRetryGarments = vm::retryGarments,
            onAskLocation = { permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
            onNextProposal = vm::nextProposal,
            onSaveLook = vm::saveLook,
            onPlanToday = { vm.planToday() },
            onOpenPlanner = { date -> nav.navigate(Planner(date)) },
            onCreateLook = { nav.navigate(CreateLook()) },
            onTryOn = { nav.navigate(TryOn()) },
            onOpenWardrobe = { nav.goToTab(MainTab.Wardrobe) },
            onOpenWishlist = { nav.navigate(Wishlist) },
            onOpenSuitcase = { nav.navigate(Suitcase) },
            onOpenCommunity = { nav.goToTab(MainTab.Social) },
        )
    }

    Box(modifier.fillMaxSize()) {
        HomeScreen(state = state, actions = actions, modifier = Modifier.statusBarsPadding())
        EvConfetti(trigger = confetti, modifier = Modifier.fillMaxSize())
    }

    state.confirmReplace?.let { current ->
        val c = EvTheme.colors
        AlertDialog(
            onDismissRequest = vm::dismissReplace,
            containerColor = c.surface,
            shape = EvShapes.card,
            title = { Text("¿Sustituir el look de hoy?", style = EvType.title, color = c.ink) },
            text = { Text("Hoy ya tienes planeado «$current». Lo cambiaremos por esta propuesta.", style = EvType.body, color = c.inkSoft) },
            confirmButton = { EvPillButton("Sustituir", onClick = { vm.planToday(confirmed = true) }, height = 46.dp) },
            dismissButton = { EvPillButton("Mantener", onClick = vm::dismissReplace, style = EvButtonStyle.Ghost, height = 46.dp) },
        )
    }
}
