package com.example.youroom.domain

import kotlin.math.max

/** Reused primitive set: transition times are minutes within one day, not boxed tree nodes. */
internal class PlanningMinutes {
    private val generations = IntArray(1440)
    private val values = IntArray(1440)
    private var generation = 1
    var size = 0
        private set

    fun clear() {
        size = 0
        if (generation == Int.MAX_VALUE) { generations.fill(0); generation = 1 }
        else generation++
    }

    fun add(minute: Int) {
        if (generations[minute] == generation) return
        generations[minute] = generation
        values[size++] = minute
    }

    fun addClamped(minute: Int, first: Int, last: Int) = add(minute.coerceIn(first,last))
    fun sort() = java.util.Arrays.sort(values,0,size)
    operator fun get(index: Int) = values[index]
}

/** Final-leg breakpoints depend on the target/window, not on the incoming DP label.
 * Cache their exact union; clamping and the current loss budget remain label-specific.
 */
internal class PlanningArrivalCuts(private val data: StudyPlanner.Prepared,
    private val spans: List<StudyPlanner.Span>, private val finals: List<StudyPlanner.Span>,
    private val windowEnd: Int, private val minimumVisitMinutes: Int = 0) {
    private class Cuts(val times: IntArray, val gaps: IntArray)
    private val cache = arrayOfNulls<Cuts>(spans.size)

    fun addTo(minutes: PlanningMinutes, targetIndex: Int, endPlusBudget: Int, first: Int, last: Int) {
        val cuts = cache[targetIndex] ?: prepare(spans[targetIndex]).also { cache[targetIndex] = it }
        for (time in cuts.times) minutes.addClamped(time,first,last)
        for (gap in cuts.gaps) minutes.addClamped(endPlusBudget-gap,first,last)
    }

    private fun prepare(target: StudyPlanner.Span): Cuts {
        val times = mutableSetOf<Int>()
        val gaps = mutableSetOf<Int>()
        for (final in finals) {
            val walk = data.travel[target.room][final.room]
            times += windowEnd-walk-2
            times += final.start-walk-1
            if(minimumVisitMinutes>0) {
                times += final.start-walk-minimumVisitMinutes
                times += windowEnd-walk-minimumVisitMinutes*2
            }
            if (data.hasExpected) {
                times += final.start-walk-PlanningPolicy.expectedStayMinimum
                times += windowEnd-walk-PlanningPolicy.expectedNeighborMinimum*2
                times += windowEnd-walk-PlanningPolicy.expectedNeighborMinimum-1
            }
            gaps += max(walk,final.start-target.end)
        }
        return Cuts(times.toIntArray(),gaps.toIntArray())
    }
}
