package com.example.youroom.domain

data class PlanningGoal(
    val start: Int = 8 * 60,
    val end: Int = 11 * 60 + 25,
    val flexible: Boolean = true,
    val origin: String? = null,
)

data class StudyWindow(val start: Int, val end: Int) {
    val duration get() = end - start
}

data class RoomLocation(val building: String, val floor: Int?, val number: Int?, val wing: String? = null)

sealed interface PlanningStep {
    val start: Int
    val end: Int
    data class Study(override val start: Int, override val end: Int, val classroom: Classroom,
        val favorite: Boolean) : PlanningStep
    data class Transfer(override val start: Int, override val end: Int, val from: Classroom,
        val to: Classroom, val walkingMinutes: Int) : PlanningStep {
        val waitingMinutes get() = end - start - walkingMinutes
    }
}

data class StudyPlan(
    val window: StudyWindow,
    val steps: List<PlanningStep>,
    val studyMinutes: Int,
    val favoriteMinutes: Int,
    val changes: Int,
    val buildingChanges: Int,
    val walkingMinutes: Int,
    val approachMinutes: Int,
    val lostMinutes: Int,
    val deviation: Int,
    val score: Int,
) {
    val signature get() = "${window.start}:${window.end}:" + steps.joinToString {
        when (it) {
            is PlanningStep.Study -> "${it.classroom.id}@${it.start}-${it.end}"
            is PlanningStep.Transfer -> "move@${it.start}-${it.end}"
        }
    }
}

data class PlanningOption(val label: String, val plan: StudyPlan)
data class PlanningResult(val options: List<PlanningOption> = emptyList(), val message: String? = null)

fun planningTime(minute: Int) = "%02d:%02d".format(java.util.Locale.ROOT, minute / 60, minute % 60)

/** Shared, inspectable policy. Costs are preference penalties, not actual walking time. */
object PlanningPolicy {
    const val shiftLimit = 15
    const val lossLimit = 20
    const val changeLimit = 2
    const val lossPenalty = 3
    const val changePenalty = 20
    const val buildingPenalty = 12
    const val walkingPenalty = 2
    fun score(loss: Int, deviation: Int, changes: Int, buildings: Int, walking: Int,
        approach: Int, favorites: Int) = loss * lossPenalty + deviation + changes * changePenalty +
        buildings * buildingPenalty + (walking + approach) * walkingPenalty - favorites

    fun windows(goal: PlanningGoal): List<StudyWindow> {
        if (!goal.flexible) return listOf(StudyWindow(goal.start, goal.end))
        return (-shiftLimit..shiftLimit step 5).flatMap { shift ->
            (0..lossLimit step 5).flatMap { trimStart ->
                (0..lossLimit - trimStart step 5).map { trimEnd ->
                    StudyWindow(goal.start + shift + trimStart, goal.end + shift - trimEnd)
                }
            }
        }.distinct().filter { admissible(goal, it) }
    }

    fun admissible(goal: PlanningGoal, window: StudyWindow): Boolean {
        val shortening = goal.end - goal.start - window.duration
        val startDelta = window.start - goal.start
        val endDelta = window.end - goal.end
        return window.start >= 0 && window.end < 1440 && window.duration > 0 && shortening in 0..lossLimit &&
            maxOf(endDelta, -shiftLimit) <= minOf(startDelta, shiftLimit)
    }
}
