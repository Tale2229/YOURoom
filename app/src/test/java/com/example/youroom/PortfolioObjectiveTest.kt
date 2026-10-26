package com.example.youroom

import com.example.youroom.domain.*
import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

/** Small exhaustive minute-by-minute oracle: no objective-based prefix pruning. */
class PortfolioObjectiveTest {
    private val campus=Campus.XITUCHENG
    private val window=StudyWindow(600,690)
    private val rooms=listOf("2-201","2-202","2-203","4-201").map {
        Classroom("01:$it",it,it.substringBefore('-'),60)
    }
    private val locations=rooms.map { CampusTopology.location(it,campus) }
    private val travel=Array(rooms.size) { a -> IntArray(rooms.size) { b ->
        if (a==b) 0 else CampusTopology.transferMinutes(campus,locations[a],locations[b])
    } }
    private val spans=listOf(StudyPlanner.Span(0,600,640),StudyPlanner.Span(1,620,660),
        StudyPlanner.Span(2,665,690),StudyPlanner.Span(3,630,690))

    private fun exhaustive(favorites: BooleanArray,spans: List<StudyPlanner.Span> = this.spans,
        window: StudyWindow = this.window): List<StudyPlan> {
        val plans=mutableListOf<StudyPlan>()
        fun visit(index: Int,arrival: Int,loss: Int,changes: Int,walk: Int,buildings: Int,favored: Int,steps: List<PlanningStep>) {
            val span=spans[index]
            if (span.end>=window.end) {
                val favorite=favored+if (favorites[span.room]) window.end-arrival else 0
                plans+=StudyPlan(window,steps+PlanningStep.Study(arrival,window.end,rooms[span.room],favorites[span.room]),
                    window.duration-loss,favorite,changes,buildings,walk,0,loss,0,
                    PlanningPolicy.score(loss,0,changes,buildings,walk,0,favorite))
            }
            if (changes==2) return
            spans.forEachIndexed { nextIndex,next ->
                if (next.room==span.room) return@forEachIndexed
                val walking=travel[span.room][next.room]
                for (time in max(next.start,arrival+walking+1)..min(next.end-1,window.end-1)) {
                    val departure=min(span.end,time-walking)
                    val nextLoss=loss+time-departure
                    if (departure<=arrival || nextLoss>20) continue
                    visit(nextIndex,time,nextLoss,changes+1,walk+walking,
                        buildings+if (locations[span.room].building!=locations[next.room].building) 1 else 0,
                        favored+if (favorites[span.room]) departure-arrival else 0,
                        steps+PlanningStep.Study(arrival,departure,rooms[span.room],favorites[span.room])+
                            PlanningStep.Transfer(departure,time,rooms[span.room],rooms[next.room],walking))
                }
            }
        }
        spans.forEachIndexed { i,span -> if (span.start<=window.start) visit(i,window.start,0,0,0,0,0,emptyList()) }
        return plans
    }

    // Independent quality oracle for this single-window fixture. These visits all
    // change rooms, so no consecutive same-room pieces need to be accumulated.
    private fun eligible(plans: List<StudyPlan>): List<StudyPlan> {
        val useful=plans.filter { plan ->
            val studies=plan.steps.filterIsInstance<PlanningStep.Study>()
            studies.size==1 || studies.all { it.end-it.start>=30 }
        }
        val base=useful.minWithOrNull(StudyPlanner.order) ?: return emptyList()
        return useful.filter { it.score<=base.score+30 && it.studyMinutes>=base.studyMinutes-15 }
    }

    @Test fun eachChampionMatchesExhaustiveSearchWithinTheCommonQualityPool() {
        for (mask in 0..15) {
            val favorites=BooleanArray(4) { mask and (1 shl it)!=0 }
            val all=eligible(exhaustive(favorites))
            val actual=MultiStudyPlanner.plan(StudyPlanner.Prepared(rooms,favorites,locations,travel,spans),emptyList(),campus,
                PlanningGoal(600,690,false),null,{},portfolio=true)
            for (direction in PlanningDirection.entries.filter { it!=PlanningDirection.EXPECTED }) {
                val best=actual.options[actual.groups.single { it.direction==direction }.optionIndices.first()].plan
                val expected=all.minWith(PlanningPortfolio.order(direction))
                // Compare the full ordered metric vector (freshness/IDs are not in this independent oracle).
                fun metrics(plan: StudyPlan)=listOf(plan.score,plan.changes,plan.buildingChanges,plan.lostMinutes,
                    plan.deviation,plan.favoriteMinutes,plan.walkingMinutes)
                assertEquals("mask=$mask direction=$direction",metrics(expected),metrics(best))
            }
        }
    }

    @Test fun crossBuildingAndFewestChangesCanHaveDifferentChampions() {
        // Give each of the three same-building visits at least thirty minutes;
        // the old 90-minute fixture could only achieve this direction with fragments.
        val usefulSpans=listOf(StudyPlanner.Span(0,600,645),StudyPlanner.Span(1,640,685),
            StudyPlanner.Span(2,680,720),StudyPlanner.Span(3,630,720))
        val result=MultiStudyPlanner.plan(StudyPlanner.Prepared(rooms,BooleanArray(4),locations,travel,usefulSpans),emptyList(),campus,
            PlanningGoal(600,720,false),null,{},portfolio=true)
        fun first(direction: PlanningDirection)=result.options[result.groups.single { it.direction==direction }.optionIndices.first()].plan
        assertEquals(1,first(PlanningDirection.CHANGES).changes)
        assertEquals(1,first(PlanningDirection.CHANGES).buildingChanges)
        assertEquals(2,first(PlanningDirection.BUILDINGS).changes)
        assertEquals(0,first(PlanningDirection.BUILDINGS).buildingChanges)
    }

    @Test fun twoMoveThirtyMinuteBoundariesAlsoMatchTheMinuteByMinuteQualityOracle() {
        val wide=StudyWindow(600,720)
        val usefulSpans=listOf(StudyPlanner.Span(0,600,645),StudyPlanner.Span(1,640,685),
            StudyPlanner.Span(2,680,720),StudyPlanner.Span(3,630,720))
        for(mask in 0..15) {
            val favorites=BooleanArray(4) { mask and (1 shl it)!=0 }
            val expected=eligible(exhaustive(favorites,usefulSpans,wide))
            val result=MultiStudyPlanner.plan(StudyPlanner.Prepared(rooms,favorites,locations,travel,usefulSpans),emptyList(),campus,
                PlanningGoal(wide.start,wide.end,false),null,{},portfolio=true)
            for(direction in PlanningDirection.entries.filter { it!=PlanningDirection.EXPECTED }) {
                val best=result.options[result.groups.single { it.direction==direction }.optionIndices.first()].plan
                val oracle=expected.minWith(PlanningPortfolio.order(direction))
                fun metrics(p: StudyPlan)=listOf(p.score,p.studyMinutes,p.favoriteMinutes,p.changes,p.buildingChanges,p.walkingMinutes)
                assertEquals("two moves mask=$mask direction=$direction",metrics(oracle),metrics(best))
            }
        }
    }

    @Test fun expectedCoverageChampionMatchesExhaustiveSearchWithBaselineAndFragmentGuards() {
        for (mask in listOf(0,4,8,10)) {
            val favorites=BooleanArray(4) { mask and (1 shl it)!=0 }
            val all=eligible(exhaustive(favorites))
            val baseline=all.minWith(StudyPlanner.order)
            for (desired in rooms.indices) {
                val ids=setOf(rooms[desired].id)
                val eligible=all.map { it.copy(expectedMinutes=ExpectedRoomPreference.minutes(it.steps,it.windows,ids)) }
                    .filter { it.changes<=baseline.changes && it.studyMinutes>=baseline.studyMinutes && it.expectedMinutes>0 }
                val result=MultiStudyPlanner.plan(StudyPlanner.Prepared(rooms,favorites,locations,travel,spans,
                    expected=BooleanArray(4) { it==desired },maximumChanges=baseline.changes,maximumLoss=baseline.lostMinutes),
                    emptyList(),campus,PlanningGoal(600,690,false,expectedRooms=ids),null,{},
                    portfolio=true,expectedOnly=true,portfolioBaseline=baseline)
                val actual=result.groups.firstOrNull { it.direction==PlanningDirection.EXPECTED }?.optionIndices?.firstOrNull()
                    ?.let { result.options[it].plan }
                assertEquals("favorites=$mask desired=$desired",eligible.maxOfOrNull { it.expectedMinutes },actual?.expectedMinutes)
            }
        }
    }

    @Test fun collectorDeduplicatesMinuteTweaksButKeepsDifferentRoomRoutesAndSharedIds() {
        val all=exhaustive(booleanArrayOf(false,false,false,true))
        val collector=PlanningPortfolio.Collector(false)
        all.forEach(collector::add)
        val result=PlanningPortfolio.result(collector.plans(),false)
        result.groups.forEach { group ->
            val keys=group.optionIndices.map { PlanningPortfolio.routeKey(result.options[it].plan) }
            assertEquals(keys.size,keys.toSet().size)
        }
        assertTrue(result.groups.flatMap { it.optionIndices }.size>result.options.size)
    }

    @Test fun longRestCompactionRetainsExpectedCoverageAndTheGlobalMoveBudget() {
        val shifted=spans+spans.map { it.copy(start=it.start+240,end=it.end+240) }
        val favorites=booleanArrayOf(false,false,false,true)
        val prepared=StudyPlanner.Prepared(rooms,favorites,locations,travel,shifted)
        val goal=PlanningGoal(840,930,true,savedWindows=listOf(window),expectedRooms=setOf(rooms.first().id))
        val baseline=MultiStudyPlanner.plan(prepared,emptyList(),campus,goal,null,{},portfolio=true).options.first().plan
        val desired=prepared.copy(expected=booleanArrayOf(true,false,false,false),maximumChanges=baseline.changes,
            maximumLoss=baseline.lostMinutes)
        fun calculate(compact: Boolean)=MultiStudyPlanner.plan(desired,emptyList(),campus,goal,null,{},portfolio=true,
            expectedOnly=true,portfolioBaseline=baseline,compactRest=compact)
        val uncompressed=calculate(false)
        val compressed=calculate(true)
        for (direction in PlanningDirection.entries) {
            fun champion(result: PlanningResult)=result.groups.firstOrNull { it.direction==direction }?.optionIndices?.firstOrNull()
                ?.let { result.options[it].plan }?.let { listOf(it.score,it.studyMinutes,it.changes,it.buildingChanges,
                    it.favoriteMinutes,it.expectedMinutes,it.walkingMinutes,it.lostMinutes,it.deviation) }
            assertEquals("direction=$direction",champion(uncompressed),champion(compressed))
        }
    }
}
