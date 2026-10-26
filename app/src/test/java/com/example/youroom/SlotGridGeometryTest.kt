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
    @Test fun inspectionBridgesOnlyNarrowHorizontalGapsUsingTheNearestFace() {
        assertEquals(1, SlotGridGeometry.inspectionHit(cells, 49f, 24f, 6f))
        assertEquals(2, SlotGridGeometry.inspectionHit(cells, 51f, 24f, 6f))
        assertEquals(3, SlotGridGeometry.inspectionHit(cells.reversed(), 103f, 24f, 6f))
        assertNull(SlotGridGeometry.inspectionHit(cells, 50f, 24f, 2f))
    }
    @Test fun inspectionNeverBridgesVerticalGapsOrOutsideTheGrid() {
        for (position in listOf(24f to 50f, -1f to 24f, 153f to 24f, 300f to 80f))
            assertNull(SlotGridGeometry.inspectionHit(cells, position.first, position.second, 6f))
    }
    @Test fun inspectionDoesNotCrossSearchSizedWhitespaceOrTreatInvalidFacesAsVisible() {
        val sparse = listOf(cells.first(), SlotBounds(14, 150f, 0f, 198f, 48f), SlotBounds(15, 0f, 0f, 0f, 0f))
        assertNull(SlotGridGeometry.inspectionHit(sparse, 100f, 24f, 6f))
        assertNull(SlotGridGeometry.inspectionHit(emptyList(), 0f, 0f, 6f))
        assertEquals(14, SlotGridGeometry.inspectionHit(sparse, 170f, 24f, 6f))
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
