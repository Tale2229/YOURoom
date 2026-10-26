package com.example.youroom

import com.example.youroom.data.SchoolJson
import com.example.youroom.domain.*
import java.time.*
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class PlannerOptimizationTest {
    private val day = LocalDate.of(2026,10,15)
    private val favorites = setOf("3-305","3-308","3-405","3-408")
    private val three = PlanningGoal(1200,1320,true,savedWindows=listOf(StudyWindow(660,720),StudyWindow(840,1020)))
    private fun real() = SchoolJson.snapshot(javaClass.getResource("/planner/west-2026-10-15.json")!!.readText(),"01",day,Instant.EPOCH)
    private fun profiles(data: ClassroomSnapshot) = data.classrooms.filter { it.name in favorites }
        .associate { it.id to it.emptyProfile().copy(favorite = true) }
    private fun metrics(plan: StudyPlan) = listOf(plan.score,plan.studyMinutes,plan.favoriteMinutes,plan.changes,
        plan.buildingChanges,plan.walkingMinutes,plan.approachMinutes,plan.lostMinutes,plan.deviation,
        plan.freshnessPenalty,plan.expectedMinutes)

    @Test fun theReportedRealThreePeriodCaseRetainsBothGoldenRoutes() {
        val data = real()
        val started = System.nanoTime()
        val result = StudyPlanner.planConfirmed(data,Campus.XITUCHENG,day,profiles(data),three)
        val recommended = result.options.first().plan
        assertEquals(listOf(-284,354,354,2,0,6,0,6,0),metrics(recommended).take(9))
        assertEquals(three.windows,recommended.windows)
        assertEquals(listOf("3-308","3-308","3-408","3-305","3-305"),
            recommended.steps.filterIsInstance<PlanningStep.Study>().map { it.classroom.name })
        val quiet = result.options.single { it.label == "少搬动方案" }.plan
        assertEquals(360,quiet.studyMinutes); assertEquals(0,quiet.changes)
        assertEquals(setOf("2-240"),quiet.steps.filterIsInstance<PlanningStep.Study>().map { it.classroom.name }.toSet())
        println("HOST ONLY optimized real three-period case: ${(System.nanoTime()-started)/1e9} s")
        validate(data,result,profiles(data),three)
    }

    @Test fun realDataWithDesiredRoomsStillKeepsCoverageMoveLimitsAndThirtyMinuteNeighbors() {
        val data = real(); val profiles = profiles(data)
        val base = StudyPlanner.planConfirmed(data,Campus.XITUCHENG,day,profiles,three)
        val started = System.nanoTime()
        val desired = three.copy(expectedRooms = setOf("01:3-305","01:3-405"))
        val result = StudyPlanner.planConfirmed(data,Campus.XITUCHENG,day,profiles,desired)
        assertTrue(result.options.isNotEmpty())
        assertTrue(result.options.first().plan.studyMinutes >= base.options.first().plan.studyMinutes)
        assertTrue(result.options.first().plan.changes <= base.options.first().plan.changes)
        println("HOST ONLY optimized real expected-room case: ${(System.nanoTime()-started)/1e9} s")
        validate(data,result,profiles,desired)
    }

    @Test fun referenceComparisonCoversLongRestsTouchingPeriodsAndFragmentedAvailability() {
        val random = Random(20261015)
        val template = real()
        val rooms = listOf("3-305","3-308","3-408","2-240").map { name -> template.classrooms.single { it.name == name } }
        val windows = listOf(
            listOf(StudyWindow(660,720),StudyWindow(840,1020),StudyWindow(1200,1320)),
            listOf(StudyWindow(600,645),StudyWindow(645,700)),
            listOf(StudyWindow(720,755),StudyWindow(760,800),StudyWindow(830,870)),
            listOf(StudyWindow(1190,1230),StudyWindow(1255,1320)))
        repeat(20) { case ->
            val data = template.copy(slots = template.slots.map { slot -> slot.copy(classrooms = rooms.filter { random.nextBoolean() }) })
            val profiles = rooms.associate { it.id to it.emptyProfile().copy(favorite = random.nextBoolean(),excluded = case % 7 == 0 && it == rooms.last()) }
            val targets = windows[case % windows.size]
            val goal = PlanningGoal(targets.last().start,targets.last().end,case % 3 != 0,
                savedWindows = targets.dropLast(1))
            compareReference("case=$case",data,profiles,goal)
        }
    }

    @Test fun referenceComparisonAlsoCoversExpectedRoomCreditAndOriginalWindowAlternatives() {
        val template = real()
        val rooms = template.classrooms.filter { it.name in setOf("3-305","3-308","3-408") }
        repeat(8) { case ->
            val random = Random(900+case)
            val data = template.copy(slots = template.slots.map { it.copy(classrooms = rooms.filter { random.nextInt(4) != 0 }) })
            val profiles = profiles(data)
            compareReference("expected=$case",data,profiles,
                PlanningGoal(930,1000,case % 2 == 0,savedWindows=listOf(StudyWindow(660,720)),
                    expectedRooms = setOf(rooms[case % rooms.size].id)))
        }
    }

    @Test fun aFailedSmallOriginalSeedCannotDiscardAFeasibleFlexiblePlan() {
        val room = Classroom("01:3-305","3-305","3",50)
        val data = ClassroomSnapshot("01",day,Instant.EPOCH,listOf(
            LessonSlot(1,LocalTime.of(10,0),LocalTime.of(10,45),listOf(room)),
            LessonSlot(3,LocalTime.of(13,0),LocalTime.of(13,45),listOf(room))))
        val goal = PlanningGoal(780,840,true,savedWindows=listOf(StudyWindow(600,660)))
        compareReference("original impossible",data,mapOf(room.id to room.emptyProfile().copy(favorite=true)),goal)
        assertTrue(StudyPlanner.planConfirmed(data,Campus.XITUCHENG,day,emptyMap(),goal).options.isNotEmpty())
    }

    @Test fun cancellationStillInterruptsARealThreePeriodRequest() {
        val data = real(); var checks = 0
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            StudyPlanner.planConfirmed(data,Campus.XITUCHENG,day,profiles(data),three) {
                if (++checks == 400) throw java.util.concurrent.CancellationException()
            }
        }
        assertEquals(400,checks)
    }

    private fun compareReference(label: String, data: ClassroomSnapshot, profiles: Map<String,RoomProfile>,goal: PlanningGoal) {
        val old = ReferenceStudyPlanner.planConfirmed(data,Campus.XITUCHENG,day,profiles,goal)
        val new = StudyPlanner.planConfirmed(data,Campus.XITUCHENG,day,profiles,goal)
        assertEquals(label,old.message,new.message)
        assertEquals("$label recommendation",old.options.firstOrNull()?.plan?.let(::metrics),new.options.firstOrNull()?.plan?.let(::metrics))
        // Equally ranked rooms can represent the same option; compare objective vectors rather than IDs.
        assertEquals("$label alternatives",old.options.map { metrics(it.plan) }.toSet(),new.options.map { metrics(it.plan) }.toSet())
        validate(data,new,profiles,goal)
        if (goal.expectedRooms.isEmpty()) {
            val portfolio=StudyPlanner.portfolio(data,Campus.XITUCHENG,day,profiles,goal)
            val reference=old.options.firstOrNull()?.plan
            if(reference==null || PlanningQuality.usefulVisits(reference)) {
                assertEquals("$label portfolio recommendation",reference?.let(::metrics)?.take(9),
                    portfolio.options.firstOrNull()?.plan?.let(::metrics)?.take(9))
            }
            assertTrue("$label quality floor",portfolio.options.all { PlanningQuality.usefulVisits(it.plan) })
            validate(data,portfolio,profiles,goal)
        }
    }

    private fun validate(data: ClassroomSnapshot,result: PlanningResult,profiles: Map<String,RoomProfile>,goal: PlanningGoal) {
        val availability = ClassroomStudyAvailability(data.slots)
        result.options.forEach { option ->
            val plan = option.plan
            assertTrue(plan.lostMinutes in 0..30)
            assertEquals(goal.windows.sumOf { it.duration }-plan.studyMinutes,plan.lostMinutes)
            assertEquals(ExpectedRoomPreference.minutes(plan.steps,plan.windows,goal.expectedRooms),plan.expectedMinutes)
            assertEquals(goal.windows.size,plan.windows.size)
            goal.windows.zip(plan.windows).forEach { (target,adjusted) ->
                assertTrue(PlanningPolicy.admissible(PlanningGoal(target.start,target.end,goal.flexible),adjusted,30))
                if (!goal.flexible) assertEquals(target,adjusted)
            }
            assertTrue(plan.windows.zipWithNext().all { (a,b) -> a.end <= b.start })
            plan.steps.zipWithNext().forEach { (a,b) -> assertEquals(a.end,b.start) }
            plan.steps.filterIsInstance<PlanningStep.Study>().forEach { study ->
                assertFalse(profiles[study.classroom.id]?.excluded == true)
                val intervals = availability.intervalsFor(data.index.freeSlotsByRoom.getValue(study.classroom.id))
                assertTrue("${study.classroom.name}: $study",intervals.any { it.start <= study.start && it.end >= study.end })
            }
            plan.steps.filterIsInstance<PlanningStep.Transfer>().forEach { assertTrue(it.end-it.start >= it.walkingMinutes) }
        }
    }
}
