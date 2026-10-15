package com.example.youroom

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.youroom.domain.*
import com.example.youroom.data.DemoData
import com.example.youroom.ui.RoomNoteDialog
import com.example.youroom.ui.SwipeRoomRow
import com.example.youroom.ui.FavoriteMotionTiming
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
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
    private var fontScale by mutableFloatStateOf(1f)
    private var visible by mutableStateOf(true)
    private var pending = false
    private var tagClicks = 0
    private var noteClicks = 0
    private var editorOpen by mutableStateOf(false)

    private fun renderRow() {
        compose.setContent {
            YouRoomTheme(campus = campus, darkTheme = dark) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                Surface { if (visible) SwipeRoomRow(RoomMatch(room, profile, setOf(1, 2, 6)), DemoData.snapshot().slots,
                    PersonalTags.defaultOrder, expanded, active, { expanded = it },
                    { profile = profile.copy(favorite = !profile.favorite) }, { tagClicks++ }, { noteClicks++ },
                    { profile = profile.copy(excluded = !profile.excluded) },
                    interactionEnabled = active && interactionEnabled, favoriteClosing = favoriteClosing,
                    onActionPending = { pending = it }, editorOpen = editorOpen) }
                }
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
        compose.onNodeWithTag("room_actions_${room.id}").assertDoesNotExist()
        reveal()
        compose.onNodeWithContentDescription("取消排除 2-201").assertIsDisplayed()
        compose.onNodeWithTag("room_excluded_${room.id}", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("room_row_${room.id}").performTouchInput { swipeRight() }
        compose.onNodeWithTag("room_actions_${room.id}").assertDoesNotExist()
    }

    @Test fun exclusionAndUnexclusionBothCloseTheRailAndLeaveTheRoomFaceInPlace() {
        renderRow()
        for (target in Campus.entries) for (night in listOf(false, true)) {
            compose.runOnIdle { campus = target; dark = night }
            reveal()
            compose.onNodeWithContentDescription("排除 2-201").performClick()
            compose.onNodeWithTag("room_actions_${room.id}").assertDoesNotExist()
            compose.onNodeWithTag("room_excluded_${room.id}", useUnmergedTree = true).assertExists()
            assertEquals(compose.onNodeWithTag("room_row_${room.id}").fetchSemanticsNode().positionInRoot.x,
                compose.onNodeWithTag("room_face_${room.id}").fetchSemanticsNode().positionInRoot.x, 1f)
            reveal()
            compose.onNodeWithContentDescription("取消排除 2-201").performClick()
            compose.onNodeWithTag("room_actions_${room.id}").assertDoesNotExist()
            compose.onNodeWithTag("room_excluded_${room.id}", useUnmergedTree = true).assertDoesNotExist()
            assertFalse(profile.excluded)
        }
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
            val strip = compose.onNodeWithTag("room_period_strip_${room.id}").fetchSemanticsNode().boundsInRoot
            assertTrue(lines.last().bottom < strip.top)
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
            val density = actions.first().width / 40f
            assertEquals(2f * density, actions.first().top - lines[0].bottom, 1f)
            assertEquals(2f * density, lines[1].top - actions.first().bottom, 1f)
            assertEquals(rail.center.y, (lines[0].top + lines[1].bottom) / 2f, 1f)
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
        compose.runOnIdle { assertEquals(20, profile.note.length) }
        compose.onNodeWithTag("room_note_count", useUnmergedTree = true).assertTextEquals("20/20")
        compose.onNodeWithTag("room_note").performTextReplacement("\uD83D\uDE00".repeat(21))
        compose.runOnIdle { assertEquals(20, RoomNotes.length(profile.note)); assertEquals(40, profile.note.length) }
        compose.onNodeWithTag("clear_room_note").performClick()
        compose.runOnIdle { assertEquals(original, profile) }
        compose.onNodeWithTag("room_note_dialog").assertIsDisplayed()
        compose.onNodeWithTag("save_profile").assertDoesNotExist()
    }

    @Test fun tagAndNoteIconsKeepTheirSizeAndSilhouetteWhileTheirInteriorsBecomeFilled() {
        renderRow(); reveal()
        for (target in Campus.entries) for (night in listOf(false, true)) {
            compose.runOnIdle { campus = target; dark = night; profile = room.emptyProfile() }
            val colors = campusColorScheme(target, night)
            val icons = (1..2).map { compose.onNodeWithTag("room_action_icon_${room.id}_$it", useUnmergedTree = true) }
            val empty = icons.map { it.captureToImage().toPixelMap() }
            val sizes = icons.map { it.fetchSemanticsNode().size }
            compose.runOnIdle { profile = profile.copy(tags = setOf("靠窗"), note = "窗边") }
            compose.onNodeWithTag("room_action_${room.id}_1").assert(
                SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "已有标签"))
            compose.onNodeWithTag("room_action_${room.id}_2").assert(
                SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "已有备注"))
            icons.forEachIndexed { i, icon ->
                assertEquals(sizes[i], icon.fetchSemanticsNode().size)
                val filled = icon.captureToImage().toPixelMap()
                fun ink(pixels: androidx.compose.ui.graphics.PixelMap) = (0 until pixels.width).sumOf { x ->
                    (0 until pixels.height).count { y -> pixels[x, y] == colors.primary } }
                assertTrue("Filled icon $i must be darker", ink(filled) > ink(empty[i]))
                for (x in 0 until filled.width) for (y in 0 until filled.height) {
                    // Same icon family: allow a few quantization levels at antialiased edges.
                    if (empty[i][x, y] == colors.primary) {
                        val actual = filled[x, y]
                        assertTrue(kotlin.math.abs(actual.red - colors.primary.red) < .025f &&
                            kotlin.math.abs(actual.green - colors.primary.green) < .025f &&
                            kotlin.math.abs(actual.blue - colors.primary.blue) < .025f)
                    }
                }
            }
            for (tagged in listOf(profile.copy(tags = emptySet(), outlets = true),
                profile.copy(tags = emptySet(), quiet = true))) {
                compose.runOnIdle { profile = tagged }
                compose.onNodeWithTag("room_action_${room.id}_1").assert(
                    SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "已有标签"))
            }
            compose.runOnIdle { profile = room.emptyProfile().copy(favorite = true, excluded = true, note = "  ") }
            compose.onNodeWithTag("room_action_${room.id}_1").assert(
                SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "无标签"))
            compose.onNodeWithTag("room_action_${room.id}_2").assert(
                SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "无备注"))
        }
    }

    @Test fun favoriteAndExclusionCloseImmediatelyButWaitForTheWaveBeforeChangingTheRoom() {
        renderRow()
        compose.mainClock.autoAdvance = false
        for (favorite in listOf(true, false)) {
            val open = compose.onNodeWithTag("room_row_${room.id}").fetchSemanticsNode()
                .config[SemanticsActions.CustomActions].single()
            compose.runOnIdle { assertTrue(open.action()) }
            compose.mainClock.advanceTimeBy(400)
            val old = profile
            compose.onNodeWithTag("room_action_${room.id}_${if (favorite) 0 else 3}").performClick()
            compose.runOnIdle { assertEquals(old, profile); assertFalse(expanded) }
            compose.mainClock.advanceTimeBy(180)
            compose.onNodeWithTag("room_actions_${room.id}").assertDoesNotExist()
            compose.mainClock.advanceTimeBy(700)
            compose.runOnIdle { if (favorite) assertTrue(profile.favorite) else assertTrue(profile.excluded) }
            val period = compose.onNodeWithTag("room_period_${room.id}_6", useUnmergedTree = true)
            assertTrue(period.fetchSemanticsNode().config.contains(SemanticsActions.OnLongClick))
        }
        compose.mainClock.autoAdvance = true
    }

    @Test fun openingIsInspectableButAnAcceptedRightSwipeStartsTheClosingWave() {
        renderRow(); reveal()
        val period = compose.onNodeWithTag("room_period_${room.id}_6", useUnmergedTree = true)
        assertTrue(period.fetchSemanticsNode().config.contains(SemanticsActions.OnLongClick))
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("room_swipe_${room.id}").performTouchInput { swipeRight(durationMillis=96) }
        compose.mainClock.advanceTimeBy(64)
        assertFalse(period.fetchSemanticsNode().config.contains(SemanticsActions.OnLongClick))
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("room_actions_${room.id}").assertDoesNotExist()
        assertTrue(period.fetchSemanticsNode().config.contains(SemanticsActions.OnLongClick))
        compose.mainClock.autoAdvance = true
    }

    @Test fun editorDismissalClosesWithoutAWaveAndOnlyThenRevealsTheSavedMetadata() {
        renderRow(); reveal()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { editorOpen=true; profile=profile.copy(outlets=true,note="靠窗") }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("room_note_text_${room.id}",true).assertTextEquals(" ")
        compose.onNodeWithTag("room_subtitle_${room.id}",true).assertTextEquals("教学楼 2 · 容量 180")
        compose.runOnIdle { assertEquals("靠窗",profile.note); editorOpen=false; expanded=false }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("room_note_text_${room.id}",true).assertTextEquals(" ")
        val period=compose.onNodeWithTag("room_period_${room.id}_6",true)
        assertTrue(period.fetchSemanticsNode().config.contains(SemanticsActions.OnLongClick))
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("room_actions_${room.id}").assertDoesNotExist()
        compose.onNodeWithTag("room_note_text_${room.id}",true).assertTextEquals("靠窗")
        compose.onNodeWithTag("room_subtitle_${room.id}",true).assertTextContains("有插座",substring=true)
        assertTrue(period.fetchSemanticsNode().config.contains(SemanticsActions.OnLongClick))
        compose.mainClock.autoAdvance = true
    }

    @Test fun disposalDuringTheTailStillCommitsTheAcceptedClickExactlyOnce() {
        renderRow()
        compose.mainClock.autoAdvance = false
        val open = compose.onNodeWithTag("room_row_${room.id}").fetchSemanticsNode()
            .config[SemanticsActions.CustomActions].single()
        compose.runOnIdle { assertTrue(open.action()) }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("room_action_${room.id}_0").performClick()
        compose.runOnIdle { assertTrue(pending); assertFalse(profile.favorite); visible = false }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { assertTrue(profile.favorite); assertFalse(pending) }
        compose.mainClock.advanceTimeBy(700)
        compose.runOnIdle { assertTrue(profile.favorite) }
        compose.mainClock.autoAdvance = true
    }

    @Test fun noteLineIsReservedAndTheStripIsEightDpBelowItRegardlessOfNoteContent() {
        renderRow()
        for (target in Campus.entries) for (scale in listOf(1f, 1.5f)) {
            compose.runOnIdle { campus = target; fontScale = scale; profile = room.emptyProfile() }
            val row = compose.onNodeWithTag("room_row_${room.id}").fetchSemanticsNode().boundsInRoot
            val strip = compose.onNodeWithTag("room_period_strip_${room.id}").fetchSemanticsNode().boundsInRoot
            val note = compose.onNodeWithTag("room_note_line_${room.id}", useUnmergedTree = true)
            val empty = note.fetchSemanticsNode().boundsInRoot
            val bar = compose.onNodeWithTag("room_period_${room.id}_6", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val density = bar.height / 5f
            assertTrue(empty.height > 0f)
            assertEquals(8f * density, bar.top - empty.bottom, 1f)
            assertEquals(29f * density, strip.height, 1f)
            for (value in listOf("窗边", "中".repeat(20), "\uD83D\uDE00".repeat(20), "很长的旧备注".repeat(10), "")) {
                compose.runOnIdle { profile = profile.copy(note = value) }
                assertEquals(row, compose.onNodeWithTag("room_row_${room.id}").fetchSemanticsNode().boundsInRoot)
                assertEquals(strip, compose.onNodeWithTag("room_period_strip_${room.id}").fetchSemanticsNode().boundsInRoot)
                assertEquals(empty, note.fetchSemanticsNode().boundsInRoot)
                if (value.isNotEmpty()) compose.onNodeWithTag("room_note_text_${room.id}", useUnmergedTree = true)
                    .assertTextEquals(RoomNotes.limit(value))
            }
        }
    }

    @Test fun openingALegacyLongNoteLimitsOnlyTheDraftUntilTheUserActuallyEdits() {
        profile = room.emptyProfile().copy(note = "旧".repeat(30), favorite = true, tags = setOf("靠窗"))
        val original = profile
        var changes = 0
        compose.setContent {
            YouRoomTheme { RoomNoteDialog(RoomMatch(room, profile, emptySet()), {}, { changes++; profile = it }) }
        }
        compose.onNodeWithTag("room_note").assertTextContains("旧".repeat(20))
        compose.onNodeWithTag("room_note_count", useUnmergedTree = true).assertTextEquals("20/20")
        compose.runOnIdle { assertEquals(0, changes); assertEquals(original, profile) }
        compose.onNodeWithTag("close_room_note").performClick()
        compose.runOnIdle { assertEquals(0, changes); assertEquals(original, profile) }
        compose.onNodeWithTag("room_note").performTextReplacement("新".repeat(21))
        compose.runOnIdle {
            assertEquals(1, changes)
            assertEquals(original.copy(note = "新".repeat(20)), profile)
        }
    }
}
