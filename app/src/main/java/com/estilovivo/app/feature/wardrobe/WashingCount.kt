package com.estilovivo.app.feature.wardrobe

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.estilovivo.app.core.data.repo.GarmentRepository
import org.koin.compose.koinInject

/** Nº de prendas en la lavadora (badge de la burbuja "Lavar"), siempre al día con la caché del armario. */
@Composable
fun rememberWashingCount(): Int {
    val repo = koinInject<GarmentRepository>()
    val garments by repo.garments.collectAsStateWithLifecycle()
    // Si nadie ha cargado el armario todavía, se carga para que el contador sea real desde el principio
    LaunchedEffect(repo) { if (repo.garments.value.isEmpty()) repo.getAll() }
    return remember(garments) { garments.count { it.isWashing } }
}
