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
    private data class EndKey(val room: Int, val end: Int, val loss: Int, val original: Boolean, val expectedUsed: Boolean,
        val totalChanges: Int = 0)
    private data class Key(val span: Int, val arrival: Int, val localChanges: Int, val loss: Int, val original: Boolean,
        val preferenceTail: ExpectedRoomPreference.Tail = ExpectedRoomPreference.Tail(), val expectedUsed: Boolean = false,
        val totalChanges: Int = 0)
    private data class Label(val key: Key, val route: Route)
    private val balanced = compareBy<Route> { it.score }.thenBy { it.changes }.thenBy { it.buildings }
        .thenBy { it.loss }.thenBy { it.deviation }.thenByDescending { it.favorites }
        .thenBy { it.walking + it.approach }.thenBy { it.freshness }
    private val quiet = compareBy<Route> { it.changes }.thenBy { it.buildings }
        .thenBy { it.walking + it.approach }.thenBy { it.loss }.thenBy { it.deviation }.then(balanced)
    private val expectedOrder = compareBy<Route> { it.changes }.thenBy { it.buildings }
        .thenBy { it.walking+it.approach }.thenByDescending { it.preference.minutes }.then(balanced).thenBy { it.preferenceTie }
    private val buildings = compareBy<Route> { it.buildings }.then(quiet)
    private val favorites = compareByDescending<Route> { it.favorites }.then(balanced)
    private val expectedCoverage = compareByDescending<Route> { it.preference.minutes }.then(balanced)
    private fun extraOrders(portfolio: Boolean, hasExpected: Boolean): List<Comparator<Route>> =
        if (!portfolio) emptyList() else listOf(buildings,favorites)+if (hasExpected) listOf(expectedCoverage) else emptyList()

    fun plan(data: StudyPlanner.Prepared, slots: List<LessonSlot>, campus: Campus, goal: PlanningGoal,
        notBefore: Int?, check: () -> Unit, baseline: PlanningResult? = null, seed: Boolean = true,
        portfolio: Boolean = false, expectedOnly: Boolean = false, portfolioBaseline: StudyPlan? = null,
        compactRest: Boolean = true): PlanningResult {
        if (data.rooms.isEmpty()) return PlanningResult(message = "当前数据中没有可规划的教室。")
        val targets = goal.windows
        val requestedExpected=goal.expectedRooms.any { it.startsWith("${campus.dataKey}:") }
        val preferenceSeed = Random.nextLong()
        val totalDuration = targets.sumOf { it.duration }
        val lossLimit = if (portfolio) goal.lossLimit else PlanningPolicy.multiLossLimit
        val orders = if (expectedOnly) listOf(expectedCoverage) else extraOrders(portfolio,data.hasExpected)
        val collected = if (portfolio) PlanningPortfolio.Collector(data.hasExpected) else null
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
                return if (portfolio) PlanningPortfolio.result(direct.map(::toPlan),requestedExpected,portfolioBaseline)
                else baseline?.let { base -> ExpectedRoomPreference.options(base,direct.map(::toPlan),preferenceSeed) }
                    ?: PlanningResult(options = listOf(PlanningOption("推荐方案",
                        ExpectedRoomPreference.choose(direct.map(::toPlan),it,Random(preferenceSeed)))))
        }

        // A small original-window search supplies legal upper bounds only. All rooms and
        // all flexible windows are still searched below; a failed seed never means no solution.
        val seedPlans = if (seed && goal.flexible && !data.hasExpected) {
            val reduced = seedData(data,targets,direct.map { it.room }.toSet(),check)
            plan(reduced,slots,campus,goal.copy(flexible = false),notBefore,check,seed = false,portfolio = portfolio).options.map { it.plan }
        } else emptyList()
        val seedRoutes = seedPlans.map { plan ->
            val last = plan.steps.filterIsInstance<PlanningStep.Study>().last().classroom
            Route(data.rooms.indexOfFirst { it.id == last.id },plan.windows.last().end,plan.lostMinutes,
                plan.deviation,plan.changes,plan.buildingChanges,plan.walkingMinutes,plan.approachMinutes,
                plan.favoriteMinutes,plan.freshnessPenalty,plan.windows == targets,plan.windows,plan.steps)
        }
        var frontier = emptyList<Route>()
        val directUpper = (direct+seedRoutes).minWithOrNull(balanced)
        val quietUpper = (direct+seedRoutes).minWithOrNull(quiet)
        val extraUppers = orders.map { (direct+seedRoutes).minWithOrNull(it) }
        (direct+seedRoutes).forEach { collected?.add(toPlan(it)) }
        val maximumWalk = data.travel.maxOfOrNull { row -> row.maxOrNull() ?: 0 } ?: 0
        targets.forEachIndexed { phase, target ->
            check()
            val scoreEnds = mutableMapOf<EndKey, Route>()
            val quietEnds = mutableMapOf<EndKey, Route>()
            val expectedEnds = mutableMapOf<EndKey, Route>()
            val extraEnds = orders.map { mutableMapOf<EndKey,Route>() }
            fun finish(route: Route) {
                if (phase == targets.lastIndex) collected?.add(toPlan(route))
                val key = EndKey(route.room, route.end, route.loss, route.original, route.preference.minutes > 0,
                    if (portfolio && data.hasExpected) route.changes else 0)
                if (!expectedOnly && scoreEnds[key]?.let { balanced.compare(route,it) < 0 } != false) scoreEnds[key] = route
                if (!expectedOnly && quietEnds[key]?.let { quiet.compare(route,it) < 0 } != false) quietEnds[key] = route
                if (data.hasExpected && expectedEnds[key]?.let { expectedOrder.compare(route,it) < 0 } != false) expectedEnds[key] = route
                orders.forEachIndexed { index,order ->
                    if (extraEnds[index][key]?.let { order.compare(route,it) < 0 } != false) extraEnds[index][key] = route
                }
            }
            val localGoal = PlanningGoal(target.start, target.end, goal.flexible, if (phase == 0) goal.origin else null)
            for (window in StudyPlanner.candidates(localGoal, slots, lossLimit)) {
                check()
                if (phase == 0 && notBefore != null && window.start < notBefore) continue
                solveStage(data, campus, localGoal, window, phase, phase == targets.lastIndex, frontier, notBefore, check, ::finish,
                    directUpper,quietUpper,favoritePrefix,favoriteBounds.drop(phase+1).sum(),portfolio,lossLimit,extraUppers,expectedOnly)
            }
            frontier = (scoreEnds.values + quietEnds.values + expectedEnds.values + extraEnds.flatMap { it.values }).distinct()
            if (frontier.isEmpty()) return PlanningResult(message = "难以满足要求，可缩短时段")
            // With enough rest, every retained endpoint can reach every next start before
            // study resumes. Exact previous end times then have no future feasibility effect.
            val nextStart = targets.getOrNull(phase+1)?.start?.minus(if (goal.flexible) PlanningPolicy.shiftLimit else 0)
            if (compactRest && (portfolio || (!data.hasExpected && data.maximumChanges == null)) && nextStart != null &&
                frontier.maxOf { it.end }+maximumWalk <= nextStart)
                frontier = compactRestEndpoints(frontier,check,portfolio,data.hasExpected,
                    data.maximumChanges!=null || (portfolio && data.hasExpected))
        }
        if (portfolio) return PlanningPortfolio.result(collected!!.plans(),requestedExpected,portfolioBaseline)
        val plans = (frontier+seedRoutes+direct).map(::toPlan)
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
        phase: Int, terminal: Boolean, previous: List<Route>, notBefore: Int?, check: () -> Unit, finish: (Route) -> Unit,
        directUpper: Route?, quietUpper: Route?, favoritePrefix: IntArray, futureFavorite: Int,
        portfolio: Boolean, lossLimit: Int, extraUppers: List<Route?>, expectedOnly: Boolean) {
        val trim = goal.end - goal.start - window.duration
        val deviation = abs(window.start-goal.start) + abs(window.end-goal.end)
        val spans = data.spans.filter { it.start < window.end && it.end > window.start }
        val finals = spans.filter { it.end >= window.end }
        val arrivals = PlanningMinutes()
        val finalCuts = PlanningArrivalCuts(data,spans,finals,window.end)
        val best = mutableMapOf<Key, Label>()
        val bestQuiet = mutableMapOf<Key, Label>()
        val bestExpected = mutableMapOf<Key, Label>()
        val orders = if (expectedOnly) listOf(expectedCoverage) else extraOrders(portfolio,data.hasExpected)
        val extraBest = orders.map { mutableMapOf<Key,Label>() }
        val queue = PriorityQueue<Label>(compareBy<Label> { it.key.arrival }.thenBy { it.route.score })
        fun enqueue(label: Label) {
            if (label.route.loss > lossLimit) return
            if (data.maximumChanges != null && label.route.changes > data.maximumChanges) return
            if (data.maximumLoss != null && label.route.loss > data.maximumLoss) return
            // Safe lower bound: remaining favorite time can only reduce score by this much;
            // movement/loss/deviation never decrease. Preserve both recommendation orders.
            if (!data.hasExpected && directUpper != null && quietUpper != null) {
                val remaining = favoritePrefix[window.end]-favoritePrefix[label.key.arrival]+futureFavorite
                val quietFloor = compareValuesBy(label.route,quietUpper,{it.changes},{it.buildings},
                    {it.walking+it.approach},{it.loss},{it.deviation})
                val extrasLose = !portfolio || (extraUppers[0]?.let { upper ->
                    compareValuesBy(label.route,upper,{it.buildings},{it.changes},{it.walking+it.approach},
                        {it.loss},{it.deviation}) > 0 } == true && extraUppers[1]?.let { upper ->
                    val ceiling = label.route.favorites+remaining
                    ceiling < upper.favorites || (ceiling == upper.favorites && label.route.score-remaining > upper.score)
                } == true)
                if (label.route.score-remaining > directUpper.score && quietFloor > 0 && extrasLose) return
            }
            val wins = !expectedOnly && best[label.key]?.let { balanced.compare(label.route,it.route) < 0 } != false
            val winsQuiet = !expectedOnly && bestQuiet[label.key]?.let { quiet.compare(label.route,it.route) < 0 } != false
            val winsExpected = data.hasExpected && bestExpected[label.key]?.let { expectedOrder.compare(label.route,it.route) < 0 } != false
            if (wins) best[label.key] = label
            if (winsQuiet) bestQuiet[label.key] = label
            if (winsExpected) bestExpected[label.key] = label
            var winsExtra = false
            orders.forEachIndexed { index,order ->
                if (extraBest[index][label.key]?.let { order.compare(label.route,it.route) < 0 } != false) {
                    extraBest[index][label.key] = label; winsExtra = true
                }
            }
            if (wins || winsQuiet || winsExpected || winsExtra) queue.add(label)
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
                if (loss > lossLimit) continue
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
                enqueue(Label(Key(index,arrival,0,loss,route.original,route.preference.tail,route.preference.minutes > 0,
                    if (portfolio && data.hasExpected) route.changes else 0),route))
            }
        }
        var iterations = 0
        while (queue.isNotEmpty()) {
            if (iterations++ % 64 == 0) check()
            val label = queue.remove()
            if (best[label.key] !== label && bestQuiet[label.key] !== label && bestExpected[label.key] !== label &&
                extraBest.none { it[label.key] === label }) continue
            val current = spans[label.key.span]
            val route = label.route
            if (current.end >= window.end) {
                val favorites = route.favorites + if (data.favorite[current.room]) window.end-label.key.arrival else 0
                finish(route.copy(end = window.end, favorites = favorites, windows = route.windows+window,
                    steps = route.steps+PlanningStep.Study(label.key.arrival,window.end,data.rooms[current.room],data.favorite[current.room]),
                    preference = route.preference.append(data.expected[current.room],window.end-label.key.arrival,
                        entireShortWindow = label.key.arrival == window.start, final = true),
                    preferenceTie = route.preferenceTie+data.expectedRanks[current.room]))
                // No future endpoint or desired-room reward remains. Leaving an already
                // available favorite cannot improve coverage, rewards or either route order.
                if (terminal && !data.hasExpected && data.favorite[current.room]) continue
            }
            for ((index,target) in spans.withIndex()) {
                if (index == label.key.span) continue
                val changing = current.room != target.room
                val changes = label.key.localChanges+if (changing) 1 else 0
                if (changes > PlanningPolicy.changeLimit ||
                    (data.maximumChanges != null && route.changes+(if (changing) 1 else 0) > data.maximumChanges) ||
                    (!changing && target.start <= current.end)) continue
                val walking = data.travel[current.room][target.room]
                val budget = min(lossLimit,data.maximumLoss ?: lossLimit)-route.loss
                val first = max(target.start,label.key.arrival+1+walking)
                val last = min(min(target.end-1,window.end-1),current.end+budget)
                if (first > last) continue
                arrivals.clear()
                arrivals.add(first); arrivals.add(last)
                arrivals.addClamped(current.end+walking,first,last)
                if (data.hasExpected) {
                    arrivals.addClamped(label.key.arrival+PlanningPolicy.expectedStayMinimum+walking,first,last)
                    arrivals.addClamped(window.end-PlanningPolicy.expectedNeighborMinimum,first,last)
                    arrivals.addClamped(target.end-PlanningPolicy.expectedNeighborMinimum,first,last)
                }
                if (changes < PlanningPolicy.changeLimit) finalCuts.addTo(arrivals,index,current.end+budget,first,last)
                arrivals.sort()
                for (candidate in 0 until arrivals.size) {
                    val arrival = arrivals[candidate]
                    val departure = min(current.end,arrival-walking)
                    if (departure <= label.key.arrival) continue
                    val loss = route.loss+arrival-departure
                    if (loss > lossLimit) continue
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
                    enqueue(Label(Key(index,arrival,changes,loss,route.original,next.preference.tail,next.preference.minutes > 0,
                        if (portfolio && data.hasExpected) next.changes else 0),next))
                }
            }
        }
    }

    private fun compactRestEndpoints(routes: List<Route>, check: () -> Unit, portfolio: Boolean,
        hasExpected: Boolean, preserveChanges: Boolean): List<Route> {
        val scores = mutableMapOf<EndKey,Route>()
        val quiets = mutableMapOf<EndKey,Route>()
        val orders = extraOrders(portfolio,hasExpected)+if (hasExpected) listOf(expectedOrder) else emptyList()
        val extras = orders.map { mutableMapOf<EndKey,Route>() }
        routes.forEachIndexed { index,route ->
            if (index % 128 == 0) check()
            // Completed user periods have no pending 30-minute neighbor credit. With
            // sufficient rest, only room/loss/global move budget affect future feasibility;
            // keep expected objectives and move counts, not every equivalent end minute.
            val key = EndKey(route.room,0,route.loss,route.original,route.preference.minutes>0,
                if (preserveChanges) route.changes else 0)
            if (scores[key]?.let { balanced.compare(route,it) < 0 } != false) scores[key] = route
            if (quiets[key]?.let { quiet.compare(route,it) < 0 } != false) quiets[key] = route
            orders.forEachIndexed { i,order -> if (extras[i][key]?.let { order.compare(route,it) < 0 } != false) extras[i][key] = route }
        }
        return (scores.values+quiets.values+extras.flatMap { it.values }).distinct()
    }

    private fun seedData(data: StudyPlanner.Prepared, targets: List<StudyWindow>, direct: Set<Int>,
        check: () -> Unit): StudyPlanner.Prepared {
        val coverage = IntArray(data.rooms.size)
        for (span in data.spans) for (target in targets)
            coverage[span.room] += (min(span.end,target.end)-max(span.start,target.start)).coerceAtLeast(0)
        val order = data.rooms.indices.sortedWith(compareByDescending<Int> { coverage[it] }.thenBy { it })
        val selected = (order.filter { data.favorite[it] }.take(8) + order.filter { it in direct }.take(2) +
            order.filterNot { data.favorite[it] }.take(4)).distinct().take(12)
        val remap = selected.withIndex().associate { it.value to it.index }
        return StudyPlanner.Prepared(selected.map { data.rooms[it] },BooleanArray(selected.size) { data.favorite[selected[it]] },
            selected.map { data.locations[it] },Array(selected.size) { first ->
                check(); IntArray(selected.size) { second -> data.travel[selected[first]][selected[second]] }
            },data.spans.mapNotNull { span -> remap[span.room]?.let { span.copy(room = it) } },
            maximumChanges = data.maximumChanges,maximumLoss = data.maximumLoss)
    }
}
