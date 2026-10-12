package com.example.youroom

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.youroom.data.ClassroomDataSource
import com.example.youroom.data.local.FilterSettings
import com.example.youroom.domain.*
import com.example.youroom.ui.YouRoomApp
import com.example.youroom.ui.YouRoomViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class YouRoomUiTest {
    @get:Rule val compose = createComposeRule()

    @Before fun renderIsolatedApp() {
        // Never read a real user's Token, database or preferences during instrumentation.
        val source = object : ClassroomDataSource {
            override val profiles = MutableStateFlow<Map<String, RoomProfile>>(emptyMap())
            override suspend fun hasSession() = false
            override suspend fun cached(campus: Campus): ClassroomSnapshot? = null
            override suspend fun login(account: String, password: String) = error("Live login disabled in UI tests")
            override suspend fun refresh(campus: Campus): ClassroomSnapshot = error("Live queries disabled in UI tests")
            override suspend fun logout() = Unit
            override suspend fun saveProfile(profile: RoomProfile) = error("Real persistence disabled in UI tests")
            override suspend fun deleteProfile(roomId: String) = error("Real persistence disabled in UI tests")
        }
        val settings = object : FilterSettings {
            override val filters = MutableStateFlow(Campus.entries.associateWith { SearchFilters() })
            override suspend fun save(campus: Campus, filters: SearchFilters) = error("Real settings disabled in UI tests")
        }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = YouRoomViewModel(source, settings) as T
        }
        compose.setContent {
            val model: YouRoomViewModel = viewModel(factory = factory)
            YouRoomApp(model)
        }
    }

    @Test fun campusSubtitleChangesWithoutChangingItsFontSize() {
        enterDemo()
        compose.onNodeWithTag("campus_label").assertTextEquals("西土城")
        val west = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("campus_label").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(west) }
        compose.onNodeWithTag("campus_SHAHE").performClick()
        compose.onNodeWithTag("campus_label").assertTextEquals("沙河")
        val shahe = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("campus_label").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(shahe) }
        assertEquals(12.sp, west.single().layoutInput.style.fontSize)
        assertEquals(west.single().layoutInput.style.fontSize, shahe.single().layoutInput.style.fontSize)
        assertEquals(west.single().layoutInput.style.fontFamily, shahe.single().layoutInput.style.fontFamily)
    }

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
