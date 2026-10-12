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

    @Test fun slotGridSelectsAnInclusiveRangeAndCanStartAgain() {
        enterDemo()
        compose.onNodeWithTag("slot_4").performScrollTo().performClick()
        compose.onNodeWithTag("slot_6").performScrollTo().performClick()
        compose.onNodeWithTag("slot_4").assertIsSelected()
        compose.onNodeWithTag("slot_5").assertIsSelected()
        compose.onNodeWithTag("slot_6").assertIsSelected()
        compose.onNodeWithTag("slot_3").assertIsNotSelected()
        compose.onNodeWithTag("slot_2").performScrollTo().performClick()
        compose.onNodeWithTag("slot_2").assertIsSelected()
        compose.onNodeWithTag("slot_5").assertIsNotSelected()
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
