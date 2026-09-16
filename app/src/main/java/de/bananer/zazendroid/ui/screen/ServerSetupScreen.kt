package de.bananer.zazendroid.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.bananer.zazendroid.R
import de.bananer.zazendroid.ui.viewmodel.ServerSetupViewModel
import de.bananer.zazendroid.ui.viewmodel.SetupErrorKind
import de.bananer.zazendroid.ui.viewmodel.SetupUiState

/** First-launch server setup. */
@Composable
fun ServerSetupScreen(vm: ServerSetupViewModel, modifier: Modifier = Modifier) {
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
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = if (error != null && text.isEmpty()) error.prefill else text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = error != null,
        )
        if (error != null) {
            Text(
                when (error.kind) {
                    SetupErrorKind.EMPTY -> stringResource(R.string.setup_error_empty)
                    SetupErrorKind.INVALID -> stringResource(R.string.setup_error_invalid, error.prefill)
                },
                color = MaterialTheme.colorScheme.error,
            )
        }
        Button(
            onClick = { vm.save(text) },
            enabled = state !is SetupUiState.Saving,
        ) {
            Text(
                if (state is SetupUiState.Saving) stringResource(R.string.action_saving)
                else stringResource(R.string.action_save),
            )
        }
    }
}
