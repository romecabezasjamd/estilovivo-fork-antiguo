package com.estilovivo.app.feature.social.shop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.estilovivo.app.core.design.EvMotion
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvSpace
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.SharedKey
import com.estilovivo.app.core.design.components.EvChip
import com.estilovivo.app.core.design.components.EvCircleButton
import com.estilovivo.app.core.design.components.EvImage
import com.estilovivo.app.core.design.components.EvLikeButton
import com.estilovivo.app.core.design.components.EvPillButton
import com.estilovivo.app.core.design.components.EvPriceTag
import com.estilovivo.app.core.design.components.EvRatingStars
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.sharedElementIfAvailable
import com.estilovivo.app.core.design.staggeredEntrance
import kotlinx.coroutines.launch

/** Modelo de UI del detalle de un artículo a la venta. */
data class ProductDetailUi(
    val id: String,
    val title: String,
    val images: List<Any>,
    val price: String,
    val brand: String? = null,
    val color: String? = null,
    val condition: String? = null,
    val size: String? = null,
    /** Tallas disponibles para mostrar (si sólo hay una, se marca esa). */
    val sizes: List<String> = emptyList(),
    val description: String? = null,
    val sellerName: String,
    val sellerAvatar: Any? = null,
    val rating: Float? = null,
    val liked: Boolean = false,
    val isOwn: Boolean = false,
)

/**
 * Detalle de producto (3ª pantalla de la referencia): hero redondeado con carrusel, puntos,
 * flechas laterales y etiqueta de precio; título, valoración, tallas en chips, detalles
 * desplegables y botón oscuro de contacto con el vendedor. La foto viaja desde la tarjeta
 * mediante elemento compartido.
 */
@Composable
fun ProductDetailScreen(
    item: ProductDetailUi,
    onBack: () -> Unit,
    onToggleLike: () -> Unit,
    onContact: () -> Unit,
    onSellerClick: () -> Unit,
    modifier: Modifier = Modifier,
    onShare: (() -> Unit)? = null,
    contacting: Boolean = false,
) {
    val c = EvTheme.colors
    val pager = rememberPagerState(pageCount = { item.images.size.coerceAtLeast(1) })
    val scope = rememberCoroutineScope()
    var detailsOpen by remember { mutableStateOf(true) }
    var selectedSize by remember { mutableStateOf(item.size ?: item.sizes.firstOrNull()) }

    Box(modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(bottom = 120.dp)) {
            // Barra superior
            Row(
                Modifier.fillMaxWidth().padding(horizontal = EvSpace.screen, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                EvCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, onBack, contentDescription = "Volver")
                Text("Detalle", style = EvType.subtitle, color = c.ink)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onShare != null) EvCircleButton(Icons.Rounded.IosShare, onShare, contentDescription = "Compartir enlace")
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        EvLikeButton(liked = item.liked, onToggle = onToggleLike, size = 44.dp, container = c.surface)
                    }
                }
            }

            // Hero con carrusel
            Box(
                Modifier.padding(horizontal = EvSpace.screen).fillMaxWidth().aspectRatio(0.86f)
                    .clip(EvShapes.hero).background(c.surfaceAlt),
            ) {
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                    EvImage(
                        model = item.images.getOrNull(page),
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize().let { if (page == 0) it.sharedElementIfAvailable(SharedKey(item.id)) else it },
                    )
                }
                if (item.images.size > 1) {
                    EvCircleButton(
                        Icons.Rounded.ChevronLeft, { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) } },
                        contentDescription = "Anterior", size = 34.dp, container = c.surface.copy(alpha = 0.55f),
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp),
                    )
                    EvCircleButton(
                        Icons.Rounded.ChevronRight, { scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(item.images.lastIndex)) } },
                        contentDescription = "Siguiente", size = 34.dp, container = c.surface.copy(alpha = 0.55f),
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp),
                    )
                    // Puntos: el activo se alarga
                    Row(
                        Modifier.align(Alignment.BottomStart).padding(start = 22.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(item.images.size) { i ->
                            val w by animateDpAsState(if (i == pager.currentPage) 20.dp else 6.dp, EvMotion.bouncy(), label = "dotW")
                            Box(Modifier.width(w).height(6.dp).clip(CircleShape).background(if (i == pager.currentPage) c.ink else c.ink.copy(alpha = 0.3f)))
                        }
                    }
                }
                EvPriceTag(item.price, Modifier.align(Alignment.BottomEnd).padding(18.dp))
            }

            // Título y valoración
            Column(
                Modifier.padding(horizontal = EvSpace.screen).padding(top = 22.dp).staggeredEntrance(1),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(item.title, style = EvType.headline.copy(fontSize = 24.sp, lineHeight = 30.sp), color = c.ink)
                if (item.rating != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EvRatingStars(item.rating)
                        Text("${"%.1f".format(item.rating)} / 5", style = EvType.caption, color = c.inkSoft)
                    }
                }
                val meta = listOfNotNull(item.brand, item.color, item.condition).joinToString("  ·  ")
                if (meta.isNotBlank()) Text(meta, style = EvType.bodySmall, color = c.inkSoft)
            }

            // Tallas
            if (item.sizes.isNotEmpty()) {
                Column(Modifier.padding(horizontal = EvSpace.screen).padding(top = 20.dp).staggeredEntrance(2), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Talla", style = EvType.caption, color = c.inkMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item.sizes.forEach { size ->
                            EvChip(text = size, selected = size == selectedSize, onClick = { selectedSize = size })
                        }
                    }
                }
            } else if (item.size != null) {
                Column(Modifier.padding(horizontal = EvSpace.screen).padding(top = 20.dp).staggeredEntrance(2), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Talla", style = EvType.caption, color = c.inkMuted)
                    EvChip(item.size, true, {})
                }
            }

            // Detalles desplegables
            Column(Modifier.padding(horizontal = EvSpace.screen).padding(top = 18.dp).animateContentSize(EvMotion.smooth()).staggeredEntrance(3)) {
                Row(
                    Modifier.fillMaxWidth().evClickable(role = null) { detailsOpen = !detailsOpen }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Ver detalles del producto", style = EvType.label, color = c.ink)
                    val rot by animateFloatAsState(if (detailsOpen) 180f else 0f, EvMotion.bouncy(), label = "chev")
                    Icon(Icons.Rounded.ExpandMore, null, tint = c.inkSoft, modifier = Modifier.rotate(rot))
                }
                AnimatedVisibility(detailsOpen, enter = expandVertically(EvMotion.smooth()) + fadeIn(), exit = shrinkVertically(EvMotion.snappy()) + fadeOut()) {
                    Text(
                        item.description?.takeIf { it.isNotBlank() } ?: "El vendedor no ha añadido descripción.",
                        style = EvType.body, color = c.inkSoft, modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }

            // Vendedor
            Row(
                Modifier.padding(horizontal = EvSpace.screen).padding(top = 14.dp).fillMaxWidth()
                    .clip(EvShapes.cardSmall).background(c.surface).evClickable(role = null, onClick = onSellerClick).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                EvImage(item.sellerAvatar, item.sellerName, Modifier.size(44.dp).clip(CircleShape))
                Column(Modifier.weight(1f)) {
                    Text("Vendido por", style = EvType.caption, color = c.inkMuted)
                    Text(item.sellerName, style = EvType.subtitle, color = c.ink)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = c.inkMuted)
            }
        }

        // Botón fijo inferior (cápsula oscura como el "Add to Cart" de la referencia)
        if (!item.isOwn) {
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                    .padding(horizontal = EvSpace.screen, vertical = 14.dp),
            ) {
                EvPillButton(
                    text = "Chatear con ${item.sellerName.substringBefore(' ')}",
                    onClick = onContact, icon = Icons.Rounded.ChatBubbleOutline, loading = contacting,
                    modifier = Modifier.fillMaxWidth(), height = 62.dp,
                )
            }
        }
    }
}
