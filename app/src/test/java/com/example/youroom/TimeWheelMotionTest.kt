package com.example.youroom

import androidx.compose.animation.core.FloatExponentialDecaySpec
import com.example.youroom.ui.TimeWheelDecay
import com.example.youroom.ui.TimeWheelFriction
import com.example.youroom.ui.timeWheelCenterCrossings
import org.junit.Assert.*
import org.junit.Test

class TimeWheelMotionTest {
    private val native = FloatExponentialDecaySpec(absVelocityThreshold = 10f)
    private val wheel = TimeWheelDecay(native)

    @Test fun lowerFrictionPreservesReleaseVelocityButTravelsLongerAndFurther() {
        for (velocity in listOf(-4000f, -200f, 200f, 4000f)) {
            assertEquals(velocity, wheel.getVelocityFromNanos(0L, 50f, velocity), .001f)
            assertEquals(50f, wheel.getValueFromNanos(0L, 50f, velocity), .001f)
            assertEquals(native.getDurationNanos(50f, velocity) / TimeWheelFriction.toDouble(),
                wheel.getDurationNanos(50f, velocity).toDouble(), 1.0)
            assertEquals((native.getTargetValue(50f, velocity) - 50f) / TimeWheelFriction,
                wheel.getTargetValue(50f, velocity) - 50f, .001f)
            assertTrue(kotlin.math.abs(wheel.getVelocityFromNanos(200_000_000L, 50f, velocity)) >
                kotlin.math.abs(native.getVelocityFromNanos(200_000_000L, 50f, velocity)))
        }
        assertEquals(native.absVelocityThreshold, wheel.absVelocityThreshold, 0f)
    }

    @Test fun nativeFrictionReproducesTheOriginalCurve() {
        val unchanged = TimeWheelDecay(native, 1f)
        for (time in listOf(0L, 100_000_000L, 300_000_000L)) {
            assertEquals(native.getValueFromNanos(time, 5f, 600f), unchanged.getValueFromNanos(time, 5f, 600f), 0f)
            assertEquals(native.getVelocityFromNanos(time, 5f, 600f), unchanged.getVelocityFromNanos(time, 5f, 600f), 0f)
        }
    }

    @Test fun tickOccursAtTheCenterNotAtTheHalfwaySelectionBoundary() {
        assertEquals(0, timeWheelCenterCrossings(25f, 25.75f))
        assertEquals(1, timeWheelCenterCrossings(25.75f, 26f))
        assertEquals(0, timeWheelCenterCrossings(26f, 26f))
        assertEquals(0, timeWheelCenterCrossings(26f, 26.25f))
        assertEquals(1, timeWheelCenterCrossings(26.25f, 26f))
        assertEquals(0, timeWheelCenterCrossings(24.996f, 25.7f))
        assertEquals(1, timeWheelCenterCrossings(25.7f, 25.996f))
        assertEquals(0, timeWheelCenterCrossings(25.996f, 26.005f))
    }

    @Test fun fastSkippedTicksReverseAndMidnightWrappingDoNotLosePassages() {
        assertEquals(5, timeWheelCenterCrossings(60_025.1f, 60_030.4f))
        assertEquals(5, timeWheelCenterCrossings(60_030.4f, 60_025.1f))
        assertEquals(1, timeWheelCenterCrossings(60_059.1f, 60_060.1f))
        assertEquals(1, timeWheelCenterCrossings(24_023.1f, 24_024.1f))
        assertEquals(0, timeWheelCenterCrossings(0f, -.2f))
        assertEquals(1, timeWheelCenterCrossings(-.2f, -1f))
    }
}
