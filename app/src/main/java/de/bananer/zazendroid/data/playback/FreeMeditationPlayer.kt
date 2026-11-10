package de.bananer.zazendroid.data.playback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlin.concurrent.thread
import kotlin.math.min

/**
 * Synthesizing player for free meditation: streams silence with a gong at the
 * start and the end to an [AudioTrack] (MODE_STREAM, no files, no codecs).
 *
 * Implements [Player] via [SimpleBasePlayer] so [PlaybackService] can host it
 * in the same [androidx.media3.session.MediaSession] as the ExoPlayer
 * (`MediaSession.setPlayer`), keeping one media notification, foreground
 * service, lockscreen/QS rendering — identical to catalog playback.
 *
 * Threading: all [Player] methods and state fields live on the main looper
 * (SimpleBasePlayer contract). The writer thread only touches [track], the
 * clock lock, and its own locals; it posts end-of-track and errors to the
 * main handler.
 */
internal class FreeMeditationPlayer(
    private val onStateChanged: () -> Unit,
    private val onError: () -> Unit,
) : SimpleBasePlayer(Looper.getMainLooper()) {

    private val mainHandler = Handler(Looper.getMainLooper())

    // Main-thread state.
    private var minutes: Int = 0
    private var title: String = ""
    private var artist: String = ""
    private var score: FreeScore? = null
    private var playbackState: Int = Player.STATE_IDLE
    private var playWhenReady: Boolean = false

    // Clock, guarded by [clockLock]; readable from the writer thread.
    private val clockLock = Any()
    private var clockBaseMs: Long = 0L
    private var clockRealMs: Long = 0L
    private var clockRunning: Boolean = false

    // Writer-thread coordination. [audioActive] is the writer's stop signal.
    // [track] is built by the writer and torn down from the main thread; both
    // are volatile because the two threads race during stop/seek.
    @Volatile private var audioActive: Boolean = false
    @Volatile private var track: AudioTrack? = null
    @Volatile private var writer: Thread? = null

    /** Loads a session of [minutes] length, prepared and paused. */
    fun start(minutes: Int, title: String, artist: String) {
        verifyApplicationThread()
        stopAudio()
        this.minutes = minutes
        this.title = title
        this.artist = artist
        score = FreeScore(minutes * 60L * Gong.SAMPLE_RATE, Gong.samples())
        playbackState = Player.STATE_READY
        playWhenReady = false
        setClock(0L, running = false)
        invalidateState()
    }

    /** Drops the session entirely (catalog playback took over). */
    fun reset() {
        verifyApplicationThread()
        stopAudio()
        minutes = 0
        title = ""
        artist = ""
        score = null
        playbackState = Player.STATE_IDLE
        playWhenReady = false
        setClock(0L, running = false)
        invalidateState()
    }

    // --- State assembly ----------------------------------------------------

    // The GET_* commands are what make the media notification work: the
    // session hands controllers a timeline only if COMMAND_GET_TIMELINE or
    // COMMAND_GET_CURRENT_MEDIA_ITEM is available, and an empty timeline makes
    // MediaNotificationManager suppress the notification entirely.
    // COMMAND_GET_METADATA supplies the notification title; COMMAND_STOP gives
    // swipe-to-dismiss parity with ExoPlayer.
    private val commands: Player.Commands = Player.Commands.Builder()
        .addAll(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_STOP,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_SEEK_BACK,
            Player.COMMAND_SEEK_FORWARD,
            Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_GET_METADATA,
        )
        .build()

    override fun getState(): State {
        val s = score
        if (s == null || minutes == 0) {
            return State.Builder()
                .setAvailableCommands(commands)
                .setPlaylist(emptyList())
                .setPlaybackState(Player.STATE_IDLE)
                .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .build()
        }
        val durationUs = s.totalFrames * 1_000_000L / Gong.SAMPLE_RATE
        val item = MediaItemData.Builder(SESSION_UID)
            .setMediaItem(
                MediaItem.Builder()
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(title)
                            .setArtist(artist)
                            .build(),
                    )
                    .build(),
            )
            .setDurationUs(durationUs)
            .setIsSeekable(true)
            .build()
        return State.Builder()
            .setAvailableCommands(commands)
            .setPlaylist(listOf(item))
            .setPlaybackState(playbackState)
            .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setContentPositionMs { currentPositionMs() }
            .setContentBufferedPositionMs { currentPositionMs() }
            .setTotalBufferedDurationMs { 0L }
            .build()
    }

    // --- Clock -------------------------------------------------------------

    private fun setClock(baseMs: Long, running: Boolean) {
        synchronized(clockLock) {
            clockBaseMs = baseMs
            clockRealMs = SystemClock.elapsedRealtime()
            clockRunning = running
        }
    }

    private fun totalMs(): Long = minutes * 60_000L

    fun currentPositionMs(): Long {
        val raw = synchronized(clockLock) {
            if (clockRunning) clockBaseMs + (SystemClock.elapsedRealtime() - clockRealMs) else clockBaseMs
        }
        return raw.coerceIn(0L, totalMs())
    }

    private fun clockFrame(): Long = currentPositionMs() * Gong.SAMPLE_RATE / 1000L

    // --- Player command handling -------------------------------------------

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (score == null) {
            this.playWhenReady = playWhenReady
            return Futures.immediateVoidFuture()
        }
        val ended = playbackState == Player.STATE_ENDED
        this.playWhenReady = playWhenReady
        playbackState = Player.STATE_READY
        if (playWhenReady) {
            // ExoPlayer restarts from the default position after ENDED; mirror that.
            if (ended) setClock(0L, running = true)
            else setClock(currentPositionMs(), running = true)
            if (ended) startAudio(0L) else resumeOrStartAudio()
        } else {
            setClock(currentPositionMs(), running = false)
            track?.pause()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        val s = score ?: return Futures.immediateVoidFuture()
        val frame = s.seekFrame(positionMs)
        setClock(frame * 1000L / Gong.SAMPLE_RATE, running = playWhenReady)
        // A seek out of ENDED must resume from the sought position; a bare
        // play() after ENDED still restarts at frame 0, mirroring ExoPlayer.
        if (playbackState == Player.STATE_ENDED) playbackState = Player.STATE_READY
        // Always restart the audio chain at the new frame; a parked writer would
        // otherwise keep its pre-seek position.
        stopAudio()
        if (playWhenReady && playbackState != Player.STATE_IDLE) startAudio(frame)
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleStop(): ListenableFuture<*> {
        // IDLE, like ExoPlayer.stop(): the notification manager hides the card
        // of a dismissed idle player, but would re-post it for a READY player —
        // resurrecting the notification the user just swiped away.
        stopAudio()
        playWhenReady = false
        setClock(0L, running = false)
        playbackState = Player.STATE_IDLE
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        stopAudio()
        return Futures.immediateVoidFuture()
    }

    // --- Audio chain ---------------------------------------------------------

    private fun resumeOrStartAudio() {
        val t = track
        if (t != null && audioActive) {
            t.play()
        } else {
            startAudio(clockFrame())
        }
    }

    private fun startAudio(fromFrame: Long) {
        stopAudio()
        val s = score ?: return
        audioActive = true
        writer = thread(name = "free-meditation-audio") { audioLoop(s, fromFrame) }
    }

    private fun audioLoop(s: FreeScore, fromFrame: Long) {
        val t = try {
            buildTrack().also { track = it; it.play() }
        } catch (e: Exception) {
            audioActive = false
            mainHandler.post { onError() }
            return
        }
        // Frames THIS track instance will play: every seek builds a fresh
        // AudioTrack whose playback head starts at 0, so the drain must
        // compare track-relative, not against absolute score frames.
        val trackEnd = s.totalFrames - fromFrame
        val buf = ShortArray(CHUNK_FRAMES)
        var frame = fromFrame
        try {
            while (frame < s.totalFrames) {
                if (!audioActive) return
                val count = min(CHUNK_FRAMES.toLong(), s.totalFrames - frame).toInt()
                s.renderInto(frame, count, buf)
                val written = t.write(buf, 0, count, AudioTrack.WRITE_BLOCKING)
                if (written < 0) throw IllegalStateException("AudioTrack.write failed: $written")
                // write(short[]) returns SHORTS written, and mono 16-bit PCM
                // is one short per frame. The old `written / 2` treated the
                // return as bytes: the frame cursor advanced at half the real
                // write rate, every chunk re-wrote its second half, and a
                // 5-minute session produced 10 minutes of audio — the end
                // gong landed long after the clock hit the end, and ENDED
                // only fired once the doubled stream drained.
                frame += written
            }
            // All audio queued: wait until the track has actually played it.
            // Waiting on the playback head (not the clock) avoids cutting the
            // tail gong: after the last write returns, hundreds of ms of audio
            // can still sit in the track buffer. Pausing freezes the head, so
            // the loop parks until resumed; only completion or a stop/seek
            // (which releases the track) exits it.
            while (audioActive && t.playbackHeadPosition < trackEnd) {
                Thread.sleep(50)
            }
            if (audioActive) {
                mainHandler.post {
                    playbackState = Player.STATE_ENDED
                    setClock(totalMs(), running = false)
                    invalidateState()
                    onStateChanged()
                }
            }
        } catch (e: Exception) {
            // A stop/seek released the track under us (audioActive is already
            // false): swallowed. A genuine mid-session failure reports.
            if (audioActive) mainHandler.post { onError() }
        } finally {
            if (audioActive) audioActive = false
            try {
                t.pause()
                t.flush()
                t.release()
            } catch (e: Exception) {
                // Track already dead; nothing to clean up.
            }
            if (Thread.currentThread() === writer) writer = null
        }
    }

    private fun buildTrack(): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(
            Gong.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minBuf > 0) { "AudioTrack.getMinBufferSize failed: $minBuf" }
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(Gong.SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minBuf * 4, CHUNK_FRAMES * 2 * 8))
            .build()
    }

    /** Stops the writer thread and tears down its track. Idempotent. */
    private fun stopAudio() {
        audioActive = false // writer exits on its next check; flush unblocks write()
        val t = track
        if (t != null) {
            try {
                t.pause()
                t.flush()
                t.release()
            } catch (e: Exception) {
                // Track already dead.
            }
        }
        // Always join: a writer that has not stored its track yet must still
        // die before the next session starts its own.
        writer?.takeIf { it !== Thread.currentThread() }?.join()
        writer = null
        track = null
    }

    companion object {
        private const val CHUNK_FRAMES = 4096
        private val SESSION_UID = Any()
    }
}
