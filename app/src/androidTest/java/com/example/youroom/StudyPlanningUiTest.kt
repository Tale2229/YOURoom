package com.example.youroom

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.youroom.data.DemoData
import com.example.youroom.domain.*
import com.example.youroom.ui.*
import com.example.youroom.ui.theme.YouRoomTheme
import com.example.youroom.ui.theme.campusColorScheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.time.*

class StudyPlanningUiTest {
    @get:Rule val compose = createComposeRule()

    private fun waitForResult(tag: String) {
        // Results below an inline picker may not be composed on a short viewport.
        // Wait using the persistent control item, then scroll to create the result item.
        compose.onNodeWithTag("planner_list").performScrollToNode(hasTestTag("planner_generate"))
        compose.waitUntil(20_000) {
            compose.onAllNodesWithTag("planner_generate").fetchSemanticsNodes().any { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "生成规划" } == true
            }
        }
        compose.onNodeWithTag("planner_list").performScrollToNode(hasTestTag(tag))
    }

    private fun waitForMessage() = waitForResult("planner_message")

    private fun assertFilledButton(tag: String) {
        val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        val fill = pixels[pixels.width / 2, (pixels.height * .18f).toInt()]
        val paper = campusColorScheme(Campus.XITUCHENG, false).background
        assertTrue("shortcut needs a visible fill", kotlin.math.abs(fill.red - paper.red) +
            kotlin.math.abs(fill.green - paper.green) + kotlin.math.abs(fill.blue - paper.blue) > .015f)
    }

    @Test fun allFourWheelsAreInlineAndMovingBlocksGenerationWithoutChangingTheGoal() {
        val demo = DemoData.snapshot()
        var goal by mutableStateOf(PlanningGoal())
        compose.setContent { YouRoomTheme {
            StudyPlanningScreen(YouRoomState(initializing = false, demo = true, snapshot = demo,
                today = demo.queryDate), goal, false, { goal = it }, {}, {}, {}, {})
        } }
        for (tag in listOf("planner_start_hour_wheel", "planner_start_minute_wheel",
            "planner_end_hour_wheel", "planner_end_minute_wheel")) {
            compose.onNodeWithTag(tag).assertIsDisplayed()
        }
        val wheel = compose.onNodeWithTag("planner_start_minute_wheel")
        val row = wheel.fetchSemanticsNode().boundsInRoot.height / 4.5f
        wheel.performTouchInput { down(center); moveBy(Offset(0f, -row), delayMillis = 300) }
        compose.onNodeWithTag("planner_generate").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(PlanningGoal(), goal) }
        wheel.performTouchInput { advanceEventTime(200); up() }
        compose.waitForIdle()
        assertEquals(481, goal.start)
        compose.onNodeWithTag("planner_generate").assertIsEnabled()
    }

    @Test fun simultaneousStartAndEndChangesKeepBothValuesAndAllOtherPreferences() {
        val demo = DemoData.snapshot()
        var goal by mutableStateOf(PlanningGoal(flexible = false, origin = "2"))
        compose.setContent { YouRoomTheme {
            StudyPlanningScreen(YouRoomState(initializing = false, demo = true, snapshot = demo,
                today = demo.queryDate), goal, false, { goal = it }, {}, {}, {}, {})
        } }
        val start = compose.onNodeWithTag("planner_start_hour_wheel").fetchSemanticsNode()
            .config[SemanticsActions.SetProgress]!!.action!!
        val end = compose.onNodeWithTag("planner_end_hour_wheel").fetchSemanticsNode()
            .config[SemanticsActions.SetProgress]!!.action!!
        compose.runOnIdle { start(9f); end(13f) }
        compose.waitForIdle()
        assertEquals(PlanningGoal(540, 780, false, "2"), goal)
    }

    @Test fun freshInputsDefaultToEightAndTwelveWithoutTheRemovedCaptions() {
        val demo = DemoData.snapshot()
        compose.setContent { YouRoomTheme {
            StudyPlanningScreen(YouRoomState(initializing = false, demo = true, snapshot = demo,
                today = demo.queryDate), PlanningGoal(), false, {}, {}, {}, {}, {})
        } }
        compose.onNodeWithTag("planner_start").assertTextContains("08:00")
        compose.onNodeWithTag("planner_end").assertTextContains("12:00")
        compose.onNodeWithText("仅规划今日").assertDoesNotExist()
        compose.onAllNodes(hasText("整体平移最多", substring = true)).assertCountEquals(0)
        compose.onNodeWithTag("planner_adjust").assertExists()
        compose.onNodeWithText("自习规划").assertDoesNotExist()
        compose.onNodeWithText("规则").assertDoesNotExist()
    }

    @Test fun fromNowButtonUsesTheClickTimeAndDoesNotChangeTheEndOrAutoGenerate() {
        val zone = SchoolZone
        val fixed = Clock.fixed(LocalDateTime.of(2026, 10, 13, 15, 20, 59).atZone(zone).toInstant(), zone)
        val data = DemoData.snapshot(clock = fixed).copy(isDemo = false)
        var goal by mutableStateOf(PlanningGoal(origin = "2"))
        var requested by mutableStateOf(true)
        var generationClicks = 0
        compose.setContent { YouRoomTheme {
            StudyPlanningScreen(YouRoomState(initializing = false, loggedIn = true, snapshot = data,
                today = data.queryDate), goal, requested, { goal = it; requested = false },
                { generationClicks++; requested = true }, {}, {}, {}, notBeforeMinute = 921, clock = fixed)
        } }
        waitForMessage()
        val message = compose.onNodeWithTag("planner_message").fetchSemanticsNode().boundsInRoot
        val button = compose.onNodeWithTag("planner_from_now").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(button.left >= message.right)
        assertFilledButton("planner_from_now")
        compose.onNodeWithTag("planner_from_now").performClick()
        assertEquals(PlanningGoal(start = 921, origin = "2"), goal)
        assertEquals(0, generationClicks)
        assertFalse(requested)
        compose.onNodeWithTag("planner_list").performScrollToNode(hasTestTag("planner_start"))
        compose.onNodeWithTag("planner_start").assertTextContains("15:21")
        assertEquals(15f, compose.onNodeWithTag("planner_start_hour_wheel").fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo].current)
        assertEquals(21f, compose.onNodeWithTag("planner_start_minute_wheel").fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo].current)
        compose.onNodeWithTag("planner_end").assertTextContains("12:00")
        compose.onNodeWithTag("planner_from_now").assertDoesNotExist()
    }

    @Test fun invalidEndOffersThreeInlineDurationShortcutsRelativeToTheStart() {
        val demo = DemoData.snapshot()
        var goal by mutableStateOf(PlanningGoal(600, 550, false, "2"))
        var requested by mutableStateOf(true)
        compose.setContent { YouRoomTheme {
            StudyPlanningScreen(YouRoomState(initializing = false, demo = true, snapshot = demo,
                today = demo.queryDate), goal, requested, { goal = it; requested = false },
                { requested = true }, {}, {}, {})
        } }
        for (hours in listOf(1, 2, 4)) {
            waitForMessage()
            val text = compose.onNodeWithTag("planner_message").fetchSemanticsNode().boundsInRoot
            val actions = compose.onNodeWithTag("planner_duration_buttons").fetchSemanticsNode().boundsInRoot
            assertTrue(actions.left >= text.right)
            listOf(1, 2, 4).forEach { assertFilledButton("planner_duration_${it}h") }
            compose.onNodeWithTag("planner_duration_${hours}h").assertIsEnabled().performClick()
            assertEquals(PlanningGoal(600, 600 + hours * 60, false, "2"), goal)
            assertFalse(requested)
            compose.runOnIdle { goal = goal.copy(end = 550); requested = true }
        }
    }

    @Test fun crossDayDurationButtonsAreDisabledInsteadOfWrappingTheEnd() {
        val demo = DemoData.snapshot()
        var goal by mutableStateOf(PlanningGoal(1260, 1200, false))
        var requested by mutableStateOf(true)
        compose.setContent { YouRoomTheme {
            StudyPlanningScreen(YouRoomState(initializing = false, demo = true, snapshot = demo,
                today = demo.queryDate), goal, requested, { goal = it; requested = false }, {}, {}, {}, {})
        } }
        waitForMessage()
        compose.onNodeWithTag("planner_duration_1h").assertIsEnabled()
        compose.onNodeWithTag("planner_duration_2h").assertIsEnabled().performClick()
        assertEquals(1380, goal.end)
        compose.runOnIdle { goal = goal.copy(end = 1200); requested = true }
        waitForMessage()
        compose.onNodeWithTag("planner_duration_4h").assertIsNotEnabled()
        assertEquals(1200, goal.end)
    }

    @Test fun fromNowIsDisabledDuringTheLastMinuteOfTheDay() {
        val fixed = Clock.fixed(LocalDateTime.of(2026, 10, 13, 23, 59).atZone(SchoolZone).toInstant(), SchoolZone)
        val data = DemoData.snapshot(clock = fixed).copy(isDemo = false)
        compose.setContent { YouRoomTheme {
            StudyPlanningScreen(YouRoomState(initializing = false, loggedIn = true, snapshot = data,
                today = data.queryDate), PlanningGoal(), true, {}, {}, {}, {}, {}, notBeforeMinute = 1439, clock = fixed)
        } }
        waitForMessage()
        compose.onNodeWithTag("planner_from_now").assertIsNotEnabled()
    }

    @Test fun anImpossibleRouteUsesTheShortMessageAndHasNoWrongTimeActions() {
        val demo = DemoData.snapshot().let { it.copy(slots = it.slots.take(1)) }
        compose.setContent { YouRoomTheme {
            StudyPlanningScreen(YouRoomState(initializing = false, demo = true, snapshot = demo,
                today = demo.queryDate), PlanningGoal(flexible = false), true, {}, {}, {}, {}, {})
        } }
        waitForMessage()
        compose.onNodeWithTag("planner_message").assertTextEquals("难以满足要求，可缩短时段")
        compose.onNodeWithTag("planner_from_now").assertDoesNotExist()
        compose.onNodeWithTag("planner_duration_buttons").assertDoesNotExist()
    }
    @Test fun aDateChangeImmediatelyRemovesPreviouslyGeneratedPlans() {
        val snapshot = DemoData.snapshot()
        var state by mutableStateOf(YouRoomState(initializing = false, demo = true,
            snapshot = snapshot, today = snapshot.queryDate))
        compose.setContent { YouRoomTheme {
            StudyPlanningScreen(state, PlanningGoal(), true, {}, {}, {}, {}, {})
        } }
        waitForResult("planner_results")
        compose.runOnIdle { state = state.copy(today = state.today.plusDays(1)) }
        compose.onNodeWithTag("planner_results").assertDoesNotExist()
        compose.onNodeWithTag("planner_list").performScrollToNode(hasTestTag("planner_generate"))
        compose.onNodeWithTag("planner_generate").assertIsNotEnabled()
    }
    @Test fun missingOrWrongCampusSnapshotsCannotEnablePlanning() {
        val wrong = DemoData.snapshot(Campus.XITUCHENG)
        compose.setContent { YouRoomTheme(campus = Campus.SHAHE) {
            StudyPlanningScreen(YouRoomState(initializing = false, demo = true, campus = Campus.SHAHE,
                snapshot = wrong), PlanningGoal(), false, {}, {}, {}, {}, {})
        } }
        compose.onNodeWithTag("planner_generate").assertIsNotEnabled()
        compose.onNodeWithTag("planner_results").assertDoesNotExist()
    }

    @Test fun lunchPlansRunByDefaultAndNoInferenceControlsOrResultsRemain() {
        val demo = DemoData.snapshot()
        compose.setContent { YouRoomTheme {
            StudyPlanningScreen(YouRoomState(initializing = false, demo = true, snapshot = demo,
                today = demo.queryDate), PlanningGoal(690, 825, false), true, {}, {}, {}, {}, {})
        } }
        compose.onNodeWithTag("planner_long_breaks").assertDoesNotExist()
        compose.onNodeWithText("允许推测午休空闲").assertDoesNotExist()
        waitForResult("planner_results")
        compose.onNodeWithTag("planner_list").performScrollToNode(hasTestTag("planner_metrics"))
        compose.onNodeWithTag("planner_metrics").assertTextContains("自习 135 分钟", substring = true)
        compose.onNodeWithTag("planner_metrics").assertTextContains("换教室 0 次", substring = true)
        compose.onAllNodes(hasText("推测", substring = true)).assertCountEquals(0)
        compose.onNodeWithText("规则").assertDoesNotExist()
    }

    @Test fun lunchAloneCanSelectAFavoriteOccupiedInBothNeighboringLessons() {
        val demo = DemoData.snapshot()
        val favorite = demo.classrooms.last()
        val data = demo.copy(slots = demo.slots.map { slot ->
            if (slot.number == 5 || slot.number == 6) slot.copy(classrooms = slot.classrooms.filterNot { it.id == favorite.id }) else slot
        })
        compose.setContent { YouRoomTheme {
            StudyPlanningScreen(YouRoomState(initializing = false, demo = true, snapshot = data,
                today = data.queryDate, profiles = mapOf(favorite.id to favorite.emptyProfile().copy(favorite = true))),
                PlanningGoal(735, 780, false), true, {}, {}, {}, {}, {})
        } }
        waitForResult("planner_results")
        compose.onNodeWithTag("planner_list").performScrollToNode(hasTestTag("planner_metrics"))
        compose.onNodeWithTag("planner_metrics").assertTextContains("收藏 45 分钟", substring = true)
        compose.onNodeWithTag("planner_list").performScrollToNode(hasTestTag("planner_step_0"))
        compose.onNodeWithTag("planner_step_0").assertTextContains(favorite.name)
    }
}
