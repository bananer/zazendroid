package de.bananer.zazendroid.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.bananer.zazendroid.data.settings.EmptyServerUrlException
import de.bananer.zazendroid.data.settings.ServerUrlStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SetupUiState {
    data class NeedsUrl(val prefill: String) : SetupUiState
    data object Saving : SetupUiState
    data object Saved : SetupUiState
    data class InvalidUrl(val kind: SetupErrorKind, val prefill: String) : SetupUiState
}

/** Resolved at the UI boundary via `setup_error_empty` / `setup_error_invalid`. */
enum class SetupErrorKind {
    EMPTY,
    INVALID,
}

class ServerSetupViewModel(private val store: ServerUrlStore) : ViewModel() {
    private val _uiState = MutableStateFlow<SetupUiState>(
        SetupUiState.NeedsUrl(ServerUrlStore.DEFAULT_DATA_SERVER_URL),
    )
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    fun save(url: String) {
        _uiState.value = SetupUiState.Saving
        viewModelScope.launch {
            try {
                store.setServerUrl(url)
                _uiState.value = SetupUiState.Saved
            } catch (e: IllegalArgumentException) {
                _uiState.value = SetupUiState.InvalidUrl(
                    kind = when (e) {
                        is EmptyServerUrlException -> SetupErrorKind.EMPTY
                        else -> SetupErrorKind.INVALID
                    },
                    prefill = url,
                )
            }
        }
    }

    /** Returns to a fresh [SetupUiState.NeedsUrl] prefilled with the default (e.g. after a reset). */
    fun backToSetup() {
        _uiState.value = SetupUiState.NeedsUrl(ServerUrlStore.DEFAULT_DATA_SERVER_URL)
    }
}
