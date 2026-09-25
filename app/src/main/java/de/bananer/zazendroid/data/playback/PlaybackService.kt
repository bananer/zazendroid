package de.bananer.zazendroid.data.playback

import android.content.Intent
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import de.bananer.zazendroid.R
import de.bananer.zazendroid.ZazenApp

/**
 * Media3 session service: owns the [MediaSession] around the app-scoped
 * ExoPlayer so the system renders playback in the QS media carousel,
 * lockscreen and Android Auto. Paused state keeps a resumable card
 * (`SHOW_NOTIFICATION_FOR_IDLE_PLAYER_AFTER_STOP_OR_ERROR`); never
 * foregrounds while idle, so a stray notification can't appear without
 * playback. Killing a paused-away task stops the service
 * ([onTaskRemoved] → `pauseAllPlayersAndStopSelf`).
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val manager = (application as ZazenApp).container.playbackManager
        check(manager is ExoPlaybackManager) { "PlaybackService needs ExoPlaybackManager.player" }
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(
                this,
                { NOTIFICATION_ID },
                CHANNEL_ID,
                R.string.notif_channel_playback,
            ).apply { setSmallIcon(R.drawable.ic_launcher_monochrome) },
        )
        session = MediaSession.Builder(this, manager.player).build()
        addSession(session!!)
        setShowNotificationForIdlePlayer(SHOW_NOTIFICATION_FOR_IDLE_PLAYER_AFTER_STOP_OR_ERROR)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.isPlaying) {
            pauseAllPlayersAndStopSelf()
        }
    }

    override fun onDestroy() {
        session?.let {
            removeSession(it)
            it.release()
        }
        session = null
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "playback"
        const val NOTIFICATION_ID = 1001
    }
}
