package com.example.youroom

import com.example.youroom.domain.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class PlanningTransferDisplayTest {
    private val a=Classroom("01:3-108","3-108","3",60)
    private val b=Classroom("01:3-205","3-205","3",60)
    private fun study(start: Int,end: Int,room: Classroom)=PlanningStep.Study(start,end,room,room==a)
    private fun move(start: Int,end: Int,from: Classroom,to: Classroom,walking: Int)=PlanningStep.Transfer(start,end,from,to,walking)
    private fun plan(steps: List<PlanningStep>,windows: List<StudyWindow> = listOf(StudyWindow(540,841))) =
        StudyPlan(StudyWindow(windows.first().start,windows.last().end),steps,295,256,2,0,6,0,6,0,-200,windows)
    private fun slot(n: Int,start: Int,end: Int,room: Classroom)=LessonSlot(n,
        LocalTime.ofSecondOfDay(start*60L),LocalTime.ofSecondOfDay(end*60L),listOf(room))
    private val snapshot=ClassroomSnapshot("01",LocalDate.of(2026,10,16),Instant.EPOCH,
        listOf(slot(1,540,690,a),slot(2,690,735,b),slot(3,735,841,a)))
    @Test fun theReportedRoundTripShowsElevenThirtyToTwelveFifteenWithoutChangingTheComputedPlan() {
        val steps=listOf(study(540,690,a),move(690,693,a,b,3),study(693,732,b),
            move(732,735,b,a,3),study(735,841,a))
        val computed=plan(steps)
        val original=computed.copy(steps=steps.toList())
        val uses=PlanningDisplay.rooms(computed,snapshot)
        assertEquals(listOf(a,b),uses.map { it.classroom })
        assertEquals(listOf(StudyWindow(540,690),StudyWindow(735,841)),uses[0].windows)
        assertEquals(listOf(StudyWindow(690,735)),uses[1].windows)
        assertEquals(original,computed)
        assertEquals(6,computed.walkingMinutes);assertEquals(295,computed.studyMinutes)
    }
    @Test fun genuineWaitingIsNotFilledAlongWithWalking() {
        val computed=plan(listOf(study(540,600,a),move(600,610,a,b,3),study(610,650,b)))
        val uses=PlanningDisplay.rooms(computed)
        assertEquals(listOf(StudyWindow(540,600)),uses[0].windows)
        assertEquals(listOf(StudyWindow(607,650)),uses[1].windows)
    }
    @Test fun aUserRestPeriodCannotBeBridgedByTheTransferBetweenWindows() {
        val computed=plan(listOf(study(540,600,a),move(600,603,a,b,3),PlanningStep.Break(603,780),study(780,841,b)),
            listOf(StudyWindow(540,600),StudyWindow(780,841)))
        val uses=PlanningDisplay.rooms(computed)
        assertEquals(listOf(StudyWindow(540,600)),uses[0].windows)
        assertEquals(listOf(StudyWindow(780,841)),uses[1].windows)
    }
    @Test fun aWalkOverlappingTheNextPeriodCanOnlyFillThePortionInsideThatPeriod() {
        val computed=plan(listOf(study(540,600,a),move(600,603,a,b,3),study(603,660,b)),
            listOf(StudyWindow(540,600),StudyWindow(601,660)))
        val uses=PlanningDisplay.rooms(computed)
        assertEquals(listOf(StudyWindow(540,600)),uses[0].windows)
        assertEquals(listOf(StudyWindow(601,660)),uses[1].windows)
    }
    @Test fun anActuallyUnavailableGapCannotBeRelabeledAsUsableWalkingTime() {
        val blocked=snapshot.copy(slots=listOf(slot(1,540,600,a),slot(2,600,610,a.copy(id="01:4-001")),slot(3,610,650,b)))
        val computed=plan(listOf(study(540,600,a),move(600,610,a,b,3),study(610,650,b)))
        val uses=PlanningDisplay.rooms(computed,blocked)
        assertEquals(listOf(StudyWindow(540,600)),uses[0].windows)
        assertEquals(listOf(StudyWindow(610,650)),uses[1].windows)
    }
    @Test fun noWalkingMinutesAndNonAdjacentStepsDoNotInventAnExtension() {
        val computed=plan(listOf(study(540,600,a),move(600,610,a,b,0),study(610,650,b)))
        assertEquals(listOf(StudyWindow(610,650)),PlanningDisplay.rooms(computed)[1].windows)
        val broken=plan(listOf(study(540,600,a),move(600,603,a,b,3),study(604,650,b)))
        assertEquals(listOf(StudyWindow(604,650)),PlanningDisplay.rooms(broken)[1].windows)
    }
}
