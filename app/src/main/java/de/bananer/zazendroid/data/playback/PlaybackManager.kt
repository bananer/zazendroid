package de.bananer.zazendroid.data.playback

import de.bananer.zazendroid.data.catalog.Course
import de.bananer.zazendroid.data.catalog.Single
import de.bananer.zazendroid.data.catalog.Unit
import kotlinx.coroutines.flow.StateFlow

/** UI snapshot of playback. Duration 0 = unknown. [single] set during single playback. */
data class PlaybackUiState(
    val courseId: String? = null,
    val courseTitle: String = "",
    val unit: Unit? = null,
    val single: Single? = null,
    val unitIndex: Int = 0,
    val unitCount: Int = 0,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val error: String? = null,
)

interface PlaybackManager {
    val state: StateFlow<PlaybackUiState>
    fun play(course: Course, startIndex: Int)
    /** Loads the course queue at [startIndex] and prepares, but does not start playback. */
    fun queue(course: Course, startIndex: Int)
    fun playSingle(single: Single)
    /** Loads a single and prepares, but does not start playback. Never touches course progress. */
    fun queueSingle(single: Single)
    fun toggle()
    fun seekTo(positionMs: Long)
    fun seekBy(deltaMs: Long)
    /**
     * Starts the background service (no-op when already running or when the
     * notification permission is still missing — in that case playback stays
     * in-activity until permission is granted and this is called again).
     */
    fun ensureForegroundService()
}
