package com.example.youroom.domain

import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Stage DP: each stage keeps endpoint/time/budget alternatives for the following period.
 * User gaps are not study or loss; walking that spills into a study period is loss.
 * No Cartesian product of complete period plans, and no independent greedy room choices.
 */
internal object MultiStudyPlanner {
    private data class Route(val room: Int, val end: Int, val loss: Int, val deviation: Int,
        val changes: Int, val buildings: Int, val walking: Int, val approach: Int, val favorites: Int,
        val freshness: Int, val original: Boolean, val windows: List<StudyWindow>, val steps: List<PlanningStep>,
        val preference: ExpectedRoomPreference.Progress = ExpectedRoomPreference.Progress(), val preferenceTie: Int = 0) {
        val score get() = PlanningPolicy.score(loss, deviation, changes, buildings, walking, approach, favorites)
    }
    private data class EndKey(val room: Int, val end: Int, val loss: Int, val original: Boolean, val expectedUsed: Boolean)
    private data class Key(val span: Int, val arrival: Int, val localChanges: Int, val loss: Int, val original: Boolean,
        val preferenceTail: ExpectedRoomPreference.Tail = ExpectedRoomPreference.Tail(), val expectedUsed: Boolean = false)
    private data class Label(val key: Key, val route: Route)
    private val balanced = compareBy<Route> { it.score }.thenBy { it.changes }.thenBy { it.buildings }
        .thenBy { it.loss }.thenBy { it.deviation }.thenByDescending { it.favorites }
        .thenBy { it.walking + it.approach }.thenBy { it.freshness }
    private val quiet = compareBy<Route> { it.changes }.thenBy { it.buildings }
        .thenBy { it.walking + it.approach }.thenBy { it.loss }.thenBy { it.deviation }.then(balanced)
    private val expectedOrder = compareBy<Route> { it.changes }.thenBy { it.buildings }
        .thenBy { it.walking+it.approach }.thenByDescending { it.preference.minutes }.then(balanced).thenBy { it.preferenceTie }

    fun plan(data: StudyPlanner.Prepared, slots: List<LessonSlot>, campus: Campus, goal: PlanningGoal,
        notBefore: Int?, check: () -> Unit, baseline: PlanningResult? = null): PlanningResult {
        if (data.rooms.isEmpty()) return PlanningResult(message = "当前数据中没有可规划的教室。")
        val targets = goal.windows
        val preferenceSeed = Random.nextLong()
        val totalDuration = targets.sumOf { it.duration }
        val favoriteTime = BooleanArray(1440)
        data.spans.filter { data.favorite[it.room] }.forEach { span ->
            for (minute in span.start.coerceAtLeast(0) until span.end.coerceAtMost(1440)) favoriteTime[minute]=true
        }
        val favoritePrefix = IntArray(1441)
        for (minute in favoriteTime.indices) favoritePrefix[minute+1]=favoritePrefix[minute]+if (favoriteTime[minute]) 1 else 0
        val favoriteBounds = targets.map { target ->
            val shift=if (goal.flexible) PlanningPolicy.shiftLimit else 0
            min(target.duration,favoritePrefix[min(1440,target.end+shift)]-favoritePrefix[max(0,target.start-shift)])
        }
        fun toPlan(route: Route) = StudyPlan(StudyWindow(route.windows.first().start, route.windows.last().end),
            route.steps, totalDuration - route.loss, route.favorites, route.changes, route.buildings,
            route.walking, route.approach, route.loss, route.deviation, route.score, route.windows, route.freshness,
            route.preference.minutes)
        val expectedIds = data.rooms.indices.filter { data.expected[it] }.map { data.rooms[it].id }.toSet()

        // An original, zero-loss, zero-movement, zero-approach route attaining the absolute
        // favorite upper bound is optimal. Avoid expanding identical always-free room graphs.
        val direct = data.rooms.indices.mapNotNull { room ->
            check()
            val spans = targets.map { target -> data.spans.firstOrNull {
                it.room == room && it.start <= target.start && it.end >= target.end
            } ?: return@mapNotNull null }
            val approach = CampusTopology.approachMinutes(campus, goal.origin, data.locations[room])
            if (notBefore != null && targets.first().start < notBefore + approach) return@mapNotNull null
            val steps = buildList<PlanningStep> { targets.forEachIndexed { index, window ->
                if (index > 0 && targets[index-1].end < window.start)
                    add(PlanningStep.Break(targets[index-1].end, window.start))
                add(PlanningStep.Study(window.start, window.end, data.rooms[room], data.favorite[room]))
            } }
            Route(room, targets.last().end, 0, 0, 0, 0, 0, approach,
                if (data.favorite[room]) totalDuration else 0,
                targets.indices.sumOf { targets[it].start - spans[it].start }, true, targets, steps,
                ExpectedRoomPreference.Progress(ExpectedRoomPreference.minutes(steps,targets,expectedIds)))
        }
        val perfectScore = -favoriteBounds.sum()
        direct.filter { it.score == perfectScore }.map(::toPlan).minWithOrNull(StudyPlanner.order)?.let {
            if (!data.hasExpected || direct.any { route -> route.preference.minutes == totalDuration })
                return baseline?.let { base -> ExpectedRoomPreference.options(base,direct.map(::toPlan),preferenceSeed) }
                    ?: PlanningResult(options = listOf(PlanningOption("推荐方案",
                        ExpectedRoomPreference.choose(direct.map(::toPlan),it,Random(preferenceSeed)))))
        }

        var frontier = emptyList<Route>()
        val directUpper = direct.minWithOrNull(balanced)
        val quietUpper = direct.minWithOrNull(quiet)
        targets.forEachIndexed { phase, target ->
            check()
            val scoreEnds = mutableMapOf<EndKey, Route>()
            val quietEnds = mutableMapOf<EndKey, Route>()
            val expectedEnds = mutableMapOf<EndKey, Route>()
            fun finish(route: Route) {
                val key = EndKey(route.room, route.end, route.loss, route.original, route.preference.minutes > 0)
                if (scoreEnds[key]?.let { balanced.compare(route,it) < 0 } != false) scoreEnds[key] = route
                if (quietEnds[key]?.let { quiet.compare(route,it) < 0 } != false) quietEnds[key] = route
                if (data.hasExpected && expectedEnds[key]?.let { expectedOrder.compare(route,it) < 0 } != false) expectedEnds[key] = route
            }
            val localGoal = PlanningGoal(target.start, target.end, goal.flexible, if (phase == 0) goal.origin else null)
            for (window in StudyPlanner.candidates(localGoal, slots, PlanningPolicy.multiLossLimit)) {
                check()
                if (phase == 0 && notBefore != null && window.start < notBefore) continue
                solveStage(data, campus, localGoal, window, phase, frontier, notBefore, check, ::finish,
                    directUpper,quietUpper,favoritePrefix,favoriteBounds.drop(phase+1).sum())
            }
            frontier = (scoreEnds.values + quietEnds.values + expectedEnds.values).distinct()
            if (frontier.isEmpty()) return PlanningResult(message = "难以满足要求，可缩短时段")
        }
        val plans = frontier.map(::toPlan)
        if (baseline != null) return ExpectedRoomPreference.options(baseline,plans,preferenceSeed)
        val options = listOfNotNull(
            plans.minWithOrNull(StudyPlanner.order)?.let { PlanningOption("推荐方案", ExpectedRoomPreference.choose(plans,it,Random(preferenceSeed))) },
            plans.filter { it.windows == targets }.minWithOrNull(StudyPlanner.order)?.let {
                PlanningOption("原时段方案", ExpectedRoomPreference.choose(plans.filter { candidate -> candidate.windows == targets },it,Random(preferenceSeed))) },
            plans.minWithOrNull(StudyPlanner.quietOrder)?.let { PlanningOption("少搬动方案", ExpectedRoomPreference.choose(plans,it,Random(preferenceSeed))) })
            .distinctBy { it.plan.signature }
        return PlanningResult(options)
    }

    private fun solveStage(data: StudyPlanner.Prepared, campus: Campus, goal: PlanningGoal, window: StudyWindow,
        phase: Int, previous: List<Route>, notBefore: Int?, check: () -> Unit, finish: (Route) -> Unit,
        directUpper: Route?, quietUpper: Route?, favoritePrefix: IntArray, futureFavorite: Int) {
        val trim = goal.end - goal.start - window.duration
        val deviation = abs(window.start-goal.start) + abs(window.end-goal.end)
        val spans = data.spans.filter { it.start < window.end && it.end > window.start }
        val finals = spans.filter { it.end >= window.end }
        val best = mutableMapOf<Key, Label>()
        val bestQuiet = mutableMapOf<Key, Label>()
        val bestExpected = mutableMapOf<Key, Label>()
        val queue = PriorityQueue<Label>(compareBy<Label> { it.key.arrival }.thenBy { it.route.score })
        fun enqueue(label: Label) {
            if (label.route.loss > PlanningPolicy.multiLossLimit) return
            if (data.maximumChanges != null && label.route.changes > data.maximumChanges) return
            if (data.maximumLoss != null && label.route.loss > data.maximumLoss) return
            // Safe lower bound: remaining favorite time can only reduce score by this much;
            // movement/loss/deviation never decrease. Preserve both recommendation orders.
            if (!data.hasExpected && directUpper != null && quietUpper != null) {
                val remaining = favoritePrefix[window.end]-favoritePrefix[label.key.arrival]+futureFavorite
                val quietFloor = compareValuesBy(label.route,quietUpper,{it.changes},{it.buildings},
                    {it.walking+it.approach},{it.loss},{it.deviation})
                if (label.route.score-remaining > directUpper.score && quietFloor > 0) return
            }
            val wins = best[label.key]?.let { balanced.compare(label.route,it.route) < 0 } != false
            val winsQuiet = bestQuiet[label.key]?.let { quiet.compare(label.route,it.route) < 0 } != false
            val winsExpected = data.hasExpected && bestExpected[label.key]?.let { expectedOrder.compare(label.route,it.route) < 0 } != false
            if (wins) best[label.key] = label
            if (winsQuiet) bestQuiet[label.key] = label
            if (winsExpected) bestExpected[label.key] = label
            if (wins || winsQuiet || winsExpected) queue.add(label)
        }
        var seeds = 0
        spans.forEachIndexed { index, span ->
            if (phase == 0) {
                if (span.start <= window.start) {
                    val approach = CampusTopology.approachMinutes(campus, goal.origin, data.locations[span.room])
                    if (notBefore == null || window.start >= notBefore + approach) {
                        val route = Route(span.room, window.start, trim, deviation, 0, 0, 0, approach, 0,
                            window.start-span.start, window == StudyWindow(goal.start,goal.end), emptyList(), emptyList())
                        enqueue(Label(Key(index,window.start,0,route.loss,route.original),route))
                    }
                }
            } else for (before in previous) {
                if (seeds++ % 128 == 0) check()
                if (before.end > window.start) continue
                if (data.maximumChanges != null && before.changes+(if (before.room != span.room) 1 else 0) > data.maximumChanges) continue
                val walking = data.travel[before.room][span.room]
                val arrival = max(max(window.start,span.start),before.end+walking)
                if (arrival >= min(window.end,span.end)) continue
                val loss = before.loss + trim + arrival-window.start
                if (loss > PlanningPolicy.multiLossLimit) continue
                val changing = before.room != span.room
                val gap = buildList<PlanningStep> {
                    if (changing) add(PlanningStep.Transfer(before.end,before.end+walking,
                        data.rooms[before.room],data.rooms[span.room],walking))
                    if (arrival > before.end+walking) add(PlanningStep.Break(before.end+walking,arrival))
                }
                val route = before.copy(room = span.room, end = arrival, loss = loss,
                    deviation = before.deviation+deviation, changes = before.changes+if (changing) 1 else 0,
                    buildings = before.buildings+if (changing && data.locations[before.room].building !=
                        data.locations[span.room].building) 1 else 0,
                    walking = before.walking+walking, freshness = before.freshness+arrival-span.start,
                    original = before.original && window == StudyWindow(goal.start,goal.end), steps = before.steps+gap)
                enqueue(Label(Key(index,arrival,0,loss,route.original,route.preference.tail,route.preference.minutes > 0),route))
            }
        }
        var iterations = 0
        while (queue.isNotEmpty()) {
            if (iterations++ % 64 == 0) check()
            val label = queue.remove()
            if (best[label.key] !== label && bestQuiet[label.key] !== label && bestExpected[label.key] !== label) continue
            val current = spans[label.key.span]
            val route = label.route
            if (current.end >= window.end) {
                val favorites = route.favorites + if (data.favorite[current.room]) window.end-label.key.arrival else 0
                finish(route.copy(end = window.end, favorites = favorites, windows = route.windows+window,
                    steps = route.steps+PlanningStep.Study(label.key.arrival,window.end,data.rooms[current.room],data.favorite[current.room]),
                    preference = route.preference.append(data.expected[current.room],window.end-label.key.arrival,
                        entireShortWindow = label.key.arrival == window.start, final = true),
                    preferenceTie = route.preferenceTie+data.expectedRanks[current.room]))
            }
            for ((index,target) in spans.withIndex()) {
                if (index == label.key.span) continue
                val changing = current.room != target.room
                val changes = label.key.localChanges+if (changing) 1 else 0
                if (changes > PlanningPolicy.changeLimit ||
                    (data.maximumChanges != null && route.changes+(if (changing) 1 else 0) > data.maximumChanges) ||
                    (!changing && target.start <= current.end)) continue
                val walking = data.travel[current.room][target.room]
                val budget = min(PlanningPolicy.multiLossLimit,data.maximumLoss ?: PlanningPolicy.multiLossLimit)-route.loss
                val first = max(target.start,label.key.arrival+1+walking)
                val last = min(min(target.end-1,window.end-1),current.end+budget)
                if (first > last) continue
                val arrivals = sortedSetOf(first,last,(current.end+walking).coerceIn(first,last))
                if (data.hasExpected) {
                    arrivals += (label.key.arrival+PlanningPolicy.expectedStayMinimum+walking).coerceIn(first,last)
                    arrivals += (window.end-PlanningPolicy.expectedNeighborMinimum).coerceIn(first,last)
                    arrivals += (target.end-PlanningPolicy.expectedNeighborMinimum).coerceIn(first,last)
                }
                if (changes < PlanningPolicy.changeLimit) finals.forEach { final ->
                    val nextWalk = data.travel[target.room][final.room]
                    arrivals += (window.end-nextWalk-2).coerceIn(first,last)
                    arrivals += (final.start-nextWalk-1).coerceIn(first,last)
                    if (data.hasExpected) {
                        arrivals += (final.start-nextWalk-PlanningPolicy.expectedStayMinimum).coerceIn(first,last)
                        arrivals += (window.end-nextWalk-PlanningPolicy.expectedNeighborMinimum*2).coerceIn(first,last)
                        arrivals += (window.end-nextWalk-PlanningPolicy.expectedNeighborMinimum-1).coerceIn(first,last)
                    }
                    arrivals += (current.end+budget-max(nextWalk,final.start-target.end)).coerceIn(first,last)
                }
                for (arrival in arrivals) {
                    val departure = min(current.end,arrival-walking)
                    if (departure <= label.key.arrival) continue
                    val loss = route.loss+arrival-departure
                    if (loss > PlanningPolicy.multiLossLimit) continue
                    val next = route.copy(room = target.room,end = arrival,loss = loss,
                        favorites = route.favorites+if (data.favorite[current.room]) departure-label.key.arrival else 0,
                        changes = route.changes+if (changing) 1 else 0,
                        buildings = route.buildings+if (changing && data.locations[current.room].building !=
                            data.locations[target.room].building) 1 else 0,
                        walking = route.walking+walking,
                        steps = route.steps+PlanningStep.Study(label.key.arrival,departure,data.rooms[current.room],data.favorite[current.room])+
                            PlanningStep.Transfer(departure,arrival,data.rooms[current.room],data.rooms[target.room],walking),
                        preference = if (data.hasExpected) route.preference.append(data.expected[current.room],
                            departure-label.key.arrival, changingToNext = changing) else route.preference,
                        preferenceTie = route.preferenceTie+data.expectedRanks[current.room])
                    enqueue(Label(Key(index,arrival,changes,loss,route.original,next.preference.tail,next.preference.minutes > 0),next))
                }
            }
        }
    }
}
