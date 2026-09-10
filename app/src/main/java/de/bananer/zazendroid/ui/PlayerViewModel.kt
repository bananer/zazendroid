package de.bananer.zazendroid.ui

import androidx.lifecycle.ViewModel
import de.bananer.zazendroid.data.playback.PlaybackManager
import de.bananer.zazendroid.data.playback.PlaybackUiState
import kotlinx.coroutines.flow.StateFlow

/** Thin wrapper: player truth lives in [PlaybackManager]; screen observes only. */
class PlayerViewModel(private val playback: PlaybackManager) : ViewModel() {
    fun ensureForegroundService() = playback.ensureForegroundService()
    val uiState: StateFlow<PlaybackUiState> = playback.state
    fun toggle() = playback.toggle()
    fun seekTo(ms: Long) = playback.seekTo(ms)
    fun seekBy(deltaMs: Long) = playback.seekBy(deltaMs)
    fun next() = playback.next()
    fun previous() = playback.previous()
}
