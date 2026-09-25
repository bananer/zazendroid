package de.bananer.zazendroid.ui.screen

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.bananer.zazendroid.R
import de.bananer.zazendroid.data.playback.PlaybackError
import de.bananer.zazendroid.ui.formatMs
import de.bananer.zazendroid.ui.formatPosition
import de.bananer.zazendroid.ui.theme.rememberCourseBrush
import de.bananer.zazendroid.ui.viewmodel.PlayerViewModel

@Composable
fun PlayerScreen(vm: PlayerViewModel) {
    val state by vm.uiState.collectAsState()
    val isFavorite by vm.isFavorite.collectAsState()
    val hasTrack = state.unit != null || state.single != null

    // Notification permission is asked upfront so the session service may post
    // the media card during real playback. Nothing here starts the service:
    // ExoPlaybackManager does that only on play/toggle, so opening the player
    // can never conjure a stray notification.
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var dragging by remember { mutableStateOf(false) }
    var dragPos by remember { mutableFloatStateOf(0f) }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    state.courseTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.weight(1f),
                )
                if (hasTrack) {
                    IconButton(onClick = { vm.toggleFavorite() }) {
                        Icon(
                            if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = if (isFavorite) stringResource(R.string.cd_remove_favorite) else stringResource(R.string.cd_add_favorite),
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
            Text(
                state.unit?.title ?: state.single?.title ?: stringResource(R.string.player_nothing),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
            )
            if (state.error == PlaybackError.AUDIO_UNAVAILABLE) {
                Text(stringResource(R.string.player_error_audio), color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { vm.toggle() },
                enabled = hasTrack && !state.isBuffering,
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(88.dp),
            ) {
                if (state.isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(44.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) stringResource(R.string.cd_pause) else stringResource(R.string.cd_play),
                        modifier = Modifier.size(44.dp),
                    )
                }
            }
            Column {
                Slider(
                    value = if (dragging) dragPos else state.positionMs.toFloat().coerceIn(0f, range),
                    onValueChange = { dragging = true; dragPos = it },
                    onValueChangeFinished = { dragging = false; vm.seekTo(dragPos.toLong()) },
                    valueRange = 0f..range,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = hasTrack && state.durationMs > 0,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        formatPosition(state.positionMs),
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
                TextButton(onClick = { vm.seekBy(-30_000) }, enabled = hasTrack) {
                    Text("-30s", color = Color.White)
                }
                Text(
                    if (state.single != null) stringResource(R.string.player_single_label)
                    else stringResource(
                        R.string.unit_position,
                        if (state.unitCount > 0) state.unitIndex + 1 else 0,
                        state.unitCount,
                    ),
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall,
                )
                TextButton(onClick = { vm.seekBy(30_000) }, enabled = hasTrack) {
                    Text("+30s", color = Color.White)
                }
            }
        }
    }
}
