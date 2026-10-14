package com.estilovivo.app.feature.profile

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.ImageUpload
import com.estilovivo.app.core.data.model.PreferencesUpdate
import com.estilovivo.app.core.data.model.ProfileUpdate
import com.estilovivo.app.core.data.model.User
import com.estilovivo.app.core.data.repo.AuthRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class EditProfileUiState(
    val name: String = "",
    val bio: String = "",
    /** "male" | "female" | "other" | "" */
    val gender: String = "",
    /** `yyyy-MM-dd` o vacío. */
    val birthDate: String = "",
    val location: String = "",
    /** Foto ya guardada (URL) y, si hay una nueva elegida, su vista previa (URI). */
    val avatarUrl: String? = null,
    val avatarPreview: String? = null,
    val bodyUrl: String? = null,
    val bodyPreview: String? = null,
    val nameError: String? = null,
    val bioError: String? = null,
    val birthError: String? = null,
    val locationError: String? = null,
    val saving: Boolean = false,
    val dirty: Boolean = false,
    val ready: Boolean = false,
) {
    val canSave: Boolean get() = ready && dirty && !saving
}

/**
 * Edición del perfil: texto (sobrevive a la muerte del proceso con [SavedStateHandle]), fotos con vista previa y guardado en
 * dos pasos (`/auth/profile` y `/auth/preferences` para la ubicación). Si algo falla, el formulario conserva lo escrito
 * para reintentar y el perfil real no cambia (el repositorio sólo publica el usuario nuevo cuando el servidor responde).
 */
class EditProfileViewModel(
    private val auth: AuthRepository,
    private val cycle: CycleStorage,
    private val saved: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private var avatarUpload: ImageUpload? = null
    private var bodyUpload: ImageUpload? = null

    /** Valores del servidor con los que se compara para saber qué ha cambiado. */
    private var baseline: User? = null

    private val _state = MutableStateFlow(EditProfileUiState())
    val state: StateFlow<EditProfileUiState> = _state.asStateFlow()
    private val _events = Channel<ProfileEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        val u = auth.currentUser.value
        if (u != null) adopt(u, force = true)
        viewModelScope.launch {
            val fresh = auth.refreshMe().let { (it as? ApiResult.Success)?.value } ?: auth.currentUser.value
            // Sólo se sustituye lo escrito si el usuario aún no ha tocado nada
            if (fresh != null) adopt(fresh, force = !_state.value.dirty)
        }
    }

    private fun adopt(user: User, force: Boolean) {
        baseline = user
        _state.update { s ->
            val keep = !force && s.ready
            s.copy(
                name = if (keep) s.name else saved.get<String>(K_NAME) ?: user.name,
                bio = if (keep) s.bio else saved.get<String>(K_BIO) ?: user.bio,
                gender = if (keep) s.gender else saved.get<String>(K_GENDER) ?: user.gender.orEmpty(),
                birthDate = if (keep) s.birthDate else saved.get<String>(K_BIRTH) ?: ProfileRules.parseDate(user.birthDate)?.toString().orEmpty(),
                location = if (keep) s.location else saved.get<String>(K_LOCATION) ?: user.locationName.orEmpty(),
                avatarUrl = user.avatar,
                bodyUrl = user.fullBodyAvatar,
                ready = true,
            ).recomputeDirty()
        }
    }

    fun onName(v: String) = edit(K_NAME) { it.copy(name = v.take(ProfileRules.NAME_MAX + 20), nameError = null) }
    fun onBio(v: String) = edit(K_BIO) { it.copy(bio = v.take(ProfileRules.BIO_MAX + 50), bioError = null) }
    fun onGender(v: String) = edit(K_GENDER) { it.copy(gender = v) }
    fun onBirthDate(v: String) = edit(K_BIRTH) { it.copy(birthDate = v, birthError = null) }
    fun onLocation(v: String) = edit(K_LOCATION) { it.copy(location = v.take(ProfileRules.LOCATION_MAX + 20), locationError = null) }

    private fun edit(key: String, block: (EditProfileUiState) -> EditProfileUiState) {
        _state.update { block(it).recomputeDirty() }
        val s = _state.value
        saved[key] = when (key) {
            K_NAME -> s.name
            K_BIO -> s.bio
            K_GENDER -> s.gender
            K_BIRTH -> s.birthDate
            else -> s.location
        }
    }

    fun setAvatar(upload: ImageUpload, previewUri: String) {
        avatarUpload = upload
        _state.update { it.copy(avatarPreview = previewUri).recomputeDirty() }
    }

    fun setBody(upload: ImageUpload, previewUri: String) {
        bodyUpload = upload
        _state.update { it.copy(bodyPreview = previewUri).recomputeDirty() }
    }

    fun discardPhotos() {
        avatarUpload = null; bodyUpload = null
        _state.update { it.copy(avatarPreview = null, bodyPreview = null).recomputeDirty() }
    }

    private fun EditProfileUiState.recomputeDirty(): EditProfileUiState {
        val b = baseline ?: return copy(dirty = false)
        val changed = name.trim() != b.name || bio.trim() != b.bio.trim() || gender != b.gender.orEmpty() ||
            birthDate != ProfileRules.parseDate(b.birthDate)?.toString().orEmpty() ||
            location.trim() != b.locationName.orEmpty().trim() ||
            avatarPreview != null || bodyPreview != null
        return copy(dirty = changed)
    }

    /** Valida y guarda. Ignora toques repetidos mientras se guarda. */
    fun save() {
        val s = _state.value
        if (s.saving || !s.ready) return
        val b = baseline ?: return
        val nameErr = ProfileRules.validateName(s.name)
        val bioErr = ProfileRules.validateBio(s.bio)
        val birthErr = ProfileRules.validateBirthDate(s.birthDate)
        val locErr = ProfileRules.validateLocation(s.location)
        if (nameErr != null || bioErr != null || birthErr != null || locErr != null) {
            _state.update { it.copy(nameError = nameErr, bioError = bioErr, birthError = birthErr, locationError = locErr) }
            _events.trySend(ProfileEvent.Error("Revisa los campos marcados."))
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val profileChanged = s.name.trim() != b.name || s.bio.trim() != b.bio.trim() || s.gender != b.gender.orEmpty() ||
                s.birthDate != ProfileRules.parseDate(b.birthDate)?.toString().orEmpty() || avatarUpload != null || bodyUpload != null
            var current: User = b
            if (profileChanged) {
                val update = ProfileUpdate(
                    name = s.name.trim().takeIf { it != b.name },
                    bio = s.bio.trim().takeIf { it != b.bio.trim() },
                    gender = s.gender.takeIf { it.isNotEmpty() && it != b.gender.orEmpty() },
                    birthDate = s.birthDate.takeIf { it.isNotEmpty() && it != ProfileRules.parseDate(b.birthDate)?.toString().orEmpty() },
                    avatar = avatarUpload,
                    fullBodyAvatar = bodyUpload,
                )
                when (val r = auth.updateProfile(update)) {
                    is ApiResult.Success -> {
                        current = r.value
                        avatarUpload = null; bodyUpload = null
                        baseline = current
                        _state.update { it.copy(avatarPreview = null, bodyPreview = null, avatarUrl = current.avatar, bodyUrl = current.fullBodyAvatar) }
                    }
                    is ApiResult.Failure -> {
                        _state.update { it.copy(saving = false) }
                        _events.send(ProfileEvent.Error(r.error.message))
                        return@launch
                    }
                }
            }
            // Ubicación y, si deja de ser femenino, el seguimiento del ciclo (preferencias)
            val locationChanged = s.location.trim() != current.locationName.orEmpty().trim()
            val dropCycle = current.cycleTracking && !ProfileRules.canTrackCycle(current.gender)
            if (locationChanged || dropCycle) {
                val prefs = PreferencesUpdate(
                    locationName = if (locationChanged) s.location.trim() else null,
                    cycleTracking = if (dropCycle) false else null,
                )
                when (val r = auth.updatePreferences(prefs)) {
                    is ApiResult.Success -> {
                        current = r.value
                        baseline = current
                        if (dropCycle) cycle.save(current.id, null)
                    }
                    is ApiResult.Failure -> {
                        _state.update { it.copy(saving = false).recomputeDirty() }
                        val prefix = if (profileChanged) "Tu perfil se guardó, pero la ubicación no: " else ""
                        _events.send(ProfileEvent.Error(prefix + r.error.message))
                        return@launch
                    }
                }
            }
            saved.remove<String>(K_NAME); saved.remove<String>(K_BIO); saved.remove<String>(K_GENDER)
            saved.remove<String>(K_BIRTH); saved.remove<String>(K_LOCATION)
            _state.update { it.copy(saving = false).recomputeDirty() }
            _events.send(ProfileEvent.Success("Perfil actualizado."))
            _events.send(ProfileEvent.Saved)
        }
    }

    private companion object {
        const val K_NAME = "name"
        const val K_BIO = "bio"
        const val K_GENDER = "gender"
        const val K_BIRTH = "birth"
        const val K_LOCATION = "location"
    }
}
