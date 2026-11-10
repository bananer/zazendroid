package de.bananer.zazendroid.data.playback

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sin

/**
 * Pure PCM synthesis for free meditation. No audio files: the gong is rendered
 * into samples at runtime, everything between the gongs is silence.
 */
internal object Gong {
    const val SAMPLE_RATE = 48_000

    /**
     * The reference recording's gong starts ~31.8 s before the end and rings
     * out to it; 32 s keeps that length and places the tail strike at 4:28 of a
     * 5-minute session, like the original.
     */
    const val DURATION_SECONDS = 32.0

    /** Dominant mode of the reference gong, measured at 598.25 Hz. */
    internal const val PITCH_HZ = 598.25

    // Deliberately not the recording's acoustic partial stack: two-operator
    // phase modulation at a 2:1 ratio gives a glassy, hollow digital bell, and
    // the index collapses fast so the long tail is a pure carrier.
    private const val MODULATOR_RATIO = 2.0
    private const val MOD_INDEX = 1.5
    private const val MOD_DECAY_SECONDS = 1.2

    // The recording's 592.9/598.25 doublet beats at 5.3 Hz; a digital LFO
    // reproduces that pulse without imitating the two acoustic modes.
    private const val TREMOLO_HZ = 5.3
    private const val TREMOLO_DEPTH = 0.15

    /**
     * Measured T60 of the reference's 598 Hz mode (37.3 s), not the 32 s
     * buffer end: a 32 s T60 measurably under-runs the recording's tail by
     * 4-5 dB at 15-20 s, which reads as silence on small speakers.
     */
    private const val T60_SECONDS = 37.0
    private const val ATTACK_SECONDS = 0.005

    /** Same peak as the reference strike (~-13 dBFS); a 32 s ring at the old
     *  0.55 peak would be over 11 dB louder on average than the recording. */
    private const val PEAK = 0.22
    private const val TWO_PI = 2.0 * PI

    /**
     * Sparse digital glitches: (start s, duration s, peak amplitude). These are
     * added after normalisation, so levels are absolute. Short sample-and-hold
     * noise bursts, all below -49 dBFS peak, giving the synthetic strike a bit
     * of digital texture without turning it into a noise effect.
     */
    private data class Glitch(val start: Double, val duration: Double, val level: Double)

    private val GLITCHES = arrayOf(
        Glitch(0.42, 0.020, 0.0035),
        Glitch(1.85, 0.012, 0.0025),
        Glitch(4.60, 0.030, 0.0035),
        Glitch(8.30, 0.016, 0.0030),
        Glitch(13.40, 0.024, 0.0025),
        Glitch(22.00, 0.018, 0.0012),
    )

    private const val GLITCH_HOLD_FRAMES = 16

    val frameCount: Int = (SAMPLE_RATE * DURATION_SECONDS).toInt()

    private val cached: ShortArray by lazy { render() }

    /** One strike, mono 16-bit at [SAMPLE_RATE]; shared immutable buffer. */
    fun samples(): ShortArray = cached

    /** Deterministic xorshift step; keeps the glitch layer reproducible. */
    private fun noiseStep(state: Int): Int {
        var x = state
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        return x
    }

    /** Renders one strike as mono 16-bit samples at [SAMPLE_RATE]. Deterministic. */
    fun render(): ShortArray = render(withGlitches = true)

    /** [withGlitches] is a test seam; production always includes the texture. */
    internal fun render(withGlitches: Boolean): ShortArray {
        val totalFrames = frameCount
        val raw = DoubleArray(totalFrames)
        // exp(-ln(1000) * t / T60) is exactly -60 dB at t = T60.
        val decay = ln(1000.0) / T60_SECONDS
        val carrierOmega = TWO_PI * PITCH_HZ
        val modulatorOmega = carrierOmega * MODULATOR_RATIO
        val tremoloOmega = TWO_PI * TREMOLO_HZ
        for (i in 0 until totalFrames) {
            val t = i.toDouble() / SAMPLE_RATE
            val attack = min(1.0, t / ATTACK_SECONDS)
            val envelope = exp(-decay * t)
            val index = MOD_INDEX * exp(-t / MOD_DECAY_SECONDS)
            val tremolo = 1.0 + TREMOLO_DEPTH * sin(tremoloOmega * t)
            val phase = carrierOmega * t + index * sin(modulatorOmega * t)
            raw[i] = envelope * attack * tremolo * sin(phase)
        }
        var peak = 0.0
        for (i in 0 until totalFrames) {
            val a = abs(raw[i])
            if (a > peak) peak = a
        }
        val out = ShortArray(totalFrames)
        if (peak > 0.0) {
            val scale = PEAK / peak
            var noiseState = 0x1F123BB5
            var heldNoise = 0.0
            var hold = 0
            for (i in 0 until totalFrames) {
                val t = i.toDouble() / SAMPLE_RATE
                if (hold == 0) {
                    noiseState = noiseStep(noiseState)
                    heldNoise = noiseState / 2_147_483_648.0
                    hold = GLITCH_HOLD_FRAMES
                }
                hold--
                var gate = 0.0
                for (g in GLITCHES) {
                    val phase = (t - g.start) / g.duration
                    if (phase > 0.0 && phase < 1.0) {
                        val level = sin(PI * phase) * g.level
                        if (level > gate) gate = level
                    }
                }
                val v = raw[i] * scale + if (withGlitches) heldNoise * gate else 0.0
                out[i] = (v * Short.MAX_VALUE).toInt().toShort()
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
