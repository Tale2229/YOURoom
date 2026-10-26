package com.example.youroom

import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.youroom.data.DemoData
import com.example.youroom.domain.*
import com.example.youroom.ui.*
import com.example.youroom.ui.theme.YOURoomTheme
import java.time.Clock
import java.time.Instant
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlannerHistoryWorkflowUiTest {
    @get:Rule val compose=createComposeRule()
    private val clock=Clock.fixed(Instant.parse("2026-10-24T00:00:00Z"),SchoolZone)
    private val data=DemoData.snapshot(clock=clock)
    private var state by mutableStateOf(YOURoomState(initializing=false,demo=true,snapshot=data,today=data.queryDate))
    private var goal by mutableStateOf(PlanningDraft(flexible=false,savedWindows=listOf(StudyWindow(480,540))))
    private val utility=PlannerUtilityState()
    private val initialInput=PlanningInput(data,Campus.XITUCHENG,data.queryDate,emptyMap(),PlanningGoal(480,540,false))
    private val result=PlanningResult(options=listOf(PlanningOption("推荐",
        StudyPlan(StudyWindow(480,540),listOf(PlanningStep.Study(480,540,data.classrooms.first(),false)),
            60,0,0,0,0,0,0,0,0))))
    private var plans by mutableStateOf(mapOf(Campus.XITUCHENG to PlanningExecution(1,initialInput,result)))
    private val saved=PlanningHistoryRecord("saved","demo",clock.instant(),data.queryDate,Campus.XITUCHENG,
        PlanningGoal(600,660,false),data,result,0,PlanningDirection.BALANCED)
    private var cleared=0
    private fun render()=compose.setContent {
        YOURoomTheme(campus=state.campus) {
            StudyPlanningScreen(state,goal,plans,{goal=it},{},{},{},{},clock=clock,showHeader=false,
                history=listOf(saved),utilityState=utility,onClearPlanning={ cleared++;plans=emptyMap() })
        }
    }
    private fun openHistory() { compose.onNodeWithTag("planner_history").performClick();compose.waitForIdle() }
    private fun close() { compose.onNodeWithContentDescription("收起面板").performClick();compose.waitForIdle() }
    private fun hour(tag: String,value: Int) {
        compose.onNodeWithTag("${tag}_hour_wheel").performSemanticsAction(SemanticsActions.SetProgress) { it(value.toFloat()) }
        compose.waitForIdle()
    }

    @Test fun closeAndViewResultConsumeCompletionButAnotherGenerationCanNotify() {
        render();openHistory()
        // A result already on screen is not a new completion when opening a panel.
        compose.onNodeWithText("计算完成").assertDoesNotExist()
        compose.runOnIdle { plans=mapOf(Campus.XITUCHENG to PlanningExecution(2,initialInput)) }
        compose.runOnIdle { plans=mapOf(Campus.XITUCHENG to PlanningExecution(2,initialInput,result)) }
        compose.onNodeWithTag("planner_message").assertTextEquals("计算完成")
        close();openHistory()
        compose.onNodeWithText("计算完成").assertDoesNotExist()
        close()
        compose.runOnIdle { plans=mapOf(Campus.XITUCHENG to PlanningExecution(3,initialInput,result)) }
        compose.onNodeWithTag("planner_leisure").performClick();compose.waitForIdle()
        compose.onNodeWithText("计算完成").assertDoesNotExist()
        compose.runOnIdle { plans=mapOf(Campus.XITUCHENG to PlanningExecution(4,initialInput)) }
        compose.runOnIdle { plans=mapOf(Campus.XITUCHENG to PlanningExecution(4,initialInput,result)) }
        compose.onNodeWithTag("planner_message").assertTextEquals("计算完成")
        compose.onNodeWithTag("planner_view_result").performClick();compose.waitForIdle()
        openHistory();compose.onNodeWithText("计算完成").assertDoesNotExist()
    }

    @Test fun liveAndHistoricalPlansNeverMoveOrResizeDuringEitherCornerReveal() {
        render()
        fun checkBothPanels() {
            val plan=compose.onNodeWithTag("planner_workspace").fetchSemanticsNode()
            val origin=plan.positionInRoot
            val size=plan.size
            fun unchanged()=compose.runOnIdle {
                assertEquals(origin.y,plan.positionInRoot.y,.5f)
                assertEquals(origin.x,plan.positionInRoot.x,.5f)
                assertEquals(size,plan.size)
            }
            for(tag in listOf("planner_history","planner_leisure")) {
                compose.mainClock.autoAdvance=false
                compose.onNodeWithTag(tag).performClick()
                compose.mainClock.advanceTimeBy(80);unchanged()
                compose.mainClock.advanceTimeBy(450);unchanged()
                compose.onNodeWithContentDescription("收起面板").performClick()
                compose.mainClock.advanceTimeBy(80);unchanged()
                compose.mainClock.advanceTimeBy(350);unchanged()
                compose.mainClock.autoAdvance=true;compose.waitForIdle()
                compose.onNodeWithText("计算完成").assertDoesNotExist()
            }
        }
        checkBothPanels()
        openHistory();compose.onNodeWithTag("history_saved").performClick();compose.waitForIdle()
        checkBothPanels()
    }

    @Test fun replayClearsDraftPeriodsButWheelsKeepHistoricalTargetsUntilANewPeriodIsConfirmed() {
        render();openHistory()
        compose.onNodeWithTag("history_saved").performClick();compose.waitForIdle()
        compose.runOnIdle { assertTrue(goal.savedWindows.isEmpty());assertEquals(saved.id,utility.recordId) }
        hour("planner_start",13);hour("planner_end",14)
        compose.runOnIdle {
            assertEquals(780,goal.start);assertEquals(840,goal.end)
            assertEquals(saved.id,utility.recordId);assertEquals(0,cleared)
        }
        compose.onNodeWithTag("planner_target_range_0",true).assertExists()
        compose.onNodeWithTag("planner_add_period").performClick();compose.waitForIdle()
        compose.runOnIdle {
            assertNull(utility.recordId);assertEquals(1,cleared)
            assertEquals(listOf(StudyWindow(780,840)),goal.savedWindows);assertTrue(plans.isEmpty())
        }
        compose.onNodeWithTag("planner_step_0").assertDoesNotExist()
    }

    @Test fun launchIsDisabledForDisplayedInputAndWheelOnlyEditsButFavoriteChangesEnableIt() {
        render()
        compose.onNodeWithTag("planner_generate").assertIsNotEnabled()
        hour("planner_start",13)
        compose.onNodeWithTag("planner_generate").assertIsNotEnabled()
        compose.runOnIdle {
            val room=data.classrooms.first()
            state=state.copy(profiles=mapOf(room.id to room.emptyProfile().copy(favorite=true)))
        }
        compose.onNodeWithTag("planner_generate").assertIsEnabled()
    }

    @Test fun ordinaryReaderSwipesForwardAndBackWithoutLaunchingOrInterruptingPlanning() {
        render()
        compose.onNodeWithTag("planner_leisure").performClick();compose.waitForIdle()
        val original=utility.item!!.id
        val previousPlans=plans
        compose.onNodeWithTag("planner_leisure_reader").performTouchInput { swipeLeft() }
        compose.waitUntil(5_000) { utility.item!!.id!=original }
        val second=utility.item!!.id
        compose.onNodeWithTag("planner_leisure_reader").performTouchInput { swipeRight() }
        compose.waitUntil(5_000) { utility.item!!.id==original }
        compose.onNodeWithTag("planner_leisure_next").performClick()
        compose.waitUntil(5_000) { utility.item!!.id==second }
        assertSame(previousPlans,plans)
    }

    @Test fun historicalReplayAllowsLiveValidationShortcutsWithoutDiscardingTheReplay() {
        state=state.copy(demo=false,loggedIn=true,snapshot=data.copy(isDemo=false))
        render();openHistory()
        compose.onNodeWithTag("history_saved").performClick();compose.waitForIdle()
        // Both issues coexist: past start AND an end no later than that start.
        hour("planner_start",7);hour("planner_end",7)
        compose.onNodeWithTag("planner_add_period").performClick();compose.waitForIdle()
        compose.onNodeWithTag("planner_message").assertTextContains("目标时段已早于当前时间",substring=true)
        compose.onNodeWithTag("planner_duration_1h").assertDoesNotExist()
        compose.onNodeWithTag("planner_from_now").assertIsEnabled().performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(481,goal.start);assertEquals(420,goal.end)
            assertEquals(saved.id,utility.recordId);assertEquals(0,cleared)
        }
        for(hours in listOf(1,2,4)) {
            hour("planner_end",8)
            compose.onNodeWithTag("planner_add_period").performClick();compose.waitForIdle()
            compose.onNodeWithTag("planner_from_now").assertDoesNotExist()
            listOf(1,2,4).forEach {
                compose.onNodeWithTag("planner_duration_${it}h").assertIsEnabled()
            }
            compose.onNodeWithTag("planner_duration_${hours}h").performClick();compose.waitForIdle()
            compose.runOnIdle {
                assertEquals(481+60*hours,goal.end)
                assertEquals(saved.id,utility.recordId);assertEquals(0,cleared)
                assertTrue(goal.savedWindows.isEmpty())
            }
            compose.onNodeWithTag("planner_target_range_0",true).assertExists()
        }
        compose.onNodeWithTag("planner_add_period").performClick();compose.waitForIdle()
        compose.runOnIdle {
            assertNull(utility.recordId);assertEquals(1,cleared)
            assertEquals(listOf(StudyWindow(481,721)),goal.savedWindows)
        }
    }
}
