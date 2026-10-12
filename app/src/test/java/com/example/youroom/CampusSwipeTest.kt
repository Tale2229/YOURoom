package com.example.youroom

import com.example.youroom.domain.*
import org.junit.Assert.*
import org.junit.Test

class CampusSwipeTest {
    @Test fun leftSwipeFromWestGoesToShahe() {
        assertEquals(Campus.SHAHE, CampusSwipe.target(Campus.XITUCHENG, -56f, 0f))
    }
    @Test fun rightSwipeFromShaheGoesToWest() {
        assertEquals(Campus.XITUCHENG, CampusSwipe.target(Campus.SHAHE, 56f, 0f))
    }
    @Test fun outwardDirectionsDoNotWrapAround() {
        assertNull(CampusSwipe.target(Campus.XITUCHENG, 100f, 0f))
        assertNull(CampusSwipe.target(Campus.SHAHE, -100f, 0f))
    }
    @Test fun shortMovementDoesNotSwitch() {
        assertNull(CampusSwipe.target(Campus.XITUCHENG, -55.9f, 0f))
    }
    @Test fun predominantlyVerticalMovementDoesNotSwitch() {
        assertNull(CampusSwipe.target(Campus.XITUCHENG, -90f, 60f))
        assertNull(CampusSwipe.target(Campus.XITUCHENG, -60f, 90f))
    }
    @Test fun clearHorizontalDiagonalMovementCanSwitch() {
        assertEquals(Campus.SHAHE, CampusSwipe.target(Campus.XITUCHENG, -100f, 30f))
    }
    @Test fun invalidCoordinatesNeverSwitch() {
        assertNull(CampusSwipe.target(Campus.XITUCHENG, Float.NaN, 0f))
        assertNull(CampusSwipe.target(Campus.XITUCHENG, Float.NEGATIVE_INFINITY, 0f))
    }
}
