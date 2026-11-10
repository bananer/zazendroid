package de.bananer.zazendroid.data.playback

import android.os.SystemClock
import android.util.Log
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device regression test for end-of-session detection: after seeking into the
 * tail gong, the player must reach STATE_ENDED once the audio drained. Catches
 * both the half-speed frame advance and the track-relative playback-head
 * comparison, neither of which is observable in pure JVM tests.
 */
@RunWith(AndroidJUnit4::class)
class FreeMeditationPlayerDeviceTest {

    @Test
    fun reachesEndedAfterTailGong() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val player = arrayOfNulls<FreeMeditationPlayer>(1)
        inst.runOnMainSync {
            player[0] = FreeMeditationPlayer(
                onStateChanged = { Log.d("FreeMeditation", "onStateChanged") },
                onError = { Log.d("FreeMeditation", "onError") },
            )
        }
        val p = player[0]!!
        inst.runOnMainSync {
            p.start(1, "DeviceTest", "Artist")
            p.play()
            p.seekTo(56_000L)
        }
        val t0 = SystemClock.elapsedRealtime()
        var state = -1
        var pos = -1L
        var playing = true
        while (SystemClock.elapsedRealtime() - t0 < 15_000L) {
            inst.runOnMainSync {
                state = p.playbackState
                pos = p.currentPosition
                playing = p.isPlaying
            }
            Log.d("FreeMeditation", "t=${SystemClock.elapsedRealtime() - t0} state=$state pos=$pos isPlaying=$playing")
            if (state == Player.STATE_ENDED) break
            SystemClock.sleep(250)
        }
        assertEquals(Player.STATE_ENDED.toLong(), state.toLong())
    }
}
