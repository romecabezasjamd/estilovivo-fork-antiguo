package com.estilovivo.app.feature.profile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.PersonAddAlt1
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.estilovivo.app.app.LocalNavigator
import com.estilovivo.app.core.data.ImageUpload
import com.estilovivo.app.core.design.EvMotion
import com.estilovivo.app.core.design.EvShapes
import com.estilovivo.app.core.design.EvSpace
import com.estilovivo.app.core.design.EvTheme
import com.estilovivo.app.core.design.EvType
import com.estilovivo.app.core.design.components.EvButtonStyle
import com.estilovivo.app.core.design.components.EvCircleButton
import com.estilovivo.app.core.design.components.EvEmptyState
import com.estilovivo.app.core.design.components.EvHeadline
import com.estilovivo.app.core.design.components.EvImage
import com.estilovivo.app.core.design.components.EvPillButton
import com.estilovivo.app.core.design.components.EvSegmented
import com.estilovivo.app.core.design.components.EvShimmerBox
import com.estilovivo.app.core.design.components.EvTextField
import com.estilovivo.app.core.design.components.LocalEvToast
import com.estilovivo.app.core.design.evClickable
import com.estilovivo.app.core.design.staggeredEntrance
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class PhotoTarget { Avatar, Body }

// ─────────────────────────────── Editar perfil ───────────────────────────────

@Composable
fun EditProfileScreen() {
    val vm: EditProfileViewModel = koinViewModel()
    val s by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val c = EvTheme.colors
    val context = LocalContext.current
    val toast = LocalEvToast.current
    val scope = rememberCoroutineScope()

    var target by rememberSaveable { mutableStateOf<PhotoTarget?>(null) }
    var sourceFor by rememberSaveable { mutableStateOf<PhotoTarget?>(null) }
    var showDate by rememberSaveable { mutableStateOf(false) }
    var showDiscard by rememberSaveable { mutableStateOf(false) }

    CollectProfileEvents(vm.events, onSaved = { nav.back() })

    val picker = rememberProfilePhotoPicker(
        onPicked = { uri ->
            val t = target ?: PhotoTarget.Avatar
            scope.launch {
                val up = ImageUpload.fromUri(context, uri)
                if (up == null) toast.error("No se pudo leer la foto. Prueba con otra.")
                else if (t == PhotoTarget.Avatar) vm.setAvatar(up, uri.toString()) else vm.setBody(up, uri.toString())
            }
        },
        onProblem = { toast.error(it) },
    )

    val leave = { if (s.dirty && !s.saving) showDiscard = true else nav.back() }
    BackHandler(enabled = s.dirty || s.saving) { if (!s.saving) showDiscard = true }

    ProfileScreenFrame("Editar\n*perfil*", onBack = leave) {
        if (!s.ready) {
            EvShimmerBox(Modifier.fillMaxWidth().height(160.dp))
            EvShimmerBox(Modifier.fillMaxWidth().height(60.dp))
            EvShimmerBox(Modifier.fillMaxWidth().height(60.dp))
            return@ProfileScreenFrame
        }
        // Fotos
        Row(Modifier.fillMaxWidth().staggeredEntrance(0), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            PhotoSlot(
                label = "Foto de perfil", model = s.avatarPreview ?: s.avatarUrl, circle = true, changed = s.avatarPreview != null,
                modifier = Modifier.weight(1f),
            ) { sourceFor = PhotoTarget.Avatar }
            PhotoSlot(
                label = "Cuerpo entero", model = s.bodyPreview ?: s.bodyUrl, circle = false, changed = s.bodyPreview != null,
                modifier = Modifier.weight(1f),
            ) { sourceFor = PhotoTarget.Body }
        }
        Text("Tu foto de cuerpo entero se usa en el probador virtual.", style = EvType.bodySmall, color = c.inkSoft)

        EvTextField(
            s.name, vm::onName, "Nombre", Modifier.staggeredEntrance(1), leadingIcon = Icons.Rounded.Person,
            error = s.nameError, enabled = !s.saving,
        )
        Column(Modifier.staggeredEntrance(2)) {
            EvTextField(s.bio, vm::onBio, "Bio", singleLine = false, minLines = 3, error = s.bioError, enabled = !s.saving)
            Text(
                "${s.bio.length}/${ProfileRules.BIO_MAX}", style = EvType.caption,
                color = if (s.bio.length > ProfileRules.BIO_MAX) c.danger else c.inkMuted,
                modifier = Modifier.align(Alignment.End).padding(top = 4.dp, end = 8.dp),
            )
        }

        Column(Modifier.staggeredEntrance(3), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Género", style = EvType.label, color = c.ink)
            val genders = listOf("female", "male", "other")
            EvSegmented(
                options = genders,
                selected = s.gender.takeIf { it in genders } ?: "",
                onSelect = vm::onGender,
                label = { when (it) { "female" -> "Mujer"; "male" -> "Hombre"; else -> "Otro" } },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Column(Modifier.staggeredEntrance(4)) {
            val date = ProfileRules.parseDate(s.birthDate)
            Row(
                Modifier.fillMaxWidth().height(60.dp).clip(EvShapes.field).background(c.surface)
                    .border(1.dp, if (s.birthError != null) c.danger else c.outline, EvShapes.field)
                    .evClickable(pressedScale = 0.98f, enabled = !s.saving) { showDate = true }.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Rounded.CalendarMonth, null, tint = c.inkMuted, modifier = Modifier.size(20.dp))
                Column {
                    if (date != null) Text("Fecha de nacimiento", style = EvType.caption, color = c.inkMuted)
                    Text(
                        date?.format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale("es", "ES"))) ?: "Fecha de nacimiento",
                        style = EvType.body, color = if (date != null) c.ink else c.inkMuted,
                    )
                }
            }
            s.birthError?.let { Text(it, style = EvType.caption, color = c.danger, modifier = Modifier.padding(start = 16.dp, top = 6.dp)) }
        }

        EvTextField(
            s.location, vm::onLocation, "Ciudad (para el tiempo)", Modifier.staggeredEntrance(5),
            leadingIcon = Icons.Rounded.LocationOn, error = s.locationError, enabled = !s.saving,
        )

        EvPillButton("Guardar cambios", vm::save, Modifier.fillMaxWidth().staggeredEntrance(6), loading = s.saving, enabled = s.canSave)
    }

    if (showDate) {
        EvDatePickerDialog(
            initial = ProfileRules.parseDate(s.birthDate),
            onPick = { vm.onBirthDate(it.toString()); showDate = false },
            onDismiss = { showDate = false },
        )
    }
    sourceFor?.let { which ->
        EvConfirmDialog(
            title = "Elegir foto", message = if (which == PhotoTarget.Avatar) "Esta será tu foto de perfil." else "Una foto de pie, de cuerpo entero, funciona mejor.",
            confirmText = "Hacer una foto", dismissText = "Cancelar",
            onConfirm = { target = which; sourceFor = null; picker.camera() },
            onDismiss = { sourceFor = null },
            extra = {
                EvPillButton("Elegir de la galería", { target = which; sourceFor = null; picker.gallery() }, Modifier.fillMaxWidth(),
                    style = EvButtonStyle.Secondary, icon = Icons.Rounded.PhotoLibrary, height = 52.dp)
            },
        )
    }
    if (showDiscard) {
        EvConfirmDialog(
            title = "¿Descartar cambios?", message = "Tienes cambios sin guardar. Si sales ahora se perderán.",
            confirmText = "Descartar", danger = true, dismissText = "Seguir editando",
            onConfirm = { showDiscard = false; vm.discardPhotos(); nav.back() },
            onDismiss = { showDiscard = false },
        )
    }
}

@Composable
private fun PhotoSlot(label: String, model: String?, circle: Boolean, changed: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = EvTheme.colors
    val ring by animateFloatAsState(if (changed) 1f else 0f, EvMotion.bouncy(), label = "ring")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.size(width = if (circle) 120.dp else 100.dp, height = if (circle) 120.dp else 150.dp)
                .clip(if (circle) CircleShape else EvShapes.cardSmall).background(c.surface)
                .border((2 * ring).dp, c.ink, if (circle) CircleShape else EvShapes.cardSmall)
                .evClickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (model != null) EvImage(model, "$label actual", Modifier.matchParentSize())
            else Icon(Icons.Rounded.Person, null, tint = c.inkMuted, modifier = Modifier.size(40.dp))
            Box(
                Modifier.align(Alignment.BottomEnd).padding(6.dp).size(32.dp).clip(CircleShape).background(c.ink),
                contentAlignment = Alignment.Center,
            ) { Icon(if (changed) Icons.Rounded.Check else Icons.Rounded.PhotoCamera, "Cambiar $label", tint = c.onInk, modifier = Modifier.size(16.dp)) }
        }
        Text(label, style = EvType.caption, color = c.inkSoft)
    }
}

// ─────────────────────────────── Cambiar contraseña ───────────────────────────────

@Composable
fun ChangePasswordScreen() {
    val vm: ChangePasswordViewModel = koinViewModel()
    val s by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val c = EvTheme.colors
    var visible by rememberSaveable { mutableStateOf(false) }
    CollectProfileEvents(vm.events, onSaved = { nav.back() })
    val eye: @Composable () -> Unit = {
        Icon(
            if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
            if (visible) "Ocultar contraseñas" else "Mostrar contraseñas", tint = c.inkMuted,
            modifier = Modifier.size(22.dp).evClickable(pressedScale = 0.8f) { visible = !visible },
        )
    }
    val vt = if (visible) VisualTransformation.None else PasswordVisualTransformation()
    val kb = KeyboardOptions(keyboardType = KeyboardType.Password)
    ProfileScreenFrame("Cambiar\n*contraseña*", onBack = { nav.back() }) {
        Text("Por seguridad cerraremos tus otras sesiones abiertas.", style = EvType.body, color = c.inkSoft)
        EvTextField(s.current, vm::onCurrent, "Contraseña actual", Modifier.staggeredEntrance(0), leadingIcon = Icons.Rounded.Lock,
            trailing = eye, error = s.errors.current, visualTransformation = vt, keyboardOptions = kb, enabled = !s.saving)
        EvTextField(s.new, vm::onNew, "Nueva contraseña (mín. ${ProfileRules.PASSWORD_MIN})", Modifier.staggeredEntrance(1), leadingIcon = Icons.Rounded.Lock,
            error = s.errors.new, visualTransformation = vt, keyboardOptions = kb, enabled = !s.saving)
        EvTextField(s.confirm, vm::onConfirm, "Repite la nueva contraseña", Modifier.staggeredEntrance(2), leadingIcon = Icons.Rounded.Lock,
            error = s.errors.confirm, visualTransformation = vt, keyboardOptions = kb, enabled = !s.saving)
        EvPillButton("Actualizar contraseña", vm::submit, Modifier.fillMaxWidth().staggeredEntrance(3), loading = s.saving, enabled = s.canSubmit)
    }
}

// ─────────────────────────────── Solicitudes de seguimiento ───────────────────────────────

@Composable
fun FollowRequestsScreen() {
    val vm: FollowRequestsViewModel = koinViewModel()
    val s by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val c = EvTheme.colors
    CollectProfileEvents(vm.events)
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = EvSpace.screen, vertical = EvSpace.md), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EvSpace.md)) {
            EvCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, { nav.back() }, contentDescription = "Volver")
            EvHeadline("Solicitudes de\n*seguimiento*")
        }
        when {
            s.loading && s.requests.isEmpty() -> Column(Modifier.padding(EvSpace.screen), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(3) { EvShimmerBox(Modifier.fillMaxWidth().height(76.dp), EvShapes.cardSmall) }
            }
            s.error != null && s.requests.isEmpty() -> EvEmptyState(Icons.Rounded.Close, "No se pudieron cargar", s.error!!, actionText = "Reintentar", onAction = vm::load)
            s.requests.isEmpty() -> EvEmptyState(
                Icons.Rounded.PersonAddAlt1, "Sin solicitudes pendientes",
                if (s.isPublic) "Tu perfil es público: cualquiera puede seguirte sin pedir permiso." else "Cuando alguien quiera seguirte, aparecerá aquí.",
            )
            else -> LazyColumn(
                Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = EvSpace.screen, end = EvSpace.screen, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(s.requests, key = { it.id }) { r ->
                    Row(
                        Modifier.fillMaxWidth().animateItem().clip(EvShapes.cardSmall).background(c.surface).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        EvImage(r.requester.avatar, "Avatar de ${r.requester.name}", Modifier.size(48.dp).clip(CircleShape))
                        Column(Modifier.weight(1f)) {
                            Text(r.requester.name, style = EvType.label, color = c.ink, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text("Quiere seguirte", style = EvType.caption, color = c.inkSoft)
                        }
                        EvCircleButton(Icons.Rounded.Close, { vm.reject(r.id) }, contentDescription = "Rechazar a ${r.requester.name}", size = 44.dp, container = c.surfaceAlt)
                        EvCircleButton(Icons.Rounded.Check, { vm.approve(r.id) }, contentDescription = "Aceptar a ${r.requester.name}", size = 44.dp, container = c.ink, tint = c.onInk)
                    }
                }
            }
        }
    }
}
