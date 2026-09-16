package de.bananer.zazendroid.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.bananer.zazendroid.data.catalog.CatalogRepository
import de.bananer.zazendroid.data.catalog.CatalogState
import de.bananer.zazendroid.data.catalog.Course
import de.bananer.zazendroid.data.catalog.Unit
import de.bananer.zazendroid.data.progress.ProgressRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class UnitRow(val unit: Unit, val completed: Boolean)

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data object Missing : DetailUiState
    data class Ready(val course: Course, val rows: List<UnitRow>) : DetailUiState
}

class CourseDetailViewModel(
    courseId: String,
    catalogRepo: CatalogRepository,
    progressRepo: ProgressRepository,
) : ViewModel() {
    val uiState: StateFlow<DetailUiState> = combine(
        catalogRepo.catalogFlow,
        progressRepo.progressFlow(courseId),
    ) { catalog, lastCompleted ->
        val c = (catalog as? CatalogState.Ready)?.catalog?.courses?.find { it.id == courseId }
            ?: return@combine if (catalog is CatalogState.Loading) DetailUiState.Loading else DetailUiState.Missing
        DetailUiState.Ready(
            course = c,
            rows = c.units.map { UnitRow(it, (lastCompleted ?: -1) >= it.orderIndex) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState.Loading)
}
