package de.bananer.zazendroid.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.bananer.zazendroid.R
import de.bananer.zazendroid.data.catalog.Course
import de.bananer.zazendroid.ui.viewmodel.CourseDetailViewModel
import de.bananer.zazendroid.ui.viewmodel.DetailUiState
import de.bananer.zazendroid.ui.formatMs

@Composable
fun CourseDetailScreen(
    vm: CourseDetailViewModel,
    onUnitClick: (course: Course, index: Int) -> Unit,
    onPreload: (course: Course, index: Int) -> Unit,
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
            Text(stringResource(R.string.detail_missing))
        }
        is DetailUiState.Ready -> LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(s.course.title, style = MaterialTheme.typography.headlineMedium)
                Text(
                    s.course.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(s.rows) { row ->
                LaunchedEffect(s.course.id, row.unit.orderIndex) {
                    onPreload(s.course, row.unit.orderIndex)
                }
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { onUnitClick(s.course, row.unit.orderIndex) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (row.completed) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = stringResource(R.string.cd_completed),
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                    } else {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(row.unit.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            formatMs(row.unit.durationSeconds?.times(1000)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
}
}

