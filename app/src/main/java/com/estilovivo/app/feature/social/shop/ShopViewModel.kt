package com.estilovivo.app.feature.social.shop

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estilovivo.app.core.data.ApiResult
import com.estilovivo.app.core.data.model.ShopItem
import com.estilovivo.app.core.data.model.User
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class ShopUiState(
    val greetingName: String = "",
    val avatar: String? = null,
    val query: String = "",
    val category: String = "all",
    val filters: ShopFilters = ShopFilters(),
    val favoritesOnly: Boolean = false,
    /** Resultado del servidor para la búsqueda + categoría (sin filtros locales). */
    val loaded: List<ShopItem> = emptyList(),
    /** Lo que se muestra: [loaded] tras aplicar filtros y favoritos. */
    val visible: List<ShopItem> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
    val availableSizes: List<String> = emptyList(),
    /** Primera carga (o cambio de búsqueda) sin nada que enseñar todavía. */
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    /** Error a pantalla completa (sólo si no hay nada que enseñar). */
    val error: String? = null,
) {
    val hasActiveFilters: Boolean get() = !filters.isEmpty
}

class ShopViewModel(
    private val catalog: ShopCatalog,
    me: StateFlow<User?>,
    private val searchDebounceMs: Long = SEARCH_DEBOUNCE_MS,
) : ViewModel() {

    private data class Core(
        val query: String = "",
        val category: String = "all",
        val filters: ShopFilters = ShopFilters(),
        val favoritesOnly: Boolean = false,
        val loaded: List<ShopItem> = emptyList(),
        /** (búsqueda, categoría) a la que pertenece [loaded]. */
        val loadedKey: Pair<String, String>? = null,
        val loading: Boolean = true,
        val refreshing: Boolean = false,
        val error: String? = null,
    )

    private val core = MutableStateFlow(Core())
    private val errorChannel = Channel<String>(Channel.BUFFERED)

    /** Mensajes de error para mostrar en un toast. */
    val errors = errorChannel.receiveAsFlow()

    val state: StateFlow<ShopUiState> = combine(core, catalog.favoriteIds, me) { c, favs, user ->
        ShopUiState(
            greetingName = user?.name?.substringBefore(' ').orEmpty(),
            avatar = user?.avatar,
            query = c.query,
            category = c.category,
            filters = c.filters,
            favoritesOnly = c.favoritesOnly,
            loaded = c.loaded,
            visible = c.loaded.applyFilters(c.filters, c.favoritesOnly, favs),
            favoriteIds = favs,
            availableSizes = c.loaded.availableSizes(),
            loading = c.loading,
            refreshing = c.refreshing,
            error = c.error,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ShopUiState())

    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private var loadSeq = 0

    init {
        load()
        viewModelScope.launch { catalog.refreshFavorites() }
    }

    /** Búsqueda con debounce: no se pide al servidor en cada pulsación. */
    fun onQueryChange(text: String) {
        val clean = text.take(MAX_QUERY)
        if (clean == core.value.query) return
        core.update { it.copy(query = clean) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(searchDebounceMs)
            load()
        }
    }

    fun onCategory(id: String) {
        if (id == core.value.category) return
        searchJob?.cancel()
        core.update { it.copy(category = id) }
        load()
    }

    fun onFilters(filters: ShopFilters) {
        core.update { it.copy(filters = filters) }
    }

    fun onFavoritesOnly(enabled: Boolean) {
        core.update { it.copy(favoritesOnly = enabled) }
    }

    /** Pull-to-refresh y reintento. */
    fun refresh() {
        load(userInitiated = true)
        viewModelScope.launch { catalog.refreshFavorites() }
    }

    fun onToggleFavorite(id: String) {
        viewModelScope.launch {
            val result = catalog.toggleFavorite(id) ?: return@launch
            if (result is ApiResult.Failure) errorChannel.trySend(result.error.message)
        }
    }

    private fun load(userInitiated: Boolean = false) {
        loadJob?.cancel()
        val seq = ++loadSeq
        val snapshot = core.value
        val key = snapshot.query.trim() to snapshot.category
        core.update { it.copy(loading = it.loaded.isEmpty() && !userInitiated, refreshing = userInitiated, error = null) }
        loadJob = viewModelScope.launch {
            val result = catalog.search(snapshot.query.trim().ifEmpty { null }, snapshot.category.takeIf { it != "all" })
            if (seq != loadSeq) return@launch // llegó tarde: ya hay una petición más nueva
            when (result) {
                is ApiResult.Success ->
                    core.update { it.copy(loaded = result.value, loadedKey = key, loading = false, refreshing = false, error = null) }
                is ApiResult.Failure -> {
                    // Si lo que hay en pantalla es de otra búsqueda/categoría, mejor error a pantalla completa que datos engañosos
                    val stillValid = core.value.loaded.isNotEmpty() && core.value.loadedKey == key
                    core.update {
                        if (stillValid) it.copy(loading = false, refreshing = false)
                        else it.copy(loaded = emptyList(), loadedKey = null, loading = false, refreshing = false, error = result.error.message)
                    }
                    if (stillValid) errorChannel.trySend(result.error.message)
                }
            }
        }
    }

    companion object {
        const val SEARCH_DEBOUNCE_MS = 350L
        const val MAX_QUERY = 80
    }
}
