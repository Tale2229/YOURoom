package com.example.youroom.domain

/** Final presentation selection, not the search's dominance rule. Champions are never
 * removed for diversity; the remaining options are useful alternatives, not exact k-best. */
internal object PlanningPortfolio {
    const val limit = 5
    val buildingOrder = compareBy<StudyPlan> { it.buildingChanges }.then(StudyPlanner.quietOrder)
    val favoriteOrder = compareByDescending<StudyPlan> { it.favoriteMinutes }.then(StudyPlanner.order)
    val expectedOrder = compareByDescending<StudyPlan> { it.expectedMinutes }.then(StudyPlanner.order)
    private val expectedRecommendationOrder = compareBy<StudyPlan> { it.changes }.thenBy { it.buildingChanges }
        .thenBy { it.walkingMinutes+it.approachMinutes }.thenByDescending { it.expectedMinutes }.then(StudyPlanner.order)

    fun order(direction: PlanningDirection): Comparator<StudyPlan> = when (direction) {
        PlanningDirection.BALANCED -> StudyPlanner.order
        PlanningDirection.CHANGES -> StudyPlanner.quietOrder
        PlanningDirection.BUILDINGS -> buildingOrder
        PlanningDirection.FAVORITES -> favoriteOrder
        PlanningDirection.EXPECTED -> expectedOrder
    }

    // Moving a transfer by a minute is not a new route. Include period boundaries so
    // choosing different rooms in separate user periods is still a meaningful alternative.
    fun routeKey(plan: StudyPlan): String = plan.windows.joinToString("|") { window ->
        plan.steps.filterIsInstance<PlanningStep.Study>()
            .filter { it.start >= window.start && it.end <= window.end }
            .joinToString(">") { it.classroom.id }
    }

    /** Bounded result storage per loss/change bucket. These resources determine whether
     * an expected-room option can satisfy the eventual baseline's coverage/move guards. */
    class Collector(private val hasExpected: Boolean) {
        private val buckets = mutableMapOf<Pair<Int,Int>,MutableMap<PlanningDirection,MutableMap<String,StudyPlan>>>()
        private val recommendations = mutableMapOf<Pair<Int,Int>,MutableMap<String,StudyPlan>>()
        fun add(plan: StudyPlan) {
            val bucket = buckets.getOrPut(plan.lostMinutes to plan.changes) { mutableMapOf() }
            val key = routeKey(plan)
            for (direction in PlanningDirection.entries) {
                if (direction == PlanningDirection.EXPECTED && (!hasExpected || plan.expectedMinutes == 0)) continue
                val choices = bucket.getOrPut(direction) { mutableMapOf() }
                val order = order(direction)
                val old = choices[key]
                if (old != null && order.compare(plan,old) >= 0) continue
                choices[key] = plan
                if (choices.size > limit) choices.remove(choices.maxWith { a,b -> order.compare(a.value,b.value) }.key)
            }
            if (hasExpected && plan.expectedMinutes>0) {
                val choices=recommendations.getOrPut(plan.lostMinutes to plan.changes) { mutableMapOf() }
                val old=choices[key]
                if (old==null || expectedRecommendationOrder.compare(plan,old)<0) choices[key]=plan
                if (choices.size>limit) choices.remove(choices.maxWith { a,b -> expectedRecommendationOrder.compare(a.value,b.value) }.key)
            }
        }
        fun plans(): List<StudyPlan> = (buckets.values.flatMap { it.values }.flatMap { it.values }+
            recommendations.values.flatMap { it.values }).distinctBy { it.signature }
    }

    fun result(plans: List<StudyPlan>, requestedExpected: Boolean, baselinePlan: StudyPlan? = null): PlanningResult {
        if (plans.isEmpty()) return PlanningResult(message = "难以满足要求，可缩短时段")
        val baseline = baselinePlan ?: plans.minWith(StudyPlanner.order)
        // Expected rooms remain a soft preference: never lose usable time or add a move
        // relative to the unmodified comprehensive champion. Fragment guards are scored in DP.
        val expected = plans.filter { it.expectedMinutes > 0 && it.studyMinutes >= baseline.studyMinutes && it.changes <= baseline.changes }
        val recommended = expected.minWithOrNull(compareByDescending<StudyPlan> { it.studyMinutes }
            .thenBy { it.changes }.thenBy { it.buildingChanges }.thenBy { it.walkingMinutes+it.approachMinutes }
            .thenByDescending { it.expectedMinutes }.then(StudyPlanner.order)) ?: baseline
        val pool = mutableListOf<PlanningOption>()
        val indices = mutableMapOf<String,Int>()
        val groups = PlanningDirection.entries.map { direction ->
            val source = if (direction == PlanningDirection.EXPECTED) expected else plans
            val ranked = source.sortedWith(order(direction))
            val first = if (direction == PlanningDirection.BALANCED) recommended else ranked.firstOrNull()
            val candidates = if (first == null) emptyList() else (listOf(first)+ranked)
                .distinctBy(::routeKey).filter { it === first || quality(it,first,direction) }.take(limit)
            PlanningGroup(direction,candidates.map { plan -> indices.getOrPut(plan.signature) {
                pool.add(PlanningOption("${direction.title} ${candidates.indexOf(plan)+1}",plan)); pool.lastIndex
            } }, if (candidates.isNotEmpty()) null else if (!requestedExpected) "尚未选择期望教室" else "没有满足条件的期望教室方案")
        }
        return PlanningResult(options = pool.toList(), groups = groups)
    }

    private fun quality(plan: StudyPlan, champion: StudyPlan, direction: PlanningDirection): Boolean {
        if (plan.studyMinutes < champion.studyMinutes-15) return false
        return when (direction) {
            PlanningDirection.BALANCED -> plan.score <= champion.score+30 && plan.changes <= champion.changes+1
            PlanningDirection.CHANGES -> plan.changes <= champion.changes+1
            PlanningDirection.BUILDINGS -> plan.buildingChanges <= champion.buildingChanges+1
            PlanningDirection.FAVORITES -> plan.favoriteMinutes >= champion.favoriteMinutes-30
            PlanningDirection.EXPECTED -> plan.expectedMinutes >= champion.expectedMinutes-30
        }
    }
}
