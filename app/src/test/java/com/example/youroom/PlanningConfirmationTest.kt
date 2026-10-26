package com.example.youroom

import com.example.youroom.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class PlanningConfirmationTest {
    private val day = LocalDate.of(2026,10,15)
    private val room = Classroom("01:2-201","2-201","2",60)
    private val data = ClassroomSnapshot("01",day,Instant.EPOCH,
        listOf(LessonSlot(1,LocalTime.of(8,0),LocalTime.of(12,0),listOf(room))))

    @Test fun aFiveMinuteLateConfirmationIsPlannedWithoutClampingItsStart() {
        val accepted = PlanningSchedule.add(PlanningDraft(480,540,false),485).goal!!
        val result = StudyPlanner.planConfirmed(data,Campus.XITUCHENG,day,emptyMap(),PlanningSchedule.confirmedGoal(accepted)!!)
        assertNull(result.problem)
        assertEquals(StudyWindow(480,540),result.options.first().plan.window)
        assertEquals(60,result.options.first().plan.studyMinutes)
    }

    @Test fun invalidWheelValuesCannotInvalidateAnAlreadyConfirmedRequest() {
        val accepted = PlanningSchedule.add(PlanningDraft(480,540,false)).goal!!.copy(start=700,end=600)
        val result = StudyPlanner.planConfirmed(data,Campus.XITUCHENG,day,emptyMap(),PlanningSchedule.confirmedGoal(accepted)!!)
        assertEquals(listOf(StudyWindow(480,540)),result.options.first().plan.windows)
        assertNull(result.problem)
    }

    @Test fun theConfirmedRouteStillProtectsCampusDateAndSlotData() {
        val goal = PlanningSchedule.confirmedGoal(PlanningSchedule.add(PlanningDraft()).goal!!)!!
        assertTrue(StudyPlanner.planConfirmed(data,Campus.SHAHE,day,emptyMap(),goal).options.isEmpty())
        assertTrue(StudyPlanner.planConfirmed(data,Campus.XITUCHENG,day.plusDays(1),emptyMap(),goal).options.isEmpty())
        assertTrue(StudyPlanner.planConfirmed(data.copy(slots=emptyList()),Campus.XITUCHENG,day,emptyMap(),goal).options.isEmpty())
    }

    @Test fun theConfirmedRouteStillHonorsExclusionsAndExpectedRooms() {
        val draft = PlanningDraft(480,540,false,expectedRooms=setOf(room.id))
        val goal = PlanningSchedule.confirmedGoal(PlanningSchedule.add(draft).goal!!)!!
        val preferred = StudyPlanner.planConfirmed(data,Campus.XITUCHENG,day,emptyMap(),goal)
        assertEquals(60,preferred.options.first().plan.expectedMinutes)
        val excluded = StudyPlanner.planConfirmed(data,Campus.XITUCHENG,day,
            mapOf(room.id to room.emptyProfile().copy(excluded=true)),goal)
        assertTrue(excluded.options.isEmpty())
    }

    @Test fun multipleConfirmationsKeepTheUserGapAndIgnoreTheNextDraft() {
        val first = PlanningSchedule.add(PlanningDraft(480,540,false)).goal!!
        val accepted = PlanningSchedule.add(first.copy(start=600,end=660)).goal!!
        val result = StudyPlanner.planConfirmed(data,Campus.XITUCHENG,day,emptyMap(),PlanningSchedule.confirmedGoal(accepted)!!)
        assertEquals(listOf(StudyWindow(480,540),StudyWindow(600,660)),result.options.first().plan.windows)
        assertEquals(120,result.options.first().plan.studyMinutes)
        assertEquals(1,result.options.first().plan.steps.filterIsInstance<PlanningStep.Break>().size)
    }
}
