package com.estilovivo.app.feature.profile

import com.estilovivo.app.core.design.asPanel
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.estilovivo.app.app.LocalNavigator
import com.estilovivo.app.core.design.EvMotion
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.EvButtonStyle
import com.estilovivo.app.core.design.components.EvPillButton
import com.estilovivo.app.core.design.components.LocalEvToast
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.floatingBob
import com.estilovivo.app.core.design.staggeredEntrance

private const val CONTACT_EMAIL = "appestilovivo@gmail.com"

private data class Perk(val icon: ImageVector, val title: String, val desc: String)

private val PERKS = listOf(
    Perk(Icons.Rounded.AutoAwesome, "Probador virtual ilimitado", "Pruébate prendas sin límites diarios."),
    Perk(Icons.Rounded.Storefront, "Ventas ilimitadas", "Publica todas las prendas que quieras a la venta."),
    Perk(Icons.Rounded.Shield, "Sin anuncios", "Una experiencia limpia, sin interrupciones."),
    Perk(Icons.Rounded.SupportAgent, "Soporte prioritario", "Respuesta rápida cuando nos necesites."),
)

/**
 * Premium es informativo: el cobro de funciones digitales debe pasar por Google Play Billing, así que aquí NO hay
 * ningún flujo de pago. El botón sólo abre un correo neutro para avisar de que te interesa.
 */
@Composable
fun PremiumScreen() {
    val nav = LocalNavigator.current
    val c = EvTheme.colors
    val context = LocalContext.current
    val toast = LocalEvToast.current
    ProfileScreenFrame("Estilo Vivo\n*Premium*", onBack = { nav.back() }) {
        Box(
            Modifier.fillMaxWidth().staggeredEntrance(0).clip(EvShapes.hero).background(c.asPanel().ink).padding(24.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            val c = c.asPanel()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.WorkspacePremium, null, tint = c.onInk, modifier = Modifier.size(40.dp).floatingBob(6f))
                Text("Tu plan: gratuito", style = EvType.title, color = c.onInk)
                Text("Incluye hasta 5 prendas a la venta y probador con límite diario.", style = EvType.body, color = c.onInk.copy(alpha = 0.75f))
            }
        }
        PERKS.forEachIndexed { i, p ->
            Row(
                Modifier.fillMaxWidth().staggeredEntrance(i + 1).clip(EvShapes.cardSmall).background(c.surface).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp).clip(EvShapes.pill).background(c.surfaceAlt), contentAlignment = Alignment.Center) {
                    Icon(p.icon, null, tint = c.ink, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(p.title, style = EvType.label, color = c.ink)
                    Text(p.desc, style = EvType.bodySmall, color = c.inkSoft)
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().staggeredEntrance(6).clip(EvShapes.card).background(c.surface).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Rounded.AllInclusive, null, tint = c.ink)
            Text("Próximamente", style = EvType.title, color = c.ink)
            Text(
                "Estamos preparando Premium. Si te interesa, avísanos y te escribiremos cuando esté disponible.",
                style = EvType.body, color = c.inkSoft, textAlign = TextAlign.Center,
            )
            EvPillButton("Avísame cuando esté disponible", {
                val intent = Intent(Intent.ACTION_SENDTO, "mailto:$CONTACT_EMAIL".toUri()).apply {
                    putExtra(Intent.EXTRA_SUBJECT, "Aviso Premium - Estilo Vivo")
                }
                runCatching { context.startActivity(intent) }.onFailure { toast.error("No hay ninguna app de correo. Escríbenos a $CONTACT_EMAIL") }
            }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Mail)
        }
    }
}

private data class PolicySection(val title: String, val paragraphs: List<String>, val bullets: List<String> = emptyList())

private val POLICY = listOf(
    PolicySection(
        "1. Información que recopilamos", listOf("Recopilamos la información que nos proporcionas directamente:"),
        listOf(
            "Cuenta: nombre, correo electrónico y contraseña (almacenada con hash unidireccional).",
            "Perfil: foto, género, fecha de nacimiento, biografía y ubicación para el tiempo.",
            "Armario digital: fotos de prendas, looks y su organización.",
            "Foto de cuerpo entero: para el probador virtual; se sube a nuestros servidores para poder usarla en todos tus dispositivos.",
            "Uso de la app: interacciones, búsquedas y preferencias.",
            "Seguimiento del ciclo: las fechas se guardan cifradas sólo en tu dispositivo y nunca se envían al servidor.",
        ),
    ),
    PolicySection(
        "2. Cómo usamos tu información", emptyList(),
        listOf(
            "Para proporcionar y mejorar el servicio.",
            "Para personalizar tu experiencia de estilo.",
            "Para enviar notificaciones relevantes (puedes desactivarlas en Ajustes).",
            "Para garantizar la seguridad de la plataforma.",
            "Para cumplir con obligaciones legales.",
        ),
    ),
    PolicySection(
        "3. Almacenamiento y seguridad",
        listOf("Tus datos se almacenan en servidores seguros y se transmiten cifrados (HTTPS). Las contraseñas se guardan con hash unidireccional y las sesiones de la app, cifradas en el dispositivo."),
    ),
    PolicySection(
        "4. Compartición de datos",
        listOf("No vendemos tu información personal. Podemos compartir datos anónimos y agregados para mejorar el servicio. Tus looks públicos son visibles según tu configuración de privacidad (perfil público o privado)."),
    ),
    PolicySection(
        "5. Tus derechos", emptyList(),
        listOf(
            "Acceder a tus datos personales.",
            "Corregir datos inexactos (Editar perfil).",
            "Eliminar tu cuenta y tus datos (Ajustes > Eliminar cuenta).",
            "Solicitar una copia de tus datos escribiéndonos.",
            "Oponerte al tratamiento de tus datos.",
        ),
    ),
    PolicySection(
        "6. Retención de datos",
        listOf("Conservamos tu información mientras tu cuenta esté activa. Si eliminas tu cuenta, tus datos se eliminan de forma permanente."),
    ),
    PolicySection(
        "7. Cookies y tecnologías similares",
        listOf("Utilizamos únicamente almacenamiento esencial para el funcionamiento de la app. No utilizamos rastreadores de terceros."),
    ),
    PolicySection(
        "8. Cambios en esta política",
        listOf("Podemos actualizar esta política periódicamente. Te avisaremos de los cambios importantes por correo o dentro de la app."),
    ),
    PolicySection(
        "9. Contacto",
        listOf("Para ejercer tus derechos o resolver dudas sobre esta política, escríbenos a privacidad@estilovivo.com."),
    ),
)

@Composable
fun PrivacyScreen() {
    val nav = LocalNavigator.current
    val c = EvTheme.colors
    ProfileScreenFrame("Política de\n*privacidad*", onBack = { nav.back() }) {
        Text("Última actualización: julio 2026", style = EvType.caption, color = c.inkMuted)
        POLICY.forEachIndexed { i, s ->
            var open by rememberSaveable(i) { mutableStateOf(i == 0) }
            val rot by animateFloatAsState(if (open) 180f else 0f, EvMotion.snappy(), label = "chev")
            Column(
                Modifier.fillMaxWidth().staggeredEntrance(i).clip(EvShapes.cardSmall).background(c.surface).animateContentSize(EvMotion.smooth()),
            ) {
                Row(
                    Modifier.fillMaxWidth().evClickable(pressedScale = 0.98f, role = Role.Button) { open = !open }.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(s.title, style = EvType.label, color = c.ink, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.ExpandMore, if (open) "Contraer" else "Expandir", tint = c.inkSoft, modifier = Modifier.rotate(rot))
                }
                AnimatedVisibility(open) {
                    Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        s.paragraphs.forEach { Text(it, style = EvType.body, color = c.inkSoft) }
                        s.bullets.forEach { Text("•  $it", style = EvType.body, color = c.inkSoft) }
                    }
                }
            }
        }
        EvPillButton("Volver", { nav.back() }, Modifier.fillMaxWidth(), style = EvButtonStyle.Secondary)
    }
}
