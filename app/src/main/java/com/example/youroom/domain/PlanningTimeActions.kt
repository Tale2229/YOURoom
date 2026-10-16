package com.example.youroom.domain

import java.time.Clock
import java.time.LocalTime

/** Minute-precision shortcuts; never silently wrap into the next day. */
internal object PlanningTimeActions {
    fun startFromNow(draft: PlanningDraft, clock: Clock): PlanningDraft? =
        startFromNow(PlanningGoal(draft.start,draft.end),clock)?.let { draft.copy(start = it.start) }

    fun endAfterHours(draft: PlanningDraft, hours: Int): PlanningDraft? =
        endAfterHours(PlanningGoal(draft.start,draft.end),hours)?.let { draft.copy(end = it.end) }

    fun startFromNow(goal: PlanningGoal, clock: Clock): PlanningGoal? {
        val now = LocalTime.now(clock.withZone(SchoolZone))
        val start = now.hour * 60 + now.minute + 1
        return if (start < 1440) goal.copy(start = start) else null
    }

    fun endAfterHours(goal: PlanningGoal, hours: Int): PlanningGoal? {
        require(hours in 1..4)
        val end = goal.start + hours * 60
        return if (goal.start in 0..1439 && end in 1..1439) goal.copy(end = end) else null
    }
}
