package com.example.youroom

import com.example.youroom.domain.LessonSlot
import com.example.youroom.domain.StudyWindow
import com.example.youroom.ui.*
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class PlanningRangeLayoutTest {
    private fun slot(n: Int, start: Int, end: Int) = LessonSlot(n,
        LocalTime.ofSecondOfDay(start * 60L), LocalTime.ofSecondOfDay(end * 60L), emptyList())
    private val slots = listOf(slot(1, 480, 525), slot(2, 530, 575))
    private fun anchors(vararg windows: StudyWindow) = planningRangeAnchors(windows.toList(), slots, 151f, 24f, 3f)
    private fun arrange(xs: List<Float>, width: Float = 320f, labelWidth: Int = 80, labelHeight: Int = 19) =
        arrangePlanningRanges(xs.map { PlanningRangeAnchor(it - 2, it + 2) },
            xs.map { PlanningRangeLabel(labelWidth, labelHeight) }, width, 24f, 8f, 4f, 5f)

    @Test fun exactMinuteCoordinatesUseTheExistingBarGeometry() {
        val mark = anchors(StudyWindow(495, 510)).single()
        assertEquals(24 + 50 / 3f, mark.start, .001f)
        assertEquals(24 + 100 / 3f, mark.end, .001f)
        assertFalse(mark.openStart); assertFalse(mark.openEnd); assertFalse(mark.outside)
    }

    @Test fun anEarlyStartIsOpenButNeverExtendsOutsideTheTeachingBars() {
        val mark = anchors(StudyWindow(440, 540)).single()
        assertEquals(24f, mark.start, 0f)
        assertTrue(mark.openStart); assertFalse(mark.openEnd); assertFalse(mark.outside)
        assertTrue(mark.end in 77f..127f)
    }

    @Test fun aLateEndIsOpenWithoutMovingTheStartOrDrawingBeyondTheLastBar() {
        val mark = anchors(StudyWindow(550, 1320)).single()
        assertEquals(127f, mark.end, 0f)
        assertFalse(mark.openStart); assertTrue(mark.openEnd); assertFalse(mark.outside)
    }

    @Test fun spanningBothEndsLeavesBothCapsOpen() {
        val mark = anchors(StudyWindow(440, 1320)).single()
        assertTrue(mark.openStart && mark.openEnd)
        assertFalse(mark.outside)
        assertEquals(24f, mark.start, 0f); assertEquals(127f, mark.end, 0f)
    }

    @Test fun whollyOutsideWindowsHaveLabelsButNoBoundaryBracket() {
        val marks = anchors(StudyWindow(410, 450), StudyWindow(1270, 1320))
        assertTrue(marks.all { it.outside && it.known })
        assertTrue(marks[0].openStart); assertTrue(marks[1].openEnd)
        assertEquals(marks[0].start, marks[0].end, 0f)
        assertEquals(marks[1].start, marks[1].end, 0f)
    }

    @Test fun exactTeachingBoundariesAreClosedNotOpen() {
        val mark = anchors(StudyWindow(480, 575)).single()
        assertFalse(mark.openStart || mark.openEnd || mark.outside)
        assertTrue(anchors(StudyWindow(450, 480), StudyWindow(575, 600)).all { it.outside })
    }

    @Test fun actualCampusTimesRatherThanEightAndTwentyFiftyFiveDetermineTheCaps() {
        val changed = listOf(slot(6, 500, 530), slot(9, 600, 660))
        val mark = planningRangeAnchors(listOf(StudyWindow(490, 650)), changed, 151f, 24f, 3f).single()
        assertTrue(mark.openStart); assertFalse(mark.openEnd)
        assertEquals(24f + 53 + 50 * 50 / 60f, mark.end, .001f)
    }

    @Test fun absentOrInvalidGeometryDoesNotInventTeachingBoundaries() {
        val windows = listOf(StudyWindow(480, 600))
        assertFalse(planningRangeAnchors(windows, emptyList(), 320f, 24f, 3f).single().known)
        assertFalse(planningRangeAnchors(windows, slots, 49f, 24f, 3f).single().known)
    }

    @Test fun separatedLabelsStayOnOneCompactRow() {
        val result = arrange(listOf(70f, 180f, 290f), width = 360f)
        assertTrue(result.labels.all { it.lane == 0 && it.top == 4f })
        assertEquals(23f, result.labelBottom, 0f)
    }

    @Test fun crowdedLabelsAddARowAndKeepTheUpperLeaderOutOfTheLowerText() {
        val result = arrange(listOf(110f, 125f))
        val lower = result.labels[0]; val upper = result.labels[1]
        assertEquals(0, lower.lane); assertEquals(1, upper.lane)
        assertTrue(upper.bottom < lower.top)
        assertTrue(upper.center <= lower.left - 4 || upper.center >= lower.right + 4)
    }

    @Test fun largeFontsAndThreeAdjacentWindowsAreNeverOverlappedOrTruncated() {
        val result = arrange(listOf(100f, 105f, 110f), labelWidth = 128, labelHeight = 29)
        result.labels.forEach { assertTrue(it.left >= 24 && it.right <= 296) }
        result.labels.forEachIndexed { i, a -> result.labels.drop(i + 1).forEach { b ->
            assertTrue(a.right <= b.left || b.right <= a.left || a.bottom <= b.top || b.bottom <= a.top)
        } }
        assertTrue(result.labelBottom >= 62)
    }

    @Test fun endpointLabelsAreClampedInsideTheSameGuttersAsTheAvailabilityBars() {
        val result = arrange(listOf(24f, 296f))
        assertEquals(24f, result.labels.first().left, 0f)
        assertEquals(296f, result.labels.last().right, 0f)
    }

    @Test fun remeasurementCanReturnToOneRowWithoutStickyLayoutState() {
        assertEquals(1, arrange(listOf(90f, 125f)).labels.maxOf { it.lane })
        assertEquals(0, arrange(listOf(90f, 200f), width = 400f).labels.maxOf { it.lane })
        assertTrue(arrange(emptyList()).labels.isEmpty())
    }
}
