package com.example.youroom

import com.example.youroom.domain.PlanningDraft
import com.example.youroom.ui.PlanningGoalSaver
import org.junit.Assert.*
import org.junit.Test

class PlanningGoalSaverTest {
    @Test fun threeConfirmedPeriodsAndAnUnconfirmedDraftRestoreSeparately() {
        val restored = PlanningGoalSaver.restore(listOf("v3",900,960,false,"",3,
            480,720,720,780,780,840,"01:2-201"))!!
        assertEquals(900,restored.start)
        assertEquals(960,restored.end)
        assertEquals(listOf(com.example.youroom.domain.StudyWindow(480,720),
            com.example.youroom.domain.StudyWindow(720,780),com.example.youroom.domain.StudyWindow(780,840)),restored.windows)
        assertEquals(restored.windows,com.example.youroom.domain.PlanningSchedule.confirmedGoal(restored)!!.windows)
    }

    @Test fun restoringLegacyWheelValuesDoesNotImplicitlyConfirmThem() {
        val restored = PlanningGoalSaver.restore(listOf("v2",780,840,true,"",1,480,720))!!
        assertEquals(listOf(com.example.youroom.domain.StudyWindow(480,720)),restored.windows)
        assertEquals(780,restored.start)
        assertNull(com.example.youroom.domain.PlanningSchedule.confirmedGoal(
            PlanningGoalSaver.restore(listOf(480,720,true,""))!!))
    }
    @Test fun currentTimeAdjustmentAndOriginRestoreWithoutALunchFlag() {
        assertEquals(PlanningDraft(690, 825, false, "main"),
            PlanningGoalSaver.restore(listOf(690, 825, false, "main")))
    }

    @Test fun oldEnabledAndDisabledLunchFlagsAreIgnoredWhileOtherChoicesSurvive() {
        for (oldFlag in listOf(false, true)) {
            assertEquals(PlanningDraft(690, 825, false, "4"),
                PlanningGoalSaver.restore(listOf(690, 825, false, oldFlag, "4")))
            assertEquals(PlanningDraft(480, 685), PlanningGoalSaver.restore(listOf(480, 685, true, oldFlag, "")))
        }
    }
    @Test fun twoSavedPeriodsRestoreAlongsideTheFinalWheelDraft() {
        val goal = PlanningDraft(900,960,false,"2",listOf(com.example.youroom.domain.StudyWindow(480,720),
            com.example.youroom.domain.StudyWindow(780,840)))
        assertEquals(goal,PlanningGoalSaver.restore(listOf(900,960,false,"2",480,720,780,840)))
    }
    @Test fun expectedRoomsRestoreForBothCampusesAlongsideSavedPeriods() {
        assertEquals(PlanningDraft(780,840,false,savedWindows=listOf(com.example.youroom.domain.StudyWindow(480,720)),
            expectedRooms=setOf("01:2-201","04:演示楼-A101")),
            PlanningGoalSaver.restore(listOf("v2",780,840,false,"",1,480,720,"01:2-201","04:演示楼-A101")))
    }
}
