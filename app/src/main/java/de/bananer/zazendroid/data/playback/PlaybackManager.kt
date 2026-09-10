package de.bananer.zazendroid.data.playback

import de.bananer.zazendroid.data.catalog.Course
import de.bananer.zazendroid.data.catalog.Unit
import kotlinx.coroutines.flow.StateFlow

/** UI snapshot of playback. Duration 0 = unknown. */
data class PlaybackUiState(
    val courseId: String? = null,
    val courseTitle: String = "",
    val unit: Unit? = null,
    val unitIndex: Int = 0,
    val unitCount: Int = 0,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val error: String? = null,
) {
    val hasNext: Boolean get() = unitIndex < unitCount - 1
    val hasPrevious: Boolean get() = unitIndex > 0
}

interface PlaybackManager {
    val state: StateFlow<PlaybackUiState>
    fun play(course: Course, startIndex: Int)
    fun toggle()
    fun seekTo(positionMs: Long)
    fun seekBy(deltaMs: Long)
    fun next()
    fun previous()
    /**
     * Starts the background service (no-op when already running or when the
     * notification permission is still missing — in that case playback stays
     * in-activity until permission is granted and this is called again).
     */
    fun ensureForegroundService()
}
