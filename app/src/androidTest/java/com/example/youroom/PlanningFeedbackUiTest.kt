package com.example.youroom

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toPixelMap
import androidx.test.platform.app.InstrumentationRegistry
import com.example.youroom.data.DemoData
import com.example.youroom.domain.*
import com.example.youroom.ui.*
import com.example.youroom.ui.theme.*
import java.time.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlanningFeedbackUiTest {
    @get:Rule val compose=createComposeRule()
    private var clock by mutableStateOf<Clock>(Clock.fixed(Instant.parse("2026-10-15T23:00:00Z"),SchoolZone))
    private val snapshot=DemoData.snapshot(clock=clock).copy(isDemo=false)
    private var state by mutableStateOf(YouRoomState(initializing=false,loggedIn=true,snapshot=snapshot,today=snapshot.queryDate))
    private var goal by mutableStateOf(PlanningDraft())
    private var fontScale by mutableFloatStateOf(1f)
    private var generated=0
    private fun render()=compose.setContent {
        val density=LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density,fontScale)) {
            YouRoomTheme(campus=state.campus) {
                StudyPlanningScreen(state,goal,emptyMap(),{goal=it},{generated++},{},{},{},clock=clock)
            }
        }
    }
    private fun add()=compose.onNodeWithTag("planner_add_period")
    private fun shot(name: String) {
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync();android.os.SystemClock.sleep(150)
        File(instrumentation.targetContext.getExternalFilesDir(null),"$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }
    @Test fun successfulAdditionBrieflyUsesLooseModeTintAndRejectsDuplicateClicks() {
        render();compose.mainClock.autoAdvance=false
        add().performClick();compose.mainClock.advanceTimeBy(48)
        add().assertIsNotEnabled().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"已添加"))
        assertEquals(listOf(StudyWindow(480,720)),goal.savedWindows)
        val pixels=compose.onNodeWithTag("planner_add_period_face",true).captureToImage().toPixelMap()
        assertEquals(campusColorScheme(Campus.XITUCHENG,false).primaryContainer,pixels[pixels.width/2,pixels.height/5])
        add().performClick()
        assertEquals(1,goal.savedWindows.size)
        compose.mainClock.advanceTimeBy(112)
        assertFalse("confirmation is only a short pulse",add().fetchSemanticsNode().config.contains(SemanticsProperties.StateDescription))
        compose.mainClock.autoAdvance=true
        compose.waitForIdle();add().assertIsEnabled()
        assertFalse(add().fetchSemanticsNode().config.contains(SemanticsProperties.StateDescription))
        assertEquals(0,generated)
    }
    @Test fun expectedRoomTapUsesTheSameBriefTintAndOnlyTheBadgeStaysSelected() {
        goal=goal.copy(expectedRooms=setOf(snapshot.classrooms.first().id))
        render()
        val colors=campusColorScheme(Campus.XITUCHENG,false)
        fun fill()=compose.onNodeWithTag("planner_expected_face",true).captureToImage().toPixelMap().let {
            it[it.width/2,it.height/5]
        }
        assertEquals(colors.surface,fill())
        compose.mainClock.autoAdvance=false
        compose.onNodeWithTag("planner_expected").performClick();compose.mainClock.advanceTimeBy(48)
        compose.onNodeWithTag("expected_room_dialog").assertExists()
        compose.onNodeWithTag("planner_expected").assertIsNotEnabled()
        assertEquals(colors.primaryContainer,fill())
        compose.onNodeWithTag("expected_close").performClick()
        compose.mainClock.advanceTimeBy(320);compose.mainClock.autoAdvance=true;compose.waitForIdle()
        assertEquals(colors.surface,fill())
        compose.onNodeWithTag("planner_expected_badge").assertTextEquals("1")
        assertEquals(1,goal.expectedRooms.size);assertEquals(0,generated)
    }
    @Test fun rejectedAdditionShakesBothWaysAndFeedbackSlidesInAndOutFromTheLeft() {
        goal=PlanningDraft(600,550)
        render();compose.mainClock.autoAdvance=false
        val resting=add().fetchSemanticsNode().positionInRoot.x
        add().performClick();compose.mainClock.advanceTimeBy(48)
        val entering=compose.onNodeWithTag("planner_feedback").fetchSemanticsNode().positionInRoot.x
        val xs=mutableListOf(add().fetchSemanticsNode().positionInRoot.x)
        repeat(5) { compose.mainClock.advanceTimeBy(40);xs+=add().fetchSemanticsNode().positionInRoot.x }
        assertTrue(xs.min()<resting-2f);assertTrue(xs.max()>resting+2f)
        compose.mainClock.advanceTimeBy(120)
        assertEquals(resting,add().fetchSemanticsNode().positionInRoot.x,1f)
        val settled=compose.onNodeWithTag("planner_feedback").fetchSemanticsNode().positionInRoot.x
        assertTrue("feedback enters from the left",entering<settled-20f)
        add().assertIsEnabled();assertTrue(goal.savedWindows.isEmpty());assertEquals(0,generated)
        add().performClick();compose.mainClock.advanceTimeBy(48)
        assertTrue("repeating the same error replays rejection",add().fetchSemanticsNode().positionInRoot.x<resting)
        compose.mainClock.advanceTimeBy(320)
        compose.runOnIdle { goal=goal.copy(end=660) }
        compose.mainClock.advanceTimeBy(48)
        val leaving=compose.onNodeWithTag("planner_feedback").fetchSemanticsNode().positionInRoot.x
        android.util.Log.i("PlannerFeedback","entering=$entering, settled=$settled, leaving=$leaving, shake=$xs")
        assertTrue("feedback exits to the left ($leaving < $settled)",leaving<settled)
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("planner_message").assertDoesNotExist()
        compose.mainClock.autoAdvance=true
    }
    @Test fun allValidationMessagesAreCompactAndShortcutsDoNotConfirmPeriods() {
        render()
        for (scale in listOf(1f,1.5f)) for (problem in listOf("invalid","short","past","overlap")) {
            compose.runOnIdle {
                fontScale=scale
                clock=Clock.fixed(Instant.parse(if(problem=="past") "2026-10-16T07:00:00Z" else "2026-10-15T23:00:00Z"),SchoolZone)
                goal=when(problem) {
                    "invalid" -> PlanningDraft(600,550)
                    "short" -> PlanningDraft(600,629)
                    "past" -> PlanningDraft(480,720)
                    else -> PlanningDraft(600,660,savedWindows=listOf(StudyWindow(480,720)))
                }
            }
            add().performClick();compose.waitForIdle()
            val layouts=mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("planner_message").assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("$problem at $scale has at most two lines",layouts.single().lineCount<=2)
            assertFalse(layouts.single().layoutInput.text.text.endsWith("。"))
            assertFalse(layouts.single().layoutInput.text.text.endsWith("."))
            if(problem=="invalid" || problem=="short") {
                for (hours in listOf(1,2,4)) {
                    compose.onNodeWithTag("planner_duration_${hours}h").assertWidthIsEqualTo(40.dp).assertHeightIsEqualTo(40.dp)
                    val face=compose.onNodeWithTag("planner_duration_${hours}h_face",true).assertWidthIsEqualTo(36.dp)
                    assertEquals(with(compose.density) { 28.dp.toPx()+2f },face.fetchSemanticsNode().boundsInRoot.height,1f)
                }
                if(problem=="short") {
                    compose.onNodeWithTag("planner_message").assertTextEquals("自习时段不能少于30分钟")
                    compose.onNodeWithTag("planner_duration_3h").assertDoesNotExist()
                }
                shot("planner-feedback-$problem-$scale-1.15.4")
                compose.onNodeWithTag("planner_duration_2h").performClick()
                assertEquals(720,goal.end);assertTrue(goal.savedWindows.isEmpty())
            } else {
                shot("planner-feedback-$problem-$scale-1.15.1")
                if(problem=="past") {
                    compose.onNodeWithTag("planner_from_now").performClick()
                    assertEquals(901,goal.start);assertTrue(goal.savedWindows.isEmpty())
                }
            }
        }
        assertEquals(0,generated)
    }

    @Test fun minimumDurationShortcutsOnlyAdjustTheWheelsAndThirtyMinutesCanBeConfirmed() {
        val saved=listOf(StudyWindow(480,540))
        val expected=setOf(snapshot.classrooms.first().id)
        goal=PlanningDraft(600,629,savedWindows=saved,expectedRooms=expected)
        render()
        for(hours in listOf(1,2,4)) {
            compose.runOnIdle { goal=goal.copy(start=600,end=629) }
            add().performClick()
            compose.onNodeWithTag("planner_message").assertTextEquals("自习时段不能少于30分钟")
            compose.onNodeWithTag("planner_duration_${hours}h").performClick()
            assertEquals(600+hours*60,goal.end)
            assertEquals(saved,goal.savedWindows);assertEquals(expected,goal.expectedRooms)
        }
        compose.runOnIdle { goal=goal.copy(start=1300,end=1320) }
        add().performClick()
        compose.onNodeWithTag("planner_duration_1h").assertIsEnabled()
        compose.onNodeWithTag("planner_duration_2h").assertIsEnabled()
        compose.onNodeWithTag("planner_duration_4h").assertIsNotEnabled()
        compose.onNodeWithTag("planner_duration_3h").assertDoesNotExist()
        compose.runOnIdle { goal=goal.copy(start=600,end=630) }
        add().performClick();compose.waitForIdle()
        assertEquals(saved+StudyWindow(600,630),goal.savedWindows)
        assertEquals(expected,goal.expectedRooms);assertEquals(0,generated)
        compose.onNodeWithTag("planner_message").assertDoesNotExist()
    }
}
