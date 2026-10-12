package com.example.youroom

import com.example.youroom.data.DemoData
import com.example.youroom.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalTime

class StudyGridLayoutTest {
    @Test fun fourteenPeriodsKeepSearchInColumnsFourAndFiveAndFilterInSix() {
        val rows = StudyGridLayout.rows((1..14).toList())
        assertEquals(3, rows.size)
        assertEquals((1..6).toList(), rows[0].map { it.number })
        assertEquals((7..12).toList(), rows[1].map { it.number })
        assertEquals(listOf(13, 14, null, null, null), rows[2].map { it.number })
        assertEquals(listOf(StudyGridCellKind.SLOT, StudyGridCellKind.SLOT, StudyGridCellKind.SPACER,
            StudyGridCellKind.SEARCH, StudyGridCellKind.FILTER), rows[2].map { it.kind })
        assertEquals(listOf(1, 1, 1, 2, 1), rows[2].map { it.span })
    }

    @Test fun missingOrAdditionalPeriodsNeverOverlapTheControlsOrInventSlots() {
        for (count in 0..20) {
            val numbers = (1..count).toList()
            val rows = StudyGridLayout.rows(numbers)
            assertTrue(rows.all { it.sumOf(StudyGridCell::span) == StudyGridLayout.columns })
            assertEquals(numbers, rows.flatten().mapNotNull { it.number })
            assertEquals(1, rows.flatten().count { it.kind == StudyGridCellKind.SEARCH })
            assertEquals(1, rows.flatten().count { it.kind == StudyGridCellKind.FILTER })
            assertEquals(3, rows.last().takeWhile { it.kind != StudyGridCellKind.SEARCH }.sumOf { it.span })
        }
        assertEquals(listOf(1, 6, 14), StudyGridLayout.rows(listOf(14, 6, 1, 6)).flatten().mapNotNull { it.number })
    }

    @Test fun sixBoundaryMarkersHaveTheRequestedTimesAndPositions() {
        val slots = DemoData.snapshot().slots
        val rows = StudyGridLayout.rows(slots.map { it.number })
        val bands = StudyGridLayout.markers(rows, slots)
        assertEquals(6, bands.flatten().count { it != null })
        assertEquals(StudyTimeMarker(1, true, "8:00"), bands[0][0])
        assertEquals(StudyTimeMarker(6, false, "13:45"), bands[1][5])
        assertEquals(StudyTimeMarker(7, true, "13:50"), bands[1][0])
        assertEquals(StudyTimeMarker(12, false, "19:15"), bands[2][5])
        assertEquals(StudyTimeMarker(13, true, "19:20"), bands[2][0])
        assertEquals(StudyTimeMarker(14, false, "20:55"), bands[3][1])
    }

    @Test fun campusTimetableAndMissingBoundaryPeriodsAreRespected() {
        val slots = DemoData.snapshot(Campus.SHAHE).slots.filterNot { it.number == 6 }.map {
            if (it.number == 7) it.copy(start = LocalTime.of(14, 5)) else it
        }
        val bands = StudyGridLayout.markers(StudyGridLayout.rows(slots.map { it.number }), slots)
        assertEquals("14:05", bands.flatten().filterNotNull().single { it.number == 7 }.label)
        assertFalse(bands.flatten().filterNotNull().any { it.number == 6 })
    }
}
