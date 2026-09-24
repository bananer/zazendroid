package de.bananer.zazendroid.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.bananer.zazendroid.R
import de.bananer.zazendroid.data.catalog.Catalog
import de.bananer.zazendroid.data.catalog.CatalogSearch
import de.bananer.zazendroid.data.catalog.SearchHit
import de.bananer.zazendroid.data.catalog.Single
import de.bananer.zazendroid.ui.formatMs
import de.bananer.zazendroid.ui.viewmodel.LibraryUiState
import de.bananer.zazendroid.ui.viewmodel.LibraryViewModel

/**
 * Local-catalog search tab: text field on top, grouped/labeled result rows.
 * Course/unit taps open the course detail; single taps queue + play.
 */
@Composable
fun SearchScreen(
    vm: LibraryViewModel,
    onCourseClick: (courseId: String) -> Unit,
    onSingleClick: (single: Single) -> Unit,
    onResetServer: () -> Unit,
) {
    LibraryTabScaffold(vm, onResetServer) { s ->
        var query by rememberSaveable(s.loadedFrom) { mutableStateOf("") }
        val hits = if (s.courses.isEmpty() && s.singles.isEmpty()) {
            emptyList()
        } else {
            CatalogSearch.search(s.toCatalog(), query)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                )
            }
            if (query.isBlank()) {
                item {
                    Text(
                        stringResource(R.string.search_prompt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (hits.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.search_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(hits, key = { hitKey(it) }) { hit ->
                    when (hit) {
                        is SearchHit.CourseHit -> SearchRow(
                            eyebrow = stringResource(R.string.search_type_course),
                            title = hit.course.title,
                            sub = s.categoryTitle(hit.course.categoryId)
                                ?: hit.course.authorName ?: "",
                            onClick = { onCourseClick(hit.course.id) },
                        )
                        is SearchHit.UnitHit -> SearchRow(
                            eyebrow = stringResource(
                                R.string.search_type_unit,
                                hit.course.title,
                            ),
                            title = hit.unit.title,
                            sub = formatMs(hit.unit.durationSeconds?.times(1000)),
                            onClick = { onCourseClick(hit.course.id) },
                        )
                        is SearchHit.SingleHit -> SearchRow(
                            eyebrow = stringResource(R.string.search_type_single),
                            title = hit.single.title,
                            sub = listOfNotNull(
                                hit.single.authorName,
                                s.categoryTitle(hit.single.categoryId),
                            ).joinToString(" · "),
                            onClick = { onSingleClick(hit.single) },
                        )
                    }
                }
            }
        }
    }
}

private fun hitKey(hit: SearchHit): String = when (hit) {
    is SearchHit.CourseHit -> "c:${hit.course.id}"
    is SearchHit.UnitHit -> "u:${hit.course.id}:${hit.unit.id}"
    is SearchHit.SingleHit -> "s:${hit.single.id}"
}
@Composable
private fun SearchRow(eyebrow: String, title: String, sub: String, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(eyebrow, style = MaterialTheme.typography.labelSmall)
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (sub.isNotEmpty()) {
                    Text(
                        sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.cd_play))
        }
    }
}

private fun LibraryUiState.Ready.toCatalog(): Catalog =
    Catalog(
        appInfo = appInfo,
        categories = categories,
        courses = courses,
        singles = singles,
        loadedFrom = loadedFrom,
        fromCache = fromCache,
    )
