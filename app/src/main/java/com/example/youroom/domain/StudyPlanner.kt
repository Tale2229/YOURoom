package com.example.youroom.domain

import java.time.LocalDate
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Time-forward label-setting DP, jointly scoring candidate windows and complete routes.
 * Maximum two room changes; all generated transfer schedules are minute-resolved.
 * For linear favorite rewards, arrival candidates are the endpoints and the final-leg
 * feasibility breakpoints. Keeping loss and arrival in the key preserves future feasibility.
 */
object StudyPlanner {
    private data class Span(val room: Int, val start: Int, val end: Int)
    private data class Key(val span: Int, val arrival: Int, val changes: Int, val loss: Int)
    private data class Label(val key: Key, val favorites: Int, val walking: Int, val buildings: Int,
        val approach: Int, val prefix: Int, val steps: List<PlanningStep>)
    private data class Prepared(val rooms: List<Classroom>, val favorite: BooleanArray,
        val locations: List<RoomLocation>, val travel: Array<IntArray>, val spans: List<Span>)

    fun plan(snapshot: ClassroomSnapshot, campus: Campus, today: LocalDate,
        profiles: Map<String, RoomProfile>, goal: PlanningGoal, notBeforeMinute: Int? = null,
        checkCancelled: () -> Unit = {}): PlanningResult {
        if (snapshot.campusId != campus.dataKey || !snapshot.isCurrentOn(today))
            return PlanningResult(message = "请先获取当前校区的今日数据。")
        val duration = goal.end - goal.start
        if (goal.start !in 0..1439 || goal.end !in 0..1439 || duration <= 0)
            return PlanningResult(message = "结束时间必须晚于开始时间，暂不支持跨日规划。", problem = PlanningProblem.INVALID_TIME)
        if (snapshot.slots.isEmpty()) return PlanningResult(message = "今日节次数据不完整。")
        if (notBeforeMinute != null && (goal.end <= notBeforeMinute ||
            goal.start + (if (goal.flexible) PlanningPolicy.shiftLimit + PlanningPolicy.lossLimit else 0) < notBeforeMinute))
            return PlanningResult(message = "目标时段已早于当前时间，请选择今天尚未开始的时段。", problem = PlanningProblem.PAST_TIME)
        val prepared = prepare(snapshot, campus, profiles, checkCancelled)
        if (prepared.rooms.isEmpty()) return PlanningResult(message = "当前数据中没有可规划的教室。")
        val windows = candidates(goal, snapshot.slots).filter { notBeforeMinute == null || it.start >= notBeforeMinute }
        var balanced: StudyPlan? = null
        var original: StudyPlan? = null
        var quiet: StudyPlan? = null
        for (window in windows) {
            checkCancelled()
            val plans = solve(prepared, campus, goal, window, notBeforeMinute, checkCancelled)
            for (plan in plans) {
                if (balanced == null || compare(plan, balanced) < 0) balanced = plan
                if (window.start == goal.start && window.end == goal.end && (original == null || compare(plan, original) < 0)) original = plan
                if (quiet == null || quietOrder.compare(plan, quiet) < 0) quiet = plan
            }
        }
        val options = listOfNotNull(balanced?.let { PlanningOption("推荐方案", it) },
            original?.let { PlanningOption("原时段方案", it) }, quiet?.let { PlanningOption("少搬动方案", it) })
            .distinctBy { it.plan.signature }
        return if (options.isNotEmpty()) PlanningResult(options) else PlanningResult(message =
            "难以满足要求，可缩短时段")
    }

    private val order = compareBy<StudyPlan> { it.score }.thenBy { it.changes }.thenBy { it.buildingChanges }
        .thenBy { it.lostMinutes }.thenBy { it.deviation }.thenByDescending { it.favoriteMinutes }.thenBy { it.signature }
    private val quietOrder = compareBy<StudyPlan> { it.changes }.thenBy { it.buildingChanges }
        .thenBy { it.walkingMinutes + it.approachMinutes }.thenBy { it.lostMinutes }.thenBy { it.deviation }.then(order)
    private fun compare(a: StudyPlan, b: StudyPlan) = order.compare(a, b)

    private fun candidates(goal: PlanningGoal, slots: List<LessonSlot>): List<StudyWindow> {
        if (!goal.flexible) return PlanningPolicy.windows(goal)
        val base = PlanningPolicy.windows(goal)
        val starts = base.map { it.start }.toMutableSet()
        val ends = base.map { it.end }.toMutableSet()
        slots.flatMap { listOf(it.start.hour * 60 + it.start.minute, it.end.hour * 60 + it.end.minute) }.forEach {
            if (it - goal.start in -PlanningPolicy.shiftLimit..(PlanningPolicy.shiftLimit + PlanningPolicy.lossLimit)) starts += it
            if (it - goal.end in -(PlanningPolicy.shiftLimit + PlanningPolicy.lossLimit)..PlanningPolicy.shiftLimit) ends += it
        }
        val pairs = starts.flatMap { start -> ends.map { end -> StudyWindow(start, end) } } +
            starts.map { StudyWindow(it, goal.end + it - goal.start) } +
            ends.map { StudyWindow(goal.start + it - goal.end, it) }
        return pairs.distinct().filter { PlanningPolicy.admissible(goal, it) }
            .sortedWith(compareBy<StudyWindow> { abs(it.start - goal.start) + abs(it.end - goal.end) }.thenBy { it.start })
    }

    private fun prepare(snapshot: ClassroomSnapshot, campus: Campus, profiles: Map<String, RoomProfile>,
        check: () -> Unit): Prepared {
        val rooms = snapshot.classrooms.filter { it.id.startsWith("${campus.dataKey}:") && profiles[it.id]?.excluded != true }
        val favorite = BooleanArray(rooms.size) { profiles[rooms[it].id]?.favorite == true }
        val locations = rooms.map { CampusTopology.location(it, campus) }
        val travel = Array(rooms.size) { first -> check(); IntArray(rooms.size) { second ->
            if (first == second) 0 else CampusTopology.transferMinutes(campus, locations[first], locations[second])
        } }
        val spans = mutableListOf<Span>()
        val availability = ClassroomStudyAvailability(snapshot.slots)
        rooms.forEachIndexed { index, room ->
            check()
            availability.intervalsFor(snapshot.index.freeSlotsByRoom.getValue(room.id)).forEach {
                spans += Span(index, it.start, it.end)
            }
        }
        return Prepared(rooms, favorite, locations, travel, spans)
    }

    private fun solve(data: Prepared, campus: Campus, goal: PlanningGoal, window: StudyWindow, notBefore: Int?,
        check: () -> Unit): List<StudyPlan> {
        val windowLoss = goal.end - goal.start - window.duration
        val budget = PlanningPolicy.lossLimit - windowLoss
        if (budget < 0) return emptyList()
        val spans = data.spans.filter { it.start < window.end && it.end > window.start }
        val finals = spans.filter { it.start < window.end && it.end >= window.end }
        val completed = mutableListOf<StudyPlan>()
        val best = mutableMapOf<Key, Label>()
        val bestQuiet = mutableMapOf<Key, Label>()
        val quietPrefix = compareBy<Label> { it.buildings }.thenBy { it.walking + it.approach }.thenBy { it.prefix }
        val queue = PriorityQueue<Label>(compareBy<Label> { it.key.arrival }.thenBy { it.prefix }.thenBy { it.key.span })
        fun enqueue(label: Label) {
            val winsScore = (best[label.key]?.prefix ?: Int.MAX_VALUE) > label.prefix
            val previousQuiet = bestQuiet[label.key]
            val winsQuiet = previousQuiet == null || quietPrefix.compare(label, previousQuiet) < 0
            if (winsScore) best[label.key] = label
            if (winsQuiet) bestQuiet[label.key] = label
            if (winsScore || winsQuiet) queue.add(label)
        }
        spans.forEachIndexed { index, span ->
            if (span.start <= window.start && span.end > window.start) {
                val approach = CampusTopology.approachMinutes(campus, goal.origin, data.locations[span.room])
                if (notBefore == null || window.start >= notBefore + approach)
                    enqueue(Label(Key(index, window.start, 0, 0), 0, 0, 0, approach, approach * 2, emptyList()))
            }
        }
        val deviation = abs(window.start - goal.start) + abs(window.end - goal.end)
        fun finish(label: Label, span: Span) {
            val length = window.end - label.key.arrival
            val favorites = label.favorites + if (data.favorite[span.room]) length else 0
            val loss = windowLoss + label.key.loss
            val last = PlanningStep.Study(label.key.arrival, window.end, data.rooms[span.room],
                data.favorite[span.room])
            completed += StudyPlan(window, label.steps + last, window.duration - label.key.loss,
                favorites, label.key.changes, label.buildings, label.walking, label.approach, loss, deviation,
                PlanningPolicy.score(loss, deviation, label.key.changes, label.buildings, label.walking, label.approach, favorites))
        }
        // A conservative lower bound: even a perfectly favored moving route pays
        // at least one change. Skip graph expansion only when a direct plan beats it.
        queue.toList().filter { spans[it.key.span].end >= window.end }.forEach { finish(it, spans[it.key.span]) }
        val favoritePossible = spans.filter { data.favorite[it.room] }.map {
            StudyWindow(max(it.start, window.start), min(it.end, window.end))
        }.sortedBy { it.start }.fold(mutableListOf<StudyWindow>()) { merged, interval ->
            if (merged.isNotEmpty() && merged.last().end >= interval.start) {
                val last = merged.removeAt(merged.lastIndex); merged += StudyWindow(last.start, max(last.end, interval.end))
            } else merged += interval
            merged
        }.sumOf { it.duration }
        val hasSeparateSameRoomSpans = spans.groupingBy { it.room }.eachCount().values.any { it > 1 }
        val movingLowerBound = windowLoss * 3 + deviation + (if (hasSeparateSameRoomSpans) 0 else 20) - favoritePossible
        if (completed.minByOrNull { it.score }?.score?.let { it <= movingLowerBound } == true) return completed
        completed.clear()
        var iterations = 0
        while (queue.isNotEmpty()) {
            if (iterations++ % 64 == 0) check()
            val label = queue.remove()
            if (best[label.key] !== label && bestQuiet[label.key] !== label) continue
            val current = spans[label.key.span]
            if (current.end >= window.end) finish(label, current)
            for ((index, target) in spans.withIndex()) {
                if (index == label.key.span) continue
                val changing = current.room != target.room
                val changes = label.key.changes + if (changing) 1 else 0
                if (changes > 2 || (!changing && target.start <= current.end)) continue
                val walking = data.travel[current.room][target.room]
                val first = max(target.start, label.key.arrival + 1 + walking)
                val last = min(min(target.end - 1, window.end - 1), current.end + budget - label.key.loss)
                if (first > last) continue
                val arrivals = sortedSetOf(first, last)
                arrivals += (current.end + walking).coerceIn(first, last)
                if (changes < 2) finals.forEach { final ->
                    val nextWalk = data.travel[target.room][final.room]
                    arrivals += (window.end - nextWalk - 2).coerceIn(first, last)
                    arrivals += (final.start - nextWalk - 1).coerceIn(first, last)
                    arrivals += (current.end + budget - label.key.loss -
                        max(nextWalk, final.start - target.end)).coerceIn(first, last)
                }
                for (arrival in arrivals) {
                    val departure = min(current.end, arrival - walking)
                    if (departure <= label.key.arrival) continue
                    val loss = label.key.loss + arrival - departure
                    if (loss > budget) continue
                    val favorites = label.favorites + if (data.favorite[current.room]) departure - label.key.arrival else 0
                    val buildings = label.buildings + if (changing && data.locations[current.room].building !=
                        data.locations[target.room].building) 1 else 0
                    val totalWalking = label.walking + walking
                    val prefix = PlanningPolicy.score(loss, 0, changes, buildings, totalWalking, label.approach, favorites)
                    val study = PlanningStep.Study(label.key.arrival, departure, data.rooms[current.room],
                        data.favorite[current.room])
                    val transfer = PlanningStep.Transfer(departure, arrival, data.rooms[current.room], data.rooms[target.room], walking)
                    enqueue(Label(Key(index, arrival, changes, loss), favorites, totalWalking, buildings,
                        label.approach, prefix, label.steps + study + transfer))
                }
            }
        }
        return completed
    }
}
