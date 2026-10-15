package com.example.youroom

import com.example.youroom.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class MultiStudyPlannerTest {
    private val day = LocalDate.of(2026,10,14)
    private val a = Classroom("01:2-201","2-201","2",60)
    private val b = Classroom("01:2-202","2-202","2",60)
    private val c = Classroom("01:3-101","3-101","3",60)
    private fun slot(number: Int,start: Int,end: Int,vararg rooms: Classroom) = LessonSlot(number,
        LocalTime.ofSecondOfDay(start.toLong()*60),LocalTime.ofSecondOfDay(end.toLong()*60),rooms.toList())
    private fun data(vararg slots: LessonSlot) = ClassroomSnapshot("01",day,Instant.EPOCH,slots.toList())
    private fun plan(snapshot: ClassroomSnapshot,goal: PlanningGoal,profiles: Map<String,RoomProfile> = emptyMap()) =
        StudyPlanner.plan(snapshot,Campus.XITUCHENG,day,profiles,goal)
    private fun two(first: StudyWindow = StudyWindow(480,540),last: StudyWindow = StudyWindow(780,840),flexible: Boolean = false) =
        PlanningGoal(last.start,last.end,flexible,savedWindows=listOf(first))

    @Test fun userBreakIsSkippedEvenIfTheRoomIsOccupiedDuringIt() {
        val result = plan(data(slot(1,480,540,a),slot(2,540,780,b),slot(3,780,840,a)),two()).options.first().plan
        assertEquals(120,result.studyMinutes)
        assertEquals(0,result.lostMinutes)
        assertEquals(0,result.changes)
        assertEquals(listOf(StudyWindow(480,540),StudyWindow(780,840)),result.windows)
        assertEquals(PlanningStep.Break(540,780),result.steps.filterIsInstance<PlanningStep.Break>().single())
        assertEquals(120,result.steps.filterIsInstance<PlanningStep.Study>().sumOf { it.end-it.start })
    }
    @Test fun jointPlanningKeepsACommonRoomInsteadOfGreedilyPickingTheFreshestFirstRoom() {
        val snapshot = data(slot(1,540,600,b),slot(2,600,660,a,b),slot(3,660,780,b),slot(4,780,840,b))
        val result = plan(snapshot,two(StudyWindow(600,660))).options.first().plan
        assertEquals(0,result.changes)
        assertTrue(result.steps.filterIsInstance<PlanningStep.Study>().all { it.classroom.id == b.id })
    }
    @Test fun walkingFitsInAUserBreakWithoutReducingStudyMinutes() {
        val result = plan(data(slot(1,480,540,a),slot(2,540,780,c),slot(3,780,840,b)),two()).options.first().plan
        assertEquals(120,result.studyMinutes)
        assertEquals(0,result.lostMinutes)
        assertEquals(1,result.changes)
        val transfer = result.steps.filterIsInstance<PlanningStep.Transfer>().single()
        assertEquals(1,transfer.walkingMinutes)
        assertTrue(transfer.end <= 780)
        result.steps.zipWithNext().forEach { (first,second) -> assertEquals(first.end,second.start) }
    }
    @Test fun touchingPeriodsPayForWalkingThatSpillsIntoTheNextStudyPeriod() {
        val result = plan(data(slot(1,480,540,a),slot(2,540,600,b)),two(last=StudyWindow(540,600))).options.first().plan
        assertEquals(119,result.studyMinutes)
        assertEquals(1,result.lostMinutes)
        assertEquals(1,result.changes)
        assertEquals(541,result.steps.filterIsInstance<PlanningStep.Study>().last().start)
    }
    @Test fun sharedThirtyMinuteBudgetCanUseTwentyFiveMinutesInOnePeriod() {
        val snapshot = data(slot(1,600,635,a),slot(3,780,835,a))
        val result = plan(snapshot,two(StudyWindow(600,660),flexible=true)).options.first().plan
        assertEquals(30,result.lostMinutes)
        assertEquals(90,result.studyMinutes)
        assertEquals(0,result.changes)
        assertTrue(plan(snapshot,PlanningGoal(600,660)).options.isEmpty())
    }
    @Test fun twoFifteenMinuteTrimsFitButThreeDoNotGetNinetyMinutesOfIndependentBudgets() {
        val snapshot = data(slot(1,600,645,a),slot(3,780,825,b),slot(5,960,1005,c))
        val two = plan(snapshot,two(StudyWindow(600,660),flexible=true)).options.first().plan
        assertEquals(30,two.lostMinutes)
        assertEquals(90,two.studyMinutes)
        val three = PlanningGoal(960,1020,savedWindows=listOf(StudyWindow(600,660),StudyWindow(780,840)))
        assertTrue(plan(snapshot,three).options.isEmpty())
    }
    @Test fun finalWheelPeriodMustBeValidAndCannotOverlapASavedPeriod() {
        val snapshot = data(slot(1,480,900,a))
        assertEquals(PlanningProblem.INVALID_TIME,plan(snapshot,two().copy(end=780)).problem)
        assertEquals(PlanningProblem.OVERLAP,plan(snapshot,two().copy(start=520)).problem)
    }
    @Test fun freshnessPrefersTheLaterContinuousFreeStartButNeverOverridesFavorites() {
        val snapshot = data(slot(1,720,780,a),slot(2,780,840,a,b))
        val goal = PlanningGoal(780,840,false)
        val fresh = plan(snapshot,goal).options.first().plan
        assertEquals(b.id,(fresh.steps.single() as PlanningStep.Study).classroom.id)
        assertEquals(0,fresh.freshnessPenalty)
        val favored = plan(snapshot,goal,mapOf(a.id to a.emptyProfile().copy(favorite=true))).options.first().plan
        assertEquals(a.id,(favored.steps.single() as PlanningStep.Study).classroom.id)
        assertEquals(60,favored.favoriteMinutes)
    }
    @Test fun freshnessNeverSacrificesCoverageOrAddsAnExtraMove() {
        val snapshot = data(slot(1,720,780,a),slot(2,780,810,a,b),slot(3,810,840,a))
        val result = plan(snapshot,PlanningGoal(780,840,false)).options.first().plan
        assertEquals(a.id,(result.steps.single() as PlanningStep.Study).classroom.id)
        assertEquals(60,result.studyMinutes)
        assertEquals(0,result.changes)
    }
    @Test fun freshnessIsCountedAtEachPeriodStartRatherThanJustTheFirst() {
        val snapshot = data(slot(1,540,600,a),slot(3,600,660,a,b),slot(5,700,780,b),slot(7,780,840,a,b))
        val single = plan(snapshot,PlanningGoal(600,660,false)).options.first().plan
        assertEquals(b.id,(single.steps.single() as PlanningStep.Study).classroom.id)
        val result = plan(snapshot,two(StudyWindow(600,660))).options.first().plan
        assertEquals(60,result.freshnessPenalty)
        assertTrue(result.steps.filterIsInstance<PlanningStep.Study>().all { it.classroom.id == a.id })
    }
    @Test fun exclusionsAndCampusDateChecksAlsoApplyToAllPeriods() {
        val snapshot = data(slot(1,480,840,a,b))
        val result = plan(snapshot,two(),mapOf(a.id to a.emptyProfile().copy(excluded=true))).options.first().plan
        assertTrue(result.steps.filterIsInstance<PlanningStep.Study>().all { it.classroom.id == b.id })
        assertTrue(StudyPlanner.plan(snapshot,Campus.SHAHE,day,emptyMap(),two()).options.isEmpty())
        assertTrue(StudyPlanner.plan(snapshot,Campus.XITUCHENG,day.plusDays(1),emptyMap(),two()).options.isEmpty())
        assertTrue(StudyPlanner.plan(snapshot,Campus.XITUCHENG,day,emptyMap(),two(),notBeforeMinute=500).options.isEmpty())
    }
    @Test fun largeThreePeriodAlwaysFreeCatalogUsesTheProvenDirectBound() {
        val rooms = (1..600).map { a.copy(id="01:2-${200+it}",name="2-${200+it}") }
        val snapshot = data(slot(1,480,1100,*rooms.toTypedArray()))
        val goal = PlanningGoal(960,1020,savedWindows=listOf(StudyWindow(480,540),StudyWindow(780,840)))
        val start = System.nanoTime()
        val result = plan(snapshot,goal).options.first().plan
        assertEquals(180,result.studyMinutes)
        assertEquals(0,result.changes)
        println("HOST ONLY: 600 rooms / three periods = ${(System.nanoTime()-start)/1_000_000} ms")
    }

    @Test fun adjustedRecommendationDoesNotDiscardTheOriginalMultiPeriodAlternative() {
        val snapshot = data(slot(1,600,615,a),slot(2,615,690,a,b),slot(3,690,705,b),
            slot(4,705,780,c),slot(5,780,840,a,b))
        val result = plan(snapshot,two(StudyWindow(600,690),flexible=true),mapOf(b.id to b.emptyProfile().copy(favorite=true)))
        assertTrue(result.options.any { it.label == "原时段方案" })
        assertTrue(result.options.first().plan.favoriteMinutes > 100)
        assertTrue(result.options.all { it.plan.lostMinutes <= 30 && it.plan.windows.zipWithNext().all { (x,y) -> x.end <= y.start } })
    }

    @Test fun fixedMultiPeriodDpMatchesAnIndependentMinuteEnumeratedReference() {
        val third = a.copy(id="01:2-203",name="2-203")
        val rooms = listOf(a,b,third)
        for (gap in listOf(0,3)) for (mask in 1..4095 step 97) {
            val windows = listOf(StudyWindow(480,490),StudyWindow(490+gap,500+gap))
            val snapshot = data(*(0..3).map { band ->
                val start = 480+band*5+if (band>=2) gap else 0
                slot(band+1,start,start+5,*rooms.filterIndexed { index,_ -> mask and (1 shl (band*3+index)) != 0 }.toTypedArray())
            }.toTypedArray())
            val goal = two(windows[0],windows[1])
            val profiles = mapOf(b.id to b.emptyProfile().copy(favorite=true))
            val actual = plan(snapshot,goal,profiles).options.firstOrNull()?.plan?.score
            val known = snapshot.classrooms.map { it.id }.toSet()
            fun available(room: Int,time: Int) = rooms[room].id in known && snapshot.slots.any {
                time in (it.start.hour*60+it.start.minute) until (it.end.hour*60+it.end.minute) &&
                    it.classrooms.any { candidate -> candidate.id == rooms[room].id }
            }
            val memo = mutableMapOf<List<Int>,Int?>()
            fun search(phase: Int,time: Int,room: Int,changes: Int,loss: Int): Int? {
                if (loss > 30 || changes > 2) return null
                if (time == windows[phase].end) {
                    if (phase == 1) return 0
                    var best: Int? = null
                    for (next in rooms.indices) {
                        val walk = if (next == room) 0 else 1
                        for (arrival in maxOf(windows[1].start,time+walk) until windows[1].end) {
                            val lost = arrival-windows[1].start
                            val result = search(1,arrival,next,0,loss+lost)?.plus(lost*3+if (next==room) 0 else 22)
                            if (result != null && (best == null || result < best)) best=result
                        }
                    }
                    return best
                }
                val key=listOf(phase,time,room,changes,loss)
                if (memo.containsKey(key)) return memo[key]
                if (!available(room,time)) return null.also { memo[key]=it }
                val reward=if (room==1) 1 else 0
                var best=search(phase,time+1,room,changes,loss)?.minus(reward)
                for (next in rooms.indices) {
                    val walk=if (next==room) 0 else 1
                    for (arrival in (time+1+walk) until windows[phase].end) {
                        if (arrival == time+1) continue
                        val lost=arrival-time-1
                        val result=search(phase,arrival,next,changes+if (next==room) 0 else 1,loss+lost)
                            ?.plus(lost*3+(if (next==room) 0 else 22)-reward)
                        if (result != null && (best == null || result < best)) best=result
                    }
                }
                memo[key]=best
                return best
            }
            val expected=rooms.indices.mapNotNull { search(0,480,it,0,0) }.minOrNull()
            assertEquals("mask=$mask / gap=$gap",expected,actual)
        }
    }
    @Test fun anUnavailableFavoriteDoesNotExpandALargeOtherwisePerfectCatalog() {
        val rooms=(1..600).map { a.copy(id="01:2-${200+it}",name="2-${200+it}") }
        val goal=PlanningGoal(960,1020,savedWindows=listOf(StudyWindow(480,540),StudyWindow(780,840)))
        val result=plan(data(slot(1,480,1100,*rooms.toTypedArray()),slot(2,1300,1400,c)),goal,
            mapOf(c.id to c.emptyProfile().copy(favorite=true))).options.first().plan
        assertEquals(180,result.studyMinutes)
        assertEquals(0,result.favoriteMinutes)
        assertEquals(0,result.changes)
    }
}
