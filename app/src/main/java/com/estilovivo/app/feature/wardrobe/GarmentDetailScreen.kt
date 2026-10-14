package com.estilovivo.app.feature.wardrobe

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.estilovivo.app.app.LocalNavigator
import com.estilovivo.app.app.Suitcase
import com.estilovivo.app.app.TryOn
import com.estilovivo.app.core.data.model.Garment
import com.estilovivo.app.core.data.model.Trip
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvSpace
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.SharedKey
import com.estilovivo.app.core.design.components.EvButtonStyle
import com.estilovivo.app.core.design.components.EvCircleButton
import com.estilovivo.app.core.design.components.EvEmptyState
import com.estilovivo.app.core.design.components.EvHeadline
import com.estilovivo.app.core.design.components.EvImage
import com.estilovivo.app.core.design.components.EvLikeButton
import com.estilovivo.app.core.design.components.EvPillButton
import com.estilovivo.app.core.design.components.EvPriceTag
import com.estilovivo.app.core.design.components.EvShimmerBox
import com.estilovivo.app.core.design.components.LocalEvToast
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.sharedElementIfAvailable
import com.estilovivo.app.core.design.staggeredEntrance
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val SHARE_BASE = "https://estilovivo.xyoncloud.win"

@Composable
fun GarmentDetailScreen(id: String, modifier: Modifier = Modifier) {
    val vm: GarmentDetailViewModel = koinViewModel(parameters = { parametersOf(id) })
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val toast = LocalEvToast.current
    val host = rememberSheetHost()
    var serverError by remember { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val c = EvTheme.colors

    CollectWardrobeEvents(vm.events, toast) { e ->
        if (e is WardrobeEvent.OperationResult) {
            when {
                e.key == GarmentDetailViewModel.KEY_DELETE -> { confirmDelete = false; if (e.success) nav.back() }
                e.success -> if (e.key == GarmentDetailViewModel.KEY_EDIT || e.key == GarmentDetailViewModel.KEY_SELL) host.close()
                else -> serverError = e.message
            }
        }
    }

    // Si la prenda desaparece (la acabamos de eliminar) se sigue mostrando mientras la pantalla se cierra
    var lastGarment by remember { mutableStateOf<Garment?>(null) }
    if (state.garment != null) lastGarment = state.garment
    Box(modifier.fillMaxSize().background(c.bg)) {
        val g = state.garment ?: lastGarment
        when {
            g != null -> DetailContent(
                g = g, state = state,
                onBack = nav::back,
                onWorn = vm::markWorn,
                onWash = { vm.setWashing(!g.isWashing) },
                onTry = { nav.navigate(TryOn(g.id)) },
                onTrip = { vm.loadTrips(); host.open("trips") },
                onSell = { serverError = null; host.open("sell") },
                onCancelSale = vm::cancelSale,
                onEdit = { serverError = null; host.open("edit") },
                onDelete = { confirmDelete = true },
                onFavorite = vm::toggleFavorite,
            )
            state.loading -> DetailSkeleton(nav::back)
            else -> Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) {
                EvEmptyState(Icons.Rounded.Checkroom, "Esta prenda ya no está", "Puede que la hayas eliminado desde otro dispositivo.", actionText = "Volver", onAction = nav::back)
            }
        }
    }

    SheetHostContent(host) { key ->
        val g = state.garment
        if (g == null) {
            LaunchedEffect(Unit) { host.close() }
            return@SheetHostContent
        }
        when (key) {
            "edit" -> EditGarmentSheet(
                garment = g, submitting = GarmentDetailViewModel.KEY_EDIT in state.busy, serverError = serverError,
                onSubmit = { f, up -> serverError = null; vm.edit(f, up) }, onClose = host::close, onClearServerError = { serverError = null },
            )
            "sell" -> SaleSheet(
                preselected = g, eligible = emptyList(), initial = GarmentForm(), initialError = null,
                submitting = GarmentDetailViewModel.KEY_SELL in state.busy, serverError = serverError,
                onSubmitNew = { _, _ -> }, onSubmitExisting = { _, f -> serverError = null; vm.sell(f) },
                onClose = host::close, onClearServerError = { serverError = null },
            )
            "trips" -> TripPickerSheet(
                garment = g, trips = state.trips, loading = state.tripsLoading, error = state.tripsError, busy = state.busy,
                onRetry = vm::loadTrips, onPick = vm::addToTrip, onCreate = { host.close(); nav.navigate(Suitcase) },
            )
            else -> LaunchedEffect(key) { host.close() }
        }
    }

    val g = state.garment
    WardrobeConfirmDialog(
        open = confirmDelete,
        title = "Eliminar prenda",
        message = "¿Seguro que quieres eliminar${g?.let { " “${it.name}”" } ?: " esta prenda"}? Esta acción no se puede deshacer.",
        confirmLabel = "Eliminar",
        icon = Icons.Rounded.DeleteOutline,
        loading = GarmentDetailViewModel.KEY_DELETE in state.busy,
        onConfirm = vm::delete,
        onDismiss = { confirmDelete = false },
    )
}

@Composable
private fun DetailSkeleton(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = EvSpace.screen)) {
        Row(Modifier.padding(vertical = 12.dp)) { EvCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, onBack, contentDescription = "Volver") }
        EvShimmerBox(Modifier.fillMaxWidth().aspectRatio(0.86f), EvShapes.hero)
        EvShimmerBox(Modifier.padding(top = 22.dp).fillMaxWidth(0.6f).size(height = 30.dp, width = 200.dp), EvShapes.field)
    }
}

@Composable
private fun DetailContent(
    g: Garment,
    state: DetailUiState,
    onBack: () -> Unit,
    onWorn: () -> Unit,
    onWash: () -> Unit,
    onTry: () -> Unit,
    onTrip: () -> Unit,
    onSell: () -> Unit,
    onCancelSale: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onFavorite: () -> Unit,
) {
    val c = EvTheme.colors
    val context = LocalContext.current
    val busy = state.busy
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(bottom = 124.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = EvSpace.screen, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                EvCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, onBack, contentDescription = "Volver")
                Text("Tu prenda", style = EvType.subtitle, color = c.ink)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (g.forSale) {
                        EvCircleButton(
                            Icons.Rounded.Share,
                            onClick = {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "Mira este ${g.name} en Estilovivo $SHARE_BASE/?product=${g.id}")
                                }
                                context.startActivity(Intent.createChooser(send, "Compartir prenda"))
                            },
                            contentDescription = "Compartir",
                        )
                    }
                    EvLikeButton(liked = state.favorite, onToggle = onFavorite, size = 44.dp, container = c.surface)
                }
            }

            // Hero
            Box(
                Modifier.padding(horizontal = EvSpace.screen).fillMaxWidth().aspectRatio(0.86f)
                    .clip(EvShapes.hero).background(c.surfaceAlt),
            ) {
                EvImage(
                    g.imageUrl, g.name, Modifier.fillMaxSize().sharedElementIfAvailable(SharedKey(g.id)).padding(12.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
                if (g.forSale) EvPriceTag(formatPrice(g.price), Modifier.align(Alignment.BottomEnd).padding(18.dp))
                if (g.isWashing) {
                    Row(
                        Modifier.align(Alignment.TopStart).padding(16.dp).clip(CircleShape).background(c.surface.copy(alpha = 0.92f)).padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Rounded.LocalLaundryService, null, tint = androidx.compose.ui.graphics.Color(0xFF3B82F6), modifier = Modifier.size(16.dp))
                        Text("En la lavadora", style = EvType.caption, color = c.ink)
                    }
                }
            }

            // Título
            Column(Modifier.padding(horizontal = EvSpace.screen).padding(top = 22.dp).staggeredEntrance(1), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(g.name, style = EvType.headline.copy(fontSize = 26.sp, lineHeight = 32.sp), color = c.ink)
                Text(
                    listOfNotNull(WardrobeCatalog.categoryLabel(WardrobeCatalog.categoryOf(g.type)), g.brand).joinToString("  ·  "),
                    style = EvType.bodySmall, color = c.inkSoft,
                )
            }

            // Estadísticas
            Row(
                Modifier.padding(horizontal = EvSpace.screen).padding(top = 18.dp).fillMaxWidth().staggeredEntrance(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatBox(Modifier.weight(1f), label = if (g.usageCount == 1) "uso" else "usos") {
                    AnimatedContent(
                        targetState = g.usageCount,
                        transitionSpec = {
                            (slideInVertically(tween(300)) { it } + fadeIn(tween(200))) togetherWith
                                (slideOutVertically(tween(300)) { -it } + fadeOut(tween(150))) using SizeTransform(clip = false)
                        },
                        label = "usage",
                    ) { n -> Text("$n", style = EvType.bigNumber, color = c.ink) }
                }
                StatBox(Modifier.weight(2f), label = "última vez") {
                    Text(
                        WardrobeLogic.lastWornLabel(g.lastWorn) ?: "Aún sin estrenar",
                        style = EvType.title, color = c.ink, modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
            }

            // Datos
            val facts = buildList {
                add("Color" to g.color.replaceFirstChar { it.uppercase() })
                add("Temporada" to WardrobeCatalog.seasonLabel(g.season))
                g.brand?.let { add("Marca" to it) }
                g.size?.let { add("Talla" to it) }
                if (g.forSale) add("Estado" to WardrobeCatalog.conditionLabel(g.condition))
                formatCreated(g.createdAt)?.let { add("Añadida" to it) }
            }
            Column(
                Modifier.padding(horizontal = EvSpace.screen).padding(top = 14.dp).fillMaxWidth().clip(EvShapes.card).background(c.surface).padding(18.dp).staggeredEntrance(3),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                facts.forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(k, style = EvType.body, color = c.inkMuted)
                        Text(v, style = EvType.label, color = c.ink)
                    }
                }
                g.description?.let {
                    Text(it, style = EvType.body, color = c.inkSoft, modifier = Modifier.padding(top = 4.dp))
                }
            }

            // Acciones
            Column(
                Modifier.padding(horizontal = EvSpace.screen).padding(top = 18.dp).staggeredEntrance(4),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val tiles = listOf(
                    Tile(Icons.Rounded.LocalLaundryService, if (g.isWashing) "Sacar de la lavadora" else "Lavar", onWash, enabled = GarmentDetailViewModel.KEY_WASH !in busy && (g.isWashing || !g.forSale)),
                    Tile(Icons.Rounded.AutoAwesome, "Probar", onTry),
                    Tile(Icons.Rounded.Flight, "Añadir a viaje", onTrip),
                    if (g.forSale) Tile(Icons.Rounded.ShoppingBag, "Retirar de la venta", onCancelSale, enabled = GarmentDetailViewModel.KEY_SELL !in busy)
                    else Tile(Icons.Rounded.Sell, "Vender", onSell),
                    Tile(Icons.Rounded.Edit, "Editar", onEdit),
                    Tile(Icons.Rounded.DeleteOutline, "Eliminar", onDelete, danger = true),
                )
                tiles.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { t -> ActionTile(t, Modifier.weight(1f)) }
                        if (row.size == 1) Box(Modifier.weight(1f))
                    }
                }
                if (!g.isWashing && g.forSale) {
                    Text("Retírala de la venta para poder meterla en la lavadora.", style = EvType.caption, color = c.inkMuted, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }

        // Botón fijo
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = EvSpace.screen, vertical = 14.dp)) {
            EvPillButton(
                text = "Me lo he puesto", onClick = onWorn, icon = Icons.Rounded.CheckCircle,
                loading = GarmentDetailViewModel.KEY_WORN in busy, modifier = Modifier.fillMaxWidth(), height = 62.dp,
            )
        }
    }
}

@Composable
private fun StatBox(modifier: Modifier, label: String, content: @Composable () -> Unit) {
    val c = EvTheme.colors
    Column(modifier.clip(EvShapes.cardSmall).background(c.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        content()
        Text(label, style = EvType.caption, color = c.inkSoft)
    }
}

private data class Tile(val icon: ImageVector, val label: String, val onClick: () -> Unit, val enabled: Boolean = true, val danger: Boolean = false)

@Composable
private fun ActionTile(t: Tile, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    val tint = if (t.danger) c.danger else c.ink
    Row(
        modifier.heightIn(min = 56.dp).clip(EvShapes.cardSmall).background(c.surface)
            .evClickable(enabled = t.enabled, pressedScale = 0.95f, role = Role.Button, onClick = t.onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(t.icon, null, tint = tint.copy(alpha = if (t.enabled) 1f else 0.35f), modifier = Modifier.size(22.dp))
        Text(t.label, style = EvType.label, color = tint.copy(alpha = if (t.enabled) 1f else 0.35f), maxLines = 2)
    }
}

// ── Selector de viaje ────────────────────────────────────────────────────────────────────────

@Composable
private fun TripPickerSheet(
    garment: Garment,
    trips: List<Trip>,
    loading: Boolean,
    error: String?,
    busy: Set<String>,
    onRetry: () -> Unit,
    onPick: (Trip) -> Unit,
    onCreate: () -> Unit,
) {
    val c = EvTheme.colors
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        EvHeadline("Añadir a un *viaje*", Modifier.padding(horizontal = EvSheetPadding, vertical = 6.dp))
        Text("“${garment.name}” se sumará a la maleta.", style = EvType.bodySmall, color = c.inkSoft, modifier = Modifier.padding(horizontal = EvSheetPadding))
        Column(Modifier.padding(horizontal = EvSheetPadding, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                loading && trips.isEmpty() -> repeat(3) { EvShimmerBox(Modifier.fillMaxWidth().size(height = 68.dp, width = 100.dp), EvShapes.cardSmall) }
                error != null && trips.isEmpty() -> EvEmptyState(Icons.Rounded.Flight, "No pudimos cargar tus viajes", error, actionText = "Reintentar", onAction = onRetry)
                trips.isEmpty() -> EvEmptyState(Icons.Rounded.Flight, "No tienes viajes planificados", "Crea uno y llévate tus prendas favoritas.", actionText = "Crear un viaje", onAction = onCreate)
                else -> trips.forEach { trip ->
                    val included = trip.garments.any { it.id == garment.id }
                    val working = "trip:${trip.id}" in busy
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(EvShapes.cardSmall).background(c.surface)
                            .evClickable(enabled = !included && !working, pressedScale = 0.97f, onClick = { onPick(trip) })
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(trip.destination, style = EvType.subtitle, color = c.ink, maxLines = 1)
                            Text(formatTripRange(trip.dateStart, trip.dateEnd), style = EvType.caption, color = c.inkMuted)
                        }
                        when {
                            working -> com.estilovivo.app.core.design.components.EvLoadingDots()
                            included -> Icon(Icons.Rounded.Check, "Ya incluida", tint = c.success)
                            else -> Icon(Icons.Rounded.Add, "Añadir a ${trip.destination}", tint = c.ink)
                        }
                    }
                }
            }
        }
    }
}

private val ES = Locale.forLanguageTag("es-ES")
private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", ES)
private val DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy", ES)

private fun parseDay(raw: String): LocalDate? = runCatching { LocalDate.parse(raw.take(10)) }.getOrNull()

/** "12 oct – 18 oct" (o el texto original si no es una fecha ISO). */
fun formatTripRange(start: String, end: String): String {
    val s = parseDay(start)
    val e = parseDay(end)
    if (s == null || e == null) return listOf(start, end).filter { it.isNotBlank() }.joinToString(" – ")
    return if (s == e) DAY_MONTH.format(s) else "${DAY_MONTH.format(s)} – ${DAY_MONTH.format(e)}"
}

private fun formatCreated(iso: String?): String? = iso?.let(::parseDay)?.let { DAY_MONTH_YEAR.format(it) }
