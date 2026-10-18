package com.example.youroom

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.youroom.data.DemoData
import com.example.youroom.domain.*
import com.example.youroom.ui.RoomSearchFocusBoundary
import com.example.youroom.ui.SlotPicker
import com.example.youroom.ui.theme.YOURoomTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RoomSearchFocusUiTest {
    @get:Rule val compose = createComposeRule()
    private var filters by mutableStateOf(SearchFilters(setOf(1, 2)))
    private var taps = 0
    private fun render() {
        compose.setContent {
            YOURoomTheme {
                RoomSearchFocusBoundary(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                        SlotPicker(Campus.XITUCHENG, DemoData.snapshot().queryDate, true, filters,
                            DemoData.snapshot().slots, { filters = it })
                        Text("外部按钮", Modifier.fillMaxWidth().height(48.dp).testTag("outside_search")
                            .clickable { taps++ })
                        LazyColumn(Modifier.weight(1f).testTag("outside_list"), state = rememberLazyListState()) {
                            items(50) { Text("教室 $it", Modifier.fillMaxWidth().height(48.dp)) }
                        }
                    }
                }
            }
        }
    }

    @Test fun outsideTapClearsFocusWithoutSwallowingItsClickOrTheQuery() {
        render()
        compose.onNodeWithTag("room_search").performTouchInput { click() }
        compose.onNodeWithTag("room_search").performTextInput("201")
        compose.onNodeWithTag("room_search").assertIsFocused()
        compose.onNodeWithTag("outside_search").performTouchInput { click() }
        compose.onNodeWithTag("room_search").assertIsNotFocused().assertTextEquals("201")
        assertEquals(1, taps)
    }

    @Test fun outsideScrollClearsFocusAndStillScrolls() {
        render()
        compose.onNodeWithTag("room_search").performTouchInput { click() }
        compose.onNodeWithTag("room_search").assertIsFocused()
        compose.onNodeWithTag("outside_list").performTouchInput { swipeUp(durationMillis = 180) }
        compose.onNodeWithTag("room_search").assertIsNotFocused()
        compose.onNodeWithText("教室 0").assertIsNotDisplayed()
    }

    @Test fun typingAndClearInsideDoNotBlurButImeSearchDoes() {
        render()
        compose.onNodeWithTag("room_search").performTouchInput { click() }
        compose.onNodeWithTag("room_search").performTextInput("201")
        compose.onNodeWithTag("room_search").performTextInput("A")
        compose.onNodeWithTag("room_search").assertIsFocused()
        compose.onNodeWithTag("clear_room_search").performTouchInput { click() }
        compose.onNodeWithTag("room_search").assertTextEquals("").assertIsFocused()
        compose.onNodeWithTag("room_search").performImeAction()
        compose.onNodeWithTag("room_search").assertIsNotFocused()
    }

    @Test fun gridSelectionStillWorksAfterOutsideDownClearsFocus() {
        render()
        compose.onNodeWithTag("room_search").performTouchInput { click() }
        compose.onNodeWithTag("slot_6").performTouchInput { click() }
        compose.onNodeWithTag("room_search").assertIsNotFocused()
        compose.onNodeWithTag("slot_6").assertIsSelected()
        assertEquals(setOf(1, 2, 6), filters.selectedSlots)
    }
}
