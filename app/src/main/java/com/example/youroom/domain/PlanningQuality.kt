package com.example.youroom.domain

/** Shared admission rules, applied before any direction ranks or fills its five slots.
 * Scores are costs (smaller is better), often negative: use additive regret, not percentages. */
internal object PlanningQuality {
    const val minimumVisitMinutes = 30
    const val maximumScoreRegret = 30
    const val maximumCoverageLoss = 15

    fun usefulVisits(plan: StudyPlan): Boolean = plan.windows.isNotEmpty() && plan.windows.all { window ->
        val studies=plan.steps.filterIsInstance<PlanningStep.Study>().filter {
            it.start>=window.start && it.end<=window.end
        }
        if(studies.isEmpty() || studies.any { it.end<=it.start }) return@all false
        var room=studies.first().classroom.id
        var duration=0
        var changed=false
        for(study in studies) {
            if(study.classroom.id!=room) {
                if(duration<minimumVisitMinutes) return@all false
                changed=true;room=study.classroom.id;duration=0
            }
            // Consecutive pieces of the same room are one visit. Waiting itself
            // is not study credit, and user-created periods are checked separately.
            duration+=study.end-study.start
        }
        !changed || duration>=minimumVisitMinutes
    }

    fun admits(plan: StudyPlan, baseline: StudyPlan): Boolean = usefulVisits(plan) &&
        plan.studyMinutes>=baseline.studyMinutes-maximumCoverageLoss &&
        plan.score.toLong()<=baseline.score.toLong()+maximumScoreRegret
}
