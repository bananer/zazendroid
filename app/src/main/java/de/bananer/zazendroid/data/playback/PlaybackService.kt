package de.bananer.zazendroid.data.playback

import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import de.bananer.zazendroid.ZazenApp

/**
 * Keeps playback alive in background with a system notification (Media3 default
 * provider). Player itself is owned by the app-scoped [ExoPlaybackManager];
 * this service only attaches it to a session.
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val manager = (application as ZazenApp).container.playbackManager
        val player = (manager as? ExoPlaybackManager)?.player
        if (player != null) {
            session = MediaSession.Builder(this, player).build()
            // MediaSessionService only foregrounds on player transitions it
            // observes. If playback started before this session attached, that
            // transition is missed and the system kills us for never calling
            // startForeground — so force one deterministically.
            if (player.playbackState != Player.STATE_IDLE) {
                player.pause()
                player.play()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }
}
