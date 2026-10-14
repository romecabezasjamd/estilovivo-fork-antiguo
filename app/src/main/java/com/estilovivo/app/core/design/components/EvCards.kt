package com.estilovivo.app.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarHalf
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.SharedKey
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.sharedElementIfAvailable
import com.estilovivo.app.core.design.staggeredEntrance

/** Modelo de UI de una tarjeta de producto (prenda a la venta, look, etc.). */
@Immutable
data class ProductCardUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val price: String? = null,
    /** URL (String), recurso (Int) o null. */
    val image: Any? = null,
    val rating: Float? = null,
    val liked: Boolean = false,
    /** Relación de aspecto de la foto (ancho/alto). */
    val aspect: Float = 0.82f,
)

/**
 * Tarjeta de la referencia: foto con esquinas grandes, corazón blanco arriba a la derecha,
 * título, valoración y subtítulo. Si hay `LocalSharedTransitionScope` la foto vuela al detalle.
 */
@Composable
fun EvProductCard(
    item: ProductCardUi,
    onClick: () -> Unit,
    onLike: (() -> Unit)?,
    modifier: Modifier = Modifier,
    index: Int = 0,
) {
    val c = EvTheme.colors
    Column(
        modifier
            .staggeredEntrance(index)
            .clip(EvShapes.card)
            .background(c.surface)
            .evClickable(pressedScale = 0.96f, role = null, onClick = onClick)
            .padding(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(item.aspect)) {
            EvImage(
                model = item.image,
                contentDescription = item.title,
                modifier = Modifier
                    .matchParentSize()
                    .sharedElementIfAvailable(SharedKey(item.id))
                    .clip(EvShapes.cardSmall),
            )
            if (onLike != null) {
                EvLikeButton(
                    liked = item.liked, onToggle = onLike,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                )
            }
            if (item.price != null) {
                Text(
                    item.price,
                    style = EvType.caption.copy(fontSize = 12.sp),
                    color = c.onInk,
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                        .clip(CircleShape).background(c.ink).padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(item.title, style = EvType.label, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (item.rating != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Star, null, tint = c.warning, modifier = Modifier.size(13.dp))
                    Text(" ${"%.1f".format(item.rating)}", style = EvType.caption, color = c.inkSoft)
                }
            }
        }
        Text(
            item.subtitle, style = EvType.bodySmall, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp, top = 2.dp),
        )
    }
}

/** Estrellas con media estrella. */
@Composable
fun EvRatingStars(rating: Float, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    val c = EvTheme.colors
    Row(modifier) {
        for (i in 1..5) {
            val icon = when {
                rating >= i -> Icons.Rounded.Star
                rating >= i - 0.5f -> Icons.Rounded.StarHalf
                else -> Icons.Rounded.StarOutline
            }
            Icon(icon, null, tint = if (rating >= i - 0.5f) c.warning else c.inkMuted, modifier = Modifier.size(size))
        }
    }
}

/** Píldora oscura con el precio (esquina del hero del detalle). */
@Composable
fun EvPriceTag(text: String, modifier: Modifier = Modifier) {
    val c = EvTheme.colors
    Text(
        text, style = EvType.price, color = c.onInk,
        modifier = modifier.clip(CircleShape).background(c.ink).padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

/** Tarjeta de estadística (número grande + etiqueta). */
@Composable
fun EvStatTile(value: String, label: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val c = EvTheme.colors
    Column(
        modifier
            .clip(EvShapes.cardSmall)
            .background(c.surface)
            .let { if (onClick != null) it.evClickable(role = null, onClick = onClick) else it }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, style = EvType.bigNumber, color = c.ink)
        Text(label, style = EvType.caption, color = c.inkSoft)
    }
}

/** Superficie genérica (tarjeta blanca redondeada). */
@Composable
fun EvCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 16.dp,
    shape: androidx.compose.ui.graphics.Shape = EvShapes.card,
    content: @Composable () -> Unit,
) {
    val c = EvTheme.colors
    Box(
        modifier
            .clip(shape)
            .background(c.surface)
            .let { if (onClick != null) it.evClickable(pressedScale = 0.97f, role = null, onClick = onClick) else it }
            .padding(padding),
    ) { content() }
}
