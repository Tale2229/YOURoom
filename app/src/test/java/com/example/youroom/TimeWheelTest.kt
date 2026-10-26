package com.example.youroom

import com.example.youroom.ui.*
import org.junit.Assert.*
import org.junit.Test

class TimeWheelTest {
    @Test fun cylinderCenterIsLargestOpaqueAndFacesTheUser() {
        val center = timeWheelProjection(0f)
        assertEquals(0f, center.y, .0001f)
        assertEquals(0f, center.rotation, .0001f)
        assertEquals(1f, center.scale, .0001f)
        assertEquals(1f, center.alpha, .0001f)
        for (distance in listOf(.25f, .5f, 1f, 2f, 3f)) {
            val edge = timeWheelProjection(distance)
            assertTrue(edge.scale < center.scale && edge.alpha < center.alpha)
            assertTrue(edge.rotation < 0f && edge.y > 0f)
        }
    }

    @Test fun cylinderIsSymmetricAndCurvedAtFractionalPositions() {
        for (distance in listOf(.1f, .25f, .5f, .75f, 1f, 2f, 3f)) {
            val above = timeWheelProjection(-distance)
            val below = timeWheelProjection(distance)
            assertEquals(-above.y, below.y, .0001f)
            assertEquals(-above.rotation, below.rotation, .0001f)
            assertEquals(above.scale, below.scale, .0001f)
            assertEquals(above.alpha, below.alpha, .0001f)
            assertTrue(below.y < distance)
        }
        assertTrue(timeWheelProjection(2f).y - timeWheelProjection(1f).y < timeWheelProjection(1f).y)
        for (step in 1..300) {
            val before = timeWheelProjection((step - 1) / 100f)
            val after = timeWheelProjection(step / 100f)
            assertTrue(kotlin.math.abs(after.y - before.y) < .02f)
            assertTrue(kotlin.math.abs(after.alpha - before.alpha) < .03f)
        }
    }

    @Test fun tappingUsesProjectedPositionsInsteadOfFlatRowOffsets() {
        for (offset in -3..3) assertEquals(offset,
            timeWheelTappedOffset(timeWheelProjection(offset.toFloat()).y))
        assertEquals(0, timeWheelTappedOffset(0f))
        assertEquals(3, timeWheelTappedOffset(10f))
        assertEquals(-3, timeWheelTappedOffset(-10f))
    }

    @Test fun everyHourAndMinuteStartsAtItsOwnValue() {
        for (count in listOf(24, 60)) for (value in 0 until count) {
            val index = timeWheelInitialIndex(value, count)
            assertEquals(value, timeWheelValue(index, count))
            assertTrue(index in count until count * (TimeWheelCycles - 1))
        }
    }

    @Test fun bothWheelsWrapInEitherDirection() {
        for (count in listOf(24, 60)) {
            val zero = timeWheelInitialIndex(0, count)
            assertEquals(count - 1, timeWheelValue(zero - 1, count))
            assertEquals(0, timeWheelValue(zero + count, count))
            assertEquals(count - 1, timeWheelValue(-1, count))
        }
    }

    @Test fun settingAValueUsesTheNearestCycle() {
        for (count in listOf(24, 60)) for (source in 0 until count) for (target in 0 until count) {
            val around = timeWheelInitialIndex(source, count)
            val index = timeWheelNearestIndex(target, around, count)
            assertEquals(target, timeWheelValue(index, count))
            assertTrue(kotlin.math.abs(index - around) <= count / 2)
        }
    }

    @Test fun nearestValueStaysInsideTheListAtItsActualEdges() {
        for (count in listOf(24, 60)) for (target in 0 until count) {
            assertTrue(timeWheelNearestIndex(target, 0, count) in 0 until count)
            val end = count * TimeWheelCycles - 1
            assertTrue(timeWheelNearestIndex(target, end, count) in end - count + 1..end)
        }
    }
}
