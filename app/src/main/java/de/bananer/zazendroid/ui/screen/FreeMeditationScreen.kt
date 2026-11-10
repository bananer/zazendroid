package de.bananer.zazendroid.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.bananer.zazendroid.R
import kotlin.math.roundToInt

internal const val FREE_MEDITATION_MIN = 5
internal const val FREE_MEDITATION_MAX = 120

/**
 * Free meditation setup: pick a length (5–120 min) and start. Playback runs on
 * the app-scoped synthesizing player behind the shared media session.
 */
@Composable
fun FreeMeditationScreen(onStart: (minutes: Int) -> Unit) {
    var minutes by rememberSaveable { mutableIntStateOf(20) }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.free_meditation_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            stringResource(R.string.free_meditation_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            stringResource(R.string.free_meditation_minutes, minutes),
            style = MaterialTheme.typography.displaySmall,
        )
        Slider(
            value = minutes.toFloat(),
            onValueChange = { minutes = it.roundToInt().coerceIn(FREE_MEDITATION_MIN, FREE_MEDITATION_MAX) },
            valueRange = FREE_MEDITATION_MIN.toFloat()..FREE_MEDITATION_MAX.toFloat(),
            steps = FREE_MEDITATION_MAX - FREE_MEDITATION_MIN - 1,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onStart(minutes) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.free_meditation_start))
        }
    }
}
