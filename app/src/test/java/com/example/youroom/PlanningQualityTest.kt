package com.example.youroom

import com.example.youroom.domain.*
import org.junit.Assert.*
import org.junit.Test

class PlanningQualityTest {
    private val ordinary = Classroom("01:3-201","3-201","3",60)
    private val favorite = Classroom("01:3-305","3-305","3",60)

    private fun route(vararg visits: Pair<Classroom,Int>, score: Int? = null): StudyPlan {
        var now=600
        val steps=mutableListOf<PlanningStep>()
        visits.forEachIndexed { index,(room,minutes) ->
            if(index>0 && room!=visits[index-1].first) {
                steps+=PlanningStep.Transfer(now,now+2,visits[index-1].first,room,2)
                now+=2
            }
            steps+=PlanningStep.Study(now,now+minutes,room,room==favorite)
            now+=minutes
        }
        val study=visits.sumOf { it.second }
        val favored=visits.filter { it.first==favorite }.sumOf { it.second }
        val moves=steps.filterIsInstance<PlanningStep.Transfer>().size
        return StudyPlan(StudyWindow(600,now),steps,study,favored,moves,0,moves*2,0,
            moves*2,0,score ?: PlanningPolicy.score(moves*2,0,moves,0,moves*2,0,favored))
    }

    @Test fun tenMinuteFavoriteVisitIsRejectedEvenWhenItsScoreWouldPass() {
        val base=route(ordinary to 120)
        val detour=route(favorite to 10,ordinary to 108)
        assertTrue(detour.score<=base.score+PlanningQuality.maximumScoreRegret)
        assertFalse(PlanningQuality.usefulVisits(detour))
        val result=PlanningPortfolio.result(listOf(base,detour),false)
        assertEquals(listOf(base),result.options.map { it.plan })
        assertTrue(result.groups.all { it.direction==PlanningDirection.EXPECTED || it.optionIndices.size==1 })
    }

    @Test fun everyVisitOnEitherSideOfAMoveMustReachThirtyMinutes() {
        assertFalse(PlanningQuality.usefulVisits(route(ordinary to 29,favorite to 60)))
        assertFalse(PlanningQuality.usefulVisits(route(ordinary to 60,favorite to 29)))
        assertTrue(PlanningQuality.usefulVisits(route(ordinary to 30,favorite to 30)))
        assertFalse(PlanningQuality.usefulVisits(route(ordinary to 40,favorite to 10,ordinary to 40)))
    }

    @Test fun consecutivePiecesOfOneVisitAccumulateButWaitingDoesNotCount() {
        val split=route(ordinary to 15,ordinary to 15,favorite to 30)
        assertTrue(PlanningQuality.usefulVisits(split))
        val short=route(ordinary to 15,ordinary to 14,favorite to 30)
        assertFalse(PlanningQuality.usefulVisits(short))
        val shortWithWait=short.copy(steps=short.steps.map {
            if(it is PlanningStep.Transfer) it.copy(end=it.end+20) else it
        })
        assertFalse(PlanningQuality.usefulVisits(shortWithWait))
    }

    @Test fun separateUserPeriodsAreCheckedIndependentlyRatherThanAcrossTheirRest() {
        val first=PlanningStep.Study(600,625,ordinary,false)
        val last=PlanningStep.Study(900,925,favorite,true)
        val separated=route(ordinary to 50).copy(window=StudyWindow(600,925),
            windows=listOf(StudyWindow(600,625),StudyWindow(900,925)),
            steps=listOf(first,PlanningStep.Break(625,900),last),favoriteMinutes=25)
        assertTrue(PlanningQuality.usefulVisits(separated))
        assertTrue(PlanningQuality.usefulVisits(route(ordinary to 25)))
    }

    @Test fun emptyAndZeroLengthVisitsDoNotBecomeDirectionChampions() {
        val base=route(ordinary to 60)
        assertFalse(PlanningQuality.usefulVisits(base.copy(steps=emptyList())))
        assertFalse(PlanningQuality.usefulVisits(base.copy(windows=emptyList())))
        assertFalse(PlanningQuality.usefulVisits(route(ordinary to 0)))
        assertTrue(PlanningPortfolio.result(listOf(route(favorite to 10,ordinary to 108)),false).options.isEmpty())
    }

    @Test fun scoreRegretUsesAnAdditiveBandEvenWhenScoresAreNegative() {
        val base=route(favorite to 120,score=-120)
        assertTrue(PlanningQuality.admits(base.copy(score=-90),base))
        assertFalse(PlanningQuality.admits(base.copy(score=-89),base))
        assertTrue(PlanningQuality.admits(base.copy(score=Int.MAX_VALUE),base.copy(score=Int.MAX_VALUE)))
        assertFalse(PlanningQuality.admits(base.copy(score=Int.MAX_VALUE),base.copy(score=Int.MIN_VALUE)))
    }

    @Test fun allDirectionsHaveTheSameUsableCoverageFloor() {
        val base=route(ordinary to 120)
        assertTrue(PlanningQuality.admits(base.copy(studyMinutes=105),base))
        assertFalse(PlanningQuality.admits(base.copy(studyMinutes=104),base))
    }

    @Test fun aLowQualityNoMoveChampionHasNoExemption() {
        val good=route(favorite to 118)
        val poor=route(ordinary to 120)
        assertTrue(PlanningQuality.usefulVisits(poor))
        val result=PlanningPortfolio.result(listOf(poor,good),false)
        assertEquals(listOf(good),result.options.map { it.plan })
        assertEquals(1,result.groups.single { it.direction==PlanningDirection.CHANGES }.optionIndices.size)
        assertEquals(1,result.groups.single { it.direction==PlanningDirection.BUILDINGS }.optionIndices.size)
    }

    @Test fun collectorRejectsFragmentedCandidatesBeforeTheyCanFillItsBoundedBuckets() {
        val collector=PlanningPortfolio.Collector(false)
        repeat(8) { index ->
            collector.add(route(favorite.copy(id="01:favorite-$index",name="3-$index") to 10,ordinary to 108)
                .copy(favoriteMinutes=10,score=-100))
        }
        val good=route(ordinary to 120)
        collector.add(good)
        assertEquals(listOf(good),collector.plans())
        assertEquals(1,PlanningPortfolio.result(collector.plans(),false).options.size)
    }

    @Test fun usefulAlternativesRemainDistinctBoundedAndIndependentlyRanked() {
        val plans=(0..7).map { index ->
            route(ordinary.copy(id="01:3-$index",name="3-$index") to 60)
        }+route(favorite to 30,ordinary to 30)
        val result=PlanningPortfolio.result(plans,false)
        val base=result.options.map { it.plan }.minWith(StudyPlanner.order)
        assertTrue(result.options.all { PlanningQuality.admits(it.plan,base) })
        result.groups.filter { it.direction!=PlanningDirection.EXPECTED }.forEach { group ->
            val options=group.optionIndices.map { result.options[it].plan }
            assertTrue(options.size in 1..5)
            assertEquals(options.distinctBy(PlanningPortfolio::routeKey),options)
            assertEquals(options.sortedWith(PlanningPortfolio.order(group.direction)),options)
        }
    }

    @Test fun aRejectedBaselineCannotResurrectAFragmentedOption() {
        val bad=route(favorite to 10,ordinary to 108)
        val good=route(ordinary to 120)
        val result=PlanningPortfolio.result(listOf(good),true,bad)
        assertEquals(listOf(good),result.options.map { it.plan })
        assertTrue(result.groups.last().optionIndices.isEmpty())
    }

    @Test fun searchCanAccumulateOneRoomVisitAcrossAWaitWithoutCountingTheWait() {
        val rooms=listOf(ordinary,favorite)
        val locations=rooms.map { CampusTopology.location(it,Campus.XITUCHENG) }
        val prepared=StudyPlanner.Prepared(rooms,booleanArrayOf(false,true),locations,
            arrayOf(intArrayOf(0,3),intArrayOf(3,0)),listOf(
                StudyPlanner.Span(0,600,620),StudyPlanner.Span(0,630,650),StudyPlanner.Span(1,650,700)))
        val result=MultiStudyPlanner.plan(prepared,emptyList(),Campus.XITUCHENG,PlanningGoal(600,700,false),null,{},portfolio=true)
        assertTrue(result.options.isNotEmpty())
        assertTrue(result.options.all { PlanningQuality.usefulVisits(it.plan) })
        val studies=result.options.first().plan.steps.filterIsInstance<PlanningStep.Study>()
        assertEquals(2,studies.count { it.classroom==ordinary })
        assertTrue(studies.filter { it.classroom==ordinary }.sumOf { it.end-it.start }>=30)
    }

    @Test fun shortNecessaryWaitsCannotMakeATwentyMinuteRoomVisitLookUseful() {
        val rooms=listOf(ordinary,favorite)
        val prepared=StudyPlanner.Prepared(rooms,booleanArrayOf(false,true),
            rooms.map { CampusTopology.location(it,Campus.XITUCHENG) },
            arrayOf(intArrayOf(0,3),intArrayOf(3,0)),listOf(
                StudyPlanner.Span(0,600,610),StudyPlanner.Span(0,620,630),StudyPlanner.Span(1,630,700)))
        val result=MultiStudyPlanner.plan(prepared,emptyList(),Campus.XITUCHENG,PlanningGoal(600,700,false),null,{},portfolio=true)
        assertTrue(result.options.isEmpty())
    }

    @Test fun aSingleRoomAfterAUserRestMayHaveLessThanThirtyMinutesLeftAfterWalking() {
        val rooms=listOf(ordinary,favorite)
        val prepared=StudyPlanner.Prepared(rooms,booleanArrayOf(false,true),
            rooms.map { CampusTopology.location(it,Campus.XITUCHENG) },
            arrayOf(intArrayOf(0,3),intArrayOf(3,0)),listOf(
                StudyPlanner.Span(0,600,630),StudyPlanner.Span(1,630,660)))
        val result=MultiStudyPlanner.plan(prepared,emptyList(),Campus.XITUCHENG,
            PlanningGoal(630,660,false,savedWindows=listOf(StudyWindow(600,630))),null,{},portfolio=true)
        assertTrue(result.options.isNotEmpty())
        val plan=result.options.first().plan
        assertEquals(57,plan.studyMinutes)
        assertEquals(2,plan.windows.size)
        assertTrue(PlanningQuality.usefulVisits(plan))
    }
}
