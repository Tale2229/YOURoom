package com.example.youroom

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.youroom.data.DemoData
import com.example.youroom.domain.*
import com.example.youroom.ui.SlotPicker
import com.example.youroom.ui.theme.YouRoomTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SlotPickerUiTest {
    @get:Rule val compose = createComposeRule()
    private val snapshot = DemoData.snapshot()
    private val changes = mutableListOf<SearchFilters>()
    private var filters by mutableStateOf(SearchFilters(setOf(6)))
    private var campus by mutableStateOf(Campus.XITUCHENG)

    private fun render() {
        compose.setContent {
            YouRoomTheme {
                Column(Modifier.fillMaxWidth().padding(6.dp)) {
                    SlotPicker(campus, snapshot.queryDate, true, filters, snapshot.slots,
                        { filters = it; changes += it })
                    Text(filters.selectedSlots.sorted().joinToString(), Modifier.testTag("committed_slots"))
                }
            }
        }
    }

    private fun point(number: Int): Offset {
        val grid = compose.onNodeWithTag("slot_grid").fetchSemanticsNode().boundsInRoot
        return compose.onNodeWithTag("slot_$number").fetchSemanticsNode().boundsInRoot.center - grid.topLeft
    }

    @Test fun dragPreviewsEveryCellButCommitsOnlyOnceOnRelease() {
        render()
        val first = point(1)
        val third = point(3)
        val fifth = point(5)
        compose.onNodeWithTag("slot_grid").performTouchInput { down(first); moveTo(third) }
        compose.onNodeWithTag("slot_1").assertIsSelected()
        compose.onNodeWithTag("slot_2").assertIsSelected()
        compose.onNodeWithTag("slot_3").assertIsSelected()
        compose.onNodeWithTag("committed_slots").assertTextEquals("6")
        assertTrue(changes.isEmpty())
        compose.onNodeWithTag("slot_grid").performTouchInput { moveTo(fifth); moveTo(third) }
        compose.onNodeWithTag("slot_5").assertIsSelected()
        assertTrue(changes.isEmpty())
        compose.onNodeWithTag("slot_grid").performTouchInput { up() }
        assertEquals(1, changes.size)
        assertEquals((1..6).toSet(), changes.single().selectedSlots)
        compose.onNodeWithTag("committed_slots").assertTextEquals("1, 2, 3, 4, 5, 6")
    }

    @Test fun cancelDiscardsThePreviewWithoutAnySearchChange() {
        render()
        val first = point(1)
        val third = point(3)
        compose.onNodeWithTag("slot_grid").performTouchInput { down(first); moveTo(third) }
        compose.onNodeWithTag("slot_3").assertIsSelected()
        compose.onNodeWithTag("slot_grid").performTouchInput { cancel() }
        compose.onNodeWithTag("slot_1").assertIsNotSelected()
        compose.onNodeWithTag("slot_2").assertIsNotSelected()
        compose.onNodeWithTag("slot_3").assertIsNotSelected()
        compose.onNodeWithTag("slot_6").assertIsSelected()
        assertTrue(changes.isEmpty())
    }

    @Test fun dragRemovalCommitsOnceAndSemanticClicksStillCommitImmediately() {
        filters = SearchFilters(setOf(1, 2, 3, 6))
        render()
        val first = point(1)
        val third = point(3)
        compose.onNodeWithTag("slot_grid").performTouchInput { down(first); moveTo(third) }
        compose.onNodeWithTag("slot_1").assertIsNotSelected()
        compose.onNodeWithTag("committed_slots").assertTextEquals("1, 2, 3, 6")
        assertTrue(changes.isEmpty())
        compose.onNodeWithTag("slot_grid").performTouchInput { up() }
        assertEquals(1, changes.size)
        assertEquals(setOf(6), filters.selectedSlots)
        compose.onNodeWithTag("slot_1").performClick()
        assertEquals(2, changes.size)
        assertEquals(setOf(1, 6), filters.selectedSlots)
        compose.onNodeWithTag("slot_grid").performTouchInput { down(first); up() }
        assertEquals(3, changes.size)
        assertEquals(setOf(6), filters.selectedSlots)
    }

    @Test fun changingContextDiscardsAHeldPreviewWithoutCommittingIntoTheNewCampus() {
        render()
        val first = point(1)
        val third = point(3)
        compose.onNodeWithTag("slot_grid").performTouchInput { down(first); moveTo(third) }
        compose.runOnIdle { campus = Campus.SHAHE }
        compose.onNodeWithTag("slot_1").assertIsNotSelected()
        compose.onNodeWithTag("slot_6").assertIsSelected()
        compose.onNodeWithTag("slot_grid").performTouchInput { up() }
        assertTrue(changes.isEmpty())
        assertEquals(setOf(6), filters.selectedSlots)
    }

    @Test fun numericFacesHaveSquareCornersAndTimeMarkersAreAbsent() {
        render()
        for (number in listOf(1, 6, 7, 12, 13, 14)) {
            compose.onNodeWithTag("time_marker_$number").assertDoesNotExist()
            compose.onNodeWithTag("time_marker_line_$number", useUnmergedTree = true).assertDoesNotExist()
            assertSquareCorners(compose.onNodeWithTag("slot_face_$number", useUnmergedTree = true))
        }
    }

    @Test fun inlineClearOnlyChangesTheQueryAndDisappearsWhenEmpty() {
        filters = filters.copy(query = "201", buildings = setOf("2"), favoritesOnly = true, tags = setOf("人少"))
        val original = filters
        render()
        val frame = compose.onNodeWithTag("room_search_frame").fetchSemanticsNode().boundsInRoot
        val clear = compose.onNodeWithTag("clear_room_search")
        val button = clear.fetchSemanticsNode().boundsInRoot
        assertTrue(frame.contains(button.center))
        assertTrue(button.center.x > frame.center.x)
        clear.assertContentDescriptionEquals("清空教室搜索").performTouchInput { click() }
        assertEquals(listOf(original.copy(query = "")), changes)
        assertEquals(original.copy(query = ""), filters)
        compose.onNodeWithTag("clear_room_search").assertDoesNotExist()
        compose.onNodeWithTag("slot_6").assertIsSelected()
    }
}
