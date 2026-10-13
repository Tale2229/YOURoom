package com.example.youroom

import com.example.youroom.ui.*
import org.junit.Assert.*
import org.junit.Test

class TimeWheelTest {
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
