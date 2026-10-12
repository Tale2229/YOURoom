package com.example.youroom

import com.example.youroom.domain.SearchFilters
import com.example.youroom.domain.SlotSelection
import org.junit.Assert.*
import org.junit.Test

class SlotSelectionTest {
    @Test fun firstTapStartsANewSingleSlotSelection() {
        val result = SlotSelection.select(SearchFilters(1, 8), 4, false)
        assertEquals(4, result.firstSlot)
        assertEquals(4, result.lastSlot)
    }

    @Test fun secondTapClosesTheRange() {
        val result = SlotSelection.select(SearchFilters(4, 4), 8, true)
        assertEquals(4, result.firstSlot)
        assertEquals(8, result.lastSlot)
    }

    @Test fun reverseDirectionIsNormalizedInsteadOfCreatingAnInvalidRange() {
        val result = SlotSelection.select(SearchFilters(8, 8), 4, true)
        assertEquals(4, result.firstSlot)
        assertEquals(8, result.lastSlot)
    }

    @Test fun selectingTheSameEndpointKeepsASingleSlot() {
        val result = SlotSelection.select(SearchFilters(4, 4), 4, true)
        assertEquals(4, result.firstSlot)
        assertEquals(4, result.lastSlot)
    }

    @Test fun changingSlotsDoesNotDiscardPersonalFilters() {
        val before = SearchFilters(1, 2, building = "2", query = "靠窗", favoritesOnly = true,
            quietOnly = true, outletsOnly = true, minimumCapacity = 60)
        val after = SlotSelection.select(before, 5, false)
        assertEquals(before.copy(firstSlot = 5, lastSlot = 5), after)
    }

    @Test(expected = IllegalArgumentException::class) fun invalidSlotIsRejected() {
        SlotSelection.select(SearchFilters(), 0, false)
    }
}
