package de.bananer.zazendroid.ui.screen

import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Test

class MoodRingTest {
    @Test
    fun `top is zero`() {
        assertEquals(0, MoodRing.angleToValue(0f, -10f))
    }

    @Test
    fun `right is 25`() {
        assertEquals(25, MoodRing.angleToValue(10f, 0f))
    }

    @Test
    fun `bottom is 50`() {
        assertEquals(50, MoodRing.angleToValue(0f, 10f))
    }

    @Test
    fun `left is 75`() {
        assertEquals(75, MoodRing.angleToValue(-10f, 0f))
    }

    @Test
    fun `value round trip at cardinal points`() {
        assertEquals(0.0, MoodRing.valueToAngle(0), 1e-9)
        assertEquals(PI / 2, MoodRing.valueToAngle(25), 1e-9)
        assertEquals(PI, MoodRing.valueToAngle(50), 1e-9)
    }
}
