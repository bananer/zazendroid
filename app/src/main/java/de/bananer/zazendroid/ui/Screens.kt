package de.bananer.zazendroid.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.bananer.zazendroid.data.catalog.Course

/** First-launch server setup + library. */
@Composable
fun ServerSetupScreen(vm: ServerSetupViewModel) {
    val state by vm.uiState.collectAsState()
    var text by remember { mutableStateOf((state as? SetupUiState.NeedsUrl)?.prefill ?: "") }
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

@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    onCourseClick: (courseId: String) -> Unit,
    onContinueClick: (course: Course, index: Int) -> Unit,
) {
    val state by vm.uiState.collectAsState()
    when (val s = state) {
        is LibraryUiState.Loading -> Column(Modifier.fillMaxSize().padding(24.dp)) {
            CircularProgressIndicator()
        }
        is LibraryUiState.NeedsServerSetup -> Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text("No server configured.")
        }
        is LibraryUiState.CatalogError -> Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text("Error: ${s.msg}")
        }
        is LibraryUiState.Ready -> LazyColumn(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(s.appInfo.appName, style = MaterialTheme.typography.headlineSmall)
                Text(s.appInfo.description, style = MaterialTheme.typography.bodyMedium)
                if (s.fromCache) Text("(offline cache)", style = MaterialTheme.typography.labelSmall)
            }
            if (s.nextUp.isNotEmpty()) {
                item { Text("Continue", style = MaterialTheme.typography.titleMedium) }
                items(s.nextUp) { next ->
                    Text(
                        "▶ ${next.courseTitle}: ${next.unit.title}",
                        modifier = Modifier.clickable {
                            s.courses.find { it.id == next.courseId }?.let { course ->
                                onContinueClick(course, next.unit.orderIndex)
                            }
                        },
                    )
                }
            }
            item { Text("Courses", style = MaterialTheme.typography.titleMedium) }
            items(s.courses) { course ->
                Column(
                    modifier = Modifier.fillMaxWidth().clickable { onCourseClick(course.id) },
                ) {
                    Text(course.title, style = MaterialTheme.typography.titleSmall)
                    Text(course.description, style = MaterialTheme.typography.bodySmall)
                    Text("${course.units.size} units", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
