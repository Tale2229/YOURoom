package com.example.youroom.domain

/** Editor state: wheel values are a draft; only '+'-confirmed windows enter a request. */
data class PlanningDraft(
    val start: Int = 8 * 60,
    val end: Int = 12 * 60,
    val flexible: Boolean = true,
    val origin: String? = null,
    val savedWindows: List<StudyWindow> = emptyList(),
    val expectedRooms: Set<String> = emptySet(),
) {
    val windows get() = savedWindows.sortedBy { it.start }
}

/** Calculation input, deliberately separate from the editable wheel draft. */
data class PlanningGoal(
    val start: Int = 8 * 60,
    val end: Int = 12 * 60,
    val flexible: Boolean = true,
    val origin: String? = null,
    val savedWindows: List<StudyWindow> = emptyList(),
    val expectedRooms: Set<String> = emptySet(),
) {
    val windows get() = (savedWindows + StudyWindow(start, end)).sortedBy { it.start }
    val lossLimit get() = if (savedWindows.isEmpty()) PlanningPolicy.lossLimit else PlanningPolicy.multiLossLimit
}

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
    data class Break(override val start: Int, override val end: Int) : PlanningStep
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
    val windows: List<StudyWindow> = listOf(window),
    val freshnessPenalty: Int = 0,
    val expectedMinutes: Int = 0,
) {
    val signature get() = windows.joinToString { "${it.start}:${it.end}" } + ":" + steps.joinToString {
        when (it) {
            is PlanningStep.Study -> "${it.classroom.id}@${it.start}-${it.end}"
            is PlanningStep.Transfer -> "move@${it.start}-${it.end}"
            is PlanningStep.Break -> "break@${it.start}-${it.end}"
        }
    }
}

data class PlanningOption(val label: String, val plan: StudyPlan)
enum class PlanningProblem { INVALID_TIME, PAST_TIME, OVERLAP, TOO_MANY }
enum class PlanningDirection(val title: String, val shortTitle: String) {
    BALANCED("综合推荐", "综合"), CHANGES("换教室最少", "少换教室"),
    BUILDINGS("跨教学楼最少", "少跨楼"), FAVORITES("收藏教室覆盖最多", "多收藏"),
    EXPECTED("期望教室覆盖最多", "多期望")
}

/** Indices reference one immutable, deduplicated option pool within a generation. */
data class PlanningGroup(val direction: PlanningDirection, val optionIndices: List<Int>, val message: String? = null)

data class PlanningResult(val options: List<PlanningOption> = emptyList(), val message: String? = null,
    val problem: PlanningProblem? = null, val groups: List<PlanningGroup> = emptyList())

fun planningTime(minute: Int) = "%02d:%02d".format(java.util.Locale.ROOT, minute / 60, minute % 60)

/** Shared, inspectable policy. Costs are preference penalties, not actual walking time. */
object PlanningPolicy {
    const val teachingStart = 8 * 60
    const val teachingEnd = 20 * 60 + 55
    const val shiftLimit = 15
    const val lossLimit = 20
    const val multiLossLimit = 30
    const val periodLimit = 3
    const val startGraceMinutes = 5
    const val changeLimit = 2
    const val expectedStayMinimum = 30
    const val expectedNeighborMinimum = 30
    const val lossPenalty = 3
    const val changePenalty = 20
    const val buildingPenalty = 12
    const val walkingPenalty = 2
    fun score(loss: Int, deviation: Int, changes: Int, buildings: Int, walking: Int,
        approach: Int, favorites: Int) = loss * lossPenalty + deviation + changes * changePenalty +
        buildings * buildingPenalty + (walking + approach) * walkingPenalty - favorites

    fun windows(goal: PlanningGoal, lossLimit: Int = goal.lossLimit): List<StudyWindow> {
        if (!goal.flexible) return listOf(StudyWindow(goal.start, goal.end))
        return (-shiftLimit..shiftLimit step 5).flatMap { shift ->
            (0..lossLimit step 5).flatMap { trimStart ->
                (0..lossLimit - trimStart step 5).map { trimEnd ->
                    StudyWindow(goal.start + shift + trimStart, goal.end + shift - trimEnd)
                }
            }
        }.distinct().filter { admissible(goal, it, lossLimit) }
    }

    fun admissible(goal: PlanningGoal, window: StudyWindow, lossLimit: Int = goal.lossLimit): Boolean {
        val shortening = goal.end - goal.start - window.duration
        val startDelta = window.start - goal.start
        val endDelta = window.end - goal.end
        return window.start >= 0 && window.end < 1440 && window.duration > 0 && shortening in 0..lossLimit &&
            maxOf(endDelta, -shiftLimit) <= minOf(startDelta, shiftLimit)
    }
}
