package com.example.youroom

import android.os.Bundle
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.example.youroom.data.SchoolJson
import com.example.youroom.domain.*
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/** Offline only. Uses a public schedule fixture, never school credentials or user preferences. */
class PlannerPerformanceDeviceTest {
    @Test fun portfolioDirectionsUseRealDataAndDoNotMultiplyIntoTwentyFiveIndependentSearches() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val day=LocalDate.of(2026,10,15)
        val data=SchoolJson.snapshot(instrumentation.context.assets.open("planner/west-2026-10-15.json")
            .bufferedReader().use { it.readText() },"01",day,Instant.EPOCH)
        val profiles=data.classrooms.filter { it.name in setOf("3-305","3-308","3-405","3-408") }
            .associate { it.id to it.emptyProfile().copy(favorite=true) }
        val goal=PlanningGoal(1200,1320,true,savedWindows=listOf(StudyWindow(660,720),StudyWindow(840,1020)))
        for (expected in listOf(false,false,true)) {
            val started=SystemClock.elapsedRealtime()
            val result=StudyPlanner.portfolio(data,Campus.XITUCHENG,day,profiles,
                if (expected) goal.copy(expectedRooms=setOf("01:3-305","01:3-405")) else goal) {
                check(SystemClock.elapsedRealtime()-started<120_000) { "Portfolio test watchdog" }
            }
            instrumentation.sendStatus(0,Bundle().apply { putString("stream",
                "PORTFOLIO_BENCH expected=$expected ${SystemClock.elapsedRealtime()-started}ms groups=${result.groups.map { it.optionIndices.size }}\n") })
            assertEquals(5,result.groups.size)
            assertTrue(result.groups.all { it.optionIndices.size<=5 })
            assertTrue(result.groups.take(4).all { it.optionIndices.isNotEmpty() })
            if (!expected) assertEquals(-284,result.options.first().plan.score)
            assertTrue(result.options.all { it.plan.lostMinutes<=30 })
        }
    }

    @Test fun realThreePeriodsAndAdversarialVariantsCompleteWithoutChangingPlanningRules() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val day = LocalDate.of(2026,10,15)
        val data = SchoolJson.snapshot(instrumentation.context.assets.open("planner/west-2026-10-15.json")
            .bufferedReader().use { it.readText() },"01",day,Instant.EPOCH)
        val favoriteNames = setOf("3-305","3-308","3-405","3-408")
        val profiles = data.classrooms.filter { it.name in favoriteNames }.associate { it.id to it.emptyProfile().copy(favorite=true) }
        val goal = PlanningGoal(1200,1320,true,savedWindows=listOf(StudyWindow(660,720),StudyWindow(840,1020)))
        fun measure(label: String, snapshot: ClassroomSnapshot = data, target: PlanningGoal = goal): PlanningResult {
            val start = SystemClock.elapsedRealtime()
            val result = StudyPlanner.planConfirmed(snapshot,Campus.XITUCHENG,day,profiles,target) {
                // Diagnostic watchdog, not a timeout or approximation in the shipped planner.
                check(SystemClock.elapsedRealtime()-start < 90_000) { "$label exceeded the test watchdog" }
            }
            val elapsed = SystemClock.elapsedRealtime()-start
            instrumentation.sendStatus(0,Bundle().apply { putString("stream","PLANNER_BENCH $label ${elapsed}ms options=${result.options.size}\n") })
            assertTrue("$label",result.options.isNotEmpty())
            result.options.forEach { assertTrue(it.plan.lostMinutes <= 30) }
            return result
        }
        repeat(3) { run ->
            val result = measure("reported-$run")
            assertEquals(354,result.options.first().plan.studyMinutes)
            assertEquals(354,result.options.first().plan.favoriteMinutes)
            assertEquals(2,result.options.first().plan.changes)
            assertEquals(0,result.options.first().plan.deviation)
            assertEquals(360,result.options.single { it.label == "少搬动方案" }.plan.studyMinutes)
        }
        measure("desired-rooms",target=goal.copy(expectedRooms=setOf("01:3-305","01:3-405")))
        measure("no-common-room",snapshot=data.copy(slots=data.slots.map { slot ->
            slot.copy(classrooms=slot.classrooms.filterNot { it.name in setOf("2-240","2-339","4-444") })
        }))
        measure("touching-periods",target=PlanningGoal(960,1020,true,
            savedWindows=listOf(StudyWindow(840,900),StudyWindow(900,960))))
    }
}
