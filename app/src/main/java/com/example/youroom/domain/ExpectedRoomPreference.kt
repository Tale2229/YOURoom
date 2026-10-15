package com.example.youroom.domain

import kotlin.random.Random

/** A desired room is a soft preference, never a permission to lose time or add a move.
 * The immediately preceding desired stay cannot be credited until its next neighbor is known.
 * Keeping this tail in DP keys prevents a short fragment from hiding a valid alternative.
 */
internal object ExpectedRoomPreference {
    fun ranks(expected: BooleanArray, random: Random = Random.Default): IntArray {
        val ranks = IntArray(expected.size)
        expected.indices.filter { expected[it] }.shuffled(random).forEachIndexed { rank,index -> ranks[index]=rank }
        return ranks
    }
    data class Tail(val pending: Int = 0, val previousShort: Boolean = false)
    data class Progress(val minutes: Int = 0, val tail: Tail = Tail()) {
        fun append(expected: Boolean, duration: Int, entireShortWindow: Boolean = false,
            changingToNext: Boolean = false, final: Boolean = false): Progress {
            val confirmed = minutes + if (duration >= PlanningPolicy.expectedNeighborMinimum) tail.pending else 0
            val eligible = expected && !tail.previousShort &&
                (duration >= PlanningPolicy.expectedStayMinimum || entireShortWindow)
            val credit = if (eligible) duration else 0
            return if (final || !changingToNext) Progress(confirmed+credit)
            else Progress(confirmed, Tail(credit, duration < PlanningPolicy.expectedNeighborMinimum))
        }
    }

    fun minutes(steps: List<PlanningStep>, windows: List<StudyWindow>, expected: Set<String>): Int =
        windows.sumOf { window ->
            val studies = steps.filterIsInstance<PlanningStep.Study>().filter {
                it.start >= window.start && it.end <= window.end
            }
            studies.withIndex().sumOf { (index, study) ->
                val length = study.end-study.start
                val before = studies.getOrNull(index-1)
                val after = studies.getOrNull(index+1)
                val whole = studies.size == 1 && study.start == window.start && study.end == window.end
                if (study.classroom.id in expected && (length >= PlanningPolicy.expectedStayMinimum || whole) &&
                    (before == null || before.classroom.id == study.classroom.id || before.end-before.start >= PlanningPolicy.expectedNeighborMinimum) &&
                    (after == null || after.classroom.id == study.classroom.id || after.end-after.start >= PlanningPolicy.expectedNeighborMinimum)) length else 0
            }
        }

    private val preferredOrder = compareByDescending<StudyPlan> { it.studyMinutes }.thenBy { it.changes }
        .thenBy { it.buildingChanges }.thenBy { it.walkingMinutes+it.approachMinutes }
        .thenByDescending { it.expectedMinutes }.thenBy { it.score }.thenBy { it.lostMinutes }
        .thenBy { it.deviation }.thenByDescending { it.favoriteMinutes }.thenBy { it.freshnessPenalty }

    fun choose(plans: List<StudyPlan>, baseline: StudyPlan, random: Random = Random.Default): StudyPlan {
        val eligible = plans.filter { it.expectedMinutes > 0 && it.studyMinutes >= baseline.studyMinutes &&
            it.changes <= baseline.changes }.distinctBy { it.signature }
        val best = eligible.minWithOrNull(preferredOrder) ?: return baseline
        // Exactly tied expected rooms are equivalent; randomize once per generated request,
        // not on every composition, and not at the expense of any established criterion.
        return eligible.filter { preferredOrder.compare(it,best) == 0 }.sortedBy { it.signature }.random(random)
    }

    fun options(baseline: PlanningResult, plans: List<StudyPlan>, seed: Long): PlanningResult = baseline.copy(
        options = baseline.options.map { option -> option.copy(plan = choose(
            if (option.label == "原时段方案") plans.filter { it.windows == option.plan.windows } else plans,
            option.plan,Random(seed))) }.distinctBy { it.plan.signature })
}
