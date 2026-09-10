package de.bananer.zazendroid.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.bananer.zazendroid.data.catalog.AppInfo
import de.bananer.zazendroid.data.catalog.CatalogState
import de.bananer.zazendroid.data.catalog.CatalogRepository
import de.bananer.zazendroid.data.catalog.Course
import de.bananer.zazendroid.data.progress.ContinueQueue
import de.bananer.zazendroid.data.progress.NextUnit
import de.bananer.zazendroid.data.progress.ProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data object NeedsServerSetup : LibraryUiState
    data class CatalogError(val msg: String) : LibraryUiState
    data class Ready(
        val appInfo: AppInfo,
        val courses: List<Course>,
        val nextUp: List<NextUnit>,
        val fromCache: Boolean,
    ) : LibraryUiState
}

class LibraryViewModel(
    catalogRepo: CatalogRepository,
    progressRepo: ProgressRepository,
    hasStoredUrl: Flow<Boolean>,
) : ViewModel() {
    val uiState: StateFlow<LibraryUiState> = combine(
        catalogRepo.catalogFlow,
        progressRepo.allProgressFlow(),
        hasStoredUrl,
    ) { catalog, progress, stored ->
        if (!stored) return@combine LibraryUiState.NeedsServerSetup
        when (catalog) {
            is CatalogState.Loading -> LibraryUiState.Loading
            is CatalogState.Error -> LibraryUiState.CatalogError(catalog.message)
            is CatalogState.Ready -> LibraryUiState.Ready(
                appInfo = catalog.catalog.appInfo,
                courses = catalog.catalog.courses,
                nextUp = ContinueQueue.nextUp(catalog.catalog.courses, progress),
                fromCache = catalog.fromCache,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState.Loading)
}
