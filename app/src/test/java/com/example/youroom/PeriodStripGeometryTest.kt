package com.example.youroom

import com.example.youroom.domain.PeriodStripGeometry
import org.junit.Assert.*
import org.junit.Test

class PeriodStripGeometryTest {
    @Test fun eachOfTheFourteenBarCentersMapsToItsOwnIndex() {
        val width = 280f
        val gap = 3f
        val bar = (width - gap * 13) / 14
        for (index in 0..13) assertEquals(index, PeriodStripGeometry.nearestIndex(index * (bar + gap) + bar / 2, width, 14, gap))
    }

    @Test fun gapsUseTheirNearestBarWithoutADisappearingHint() {
        assertEquals(0, PeriodStripGeometry.nearestIndex(9.9f, 44f, 4, 4f))
        assertEquals(1, PeriodStripGeometry.nearestIndex(10.1f, 44f, 4, 4f))
    }

    @Test fun outsidePositionsClampToTheFirstAndLastBars() {
        assertEquals(0, PeriodStripGeometry.nearestIndex(-100f, 280f, 14, 3f))
        assertEquals(13, PeriodStripGeometry.nearestIndex(400f, 280f, 14, 3f))
        assertEquals(0, PeriodStripGeometry.nearestIndex(100f, 8f, 1, 3f))
    }

    @Test fun rightToLeftMirrorsIndicesWithoutAssumingPeriodNumbersAreContiguous() {
        assertEquals(13, PeriodStripGeometry.nearestIndex(0f, 280f, 14, 3f, reversed = true))
        assertEquals(0, PeriodStripGeometry.nearestIndex(280f, 280f, 14, 3f, reversed = true))
        assertEquals(1, PeriodStripGeometry.nearestIndex(50f, 150f, 3, 0f, reversed = true))
    }

    @Test fun emptyOrInvalidGeometryCannotReturnAnIndex() {
        for (width in listOf(0f, -10f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertNull(PeriodStripGeometry.nearestIndex(0f, width, 14, 3f))
        }
        assertNull(PeriodStripGeometry.nearestIndex(Float.NaN, 280f, 14, 3f))
        assertNull(PeriodStripGeometry.nearestIndex(0f, 280f, 0, 3f))
        assertNull(PeriodStripGeometry.nearestIndex(0f, 280f, 14, -1f))
        assertNull(PeriodStripGeometry.nearestIndex(0f, 10f, 14, 3f))
    }
}
