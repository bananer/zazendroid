package de.bananer.zazendroid.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.bananer.zazendroid.R
import de.bananer.zazendroid.data.catalog.CatalogErrorKind
import de.bananer.zazendroid.data.catalog.Course
import de.bananer.zazendroid.data.catalog.Single
import de.bananer.zazendroid.data.favorites.FavoriteEntry
import de.bananer.zazendroid.ui.viewmodel.LibraryUiState
import de.bananer.zazendroid.ui.viewmodel.LibraryViewModel
import de.bananer.zazendroid.ui.formatMs
import de.bananer.zazendroid.ui.theme.rememberCourseBrush

/** Non-Ready library states render identically on every tab. */
@Composable
private fun LibraryTabScaffold(
    vm: LibraryViewModel,
    onResetServer: () -> Unit,
    content: @Composable (LibraryUiState.Ready) -> Unit,
) {
    val state by vm.uiState.collectAsState()
    when (val s = state) {
        is LibraryUiState.Loading -> Column(Modifier.fillMaxSize().padding(24.dp)) {
            CircularProgressIndicator()
        }
        is LibraryUiState.NeedsServerSetup -> Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text(stringResource(R.string.library_no_server))
        }
        is LibraryUiState.CatalogError -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(
                    R.string.library_error,
                    stringResource(
                        when (s.kind) {
                            CatalogErrorKind.UNREACHABLE -> R.string.catalog_error_unreachable
                            CatalogErrorKind.INVALID_CONTENT -> R.string.catalog_error_invalid_content
                        },
                    ),
                ),
            )
            Button(onClick = onResetServer) {
                Text(stringResource(R.string.action_change_server))
            }
        }
        is LibraryUiState.Ready -> content(s)
    }
}

@Composable
fun HomeScreen(
    vm: LibraryViewModel,
    onContinueClick: (course: Course, index: Int) -> Unit,
    onFavoriteClick: (entry: FavoriteEntry) -> Unit,
    onPreload: (course: Course, index: Int) -> Unit,
    onPreloadSingle: (single: Single) -> Unit,
    onResetServer: () -> Unit,
) {
    LibraryTabScaffold(vm, onResetServer) { s ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(s.appInfo.appName, style = MaterialTheme.typography.headlineMedium)
                Text(
                    s.appInfo.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (s.fromCache) {
                    Text(
                        stringResource(R.string.badge_offline_cache),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Text(
                    "${pluralStringResource(R.plurals.courses_count, s.courses.size, s.courses.size)} · " +
                        pluralStringResource(R.plurals.singles_count, s.singles.size, s.singles.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onResetServer) {
                    Text(stringResource(R.string.action_change_server))
                }
            }
            if (s.nextUp.isNotEmpty()) {
                item { Text(stringResource(R.string.heading_continue), style = MaterialTheme.typography.titleMedium) }
                items(s.nextUp) { next ->
                    val course = s.courses.find { it.id == next.courseId }
                    if (course != null && next.unit.orderIndex in course.units.indices) {
                        LaunchedEffect(next.courseId, next.unit.orderIndex) {
                            onPreload(course, next.unit.orderIndex)
                        }
                    }
                    Card(
                        onClick = {
                            if (course != null) onContinueClick(course, next.unit.orderIndex)
                        },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(next.courseTitle, style = MaterialTheme.typography.labelLarge)
                                Text(next.unit.title, style = MaterialTheme.typography.titleLarge)
                                if (course != null) {
                                    Text(
                                        stringResource(
                                            R.string.unit_position,
                                            next.unit.orderIndex + 1,
                                            course.units.size,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.cd_resume))
                        }
                    }
                }
            } else {
                item {
                    Text(
                        stringResource(R.string.home_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (s.favorites.isNotEmpty()) {
                item { Text(stringResource(R.string.heading_favorites), style = MaterialTheme.typography.titleMedium) }
                items(s.favorites) { entry ->
                    when (entry) {
                        is FavoriteEntry.UnitFavorite -> LaunchedEffect(entry.unit.id) {
                            onPreload(entry.course, entry.unit.orderIndex)
                        }
                        is FavoriteEntry.SingleFavorite -> LaunchedEffect(entry.single.id) {
                            onPreloadSingle(entry.single)
                        }
                    }
                    FavoriteCard(entry, onClick = { onFavoriteClick(entry) })
                }
            }
        }
    }
}

@Composable
fun CoursesScreen(
    vm: LibraryViewModel,
    onCourseClick: (courseId: String) -> Unit,
    onPreload: (course: Course, index: Int) -> Unit,
    onResetServer: () -> Unit,
) {
    LibraryTabScaffold(vm, onResetServer) { s ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Text(stringResource(R.string.heading_courses), style = MaterialTheme.typography.titleMedium) }
            items(s.courses) { course ->
                if (course.units.isNotEmpty()) {
                    LaunchedEffect(course.id) {
                        onPreload(course, 0)
                    }
                }
                CourseCard(course) { onCourseClick(course.id) }
            }
        }
    }
}

@Composable
fun SinglesScreen(
    vm: LibraryViewModel,
    onSingleClick: (single: Single) -> Unit,
    onPreloadSingle: (single: Single) -> Unit,
    onResetServer: () -> Unit,
) {
    LibraryTabScaffold(vm, onResetServer) { s ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Text(stringResource(R.string.heading_singles), style = MaterialTheme.typography.titleMedium) }
            if (s.singles.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.singles_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(s.singles) { single ->
                LaunchedEffect(single.id) {
                    onPreloadSingle(single)
                }
                Card(onClick = { onSingleClick(single) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(single.title, style = MaterialTheme.typography.titleMedium)
                            val meta = listOfNotNull(single.authorName, single.categoryTitle)
                                .joinToString(" · ")
                            if (meta.isNotEmpty()) {
                                Text(
                                    meta,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                formatMs(single.durationSeconds?.times(1000)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.cd_play))
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseCard(course: Course, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().height(96.dp)
                    .background(rememberCourseBrush(course.id)),
                contentAlignment = Alignment.BottomStart,
            ) {
                Text(
                    course.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Column(Modifier.padding(16.dp)) {
                Text(
                    course.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    pluralStringResource(R.plurals.units_count, course.units.size, course.units.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FavoriteCard(entry: FavoriteEntry, onClick: () -> Unit) {
    val singleFallback = stringResource(R.string.eyebrow_single)
    val (eyebrow, title, sub) = when (entry) {
        is FavoriteEntry.UnitFavorite ->
            Triple(
                entry.course.title,
                entry.unit.title,
                stringResource(
                    R.string.unit_position,
                    entry.unit.orderIndex + 1,
                    entry.course.units.size,
                ),
            )
        is FavoriteEntry.SingleFavorite ->
            Triple(
                listOfNotNull(entry.single.authorName, entry.single.categoryTitle)
                    .joinToString(" · ").ifEmpty { singleFallback },
                entry.single.title,
                formatMs(entry.single.durationSeconds?.times(1000)),
            )
    }
    Card(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(eyebrow, style = MaterialTheme.typography.labelLarge)
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.cd_play_favorite))
        }
    }
}
