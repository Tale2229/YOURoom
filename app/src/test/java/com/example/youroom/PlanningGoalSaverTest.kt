package com.example.youroom

import com.example.youroom.domain.PlanningGoal
import com.example.youroom.ui.PlanningGoalSaver
import org.junit.Assert.*
import org.junit.Test

class PlanningGoalSaverTest {
    @Test fun currentTimeAdjustmentAndOriginRestoreWithoutALunchFlag() {
        assertEquals(PlanningGoal(690, 825, false, "main"),
            PlanningGoalSaver.restore(listOf(690, 825, false, "main")))
    }

    @Test fun oldEnabledAndDisabledLunchFlagsAreIgnoredWhileOtherChoicesSurvive() {
        for (oldFlag in listOf(false, true)) {
            assertEquals(PlanningGoal(690, 825, false, "4"),
                PlanningGoalSaver.restore(listOf(690, 825, false, oldFlag, "4")))
            assertEquals(PlanningGoal(480, 685), PlanningGoalSaver.restore(listOf(480, 685, true, oldFlag, "")))
        }
    }
}
