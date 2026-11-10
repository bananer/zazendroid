package de.bananer.zazendroid.data.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import de.bananer.zazendroid.MainActivity
import de.bananer.zazendroid.R
import de.bananer.zazendroid.ZazenApp

/**
 * Media3 session service: owns the [MediaSession] around whichever player is
 * active — the app-scoped ExoPlayer for catalog content, the synthesizing
 * [FreeMeditationPlayer] for free meditation — so the system renders playback
 * in the QS media carousel, lockscreen and Android Auto. Player swaps go
 * through [ExoPlaybackManager.onActivePlayerChanged] → `MediaSession.setPlayer`,
 * keeping one notification and foreground state across the handover. Paused
 * state keeps a resumable card
 * (`SHOW_NOTIFICATION_FOR_IDLE_PLAYER_AFTER_STOP_OR_ERROR`); never foregrounds
 * while idle, so a stray notification can't appear without playback. Killing
 * a paused-away task stops the service
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
        // One session around whichever player is active: ExoPlayer for catalog
        // content, FreeMeditationPlayer for free meditation. setPlayer keeps the
        // notification and foreground state across the swap.
        manager.onActivePlayerChanged = { player -> session?.setPlayer(player) }
        // The default notification provider uses MediaSession.sessionActivity
        // as the notification's content intent; without one the card is not
        // tappable. Route the tap to MainActivity, which opens the player.
        val openPlayer = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_PLAYER),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        session = MediaSession.Builder(this, manager.activePlayer)
            .setSessionActivity(openPlayer)
            .build()
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
        // onCreate checked the type; null the callback so the app-scoped
        // manager doesn't keep a reference to this destroyed service.
        ((application as ZazenApp).container.playbackManager as ExoPlaybackManager)
            .onActivePlayerChanged = null
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
