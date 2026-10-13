package com.example.youroom

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.youroom.domain.*
import com.example.youroom.data.DemoData
import com.example.youroom.ui.RoomNoteDialog
import com.example.youroom.ui.SwipeRoomRow
import com.example.youroom.ui.FavoriteMotionTiming
import androidx.compose.ui.unit.dp
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
    private var interactionEnabled by mutableStateOf(true)
    private var favoriteClosing by mutableStateOf(false)
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
                    { profile = profile.copy(excluded = !profile.excluded) },
                    interactionEnabled = active && interactionEnabled, favoriteClosing = favoriteClosing) }
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
        compose.onNodeWithTag("room_actions_${room.id}").assertDoesNotExist()
        reveal()
        compose.onNodeWithContentDescription("取消收藏 2-201").assertIsDisplayed()
        compose.onNodeWithContentDescription("排除 2-201").performClick()
        compose.onNodeWithContentDescription("取消排除 2-201").assertIsDisplayed()
        compose.onNodeWithTag("room_excluded_${room.id}", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("room_row_${room.id}").performTouchInput { swipeRight() }
        compose.onNodeWithTag("room_actions_${room.id}").assertDoesNotExist()
    }

    @Test fun compactRailHasOnlyTwoThinLinesAndEqualSideGuttersWithoutIncreasingRowHeight() {
        renderRow()
        for (note in listOf("", "一行备注")) {
            compose.runOnIdle { profile = profile.copy(note = note) }
            val closed = compose.onNodeWithTag("room_row_${room.id}").fetchSemanticsNode().boundsInRoot
            reveal()
            val lines = listOf("top_thin", "bottom_thin").map {
                compose.onNodeWithTag("room_action_line_${room.id}_$it").fetchSemanticsNode().boundsInRoot
            }
            compose.onNodeWithTag("room_action_line_${room.id}_top_thick").assertDoesNotExist()
            compose.onNodeWithTag("room_action_line_${room.id}_bottom_thick").assertDoesNotExist()
            assertEquals(lines[0].height, lines[1].height, 1f)
            val separator = compose.onNodeWithTag("room_divider_${room.id}", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            assertEquals(separator.top, lines.last().top, 1f)
            assertEquals(closed.height, compose.onNodeWithTag("room_row_${room.id}").fetchSemanticsNode().boundsInRoot.height, 1f)
            val actions = (0..3).map { compose.onNodeWithTag("room_action_${room.id}_$it").fetchSemanticsNode().boundsInRoot }
            val rail = compose.onNodeWithTag("room_actions_${room.id}").fetchSemanticsNode().boundsInRoot
            val leftGutter = actions.first().left - rail.left
            val rightGutter = rail.right - actions.last().right
            assertTrue(leftGutter > 1f)
            assertEquals(leftGutter, rightGutter, 1f)
            // Density rounding of four 40dp targets plus two 12dp gutters can
            // differ by up to two pixels from the rounded 184dp rail width.
            assertEquals(actions.first().width * 12 / 40, leftGutter, 2f)
            for (index in 0..2) assertEquals(actions[0].width, actions[index + 1].center.x - actions[index].center.x, 2f)
            assertTrue(lines[0].bottom < actions.first().top && actions.first().bottom < lines[1].top)
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

    @Test fun actionRailUsesKhakiWestAndGreenShaheSurfacesInBothModes() {
        renderRow()
        for (target in Campus.entries) for (night in listOf(false, true)) {
            compose.runOnIdle { campus = target; dark = night }
            reveal()
            val pixels = compose.onNodeWithTag("room_actions_${room.id}").captureToImage().toPixelMap()
            assertEquals(campusColorScheme(target, night).surfaceContainer, pixels[3, pixels.height / 2])
            compose.onNodeWithTag("room_swipe_${room.id}", useUnmergedTree = true).performTouchInput { swipeRight() }
        }
    }

    @Test fun favoriteCornerUsesSelectedSlotColorAndKeepsTheFaceUntintedInBothModes() {
        profile = room.emptyProfile().copy(favorite = true)
        renderRow()
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark = night }
            val colors = campusColorScheme(Campus.XITUCHENG, night)
            val favorite = compose.onNodeWithTag("room_face_${room.id}").captureToImage().toPixelMap()[3, 3]
            assertEquals(colors.background, favorite)
            val corner = compose.onNodeWithTag("room_favorite_${room.id}", useUnmergedTree = true).captureToImage().toPixelMap()
            assertEquals(colors.primary, corner[corner.width - 3, 2])
            reveal()
            val pixels = compose.onNodeWithTag("room_actions_${room.id}").captureToImage().toPixelMap()
            val rail = pixels[3, pixels.height / 2]
            assertEquals(colors.surfaceContainer, rail)
            assertNotEquals(favorite, rail)
            compose.onNodeWithTag("room_swipe_${room.id}", useUnmergedTree = true).performTouchInput { swipeRight() }
        }
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

    @Test fun favoritesUseRightAngledTopRightCornersWithoutChangingBackgroundOrHeightInBothThemes() {
        renderRow()
        for (target in Campus.entries) for (night in listOf(false, true)) {
            compose.runOnIdle { campus = target; dark = night; profile = room.emptyProfile() }
            val colors = campusColorScheme(target, night)
            fun background(): Color = compose.onNodeWithTag("room_face_${room.id}")
                .captureToImage().toPixelMap()[3, 3]
            assertEquals(colors.background, background())
            val before = compose.onNodeWithTag("room_row_${room.id}").fetchSemanticsNode().boundsInRoot
            compose.onNodeWithTag("room_favorite_${room.id}", useUnmergedTree = true).assertDoesNotExist()
            compose.runOnIdle { profile = profile.copy(favorite = true) }
            assertEquals(colors.background, background())
            assertEquals(before, compose.onNodeWithTag("room_row_${room.id}").fetchSemanticsNode().boundsInRoot)
            val face = compose.onNodeWithTag("room_face_${room.id}").fetchSemanticsNode().boundsInRoot
            val marker = compose.onNodeWithTag("room_favorite_${room.id}", useUnmergedTree = true)
            marker.assertWidthIsEqualTo(22.dp).assertHeightIsEqualTo(22.dp)
            val bounds = marker.fetchSemanticsNode().boundsInRoot
            val lastPeriod = compose.onNodeWithTag("room_period_${room.id}_14", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            assertEquals(face.top, bounds.top, 1f); assertEquals(lastPeriod.right, bounds.right, 1f)
            assertEquals(bounds.width, bounds.height, 1f)
            val pixels = marker.captureToImage().toPixelMap()
            assertEquals(colors.primary, pixels[pixels.width - 3, 2])
            assertEquals(colors.background, pixels[2, pixels.height - 3])
            compose.onNodeWithContentDescription("取消收藏 2-201").assertDoesNotExist()
        }
    }

    @Test fun favoriteAndUnfavoriteEachCloseTheRailAndPreserveOtherMarks() {
        profile = room.emptyProfile().copy(excluded = true, tags = setOf("靠窗"), note = "窗边")
        renderRow()
        for (target in Campus.entries) {
            compose.runOnIdle { campus = target }
            for (favorite in listOf(true, false)) {
                reveal()
                compose.onNodeWithTag("room_action_${room.id}_0").performTouchInput { click() }
                compose.onNodeWithTag("room_actions_${room.id}").assertDoesNotExist()
                assertEquals(favorite, profile.favorite)
                assertTrue(profile.excluded); assertEquals(setOf("靠窗"), profile.tags); assertEquals("窗边", profile.note)
            }
        }
    }

    @Test fun favoriteBusyStateAnimatesTheRailClosedRatherThanSnappingAnInactiveRow() {
        renderRow(); reveal()
        // boundsInRoot is clipped by the row: it cannot measure the negative
        // foreground offset. positionInRoot reports the actual moving origin.
        val start = compose.onNodeWithTag("room_face_${room.id}").fetchSemanticsNode().positionInRoot.x
        val row = compose.onNodeWithTag("room_row_${room.id}").fetchSemanticsNode().positionInRoot.x
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { favoriteClosing = true; interactionEnabled = false; expanded = false }
        compose.mainClock.advanceTimeBy(96)
        val during = compose.onNodeWithTag("room_face_${room.id}").fetchSemanticsNode().positionInRoot.x
        assertTrue("Foreground should travel gradually: $start -> $during -> $row", during > start && during < row)
        compose.onNodeWithTag("room_actions_${room.id}", useUnmergedTree = true).assertExists()
        compose.mainClock.advanceTimeBy(FavoriteMotionTiming.Close + 64L)
        assertEquals(row, compose.onNodeWithTag("room_face_${room.id}").fetchSemanticsNode().positionInRoot.x, 1f)
        compose.onNodeWithTag("room_actions_${room.id}").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }

    @Test fun fastOpenAndCloseWithOppositeReleaseRecoilDoNotBounceToTheOldAnchor() {
        renderRow()
        for (duration in listOf(48L, 96L, 180L)) {
            compose.onNodeWithTag("room_row_${room.id}").performTouchInput {
                down(centerRight); moveTo(centerLeft, delayMillis = duration)
                moveBy(androidx.compose.ui.geometry.Offset(20f, 0f), delayMillis = 16); up()
            }
            (0..3).forEach { compose.onNodeWithTag("room_action_${room.id}_$it").assertIsDisplayed() }
            compose.onNodeWithTag("room_row_${room.id}").performTouchInput {
                down(centerLeft); moveTo(centerRight, delayMillis = duration)
                moveBy(androidx.compose.ui.geometry.Offset(-20f, 0f), delayMillis = 16); up()
            }
            compose.onNodeWithTag("room_actions_${room.id}").assertDoesNotExist()
        }
        assertEquals(room.emptyProfile(), profile)
    }

    @Test fun aNewDragDuringSettlementCancelsTheOldAnimationAndCancelRestoresItsIntent() {
        renderRow()
        fun openAccessibly() {
            val action = compose.onNodeWithTag("room_row_${room.id}").fetchSemanticsNode()
                .config[SemanticsActions.CustomActions].single()
            compose.runOnIdle { assertTrue(action.action()) }
        }
        compose.mainClock.autoAdvance = false
        openAccessibly(); compose.mainClock.advanceTimeBy(48)
        compose.onNodeWithTag("room_row_${room.id}").performTouchInput { swipeRight(durationMillis = 64) }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("room_actions_${room.id}").assertDoesNotExist()
        openAccessibly(); compose.mainClock.advanceTimeBy(48)
        compose.onNodeWithTag("room_row_${room.id}").performTouchInput {
            down(centerLeft); moveTo(centerRight, delayMillis = 32); cancel()
        }
        compose.mainClock.advanceTimeBy(400)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("room_actions_${room.id}").assertIsDisplayed()
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
