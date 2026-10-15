package com.example.youroom

import com.example.youroom.domain.RoomActionFlip
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class RoomActionFlipTest {
    @Test fun theRightmostButtonIsFirstAndRequiresItsWholeTouchAreaToBeUncovered() {
        assertEquals(52f, RoomActionFlip.threshold(3), 0f)
        assertTrue(RoomActionFlip.crossings(0f, 51.9f).isEmpty())
        assertEquals(listOf(3), RoomActionFlip.crossings(0f, 52f))
        assertEquals(listOf(2), RoomActionFlip.crossings(52f, 92f))
        assertEquals(listOf(1), RoomActionFlip.crossings(92f, 132f))
        assertEquals(listOf(0), RoomActionFlip.crossings(132f, 172f))
    }
    @Test fun aFastFrameCannotSkipAnyButtonAndKeepsTheRightToLeftOrder() {
        assertEquals(listOf(3,2,1,0), RoomActionFlip.crossings(0f, 184f))
        assertEquals(listOf(2,1), RoomActionFlip.crossings(80f, 150f))
    }
    @Test fun closingOrHoldingStillNeverTriggersAnOpeningFlip() {
        assertTrue(RoomActionFlip.crossings(184f, 0f).isEmpty())
        assertTrue(RoomActionFlip.crossings(92f, 92f).isEmpty())
    }
    @Test fun strongerPullsHaveStrictlyGreaterInitialVelocityAndAcceleration() {
        val speeds=listOf(0f,100f,600f,1500f,2600f)
        speeds.zipWithNext().forEach { (a,b) ->
            assertTrue(RoomActionFlip.initialVelocity(b)>RoomActionFlip.initialVelocity(a))
            assertTrue(RoomActionFlip.stiffness(b)>RoomActionFlip.stiffness(a))
            fun acceleration(speed: Float): Float {
                val k=RoomActionFlip.stiffness(speed)
                return k*360f-2*RoomActionFlip.dampingRatio*sqrt(k)*RoomActionFlip.initialVelocity(speed)
            }
            assertTrue(acceleration(b)>acceleration(a))
        }
    }
    @Test fun invalidOrExtremeVelocitiesRemainFiniteAndBounded() {
        for (speed in listOf(Float.NaN,Float.POSITIVE_INFINITY,-300f,0f,100_000f)) {
            assertTrue(RoomActionFlip.initialVelocity(speed) in 300f..1200f)
            assertTrue(RoomActionFlip.stiffness(speed) in 75f..195f)
        }
        assertTrue(RoomActionFlip.crossings(Float.NaN,184f).isEmpty())
    }
}
