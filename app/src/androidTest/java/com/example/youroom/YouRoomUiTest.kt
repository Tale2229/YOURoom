package com.example.youroom

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
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
import org.junit.Assert.assertTrue
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
            override val filters = MutableStateFlow(SearchFilters())
            override val customTags = MutableStateFlow<Set<String>>(emptySet())
            override suspend fun save(filters: SearchFilters) = error("Real settings disabled in UI tests")
            override suspend fun addTag(name: String) = error("Real settings disabled in UI tests")
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
        compose.onNodeWithTag("campus_swipe_region").performTouchInput { swipeLeft() }
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
        compose.onNodeWithTag("campus_label").assertTextEquals("西土城")
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
        compose.onNodeWithTag("campus_swipe_region").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("campus_label").assertTextEquals("沙河")
        compose.onNodeWithTag("room_list").performScrollToNode(hasText("演示楼-A101"))
        compose.onNodeWithText("演示楼-A101").assertIsDisplayed()
        compose.onNodeWithText("2-201").assertDoesNotExist()
        compose.onNodeWithTag("campus_swipe_region").performTouchInput { swipeRight() }
        compose.onNodeWithTag("room_list").performScrollToNode(hasText("2-201"))
        compose.onNodeWithText("2-201").assertIsDisplayed()
    }

    private fun enterDemo() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("demo").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("demo").performScrollTo().performClick()
    }

    @Test fun fixedHeaderAndIndependentScrollPositionsSurviveCampusSwitch() {
        enterDemo()
        val header = compose.onNodeWithTag("campus_header").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("room_list").performScrollToNode(hasText("未来学习大楼-202-203"))
        val scroll = compose.onNodeWithTag("room_list").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue(scroll > 0f)
        assertEquals(header, compose.onNodeWithTag("campus_header").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("campus_swipe_region").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("campus_label").assertTextEquals("沙河")
        compose.onNodeWithTag("campus_swipe_region").performTouchInput { swipeRight() }
        compose.onNodeWithTag("campus_label").assertTextEquals("西土城")
        assertEquals(scroll, compose.onNodeWithTag("room_list").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value())
        compose.onNodeWithTag("campus_SHAHE").assertDoesNotExist()
        compose.onNodeWithTag("campus_XITUCHENG").assertDoesNotExist()
    }

    @Test fun verticalOrOutwardHeaderSwipesDoNotSwitchCampuses() {
        enterDemo()
        compose.onNodeWithTag("campus_swipe_region").performTouchInput { swipeRight() }
        compose.onNodeWithTag("campus_label").assertTextEquals("西土城")
        compose.onNodeWithTag("campus_swipe_region").performTouchInput { swipeDown() }
        compose.onNodeWithTag("campus_label").assertTextEquals("西土城")
        compose.onNodeWithTag("refresh_rooms").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("campus_label").assertTextEquals("西土城")
    }

    @Test fun campusCanBeSwitchedUsingTheAccessibilityAction() {
        enterDemo()
        val action = compose.onNodeWithTag("campus_swipe_region").fetchSemanticsNode().config[SemanticsActions.CustomActions].single()
        assertEquals("切换到沙河校区", action.label)
        compose.runOnIdle { assertTrue(action.action()) }
        compose.onNodeWithTag("campus_label").assertTextEquals("沙河")
    }

    @Test fun listScrollingDoesNotPaintOrSwitchCampus() {
        enterDemo()
        compose.onNodeWithTag("room_list").performTouchInput {
            swipeUp(startY = height * .9f, endY = height * .1f)
        }
        compose.onNodeWithTag("campus_label").assertTextEquals("西土城")
        compose.onNodeWithTag("room_list").performScrollToIndex(1)
        compose.onNodeWithTag("slot_1").assertIsSelected()
        compose.onNodeWithTag("slot_2").assertIsSelected()
        compose.onNodeWithTag("slot_3").assertIsNotSelected()
    }

    @Test fun filterPopupSupportsMultipleBuildingsAndCountsSelectedChips() {
        enterDemo()
        compose.onNodeWithTag("extra_filters").performScrollTo().performClick()
        compose.onNodeWithTag("filter_dialog").assertIsDisplayed()
        compose.onNodeWithTag("building_2").performClick()
        compose.onNodeWithTag("building_3").performClick()
        compose.onNodeWithTag("building_2").assertIsSelected()
        compose.onNodeWithTag("building_3").assertIsSelected()
        compose.onNodeWithTag("building_all").assertIsNotSelected()
        compose.onNodeWithTag("filter_favorite").performClick()
        compose.onNodeWithTag("filter_outlets").performClick()
        compose.onNodeWithTag("apply_filters").performClick()
        compose.onNodeWithTag("filter_dialog").assertDoesNotExist()
        compose.onNodeWithTag("filter_badge").assertTextEquals("4")
        compose.onNodeWithText("已有额外筛选 · 点击修改").assertDoesNotExist()
        compose.onNodeWithTag("campus_swipe_region").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("filter_badge").assertTextEquals("2")
        compose.onNodeWithTag("extra_filters").performClick()
        compose.onNodeWithTag("building_all").assertIsSelected()
        compose.onNodeWithTag("filter_favorite").assertIsSelected()
        compose.onNodeWithTag("filter_outlets").assertIsSelected()
    }

    @Test fun selectedTimeIsSharedEvenAfterEditingTheOtherCampus() {
        enterDemo()
        compose.onNodeWithText("清空").performScrollTo().performClick()
        compose.onNodeWithTag("slot_1").performClick()
        compose.onNodeWithTag("slot_6").performClick()
        compose.onNodeWithTag("campus_swipe_region").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("slot_1").assertIsSelected()
        compose.onNodeWithTag("slot_6").assertIsSelected()
        compose.onNodeWithTag("slot_2").assertIsNotSelected()
        compose.onNodeWithTag("slot_3").performClick()
        compose.onNodeWithTag("campus_swipe_region").performTouchInput { swipeRight() }
        compose.onNodeWithTag("slot_3").assertIsSelected()
    }

    @Test fun customTermCanBeCreatedMarkedAndUsedToFilter() {
        enterDemo()
        compose.onNodeWithTag("extra_filters").performScrollTo().performClick()
        compose.onNodeWithTag("edit_tags").performScrollTo().performClick()
        compose.onNodeWithTag("custom_tag_name").performScrollTo().performTextInput("靠窗")
        compose.onNodeWithTag("add_tag").performScrollTo().performClick()
        compose.onNodeWithTag("filter_tag_靠窗").performScrollTo().assertExists()
        compose.onNodeWithTag("apply_filters").performClick()
        compose.onNodeWithTag("room_list").performScrollToNode(hasText("2-201"))
        compose.onNodeWithText("2-201").performClick()
        compose.onNodeWithText("靠窗").performScrollTo().performClick()
        compose.onNodeWithTag("save_profile").performScrollTo().performClick()
        compose.onNodeWithTag("room_list").performScrollToNode(hasTestTag("extra_filters"))
        compose.onNodeWithTag("extra_filters").performClick()
        compose.onNodeWithTag("filter_tag_靠窗").performScrollTo().performClick()
        compose.onNodeWithTag("apply_filters").performClick()
        compose.onNodeWithTag("filter_badge").assertTextEquals("1")
        compose.onNodeWithTag("room_list").performScrollToNode(hasText("2-201"))
        compose.onNodeWithText("2-201").assertIsDisplayed()
        compose.onNodeWithText("3-108").assertDoesNotExist()
    }

    @Test fun campusTransitionMovesTheWholePageAtMidAnimation() {
        enterDemo()
        val initialHeader = compose.onNodeWithTag("campus_header").fetchSemanticsNode().boundsInRoot
        val action = compose.onNodeWithTag("campus_swipe_region").fetchSemanticsNode().config[SemanticsActions.CustomActions].single()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { assertTrue(action.action()) }
        compose.mainClock.advanceTimeBy(160)
        val moving = compose.onAllNodesWithTag("campus_header").fetchSemanticsNodes()
        assertEquals(2, moving.size)
        assertTrue(moving.any { kotlin.math.abs(it.boundsInRoot.left - initialHeader.left) > initialHeader.width * .2f })
        compose.mainClock.advanceTimeBy(500)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("campus_label").assertTextEquals("沙河")
    }
}
