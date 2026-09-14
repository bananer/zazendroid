package de.bananer.zazendroid.ui

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.bananer.zazendroid.data.catalog.Course
import de.bananer.zazendroid.data.catalog.Single
import de.bananer.zazendroid.data.favorites.FavoriteEntry
import de.bananer.zazendroid.ui.theme.rememberCourseBrush

/** First-launch server setup + library. */
@Composable
fun ServerSetupScreen(vm: ServerSetupViewModel) {
    val state by vm.uiState.collectAsState()
    var text by remember(state) {
        mutableStateOf(
            (state as? SetupUiState.NeedsUrl)?.prefill
                ?: (state as? SetupUiState.InvalidUrl)?.prefill
                ?: "",
        )
    }
    val error = state as? SetupUiState.InvalidUrl
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Data server URL", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = if (error != null && text.isEmpty()) error.prefill else text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = error != null,
        )
        if (error != null) Text(error.reason, color = MaterialTheme.colorScheme.error)
        Button(
            onClick = { vm.save(text) },
            enabled = state !is SetupUiState.Saving,
        ) {
            Text(if (state is SetupUiState.Saving) "Saving…" else "Save")
        }
    }
}

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
            Text("No server configured.")
        }
        is LibraryUiState.CatalogError -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Error: ${s.msg}")
            Button(onClick = onResetServer) {
                Text("Change server")
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
                        "Offline cache",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Text(
                    "${s.courses.size} courses · ${s.singles.size} singles",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onResetServer) {
                    Text("Change server")
                }
            }
            if (s.nextUp.isNotEmpty()) {
                item { Text("Continue", style = MaterialTheme.typography.titleMedium) }
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
                                        "Unit ${next.unit.orderIndex + 1} of ${course.units.size}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Resume")
                        }
                    }
                }
            } else {
                item {
                    Text(
                        "Nothing in progress yet. Pick a course or a single to begin.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (s.favorites.isNotEmpty()) {
                item { Text("Favorites", style = MaterialTheme.typography.titleMedium) }
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
            item { Text("Courses", style = MaterialTheme.typography.titleMedium) }
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
            item { Text("Singles", style = MaterialTheme.typography.titleMedium) }
            if (s.singles.isEmpty()) {
                item {
                    Text(
                        "No singles on this server yet.",
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
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
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
                    "${course.units.size} units",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FavoriteCard(entry: FavoriteEntry, onClick: () -> Unit) {
    val (eyebrow, title, sub) = when (entry) {
        is FavoriteEntry.UnitFavorite ->
            Triple(
                entry.course.title,
                entry.unit.title,
                "Unit ${entry.unit.orderIndex + 1} of ${entry.course.units.size}",
            )
        is FavoriteEntry.SingleFavorite ->
            Triple(
                listOfNotNull(entry.single.authorName, entry.single.categoryTitle)
                    .joinToString(" · ").ifEmpty { "Single" },
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
            Icon(Icons.Filled.PlayArrow, contentDescription = "Play favorite")
        }
    }
}
