package com.example.youroom

import com.example.youroom.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.util.concurrent.CancellationException

class StudyPlannerTest {
    private val day = LocalDate.of(2026, 10, 12)
    private val a = Classroom("01:2-201", "2-201", "2", 60)
    private val b = Classroom("01:2-202", "2-202", "2", 80)
    private val c = Classroom("01:3-101", "3-101", "3", 50)
    private fun slot(number: Int, start: Int, end: Int, vararg rooms: Classroom) =
        LessonSlot(number, LocalTime.ofSecondOfDay(start.toLong() * 60), LocalTime.ofSecondOfDay(end.toLong() * 60), rooms.toList())
    private fun snapshot(vararg slots: LessonSlot, campus: String = "01") = ClassroomSnapshot(campus, day, Instant.EPOCH, slots.toList())
    private fun run(data: ClassroomSnapshot, goal: PlanningGoal, profiles: Map<String, RoomProfile> = emptyMap()) =
        StudyPlanner.plan(data, if (data.campusId == "04") Campus.SHAHE else Campus.XITUCHENG, day, profiles, goal)
    private fun favorite(room: Classroom) = room.emptyProfile().copy(favorite = true)

    @Test fun directFavoriteWinsWithoutAnyMovement() {
        val data = snapshot(slot(1, 480, 525, a, b), slot(2, 530, 575, a, b))
        val plan = run(data, PlanningGoal(480, 575, false), mapOf(b.id to favorite(b))).options.first().plan
        assertEquals(b.id, (plan.steps.single() as PlanningStep.Study).classroom.id)
        assertEquals(95, plan.favoriteMinutes)
        assertEquals(95, plan.studyMinutes)
        assertEquals(0, plan.changes)
    }

    @Test fun tenFavoriteMinutesNeverJustifyAnExtraRoomChange() {
        val data = snapshot(slot(1, 480, 490, a, b), slot(2, 490, 540, a))
        val plan = run(data, PlanningGoal(480, 540, false), mapOf(b.id to favorite(b))).options.first().plan
        assertEquals(0, plan.changes)
        assertEquals(a.id, (plan.steps.single() as PlanningStep.Study).classroom.id)
    }

    @Test fun forcedTransferHasRealElapsedTimeAndNeverOverlapsStudy() {
        val data = snapshot(slot(1, 480, 525, a), slot(2, 530, 575, b))
        val plan = run(data, PlanningGoal(480, 575, false)).options.first().plan
        assertEquals(1, plan.changes)
        val transfer = plan.steps.filterIsInstance<PlanningStep.Transfer>().single()
        assertTrue(transfer.end - transfer.start >= transfer.walkingMinutes)
        assertEquals(1, plan.lostMinutes)
        assertEquals(94, plan.studyMinutes)
        assertEquals(480, plan.steps.first().start)
        assertEquals(575, plan.steps.last().end)
        plan.steps.zipWithNext().forEach { (first, second) -> assertEquals(first.end, second.start) }
    }

    @Test fun longCrossBuildingTransferCutsConfirmedStudyAndRespectsBudget() {
        val data = snapshot(slot(1, 480, 525, a), slot(2, 525, 570, c))
        val plan = run(data, PlanningGoal(480, 570, false)).options.first().plan
        assertEquals(1, plan.buildingChanges)
        assertTrue(plan.walkingMinutes > 1)
        assertEquals(90 - plan.walkingMinutes, plan.studyMinutes)
        assertTrue(plan.lostMinutes <= 20)
        plan.steps.filterIsInstance<PlanningStep.Study>().forEach {
            if (it.classroom.id == a.id) assertTrue(it.end <= 525) else assertTrue(it.start >= 525)
        }
    }

    @Test fun timeAdjustmentAndRouteAreComparedUsingTheSameCost() {
        val data = snapshot(slot(1, 480, 525, a), slot(2, 530, 575, a, b), slot(3, 575, 620, a, b))
        val goal = PlanningGoal(520, 610, true)
        val result = run(data, goal, mapOf(b.id to favorite(b)))
        val plan = result.options.first().plan
        assertTrue(plan.window.start >= 525)
        assertEquals(0, plan.changes)
        assertTrue(plan.favoriteMinutes > 70)
        assertTrue(result.options.any { it.label == "原时段方案" })
        assertTrue(kotlin.math.abs(plan.window.start - goal.start) <= 15)
        assertTrue(kotlin.math.abs(plan.window.end - goal.end) <= 15)
        assertTrue(plan.window.duration <= goal.end - goal.start)
    }

    @Test fun forbiddenRoomsAreExcludedAndCustomTagsDoNotChangeRanking() {
        val data = snapshot(slot(1, 480, 525, a, b))
        val goal = PlanningGoal(480, 525, false)
        val profiles = mapOf(a.id to favorite(a).copy(excluded = true), b.id to b.emptyProfile().copy(tags = setOf("坏标签")))
        assertEquals(b.id, (run(data, goal, profiles).options.first().plan.steps.first() as PlanningStep.Study).classroom.id)
        assertEquals(run(data, goal).options.first().plan.score,
            run(data, goal, mapOf(a.id to a.emptyProfile().copy(tags = setOf("任意自定义")))).options.first().plan.score)
    }

    @Test fun lunchIsUsableByDefaultWithoutArtificialLossOrMovement() {
        val data = snapshot(slot(5, 690, 735, a), slot(6, 780, 825, a))
        val plan = run(data, PlanningGoal(690, 825, false)).options.first().plan
        assertEquals(135, plan.studyMinutes)
        assertEquals(0, plan.lostMinutes)
        assertEquals(1, plan.steps.size)
        assertEquals(0, plan.changes)
    }

    @Test fun missingPeriodIsNeverBridgedAsConfirmedAvailability() {
        val data = snapshot(slot(1, 480, 525, a), slot(3, 530, 575, a))
        val plan = run(data, PlanningGoal(480, 575, false)).options.first().plan
        assertEquals(5, plan.lostMinutes)
        assertEquals(0, plan.changes)
        assertEquals(2, plan.steps.filterIsInstance<PlanningStep.Study>().size)
    }

    @Test fun lunchAloneIsUsableEvenWhenTheRoomIsOccupiedInBothNeighboringLessons() {
        val data = snapshot(slot(5, 690, 735, b), slot(6, 780, 825, b), slot(7, 830, 875, a))
        val plan = run(data, PlanningGoal(735, 780, false), mapOf(a.id to favorite(a))).options.first().plan
        assertEquals(a.id, (plan.steps.single() as PlanningStep.Study).classroom.id)
        assertEquals(45, plan.studyMinutes)
        assertEquals(45, plan.favoriteMinutes)
        assertEquals(0, plan.lostMinutes)
        assertTrue(run(data, PlanningGoal(734, 780, false), mapOf(b.id to b.emptyProfile().copy(excluded = true))).options.isEmpty())
        assertTrue(run(data, PlanningGoal(735, 781, false), mapOf(b.id to b.emptyProfile().copy(excluded = true))).options.isEmpty())
    }

    @Test fun aBreakLongerThanNinetyMinutesIsStillUsable() {
        val data = snapshot(slot(1, 480, 525, a), slot(2, 630, 675, a))
        val plan = run(data, PlanningGoal(480, 675, false)).options.first().plan
        assertEquals(195, plan.studyMinutes)
        assertEquals(0, plan.changes)
        assertEquals(0, plan.lostMinutes)
    }

    @Test fun lessonsThatAreNotListedFreeCannotBeBridgedByBreakAvailability() {
        val data = snapshot(slot(1, 480, 525, a), slot(2, 530, 575, b), slot(3, 580, 625, a))
        val plan = run(data, PlanningGoal(480, 625, false)).options.first().plan
        assertEquals(2, plan.changes)
        assertTrue(plan.steps.filterIsInstance<PlanningStep.Study>().filter { it.classroom.id == a.id }
            .all { it.end <= 530 || it.start >= 575 })
        assertTrue(run(data, PlanningGoal(480, 625, false), mapOf(b.id to b.emptyProfile().copy(excluded = true))).options.isEmpty())
    }

    @Test fun shortUnconfirmedGapInAFavoriteRoomIsNotIncorrectlyPruned() {
        val data = snapshot(slot(1, 480, 487, a, b), slot(2, 487, 488, a), slot(3, 488, 496, a, b))
        val plan = run(data, PlanningGoal(480, 496, false), mapOf(b.id to favorite(b))).options.first().plan
        assertEquals(15, plan.favoriteMinutes)
        assertEquals(1, plan.lostMinutes)
        assertEquals(0, plan.changes)
    }

    @Test fun preEightTargetsAreAllowedButDaytimeGapsAndInvalidCrossDayTargetsAreRejected() {
        val data = snapshot(slot(1, 480, 525, a))
        assertEquals(55, run(data, PlanningGoal(470, 525, false)).options.first().plan.studyMinutes)
        assertTrue(run(data, PlanningGoal(480, 540, false)).options.isEmpty())
        assertTrue(run(data, PlanningGoal(1300, 500)).options.isEmpty())
        assertTrue(run(data, PlanningGoal(480, 480)).options.isEmpty())
    }

    @Test fun beforeEightAndAfterEightFiftyFiveEveryKnownRoomCanBeChosen() {
        val data = snapshot(slot(1, 480, 525, a), slot(14, 1210, 1255, b))
        for (goal in listOf(PlanningGoal(0, 480, false), PlanningGoal(1255, 1439, false))) {
            val plan = run(data, goal, mapOf(b.id to favorite(b))).options.first().plan
            assertEquals(goal.end - goal.start, plan.studyMinutes)
            assertEquals(goal.end - goal.start, plan.favoriteMinutes)
            assertEquals(b.id, (plan.steps.single() as PlanningStep.Study).classroom.id)
            assertEquals(0, plan.changes)
        }
    }

    @Test fun earlyAndLateAvailabilityStillRespectsExclusionsCampusDateAndRealGenerationTime() {
        val data = snapshot(slot(1, 480, 525, a, b))
        val early = PlanningGoal(420, 479, false)
        val late = PlanningGoal(1255, 1350, false)
        for (goal in listOf(early, late)) {
            val plan = run(data, goal, mapOf(b.id to favorite(b).copy(excluded = true))).options.first().plan
            assertEquals(a.id, (plan.steps.single() as PlanningStep.Study).classroom.id)
            assertTrue(run(data, goal, mapOf(a.id to a.emptyProfile().copy(excluded = true),
                b.id to b.emptyProfile().copy(excluded = true))).options.isEmpty())
            assertTrue(StudyPlanner.plan(data, Campus.SHAHE, day, emptyMap(), goal).options.isEmpty())
            assertTrue(StudyPlanner.plan(data, Campus.XITUCHENG, day.plusDays(1), emptyMap(), goal).options.isEmpty())
        }
        assertEquals(PlanningProblem.PAST_TIME,
            StudyPlanner.plan(data, Campus.XITUCHENG, day, emptyMap(), early, notBeforeMinute = 500).problem)
    }

    @Test fun crossingEightStillRequiresTheRoomToBeFreeInTheFirstLesson() {
        val data = snapshot(slot(1, 480, 525, a), slot(2, 530, 575, b))
        val plan = run(data, PlanningGoal(470, 525, false), mapOf(b.id to favorite(b))).options.first().plan
        assertEquals(a.id, (plan.steps.single() as PlanningStep.Study).classroom.id)
        assertEquals(0, plan.changes)
        assertTrue(run(data, PlanningGoal(480, 525, false), mapOf(a.id to a.emptyProfile().copy(excluded = true))).options.isEmpty())
    }

    @Test fun noSnapshotRoomsAreInventedForEarlyOrLatePlanning() {
        val empty = snapshot(slot(1, 480, 525))
        for (goal in listOf(PlanningGoal(420, 479, false), PlanningGoal(1255, 1350, false)))
            assertTrue(run(empty, goal, mapOf(a.id to favorite(a))).options.isEmpty())
    }

    @Test fun feedbackTypesMatchTheirShortcutsAndNoSolutionUsesTheRequestedCopy() {
        val data = snapshot(slot(1, 480, 525, a))
        assertEquals(PlanningProblem.INVALID_TIME, run(data, PlanningGoal(600, 500)).problem)
        val past = StudyPlanner.plan(data, Campus.XITUCHENG, day, emptyMap(), PlanningGoal(480, 525), notBeforeMinute = 600)
        assertEquals(PlanningProblem.PAST_TIME, past.problem)
        val impossible = run(data, PlanningGoal(480, 540, false))
        assertEquals("难以满足要求，可缩短时段", impossible.message)
        assertNull(impossible.problem)
        assertNull(StudyPlanner.plan(data, Campus.SHAHE, day, emptyMap(), PlanningGoal()).problem)
    }

    @Test fun staleAndMismatchedCampusesCannotProducePlans() {
        val data = snapshot(slot(1, 480, 525, a))
        assertTrue(StudyPlanner.plan(data, Campus.SHAHE, day, emptyMap(), PlanningGoal()).options.isEmpty())
        assertTrue(StudyPlanner.plan(data, Campus.XITUCHENG, day.plusDays(1), emptyMap(), PlanningGoal()).options.isEmpty())
    }

    @Test fun livePlanningNeverRecommendsAStartBeforeGenerationTime() {
        val data = snapshot(slot(1, 480, 525, a), slot(2, 530, 575, a))
        assertTrue(StudyPlanner.plan(data, Campus.XITUCHENG, day, emptyMap(),
            PlanningGoal(480, 575), notBeforeMinute = 540).options.isEmpty())
        val result = StudyPlanner.plan(data, Campus.XITUCHENG, day, emptyMap(),
            PlanningGoal(528, 575), notBeforeMinute = 530)
        assertTrue(result.options.isNotEmpty())
        assertTrue(result.options.all { it.plan.window.start >= 530 })
    }

    @Test fun shaheUsesItsOwnRoomIdsAndFavoriteProfiles() {
        val room = a.copy(id = "04:教一-201", name = "教一-201", building = "教一")
        val data = snapshot(slot(1, 480, 525, room), campus = "04")
        val plan = run(data, PlanningGoal(480, 525, false), mapOf(room.id to favorite(room), a.id to a.emptyProfile().copy(excluded = true)))
            .options.first().plan
        assertEquals(45, plan.favoriteMinutes)
        assertEquals(room.id, (plan.steps.first() as PlanningStep.Study).classroom.id)
    }

    @Test fun cancellationIsCheckedBeforeAResultIsPublished() {
        assertThrows(CancellationException::class.java) {
            StudyPlanner.plan(snapshot(slot(1, 480, 525, a)), Campus.XITUCHENG, day, emptyMap(), PlanningGoal()) {
                throw CancellationException()
            }
        }
    }

    @Test fun candidatesNeverDoubleTheTwentyMinuteShorteningAllowance() {
        val goal = PlanningGoal(500, 600)
        PlanningPolicy.windows(goal).forEach {
            assertTrue(it.duration in 80..100)
            assertTrue(PlanningPolicy.admissible(goal, it))
        }
        assertTrue(PlanningPolicy.windows(goal).contains(StudyWindow(520, 600)))
        assertTrue(PlanningPolicy.windows(goal).contains(StudyWindow(500, 580)))
    }

    @Test fun theFullTwentyMinutesCanBeTrimmedFromOneEdge() {
        val data = snapshot(slot(1, 530, 600, a))
        val plan = run(data, PlanningGoal(510, 600)).options.first().plan
        assertEquals(StudyWindow(530, 600), plan.window)
        assertEquals(20, plan.lostMinutes)
    }

    @Test fun aLiveOriginTripMustFitBeforeTheFirstStudySegment() {
        val data = snapshot(slot(1, 480, 575, a))
        assertTrue(StudyPlanner.plan(data, Campus.XITUCHENG, day, emptyMap(),
            PlanningGoal(480, 575, false, origin = "4"), notBeforeMinute = 480).options.isEmpty())
        val plan = StudyPlanner.plan(data, Campus.XITUCHENG, day, emptyMap(),
            PlanningGoal(480, 575, origin = "4"), notBeforeMinute = 480).options.first().plan
        assertTrue(plan.window.start >= 480 + plan.approachMinutes)
    }

    @Test fun fixedWindowDynamicProgrammingMatchesMinuteEnumeratedSmallSchedules() {
        val third = a.copy(id = "01:2-203", name = "2-203")
        val rooms = listOf(a, b, third)
        for (gap in listOf(0, 3)) for (mask in (1..511 step 7).toList() + listOf(65, 73, 110, 182, 219, 273, 346, 365, 438, 511)) {
            val goal = PlanningGoal(480, 525 + 2 * gap, false)
            val data = snapshot(*(0..2).map { band -> slot(band + 1, 480 + band * (15 + gap), 495 + band * (15 + gap),
                *rooms.filterIndexed { index, _ -> mask and (1 shl (band * 3 + index)) != 0 }.toTypedArray()) }.toTypedArray())
            val profiles = mapOf(b.id to favorite(b))
            val actual = run(data, goal, profiles).options.firstOrNull()?.plan?.score
            val reference = bruteScore(data, rooms, goal, profiles)
            assertEquals("Availability mask $mask / gap $gap", reference, actual)
        }
    }

    private fun bruteScore(data: ClassroomSnapshot, rooms: List<Classroom>, goal: PlanningGoal,
        profiles: Map<String, RoomProfile>): Int? {
        // Independent minute-by-minute reference, including all departure times.
        val known = data.classrooms.map { it.id }.toSet()
        val consecutive = data.slots.sortedBy { it.number }.zipWithNext().filter { (before, after) ->
            after.number == before.number + 1
        }
        val available = Array(rooms.size) { room -> BooleanArray(goal.end - goal.start) { offset ->
            val minute = goal.start + offset
            rooms[room].id in known && (data.slots.any { slot ->
                minute in (slot.start.hour * 60 + slot.start.minute) until (slot.end.hour * 60 + slot.end.minute) &&
                    slot.classrooms.any { it.id == rooms[room].id }
            } || consecutive.any { (before, after) ->
                minute in (before.end.hour * 60 + before.end.minute) until (after.start.hour * 60 + after.start.minute)
            })
        } }
        val memo = mutableMapOf<List<Int>, Int?>()
        fun search(time: Int, room: Int, changes: Int, loss: Int): Int? {
            if (loss > 20 || changes > 2) return null
            if (time == goal.end) return loss * 3 + changes * 20 + changes * 2
            val key = listOf(time, room, changes, loss)
            if (memo.containsKey(key)) return memo[key]
            if (!available[room][time - goal.start]) return null.also { memo[key] = it }
            val bonus = if (profiles[rooms[room].id]?.favorite == true) 1 else 0
            var best: Int? = search(time + 1, room, changes, loss)?.minus(bonus)
            // A study minute must precede each change, matching production's nonempty segments.
            for (target in rooms.indices) {
                val changing = target != room
                val walk = if (changing) 1 else 0
                for (arrival in (time + 1 + walk)..minOf(goal.end - 1, time + 1 + 20 - loss)) {
                    if (arrival <= time + 1) continue
                    val next = search(arrival, target, changes + if (changing) 1 else 0,
                        loss + arrival - time - 1)?.minus(bonus)
                    if (next != null && (best == null || next < best)) best = next
                }
            }
            memo[key] = best; return best
        }
        return rooms.indices.mapNotNull { search(goal.start, it, 0, 0) }.minOrNull()
    }

    @Test fun aLargeAlwaysFreeSnapshotHasACheapDirectRouteWithoutSearchingMoves() {
        val rooms = (1..600).map { Classroom("01:2-${200 + it}", "2-${200 + it}", "2", 60) }
        val data = snapshot(*(0..13).map { number -> slot(number + 1, 480 + number * 45,
            525 + number * 45, *rooms.toTypedArray()) }.toTypedArray())
        val started = System.nanoTime()
        val result = run(data, PlanningGoal(480, 650))
        assertEquals(0, result.options.first().plan.changes)
        println("HOST ONLY: 600 rooms / 14 periods / flexible windows = ${(System.nanoTime() - started) / 1_000_000} ms")
    }
}
