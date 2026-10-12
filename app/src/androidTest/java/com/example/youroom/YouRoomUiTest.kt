package com.example.youroom

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class YouRoomUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun demoWorkspaceAndPreferencesAreReachableWithoutCredentials() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("demo").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("demo").performScrollTo().performClick()
        compose.onNodeWithText("可选教室").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("收藏 2-201").performScrollTo().performClick()
        compose.onNodeWithTag("tab_preferences").performClick()
        compose.onNodeWithText("2-201").assertIsDisplayed()
        compose.onNodeWithText("已收藏").assertIsDisplayed()
    }

    @Test fun slotGridSupportsDisjointSelectionAndClear() {
        enterDemo()
        compose.onNodeWithText("清空").performScrollTo().performClick()
        compose.onNodeWithTag("slot_1").performScrollTo().performClick()
        compose.onNodeWithTag("slot_3").performClick()
        compose.onNodeWithTag("slot_1").assertIsSelected()
        compose.onNodeWithTag("slot_2").assertIsNotSelected()
        compose.onNodeWithTag("slot_3").assertIsSelected()
        compose.onNodeWithText("清空").performClick()
        compose.onNodeWithTag("room_list").performScrollToNode(hasText("请选择自习时段"))
        compose.onNodeWithText("请选择自习时段").assertIsDisplayed()
    }

    @Test fun gridDragCanAddAndRemoveWithoutTogglingTheWholePage() {
        enterDemo()
        compose.onNodeWithText("清空").performScrollTo().performClick()
        val grid = compose.onNodeWithTag("slot_grid").fetchSemanticsNode().boundsInRoot
        val first = compose.onNodeWithTag("slot_1").fetchSemanticsNode().boundsInRoot.center - grid.topLeft
        val third = compose.onNodeWithTag("slot_3").fetchSemanticsNode().boundsInRoot.center - grid.topLeft
        compose.onNodeWithTag("slot_grid").performTouchInput {
            down(first)
            moveTo(third)
            up()
        }
        compose.onNodeWithTag("slot_1").assertIsSelected()
        compose.onNodeWithTag("slot_2").assertIsSelected()
        compose.onNodeWithTag("slot_3").assertIsSelected()
        compose.onNodeWithTag("campus_XITUCHENG").assertIsSelected()
        compose.onNodeWithTag("slot_grid").performTouchInput {
            down(first)
            moveTo(third)
            up()
        }
        compose.onNodeWithTag("slot_1").assertIsNotSelected()
        compose.onNodeWithTag("slot_2").assertIsNotSelected()
    }

    @Test fun cancelingAPaintGestureRestoresTheSelection() {
        enterDemo()
        compose.onNodeWithText("清空").performScrollTo().performClick()
        val grid = compose.onNodeWithTag("slot_grid").fetchSemanticsNode().boundsInRoot
        val first = compose.onNodeWithTag("slot_1").fetchSemanticsNode().boundsInRoot.center - grid.topLeft
        val third = compose.onNodeWithTag("slot_3").fetchSemanticsNode().boundsInRoot.center - grid.topLeft
        compose.onNodeWithTag("slot_grid").performTouchInput { down(first); moveTo(third); cancel() }
        compose.onNodeWithTag("slot_1").assertIsNotSelected()
        compose.onNodeWithTag("slot_2").assertIsNotSelected()
        compose.onNodeWithTag("slot_3").assertIsNotSelected()
    }

    @Test fun campusDemoSwitchDoesNotCarryWestClassroomsIntoShahe() {
        enterDemo()
        compose.onNodeWithTag("campus_SHAHE").performScrollTo().performClick()
        compose.onNodeWithTag("campus_SHAHE").assertIsSelected()
        compose.onNodeWithText("演示楼-A101").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2-201").assertDoesNotExist()
        compose.onNodeWithTag("campus_XITUCHENG").performScrollTo().performClick()
        compose.onNodeWithText("2-201").performScrollTo().assertIsDisplayed()
    }

    private fun enterDemo() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("demo").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("demo").performScrollTo().performClick()
    }
}
