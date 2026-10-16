package com.example.youroom

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.example.youroom.data.DemoData
import com.example.youroom.domain.*
import com.example.youroom.ui.*
import com.example.youroom.ui.theme.YouRoomTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlanningAlternativesUiTest {
    @get:Rule val compose=createComposeRule()
    private val data=DemoData.snapshot()
    private var state by mutableStateOf(YouRoomState(initializing=false,demo=true,snapshot=data,today=data.queryDate))
    private val goal=PlanningDraft(flexible=false,savedWindows=listOf(StudyWindow(480,600)))
    private val options=data.classrooms.take(6).mapIndexed { i,room ->
        PlanningOption("方案$i",StudyPlan(StudyWindow(480,600),listOf(PlanningStep.Study(480,600,room,i==0)),
            120,if (i==0) 120 else 0,if (i==0) 1 else 0,0,0,0,0,0,i))
    }
    private val result=PlanningResult(options=options,groups=PlanningDirection.entries.map {
        PlanningGroup(it,if (it==PlanningDirection.EXPECTED) emptyList() else if (it==PlanningDirection.BALANCED) listOf(0,1,2,3,4) else listOf(1,2,3,4,5),
            if (it==PlanningDirection.EXPECTED) "尚未选择期望教室" else null)
    })
    private var execution by mutableStateOf(PlanningExecution(1,PlanningInput(data,Campus.XITUCHENG,data.queryDate,emptyMap(),
        PlanningSchedule.confirmedGoal(goal)!!),result))
    private var draft by mutableStateOf(goal)
    private var generates=0
    private val shahe=DemoData.snapshot(Campus.SHAHE)
    private var shaheExecution by mutableStateOf(PlanningExecution(1,PlanningInput(shahe,Campus.SHAHE,shahe.queryDate,emptyMap(),
        PlanningSchedule.confirmedGoal(goal)!!),result.copy(options=options.mapIndexed { index,option ->
            option.copy(plan=option.plan.copy(steps=listOf(PlanningStep.Study(480,600,shahe.classrooms[index],false))))
        })))
    private fun render()=compose.setContent { YouRoomTheme {
        StudyPlanningScreen(state,draft,mapOf(Campus.XITUCHENG to execution,Campus.SHAHE to shaheExecution),{draft=it},{generates++},{},{},{},
            { campus,id,index -> if (campus==Campus.XITUCHENG && id==execution.id) execution=execution.copy(selectedOption=index)
                else if (campus==Campus.SHAHE && id==shaheExecution.id) shaheExecution=shaheExecution.copy(selectedOption=index) })
    } }
    private fun open() {
        compose.onNodeWithTag("planner_alternatives").performClick(); compose.waitForIdle()
    }
    private fun shot(name: String) {
        // Wait for the Android render thread too, not just Compose's test clock.
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(200)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null),"$name.png").outputStream().use {
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                .compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }

    @Test fun changingDirectionsOnlyBrowsesAndChoosingAnAlternativeUpdatesTheMainRoute() {
        render();open()
        compose.onNodeWithTag("planner_direction_CHANGES").performClick()
        compose.waitForIdle()
        assertEquals(0,execution.selectedOption)
        compose.onNodeWithTag("planner_alternative_CHANGES_0").performClick()
        compose.waitForIdle()
        assertEquals(1,execution.selectedOption)
        assertEquals(0,generates)
        compose.onNodeWithTag("planner_alternatives").assertTextContains("换教室最少 · 方案 1/5",substring=true)
        compose.onNodeWithTag("planner_list").performScrollToNode(hasTestTag("planner_step_0"))
        compose.onNodeWithTag("planner_step_0").assert(hasAnyDescendant(hasText(options[1].plan.steps.filterIsInstance<PlanningStep.Study>().first().classroom.name)))
        open()
        compose.onNodeWithTag("planner_direction_CHANGES").assertIsSelected()
        compose.onNodeWithTag("planner_alternative_CHANGES_0").assertIsSelected()
    }

    @Test fun backingOutDoesNotSelectOrGenerateAndHeaderStaysAboveTheDrawer() {
        render()
        val header=compose.onNodeWithTag("campus_header").fetchSemanticsNode().boundsInRoot
        open()
        val after=compose.onNodeWithTag("campus_header").fetchSemanticsNode().boundsInRoot
        val drawer=compose.onNodeWithTag("planner_drawer").fetchSemanticsNode().boundsInRoot
        assertEquals(header,after); assertTrue(drawer.top>=after.bottom)
        compose.onNodeWithTag("planner_direction_FAVORITES").performClick()
        Espresso.pressBack();compose.waitForIdle()
        assertEquals(0,execution.selectedOption);assertEquals(0,generates)
        compose.onNodeWithTag("planner_alternatives").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"已收起"))
    }

    @Test fun expectedDirectionUsesTheGeneratedInputNotLiveEditorChanges() {
        render()
        compose.runOnIdle { draft=draft.copy(expectedRooms=setOf(data.classrooms.first().id)) }
        open()
        compose.onNodeWithTag("planner_direction_EXPECTED").assertIsNotEnabled()
        assertEquals(0,execution.selectedOption);assertEquals(0,generates)
    }

    @Test fun drawerShowsAtMostFiveSummariesAndKeepsTheLastOneReachable() {
        render()
        val header=compose.onNodeWithTag("campus_header").fetchSemanticsNode().boundsInRoot
        open()
        compose.onNodeWithTag("planner_alternative_list").performScrollToIndex(4)
        compose.onNodeWithTag("planner_alternative_BALANCED_4").assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals(4,execution.selectedOption)
        open();shot("planner-alternatives")
        Espresso.pressBack();compose.waitForIdle()
        compose.onNodeWithTag("planner_list").performScrollToNode(hasTestTag("planner_step_0"))
        compose.waitForIdle()
        assertEquals(header,compose.onNodeWithTag("campus_header").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("YouRoom").assertIsDisplayed()
        shot("planner-route")
    }

    @Test fun newGenerationResetsComparisonButOrdinaryEditsKeepIt() {
        render();open()
        compose.onNodeWithTag("planner_direction_BUILDINGS").performClick()
        compose.onNodeWithTag("planner_alternative_BUILDINGS_1").performClick()
        compose.waitForIdle()
        compose.runOnIdle { draft=draft.copy(flexible=true) }
        open();compose.onNodeWithTag("planner_direction_BUILDINGS").assertIsSelected()
        Espresso.pressBack();compose.waitForIdle()
        compose.runOnIdle { execution=execution.copy(id=2,selectedOption=0) }
        open();compose.onNodeWithTag("planner_direction_BALANCED").assertIsSelected()
        assertEquals(0,execution.selectedOption); assertEquals(0,generates)
    }

    @Test fun eachCampusKeepsItsOwnDirectionAndSelectedPlan() {
        render();open()
        compose.onNodeWithTag("planner_direction_BUILDINGS").performClick()
        compose.onNodeWithTag("planner_alternative_BUILDINGS_1").performClick()
        compose.waitForIdle()
        compose.runOnIdle { state=state.copy(campus=Campus.SHAHE,snapshot=shahe) }
        compose.waitForIdle();open()
        compose.onNodeWithTag("planner_direction_BALANCED").assertIsSelected()
        compose.onNodeWithTag("planner_direction_FAVORITES").performClick()
        compose.onNodeWithTag("planner_alternative_FAVORITES_0").performClick()
        compose.waitForIdle()
        assertEquals(2,execution.selectedOption);assertEquals(1,shaheExecution.selectedOption)
        compose.runOnIdle { state=state.copy(campus=Campus.XITUCHENG,snapshot=data) }
        compose.waitForIdle();open()
        compose.onNodeWithTag("planner_direction_BUILDINGS").assertIsSelected()
        compose.onNodeWithTag("planner_alternative_BUILDINGS_1").assertIsSelected()
        assertEquals(0,generates)
    }
}
