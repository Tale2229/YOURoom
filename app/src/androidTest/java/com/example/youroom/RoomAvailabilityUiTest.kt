package com.example.youroom

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.youroom.data.DemoData
import com.example.youroom.domain.*
import com.example.youroom.ui.RoomAvailabilityStrip
import com.example.youroom.ui.SwipeRoomRow
import com.example.youroom.ui.theme.YouRoomTheme
import com.example.youroom.ui.theme.campusColorScheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalTime

@OptIn(ExperimentalTestApi::class)
class RoomAvailabilityUiTest {
    @get:Rule val compose = createComposeRule()
    private val id = "01:2-201"
    private val room = Classroom(id, "2-201", "2", 180)
    private var slots by mutableStateOf(DemoData.snapshot().slots)
    private var enabled by mutableStateOf(true)
    private var revision by mutableIntStateOf(0)
    private var campus by mutableStateOf(Campus.XITUCHENG)
    private var fontScale by mutableFloatStateOf(1f)
    private var direction by mutableStateOf(LayoutDirection.Ltr)
    private var dark by mutableStateOf(false)
    private var expandedRoom by mutableStateOf<String?>(null)
    private var edits = 0
    private val stripTag get() = "room_period_strip_$id"
    private val tooltip get() = "room_time_${id}_tooltip"
    private fun face(number: Int) = compose.onNodeWithTag("room_period_${id}_$number", useUnmergedTree = true)
    private fun label(number: Int) = compose.onNodeWithTag("room_time_${id}_label_$number")

    private fun render(list: Boolean = false) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale),
                LocalLayoutDirection provides direction) {
                YouRoomTheme(campus = campus, darkTheme = dark) {
                    if (!list) Surface {
                        RoomAvailabilityStrip(id, setOf(1, 2, 6), slots, enabled, inspectionKey = revision)
                    } else Surface(Modifier.fillMaxSize()) {
                        LazyColumn(Modifier.fillMaxSize().testTag("inspection_test_list")) {
                            item { Spacer(Modifier.height(72.dp)) }
                            items(20, key = { it }) { index ->
                                val current = room.copy(id = if (index == 0) id else "01:2-${300 + index}",
                                    name = if (index == 0) room.name else "2-${300 + index}")
                                SwipeRoomRow(RoomMatch(current, current.emptyProfile(), setOf(1, 2, 6)), slots,
                                    PersonalTags.defaultOrder, expandedRoom == current.id, enabled,
                                    { open -> if (open) expandedRoom = current.id else if (expandedRoom == current.id) expandedRoom = null },
                                    { edits++ }, { edits++ }, { edits++ }, { edits++ }, inspectionKey = revision)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun point(number: Int): Offset {
        val strip = compose.onNodeWithTag(stripTag).fetchSemanticsNode().boundsInRoot
        return face(number).fetchSemanticsNode().boundsInRoot.center - strip.topLeft
    }

    private fun hold(number: Int) {
        val position = point(number)
        compose.onNodeWithTag(stripTag).performTouchInput { down(position) }
        compose.waitUntil(4_000) { compose.onAllNodesWithTag(tooltip).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(tooltip).assertIsDisplayed()
    }

    @Test fun shortTapAndEarlyHorizontalMotionNeverOpenOrCloseTheUpperActions() {
        render(list = true)
        compose.onNodeWithTag(stripTag).performTouchInput { click(center) }
        compose.onNodeWithTag(tooltip).assertDoesNotExist()
        compose.onNodeWithTag(stripTag).performTouchInput { swipeLeft(durationMillis = 120) }
        compose.onNodeWithTag(tooltip).assertDoesNotExist()
        compose.onNodeWithTag("room_actions_$id").assertDoesNotExist()
        compose.onNodeWithTag("room_swipe_$id").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("room_actions_$id").assertIsDisplayed()
        compose.onNodeWithTag(stripTag, useUnmergedTree = true).performTouchInput { swipeRight(durationMillis = 120) }
        compose.onNodeWithTag("room_actions_$id").assertIsDisplayed()
        assertEquals(0, edits)
    }

    @Test fun holdUsesTheResponseTimeAndLiftsOnlyThatBarWithoutChangingHeight() {
        slots = slots.map { if (it.number == 6) it.copy(start = LocalTime.of(14, 10), end = LocalTime.of(14, 55)) else it }
        render()
        val strip = compose.onNodeWithTag(stripTag).fetchSemanticsNode().boundsInRoot
        val initial = face(6).fetchSemanticsNode().boundsInRoot
        val next = face(7).fetchSemanticsNode().boundsInRoot
        hold(6)
        label(6).assertTextEquals("6 · 14:10–14:55")
        compose.waitForIdle()
        val lifted = face(6).fetchSemanticsNode().boundsInRoot
        assertTrue(lifted.top < initial.top)
        assertEquals(initial.height, lifted.height, 1f)
        assertEquals(next, face(7).fetchSemanticsNode().boundsInRoot)
        assertEquals(strip, compose.onNodeWithTag(stripTag).fetchSemanticsNode().boundsInRoot)
        face(6).assertIsSelected()
        compose.onNodeWithTag(stripTag).performTouchInput { up() }
        compose.onNodeWithTag(tooltip).assertDoesNotExist()
        face(6).assertIsNotSelected()
        assertEquals(initial, face(6).fetchSemanticsNode().boundsInRoot)
    }

    @Test fun heldInspectionFollowsXEvenOutsideTheRowAndClampsAtBothEnds() {
        render()
        val first = point(1)
        val sixth = point(6)
        val fourteenth = point(14)
        hold(1)
        compose.onNodeWithTag(stripTag).performTouchInput { moveTo(sixth + Offset(0f, 180f)) }
        label(6).assertTextEquals("6 · 13:00–13:45")
        compose.onNodeWithTag(stripTag).performTouchInput { moveTo(fourteenth + Offset(100f, -160f)) }
        label(14).assertTextEquals("14 · 20:10–20:55")
        compose.onNodeWithTag(stripTag).performTouchInput { moveTo(first + Offset(-100f, 220f)) }
        label(1).assertTextEquals("1 · 08:00–08:45")
        compose.onNodeWithTag(stripTag).performTouchInput { up() }
        compose.onNodeWithTag(tooltip).assertDoesNotExist()
    }

    @Test fun earlyVerticalDragScrollsTheListButAHeldDragLocksItUntilRelease() {
        render(list = true)
        val list = compose.onNodeWithTag("inspection_test_list")
        fun scroll() = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        hold(6)
        val before = scroll()
        val target = point(8)
        compose.onNodeWithTag(stripTag).performTouchInput { moveTo(target + Offset(0f, -300f)) }
        label(8).assertTextEquals("8 · 14:45–15:30")
        assertEquals(before, scroll(), .01f)
        compose.onNodeWithTag("room_actions_$id").assertDoesNotExist()
        compose.onNodeWithTag(stripTag).performTouchInput { up() }
        compose.onNodeWithTag(stripTag).performTouchInput {
            down(center); moveTo(center + Offset(0f, -280f), delayMillis = 120); up()
        }
        compose.onNodeWithTag(tooltip).assertDoesNotExist()
        assertTrue(scroll() > before)
        assertEquals(0, edits)
    }

    @Test fun cancellingAHoldResetsTheBarAndSecondFingersCancelBothBeforeAndAfterActivation() {
        render()
        val original = face(6).fetchSemanticsNode().boundsInRoot
        hold(6)
        compose.onNodeWithTag(stripTag).performTouchInput { cancel() }
        compose.onNodeWithTag(tooltip).assertDoesNotExist()
        assertEquals(original, face(6).fetchSemanticsNode().boundsInRoot)
        hold(6)
        compose.onNodeWithTag(stripTag).performTouchInput { down(1, centerRight); up(1); up(0) }
        compose.onNodeWithTag(tooltip).assertDoesNotExist()
        compose.onNodeWithTag(stripTag).performTouchInput { down(0, centerLeft); down(1, centerRight); up(1); up(0) }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag(tooltip).assertDoesNotExist()
    }

    @Test fun campusResponseChangeAndDisablingDismissHeldAndAccessibleHints() {
        render()
        hold(6)
        compose.runOnIdle { enabled = false }
        compose.onNodeWithTag(tooltip).assertDoesNotExist()
        compose.onNodeWithTag(stripTag).performTouchInput { up() }
        compose.runOnIdle { enabled = true }
        hold(6)
        compose.runOnIdle { revision++; campus = Campus.SHAHE }
        compose.onNodeWithTag(tooltip).assertDoesNotExist()
        compose.onNodeWithTag(stripTag).performTouchInput { up() }
        face(6).performSemanticsAction(SemanticsActions.OnLongClick) { assertTrue(it()) }
        label(6).assertExists()
        compose.runOnIdle { slots = slots.filter { it.number != 6 } }
        compose.onNodeWithTag(tooltip).assertDoesNotExist()
    }

    @Test fun unavailableBarsRemainGreyWhileTheirTimesAreInspectableInBothCampusThemes() {
        render()
        for (target in Campus.entries) for (night in listOf(false, true)) {
            compose.runOnIdle { campus = target; dark = night; revision++ }
            val colors = campusColorScheme(target, night)
            assertEquals(colors.outlineVariant, face(3).captureToImage().toPixelMap()[2, 2])
            assertEquals(colors.primary, face(6).captureToImage().toPixelMap()[2, 2])
            hold(3)
            label(3).assertTextEquals("3 · 09:50–10:35")
            assertEquals(colors.outlineVariant, face(3).captureToImage().toPixelMap()[2, 2])
            val pixels = compose.onNodeWithTag(tooltip).captureToImage().toPixelMap()
            assertEquals(colors.background, pixels[pixels.width / 2, pixels.height / 8])
            assertEquals(colors.background, pixels[1, pixels.height / 2])
            compose.onNodeWithTag(stripTag).performTouchInput { up() }
        }
    }

    @Test fun accessibilityAndKeyboardShowTimeWithoutAnySelectionOrProfileEdit() {
        render(list = true)
        face(6).performSemanticsAction(SemanticsActions.OnLongClick) { assertTrue(it()) }
        label(6).assertTextEquals("6 · 13:00–13:45")
        compose.mainClock.advanceTimeBy(2_100)
        compose.onNodeWithTag(tooltip).assertDoesNotExist()
        face(3).performSemanticsAction(SemanticsActions.RequestFocus) { assertTrue(it()) }
        face(3).performKeyInput { pressKey(Key.Enter) }
        label(3).assertTextEquals("3 · 09:50–10:35")
        assertEquals(0, edits)
        compose.onNodeWithTag("room_actions_$id").assertDoesNotExist()
    }

    @Test fun rightToLeftAndSparsePeriodsStillUseTheActualBarUnderTheFinger() {
        direction = LayoutDirection.Rtl
        slots = slots.filter { it.number in setOf(1, 6, 14) }
        render()
        assertTrue(face(1).fetchSemanticsNode().boundsInRoot.left > face(14).fetchSemanticsNode().boundsInRoot.right)
        val last = point(14)
        hold(1)
        label(1).assertTextEquals("1 · 08:00–08:45")
        compose.onNodeWithTag(stripTag).performTouchInput { moveTo(last) }
        label(14).assertTextEquals("14 · 20:10–20:55")
        compose.onNodeWithTag(stripTag).performTouchInput { up() }
    }

    @Test fun largeFontHintsDoNotIncreaseTheRowHeightAndAnEmptyStripDoesNothing() {
        fontScale = 1.5f
        render(list = true)
        val row = compose.onNodeWithTag("room_row_$id").fetchSemanticsNode().boundsInRoot
        hold(14)
        label(14).assertIsDisplayed()
        assertEquals(row, compose.onNodeWithTag("room_row_$id").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag(stripTag).performTouchInput { up() }
        compose.runOnIdle { slots = emptyList() }
        compose.onNodeWithTag(stripTag).performTouchInput { click() }
        compose.onNodeWithTag(tooltip).assertDoesNotExist()
    }

    @Test fun onlyTheUpperRegionSlidesAndExpandedRowsStillAllowTimeInspection() {
        render(list = true)
        val strip = compose.onNodeWithTag(stripTag).fetchSemanticsNode().boundsInRoot
        val bars = (1..14).map { face(it).fetchSemanticsNode().boundsInRoot }
        compose.onNodeWithTag("room_swipe_$id").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("room_actions_$id").assertIsDisplayed()
        compose.onNodeWithTag(tooltip).assertDoesNotExist()
        assertEquals(strip, compose.onNodeWithTag(stripTag).fetchSemanticsNode().boundsInRoot)
        for (i in 1..14) assertEquals(bars[i - 1], face(i).fetchSemanticsNode().boundsInRoot)
        hold(14)
        label(14).assertTextEquals("14 · 20:10–20:55")
        val six = point(6)
        compose.onNodeWithTag(stripTag).performTouchInput { moveTo(six) }
        label(6).assertExists()
        compose.onNodeWithTag("room_actions_$id").assertIsDisplayed()
        compose.onNodeWithTag(stripTag).performTouchInput { up() }
        compose.onNodeWithTag("room_swipe_$id", useUnmergedTree = true).performTouchInput { swipeRight() }
        compose.onNodeWithTag("room_actions_$id").assertDoesNotExist()
        hold(6)
        label(6).assertExists()
        compose.onNodeWithTag(stripTag).performTouchInput { up() }
    }

    @Test fun aHeldStripKeepsOwnershipDuringHorizontalMotionAndNeverSwipesTheRow() {
        render(list = true)
        hold(6)
        compose.onNodeWithTag(stripTag).performTouchInput { moveTo(centerLeft, delayMillis = 80) }
        compose.onNodeWithTag(tooltip).assertExists()
        compose.onNodeWithTag("room_actions_$id").assertDoesNotExist()
        compose.onNodeWithTag(stripTag).performTouchInput { up() }
        compose.onNodeWithTag(tooltip).assertDoesNotExist()
        assertEquals(0, edits)
    }

    @Test fun diagonalVerticalMotionStillScrollsInsteadOfOpeningActions() {
        render(list = true)
        val list = compose.onNodeWithTag("inspection_test_list")
        val before = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        compose.onNodeWithTag(stripTag).performTouchInput {
            down(center); moveTo(center + Offset(-45f, -250f), delayMillis = 120); up()
        }
        assertTrue(list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > before)
        compose.onNodeWithTag("room_actions_$id").assertDoesNotExist()
        compose.onNodeWithTag(tooltip).assertDoesNotExist()
    }
}
