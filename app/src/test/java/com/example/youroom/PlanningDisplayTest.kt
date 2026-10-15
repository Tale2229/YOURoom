package com.example.youroom

import com.example.youroom.domain.*
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class PlanningDisplayTest {
    private fun slot(number: Int,start: Int,end: Int)=LessonSlot(number,LocalTime.ofSecondOfDay(start*60L),LocalTime.ofSecondOfDay(end*60L),emptyList())
    private val slots=listOf(slot(1,480,525),slot(2,530,575))

    @Test fun partialPeriodsArePositionedNotJustFilledFromTheLeft() {
        val actual=PlanningDisplay.portions(slots.first(),listOf(StudyWindow(505,515))).single()
        assertEquals(25/45f,actual.start,.0001f); assertEquals(35/45f,actual.end,.0001f)
    }
    @Test fun separateSelectionsWithinOneLessonDoNotFillTheirGap() {
        val actual=PlanningDisplay.portions(slots.first(),listOf(StudyWindow(480,490),StudyWindow(510,525)))
        assertEquals(2,actual.size);assertEquals(10/45f,actual.first().end,.0001f)
        assertEquals(30/45f,actual.last().start,.0001f)
    }
    @Test fun mergingOnlyCombinesTouchingOrOverlappingValidWindows() {
        val windows=listOf(StudyWindow(20,30),StudyWindow(5,10),StudyWindow(10,25),StudyWindow(40,45),StudyWindow(50,50))
        assertEquals(listOf(StudyWindow(5,30),StudyWindow(40,45)),PlanningDisplay.merge(windows))
        assertEquals(5,windows.size)
    }
    @Test fun breaksAndOutsideTeachingTimeDoNotFalselyColorALesson() {
        assertTrue(PlanningDisplay.portions(slots.first(),listOf(StudyWindow(525,530),StudyWindow(1260,1320))).isEmpty())
        assertTrue(PlanningDisplay.portions(slots[1],emptyList()).isEmpty())
    }
    @Test fun minuteMappingUsesRealDurationWithinEachEqualWidthBarAndWithinGaps() {
        assertEquals(0f,PlanningDisplay.xAt(480f,slots,103f,3f)!!,.0001f)
        assertEquals(50f,PlanningDisplay.xAt(525f,slots,103f,3f)!!,.0001f)
        assertEquals(51.5f,PlanningDisplay.xAt(527.5f,slots,103f,3f)!!,.0001f)
        assertEquals(53f,PlanningDisplay.xAt(530f,slots,103f,3f)!!,.0001f)
        assertEquals(103f,PlanningDisplay.xAt(575f,slots,103f,3f)!!,.0001f)
    }
    @Test fun nonContiguousNumbersAndDifferentCampusTimesAreNotAssumedToBeFortyFiveMinutes() {
        val irregular=listOf(slot(3,500,530),slot(8,600,660))
        assertEquals(25f,PlanningDisplay.xAt(515f,irregular,103f,3f)!!,.0001f)
        assertEquals(78f,PlanningDisplay.xAt(630f,irregular,103f,3f)!!,.0001f)
        assertEquals(.5f,PlanningDisplay.portions(irregular.last(),listOf(StudyWindow(630,660))).single().start,.0001f)
    }
    @Test fun beforeSchoolAndAfterSchoolMarkersClampToTheStripEdges() {
        assertEquals(0f,PlanningDisplay.xAt(0f,slots,103f,3f)!!,0f)
        assertEquals(103f,PlanningDisplay.xAt(1439f,slots,103f,3f)!!,0f)
    }
    @Test fun invalidOrMissingGeometryDoesNotInventAMarker() {
        assertNull(PlanningDisplay.xAt(500f,emptyList(),103f,3f))
        assertNull(PlanningDisplay.xAt(Float.NaN,slots,103f,3f))
        assertNull(PlanningDisplay.xAt(500f,slots,2f,3f))
        assertNull(PlanningDisplay.xAt(500f,slots,103f,-1f))
        assertTrue(PlanningDisplay.portions(slot(1,480,480),listOf(StudyWindow(480,490))).isEmpty())
    }
    @Test fun revisitsShareOneRoomRowWithoutConnectingUnplannedRestTime() {
        val a=Classroom("01:2-201","2-201","2",60)
        val b=Classroom("01:2-202","2-202","2",60)
        val steps=listOf(PlanningStep.Study(480,500,a,true),PlanningStep.Transfer(500,501,a,b,1),
            PlanningStep.Study(501,525,b,false),PlanningStep.Break(525,780),PlanningStep.Study(780,840,a,true))
        val plan=StudyPlan(StudyWindow(480,840),steps,104,80,2,0,1,0,1,0,0)
        val uses=PlanningDisplay.rooms(plan)
        assertEquals(listOf(a,b),uses.map { it.classroom })
        assertEquals(listOf(StudyWindow(480,500),StudyWindow(780,840)),uses.first().windows)
        assertTrue(uses.first().favorite);assertFalse(uses.last().favorite)
        assertEquals(5,plan.steps.size)
    }
    @Test fun roomNamesInDifferentCampusesNeverMerge() {
        val a=Classroom("01:A101","A101","A",60);val b=a.copy(id="04:A101")
        val plan=StudyPlan(StudyWindow(480,600),listOf(PlanningStep.Study(480,500,a,false),PlanningStep.Study(550,600,b,false)),
            70,0,1,1,0,0,0,0,0)
        assertEquals(2,PlanningDisplay.rooms(plan).size)
    }
}
