package de.bananer.zazendroid.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.bananer.zazendroid.data.catalog.AppInfo
import de.bananer.zazendroid.data.catalog.CatalogErrorKind
import de.bananer.zazendroid.data.catalog.CatalogRepository
import de.bananer.zazendroid.data.catalog.CatalogState
import de.bananer.zazendroid.data.catalog.Category
import de.bananer.zazendroid.data.catalog.Course
import de.bananer.zazendroid.data.catalog.Single
import de.bananer.zazendroid.data.favorites.FavoriteEntry
import de.bananer.zazendroid.data.favorites.FavoriteList
import de.bananer.zazendroid.data.favorites.FavoritesRepository
import de.bananer.zazendroid.data.progress.ContinueQueue
import de.bananer.zazendroid.data.progress.NextUnit
import de.bananer.zazendroid.data.progress.ProgressRepository
import de.bananer.zazendroid.data.settings.ServerUrlStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data object NeedsServerSetup : LibraryUiState
    data class CatalogError(val kind: CatalogErrorKind) : LibraryUiState
    data class Ready(
        val appInfo: AppInfo,
        val categories: List<Category>,
        val courses: List<Course>,
        val singles: List<Single>,
        val nextUp: List<NextUnit>,
        val favorites: List<FavoriteEntry>,
        val fromCache: Boolean,
        val loadedFrom: String,
    ) : LibraryUiState {
        private val titlesById: Map<String, String> by lazy { categories.associate { it.id to it.title } }
        fun categoryTitle(id: String): String? = titlesById[id]
    }
}

class LibraryViewModel(
    catalogRepo: CatalogRepository,
    progressRepo: ProgressRepository,
    favoritesRepo: FavoritesRepository,
    hasStoredUrl: Flow<Boolean>,
    private val serverUrlStore: ServerUrlStore,
) : ViewModel() {
    val uiState: StateFlow<LibraryUiState> = combine(
        catalogRepo.catalogFlow,
        progressRepo.allProgressFlow(),
        favoritesRepo.favoritesFlow(),
        hasStoredUrl,
    ) { catalog, progress, favorites, stored ->
        if (!stored) return@combine LibraryUiState.NeedsServerSetup
        when (catalog) {
            is CatalogState.Loading -> LibraryUiState.Loading
            is CatalogState.Error -> LibraryUiState.CatalogError(catalog.kind)
            is CatalogState.Ready -> LibraryUiState.Ready(
                appInfo = catalog.catalog.appInfo,
                categories = catalog.catalog.categories,
                courses = catalog.catalog.courses,
                singles = catalog.catalog.singles,
                nextUp = ContinueQueue.nextUp(catalog.catalog.courses, progress),
                favorites = FavoriteList.resolve(catalog.catalog.courses, catalog.catalog.singles, favorites),
                fromCache = catalog.fromCache,
                loadedFrom = catalog.catalog.loadedFrom,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState.Loading)
    /** Clears the stored server URL; the setup gate picks up the change. */
    fun resetServer() {
        viewModelScope.launch { serverUrlStore.clear() }
    }
}
