package de.bananer.zazendroid.data.playback

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import de.bananer.zazendroid.R
import de.bananer.zazendroid.data.catalog.Course
import de.bananer.zazendroid.data.catalog.Single
import de.bananer.zazendroid.data.progress.ProgressRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
 * ExoPlayer-backed [PlaybackManager] for catalog content, plus a
 * [FreeMeditationPlayer] for locally synthesized gong sessions.
 * [PlaybackService] hosts whichever player is active (see
 * [onActivePlayerChanged]/[activePlayer]) in the media session for the system
 * media carousel. UI observes [state] only.
 */
class ExoPlaybackManager(
    private val appContext: Context,
    private val progress: ProgressRepository,
    private val scope: CoroutineScope,
) : PlaybackManager {

    init {
        // Gong.samples() renders ~1.2M samples on first use (tens of ms on the
        // main thread); prewarm in the background so the first free-meditation
        // start doesn't jank. The lazy is synchronized, so a racing start waits.
        scope.launch(Dispatchers.Default) { Gong.samples() }
    }

    private val _state = MutableStateFlow(PlaybackUiState())
    override val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private var course: Course? = null
    private var single: Single? = null
    private var currentUnitIndex: Int = 0
    private var pollJob: Job? = null
    private var freeMinutes: Int? = null
    private var freeTitle: String = ""

    /** Notified on the main thread whenever the session's active player must change. */
    var onActivePlayerChanged: ((Player) -> Unit)? = null

    private val freePlayer: FreeMeditationPlayer by lazy {
        FreeMeditationPlayer(
            onStateChanged = { publish() },
            onError = {
                _state.update { it.copy(error = PlaybackError.AUDIO_UNAVAILABLE, isPlaying = false, isBuffering = false) }
            },
        )
    }

    /** The player the media session must currently expose. */
    val activePlayer: Player get() = if (freeMinutes != null) freePlayer else player

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
        deactivateFreeMeditation()
        this.course = course
        this.single = null
        this.currentUnitIndex = startIndex.coerceIn(course.units.indices)
        _state.update { it.copy(error = null) }
        val unit = course.units[currentUnitIndex]
        player.setMediaItems(
            listOf(
                MediaItem.Builder()
                    .setUri(unit.audioUrl)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(unit.title)
                            .setArtist(course.title)
                            .setAlbumTitle(course.title)
                            .build(),
                    )
                    .build(),
            ),
            0,
            0L,
        )
        player.prepare()
        player.pause()
        publish()
        updatePolling()
    }

    override fun playFreeMeditation(minutes: Int, title: String) {
        course = null
        single = null
        _state.update { it.copy(error = null) }
        if (freeMinutes == null) {
            // Switching from catalog playback: idle the Exo chain, hand the
            // session to the synthesizing player before audio starts.
            player.pause()
        }
        freeMinutes = minutes
        freeTitle = title
        onActivePlayerChanged?.invoke(activePlayer)
        freePlayer.start(minutes, title, artist = appContext.getString(R.string.app_name))
        startPlayback()
    }

    private fun deactivateFreeMeditation() {
        if (freeMinutes == null) return
        freeMinutes = null
        freeTitle = ""
        freePlayer.reset()
        onActivePlayerChanged?.invoke(player)
    }

    override fun playSingle(single: Single) {
        queueSingle(single)
        startPlayback()
    }

    override fun queueSingle(single: Single) {
        deactivateFreeMeditation()
        this.course = null
        this.single = single
        _state.update { it.copy(error = null) }
        player.setMediaItems(
            listOf(
                MediaItem.Builder()
                    .setUri(single.audioUrl)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(single.title)
                            .setArtist(single.authorName)
                            .build(),
                    )
                    .build(),
            ),
            0,
            0L,
        )
        player.prepare()
        player.pause()
        publish()
        updatePolling()
    }

    override fun preload(course: Course, startIndex: Int) {
        if (player.isPlaying || player.mediaItemCount > 0 || course.units.isEmpty()) return
        val unit = course.units[startIndex.coerceIn(course.units.indices)]
        player.setMediaItems(
            listOf(
                MediaItem.Builder()
                    .setUri(unit.audioUrl)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(unit.title)
                            .setArtist(course.title)
                            .setAlbumTitle(course.title)
                            .build(),
                    )
                    .build(),
            ),
            0,
            0L,
        )
        player.prepare()
        player.pause()
    }

    override fun preloadSingle(single: Single) {
        // Passive preload: never interrupts an active free meditation session.
        if (player.isPlaying || player.mediaItemCount > 0) return
        player.setMediaItems(
            listOf(
                MediaItem.Builder()
                    .setUri(single.audioUrl)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(single.title)
                            .setArtist(single.authorName)
                            .build(),
                    )
                    .build(),
            ),
            0,
            0L,
        )
        player.prepare()
        player.pause()
    }

    override fun toggle() {
        if (activePlayer.isPlaying) {
            activePlayer.pause()
        } else {
            startPlayback()
        }
    }

    /**
     * Play with immediate UI feedback. Exo reports BUFFERING/READY async, so
     * [publish] alone leaves a dead frame on slow networks; the optimistic
     * flag covers the gap until the next player callback corrects it.
     *
     * Also starts position polling: the free player emits no state changes
     * while playing, so the in-app progress would otherwise freeze.
     */
    private fun startPlayback() {
        ensureForegroundService()
        activePlayer.play()
        publish()
        if (!activePlayer.isPlaying) _state.update { it.copy(isBuffering = true) }
        updatePolling()
    }

    override fun seekTo(positionMs: Long) {
        activePlayer.seekTo(positionMs.coerceAtLeast(0L))
        publish()
    }

    /**
     * Starts the session service only for real playback. The session then
     * foregrounds itself while playing; idle preloads/queue never post a
     * notification, and the resumable paused card is managed by the service.
     */
    override fun ensureForegroundService() {
        ContextCompat.startForegroundService(
            appContext,
            Intent(appContext, PlaybackService::class.java),
        )
    }
    override fun seekBy(deltaMs: Long) {
        val p = activePlayer
        p.seekTo((p.currentPosition + deltaMs).coerceAtLeast(0L))
        publish()
    }

    private fun publish() {
        val c = course
        val s = single
        val fm = freeMinutes
        val index = if (c != null) currentUnitIndex else 0
        val ap = activePlayer
        _state.update {
            it.copy(
                courseId = c?.id ?: s?.id,
                courseTitle = c?.title ?: s?.title ?: freeTitle,
                unit = c?.units?.getOrNull(index),
                single = s,
                unitIndex = index,
                unitCount = c?.units?.size ?: if (s != null) 1 else 0,
                freeMinutes = fm,
                isPlaying = ap.isPlaying,
                isBuffering = fm == null && !ap.isPlaying && ap.playbackState == Player.STATE_BUFFERING,
                positionMs = ap.currentPosition.coerceAtLeast(0L),
                durationMs = ap.duration.takeIf { d -> d != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L,
            )
        }
    }

    private fun updatePolling() {
        pollJob?.cancel()
        if (!activePlayer.isPlaying) return
        pollJob = scope.launch {
            while (isActive) {
                delay(500.milliseconds)
                publish()
                if (!activePlayer.isPlaying) break
            }
        }
    }
}
