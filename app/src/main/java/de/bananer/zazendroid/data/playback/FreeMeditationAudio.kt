package de.bananer.zazendroid.data.playback

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Pure PCM synthesis for free meditation. No audio files: the gong is rendered
 * into samples at runtime, everything between the gongs is silence.
 */
internal object Gong {
    const val SAMPLE_RATE = 48_000
    const val DURATION_SECONDS = 4.0

    /** Inharmonic partials (frequency Hz, relative amplitude, decay per second). */
    private val PARTIALS = arrayOf(
        Triple(98.0, 1.00, 1.2),
        Triple(164.8, 0.55, 1.9),
        Triple(232.0, 0.40, 2.6),
        Triple(341.0, 0.30, 3.4),
        Triple(526.0, 0.20, 4.4),
        Triple(812.0, 0.12, 5.6),
    )

    private const val ATTACK_SECONDS = 0.02
    private const val PEAK = 0.55

    val frameCount: Int = (SAMPLE_RATE * DURATION_SECONDS).toInt()

    private val cached: ShortArray by lazy { render() }

    /** One strike, mono 16-bit at [SAMPLE_RATE]; shared immutable buffer. */
    fun samples(): ShortArray = cached

    /** Renders one strike as mono 16-bit samples at [SAMPLE_RATE]. Deterministic. */
    fun render(): ShortArray {
        val totalFrames = frameCount
        val raw = DoubleArray(totalFrames)
        for ((freq, amp, decay) in PARTIALS) {
            val w = 2.0 * PI * freq / SAMPLE_RATE
            for (i in 0 until totalFrames) {
                val t = i.toDouble() / SAMPLE_RATE
                val attack = min(1.0, t / ATTACK_SECONDS)
                raw[i] += amp * exp(-decay * t) * attack * sin(w * i)
            }
        }
        var peak = 0.0
        for (i in 0 until totalFrames) {
            raw[i] = tanh(raw[i])
            val a = kotlin.math.abs(raw[i])
            if (a > peak) peak = a
        }
        val out = ShortArray(totalFrames)
        if (peak > 0.0) {
            val scale = PEAK / peak
            for (i in 0 until totalFrames) {
                out[i] = (raw[i] * scale * Short.MAX_VALUE).toInt().toShort()
            }
        }
        return out
    }
}

/**
 * Frame-level schedule of one free meditation: gong at the head, silence,
 * gong at the tail. [renderInto] fills [out] for any in-bounds frame window.
 */
internal class FreeScore(val totalFrames: Long, private val gong: ShortArray) {

    init {
        require(totalFrames > 2L * gong.size) { "meditation too short for two gongs" }
    }

    val gongFrames: Long = gong.size.toLong()

    fun renderInto(startFrame: Long, count: Int, out: ShortArray) {
        require(startFrame >= 0)
        require(out.size >= count)
        val n = min(count.toLong(), totalFrames - startFrame).toInt()
        val headEnd = gongFrames
        val tailStart = totalFrames - gongFrames
        for (i in 0 until n) {
            val f = startFrame + i
            out[i] = when {
                f < headEnd -> gong[f.toInt()]
                f >= tailStart -> gong[(f - tailStart).toInt()]
                else -> 0
            }
        }
    }

    /**
     * Seek target in frames for a seek to [positionMs]: positions landing
     * inside a gong window snap to that gong's start so the strike is always
     * heard — landing mid-decay, or on the gong's final frame at a max-length
     * seek, sounds like the gong is missing entirely.
     */
    fun seekFrame(positionMs: Long): Long {
        val raw = (positionMs * Gong.SAMPLE_RATE / 1000L).coerceIn(0L, totalFrames - 1)
        return when {
            raw >= totalFrames - gongFrames -> totalFrames - gongFrames
            raw < gongFrames -> 0L
            else -> raw
        }
    }
}
