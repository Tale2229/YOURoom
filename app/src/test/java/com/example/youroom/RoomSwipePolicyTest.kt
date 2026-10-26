package com.example.youroom

import com.example.youroom.domain.RoomSwipePolicy
import org.junit.Assert.*
import org.junit.Test

class RoomSwipePolicyTest {
    private fun target(start: Float, end: Float, speed: Float = 0f) = RoomSwipePolicy.target(start, end, 184f, speed)
    @Test fun fullyRevealedRailIgnoresOppositeReleaseVelocity() { assertTrue(target(0f, -184f, 4000f)) }
    @Test fun fullyHiddenRailIgnoresOppositeReleaseVelocity() { assertFalse(target(-184f, 0f, -4000f)) }
    @Test fun nearAnchorsRemainStableWithReleaseRecoil() {
        assertTrue(target(0f, -176f, 4000f)); assertFalse(target(-184f, -8f, -4000f))
    }
    @Test fun opposedTailVelocityDoesNotUndoTheNetOpeningOrClosingMovement() {
        assertTrue(target(0f, -150f, 1800f)); assertFalse(target(-184f, -34f, -1800f))
    }
    @Test fun meaningfulQuickFlickCanOpenOrCloseBeforeTheHalfwayPoint() {
        assertTrue(target(0f, -32f, -1200f)); assertFalse(target(-184f, -142f, 1200f))
    }
    @Test fun slowPartialDragsUseTheVisibleHalfwayPoint() {
        assertFalse(target(0f, -60f, -100f)); assertTrue(target(-184f, -120f, 100f))
    }
    @Test fun tinyFastMovementDoesNotSwitchAnchors() {
        assertFalse(target(0f, -5f, -5000f)); assertTrue(target(-184f, -179f, 5000f))
    }
    @Test fun exactHalfwayReturnsClosed() { assertFalse(target(0f, -92f)) }
    @Test fun offsetsBeyondTheAnchorsAreClamped() {
        assertTrue(target(0f, -400f, 4000f)); assertFalse(target(-184f, 100f, -4000f))
    }
    @Test fun nonFiniteVelocityFallsBackToPosition() {
        assertTrue(target(0f, -120f, Float.NaN)); assertFalse(target(-184f, -40f, Float.POSITIVE_INFINITY))
    }
    @Test fun invalidGeometryDoesNotOpen() {
        assertFalse(RoomSwipePolicy.target(0f, -20f, 0f, -1000f))
        assertFalse(RoomSwipePolicy.target(0f, Float.NaN, 184f, -1000f))
        assertFalse(RoomSwipePolicy.target(0f, -20f, Float.POSITIVE_INFINITY, -1000f))
    }
}
