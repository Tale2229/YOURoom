package com.example.youroom

import com.example.youroom.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalTime

class SlotSelectionTest {
    @Test fun clickingAddsWithoutChangingOtherSelectedPeriods() {
        assertEquals(setOf(1, 2, 6), SlotSelection.toggle(setOf(1, 2), 6))
    }

    @Test fun clickingASelectedPeriodRemovesOnlyThatPeriod() {
        assertEquals(setOf(1, 6), SlotSelection.toggle(setOf(1, 2, 6), 2))
    }

    @Test fun clickingTheLastSelectionAllowsAnEmptySet() {
        assertTrue(SlotSelection.toggle(setOf(1), 1).isEmpty())
    }

    @Test fun dragStartingFromUnselectedCellUniformlyAdds() {
        val drag = SlotDragSelection(setOf(1, 5), 2)
        assertEquals(setOf(1, 2, 3, 4, 5), drag.visit(listOf(3, 4, 5)))
    }

    @Test fun dragStartingFromSelectedCellUniformlyRemoves() {
        val drag = SlotDragSelection(setOf(1, 2, 5), 2)
        assertEquals(setOf(1), drag.visit(listOf(3, 4, 5)))
    }

    @Test fun repeatedVisitsAndReverseDirectionDoNotFlipSelections() {
        val drag = SlotDragSelection(setOf(1), 2)
        drag.visit(listOf(3, 4, 3, 2))
        assertEquals(setOf(1, 2, 3, 4), drag.selection)
    }

    @Test fun cancellationRestoresTheOriginalSelection() {
        val drag = SlotDragSelection(setOf(1, 5), 2)
        drag.visit(listOf(3, 4, 5))
        assertEquals(setOf(1, 5), drag.cancel())
    }

    @Test fun summaryNeverBridgesUnselectedPeriods() {
        fun slot(number: Int, start: String, end: String) =
            LessonSlot(number, LocalTime.parse(start), LocalTime.parse(end), emptyList())
        val slots = listOf(slot(7, "13:50", "14:35"), slot(1, "08:00", "08:45"),
            slot(6, "13:00", "13:45"), slot(2, "08:50", "09:35"))
        assertEquals("08:00—09:35 · 13:00—14:35", SlotSelection.summary(slots))
        assertEquals(listOf(listOf(1, 2), listOf(6, 7)), SlotSelection.groups(slots).map { row -> row.map { it.number } })
    }

    @Test(expected = IllegalArgumentException::class) fun invalidSlotIsRejected() {
        SlotSelection.toggle(emptySet(), 0)
    }
}
