package com.example.youroom

import com.example.youroom.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class PlanningTimeActionsTest {
    private fun at(time: String, zone: ZoneId = SchoolZone) = Clock.fixed(
        LocalDateTime.parse("2026-10-13T$time").atZone(SchoolZone).toInstant(), zone)

    @Test fun freshPlanningDefaultsAreEightToTwelve() {
        assertEquals(480, PlanningGoal().start)
        assertEquals(720, PlanningGoal().end)
    }

    @Test fun fromNowAddsOneMinuteAtMinutePrecisionAndKeepsOtherChoices() {
        val goal = PlanningGoal(480, 1200, false, "main")
        assertEquals(goal.copy(start = 15 * 60 + 21), PlanningTimeActions.startFromNow(goal, at("15:20:59")))
        assertEquals(goal.copy(start = 15 * 60 + 21), PlanningTimeActions.startFromNow(goal, at("15:20:00")))
    }

    @Test fun theCurrentTimeAlwaysUsesSchoolTimeNotTheDeviceClockZone() {
        assertEquals(15 * 60 + 21, PlanningTimeActions.startFromNow(PlanningGoal(), at("15:20:00", ZoneOffset.UTC))?.start)
    }

    @Test fun durationShortcutsAreRelativeToTheStartAndKeepOtherChoices() {
        val goal = PlanningGoal(630, 600, false, "4")
        for (hours in listOf(1, 2, 4)) assertEquals(goal.copy(end = 630 + hours * 60),
            PlanningTimeActions.endAfterHours(goal, hours))
    }

    @Test fun shortcutsNeverWrapAcrossMidnight() {
        assertNull(PlanningTimeActions.startFromNow(PlanningGoal(), at("23:59:00")))
        assertEquals(1439, PlanningTimeActions.startFromNow(PlanningGoal(), at("23:58:59"))?.start)
        assertEquals(1439, PlanningTimeActions.endAfterHours(PlanningGoal(1379, 1200), 1)?.end)
        assertNull(PlanningTimeActions.endAfterHours(PlanningGoal(1380, 1200), 1))
        assertNull(PlanningTimeActions.endAfterHours(PlanningGoal(1260, 1200), 4))
    }

    @Test fun invalidStartsAndUnsupportedDurationsCannotCreateInvalidGoals() {
        assertNull(PlanningTimeActions.endAfterHours(PlanningGoal(-1, 720), 1))
        assertNull(PlanningTimeActions.endAfterHours(PlanningGoal(1440, 720), 1))
        assertThrows(IllegalArgumentException::class.java) { PlanningTimeActions.endAfterHours(PlanningGoal(), 3) }
    }
}
