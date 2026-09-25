package de.bananer.zazendroid.data.playback

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import de.bananer.zazendroid.data.catalog.Course
import de.bananer.zazendroid.data.catalog.Single
import de.bananer.zazendroid.data.progress.ProgressRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * ExoPlayer-backed [PlaybackManager]. Owns one app-scoped player (surfaces to
 * [PlaybackService] for background + notification); UI observes [state] only.
 */
class ExoPlaybackManager(
    private val appContext: Context,
    private val progress: ProgressRepository,
    private val scope: CoroutineScope,
) : PlaybackManager {

    private val _state = MutableStateFlow(PlaybackUiState())
    override val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private var course: Course? = null
    private var single: Single? = null
    private var currentUnitIndex: Int = 0
    private var pollJob: Job? = null

    val player: ExoPlayer by lazy {
        ExoPlayer.Builder(appContext).build().also { exo ->
            exo.addListener(listener)
        }
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            publish()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                course?.let { c ->
                    scope.launch { progress.markCompleted(c.id, currentUnitIndex) }
                }
            }
            publish()
            updatePolling()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            publish()
            updatePolling()
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.update { it.copy(error = PlaybackError.AUDIO_UNAVAILABLE, isPlaying = false, isBuffering = false) }
        }
    }

    override fun play(course: Course, startIndex: Int) {
        queue(course, startIndex)
        // Service first: it must exist before playback starts so the
        // notification is up while audio runs in background.
        startPlayback()
    }

    /**
     * Loads only the tapped unit, never the rest of the course: Exo treats
     * multiple media items as a playlist and auto-advances, but playback
     * MUST stop when the current unit finishes.
     */
    override fun queue(course: Course, startIndex: Int) {
        require(course.units.isNotEmpty()) { "course has no units" }
        this.course = course
        this.single = null
        this.currentUnitIndex = startIndex.coerceIn(course.units.indices)
        _state.update { it.copy(error = null) }
        player.setMediaItems(
            listOf(MediaItem.fromUri(course.units[currentUnitIndex].audioUrl)),
            0,
            0L,
        )
        player.prepare()
        player.pause()
        publish()
        updatePolling()
    }

    override fun playSingle(single: Single) {
        queueSingle(single)
        startPlayback()
    }

    override fun queueSingle(single: Single) {
        this.course = null
        this.single = single
        _state.update { it.copy(error = null) }
        player.setMediaItems(listOf(MediaItem.fromUri(single.audioUrl)), 0, 0L)
        player.prepare()
        player.pause()
        publish()
        updatePolling()
    }

    override fun preload(course: Course, startIndex: Int) {
        if (player.isPlaying || player.mediaItemCount > 0 || course.units.isEmpty()) return
        player.setMediaItems(
            listOf(MediaItem.fromUri(course.units[startIndex.coerceIn(course.units.indices)].audioUrl)),
            0,
            0L,
        )
        player.prepare()
        player.pause()
    }

    override fun preloadSingle(single: Single) {
        if (player.isPlaying || player.mediaItemCount > 0) return
        player.setMediaItems(listOf(MediaItem.fromUri(single.audioUrl)), 0, 0L)
        player.prepare()
        player.pause()
    }

    override fun toggle() {
        if (player.isPlaying) {
            player.pause()
        } else {
            startPlayback()
        }
    }

    /**
     * Play with immediate UI feedback. Exo reports BUFFERING/READY async, so
     * [publish] alone leaves a dead frame on slow networks; the optimistic
     * flag covers the gap until the next player callback corrects it.
     */
    private fun startPlayback() {
        ensureForegroundService()
        player.play()
        publish()
        if (!player.isPlaying) _state.update { it.copy(isBuffering = true) }
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
        publish()
    }
    override fun ensureForegroundService() {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ContextCompat.startForegroundService(
            appContext,
            Intent(appContext, PlaybackService::class.java),
        )
    }
    override fun seekBy(deltaMs: Long) {
        player.seekTo((player.currentPosition + deltaMs).coerceAtLeast(0L))
        publish()
    }

    private fun publish() {
        val c = course
        val s = single
        val index = if (c != null) currentUnitIndex else 0
        _state.update {
            it.copy(
                courseId = c?.id ?: s?.id,
                courseTitle = c?.title ?: s?.title ?: "",
                unit = c?.units?.getOrNull(index),
                single = s,
                unitIndex = index,
                unitCount = c?.units?.size ?: if (s != null) 1 else 0,
                isPlaying = player.isPlaying,
                isBuffering = !player.isPlaying && player.playbackState == Player.STATE_BUFFERING,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = player.duration.takeIf { d -> d != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L,
            )
        }
    }

    private fun updatePolling() {
        pollJob?.cancel()
        if (!player.isPlaying) return
        pollJob = scope.launch {
            while (isActive) {
                delay(500.milliseconds)
                publish()
                if (!player.isPlaying) break
            }
        }
    }
}
