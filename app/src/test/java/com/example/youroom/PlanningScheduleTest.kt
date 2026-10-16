package com.example.youroom

import com.example.youroom.domain.*
import org.junit.Assert.*
import org.junit.Test

class PlanningScheduleTest {
    @Test fun anUnconfirmedDraftIsNeverARequest() {
        val draft = PlanningDraft()
        assertTrue(draft.windows.isEmpty())
        assertNull(PlanningSchedule.confirmedGoal(draft))
    }

    @Test fun additionConfirmsOnlyTheDraftAndPreparesTheNextHour() {
        val original = PlanningDraft(480,720,false,"2",expectedRooms=setOf("01:2-201"))
        val next = PlanningSchedule.add(original).goal!!
        assertEquals(original.copy(start=720,end=780,savedWindows=listOf(StudyWindow(480,720))),next)
        val request = PlanningSchedule.confirmedGoal(next)!!
        assertEquals(listOf(StudyWindow(480,720)),request.windows)
        assertEquals(20,request.lossLimit)
        assertNull(request.origin)
        assertEquals(original.expectedRooms,request.expectedRooms)
    }

    @Test fun allThreePeriodsMustBeConfirmedAndAFourthIsRejected() {
        val first = PlanningSchedule.add(PlanningDraft()).goal!!
        val second = PlanningSchedule.add(first).goal!!
        val third = PlanningSchedule.add(second).goal!!
        assertEquals(3,third.savedWindows.size)
        assertEquals(listOf(StudyWindow(480,720),StudyWindow(720,780),StudyWindow(780,840)),third.windows)
        assertEquals(840,third.start)
        assertEquals(900,third.end)
        assertEquals(30,PlanningSchedule.confirmedGoal(third)!!.lossLimit)
        assertEquals(PlanningProblem.TOO_MANY,PlanningSchedule.add(third).error!!.problem)
    }

    @Test fun overlapIsRejectedButTouchingAndUnorderedDisjointPeriodsAreLegal() {
        val draft = PlanningDraft(720,780,savedWindows=listOf(StudyWindow(480,720)))
        assertNull(PlanningSchedule.validate(draft))
        assertEquals(PlanningProblem.OVERLAP,PlanningSchedule.add(draft.copy(start=719)).error!!.problem)
        val earlier = PlanningSchedule.add(draft.copy(start=300,end=400)).goal!!
        assertEquals(listOf(StudyWindow(300,400),StudyWindow(480,720)),PlanningSchedule.confirmedGoal(earlier)!!.windows)
    }

    @Test fun invalidDraftIsRejectedWithoutAffectingExistingConfirmedWindows() {
        val draft = PlanningDraft(720,720,savedWindows=listOf(StudyWindow(480,720)))
        assertEquals(PlanningProblem.INVALID_TIME,PlanningSchedule.add(draft).error!!.problem)
        assertEquals(PlanningProblem.INVALID_TIME,PlanningSchedule.add(draft.copy(end=1440)).error!!.problem)
        assertEquals(listOf(StudyWindow(480,720)),PlanningSchedule.confirmedGoal(draft)!!.windows)
    }

    @Test fun newConfirmationsRequireThirtyMinutesAndRejectingNeverChangesSavedPeriodsOrPreferences() {
        val original=PlanningDraft(780,809,savedWindows=listOf(StudyWindow(480,720)),
            expectedRooms=setOf("01:3-305"))
        for (duration in listOf(1,5,20,29)) {
            val draft=original.copy(end=original.start+duration)
            val rejected=PlanningSchedule.add(draft)
            assertNull(rejected.goal)
            assertEquals(PlanningProblem.TOO_SHORT,rejected.error!!.problem)
            assertEquals("自习时段不能少于30分钟",rejected.error.message)
            assertEquals(original.savedWindows,draft.savedWindows)
            assertEquals(original.expectedRooms,draft.expectedRooms)
        }
        for (duration in listOf(30,31,60)) {
            val added=PlanningSchedule.add(original.copy(end=original.start+duration)).goal!!
            assertEquals(original.savedWindows+StudyWindow(780,780+duration),added.savedWindows)
        }
    }

    @Test fun theNewConfirmationMinimumDoesNotPurgeOlderSavedPeriodsOrRevalidateOnGeneration() {
        val older=PlanningDraft(800,810,savedWindows=listOf(StudyWindow(600,615)))
        assertEquals(older,PlanningSchedule.restore(older))
        assertEquals(older.savedWindows,PlanningSchedule.confirmedGoal(older)!!.windows)
        assertEquals(PlanningProblem.INVALID_TIME,PlanningSchedule.add(older.copy(end=800)).error!!.problem)
        assertEquals(PlanningProblem.TOO_SHORT,PlanningSchedule.add(PlanningDraft(1410,1439)).error!!.problem)
    }

    @Test fun exactlyFiveLateMinutesAreAllowedButTheNextMinuteIsNot() {
        for (now in 480..485) assertNotNull(PlanningSchedule.add(PlanningDraft(),now).goal)
        assertEquals(PlanningProblem.PAST_TIME,PlanningSchedule.add(PlanningDraft(),486).error!!.problem)
        assertEquals(PlanningProblem.PAST_TIME,PlanningSchedule.add(PlanningDraft(),500).error!!.problem)
    }

    @Test fun anEarlierConfirmedPeriodIsNotRecheckedWhenAddingALaterOne() {
        val draft = PlanningDraft(780,840,savedWindows=listOf(StudyWindow(480,720)))
        assertNotNull(PlanningSchedule.add(draft,782).goal)
        assertEquals(2,PlanningSchedule.add(draft,782).goal!!.savedWindows.size)
    }

    @Test fun lateAdditionNeverWrapsAndATargetEndingAt2359CanBeConfirmed() {
        val next = PlanningSchedule.add(PlanningDraft(1350,1410)).goal!!
        assertEquals(1410,next.start)
        assertEquals(1439,next.end)
        val last = PlanningSchedule.add(PlanningDraft(1400,1439)).goal!!
        assertEquals(listOf(StudyWindow(1400,1439)),last.windows)
        assertEquals(1439,last.start)
        assertEquals(1439,last.end)
        assertEquals(PlanningProblem.INVALID_TIME,PlanningSchedule.add(last).error!!.problem)
    }

    @Test fun removingAPeriodRestoresItsTimesInTheWheelsAndKeepsPreferences() {
        val draft = PlanningDraft(1000,1100,false,"main",
            listOf(StudyWindow(780,840),StudyWindow(480,720)),setOf("01:2-201"))
        val removed = PlanningSchedule.remove(draft,1)
        assertEquals(draft.copy(start=480,end=720,savedWindows=listOf(StudyWindow(780,840))),removed)
        assertEquals(listOf(StudyWindow(780,840)),PlanningSchedule.confirmedGoal(removed)!!.windows)
        assertEquals(removed,PlanningSchedule.remove(removed,9))
    }

    @Test fun deletingAndReconfirmingDoesNotDuplicateTheWindow() {
        val once = PlanningSchedule.add(PlanningDraft()).goal!!
        val removed = PlanningSchedule.remove(once,0)
        assertNull(PlanningSchedule.confirmedGoal(removed))
        assertEquals(once,PlanningSchedule.add(removed).goal)
    }

    @Test fun restorationDiscardsInvalidOverlappingAndExcessConfirmationsWithoutConfirmingTheDraft() {
        val draft = PlanningDraft(-1,1500,savedWindows=listOf(StudyWindow(600,700),StudyWindow(650,720),
            StudyWindow(500,400),StudyWindow(0,60),StudyWindow(700,800),StudyWindow(900,1000)))
        val restored = PlanningSchedule.restore(draft)
        assertEquals(listOf(StudyWindow(600,700),StudyWindow(0,60),StudyWindow(700,800)),restored.savedWindows)
        assertEquals(0,restored.start)
        assertEquals(1439,restored.end)
    }

    @Test fun confirmedRequestsKeepSingleAndMultipleLossBudgetsIndependentOfTheDraft() {
        val one = PlanningDraft(1000,1000,savedWindows=listOf(StudyWindow(480,540)))
        val two = one.copy(savedWindows=one.savedWindows+StudyWindow(600,660))
        assertEquals(20,PlanningSchedule.confirmedGoal(one)!!.lossLimit)
        assertEquals(30,PlanningSchedule.confirmedGoal(two)!!.lossLimit)
        assertEquals(PlanningSchedule.confirmedGoal(one),PlanningSchedule.confirmedGoal(one.copy(start=-1,end=1600)))
    }
}
