package com.example.youroom

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.youroom.data.DemoData
import com.example.youroom.domain.*
import com.example.youroom.ui.*
import com.example.youroom.ui.theme.YOURoomTheme
import java.io.File
import java.time.Clock
import java.time.Instant
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlanningTransferDisplayUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun actualRouteLabelsCoverWalkingButTheFrozenPlanAndTotalsAreUntouched() {
        val clock=Clock.fixed(Instant.parse("2026-10-16T01:00:00Z"),SchoolZone)
        val a=Classroom("01:3-108","3-108","3",60)
        val b=Classroom("01:3-205","3-205","3",60)
        val original=DemoData.snapshot(clock=clock)
        val data=original.copy(isDemo=false,slots=original.slots.map { it.copy(classrooms=listOf(if (it.number==5) b else a)) })
        val goal=PlanningDraft(841,901,savedWindows=listOf(StudyWindow(540,841)))
        val plan=StudyPlan(StudyWindow(540,841),listOf(
            PlanningStep.Study(540,690,a,true),PlanningStep.Transfer(690,693,a,b,3),
            PlanningStep.Study(693,732,b,false),PlanningStep.Transfer(732,735,b,a,3),PlanningStep.Study(735,841,a,true)),
            295,256,2,0,6,0,6,0,-200)
        val execution=PlanningExecution(1,PlanningInput(data,Campus.XITUCHENG,data.queryDate,emptyMap(),
            PlanningSchedule.confirmedGoal(goal)!!),PlanningResult(options=listOf(PlanningOption("综合",plan))))
        compose.setContent { YOURoomTheme { StudyPlanningScreen(
            YOURoomState(initializing=false,loggedIn=true,snapshot=data,today=data.queryDate),
            goal,mapOf(Campus.XITUCHENG to execution),{},{},{},{},{},clock=clock) } }
        compose.onNodeWithTag("planner_range_${a.id}_0",true).assertTextEquals("09:00–11:30")
        compose.onNodeWithTag("planner_range_${a.id}_1",true).assertTextEquals("12:15–14:01")
        compose.onNodeWithTag("planner_list").performScrollToNode(hasTestTag("planner_step_1"))
        compose.onNodeWithTag("planner_range_${b.id}_0",true).assertTextEquals("11:30–12:15")
        val frozen=execution.result!!.options.single().plan
        assertSame(plan,frozen);assertEquals(295,frozen.studyMinutes);assertEquals(6,frozen.walkingMinutes)
        assertEquals(693,(frozen.steps[2] as PlanningStep.Study).start)
        assertEquals(732,(frozen.steps[2] as PlanningStep.Study).end)
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        File(instrumentation.targetContext.getExternalFilesDir(null),"planner-transfer-display-1.14.2.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }
}
