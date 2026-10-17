package com.example.youroom

import com.example.youroom.domain.*
import com.example.youroom.ui.planningRouteSummary
import org.junit.Assert.*
import org.junit.Test

class PlanningRouteSummaryTest {
    private val a=Classroom("01:3-308","3-308","3",60)
    private val b=Classroom("01:3-405","3-405","3",60)
    private fun plan(rooms: List<Classroom>): StudyPlan {
        val windows=rooms.indices.map { StudyWindow(480+it*120,540+it*120) }
        return StudyPlan(StudyWindow(480,windows.last().end),rooms.mapIndexed { index,room ->
            PlanningStep.Study(windows[index].start,windows[index].end,room,false)
        },rooms.size*60,0,0,0,0,0,0,0,0,windows)
    }
    @Test fun aThreePeriodPlanUsesOnlyRoomNamesAndRouteArrows() {
        assertEquals("3-308 → 3-405 → 3-308",planningRouteSummary(plan(listOf(a,b,a))))
    }
    @Test fun stayingInTheSameRoomAcrossRestPeriodsDoesNotLookLikeDifferentPlans() {
        assertEquals("3-308",planningRouteSummary(plan(listOf(a,a,a))))
        assertEquals("3-308 → 3-405",planningRouteSummary(plan(listOf(a,a,b))))
    }
    @Test fun singlePeriodStillShowsOnlyItsRoomName() {
        assertEquals("3-308",planningRouteSummary(plan(listOf(a))))
    }
}
