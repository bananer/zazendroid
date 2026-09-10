package de.bananer.zazendroid.data.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import de.bananer.zazendroid.MainActivity
import de.bananer.zazendroid.ZazenApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Plain foreground service: keeps audio alive in background behind an ongoing
 * notification with transport actions. We foreground explicitly in [onCreate]
 * instead of relying on `MediaSessionService` auto-foregrounding, which proved
 * timing-dependent (missed playing transition → system kill).
 */
class PlaybackService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(NOTIFICATION_ID, notificationFor(current()))
        val manager = (application as ZazenApp).container.playbackManager
        scope.launch {
            manager.state
                .map { Triple(it.unit?.id, it.unit?.title, it.isPlaying) }
                .distinctUntilChanged()
                .collect { notifyCurrent() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = (application as ZazenApp).container.playbackManager
        when (intent?.action) {
            ACTION_TOGGLE -> manager.toggle()
            ACTION_NEXT -> manager.next()
            ACTION_PREV -> manager.previous()
        }
        notifyCurrent()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun current(): PlaybackUiState =
        (application as ZazenApp).container.playbackManager.state.value

    private fun notifyCurrent() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, notificationFor(current()))
    }

    private fun notificationFor(state: PlaybackUiState): Notification {
        val content = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        fun action(act: String, label: String, icon: Int): NotificationCompat.Action =
            NotificationCompat.Action(
                icon, label,
                PendingIntent.getService(
                    this, act.hashCode(), Intent(this, PlaybackService::class.java).setAction(act),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(state.unit?.title ?: "ZazenDroid")
            .setContentText(state.courseTitle.ifEmpty { "Meditation" })
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(content)
            .setOngoing(state.isPlaying)
            .addAction(action(ACTION_PREV, "Prev", android.R.drawable.ic_media_previous))
            .addAction(
                action(
                    ACTION_TOGGLE,
                    if (state.isPlaying) "Pause" else "Play",
                    if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                ),
            )
            .addAction(action(ACTION_NEXT, "Next", android.R.drawable.ic_media_next))
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    companion object {
        const val CHANNEL_ID = "playback"
        const val NOTIFICATION_ID = 1001
        const val ACTION_TOGGLE = "de.bananer.zazendroid.TOGGLE"
        const val ACTION_NEXT = "de.bananer.zazendroid.NEXT"
        const val ACTION_PREV = "de.bananer.zazendroid.PREV"
    }
}
