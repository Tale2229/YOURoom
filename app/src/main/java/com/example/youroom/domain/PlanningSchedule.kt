package com.example.youroom.domain

/** Confirmation is the only editor boundary that validates time input. */
internal object PlanningSchedule {
    fun validate(draft: PlanningDraft, notBefore: Int? = null): PlanningResult? {
        if (draft.savedWindows.size >= PlanningPolicy.periodLimit)
            return PlanningResult(message = "最多支持三个时段。", problem = PlanningProblem.TOO_MANY)
        val current = StudyWindow(draft.start,draft.end)
        // Keep malformed restored values defensive, but fix a past start before asking
        // about duration: "from now" changes the start and may invalidate the old end.
        if (current.start !in 0..1439 || current.end !in 0..1439)
            return PlanningResult(message = "结束时间必须晚于开始时间，暂不支持跨日规划。", problem = PlanningProblem.INVALID_TIME)
        if (notBefore != null && current.start < notBefore - PlanningPolicy.startGraceMinutes)
            return PlanningResult(message = "目标时段已早于当前时间，请选择今天尚未开始的时段。", problem = PlanningProblem.PAST_TIME)
        if (!valid(current))
            return PlanningResult(message = "结束时间必须晚于开始时间，暂不支持跨日规划。", problem = PlanningProblem.INVALID_TIME)
        if (current.duration < PlanningPolicy.minimumPeriodMinutes)
            return PlanningResult(message = "自习时段不能少于30分钟", problem = PlanningProblem.TOO_SHORT)
        if (draft.savedWindows.any { current.start < it.end && it.start < current.end })
            return PlanningResult(message = "时段不能重叠，请调整当前时间。", problem = PlanningProblem.OVERLAP)
        return null
    }

    data class Addition(val goal: PlanningDraft? = null, val error: PlanningResult? = null)
    fun add(draft: PlanningDraft, notBefore: Int? = null): Addition {
        validate(draft,notBefore)?.let { return Addition(error = it) }
        return Addition(goal = draft.copy(start = draft.end, end = minOf(draft.end + 60,1439),
            savedWindows = draft.savedWindows + StudyWindow(draft.start,draft.end)))
    }

    fun remove(draft: PlanningDraft, index: Int): PlanningDraft {
        val removed = draft.savedWindows.getOrNull(index) ?: return draft
        return draft.copy(start = removed.start,end = removed.end,
            savedWindows = draft.savedWindows.filterIndexed { i,_ -> i != index })
    }

    /** No clock or input checks here: these immutable windows have already been confirmed. */
    fun confirmedGoal(draft: PlanningDraft): PlanningGoal? {
        val windows = draft.windows
        val first = windows.firstOrNull() ?: return null
        return PlanningGoal(first.start,first.end,draft.flexible,origin = null,
            savedWindows = windows.drop(1),expectedRooms = draft.expectedRooms)
    }

    /** Defensive restoration is not generation-time validation; never auto-confirm old wheels. */
    fun restore(draft: PlanningDraft): PlanningDraft {
        val retained = mutableListOf<StudyWindow>()
        for (window in draft.savedWindows) {
            if (valid(window) && retained.none { window.start < it.end && it.start < window.end } &&
                retained.size < PlanningPolicy.periodLimit) retained += window
        }
        return draft.copy(start = draft.start.coerceIn(0,1439),end = draft.end.coerceIn(0,1439),savedWindows = retained)
    }

    private fun valid(window: StudyWindow) = window.start in 0..1439 && window.end in 0..1439 && window.duration > 0

    // Raw calculation callers retain defensive validation. The confirmed editor route bypasses it.
    fun validate(goal: PlanningGoal, notBefore: Int? = null): PlanningResult? {
        if (goal.savedWindows.size >= PlanningPolicy.periodLimit)
            return PlanningResult(message = "最多支持三个时段。", problem = PlanningProblem.TOO_MANY)
        if (goal.start !in 0..1439 || goal.end !in 0..1439 || goal.end <= goal.start)
            return PlanningResult(message = "结束时间必须晚于开始时间，暂不支持跨日规划。", problem = PlanningProblem.INVALID_TIME)
        if (goal.savedWindows.any { it.start !in 0..1439 || it.end !in 0..1439 || it.duration <= 0 })
            return PlanningResult(message = "已保存的时段无效，请删除后重新添加。")
        if (goal.windows.zipWithNext().any { (a,b) -> a.end > b.start })
            return PlanningResult(message = "时段不能重叠，请调整当前时间。", problem = PlanningProblem.OVERLAP)
        if (notBefore != null) {
            if (goal.savedWindows.any { it.start < notBefore })
                return PlanningResult(message = "已保存的时段早于当前时间，请删除后重新添加。")
            if (goal.start < notBefore)
                return PlanningResult(message = "目标时段已早于当前时间，请选择今天尚未开始的时段。", problem = PlanningProblem.PAST_TIME)
        }
        return null
    }

}
