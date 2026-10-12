package com.example.youroom

import com.example.youroom.domain.*
import org.junit.Assert.*
import org.junit.Test

class SlotGridGeometryTest {
    private val cells = listOf(SlotBounds(1, 0f, 0f, 48f, 48f),
        SlotBounds(2, 52f, 0f, 100f, 48f), SlotBounds(3, 104f, 0f, 152f, 48f),
        SlotBounds(4, 0f, 52f, 48f, 100f), SlotBounds(5, 52f, 52f, 100f, 100f))

    @Test fun gapsDoNotHitACell() {
        assertEquals(1, SlotGridGeometry.hit(cells, 24f, 24f))
        assertNull(SlotGridGeometry.hit(cells, 50f, 24f))
        assertNull(SlotGridGeometry.hit(cells, -20f, -20f))
    }
    @Test fun fastHorizontalSweepIncludesSkippedCellsInOrder() {
        assertEquals(listOf(1, 2, 3), SlotGridGeometry.crossed(cells, 24f, 24f, 140f, 24f))
    }
    @Test fun reverseSweepHasReverseVisitOrder() {
        assertEquals(listOf(3, 2, 1), SlotGridGeometry.crossed(cells, 140f, 24f, 24f, 24f))
    }
    @Test fun verticalSweepDoesNotFillOrdinalNeighbors() {
        assertEquals(listOf(1, 4), SlotGridGeometry.crossed(cells, 24f, 24f, 24f, 80f))
    }
    @Test fun leavingGridCountsOnlyCellsActuallyCrossed() {
        assertEquals(listOf(1, 2, 3), SlotGridGeometry.crossed(cells, 24f, 24f, 250f, 24f))
    }
    @Test fun zeroLengthSegmentIsASingleCellHit() {
        assertEquals(listOf(1), SlotGridGeometry.crossed(cells, 24f, 24f, 24f, 24f))
    }
    @Test fun clippingDoesNotTreatInvisibleCellsAsSelectable() {
        assertNull(SlotBounds(7, 0f, 0f, 0f, 0f).intersection(0f, 0f, 10f, 10f))
    }
}
