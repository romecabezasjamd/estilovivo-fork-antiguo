package com.estilovivo.app.feature.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PersonAddAlt1
import androidx.compose.material.icons.rounded.PublicOff
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.estilovivo.app.app.ChangePassword
import com.estilovivo.app.app.EditProfile
import com.estilovivo.app.app.FollowRequests
import com.estilovivo.app.app.LocalNavigator
import com.estilovivo.app.app.Premium
import com.estilovivo.app.app.Privacy
import com.estilovivo.app.core.data.session.ThemeMode
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvSpace
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.EvButtonStyle
import com.estilovivo.app.core.design.components.EvPillButton
import com.estilovivo.app.core.design.components.EvSegmented
import com.estilovivo.app.core.design.components.EvTextField
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.staggeredEntrance
import com.estilovivo.app.feature.profile.ProfileRules.EmailKey
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
private fun SectionLabel(text: String, index: Int) =
    Text(text, style = EvType.title, color = EvTheme.colors.ink, modifier = Modifier.padding(top = EvSpace.sm).staggeredEntrance(index))

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    tint: androidx.compose.ui.graphics.Color = EvTheme.colors.ink,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = EvTheme.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = 60.dp)
            .let { if (onClick != null) it.evClickable(pressedScale = 0.98f, role = Role.Button, onClick = onClick) else it }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(c.surfaceAlt), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = EvType.label, color = tint)
            if (subtitle != null) Text(subtitle, style = EvType.bodySmall, color = c.inkSoft)
        }
        if (trailing != null) trailing()
        else if (onClick != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.inkMuted)
    }
}

@Composable
fun SettingsSection() {
    val vm: SettingsViewModel = koinViewModel()
    val s by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val c = EvTheme.colors
    val context = LocalContext.current
    var showPeriod by rememberSaveable { mutableStateOf(false) }
    var periodError by remember { mutableStateOf<String?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshRequests() }
    CollectProfileEvents(
        vm.events,
        onLoggedOut = { nav.resetToWelcome() },
        onAccountDeleted = { nav.resetToWelcome() },
    )

    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0" }

    Column(verticalArrangement = Arrangement.spacedBy(EvSpace.md)) {
        // ── Cuenta
        SectionLabel("Cuenta", 0)
        SettingsCard(Modifier.staggeredEntrance(1)) {
            SettingRow(Icons.Rounded.Edit, "Editar perfil", subtitle = "Nombre, bio, fotos, ubicación", onClick = { nav.navigate(EditProfile) })
            Divider()
            SettingRow(Icons.Rounded.Lock, "Cambiar contraseña", onClick = { nav.navigate(ChangePassword) })
        }

        // ── Privacidad
        SectionLabel("Privacidad", 2)
        SettingsCard(Modifier.staggeredEntrance(3)) {
            SettingRow(
                if (s.isPublic) Icons.Rounded.Public else Icons.Rounded.PublicOff, "Perfil público",
                subtitle = if (s.isPublic) "Cualquiera puede ver tus looks y seguirte."
                else "Sólo quien aprobes puede ver tus looks y seguirte.",
                trailing = { EvSwitch(s.isPublic, vm::setProfilePublic, saving = "public" in s.saving) },
            )
            Divider()
            SettingRow(
                Icons.Rounded.PersonAddAlt1, "Solicitudes de seguimiento",
                subtitle = when (val n = s.pendingRequests) {
                    null -> "Gestiona quién quiere seguirte"
                    0 -> "No tienes solicitudes pendientes"
                    1 -> "1 solicitud pendiente"
                    else -> "$n solicitudes pendientes"
                },
                onClick = { nav.navigate(FollowRequests) },
            )
        }

        // ── Emails
        SectionLabel("Avisos por email", 4)
        SettingsCard(Modifier.staggeredEntrance(5)) {
            val saving = "emails" in s.saving
            SettingRow(Icons.Rounded.Email, "Notificaciones por email", subtitle = "Interruptor general",
                trailing = { EvSwitch(s.emails.master, { vm.setEmail(EmailKey.Master, it) }, saving = saving) })
            Divider()
            EmailRow("Mensajes de chat", s.emails.chat, saving) { vm.setEmail(EmailKey.Chat, it) }
            EmailRow("Nuevos seguidores", s.emails.follows, saving) { vm.setEmail(EmailKey.Follows, it) }
            EmailRow("Lavadora (prendas listas)", s.emails.washing, saving) { vm.setEmail(EmailKey.Washing, it) }
            EmailRow("Retos semanales", s.emails.challenges, saving) { vm.setEmail(EmailKey.Challenges, it) }
        }

        // ── Apariencia y accesibilidad
        SectionLabel("Apariencia y accesibilidad", 6)
        SettingsCard(Modifier.staggeredEntrance(7)) {
            Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(Icons.Rounded.Palette, null, tint = c.ink)
                    Text("Modo", style = EvType.label, color = c.ink)
                }
                EvSegmented(
                    ThemeMode.entries, s.local.themeMode, vm::setThemeMode,
                    label = { when (it) { ThemeMode.System -> "Sistema"; ThemeMode.Light -> "Claro"; ThemeMode.Dark -> "Oscuro" } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Divider()
            ThemePickerSection(s.local, vm::setBrandTheme)
            Divider()
            FontScaleRow(s.local.fontScale, vm::setFontScale)
            Divider()
            SettingRow(Icons.Rounded.Contrast, "Alto contraste", subtitle = "Textos y bordes más marcados",
                trailing = { EvSwitch(s.local.highContrast, vm::setHighContrast) })
            Divider()
            SettingRow(Icons.Rounded.Vibration, "Vibración táctil", subtitle = "Respuesta háptica al tocar",
                trailing = { EvSwitch(s.local.haptics, vm::setHaptics) })
        }

        // ── Ciclo (sólo género femenino)
        if (s.cycleAvailable) {
            SectionLabel("Ciclo", 8)
            SettingsCard(Modifier.staggeredEntrance(9)) {
                SettingRow(
                    Icons.Rounded.WaterDrop, "Seguimiento del ciclo",
                    subtitle = "Mensajes de cariño en tus días. Las fechas se quedan sólo en tu móvil.",
                    trailing = { EvSwitch(s.cycleTracking, vm::setCycleTracking, saving = "cycle" in s.saving) },
                )
                AnimatedVisibility(s.cycleTracking, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Divider()
                        val p = s.cyclePeriod
                        Text(
                            if (p == null) "Aún no has indicado tu periodo."
                            else "Periodo: ${fmt(p.startDate)} – ${fmt(p.endDate)}",
                            style = EvType.body, color = c.inkSoft,
                        )
                        periodError?.let { Text(it, style = EvType.caption, color = c.danger) }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            EvPillButton(if (p == null) "Indicar periodo" else "Cambiar", { showPeriod = true }, Modifier.weight(1f), height = 48.dp)
                            if (p != null) EvPillButton("Borrar", { vm.clearCyclePeriod() }, style = EvButtonStyle.Secondary, height = 48.dp)
                        }
                    }
                }
            }
        }

        // ── App
        SectionLabel("Estilo Vivo", 10)
        SettingsCard(Modifier.staggeredEntrance(11)) {
            SettingRow(Icons.Rounded.WorkspacePremium, "Premium", subtitle = "Plan gratuito · ver ventajas", onClick = { nav.navigate(Premium) })
            Divider()
            SettingRow(Icons.Rounded.Shield, "Política de privacidad", onClick = { nav.navigate(Privacy) })
            Divider()
            SettingRow(Icons.Rounded.TextFields, "Versión de la app", subtitle = version)
        }

        // ── Sesión
        Column(Modifier.padding(top = EvSpace.md).staggeredEntrance(12), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EvPillButton("Cerrar sesión", vm::askLogout, Modifier.fillMaxWidth(), style = EvButtonStyle.Secondary, icon = Icons.AutoMirrored.Rounded.Logout)
            EvPillButton("Eliminar mi cuenta", vm::startDelete, Modifier.fillMaxWidth(), style = EvButtonStyle.Ghost, icon = Icons.Rounded.DeleteForever)
        }
    }

    if (showPeriod) {
        val p = s.cyclePeriod
        EvRangePickerDialog(
            initial = p?.let { a -> ProfileRules.parseDate(a.startDate)?.let { st -> ProfileRules.parseDate(a.endDate)?.let { en -> st to en } } },
            onPick = { st, en -> periodError = vm.saveCyclePeriod(st, en); if (periodError == null) showPeriod = false },
            onDismiss = { showPeriod = false },
        )
    }
    if (s.showLogoutConfirm) {
        EvConfirmDialog(
            "¿Cerrar sesión?", "Podrás volver a entrar cuando quieras con tu email y contraseña.",
            "Cerrar sesión", onConfirm = vm::confirmLogout, onDismiss = vm::dismissLogout, loading = s.loggingOut,
        )
    }
    when (s.delete.stage) {
        DeleteFlow.Stage.Warning -> EvConfirmDialog(
            "¿Eliminar tu cuenta?",
            "Se borrarán para siempre tu armario, looks, mensajes, historias y progreso. No se puede deshacer.",
            "Continuar", onConfirm = vm::proceedDelete, onDismiss = vm::cancelDelete, danger = true,
        )
        DeleteFlow.Stage.Typing -> EvConfirmDialog(
            "Confirma que quieres eliminarla", "Escribe ${ProfileRules.DELETE_WORD} para confirmar.",
            "Eliminar definitivamente", onConfirm = vm::confirmDelete, onDismiss = vm::cancelDelete,
            danger = true, loading = s.delete.deleting, confirmEnabled = s.delete.canConfirm,
            extra = {
                EvTextField(
                    s.delete.typed, vm::setDeleteText, ProfileRules.DELETE_WORD, enabled = !s.delete.deleting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters),
                )
            },
        )
        DeleteFlow.Stage.None -> Unit
    }
}

private fun fmt(iso: String): String =
    ProfileRules.parseDate(iso)?.format(DateTimeFormatter.ofPattern("d MMM", Locale("es", "ES"))) ?: iso

@Composable
private fun EmailRow(title: String, checked: Boolean, saving: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 54.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = EvType.body, color = EvTheme.colors.ink, modifier = Modifier.weight(1f))
        EvSwitch(checked, onChange, saving = saving)
    }
}

@Composable
private fun FontScaleRow(current: Float, onCommit: (Float) -> Unit) {
    val c = EvTheme.colors
    var draft by remember(current) { mutableFloatStateOf(current) }
    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Rounded.TextFields, null, tint = c.ink)
            Text("Tamaño del texto", style = EvType.label, color = c.ink, modifier = Modifier.weight(1f))
            Text("${(draft * 100).toInt()}%", style = EvType.caption, color = c.inkSoft)
        }
        Slider(
            value = draft, onValueChange = { draft = it },
            onValueChangeFinished = { onCommit(draft) },
            valueRange = ProfileRules.MIN_FONT_SCALE..ProfileRules.MAX_FONT_SCALE,
            steps = 12,
            colors = SliderDefaults.colors(thumbColor = c.ink, activeTrackColor = c.ink, inactiveTrackColor = c.outline, activeTickColor = c.onInk, inactiveTickColor = c.inkMuted),
        )
        // Vista previa en vivo: el tamaño real se aplica al soltar
        Text("Así se verá tu texto.", color = c.inkSoft, fontSize = (14f * draft / current.coerceAtLeast(0.5f)).sp, style = EvType.body.copy(fontSize = (14f * draft / current.coerceAtLeast(0.5f)).sp))
    }
}
