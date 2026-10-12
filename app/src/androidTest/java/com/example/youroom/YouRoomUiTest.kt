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
            override val catalog = MutableStateFlow(TagCatalog())
        override val buildings = MutableStateFlow(Campus.entries.associateWith { emptySet<String>() })
            override suspend fun save(filters: SearchFilters) = error("Real settings disabled in UI tests")
            override suspend fun addTag(name: String) = error("Real settings disabled in UI tests")
            override suspend fun saveBuildings(campus: Campus, names: Set<String>) = error("Real settings disabled in UI tests")
            override suspend fun removeTag(name: String) = error("Real settings disabled in UI tests")
            override suspend fun reorderTags(names: List<String>) = error("Real settings disabled in UI tests")
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
        compose.onNodeWithText("请选择自习时段").assertDoesNotExist()
        compose.onNodeWithTag("slot_summary").assertDoesNotExist()
        compose.onNodeWithTag("room_list").performScrollToNode(hasText("2-201"))
        compose.onNodeWithText("2-201").assertIsDisplayed()
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
        compose.onNodeWithTag("apply_filters").assertDoesNotExist()
        compose.onNodeWithText("个人词条").assertDoesNotExist()
        compose.onNodeWithTag("building_2").performClick()
        compose.onNodeWithTag("building_3").performClick()
        compose.onNodeWithTag("building_2").assertIsSelected()
        compose.onNodeWithTag("building_3").assertIsSelected()
        compose.onNodeWithTag("building_all").assertIsNotSelected()
        compose.onNodeWithTag("filter_favorite").performClick()
        compose.onNodeWithTag("filter_outlets").performClick()
        compose.onNodeWithTag("filter_badge").assertTextEquals("4")
        compose.onNodeWithTag("close_filters").performClick()
        compose.onNodeWithTag("filter_dialog").assertDoesNotExist()
        compose.onNodeWithTag("filter_badge").assertTextEquals("4")
        compose.onNodeWithText("已有额外筛选 · 点击修改").assertDoesNotExist()
        compose.onNodeWithTag("campus_swipe_region").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("filter_badge").assertTextEquals("2")
        compose.onNodeWithTag("extra_filters").performClick()
        compose.onNodeWithTag("building_all").assertIsSelected()
        compose.onNodeWithTag("filter_favorite").assertIsSelected()
        compose.onNodeWithTag("filter_outlets").assertIsSelected()
        compose.onNodeWithTag("close_filters").performClick()
        compose.onNodeWithTag("campus_swipe_region").performTouchInput { swipeRight() }
        compose.onNodeWithTag("extra_filters").performClick()
        compose.onNodeWithTag("building_2").assertIsSelected()
        compose.onNodeWithTag("building_3").assertIsSelected()
    }

    @Test fun filtersHaveNoCapacityFieldAndResetAndCloseShareTheFooter() {
        enterDemo()
        compose.onNodeWithTag("extra_filters").performScrollTo().performClick()
        compose.onNodeWithText("最低标注容量").assertDoesNotExist()
        compose.onNodeWithText("重置筛选").assertDoesNotExist()
        compose.onNodeWithTag("reset_filters").assertTextEquals("重置")
        compose.onNodeWithTag("close_filters").assertTextEquals("关闭")
        val reset = compose.onNodeWithTag("reset_filters").fetchSemanticsNode().boundsInRoot
        val close = compose.onNodeWithTag("close_filters").fetchSemanticsNode().boundsInRoot
        val campus = compose.onNodeWithTag("filter_campus").fetchSemanticsNode().boundsInRoot
        assertTrue(reset.right <= close.left)
        assertEquals(reset.center.y, close.center.y, 1f)
        assertTrue(close.top > campus.bottom)
        compose.onNodeWithTag("building_2").performClick()
        compose.onNodeWithTag("filter_favorite").performClick()
        compose.onNodeWithTag("reset_filters").performClick()
        compose.onNodeWithTag("building_all").assertIsSelected()
        compose.onNodeWithTag("filter_favorite").assertIsNotSelected()
        compose.onNodeWithTag("filter_badge").assertDoesNotExist()
        compose.onNodeWithTag("filter_dialog").assertIsDisplayed()
        compose.onNodeWithTag("close_filters").performClick()
        compose.onNodeWithTag("filter_dialog").assertDoesNotExist()
    }

    @Test fun addTermButtonIsToTheRightOfTheNameField() {
        enterDemo()
        compose.onNodeWithTag("extra_filters").performScrollTo().performClick()
        compose.onNodeWithTag("edit_tags").performClick()
        val input = compose.onNodeWithTag("custom_tag_name").fetchSemanticsNode().boundsInRoot
        val add = compose.onNodeWithTag("add_tag").fetchSemanticsNode().boundsInRoot
        assertTrue(add.left >= input.right)
        assertTrue(add.center.y in input.top..input.bottom)
        compose.onNodeWithTag("custom_tag_name").performTextInput("靠窗")
        compose.onNodeWithTag("close_filters").assertIsDisplayed()
        compose.onNodeWithTag("add_tag").performClick()
        compose.onNodeWithTag("filter_tag_靠窗").assertExists()
    }

    @Test fun emptyTimeSelectionStillAllowsExtraFiltersAndReset() {
        enterDemo()
        compose.onNodeWithText("清空").performScrollTo().performClick()
        compose.onNodeWithTag("extra_filters").performScrollTo().performClick()
        compose.onNodeWithTag("filter_favorite").performClick()
        compose.onNodeWithTag("close_filters").performClick()
        compose.onNodeWithText("没有符合条件的教室").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("清除额外筛选").performScrollTo().performClick()
        compose.onNodeWithTag("room_list").performScrollToNode(hasText("2-201"))
        compose.onNodeWithText("2-201").assertIsDisplayed()
        compose.onNodeWithText("请选择自习时段").assertDoesNotExist()
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

    @Test fun compactGridAlignsTheSearchFilterAndSixTimeMarkers() {
        enterDemo()
        val first = compose.onNodeWithTag("slot_1").fetchSemanticsNode().boundsInRoot
        val sixth = compose.onNodeWithTag("slot_6").fetchSemanticsNode().boundsInRoot
        val seventh = compose.onNodeWithTag("slot_7").fetchSemanticsNode().boundsInRoot
        val tenth = compose.onNodeWithTag("slot_10").fetchSemanticsNode().boundsInRoot
        val eleventh = compose.onNodeWithTag("slot_11").fetchSemanticsNode().boundsInRoot
        val twelfth = compose.onNodeWithTag("slot_12").fetchSemanticsNode().boundsInRoot
        val thirteenth = compose.onNodeWithTag("slot_13").fetchSemanticsNode().boundsInRoot
        val fourteenth = compose.onNodeWithTag("slot_14").fetchSemanticsNode().boundsInRoot
        val search = compose.onNodeWithTag("room_search_frame").fetchSemanticsNode().boundsInRoot
        val filter = compose.onNodeWithTag("extra_filters").fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, sixth.top, 1f)
        assertEquals(first.left, seventh.left, 1f)
        assertEquals(first.left, thirteenth.left, 1f)
        assertEquals(tenth.left, search.left, 1f)
        assertEquals(eleventh.right, search.right, 1f)
        assertEquals(twelfth.left, filter.left, 1f)
        assertEquals(twelfth.right, filter.right, 1f)
        assertEquals(fourteenth.center.y, search.center.y, 1f)
        assertEquals(fourteenth.center.y, filter.center.y, 1f)
        assertTrue(first.width + 1f >= first.height)
        // The Pager keeps the other campus precomposed. Ignore that clipped face
        // when inspecting the unmerged tree; it is hidden from normal semantics.
        val face = compose.onAllNodesWithTag("slot_face_1", useUnmergedTree = true).fetchSemanticsNodes()
            .single { it.boundsInRoot.width > 0f && first.contains(it.boundsInRoot.center) }.boundsInRoot
        assertEquals(first.height * .75f, face.height, 1f)
        mapOf(1 to "8:00", 6 to "13:45", 7 to "13:50", 12 to "19:15", 13 to "19:20", 14 to "20:55")
            .forEach { (number, label) -> compose.onNodeWithTag("time_marker_$number").assertTextEquals(label) }
        val start = compose.onNodeWithTag("time_marker_1").fetchSemanticsNode().boundsInRoot
        val end = compose.onNodeWithTag("time_marker_14").fetchSemanticsNode().boundsInRoot
        assertTrue(start.bottom <= first.top)
        assertTrue(end.top >= fourteenth.bottom)
        assertEquals(fourteenth.right, end.right, 1f)
        compose.onNodeWithText("搜索教室或备注").assertDoesNotExist()
        compose.onNodeWithText("只确认所选教学节次空闲，课间与午休不作保证。").assertDoesNotExist()
    }

    @Test fun editingSearchAndOpeningFiltersDoesNotPaintPeriods() {
        enterDemo()
        compose.onNodeWithTag("room_search").performClick().performTextInput("201")
        compose.onNodeWithTag("slot_1").assertIsSelected()
        compose.onNodeWithTag("slot_2").assertIsSelected()
        compose.onNodeWithTag("slot_13").assertIsNotSelected()
        compose.onNodeWithTag("extra_filters").performClick()
        compose.onNodeWithTag("filter_dialog").assertIsDisplayed()
        compose.onNodeWithTag("close_filters").performClick()
        compose.onNodeWithTag("slot_1").assertIsSelected()
        compose.onNodeWithTag("slot_2").assertIsSelected()
        compose.onNodeWithTag("slot_14").assertIsNotSelected()
    }

    @Test fun roomDetailsUpdateMarksAndNotesWithoutSavingOrDismissing() {
        enterDemo()
        compose.onNodeWithTag("room_list").performScrollToNode(hasText("2-201"))
        compose.onNodeWithText("2-201").performClick()
        compose.onNodeWithTag("save_profile").assertDoesNotExist()
        compose.onNodeWithText("保存个人标记").assertDoesNotExist()
        compose.onNodeWithText("标签仅依据个人经验；未勾选表示未标记，不代表没有。").assertDoesNotExist()
        compose.onNodeWithText("标注容量 180", substring = true).assertIsDisplayed()
        compose.onNodeWithText("（非余座）", substring = true).assertDoesNotExist()
        compose.onNodeWithText("主题色：课表空闲；灰色：未列为空闲。课间与午休不作保证。").assertDoesNotExist()
        compose.onNodeWithText("已收藏").performScrollTo().performClick()
        compose.onNodeWithText("有插座").performScrollTo().performClick()
        compose.onNodeWithTag("room_note").performScrollTo().performTextInput("仅备注关键词")
        compose.onNodeWithTag("room_detail").assertExists()
        compose.onNodeWithTag("close_room_detail").performScrollTo().performClick()
        compose.onNodeWithContentDescription("取消收藏 2-201").performScrollTo().assertExists()
        compose.onNodeWithTag("room_list").performScrollToNode(hasText("2-201"))
        compose.onNodeWithText("2-201").performClick()
        compose.onNodeWithTag("room_note").performScrollTo().assertTextContains("仅备注关键词")
        compose.onNodeWithText("有插座").performScrollTo().assertHasClickAction()
        compose.onNodeWithTag("close_room_detail").performScrollTo().performClick()
        compose.onNodeWithTag("room_search").performScrollTo().performTextInput("仅备注关键词")
        compose.onNodeWithTag("room_search").performImeAction()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("没有符合条件的教室").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("没有符合条件的教室").performScrollTo().assertIsDisplayed()
    }

    @Test fun customTermCanBeCreatedMarkedAndUsedToFilter() {
        enterDemo()
        compose.onNodeWithTag("extra_filters").performScrollTo().performClick()
        compose.onNodeWithTag("edit_tags").performScrollTo().performClick()
        compose.onNodeWithTag("custom_tag_name").performScrollTo().performTextInput("靠窗")
        compose.onNodeWithTag("add_tag").performScrollTo().performClick()
        compose.onNodeWithTag("filter_tag_靠窗").performScrollTo().assertExists()
        compose.onNodeWithTag("close_filters").performClick()
        compose.onNodeWithTag("room_list").performScrollToNode(hasText("2-201"))
        compose.onNodeWithText("2-201").performClick()
        compose.onNodeWithText("靠窗").performScrollTo().performClick()
        compose.onNodeWithTag("save_profile").assertDoesNotExist()
        compose.onNodeWithTag("close_room_detail").performScrollTo().performClick()
        compose.onNodeWithTag("room_list").performScrollToNode(hasTestTag("extra_filters"))
        compose.onNodeWithTag("extra_filters").performClick()
        compose.onNodeWithTag("filter_tag_靠窗").performScrollTo().performClick()
        compose.onNodeWithTag("close_filters").performClick()
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
        // The offscreen, precomposed page is hidden from accessibility/test queries.
        assertTrue(moving.isNotEmpty())
        assertTrue(moving.any {
            val bounds = it.boundsInRoot
            kotlin.math.abs(bounds.left - initialHeader.left) > 1f || bounds.width < initialHeader.width * .9f
        })
        compose.mainClock.advanceTimeBy(500)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("campus_label").assertTextEquals("沙河")
    }

    @Test fun termDeletionRequiresConfirmationAndFavoriteCannotBeDeleted() {
        enterDemo()
        compose.onNodeWithTag("extra_filters").performScrollTo().performClick()
        compose.onNodeWithTag("filter_outlets").performClick()
        compose.onNodeWithTag("edit_tags").performClick()
        compose.onNodeWithContentDescription("删除词条 已收藏").assertDoesNotExist()
        compose.onNodeWithContentDescription("删除词条 有插座").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithTag("filter_outlets").assertExists()
        compose.onNodeWithContentDescription("删除词条 有插座").performClick()
        compose.onNodeWithTag("confirm_delete_tag").performClick()
        compose.onNodeWithTag("filter_outlets").assertDoesNotExist()
        compose.onNodeWithTag("filter_badge").assertDoesNotExist()
    }

    @Test fun editableTermsCanBeDraggedAndAccessibleOrderIsRestoredOnReopen() {
        enterDemo()
        compose.onNodeWithTag("extra_filters").performScrollTo().performClick()
        compose.onNodeWithTag("edit_tags").performClick()
        val source = compose.onNodeWithTag("filter_outlets").fetchSemanticsNode().boundsInRoot
        val target = compose.onNodeWithTag("filter_tag_人少").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("filter_outlets").performTouchInput {
            down(center)
            moveTo(target.center - source.topLeft, delayMillis = 200)
            up()
        }
        compose.onNodeWithTag("close_filters").performClick()
        compose.onNodeWithTag("extra_filters").performClick()
        val outlets = compose.onNodeWithTag("filter_outlets").fetchSemanticsNode().boundsInRoot
        val people = compose.onNodeWithTag("filter_tag_人少").fetchSemanticsNode().boundsInRoot
        assertTrue(outlets.top > people.top || (outlets.top == people.top && outlets.left > people.left))
    }

    @Test fun buildingRowsAndCampusHeadingHaveTheRequestedLayout() {
        enterDemo()
        compose.onNodeWithTag("extra_filters").performScrollTo().performClick()
        compose.onNodeWithTag("filter_campus").assertTextEquals("西土城")
        compose.onNodeWithTag("building_all").assertTextEquals("全部教学楼")
        compose.onNodeWithTag("building_row_1").assertExists()
        compose.onNodeWithTag("building_row_2").assertDoesNotExist()
        compose.onNodeWithTag("close_filters").performClick()
        compose.onNodeWithTag("campus_swipe_region").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("extra_filters").performClick()
        compose.onNodeWithTag("filter_campus").assertTextEquals("沙河")
        compose.onNodeWithTag("building_row_0").assertExists()
        compose.onNodeWithTag("building_row_1").assertDoesNotExist()
    }
}
