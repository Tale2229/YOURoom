package com.example.youroom

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.youroom.domain.*
import com.example.youroom.data.DemoData
import com.example.youroom.ui.RoomNoteDialog
import com.example.youroom.ui.SwipeRoomRow
import com.example.youroom.ui.theme.YouRoomTheme
import com.example.youroom.ui.theme.campusColorScheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SwipeRoomRowUiTest {
    @get:Rule val compose = createComposeRule()
    private val room = Classroom("01:2-201", "2-201", "2", 180)
    private var profile by mutableStateOf(room.emptyProfile())
    private var expanded by mutableStateOf(false)
    private var active by mutableStateOf(true)
    private var campus by mutableStateOf(Campus.XITUCHENG)
    private var dark by mutableStateOf(false)
    private var tagClicks = 0
    private var noteClicks = 0

    private fun renderRow() {
        compose.setContent {
            YouRoomTheme(campus = campus, darkTheme = dark) {
                Surface { SwipeRoomRow(RoomMatch(room, profile, setOf(1, 2, 6)), DemoData.snapshot().slots,
                    PersonalTags.defaultOrder, expanded, active, { expanded = it },
                    { profile = profile.copy(favorite = !profile.favorite) }, { tagClicks++ }, { noteClicks++ },
                    { profile = profile.copy(excluded = !profile.excluded) }) }
            }
        }
    }

    private fun reveal() {
        compose.onNodeWithTag("room_row_${room.id}").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("room_actions_${room.id}").assertIsDisplayed()
    }

    @Test fun fourButtonsHaveTheRequestedOrderAndTouchSizeAndOnlyAppearAfterLeftSwipe() {
        renderRow()
        compose.onNodeWithContentDescription("收藏 2-201").assertDoesNotExist()
        compose.onNodeWithText("2-201").performTouchInput { click() }
        compose.onNodeWithTag("room_actions_${room.id}").assertDoesNotExist()
        reveal()
        val actions = (0..3).map { compose.onNodeWithTag("room_action_${room.id}_$it") }
        actions.forEach { it.assertIsDisplayed().assertWidthIsAtLeast(androidx.compose.ui.unit.Dp(40f))
            .assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(48f)) }
        val bounds = actions.map { it.fetchSemanticsNode().boundsInRoot }
        for (index in 0..2) assertTrue(bounds[index].right <= bounds[index + 1].left + 1f)
        compose.onNodeWithContentDescription("修改标签 2-201").performClick()
        compose.onNodeWithContentDescription("修改备注 2-201").performClick()
        assertEquals(1, tagClicks); assertEquals(1, noteClicks)
        compose.onNodeWithContentDescription("收藏 2-201").performClick()
        compose.onNodeWithContentDescription("取消收藏 2-201").assertIsDisplayed()
        compose.onNodeWithContentDescription("排除 2-201").performClick()
        compose.onNodeWithContentDescription("取消排除 2-201").assertIsDisplayed()
        compose.onNodeWithTag("room_excluded_${room.id}", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("room_row_${room.id}").performTouchInput { swipeRight() }
        compose.onNodeWithTag("room_actions_${room.id}").assertDoesNotExist()
    }

    @Test fun compactRailHasFourOrderedLinesAndItsBottomSeparatorMatchesTheRow() {
        renderRow()
        for (note in listOf("", "一行备注")) {
            compose.runOnIdle { profile = profile.copy(note = note) }
            val closed = compose.onNodeWithTag("room_row_${room.id}").fetchSemanticsNode().boundsInRoot
            reveal()
            val lines = listOf("top_thin", "top_thick", "bottom_thick", "bottom_thin").map {
                compose.onNodeWithTag("room_action_line_${room.id}_$it").fetchSemanticsNode().boundsInRoot
            }
            for (index in 0..2) assertTrue(lines[index].bottom < lines[index + 1].top)
            assertEquals(lines[0].height, lines[3].height, 1f)
            assertEquals(lines[1].height, lines[2].height, 1f)
            assertEquals(lines[0].height * 3, lines[1].height, 1.1f)
            val separator = compose.onNodeWithTag("room_divider_${room.id}", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            assertEquals(separator.top, lines.last().top, 1f)
            assertEquals(closed.height, compose.onNodeWithTag("room_row_${room.id}").fetchSemanticsNode().boundsInRoot.height, 1f)
            val actions = (0..3).map { compose.onNodeWithTag("room_action_${room.id}_$it").fetchSemanticsNode().boundsInRoot }
            val width = compose.onNodeWithTag("room_actions_${room.id}").fetchSemanticsNode().boundsInRoot.width
            assertEquals(width / 4, actions[0].width, 1f)
            for (index in 0..2) assertEquals(width / 4, actions[index + 1].center.x - actions[index].center.x, 1f)
            assertTrue(lines[1].bottom < actions.first().top && actions.first().bottom < lines[2].top)
            compose.onNodeWithTag("room_action_${room.id}_1").performTouchInput { click() }
            compose.onNodeWithTag("room_action_${room.id}_2").performTouchInput { click() }
            compose.onNodeWithTag("room_swipe_${room.id}", useUnmergedTree = true).performTouchInput { swipeRight() }
        }
        assertEquals(2, tagClicks); assertEquals(2, noteClicks)
    }

    @Test fun cancelRestoresClosedAndOpenAnchorsWithoutChangingMarks() {
        renderRow()
        compose.onNodeWithTag("room_row_${room.id}").performTouchInput {
            down(centerRight); moveTo(centerLeft, delayMillis = 180); cancel()
        }
        compose.onNodeWithTag("room_actions_${room.id}").assertDoesNotExist()
        assertEquals(room.emptyProfile(), profile)
        reveal()
        compose.onNodeWithTag("room_row_${room.id}").performTouchInput {
            down(centerLeft); moveTo(centerRight, delayMillis = 180); cancel()
        }
        compose.onNodeWithTag("room_actions_${room.id}").assertIsDisplayed()
        assertEquals(room.emptyProfile(), profile)
    }

    @Test fun verticalDragDoesNotRevealAndAnInactivePageClosesItsActions() {
        renderRow()
        compose.onNodeWithTag("room_row_${room.id}").performTouchInput { swipeUp() }
        compose.onNodeWithTag("room_actions_${room.id}").assertDoesNotExist()
        reveal()
        compose.runOnIdle { active = false }
        compose.onNodeWithTag("room_actions_${room.id}").assertDoesNotExist()
        assertEquals(room.emptyProfile(), profile)
    }

    @Test fun accessibilityCanRevealActionsAndTappingTheFaceClosesThem() {
        renderRow()
        val action = compose.onNodeWithTag("room_row_${room.id}").fetchSemanticsNode()
            .config[SemanticsActions.CustomActions].single()
        assertEquals("展开教室操作", action.label)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { assertTrue(action.action()) }
        compose.mainClock.advanceTimeBy(48)
        compose.onNodeWithTag("room_action_${room.id}_0", useUnmergedTree = true).assertIsNotEnabled()
        compose.mainClock.autoAdvance = true
        compose.onNodeWithContentDescription("收藏 2-201").assertIsDisplayed()
        compose.onNodeWithTag("room_row_${room.id}").performTouchInput { click(centerLeft) }
        compose.onNodeWithTag("room_actions_${room.id}").assertDoesNotExist()
    }

    @Test fun favoriteUsesCampusBackgroundHighlightWithoutAPermanentStarInBothThemes() {
        renderRow()
        for (target in Campus.entries) for (night in listOf(false, true)) {
            compose.runOnIdle { campus = target; dark = night; profile = room.emptyProfile() }
            val colors = campusColorScheme(target, night)
            fun background(): Color = compose.onNodeWithTag("room_face_${room.id}")
                .captureToImage().toPixelMap()[3, 3]
            assertEquals(colors.background, background())
            compose.runOnIdle { profile = profile.copy(favorite = true) }
            assertEquals(colors.primaryContainer, background())
            assertNotEquals(colors.background, background())
            if (!night) assertTrue(background().luminance() < colors.background.luminance())
            compose.onNodeWithContentDescription("取消收藏 2-201").assertDoesNotExist()
        }
    }

    @Test fun notePopupPreservesSpacesLimitsLengthAndClearsOnlyTheNoteImmediately() {
        profile = room.emptyProfile().copy(favorite = true, excluded = true, tags = setOf("靠窗"))
        val original = profile
        compose.setContent {
            YouRoomTheme { RoomNoteDialog(RoomMatch(room, profile, emptySet()), {}, { profile = it }) }
        }
        compose.onNodeWithTag("room_note").performTextInput("  保留空格  ")
        compose.runOnIdle { assertEquals("  保留空格  ", profile.note) }
        compose.onNodeWithTag("room_note").performTextReplacement("a".repeat(520))
        compose.runOnIdle { assertEquals(500, profile.note.length) }
        compose.onNodeWithTag("clear_room_note").performClick()
        compose.runOnIdle { assertEquals(original, profile) }
        compose.onNodeWithTag("room_note_dialog").assertIsDisplayed()
        compose.onNodeWithTag("save_profile").assertDoesNotExist()
    }
}
