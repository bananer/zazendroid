package de.bananer.zazendroid.data.playback

import kotlin.math.abs
import kotlin.math.max
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeMeditationAudioTest {

    @Test
    fun `gong render is deterministic`() {
        assertArrayEquals(Gong.render(), Gong.render())
    }

    @Test
    fun `gong decays to near silence at the end`() {
        val gong = Gong.render()
        val peak = gong.maxOf { abs(it.toInt()) }
        assertTrue("peak $peak too quiet", peak > 5_000)
        val tailPeak = gong.takeLast(Gong.SAMPLE_RATE / 2).maxOf { abs(it.toInt()) }
        assertTrue("tail $tailPeak not decayed", tailPeak < peak / 10)
    }

    @Test
    fun `gong starts with attack ramp and stays in bounds`() {
        val gong = Gong.render()
        assertEquals(Gong.frameCount, gong.size)
        assertTrue("attack not silent at frame 0", abs(gong[0].toInt()) < 2_000)
        gong.forEach { assertTrue(abs(it.toInt()) <= Short.MAX_VALUE.toInt()) }
    }

    @Test
    fun `gong matches the reference ring length`() {
        assertEquals(32.0, Gong.DURATION_SECONDS, 0.0)
        assertEquals(32 * Gong.SAMPLE_RATE, Gong.frameCount)
        // The original still rings well past 15 s; a 4 s strike would be silent.
        val ring = Gong.samples().copyOfRange(15 * Gong.SAMPLE_RATE, 16 * Gong.SAMPLE_RATE)
        assertTrue("gong is silent at 15 s", ring.maxOf { abs(it.toInt()) } > 100)
    }

    @Test
    fun `gong rings at the reference pitch`() {
        // After ~5 s the FM index is negligible, leaving the carrier plus the
        // 5.3 Hz tremolo. Count zero crossings over one second as a cheap pitch
        // check without pulling an FFT into the JVM test.
        val gong = Gong.samples()
        val start = 5 * Gong.SAMPLE_RATE
        val end = 6 * Gong.SAMPLE_RATE
        var crossings = 0
        for (i in start + 1 until end) {
            if ((gong[i - 1] < 0) != (gong[i] < 0)) crossings++
        }
        assertEquals(Gong.PITCH_HZ * 2, crossings.toDouble(), 2.0)
    }

    @Test
    fun `glitch bursts are localized and stay below the gong peak`() {
        val withGlitches = Gong.render(withGlitches = true)
        val clean = Gong.render(withGlitches = false)

        // First burst: 0.42-0.44 s. It must actually alter the samples.
        val burstStart = (0.42 * Gong.SAMPLE_RATE).toInt()
        val burstEnd = (0.44 * Gong.SAMPLE_RATE).toInt()
        assertTrue(
            "glitch burst missing",
            (burstStart until burstEnd).any { withGlitches[it] != clean[it] },
        )

        // 2.0-2.1 s is between bursts: the carrier must be untouched.
        val cleanStart = (2.0 * Gong.SAMPLE_RATE).toInt()
        val cleanEnd = (2.1 * Gong.SAMPLE_RATE).toInt()
        assertTrue(
            "clean window altered",
            (cleanStart until cleanEnd).all { withGlitches[it] == clean[it] },
        )

        // 0.22 peak + 0.0035 glitch = 0.2235; leave a little headroom.
        val bound = (0.225 * Short.MAX_VALUE).toInt()
        assertTrue("glitch exceeds expected peak", withGlitches.maxOf { abs(it.toInt()) } <= bound)
    }

    @Test
    fun `score renders gong at head silence in middle gong at tail`() {
        val gong = ShortArray(1_000) { (it % 101).toShort() }
        val score = FreeScore(totalFrames = 100_000L, gong = gong)
        assertEquals(gong.size.toLong(), score.gongFrames)

        val out = ShortArray(1_500)
        // Head: exact gong prefix.
        score.renderInto(0, out.size, out)
        gong.forEachIndexed { i, s -> assertEquals("head frame $i", s, out[i]) }

        // Middle: silence.
        score.renderInto(60_000, out.size, out)
        out.forEachIndexed { i, s -> assertEquals("middle frame $i", 0, s.toInt()) }

        // Tail: exact gong suffix.
        val tailStart = 100_000L - gong.size
        score.renderInto(tailStart - 500, out.size, out)
        for (i in out.indices) {
            val f = tailStart - 500L + i
            val expected = if (f >= tailStart) gong[(f - tailStart).toInt()] else 0
            assertEquals("tail frame $f", expected, out[i])
        }
    }

    @Test
    fun `score clamps rendering past total frames`() {
        val gong = ShortArray(1_000) { (it % 101).toShort() }
        val score = FreeScore(totalFrames = 100_000L, gong = gong)
        val out = ShortArray(1_000)
        score.renderInto(100_000L - 500, out.size, out)
        // Only the 500 frames before the end are written; the rest stays untouched (0).
        out.drop(500).forEach { assertEquals(0, it.toInt()) }
        assertEquals(gong[0], out[500])
    }

    @Test
    fun `score rejects too short meditations`() {
        val gong = ShortArray(1_000) { (it % 101).toShort() }
        val tooShort = 2L * gong.size
        try {
            FreeScore(tooShort, gong)
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
        }
    }

    @Test
    fun `longest session frames fit audio track head position range`() {
        // AudioTrack playback head is a uint32 frame counter; the maximum
        // session (120 min) must not wrap it.
        val maxFrames = 120L * 60 * Gong.SAMPLE_RATE
        assertTrue(maxFrames < Int.MAX_VALUE.toLong() * 2)
        val score = FreeScore(maxFrames, Gong.samples())
        assertEquals(maxFrames, score.totalFrames)
        val peak = max(1, Gong.samples().maxOf { abs(it.toInt()) })
        assertTrue(peak > 0)
    }

    @Test
    fun `seeks inside gong windows snap so gongs play in full`() {
        val gong = ShortArray(1_000) { (it % 101).toShort() }
        val score = FreeScore(totalFrames = 100_000L, gong = gong)
        fun ms(frames: Long) = frames * 1000L / Gong.SAMPLE_RATE

        // Head window snaps to 0.
        assertEquals(0L, score.seekFrame(0L))
        assertEquals(0L, score.seekFrame(ms(999L)))
        // Silence is exact.
        assertEquals(60_000L, score.seekFrame(ms(60_000L)))
        // Tail window, max-length and overshoot seeks snap to the gong's start.
        val tailStart = 99_000L
        assertEquals(tailStart, score.seekFrame(ms(99_500L)))
        assertEquals(tailStart, score.seekFrame(ms(100_000L)))
        assertEquals(tailStart, score.seekFrame(ms(100_000L) * 2))
    }
}
