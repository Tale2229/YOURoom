package com.example.youroom

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.youroom.data.DemoData
import com.example.youroom.domain.*
import com.example.youroom.ui.*
import com.example.youroom.ui.theme.YouRoomTheme
import java.io.File
import java.time.*
import kotlin.math.roundToInt
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CompactPlanningUiTest {
    @get:Rule val compose=createComposeRule()
    private val clock=Clock.fixed(Instant.parse("2026-10-15T03:00:00Z"),SchoolZone)
    private val data=DemoData.snapshot(clock=clock).copy(isDemo=false)
    private var state by mutableStateOf(YouRoomState(initializing=false,loggedIn=true,snapshot=data,today=data.queryDate))
    private var goal by mutableStateOf(PlanningDraft(1200,1320,true,
        savedWindows=listOf(StudyWindow(660,720),StudyWindow(840,1020)),expectedRooms=data.classrooms.take(2).map { it.id }.toSet()))
    private val a=data.classrooms[0]; private val b=data.classrooms[1]
    private val plan=StudyPlan(StudyWindow(660,1020),listOf(PlanningStep.Study(660,720,a,true),
        PlanningStep.Study(840,930,a,true),PlanningStep.Study(935,1020,b,false)),235,150,1,0,0,0,5,0,0,
        windows=listOf(StudyWindow(660,720),StudyWindow(840,1020)))
    private val execution=PlanningExecution(1,PlanningInput(data,Campus.XITUCHENG,data.queryDate,emptyMap(),
        PlanningSchedule.confirmedGoal(goal)!!),PlanningResult(options=listOf(PlanningOption("综合",plan))))
    private var fontScale by mutableFloatStateOf(1f)
    private var night by mutableStateOf(false)
    private var generations=0
    private fun render()=compose.setContent {
        val density=LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density,fontScale)) {
            YouRoomTheme(campus=state.campus,darkTheme=night) {
                StudyPlanningScreen(state,goal,mapOf(Campus.XITUCHENG to execution),{goal=it},{generations++},{},{},{},clock=clock)
            }
        }
    }
    private fun label(index: Int)=compose.onNodeWithTag("planner_target_range_$index",true)
    private fun closeEditor() {
        val action=compose.onNodeWithTag("planner_results_area").fetchSemanticsNode().config[SemanticsActions.CustomActions].first()
        compose.runOnIdle { action.action() };compose.waitForIdle()
    }
    private fun shot(name: String) {
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync();android.os.SystemClock.sleep(150)
        File(instrumentation.targetContext.getExternalFilesDir(null),"$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }

    @Test fun fourEqualActionsAreVerticalBesideUniformlyScaledWheels() {
        render()
        val tags=listOf("planner_add_period","planner_expected","planner_adjust","planner_generate")
        val buttons=tags.map { compose.onNodeWithTag(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot }
        val side=with(compose.density) { 48.dp.toPx() }
        buttons.forEach { assertEquals(side,it.width,1f);assertEquals(side,it.height,1f);assertEquals(buttons[0].left,it.left,1f) }
        buttons.zipWithNext().forEach { (first,second) -> assertTrue(first.bottom<=second.top) }
        val wheels=compose.onNodeWithTag("planner_wheels").fetchSemanticsNode().boundsInRoot
        assertTrue(wheels.right<buttons[0].left)
        assertEquals(wheels.top,buttons[0].top,1f)
        assertTrue(buttons.last().bottom-wheels.bottom in 0f..with(compose.density) { 20.dp.toPx() })
        compose.onNodeWithTag("planner_generate").assertContentDescriptionEquals("生成规划")
        compose.onNodeWithTag("planner_adjust").assertIsOn().performClick().assertIsOff()
        compose.onNodeWithTag("planner_expected_badge").assertTextEquals("2")
        assertEquals(0,generations)
        shot("planner-compact-west")
    }

    @Test fun targetTimesAreBelowTheirBarsWhileActualRoomTimesStayAbove() {
        render()
        compose.onNodeWithTag("planner_saved_periods").assertDoesNotExist()
        compose.onNodeWithTag("planner_window").assertDoesNotExist()
        compose.onNodeWithTag("planner_results").assertDoesNotExist()
        val target=compose.onNodeWithTag("planner_target_bar",true).fetchSemanticsNode().boundsInRoot
        assertTrue(label(0).fetchSemanticsNode().boundsInRoot.top>=target.bottom)
        closeEditor()
        val room=compose.onNodeWithTag("planner_range_${a.id}_0",true).fetchSemanticsNode().boundsInRoot
        val bars=compose.onNodeWithTag("room_period_strip_${a.id}",true).fetchSemanticsNode().boundsInRoot
        assertTrue(room.bottom<bars.top)
        label(0).assertTextEquals("11:00–12:00")
        compose.onNodeWithTag("planner_now_marker",true).assertExists()
    }

    @Test fun deletingAllTargetsRefillsTheWheelsButKeepsTheGeneratedRoute() {
        render();closeEditor()
        label(0).performClick();compose.onNodeWithTag("planner_remove_period_0").performClick()
        assertEquals(listOf(StudyWindow(840,1020)),goal.savedWindows)
        assertEquals(660,goal.start);assertEquals(720,goal.end)
        label(0).performClick();compose.onNodeWithTag("planner_remove_period_0").performClick()
        assertTrue(goal.savedWindows.isEmpty())
        compose.onNodeWithTag("planner_step_0").assertExists()
        compose.onNodeWithTag("planner_expand_handle").performClick()
        compose.onNodeWithTag("planner_generate").assertIsNotEnabled()
        compose.onNodeWithTag("planner_add_period").performScrollTo().performClick()
        assertEquals(listOf(StudyWindow(840,1020)),goal.savedWindows)
        assertEquals(0,generations)
        compose.onNodeWithTag("planner_generate").performClick()
        assertEquals(1,generations)
    }

    @Test fun editOpensThePanelAndRefillsOnlyTheChosenConfirmedPeriod() {
        render();closeEditor()
        label(1).performClick();compose.onNodeWithTag("planner_edit_period_1").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("planner_editor").assertExists()
        compose.onNodeWithTag("planner_start").assertTextContains("14:00")
        compose.onNodeWithTag("planner_end").assertTextContains("17:00")
        assertEquals(listOf(StudyWindow(660,720)),goal.savedWindows)
        assertEquals(2,goal.expectedRooms.size);assertTrue(goal.flexible)
        assertEquals(0,generations)
    }

    @Test fun touchingConfirmedPeriodsStaySeparateAndOnlyPlusAddsTheThird() {
        goal=goal.copy(start=780,end=840,savedWindows=listOf(StudyWindow(480,720),StudyWindow(720,780)))
        render()
        label(0).assertTextEquals("08:00–12:00");label(1).assertTextEquals("12:00–13:00")
        label(2).assertDoesNotExist()
        compose.onNodeWithTag("planner_add_period").performClick()
        label(2).assertTextEquals("13:00–14:00")
        compose.onNodeWithTag("planner_add_period").assertIsNotEnabled()
        assertEquals(0,generations)
    }

    @Test fun targetBracketCapsPointUpAndNoLongerDown() {
        goal=goal.copy(savedWindows=listOf(StudyWindow(530,575)))
        render()
        val lines=compose.onNodeWithTag("planner_target_range_lines",true)
        val bounds=lines.fetchSemanticsNode().boundsInRoot
        val bar=compose.onNodeWithTag("planner_target_period_2",true).fetchSemanticsNode().boundsInRoot
        val pixels=lines.captureToImage().toPixelMap()
        fun ink(dy: Float): Boolean {
            val x=(bar.left-bounds.left).roundToInt()
            val y=with(compose.density) { (bar.bottom-bounds.top+dy.dp.toPx()).roundToInt() }
            return (-1..1).any { dx -> (-1..1).any { yy ->
                val p=pixels[(x+dx).coerceIn(0,pixels.width-1),(y+yy).coerceIn(0,pixels.height-1)]
                p.alpha>.5f && p.red<.7f
            } }
        }
        assertTrue("upward cap",ink(8f))
        assertFalse("no downward cap",ink(14f))
    }

    @Test fun targetOpenEndsRemainOpenAndEntirelyOutsidePeriodsStayTextOnly() {
        goal=goal.copy(savedWindows=listOf(StudyWindow(440,540),StudyWindow(1190,1320)))
        render()
        label(0).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"起点开放"))
        label(1).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"终点开放"))
        compose.runOnIdle { goal=goal.copy(savedWindows=listOf(StudyWindow(410,450),StudyWindow(1270,1320))) }
        label(0).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"全部位于课前"))
        label(1).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"全部位于课后"))
    }

    @Test fun crowdedTargetsRemainReadableAtLargeFontSizesAndBothCampusThemes() {
        goal=goal.copy(savedWindows=listOf(StudyWindow(660,680),StudyWindow(695,710),StudyWindow(715,725)))
        render()
        for (campus in Campus.entries) for (dark in listOf(false,true)) {
            compose.runOnIdle {
                fontScale=1.5f;night=dark
                state=state.copy(campus=campus,snapshot=DemoData.snapshot(campus,clock).copy(isDemo=false))
            }
            compose.waitForIdle()
            val bounds=(0..2).map { label(it).fetchSemanticsNode().boundsInRoot }
            bounds.forEachIndexed { i,a -> bounds.drop(i+1).forEach { b -> assertFalse(a.overlaps(b)) } }
            for (i in 0..2) {
                val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                compose.onNodeWithText("${planningTime(goal.savedWindows[i].start)}–${planningTime(goal.savedWindows[i].end)}",useUnmergedTree=true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val result=layouts.single()
                assertFalse("${result.layoutInput.text}: ${result.size}, paragraph ${result.multiParagraph.width}x${result.multiParagraph.height}, width=${result.didOverflowWidth}, height=${result.didOverflowHeight}",result.hasVisualOverflow)
            }
        }
        compose.runOnIdle { fontScale=1f;night=false }
        shot("planner-compact-shahe")
    }
}
