package com.example.youroom

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.youroom.data.DemoData
import com.example.youroom.domain.*
import com.example.youroom.ui.*
import com.example.youroom.ui.theme.YouRoomTheme
import com.example.youroom.ui.theme.campusColorScheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.*

class StudyPlanningUiTest {
    @get:Rule val compose = createComposeRule()
    private val demo = DemoData.snapshot()
    private var goal by mutableStateOf(PlanningDraft())
    private var requested by mutableStateOf(false)
    private var state by mutableStateOf(YouRoomState(initializing=false,demo=true,snapshot=demo,today=demo.queryDate))
    private var clock by mutableStateOf<Clock>(Clock.system(SchoolZone))
    private var generationClicks = 0

    private fun edit(next: PlanningDraft) {
        if (PlanningSchedule.confirmedGoal(goal) != PlanningSchedule.confirmedGoal(next)) requested=false
        goal=next
    }
    private fun render() {
        compose.setContent { YouRoomTheme(campus=state.campus) {
            StudyPlanningScreen(state,goal,requested,::edit,{generationClicks++;requested=true},{},{},{},
                clock=clock,showHeader=false)
        } }
    }
    private fun waitForResult(tag: String) {
        compose.onNodeWithTag("planner_list").performScrollToNode(hasTestTag("planner_generate"))
        compose.waitUntil(20_000) {
            compose.onAllNodesWithTag("planner_generate").fetchSemanticsNodes().any {
                it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "生成规划" } == true
            }
        }
        compose.onNodeWithTag("planner_list").performScrollToNode(hasTestTag(tag))
    }
    private fun generate() {
        compose.onNodeWithTag("planner_generate").performScrollTo().performClick()
        waitForResult("planner_results")
    }
    private fun add() = compose.onNodeWithTag("planner_add_period").performScrollTo().performClick()
    private fun message() = waitForResult("planner_message")
    private fun setRealTime(hour: Int,minute: Int,second: Int=0) {
        clock=Clock.fixed(LocalDateTime.of(2026,10,15,hour,minute,second).atZone(SchoolZone).toInstant(),SchoolZone)
        val real=DemoData.snapshot(clock=clock).copy(isDemo=false)
        state=state.copy(demo=false,loggedIn=true,snapshot=real,today=real.queryDate)
    }
    private fun assertFilledButton(tag: String) {
        val pixels=compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        val fill=pixels[pixels.width/2,(pixels.height*.18f).toInt()]
        val paper=campusColorScheme(Campus.XITUCHENG,false).background
        assertTrue(kotlin.math.abs(fill.red-paper.red)+kotlin.math.abs(fill.green-paper.green)+kotlin.math.abs(fill.blue-paper.blue)>.015f)
    }

    @Test fun periodWidthsStayFixedForOneTwoAndThreeCellsAndCaptionsKeepTheirGap() {
        render()
        compose.onNodeWithTag("planner_current_period").assertDoesNotExist()
        compose.onNodeWithTag("planner_saved_period_0").assertDoesNotExist()
        for ((label,tag) in listOf("开始时间" to "planner_start_hour_wheel","结束时间" to "planner_end_hour_wheel")) {
            val caption=compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
            val wheel=compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertEquals(with(compose.density) { 8.dp.toPx() },wheel.top-caption.bottom,1f)
        }
        add();compose.waitForIdle()
        val width=compose.onNodeWithTag("planner_saved_period_0_face",true).fetchSemanticsNode().boundsInRoot.width
        val strip=compose.onNodeWithTag("planner_saved_periods").fetchSemanticsNode().boundsInRoot.width
        assertTrue(width < strip/2)
        repeat(2) {
            add(); compose.waitForIdle()
            assertEquals(width,compose.onNodeWithTag("planner_saved_period_0_face",true).fetchSemanticsNode().boundsInRoot.width,1f)
        }
        compose.onNodeWithTag("planner_current_period").assertDoesNotExist()
        compose.onNodeWithTag("planner_add_period").assertIsNotEnabled()
    }

    @Test fun threeConfirmedPeriodsFitOneRowWithCornerCrossesAndCompleteTimes() {
        goal=PlanningDraft(840,900,savedWindows=listOf(StudyWindow(480,720),StudyWindow(720,780),StudyWindow(780,840)))
        render()
        val faces=(0..2).map { compose.onNodeWithTag("planner_saved_period_"+it+"_face",true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot }
        val strip=compose.onNodeWithTag("planner_saved_periods").fetchSemanticsNode().boundsInRoot
        val add=compose.onNodeWithTag("planner_add_period").fetchSemanticsNode().boundsInRoot
        for (face in faces) {
            assertEquals(faces[0].top,face.top,1f)
            assertEquals(faces[0].width,face.width,1f)
            assertEquals(with(compose.density) { 28.dp.toPx() },face.height,1f)
        }
        assertEquals(strip.left,faces.first().left,1f)
        assertEquals(strip.right,faces.last().right,1f)
        assertTrue(faces.last().right<=add.left)
        for ((index,text) in listOf("08:00–12:00","12:00–13:00","13:00–14:00").withIndex()) {
            compose.onNodeWithTag("planner_saved_period_"+index).assertTextContains(text)
            val results=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNode(hasText(text),true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            assertFalse(results.single().hasVisualOverflow)
            val remove=compose.onNodeWithTag("planner_remove_period_"+index).fetchSemanticsNode().boundsInRoot
            assertTrue(remove.center.x>faces[index].center.x)
            assertEquals(faces[index].top+with(compose.density) { 3.dp.toPx() },remove.center.y,1f)
        }
        val image=compose.onNodeWithTag("planner_screen").captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.externalCacheDir,"planner-confirmations-1.12.1.png")
            .outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
    }

    @Test fun freshInputsAreAnUnconfirmedDraftWithoutACrossOrGeneratedPlan() {
        render()
        compose.onNodeWithTag("planner_start").assertTextContains("08:00")
        compose.onNodeWithTag("planner_end").assertTextContains("12:00")
        compose.onNodeWithTag("planner_current_period").assertDoesNotExist()
        compose.onNodeWithTag("planner_remove_period_0").assertDoesNotExist()
        compose.onNodeWithTag("planner_generate").assertIsNotEnabled()
        compose.onNodeWithText("仅规划今日").assertDoesNotExist()
        compose.onNodeWithText("自习规划").assertDoesNotExist()
        compose.onNodeWithText("规则").assertDoesNotExist()
        compose.onAllNodes(hasText("整体平移最多",substring=true)).assertCountEquals(0)
    }

    @Test fun movingWheelsBlockConfirmationButNeverEditConfirmedPeriods() {
        goal=PlanningSchedule.add(goal).goal!!
        val original=goal
        render()
        val wheel=compose.onNodeWithTag("planner_start_minute_wheel")
        val row=wheel.fetchSemanticsNode().boundsInRoot.height/3.5f
        wheel.performTouchInput { down(center);moveBy(Offset(0f,-row),delayMillis=300) }
        compose.onNodeWithTag("planner_generate").assertIsNotEnabled()
        compose.onNodeWithTag("planner_add_period").assertIsNotEnabled()
        assertEquals(original,goal)
        wheel.performTouchInput { advanceEventTime(200);up() }
        compose.waitForIdle()
        assertEquals(721,goal.start)
        assertEquals(original.savedWindows,goal.savedWindows)
        compose.onNodeWithTag("planner_saved_period_0").assertTextContains("08:00–12:00")
        compose.onNodeWithTag("planner_current_period").assertDoesNotExist()
        compose.onNodeWithTag("planner_saved_period_1").assertDoesNotExist()
    }

    @Test fun simultaneousHourChangesKeepBothDraftValuesAndPreferences() {
        goal=PlanningDraft(flexible=false,origin="2",expectedRooms=setOf("01:2-201"))
        val original=goal
        render()
        val start=compose.onNodeWithTag("planner_start_hour_wheel").fetchSemanticsNode().config[SemanticsActions.SetProgress].action!!
        val end=compose.onNodeWithTag("planner_end_hour_wheel").fetchSemanticsNode().config[SemanticsActions.SetProgress].action!!
        compose.runOnIdle { start(9f);end(13f) }
        compose.waitForIdle()
        assertEquals(original.copy(start=540,end=780),goal)
        compose.onNodeWithTag("planner_generate").assertIsNotEnabled()
    }

    @Test fun addingAllThreeIsRequiredAndTheNextDraftIsNotAHiddenFourthPeriod() {
        render()
        repeat(2) { add();compose.waitForIdle() }
        assertEquals(listOf(StudyWindow(480,720),StudyWindow(720,780)),goal.savedWindows)
        compose.onNodeWithTag("planner_current_period").assertDoesNotExist()
        compose.onNodeWithTag("planner_remove_period_2").assertDoesNotExist()
        generate()
        compose.onNodeWithTag("planner_metrics").assertTextContains("自习 300 分钟",substring=true)
        compose.onNodeWithTag("planner_list").performScrollToNode(hasTestTag("planner_add_period"))
        add();compose.waitForIdle()
        assertEquals(3,goal.savedWindows.size)
        compose.onNodeWithTag("planner_remove_period_2").assertExists()
        compose.onNodeWithTag("planner_current_period").assertDoesNotExist()
        assertEquals(840,goal.start)
        assertEquals(900,goal.end)
        generate()
        compose.onNodeWithTag("planner_window").assertTextContains("3 · 13:00—14:00",substring=true)
        compose.onNodeWithTag("planner_metrics").assertTextContains("自习 360 分钟",substring=true)
    }

    @Test fun deletingAConfirmedPeriodLoadsItsTimesAndAllowsReconfirmation() {
        goal=PlanningDraft(900,960,savedWindows=listOf(StudyWindow(480,720),StudyWindow(720,780),StudyWindow(780,840)))
        render()
        compose.onNodeWithTag("planner_add_period").assertIsNotEnabled()
        compose.onNodeWithTag("planner_remove_period_1").performClick()
        assertEquals(listOf(StudyWindow(480,720),StudyWindow(780,840)),goal.savedWindows)
        assertEquals(720,goal.start)
        assertEquals(780,goal.end)
        compose.waitForIdle()
        compose.onNodeWithTag("planner_start").assertTextContains("12:00")
        compose.onNodeWithTag("planner_end").assertTextContains("13:00")
        compose.onNodeWithTag("planner_current_period").assertDoesNotExist()
        compose.onNodeWithTag("planner_add_period").assertIsEnabled().performClick()
        assertEquals(3,goal.savedWindows.size)
        assertEquals(1,goal.savedWindows.count { it==StudyWindow(720,780) })
    }

    @Test fun removingTheLastConfirmationLeavesNoPeriodBlockAndDisablesGeneration() {
        render();add();compose.waitForIdle()
        compose.onNodeWithTag("planner_remove_period_0").performClick()
        compose.waitForIdle()
        assertTrue(goal.savedWindows.isEmpty())
        compose.onNodeWithTag("planner_current_period").assertDoesNotExist()
        compose.onNodeWithTag("planner_remove_period_0").assertDoesNotExist()
        compose.onNodeWithTag("planner_generate").assertIsNotEnabled()
        assertEquals(480,goal.start)
        assertEquals(720,goal.end)
    }

    @Test fun overlappingDraftCannotBeAddedButConfirmedPeriodsStillGenerate() {
        goal=PlanningDraft(660,780,false,savedWindows=listOf(StudyWindow(480,720)))
        render();add();message()
        assertEquals(1,goal.savedWindows.size)
        compose.onNodeWithTag("planner_message").assertTextContains("时段不能重叠",substring=true)
        compose.onNodeWithTag("planner_duration_buttons").assertDoesNotExist()
        generate()
        compose.onNodeWithTag("planner_metrics").assertTextContains("自习 240 分钟",substring=true)
        compose.onNodeWithTag("planner_message").assertDoesNotExist()
    }

    @Test fun anInvalidUnconfirmedDraftIsNotValidatedAtGeneration() {
        goal=PlanningDraft(780,780,false,savedWindows=listOf(StudyWindow(480,720)))
        render();generate()
        compose.onNodeWithTag("planner_metrics").assertTextContains("自习 240 分钟",substring=true)
        compose.onNodeWithTag("planner_message").assertDoesNotExist()
        assertEquals(780,goal.end)
    }

    @Test fun rollingTheDraftAfterGenerationKeepsTheExistingConfirmedResult() {
        goal=PlanningSchedule.add(goal.copy(flexible=false)).goal!!
        render();generate()
        compose.onNodeWithTag("planner_list").performScrollToNode(hasTestTag("planner_end_hour_wheel"))
        compose.onNodeWithTag("planner_end_hour_wheel").performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
        compose.waitForIdle()
        assertTrue(goal.end<goal.start)
        assertTrue(requested)
        waitForResult("planner_metrics")
        compose.onNodeWithTag("planner_metrics").assertTextContains("自习 240 分钟",substring=true)
        assertEquals(1,generationClicks)
    }

    @Test fun pastAddFeedbackUsesTheSameFromNowButtonWithoutAutoConfirming() {
        setRealTime(15,20,59)
        goal=goal.copy(origin="2")
        render();add();message()
        assertTrue(goal.savedWindows.isEmpty())
        val text=compose.onNodeWithTag("planner_message").fetchSemanticsNode().boundsInRoot
        val button=compose.onNodeWithTag("planner_from_now").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(button.left>=text.right)
        assertFilledButton("planner_from_now")
        compose.onNodeWithTag("planner_from_now").performClick()
        compose.waitForIdle()
        assertEquals(921,goal.start)
        assertEquals(720,goal.end)
        assertEquals("2",goal.origin)
        assertEquals(0,generationClicks)
        assertTrue(goal.savedWindows.isEmpty())
        compose.onNodeWithTag("planner_generate").assertIsNotEnabled()
        compose.onNodeWithTag("planner_from_now").assertDoesNotExist()
    }

    @Test fun invalidAddUsesTheSameThreeDurationButtonsAndDoesNotConfirmTheShortcut() {
        goal=PlanningDraft(600,550,false,"2")
        render()
        for (hours in listOf(1,2,4)) {
            add();message()
            val text=compose.onNodeWithTag("planner_message").fetchSemanticsNode().boundsInRoot
            val actions=compose.onNodeWithTag("planner_duration_buttons").fetchSemanticsNode().boundsInRoot
            assertTrue(actions.left>=text.right)
            listOf(1,2,4).forEach { assertFilledButton("planner_duration_"+it+"h") }
            compose.onNodeWithTag("planner_duration_"+hours+"h").performClick()
            assertEquals(600+hours*60,goal.end)
            assertTrue(goal.savedWindows.isEmpty())
            assertFalse(requested)
            compose.runOnIdle { edit(goal.copy(end=550)) }
        }
    }

    @Test fun crossDayDurationButtonsStayDisabledInsteadOfWrapping() {
        goal=PlanningDraft(1260,1200,false)
        render();add();message()
        compose.onNodeWithTag("planner_duration_1h").assertIsEnabled()
        compose.onNodeWithTag("planner_duration_2h").assertIsEnabled().performClick()
        assertEquals(1380,goal.end)
        compose.runOnIdle { edit(goal.copy(end=1200)) }
        add();message()
        compose.onNodeWithTag("planner_duration_4h").assertIsNotEnabled()
    }

    @Test fun fromNowIsDisabledDuringTheLastMinuteOfTheDay() {
        setRealTime(23,59)
        render();add();message()
        compose.onNodeWithTag("planner_from_now").assertIsNotEnabled()
    }

    @Test fun exactlyFiveMinutesLateCanBeConfirmedAndGenerationDoesNotRecheckTheClock() {
        setRealTime(13,5)
        goal=PlanningDraft(780,840,false)
        render();add();compose.waitForIdle()
        assertEquals(listOf(StudyWindow(780,840)),goal.savedWindows)
        compose.runOnIdle { setRealTime(15,0) }
        generate()
        compose.onNodeWithTag("planner_metrics").assertTextContains("自习 60 分钟",substring=true)
        compose.onNodeWithTag("planner_message").assertDoesNotExist()
        compose.onNodeWithTag("planner_window").assertTextContains("13:00—14:00")
    }

    @Test fun addChecksClickTimeAndRejectsOneSecondBeyondTheGraceBoundary() {
        setRealTime(12,55)
        goal=PlanningDraft(780,840,false)
        render()
        compose.runOnIdle { setRealTime(13,5,1) }
        add();message()
        assertTrue(goal.savedWindows.isEmpty())
        compose.onNodeWithTag("planner_message").assertTextContains("早于当前时间",substring=true)
        compose.onNodeWithTag("planner_from_now").assertIsEnabled()
    }

    @Test fun anImpossibleRouteUsesTheShortMessageWithoutTimeValidationButtons() {
        state=state.copy(snapshot=demo.copy(slots=demo.slots.take(1)))
        goal=PlanningDraft(flexible=false,savedWindows=listOf(StudyWindow(480,720)))
        requested=true
        render();message()
        compose.onNodeWithTag("planner_message").assertTextEquals("难以满足要求，可缩短时段")
        compose.onNodeWithTag("planner_from_now").assertDoesNotExist()
        compose.onNodeWithTag("planner_duration_buttons").assertDoesNotExist()
    }

    @Test fun aDateChangeStillRemovesPreviouslyGeneratedPlans() {
        goal=goal.copy(savedWindows=listOf(StudyWindow(480,720)))
        requested=true
        render();waitForResult("planner_results")
        compose.runOnIdle { state=state.copy(today=state.today.plusDays(1)) }
        compose.onNodeWithTag("planner_results").assertDoesNotExist()
        compose.onNodeWithTag("planner_generate").performScrollTo().assertIsNotEnabled()
    }

    @Test fun missingOrWrongCampusSnapshotsCannotEnablePlanning() {
        state=state.copy(campus=Campus.SHAHE)
        goal=goal.copy(savedWindows=listOf(StudyWindow(480,720)))
        render()
        compose.onNodeWithTag("planner_generate").assertIsNotEnabled()
        compose.onNodeWithTag("planner_results").assertDoesNotExist()
    }

    @Test fun lunchStillWorksForConfirmedPeriodsWithoutInferenceControls() {
        goal=PlanningDraft(900,960,false,savedWindows=listOf(StudyWindow(690,825)))
        requested=true
        render();waitForResult("planner_metrics")
        compose.onNodeWithTag("planner_metrics").assertTextContains("自习 135 分钟",substring=true)
        compose.onNodeWithTag("planner_metrics").assertTextContains("换教室 0 次",substring=true)
        compose.onNodeWithTag("planner_long_breaks").assertDoesNotExist()
        compose.onNodeWithText("允许推测午休空闲").assertDoesNotExist()
        compose.onAllNodes(hasText("推测",substring=true)).assertCountEquals(0)
    }

    @Test fun confirmedLunchAloneCanChooseAFavoriteOccupiedInBothNeighboringLessons() {
        val favorite=demo.classrooms.last()
        val data=demo.copy(slots=demo.slots.map {
            if (it.number==5||it.number==6) it.copy(classrooms=it.classrooms.filterNot { room -> room.id==favorite.id }) else it
        })
        state=state.copy(snapshot=data,profiles=mapOf(favorite.id to favorite.emptyProfile().copy(favorite=true)))
        goal=PlanningDraft(flexible=false,savedWindows=listOf(StudyWindow(735,780)))
        requested=true
        render();waitForResult("planner_metrics")
        compose.onNodeWithTag("planner_metrics").assertTextContains("收藏 45 分钟",substring=true)
        waitForResult("planner_step_0")
        compose.onNodeWithTag("planner_step_0").assertTextContains(favorite.name)
    }

    @Test fun confirmationsAndTheUnconfirmedDraftSurviveRestorationSeparately() {
        val restore=StateRestorationTester(compose)
        restore.setContent {
            var saved by rememberSaveable(stateSaver=PlanningGoalSaver) { mutableStateOf(PlanningDraft()) }
            YouRoomTheme { StudyPlanningScreen(state,saved,false,{saved=it},{},{},{},{},showHeader=false) }
        }
        repeat(2) { add();compose.waitForIdle() }
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("planner_saved_period_0").assertTextContains("08:00–12:00")
        compose.onNodeWithTag("planner_saved_period_1").assertTextContains("12:00–13:00")
        compose.onNodeWithTag("planner_current_period").assertDoesNotExist()
        compose.onNodeWithTag("planner_start").assertTextContains("13:00")
        compose.onNodeWithTag("planner_end").assertTextContains("14:00")
        compose.onNodeWithTag("planner_remove_period_2").assertDoesNotExist()
        compose.onNodeWithTag("planner_add_period").assertIsEnabled().performClick()
        compose.waitForIdle()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("planner_saved_period_2").assertTextContains("13:00–14:00")
        compose.onNodeWithTag("planner_current_period").assertDoesNotExist()
        compose.onNodeWithTag("planner_add_period").assertIsNotEnabled()
    }
}
