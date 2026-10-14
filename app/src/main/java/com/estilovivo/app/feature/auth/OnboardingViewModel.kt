package com.estilovivo.app.feature.auth

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.model.PreferencesUpdate
import com.estilovivo.app.core.data.repo.AuthRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Opción del quiz de estilo (id que guarda el servidor + etiqueta). */
@Immutable
data class QuizOption(val id: String, val label: String, val hex: Long? = null)

enum class QuizKind(val title: String, val subtitle: String, val max: Int) {
    Colors("¿Qué *colores* prefieres?", "Elige tus favoritos (máx. 5)", 5),
    Styles("¿Cuál es tu *estilo*?", "Elige hasta 3", 3),
    Occasions("¿Para qué *ocasiones*?", "¿Dónde usarás tu ropa? (máx. 4)", 4),
    Fabrics("¿Qué *tejidos* amas?", "Elige hasta 4", 4),
}

object QuizCatalog {
    val colors = listOf(
        QuizOption("black", "Negro", 0xFF000000), QuizOption("white", "Blanco", 0xFFFFFFFF),
        QuizOption("blue", "Azul", 0xFF3B82F6), QuizOption("red", "Rojo", 0xFFEF4444),
        QuizOption("green", "Verde", 0xFF22C55E), QuizOption("pink", "Rosa", 0xFFEC4899),
        QuizOption("brown", "Marrón", 0xFF92400E), QuizOption("gray", "Gris", 0xFF6B7280),
        QuizOption("beige", "Beige", 0xFFD4A574), QuizOption("purple", "Morado", 0xFF8B5CF6),
    )
    val styles = listOf(
        QuizOption("casual", "Casual"), QuizOption("elegant", "Elegante"), QuizOption("sporty", "Deportivo"),
        QuizOption("bohemian", "Bohemio"), QuizOption("minimalist", "Minimalista"), QuizOption("classic", "Clásico"),
        QuizOption("streetwear", "Streetwear"), QuizOption("romantic", "Romántico"),
    )
    val occasions = listOf(
        QuizOption("work", "Trabajo"), QuizOption("casual", "Casual"), QuizOption("party", "Fiesta"),
        QuizOption("sport", "Deporte"), QuizOption("date", "Cita"), QuizOption("travel", "Viaje"),
    )
    val fabrics = listOf(
        QuizOption("cotton", "Algodón"), QuizOption("linen", "Lino"), QuizOption("wool", "Lana"),
        QuizOption("silk", "Seda"), QuizOption("denim", "Vaquero"), QuizOption("leather", "Cuero"),
        QuizOption("knit", "Punto"), QuizOption("satin", "Satén"),
    )

    fun options(kind: QuizKind): List<QuizOption> = when (kind) {
        QuizKind.Colors -> colors
        QuizKind.Styles -> styles
        QuizKind.Occasions -> occasions
        QuizKind.Fabrics -> fabrics
    }
}

const val INTRO_PAGES = 3
val QUIZ_STEPS = QuizKind.entries
/** 0..2 = carrusel, 3..6 = quiz. */
const val TOTAL_STEPS = INTRO_PAGES + 4

@Immutable
data class OnboardingState(
    val step: Int = 0,
    val selected: Map<QuizKind, Set<String>> = emptyMap(),
    val saving: Boolean = false,
    val error: String? = null,
    val finished: Boolean = false,
) {
    val inQuiz get() = step >= INTRO_PAGES
    val kind: QuizKind? get() = QUIZ_STEPS.getOrNull(step - INTRO_PAGES)
    val isLast get() = step == TOTAL_STEPS - 1
    fun picks(kind: QuizKind): Set<String> = selected[kind].orEmpty()
}

sealed interface OnboardingEffect {
    /** Todo guardado: mostrar confeti, pedir notificaciones y entrar. */
    data object Done : OnboardingEffect
}

class OnboardingViewModel(
    private val auth: AuthRepository,
    private val store: AuthStore,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()
    private val _effects = Channel<OnboardingEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun goTo(step: Int) = _state.update { if (it.saving || it.finished) it else it.copy(step = step.coerceIn(0, TOTAL_STEPS - 1), error = null) }

    fun back() = _state.update { if (it.step == 0 || it.saving) it else it.copy(step = it.step - 1, error = null) }

    /** "Siguiente": avanza; en el último paso guarda el quiz. */
    fun next() {
        val s = _state.value
        if (s.saving || s.finished) return
        if (s.isLast) save() else _state.update { it.copy(step = it.step + 1, error = null) }
    }

    /** "Saltar" el carrusel: va directo al quiz. */
    fun skipIntro() = _state.update { if (it.inQuiz || it.saving) it else it.copy(step = INTRO_PAGES) }

    /** "Saltar" todo: entra a la app sin guardar nada. */
    fun skipAll() {
        val s = _state.value
        if (s.saving || s.finished) return
        viewModelScope.launch { complete() }
    }

    fun toggle(kind: QuizKind, id: String) = _state.update { s ->
        if (s.saving) return@update s
        val cur = s.picks(kind)
        val next = when {
            id in cur -> cur - id
            cur.size >= kind.max -> cur // tope alcanzado: no cambia
            else -> cur + id
        }
        s.copy(selected = s.selected + (kind to next), error = null)
    }

    private fun save() {
        val s = _state.value
        val colors = s.picks(QuizKind.Colors).toList()
        val styles = s.picks(QuizKind.Styles).toList()
        val occasions = s.picks(QuizKind.Occasions).toList()
        val fabrics = s.picks(QuizKind.Fabrics).toList()
        if (colors.isEmpty() && styles.isEmpty() && occasions.isEmpty() && fabrics.isEmpty()) {
            viewModelScope.launch { complete() }
            return
        }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val r = auth.updatePreferences(
                PreferencesUpdate(styleColors = colors, styleStyles = styles, styleOccasions = occasions, styleFabrics = fabrics),
            )
            when (r) {
                is ApiResult.Success -> complete()
                is ApiResult.Failure -> _state.update {
                    it.copy(saving = false, error = r.error.message + " Puedes reintentar o saltar este paso.")
                }
            }
        }
    }

    private suspend fun complete() {
        store.setOnboardingDone()
        _state.update { it.copy(saving = false, finished = true, error = null) }
        _effects.send(OnboardingEffect.Done)
    }
}
