package com.example.youroom

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.youroom.data.DemoData
import com.example.youroom.domain.*
import com.example.youroom.ui.*
import com.example.youroom.ui.theme.YouRoomTheme
import org.junit.Rule
import org.junit.Test

class StudyPlanningUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun aDateChangeImmediatelyRemovesPreviouslyGeneratedPlans() {
        val snapshot = DemoData.snapshot()
        var state by mutableStateOf(YouRoomState(initializing = false, demo = true,
            snapshot = snapshot, today = snapshot.queryDate))
        compose.setContent { YouRoomTheme {
            StudyPlanningScreen(state, PlanningGoal(), true, {}, {}, {}, {}, {})
        } }
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("planner_results").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { state = state.copy(today = state.today.plusDays(1)) }
        compose.onNodeWithTag("planner_results").assertDoesNotExist()
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
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("planner_results").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("planner_list").performScrollToNode(hasTestTag("planner_metrics"))
        compose.onNodeWithTag("planner_metrics").assertTextContains("自习 135 分钟", substring = true)
        compose.onNodeWithTag("planner_metrics").assertTextContains("换教室 0 次", substring = true)
        compose.onAllNodes(hasText("推测", substring = true)).assertCountEquals(0)
        compose.onNodeWithTag("planner_list").performScrollToNode(hasText("规则"))
        compose.onNodeWithText("规则").performClick()
        compose.onNodeWithText("将全部课间（包括午间）视为可用", substring = true).assertExists()
        compose.onAllNodes(hasText("推测", substring = true)).assertCountEquals(0)
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
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("planner_results").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("planner_list").performScrollToNode(hasTestTag("planner_metrics"))
        compose.onNodeWithTag("planner_metrics").assertTextContains("收藏 45 分钟", substring = true)
        compose.onNodeWithTag("planner_list").performScrollToNode(hasTestTag("planner_step_0"))
        compose.onNodeWithTag("planner_step_0").assertTextContains(favorite.name)
    }
}
