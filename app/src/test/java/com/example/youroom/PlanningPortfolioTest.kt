package com.example.youroom

import com.example.youroom.data.SchoolJson
import com.example.youroom.domain.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class PlanningPortfolioTest {
    private val day = LocalDate.of(2026,10,15)
    private fun real() = SchoolJson.snapshot(javaClass.getResource("/planner/west-2026-10-15.json")!!.readText(),"01",day,Instant.EPOCH)
    private fun run(goal: PlanningGoal, expected: Boolean = false): PlanningResult {
        val data = real()
        val profiles = data.classrooms.filter { it.name in setOf("3-305","3-308","3-405","3-408") }
            .associate { it.id to it.emptyProfile().copy(favorite=true) }
        val start = System.nanoTime()
        val result = StudyPlanner.portfolio(data,Campus.XITUCHENG,day,profiles,
            if (expected) goal.copy(expectedRooms=setOf("01:3-305","01:3-405")) else goal)
        println("PORTFOLIO expected=$expected ${(System.nanoTime()-start)/1e9}s groups=${result.groups.map { it.optionIndices.size }}")
        assertEquals(5,result.groups.size)
        assertEquals(result.options.size,result.options.distinctBy { it.plan.signature }.size)
        result.groups.forEach { group ->
            assertTrue(group.optionIndices.size<=5)
            val plans = group.optionIndices.map { result.options[it].plan }
            assertEquals(plans.size,plans.distinctBy(PlanningPortfolio::routeKey).size)
            if (group.direction!=PlanningDirection.BALANCED) assertEquals(plans.sortedWith(PlanningPortfolio.order(group.direction)),plans)
        }
        val availability = ClassroomStudyAvailability(data.slots)
        result.options.forEach { option ->
            val plan=option.plan
            assertTrue(PlanningQuality.admits(plan,result.options.map { it.plan }.minWith(StudyPlanner.order)))
            assertTrue(plan.lostMinutes in 0..goal.lossLimit)
            assertEquals(goal.windows.sumOf { it.duration }-plan.studyMinutes,plan.lostMinutes)
            assertEquals(ExpectedRoomPreference.minutes(plan.steps,plan.windows,if (expected) setOf("01:3-305","01:3-405") else emptySet()),plan.expectedMinutes)
            plan.steps.filterIsInstance<PlanningStep.Study>().forEach { step ->
                assertTrue(availability.intervalsFor(data.index.freeSlotsByRoom.getValue(step.classroom.id)).any {
                    it.start<=step.start && it.end>=step.end
                })
            }
        }
        return result
    }
    @Test fun realThreePeriodsKeepTheRecommendationAndFiveIndependentDirections() {
        val result=run(PlanningGoal(1200,1320,true,savedWindows=listOf(StudyWindow(660,720),StudyWindow(840,1020))))
        assertEquals(-284,result.options.first().plan.score)
        assertEquals(result.options.minOf { it.plan.changes },
            result.options[result.groups.single { it.direction==PlanningDirection.CHANGES }.optionIndices.first()].plan.changes)
        assertTrue(result.groups.last().optionIndices.isEmpty())
    }
    @Test fun desiredRoomsKeepTheCoverageAndMoveGuards() {
        val result=run(PlanningGoal(1200,1320,true,savedWindows=listOf(StudyWindow(660,720),StudyWindow(840,1020))),true)
        val base=result.options.map { it.plan }.minWith(StudyPlanner.order)
        result.groups.last().optionIndices.forEach { index ->
            assertTrue(result.options[index].plan.studyMinutes>=base.studyMinutes)
            assertTrue(result.options[index].plan.changes<=base.changes)
            assertTrue(result.options[index].plan.expectedMinutes>0)
        }
    }
    @Test fun singlePeriodKeepsFourQualityRoomsWithoutPaddingAnyDirection() {
        val result=run(PlanningGoal(1260,1320,false))
        assertEquals(4,result.groups.single { it.direction==PlanningDirection.CHANGES }.optionIndices.size)
        assertEquals(4,result.groups.first().optionIndices.size) // Four favored rooms; do not pad with poor alternatives.
        assertTrue(result.options.all { it.plan.changes==0 && it.plan.studyMinutes==60 })
    }
    @Test fun cancellationAndStaleDataAreNotBypassedByPortfolioSearch() {
        val data=real()
        assertTrue(StudyPlanner.portfolio(data,Campus.SHAHE,day,emptyMap(),PlanningGoal()).options.isEmpty())
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            StudyPlanner.portfolio(data,Campus.XITUCHENG,day,emptyMap(),PlanningGoal()) { throw java.util.concurrent.CancellationException() }
        }
    }
    @Test fun anotherCampussExpectedRoomsDoNotEnableThisDirectionAndEmptyDataKeepsItsError() {
        val data=real()
        val result=StudyPlanner.portfolio(data,Campus.XITUCHENG,day,emptyMap(),
            PlanningGoal(1260,1320,false,expectedRooms=setOf("04:教室A101")))
        assertEquals("尚未选择期望教室",result.groups.last().message)
        val excluded=data.classrooms.associate { it.id to it.emptyProfile().copy(excluded=true) }
        assertEquals("当前数据中没有可规划的教室。",StudyPlanner.portfolio(data,Campus.XITUCHENG,day,excluded,
            PlanningGoal(1260,1320,false,expectedRooms=setOf(data.classrooms.first().id))).message)
    }
}
