package de.bananer.zazendroid.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.bananer.zazendroid.data.catalog.Course
@Composable
fun CourseDetailScreen(
    vm: CourseDetailViewModel,
    onUnitClick: (course: Course, index: Int) -> Unit,
) {
    // Settle notification permission before any tap can start playback: the
    // background service cannot foreground without it.
    if (Build.VERSION.SDK_INT >= 33) {
        val context = LocalContext.current
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
        LaunchedEffect(Unit) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    val state by vm.uiState.collectAsState()
    when (val s = state) {
        is DetailUiState.Loading -> Column(Modifier.fillMaxSize().padding(24.dp)) {
            CircularProgressIndicator()
        }
        is DetailUiState.Missing -> Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text("Course not found.")
        }
        is DetailUiState.Ready -> LazyColumn(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(s.course.title, style = MaterialTheme.typography.headlineSmall)
                Text(s.course.description, style = MaterialTheme.typography.bodyMedium)
            }
            items(s.rows) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { onUnitClick(s.course, row.unit.orderIndex) }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            (if (row.completed) "✓ " else "") + row.unit.title,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            formatMs(row.unit.durationSeconds?.times(1000)),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

fun formatMs(ms: Long?): String {
    if (ms == null || ms <= 0) return "–"
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}
