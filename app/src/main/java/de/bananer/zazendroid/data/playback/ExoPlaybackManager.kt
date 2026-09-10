package de.bananer.zazendroid.data.playback

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import de.bananer.zazendroid.data.catalog.Course
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
    private var pollJob: Job? = null

    val player: ExoPlayer by lazy {
        ExoPlayer.Builder(appContext).build().also { exo ->
            exo.addListener(listener)
        }
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                // currentMediaItemIndex already points at the new item; the one
                // we left finished playing.
                course?.let { c ->
                    val prev = player.currentMediaItemIndex - 1
                    if (prev >= 0) scope.launch { progress.markCompleted(c.id, prev) }
                }
            }
            publish()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                course?.let { c ->
                    val last = player.currentMediaItemIndex
                    scope.launch { progress.markCompleted(c.id, last) }
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
            _state.update { it.copy(error = "Cannot play audio", isPlaying = false) }
        }
    }

    override fun play(course: Course, startIndex: Int) {
        require(course.units.isNotEmpty()) { "course has no units" }
        this.course = course
        _state.update { it.copy(error = null) }
        player.setMediaItems(
            course.units.map { MediaItem.fromUri(it.audioUrl) },
            startIndex.coerceIn(course.units.indices),
            0L,
        )
        player.prepare()
        player.play()
        ensureForegroundService()
        publish()
        updatePolling()
    }

    override fun toggle() {
        if (player.isPlaying) player.pause() else player.play()
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
        publish()
    }

    override fun ensureForegroundService() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
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

    override fun next() {
        if (player.hasNextMediaItem()) player.seekToNextMediaItem()
    }

    override fun previous() {
        if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
    }

    private fun publish() {
        val c = course
        val index = if (player.mediaItemCount > 0) player.currentMediaItemIndex else 0
        _state.update {
            it.copy(
                courseId = c?.id,
                courseTitle = c?.title ?: "",
                unit = c?.units?.getOrNull(index),
                unitIndex = index,
                unitCount = c?.units?.size ?: 0,
                isPlaying = player.isPlaying,
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
                delay(500)
                publish()
                if (!player.isPlaying) break
            }
        }
    }
}
