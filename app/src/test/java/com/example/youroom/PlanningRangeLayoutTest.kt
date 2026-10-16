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

    @Test fun crowdedLabelsMoveTheFartherTextToItsLongVerticalLegInsteadOfReturningSideways() {
        val result = arrange(listOf(110f, 125f))
        val lower = result.labels[0]; val upper = result.labels[1]
        assertEquals(0, lower.lane); assertEquals(1, upper.lane)
        assertTrue(upper.bottom < lower.top)
        assertEquals(110f,lower.center,.01f)
        assertTrue(upper.center>125f)
        val column=result.leaders[1].verticalX
        assertEquals(upper.center,column,0f)
        assertNull(result.leaders[1].escapeX)
        assertTrue(column <= lower.left - 4 || column >= lower.right + 4)
    }

    @Test fun offsetCentersStillHaveVerticalStemsAtBothEndsAndOnlyTwoBends() {
        val label=PlanningRangePlacement(50f,4f,0,PlanningRangeLabel(80,19))
        val anchor=PlanningRangeAnchor(140f,200f)
        val leader=routePlanningRange(anchor,label,listOf(label),320f,24f,4f)
        assertFalse(leader.straight)
        assertEquals(label.center,leader.startX,0f);assertEquals(anchor.center,leader.endX,0f)
        assertEquals(listOf(RangeLeaderPoint(90f,25f),RangeLeaderPoint(90f,60f),
            RangeLeaderPoint(170f,60f),RangeLeaderPoint(170f,80f)),leader.points(25f,60f,80f))
    }

    @Test fun aClearCenterColumnUsesTwoCorridorBendsAndRetainsBothCenters() {
        val near=PlanningRangePlacement(100f,30f,0,PlanningRangeLabel(80,19))
        val far=PlanningRangePlacement(50f,4f,1,PlanningRangeLabel(80,19))
        val leader=routePlanningRange(PlanningRangeAnchor(140f,200f),far,listOf(near,far),320f,24f,4f)
        assertFalse(leader.straight);assertNull(leader.escapeX)
        assertEquals(90f,leader.startX,0f);assertEquals(170f,leader.endX,0f)
        val path=leader.points(25f,60f,80f)
        assertEquals(4,path.size)
        assertEquals(path[0].x,path[1].x,0f)
        assertEquals(path[1].y,path[2].y,0f)
        assertEquals(path[2].x,path[3].x,0f)
    }

    @Test fun impossibleCenterColumnsStillKeepVerticalEndStemsAndAvoidTheOtherText() {
        val near=PlanningRangePlacement(100f,30f,0,PlanningRangeLabel(100,19))
        val far=PlanningRangePlacement(120f,4f,1,PlanningRangeLabel(60,19))
        val leader=routePlanningRange(PlanningRangeAnchor(140f,160f),far,listOf(near,far),320f,24f,4f)
        assertNotNull(leader.escapeX)
        assertTrue(leader.verticalX<=96f || leader.verticalX>=204f)
        assertEquals(far.center,leader.startX,0f)
        assertEquals(150f,leader.endX,0f)
        val path=leader.points(25f,60f,80f)
        assertEquals(6,path.size)
        assertEquals(path[0].x,path[1].x,0f)
        assertEquals(path[path.lastIndex-1].x,path.last().x,0f)
        path.zipWithNext().forEach { (a,b) -> assertFalse(crossesText(a,b,near)) }
    }

    @Test fun consecutiveLunchPeriodsKeepAllCenterColumnsClearWithAtMostTwoBends() {
        val day=com.example.youroom.data.DemoData.snapshot().slots
        val anchors=planningRangeAnchors(listOf(StudyWindow(733,793),StudyWindow(793,853),StudyWindow(853,913)),
            day,360f,24f,3f)
        val result=arrangePlanningRanges(anchors,List(3) { PlanningRangeLabel(84,19) },360f,24f,8f,4f,5f)
        assertTrue(result.leaders.all { it.escapeX==null })
        result.leaders.forEachIndexed { i,leader ->
            val label=result.labels[i]
            assertEquals(label.center,leader.startX,0f)
            assertEquals(anchors[i].center,leader.endX,0f)
            assertTrue(leader.points(label.bottom+2,result.labelBottom+4,100f).size<=4)
        }
    }

    @Test fun fullWidthNearerTextCanUseTheOuterGutterWithoutLeavingTheScreen() {
        val near=PlanningRangePlacement(24f,30f,0,PlanningRangeLabel(272,19))
        val far=PlanningRangePlacement(100f,4f,1,PlanningRangeLabel(80,19))
        val leader=routePlanningRange(PlanningRangeAnchor(120f,160f),far,listOf(near,far),320f,24f,4f)
        assertEquals(20f,leader.verticalX,0f)
        val points=leader.points(25f,60f,80f)
        assertTrue(points.size<=6)
        assertTrue(points.all { it.x in 1f..319f })
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
        assertEquals(1, arrange(listOf(90f, 105f)).labels.maxOf { it.lane })
        assertEquals(0, arrange(listOf(90f, 200f), width = 400f).labels.maxOf { it.lane })
        assertTrue(arrange(emptyList()).labels.isEmpty())
    }

    @Test fun theThirdAdjacentTimeReusesTheFirstRowInsteadOfEscapingToTheLeft() {
        val result=arrange(listOf(160f,205f,240f),360f,90)
        assertEquals(listOf(0,1,0),result.labels.map { it.lane })
        assertTrue(result.labels.zipWithNext().all { (a,b) -> a.center<=b.center })
        assertTrue(kotlin.math.abs(result.labels[2].center-240f)<20f)
        result.labels.forEachIndexed { i,label ->
            assertTrue(kotlin.math.abs(label.center-listOf(160f,205f,240f)[i])<=45.01f)
        }
    }

    @Test fun earlierLabelsCanMoveTogetherToMakeASmallSameRowGap() {
        val result=arrange(listOf(140f,210f),360f,80)
        assertEquals(listOf(0,0),result.labels.map { it.lane })
        assertEquals(131f,result.labels[0].center,.02f)
        assertEquals(219f,result.labels[1].center,.02f)
    }

    @Test fun shuffledInputKeepsChronologicalGeometryButReturnsOriginalIndices() {
        val result=arrange(listOf(240f,160f,205f),360f,90)
        assertEquals(listOf(0,0,1),result.labels.map { it.lane })
        assertTrue(result.labels[1].center<=result.labels[2].center)
        assertTrue(result.labels[2].center<=result.labels[0].center)
    }

    @Test fun denseTimesPreferMovingTextToClearCenterColumnsOverFoldedWires() {
        val result=arrange(listOf(150f,151f,152f),320f,100)
        assertTrue(result.labels.maxOf { it.lane }>=1)
        assertTrue(result.leaders.all { it.escapeX==null })
        result.labels.forEachIndexed { i,label ->
            assertEquals(label.center,result.leaders[i].verticalX,0f)
            result.labels.filter { it.lane<label.lane }.forEach {
                assertTrue(label.center<=it.left-3.99f || label.center>=it.right+3.99f)
            }
        }
    }

    @Test fun geometryIsScaleIndependentAndDoesNotLeaveGapsInItsRows() {
        val normal=arrange(listOf(160f,205f,240f),360f,90)
        val doubled=arrangePlanningRanges(listOf(320f,410f,480f).map { PlanningRangeAnchor(it-4,it+4) },
            List(3) { PlanningRangeLabel(180,38) },720f,48f,16f,8f,10f)
        normal.labels.zip(doubled.labels).forEach { (a,b) ->
            assertEquals(a.lane,b.lane);assertEquals(a.center*2,b.center,.02f)
            assertEquals(a.top*2,b.top,.02f)
        }
    }

    @Test fun randomTriplesStayBoundedOrderedAndSeparatedAcrossRemeasurements() {
        val random=java.util.Random(426)
        repeat(160) {
            val width=280f+random.nextInt(160)
            val xs=List(3) { 24f+random.nextFloat()*(width-48) }.sorted()
            val labelWidth=60+random.nextInt(90)
            val result=arrange(xs,width,labelWidth,19)
            if(width-48>=2*labelWidth+8) assertTrue("enough room must not need folded wires",result.leaders.all { it.escapeX==null })
            assertTrue(result.labels.zipWithNext().all { (a,b) -> a.center<=b.center+.01f })
            result.labels.forEach { assertTrue(it.left>=23.99f && it.right<=width-23.99f) }
            result.labels.forEachIndexed { i,a -> result.labels.drop(i+1).forEach { b ->
                assertTrue(a.right<=b.left || b.right<=a.left || a.bottom<=b.top || b.bottom<=a.top)
            } }
            result.leaders.forEachIndexed { i,leader ->
                val label=result.labels[i]
                assertEquals(label.center,leader.startX,0f)
                assertEquals(PlanningRangeAnchor(xs[i]-2,xs[i]+2).center,leader.endX,0f)
                val points=leader.points(label.bottom+2,result.labelBottom+4+label.routeTrack*3,result.annotationHeight-2f)
                assertTrue(points.size<=if(leader.escapeX==null) 4 else 6)
                assertEquals(points.first().x,points[1].x,0f)
                assertEquals(points[points.lastIndex-1].x,points.last().x,0f)
                if(leader.escapeX==null) {
                    val horizontal=points.zipWithNext().map { (a,b) -> b.x-a.x }.filter { kotlin.math.abs(it)>.01f }
                    assertTrue(horizontal.all { it>=0 } || horizontal.all { it<=0 })
                }
                points.zipWithNext().forEach { (a,b) -> result.labels.filter { it.lane<label.lane }.forEach {
                    assertFalse("wire must not cross another time label",crossesText(a,b,it))
                } }
            }
        }
    }

    @Test fun adjacentHalfHoursShiftTheSecondTimeRightWithoutMovingTheFirstOrChangingItsMeaning() {
        val slots=com.example.youroom.data.DemoData.snapshot().slots
        val windows=listOf(StudyWindow(840,870),StudyWindow(870,900))
        val anchors=planningRangeAnchors(windows,slots,360f,24f,3f)
        val result=arrangePlanningRanges(anchors,List(2) { PlanningRangeLabel(84,19) },360f,24f,8f,4f,5f)
        val first=result.labels[0];val second=result.labels[1]
        assertEquals(0,first.lane);assertEquals(1,second.lane)
        assertEquals(anchors[0].center,first.center,.01f)
        assertTrue(second.center>anchors[1].center)
        assertTrue(second.center>=first.right+3.99f)
        assertEquals(second.center,result.leaders[1].verticalX,0f)
        assertNull(result.leaders[1].escapeX)
        val points=result.leaders[1].points(second.bottom+2,result.labelBottom+4,result.annotationHeight-2f)
        assertEquals(4,points.size)
        assertEquals(second.center,points[0].x,0f);assertEquals(anchors[1].center,points.last().x,0f)
        assertTrue(points.zipWithNext().all { (a,b) -> b.x<=a.x })
    }

    @Test fun longerRoomHistoriesHaveABoundedSearchAndKeepAllAnnotations() {
        val xs=List(12) { 30f+it*23f }
        val result=arrange(xs,360f,80)
        assertEquals(xs.size,result.labels.size)
        assertTrue(result.labels.zipWithNext().all { (a,b) -> a.center<=b.center+.01f })
        result.labels.forEach { assertTrue(it.left>=23.99f && it.right<=336.01f) }
        result.labels.forEachIndexed { i,a -> result.labels.drop(i+1).forEach { b ->
            assertTrue(a.lane!=b.lane || a.right+7.99<=b.left || b.right+7.99<=a.left)
        } }
    }

    private fun crossesText(a: RangeLeaderPoint,b: RangeLeaderPoint,label: PlanningRangePlacement): Boolean {
        var low=0f; var high=1f
        fun clip(start: Float,delta: Float,min: Float,max: Float): Boolean {
            if (kotlin.math.abs(delta)<.0001f) return start>min && start<max
            val first=(min-start)/delta;val last=(max-start)/delta
            low=maxOf(low,minOf(first,last));high=minOf(high,maxOf(first,last))
            return low<high-.0001f
        }
        return clip(a.x,b.x-a.x,label.left,label.right) && clip(a.y,b.y-a.y,label.top,label.bottom)
    }
}
