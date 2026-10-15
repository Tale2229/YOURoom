package com.example.youroom

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.youroom.data.DemoData
import com.example.youroom.domain.*
import com.example.youroom.ui.*
import com.example.youroom.ui.theme.YouRoomTheme
import com.example.youroom.ui.theme.campusColorScheme
import java.time.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlanningPresentationUiTest {
    @get:Rule val compose=createComposeRule()
    private var clock by mutableStateOf<Clock>(Clock.fixed(Instant.parse("2026-10-15T03:00:00Z"),SchoolZone))
    private val data=DemoData.snapshot(clock=clock)
    private var state by mutableStateOf(YouRoomState(initializing=false,demo=true,snapshot=data,today=data.queryDate))
    private val a=data.classrooms[0]; private val b=data.classrooms[1]
    private var draft by mutableStateOf(PlanningDraft(flexible=false,savedWindows=listOf(StudyWindow(480,575),StudyWindow(780,825))))
    private val plan=StudyPlan(StudyWindow(480,825),listOf(PlanningStep.Study(480,525,a,true),
        PlanningStep.Transfer(525,530,a,b,1),PlanningStep.Study(530,575,b,false),
        PlanningStep.Break(575,780),PlanningStep.Study(780,825,a,true)),135,90,2,0,1,0,0,0,0,
        windows=listOf(StudyWindow(480,575),StudyWindow(780,825)))
    private var shownPlan=plan
    private var generations=0
    private var showResult=true
    private fun render()=compose.setContent { YouRoomTheme {
        val goal=PlanningGoal(780,825,false,savedWindows=listOf(StudyWindow(480,575)),expectedRooms=setOf(a.id))
        val execution=PlanningExecution(1,PlanningInput(data,Campus.XITUCHENG,data.queryDate,emptyMap(),goal),
            PlanningResult(options=listOf(PlanningOption("综合",shownPlan))))
        StudyPlanningScreen(state,draft,if (showResult) mapOf(Campus.XITUCHENG to execution) else emptyMap(),
            {draft=it},{generations++},{},{},{},clock=clock)
    } }
    private fun closeEditor() {
        compose.onNodeWithTag("planner_results_area").performTouchInput {
            swipe(Offset(width*.85f,height*.88f),Offset(width*.85f,height*.06f),300)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("planner_editor").assertDoesNotExist()
    }
    private fun pull(distanceDp: Float) {
        val distance=with(compose.density) { distanceDp.dp.toPx() }
        compose.onNodeWithTag("planner_list").performTouchInput {
            val start=Offset(width*.9f,height*.15f)
            swipe(start,start+Offset(0f,distance),350)
        }
        compose.waitForIdle()
    }

    @Test fun lowerUpSwipeClosesAllInputsWithoutChangingTheDraftOrGenerating() {
        render();val original=draft
        val header=compose.onNodeWithTag("campus_header").fetchSemanticsNode().boundsInRoot
        closeEditor()
        listOf("planner_start","planner_end","planner_saved_periods","planner_expected","planner_adjust","planner_generate")
            .forEach { compose.onNodeWithTag(it).assertDoesNotExist() }
        compose.onNodeWithTag("planner_expand_handle").assertIsDisplayed()
        assertEquals(header,compose.onNodeWithTag("campus_header").fetchSemanticsNode().boundsInRoot)
        assertEquals(original,draft);assertEquals(0,generations)
        pull(28f);compose.onNodeWithTag("planner_editor").assertDoesNotExist()
        pull(100f);compose.onNodeWithTag("planner_editor").assertExists()
        assertEquals(original,draft);assertEquals(0,generations)
    }

    @Test fun detailScrollingOnlyReopensTheEditorAfterReturningToTheTopAndPullingFurther() {
        shownPlan=plan.copy(steps=data.classrooms.mapIndexed { index,room ->
            PlanningStep.Study(480+index*10,490+index*10,room,false)
        },windows=listOf(StudyWindow(480,600)))
        render();closeEditor()
        compose.onNodeWithTag("planner_list").performScrollToIndex(7)
        pull(36f)
        compose.onNodeWithTag("planner_editor").assertDoesNotExist()
        compose.onNodeWithTag("planner_list").performScrollToIndex(0)
        pull(28f);compose.onNodeWithTag("planner_editor").assertDoesNotExist()
        pull(100f);compose.onNodeWithTag("planner_editor").assertExists()
    }

    @Test fun theHandleAndAccessibilityActionCanReopenWithoutAPreciseDrag() {
        render();closeEditor()
        compose.onNodeWithTag("planner_expand_handle").performClick()
        compose.waitForIdle();compose.onNodeWithTag("planner_editor").assertExists()
        val action=compose.onNodeWithTag("planner_results_area").fetchSemanticsNode().config[SemanticsActions.CustomActions].first().action
        compose.runOnIdle { assertTrue(action()) }
        compose.waitForIdle();compose.onNodeWithTag("planner_editor").assertDoesNotExist()
    }

    @Test fun manipulatingTheTimeWheelDoesNotCollapseThePanelOrConfirmAPeriod() {
        showResult=false;draft=draft.copy(savedWindows=emptyList())
        render()
        compose.onNodeWithTag("planner_start_hour_wheel").performSemanticsAction(SemanticsActions.SetProgress) { it(9f) }
        compose.waitForIdle()
        compose.onNodeWithTag("planner_editor").assertExists()
        assertTrue(draft.savedWindows.isEmpty());assertEquals(0,generations)
    }

    @Test fun targetBarsStartLightAndOnlyConfirmedMinutePortionsBecomeDark() {
        showResult=false;draft=PlanningDraft(495,510,false)
        render()
        val tag="planner_target_period_1"
        compose.onNodeWithTag(tag,true).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"未选中"))
        compose.onNodeWithTag("planner_add_period").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf(StudyWindow(495,510)),draft.savedWindows)
        val pixels=compose.onNodeWithTag(tag,true).captureToImage().toPixelMap()
        val colors=campusColorScheme(Campus.XITUCHENG,false)
        assertEquals(colors.outlineVariant,pixels[(pixels.width*.1f).toInt(),pixels.height/2])
        assertEquals(colors.primary,pixels[pixels.width/2,pixels.height/2])
        assertEquals(colors.outlineVariant,pixels[(pixels.width*.9f).toInt(),pixels.height/2])
        compose.onNodeWithTag("planner_target_strip").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"当前时间 11:00"))
    }

    @Test fun repeatedRoomsShareFourteenStaticAvailabilityBarsAndAllTheirTimeRanges() {
        render();closeEditor()
        compose.onAllNodesWithTag("planner_room_strip_${a.id}",true).assertCountEquals(1)
        compose.onAllNodesWithTag("planner_room_strip_${b.id}",true).assertCountEquals(1)
        compose.onNodeWithTag("planner_range_${a.id}_0",true).assertTextEquals("08:00–08:45")
        compose.onNodeWithTag("planner_range_${a.id}_1",true).assertTextEquals("13:00–13:45")
        data.slots.forEach { compose.onNodeWithTag("room_period_${a.id}_${it.number}",true).assertExists() }
        compose.onNodeWithTag("planner_metrics").assertDoesNotExist()
        compose.onNodeWithText("间隔").assertDoesNotExist()
        compose.onNodeWithText("目标时段").assertDoesNotExist()
        compose.onNodeWithText("调整").assertDoesNotExist()
        val selection=compose.onNodeWithTag("planner_alternatives").fetchSemanticsNode().boundsInRoot
        val room=compose.onNodeWithTag("planner_step_0").fetchSemanticsNode().boundsInRoot
        assertTrue(selection.bottom<=room.top)
    }

    @Test fun longPressOnAPlannedRoomStripInspectsThePeriodWithoutOpeningTheRoomOrPanel() {
        render();closeEditor()
        compose.onNodeWithTag("room_period_strip_${a.id}",true).performTouchInput {
            down(Offset(width*.2f,height*.35f))
        }
        compose.waitUntil(4_000) { compose.onAllNodesWithTag("room_time_${a.id}_tooltip",true).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasTestTag("room_time_${a.id}_tooltip"),true).assertCountEquals(1)
        compose.onNodeWithTag("planner_editor").assertDoesNotExist()
        compose.onNodeWithTag("room_period_strip_${a.id}",true).performTouchInput { up() }
        compose.waitForIdle()
    }
}
