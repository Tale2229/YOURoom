package com.example.youroom

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.youroom.data.DemoData
import com.example.youroom.domain.*
import com.example.youroom.ui.SlotPicker
import com.example.youroom.ui.timeMarkerLineRange
import com.example.youroom.ui.theme.YouRoomTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.roundToInt

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

    @Test fun timeGuideEndpointsMeetTheSlotFacesAndTextIsTenSp() {
        render()
        for (number in listOf(1, 6, 7, 12, 13, 14)) {
            val target = compose.onNodeWithTag("slot_$number").fetchSemanticsNode().boundsInRoot
            val face = compose.onNodeWithTag("slot_face_$number", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val line = compose.onNodeWithTag("time_marker_line_$number", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val start = number in listOf(1, 7, 13)
            val range = timeMarkerLineRange(start, line.height, target.height.roundToInt(), face.height.roundToInt(),
                target.height / 48f * 2f)
            if (start) assertEquals(face.top, line.top + range.endInclusive, 1f)
            else assertEquals(face.bottom, line.top + range.start, 1f)
            val text = compose.onNodeWithTag("time_marker_label_$number", useUnmergedTree = true)
            val layout = mutableListOf<TextLayoutResult>()
            text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
            assertEquals(10.sp, layout.single().layoutInput.style.fontSize)
            val gap = if (start) face.top - text.fetchSemanticsNode().boundsInRoot.bottom else
                text.fetchSemanticsNode().boundsInRoot.top - face.bottom
            assertTrue("The time label must sit within 4dp of period $number", gap <= target.height / 48f * 4f + 1f)
        }
    }
}
