package de.bananer.zazendroid.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun PlayerScreen(vm: PlayerViewModel) {
    val state by vm.uiState.collectAsState()

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

    var dragging by remember { mutableStateOf(false) }
    var dragPos by remember { mutableStateOf(0f) }
    LaunchedEffect(state.positionMs, dragging) {
        if (!dragging) dragPos = state.positionMs.toFloat()
    }
    val range = (state.durationMs.coerceAtLeast(1L)).toFloat()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(state.courseTitle, style = MaterialTheme.typography.titleMedium)
        Text(
            state.unit?.title ?: "Nothing playing",
            style = MaterialTheme.typography.headlineSmall,
        )
        if (state.error != null) {
            Text(state.error!!, color = MaterialTheme.colorScheme.error)
        }
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
            Text(formatMs(state.positionMs))
            Text(formatMs(state.durationMs.takeIf { it > 0 }))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { vm.seekBy(-30_000) }, enabled = state.unit != null) {
                Text("-30s")
            }
            Button(onClick = { vm.toggle() }, enabled = state.unit != null) {
                Text(if (state.isPlaying) "Pause" else "Play")
            }
            TextButton(onClick = { vm.seekBy(30_000) }, enabled = state.unit != null) {
                Text("+30s")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { vm.previous() }, enabled = state.hasPrevious) {
                Text("‹ Prev")
            }
            Text(
                "${if (state.unitCount > 0) state.unitIndex + 1 else 0} / ${state.unitCount}",
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            TextButton(onClick = { vm.next() }, enabled = state.hasNext) {
                Text("Next ›")
            }
        }
    }
}
