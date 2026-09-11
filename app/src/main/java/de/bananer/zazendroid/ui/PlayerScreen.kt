package de.bananer.zazendroid.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.bananer.zazendroid.ui.theme.rememberCourseBrush
@Composable
fun PlayerScreen(vm: PlayerViewModel) {
    val state by vm.uiState.collectAsState()

    if (Build.VERSION.SDK_INT >= 33) {
        val context = LocalContext.current
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) vm.ensureForegroundService()
        }
        LaunchedEffect(Unit) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                vm.ensureForegroundService()
            }
        }
    }

    var dragging by remember { mutableStateOf(false) }
    var dragPos by remember { mutableStateOf(0f) }
    LaunchedEffect(state.positionMs, dragging) {
        if (!dragging) dragPos = state.positionMs.toFloat()
    }
    val range = (state.durationMs.coerceAtLeast(1L)).toFloat()

    Box(
        modifier = Modifier.fillMaxSize()
            .background(rememberCourseBrush(state.courseId ?: "player")),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                state.courseTitle,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.8f),
            )
            Text(
                state.unit?.title ?: "Nothing playing",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
            )
            if (state.error != null) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { vm.toggle() },
                enabled = state.unit != null,
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(88.dp),
            ) {
                Icon(
                    if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(44.dp),
                )
            }
            Column {
                Slider(
                    value = if (dragging) dragPos else state.positionMs.toFloat().coerceIn(0f, range),
                    onValueChange = { dragging = true; dragPos = it },
                    onValueChangeFinished = { dragging = false; vm.seekTo(dragPos.toLong()) },
                    valueRange = 0f..range,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.unit != null && state.durationMs > 0,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        formatMs(state.positionMs),
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        formatMs(state.durationMs.takeIf { it > 0 }),
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { vm.seekBy(-30_000) }, enabled = state.unit != null) {
                    Text("-30s", color = Color.White)
                }
                TextButton(onClick = { vm.previous() }, enabled = state.hasPrevious) {
                    Text("‹ Prev", color = Color.White)
                }
                Text(
                    "${if (state.unitCount > 0) state.unitIndex + 1 else 0} / ${state.unitCount}",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall,
                )
                TextButton(onClick = { vm.next() }, enabled = state.hasNext) {
                    Text("Next ›", color = Color.White)
                }
                TextButton(onClick = { vm.seekBy(30_000) }, enabled = state.unit != null) {
                    Text("+30s", color = Color.White)
                }
            }
        }
    }
}
