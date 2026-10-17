package com.example.youroom.domain

/** Presentation-only intervals. They never feed back into, or rerun, the planner. */
internal data class PlanningRoomUse(val classroom: Classroom, val favorite: Boolean, val windows: List<StudyWindow>)
internal data class SlotPortion(val start: Float, val end: Float)

internal object PlanningDisplay {
    fun merge(windows: List<StudyWindow>): List<StudyWindow> {
        val merged=mutableListOf<StudyWindow>()
        for (window in windows.filter { it.duration>0 }.sortedBy { it.start }) {
            val last=merged.lastOrNull()
            if (last!=null && window.start<=last.end) merged[merged.lastIndex]=StudyWindow(last.start,maxOf(last.end,window.end))
            else merged+=window
        }
        return merged
    }

    /** Preserve first-visit order and display walking minutes as part of adjacent
     * room use. Real waiting / user breaks remain gaps; no score or step is edited. */
    fun rooms(plan: StudyPlan, snapshot: ClassroomSnapshot? = null): List<PlanningRoomUse> {
        val ranges = plan.steps.map { if (it is PlanningStep.Study) StudyWindow(it.start,it.end) else null }.toMutableList()
        val availability = snapshot?.let { ClassroomStudyAvailability(it.slots) }
        val intervals = mutableMapOf<String,List<StudyWindow>>()
        fun span(study: PlanningStep.Study): StudyWindow? {
            val window = plan.windows.firstOrNull { study.start >= it.start && study.end <= it.end } ?: return null
            if (snapshot == null || availability == null) return window
            val available = intervals.getOrPut(study.classroom.id) {
                availability.intervalsFor(snapshot.index.freeSlotsByRoom[study.classroom.id].orEmpty())
            }.firstOrNull { it.start <= study.start && it.end >= study.end } ?: return null
            return StudyWindow(maxOf(window.start,available.start), minOf(window.end,available.end))
        }
        plan.steps.forEachIndexed { index, step ->
            if (step !is PlanningStep.Transfer) return@forEachIndexed
            val before = plan.steps.getOrNull(index-1) as? PlanningStep.Study ?: return@forEachIndexed
            val after = plan.steps.getOrNull(index+1) as? PlanningStep.Study ?: return@forEachIndexed
            if (before.end != step.start || after.start != step.end || before.classroom.id != step.from.id ||
                after.classroom.id != step.to.id) return@forEachIndexed
            val walking = step.walkingMinutes.coerceIn(0,(step.end-step.start).coerceAtLeast(0))
            // Prefer filling the incoming room backwards. If it only becomes free
            // at arrival, keep the minutes with the outgoing room instead.
            val incoming = minOf(walking, (after.start-maxOf(step.start,span(after)?.start ?: after.start)).coerceAtLeast(0))
            val outgoing = minOf(walking-incoming,(minOf(step.end,span(before)?.end ?: before.end)-before.end).coerceAtLeast(0))
            ranges[index+1] = ranges[index+1]!!.copy(start=after.start-incoming)
            ranges[index-1] = ranges[index-1]!!.copy(end=before.end+outgoing)
        }
        return plan.steps.withIndex().filter { it.value is PlanningStep.Study }
            .groupBy { (it.value as PlanningStep.Study).classroom.id }.values.map { visits ->
                val studies = visits.map { it.value as PlanningStep.Study }
                PlanningRoomUse(studies.first().classroom,studies.any { it.favorite },merge(visits.map { ranges[it.index]!! }))
            }
    }

    fun portions(slot: LessonSlot, windows: List<StudyWindow>): List<SlotPortion> {
        val start=slot.start.toSecondOfDay()/60f
        val end=slot.end.toSecondOfDay()/60f
        if (end<=start) return emptyList()
        return merge(windows).mapNotNull {
            val from=maxOf(start,it.start.toFloat()); val to=minOf(end,it.end.toFloat())
            if (from>=to) null else SlotPortion((from-start)/(end-start),(to-start)/(end-start))
        }
    }

    /** Equal-width teaching bars, proportional time within each bar and within each gap.
     * Times outside teaching hours clamp to an edge; exact times remain in room captions. */
    fun xAt(minute: Float, slots: List<LessonSlot>, width: Float, gap: Float,
        layout: PeriodStripLayout = PeriodStripLayout.EQUAL): Float? {
        if (!minute.isFinite() || !width.isFinite() || !gap.isFinite() || gap<0 || slots.isEmpty()) return null
        if(layout==PeriodStripLayout.TIME_PROPORTIONAL) {
            if(width<=0 || slots.any { it.end<=it.start } || slots.zipWithNext().any { (a,b) -> b.start<a.end }) return null
            val first=slots.first().start.toSecondOfDay()/60f
            val last=slots.last().end.toSecondOfDay()/60f
            return ((minute-first)/(last-first)).coerceIn(0f,1f)*width
        }
        val bar=(width-gap*(slots.size-1))/slots.size
        if (bar<=0) return null
        val first=slots.first().start.toSecondOfDay()/60f
        if (minute<=first) return 0f
        slots.forEachIndexed { index,slot ->
            val start=slot.start.toSecondOfDay()/60f
            val end=slot.end.toSecondOfDay()/60f
            if (end<=start) return null
            if (minute<=end) return (index*(bar+gap)+(minute-start)/(end-start)*bar).coerceIn(0f,width)
            slots.getOrNull(index+1)?.let { next ->
                val nextStart=next.start.toSecondOfDay()/60f
                if (minute<nextStart) return index*(bar+gap)+bar+(minute-end)/(nextStart-end)*gap
            }
        }
        return width
    }
}

/** One toggle per drag. A short pull and a fling alone cannot reopen the editor. */
internal class PlanningPanelGesture(private val collapseDistance: Float, private val expandDistance: Float) {
    enum class Action { COLLAPSE, EXPAND }
    data class Decision(val consume: Boolean, val action: Action? = null)
    private var distance=0f
    private var direction=0
    var claimed=false; private set
    private var committed=false
    fun reset() { distance=0f; direction=0; claimed=false; committed=false }
    fun drag(delta: Float, collapsed: Boolean, atTop: Boolean): Decision {
        if (committed) return Decision(true)
        val next=when {
            !collapsed && delta<0 -> -1
            collapsed && atTop && delta>0 -> 1
            else -> 0
        }
        if (next==0) { distance=0f; direction=0; return Decision(false) }
        if (next!=direction) distance=0f
        direction=next; claimed=true; distance+=kotlin.math.abs(delta)
        if (distance<(if (next<0) collapseDistance else expandDistance)) return Decision(true)
        committed=true
        return Decision(true,if (next<0) Action.COLLAPSE else Action.EXPAND)
    }
}
