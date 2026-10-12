package com.example.youroom

import com.example.youroom.domain.*
import org.junit.Assert.*
import org.junit.Test

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
}
