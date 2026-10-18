package com.example.youroom.domain

data class TimeTemplate(val id: String, val name: String, val windows: List<StudyWindow>)

data class CampusPlanningPreferences(
    val direction: PlanningDirection = PlanningDirection.BALANCED,
    val flexible: Boolean = true,
    val templates: List<TimeTemplate> = emptyList(),
)

data class PlanningPreferences(val campuses: Map<Campus, CampusPlanningPreferences> = emptyMap()) {
    fun forCampus(campus: Campus) = campuses[campus] ?: CampusPlanningPreferences()
    // Match the storage reader's shape even after an initially missing/caught preference flow.
    fun withCampus(campus: Campus, value: CampusPlanningPreferences) = PlanningPreferences(
        Campus.entries.associateWith { if (it == campus) value else forCampus(it) })
}

internal object TimeTemplates {
    fun valid(windows: List<StudyWindow>): Boolean = windows.size in 1..PlanningPolicy.periodLimit &&
        windows.all { it.start in 0..1439 && it.end in 0..1439 && it.duration >= PlanningPolicy.minimumPeriodMinutes } &&
        windows.sortedBy { it.start }.zipWithNext().none { (a, b) -> a.end > b.start }

    data class Import(val windows: List<StudyWindow> = emptyList(), val skipped: Int = 0, val message: String? = null) {
        val accepted get() = windows.isNotEmpty()
    }

    /** Validate at the moment of import. Failure never partially changes the editor. */
    fun import(windows: List<StudyWindow>, skipInvalid: Boolean, notBefore: Int?): Import {
        if (windows.isEmpty()) return Import(message = "组合中没有时段")
        val retained = mutableListOf<StudyWindow>()
        var firstProblem: String? = null
        var skipped = 0
        windows.sortedBy { it.start }.forEach { window ->
            val error = PlanningSchedule.validate(PlanningDraft(start = window.start, end = window.end,
                savedWindows = retained.toList()), notBefore)
            if (error == null) retained += window else {
                skipped++
                if (firstProblem == null) firstProblem = error.message?.removeSuffix("。")
            }
        }
        if (skipped > 0 && !skipInvalid) return Import(skipped = skipped,
            message = "$firstProblem\n组合未导入")
        if (retained.isEmpty()) return Import(skipped = skipped, message = "没有可导入的合法时段")
        return Import(retained.toList(), skipped,
            if (skipped > 0) "已导入${retained.size}个时段，跳过${skipped}个无效时段" else "已导入${retained.size}个时段")
    }
}

internal data class DefaultPlanSelection(val direction: PlanningDirection, val index: Int)
internal fun PlanningResult.defaultSelection(requested: PlanningDirection): DefaultPlanSelection {
    fun first(direction: PlanningDirection) = groups.firstOrNull { it.direction == direction }?.optionIndices
        ?.firstOrNull { it in options.indices }
    first(requested)?.let { return DefaultPlanSelection(requested, it) }
    return DefaultPlanSelection(PlanningDirection.BALANCED, first(PlanningDirection.BALANCED) ?: 0)
}
