package com.example.youroom

import com.example.youroom.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import kotlin.random.Random

class ExpectedRoomPreferenceTest {
    private val day = LocalDate.of(2026,10,14)
    private val a = Classroom("01:2-201","2-201","2",60)
    private val b = Classroom("01:2-202","2-202","2",60)
    private val c = Classroom("01:2-203","2-203","2",60)
    private fun slot(number: Int,start: Int,end: Int,vararg rooms: Classroom) = LessonSlot(number,
        LocalTime.ofSecondOfDay(start.toLong()*60),LocalTime.ofSecondOfDay(end.toLong()*60),rooms.toList())
    private fun snapshot(vararg slots: LessonSlot) = ClassroomSnapshot("01",day,Instant.EPOCH,slots.toList())
    private fun plan(data: ClassroomSnapshot,goal: PlanningGoal,profiles: Map<String,RoomProfile> = emptyMap()) =
        StudyPlanner.plan(data,Campus.XITUCHENG,day,profiles,goal).options.first().plan
    private fun studies(room: Classroom,start: Int,end: Int) = PlanningStep.Study(start,end,room,false)
    private fun roomPlan(room: Classroom,minutes: Int=60,changes: Int=0,expected: Int=minutes) =
        StudyPlan(StudyWindow(480,480+minutes),listOf(studies(room,480,480+minutes)),minutes,0,
            changes,0,changes,0,0,0,changes*20,expectedMinutes=expected)

    @Test fun wholeDesiredRoomBeatsAFavoriteWithoutLosingTimeOrAddingAMove() {
        val data = snapshot(slot(1,480,600,a,b))
        val result = plan(data,PlanningGoal(480,600,false,expectedRooms=setOf(b.id)),
            mapOf(a.id to a.emptyProfile().copy(favorite=true)))
        assertEquals(b.id,(result.steps.single() as PlanningStep.Study).classroom.id)
        assertEquals(120,result.studyMinutes)
        assertEquals(0,result.changes)
        assertEquals(0,result.favoriteMinutes)
        assertEquals(120,result.expectedMinutes)
    }

    @Test fun aPartialDesiredRoomDoesNotCauseAnExtraMove() {
        val data = snapshot(slot(1,480,540,a),slot(2,540,600,a,b))
        val result = plan(data,PlanningGoal(480,600,false,expectedRooms=setOf(b.id)))
        assertEquals(a.id,(result.steps.single() as PlanningStep.Study).classroom.id)
        assertEquals(120,result.studyMinutes)
        assertEquals(0,result.changes)
        assertEquals(0,result.expectedMinutes)
    }

    @Test fun shorteningForADesiredRoomCannotReplaceAFullyUsablePlan() {
        val data = snapshot(slot(1,720,780,a),slot(2,780,890,a,b),slot(3,890,900,a))
        val result = plan(data,PlanningGoal(780,900,expectedRooms=setOf(b.id)))
        assertEquals(120,result.studyMinutes)
        assertEquals(a.id,(result.steps.single() as PlanningStep.Study).classroom.id)
    }

    @Test fun anEntireShortTargetIsAllowed() {
        val data = snapshot(slot(1,480,500,a,b))
        val result = plan(data,PlanningGoal(480,500,false,expectedRooms=setOf(b.id)))
        assertEquals(b.id,(result.steps.single() as PlanningStep.Study).classroom.id)
        assertEquals(20,result.expectedMinutes)
    }

    @Test fun thirtyMinuteNeighborBoundaryIsInclusiveOnBothSides() {
        fun credit(left: Int,right: Int): Int {
            val steps = listOf(studies(a,480,480+left),studies(b,481+left,541+left),studies(c,542+left,542+left+right))
            return ExpectedRoomPreference.minutes(steps,listOf(StudyWindow(480,542+left+right)),setOf(b.id))
        }
        assertEquals(0,credit(29,30))
        assertEquals(0,credit(30,29))
        assertEquals(60,credit(30,30))
    }

    @Test fun aUserBreakDoesNotCountAsAShortStudyNeighbor() {
        val windows = listOf(StudyWindow(480,500),StudyWindow(780,840))
        val steps = listOf(studies(a,480,500),PlanningStep.Break(500,780),studies(b,780,840))
        assertEquals(60,ExpectedRoomPreference.minutes(steps,windows,setOf(b.id)))
    }

    @Test fun incrementalCreditWaitsForTheFollowingNeighbor() {
        val progress = ExpectedRoomPreference.Progress().append(false,30,changingToNext=true)
            .append(true,60,changingToNext=true)
        assertEquals(0,progress.minutes)
        assertEquals(60,progress.tail.pending)
        assertEquals(0,progress.append(false,29,final=true).minutes)
        assertEquals(60,progress.append(false,30,final=true).minutes)
    }

    @Test fun aShortPrecedingNeighborPreventsCreditAndDoesNotLeakAcrossPeriods() {
        val bad = ExpectedRoomPreference.Progress().append(false,29,changingToNext=true).append(true,60,final=true)
        assertEquals(0,bad.minutes)
        assertEquals(60,bad.append(true,60,final=true).minutes)
    }

    @Test fun shortUnrelatedLegsAreNotIncorrectlyRejected() {
        val progress = ExpectedRoomPreference.Progress().append(false,10,changingToNext=true)
            .append(false,30,changingToNext=true).append(true,60,final=true)
        assertEquals(60,progress.minutes)
    }

    @Test fun preferenceChoiceNeverSacrificesCoverageOrAddsMovement() {
        val baseline = roomPlan(a,expected=0)
        assertEquals(baseline,ExpectedRoomPreference.choose(listOf(roomPlan(b,59),roomPlan(c,60,1)),baseline))
        assertEquals(b.id,(ExpectedRoomPreference.choose(listOf(roomPlan(b),roomPlan(c,59)),baseline)
            .steps.single() as PlanningStep.Study).classroom.id)
    }

    @Test fun tiedExpectedRoomsAreRandomizedButUntiedCostsRemainRespected() {
        val baseline=roomPlan(a,expected=0)
        val candidates=listOf(roomPlan(b),roomPlan(c))
        val selected=(0..30).map { seed -> (ExpectedRoomPreference.choose(candidates,baseline,Random(seed))
            .steps.single() as PlanningStep.Study).classroom.id }.toSet()
        assertEquals(setOf(b.id,c.id),selected)
        assertEquals(b.id,(ExpectedRoomPreference.choose(listOf(roomPlan(b),roomPlan(c).copy(freshnessPenalty=1)),baseline)
            .steps.single() as PlanningStep.Study).classroom.id)
    }

    @Test fun tiedDpPrefixesUseStablePerRequestRandomRanks() {
        val expected=booleanArrayOf(false,true,true,false)
        val winners=(0..30).map { seed ->
            val ranks=ExpectedRoomPreference.ranks(expected,Random(seed))
            assertEquals(0,ranks[0]); assertEquals(0,ranks[3])
            if (ranks[1] < ranks[2]) 1 else 2
        }.toSet()
        assertEquals(setOf(1,2),winners)
    }

    @Test fun excludedMissingAndOtherCampusSelectionsDoNotAffectTheResult() {
        val data=snapshot(slot(1,480,540,a,b))
        val result=plan(data,PlanningGoal(480,540,false,expectedRooms=setOf(b.id,"04:2-201","01:missing")),
            mapOf(b.id to b.emptyProfile().copy(excluded=true)))
        assertEquals(a.id,(result.steps.single() as PlanningStep.Study).classroom.id)
        assertEquals(0,result.expectedMinutes)
    }

    @Test fun multiplePeriodsKeepDesiredCreditSeparateFromPersonalBreaks() {
        val data=snapshot(slot(1,480,500,a,b),slot(2,500,780,a),slot(3,780,840,a,b))
        val goal=PlanningGoal(780,840,false,savedWindows=listOf(StudyWindow(480,500)),expectedRooms=setOf(b.id))
        val result=plan(data,goal,mapOf(a.id to a.emptyProfile().copy(favorite=true)))
        assertEquals(80,result.expectedMinutes)
        assertEquals(80,result.studyMinutes)
        assertEquals(0,result.changes)
        assertTrue(result.steps.filterIsInstance<PlanningStep.Study>().all { it.classroom.id == b.id })
    }

    @Test fun threePeriodDirectBoundWorksForALargeDesiredCatalog() {
        val rooms=(1..600).map { a.copy(id="01:2-${200+it}",name="2-${200+it}") }
        val data=snapshot(slot(1,480,1100,*rooms.toTypedArray()))
        val goal=PlanningGoal(960,1020,savedWindows=listOf(StudyWindow(480,540),StudyWindow(780,840)),
            expectedRooms=setOf(rooms.last().id))
        val result=plan(data,goal)
        assertEquals(180,result.expectedMinutes)
        assertEquals(rooms.last().id,result.steps.filterIsInstance<PlanningStep.Study>().first().classroom.id)
    }

    @Test fun expectedRoomCanReplaceAFavoriteOnAnAlreadyNecessaryMove() {
        val data=snapshot(slot(1,600,660,a,b),slot(2,660,720,c))
        val profiles=mapOf(a.id to a.emptyProfile().copy(favorite=true))
        val baseline=plan(data,PlanningGoal(600,720,false),profiles)
        val result=plan(data,PlanningGoal(600,720,false,expectedRooms=setOf(b.id)),profiles)
        assertEquals(b.id,result.steps.filterIsInstance<PlanningStep.Study>().first().classroom.id)
        assertEquals(baseline.studyMinutes,result.studyMinutes)
        assertEquals(baseline.changes,result.changes)
        assertTrue(result.expectedMinutes >= 30)
        assertEquals(ExpectedRoomPreference.minutes(result.steps,result.windows,setOf(b.id)),result.expectedMinutes)
    }

    @Test fun jointPlanningCanPreferDifferentExpectedRoomsWithoutAddingCrossPeriodMoves() {
        val data=snapshot(slot(1,600,660,a,b),slot(2,660,780,a),slot(3,780,840,c))
        val goal=PlanningGoal(780,840,false,savedWindows=listOf(StudyWindow(600,660)),expectedRooms=setOf(b.id,c.id))
        val profiles=mapOf(a.id to a.emptyProfile().copy(favorite=true))
        val result=plan(data,goal,profiles)
        assertEquals(120,result.studyMinutes)
        assertEquals(1,result.changes)
        assertEquals(120,result.expectedMinutes)
        assertEquals(listOf(b.id,c.id),result.steps.filterIsInstance<PlanningStep.Study>().map { it.classroom.id })
    }

    @Test fun aShortNeighborDoesNotEarnAPreferenceBonusInTheActualDp() {
        val data=snapshot(slot(1,600,620,a,b),slot(2,620,680,c))
        val goal=PlanningGoal(600,680,false,expectedRooms=setOf(c.id))
        val result=plan(data,goal)
        assertEquals(0,result.expectedMinutes)
        assertEquals(1,result.changes)
        assertTrue(result.steps.filterIsInstance<PlanningStep.Study>().first().end-600 < 30)
    }

    @Test fun anIntermediateRoomCanBeExactlyThirtyMinutesWhileMaximizingTheDesiredStay() {
        val d=a.copy(id="01:2-204",name="2-204")
        val data=snapshot(slot(1,600,630,a,b),slot(2,630,650,a,b,c),slot(3,650,680,c),slot(4,680,740,d))
        val result=plan(data,PlanningGoal(600,740,false,expectedRooms=setOf(b.id)),
            mapOf(a.id to a.emptyProfile().copy(favorite=true)))
        assertEquals(138,result.studyMinutes)
        assertEquals(2,result.changes)
        assertEquals(49,result.expectedMinutes)
        val studies=result.steps.filterIsInstance<PlanningStep.Study>()
        assertEquals(b.id,studies.first().classroom.id)
        assertEquals(30,studies[1].end-studies[1].start)
    }

    @Test fun aCheaperNonExpectedPrefixCannotPruneAnEligibleExpectedMultiPeriodRoute() {
        val d=a.copy(id="01:2-204",name="2-204")
        val e=a.copy(id="01:2-205",name="2-205")
        val data=snapshot(slot(1,600,630,a,b,e),slot(2,630,650,a,b,c,e),slot(3,650,680,c,e),
            slot(4,680,740,d),slot(5,740,900,e),slot(6,900,960,d))
        val goal=PlanningGoal(900,960,false,savedWindows=listOf(StudyWindow(600,740)),expectedRooms=setOf(b.id))
        val profiles=mapOf(a.id to a.emptyProfile().copy(favorite=true),d.id to d.emptyProfile().copy(favorite=true))
        val baseline=plan(data,goal.copy(expectedRooms=emptySet()),profiles)
        val result=plan(data,goal,profiles)
        assertEquals(2,baseline.changes)
        assertEquals(b.id,result.steps.filterIsInstance<PlanningStep.Study>().first().classroom.id)
        assertEquals(49,result.expectedMinutes)
        assertEquals(baseline.studyMinutes,result.studyMinutes)
        assertEquals(baseline.changes,result.changes)
    }
}
