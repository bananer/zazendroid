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

/**
 * App-scoped playback seam. Playing a course enqueues all its units from
 * [startIndex]; automatic track advance marks each finished unit completed in
 * [de.bananer.zazendroid.data.progress.ProgressRepository] (monotonic).
 * Manual next/previous/seek never marks completion.
 */
interface PlaybackManager {
    val state: StateFlow<PlaybackUiState>
    fun play(course: Course, startIndex: Int)
    fun toggle()
    fun seekTo(positionMs: Long)
    fun seekBy(deltaMs: Long)
    fun next()
    fun previous()
}
