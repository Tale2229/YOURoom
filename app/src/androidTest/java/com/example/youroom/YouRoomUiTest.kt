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
        compose.onNodeWithText("可选教室").assertIsDisplayed()
        compose.onNodeWithContentDescription("收藏 2-201").performScrollTo().performClick()
        compose.onNodeWithTag("tab_preferences").performClick()
        compose.onNodeWithText("2-201").assertIsDisplayed()
        compose.onNodeWithText("已收藏").assertIsDisplayed()
    }
}
