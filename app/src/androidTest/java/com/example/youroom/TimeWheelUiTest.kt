package com.example.youroom

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.example.youroom.domain.Campus
import com.example.youroom.ui.WheelTimePicker
import com.example.youroom.ui.timeWheelProjection
import com.example.youroom.ui.theme.YOURoomTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TimeWheelUiTest {
    @get:Rule val compose = createComposeRule()
    private val committed = mutableListOf<Int>()
    private var time by mutableIntStateOf(8 * 60 + 25)
    private var moving = false
    private val ticks = mutableListOf<HapticFeedbackType>()
    private val haptic = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { ticks += hapticFeedbackType }
    }
    private val hour = "test_time_hour_wheel"
    private val minute = "test_time_minute_wheel"

    private fun render(initial: Int = 8 * 60 + 25, fontScale: Float = 1f, campus: Campus = Campus.XITUCHENG) {
        time = initial
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale), LocalHapticFeedback provides haptic) {
                YOURoomTheme(campus = campus) {
                    WheelTimePicker("开始时间", time, "test_time", onMoving = { moving = it }) {
                        time = it; committed += it
                    }
                }
            }
        }
    }

    private fun value(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode()
        .config[SemanticsProperties.ProgressBarRangeInfo].current.toInt()

    private fun action(tag: String, label: String) {
        val action = compose.onNodeWithTag(tag).fetchSemanticsNode()
            .config[SemanticsActions.CustomActions].single { it.label == label }
        compose.runOnIdle { assertTrue(action.action()) }
        compose.waitForIdle()
    }

    @Test fun initialValuesAreInlineWithoutKeyboardOrConfirmation() {
        render()
        assertEquals(8, value(hour))
        assertEquals(25, value(minute))
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithTag("planner_time_confirm").assertDoesNotExist()
        compose.onNodeWithTag("planner_time_cancel").assertDoesNotExist()
        assertTrue(committed.isEmpty())
        assertTrue(ticks.isEmpty())
        compose.onNodeWithText("小时").assertDoesNotExist()
        compose.onNodeWithText("分钟").assertDoesNotExist()
        val colon = compose.onNodeWithTag("test_time_colon").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val hours = compose.onNodeWithTag(hour).fetchSemanticsNode().boundsInRoot
        val minutes = compose.onNodeWithTag(minute).fetchSemanticsNode().boundsInRoot
        assertTrue(colon.left >= hours.right && colon.right <= minutes.left)
        assertEquals(hours.center.y - 2f, colon.center.y, 1f)
    }

    @Test fun draggingEitherWheelChangesOnlyThatColumnAndCommitsAfterSettlement() {
        render()
        for (tag in listOf(hour, minute)) {
            val wheel = compose.onNodeWithTag(tag)
            val row = wheel.fetchSemanticsNode().boundsInRoot.height / 4.5f
            wheel.performTouchInput {
                down(center)
                moveBy(Offset(0f, -row), delayMillis = 500)
                advanceEventTime(200)
                up()
            }
            compose.waitForIdle()
        }
        assertEquals(9, value(hour))
        assertEquals(26, value(minute))
        assertEquals(listOf(9 * 60 + 25, 9 * 60 + 26), committed)
    }

    @Test fun tappingTheProjectedNeighborSnapsItIntoTheCenter() {
        render()
        val wheel = compose.onNodeWithTag(minute)
        wheel.performTouchInput { click(Offset(centerX,
            centerY + height / 4.5f * timeWheelProjection(1f).y)) }
        compose.waitForIdle()
        assertEquals(26, value(minute))
        assertEquals(8 * 60 + 26, time)
    }

    @Test fun midnightWrapAndAccessibleActionsKeepTheOtherColumnUnchanged() {
        render(0)
        action(hour, "上一小时")
        assertEquals(23, value(hour))
        assertEquals(0, value(minute))
        action(minute, "上一分钟")
        assertEquals(59, value(minute))
        assertEquals(1439, time)
        action(hour, "下一小时")
        action(minute, "下一分钟")
        assertEquals(0, time)
    }

    @Test fun accessibleSetValueCommitsImmediatelyWithoutAButton() {
        render()
        compose.onNodeWithTag(hour).performSemanticsAction(SemanticsActions.SetProgress) { it(23f) }
        compose.onNodeWithTag(minute).performSemanticsAction(SemanticsActions.SetProgress) { it(59f) }
        compose.waitForIdle()
        assertEquals(1439, time)
        assertFalse(moving)
    }

    @Test fun quickFlingSettlesAtAValidMinuteAndCommitsExactlyOnce() {
        render()
        compose.onNodeWithTag(minute).performTouchInput { swipeUp(durationMillis = 120) }
        compose.waitForIdle()
        val selected = value(minute)
        assertTrue(selected in 0..59 && selected != 25)
        assertEquals(listOf(8 * 60 + selected), committed)
        assertFalse(moving)
    }

    @Test fun heldDragDoesNotCommitAnUnsettledValue() {
        render()
        val wheel = compose.onNodeWithTag(minute)
        val row = wheel.fetchSemanticsNode().boundsInRoot.height / 4.5f
        wheel.performTouchInput { down(center); moveBy(Offset(0f, -row), delayMillis = 300) }
        compose.runOnIdle { assertTrue(moving); assertTrue(committed.isEmpty()) }
        wheel.performTouchInput { advanceEventTime(200); up() }
        compose.waitForIdle()
        assertEquals(listOf(8 * 60 + 26), committed)
        assertFalse(moving)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun directionKeysAdjustTheFocusedWheelWithoutOpeningInput() {
        render()
        val wheel = compose.onNodeWithTag(hour)
        wheel.performSemanticsAction(SemanticsActions.RequestFocus) { assertTrue(it()) }
        wheel.performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()
        assertEquals(9, value(hour))
        wheel.performKeyInput { pressKey(Key.DirectionUp) }
        compose.waitForIdle()
        assertEquals(8, value(hour))
    }

    @Test fun largeFontWheelsStaySideBySideInShahe() {
        render(fontScale = 1.5f, campus = Campus.SHAHE)
        val hours = compose.onNodeWithTag(hour).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val minutes = compose.onNodeWithTag(minute).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(hours.right < minutes.left)
        assertTrue(hours.width >= 48 && minutes.width >= 48)
        assertEquals(8, value(hour))
        assertEquals(25, value(minute))
    }

    @Test fun externalShortcutsUpdateBothColumnsWithoutEmittingEdits() {
        render()
        compose.runOnIdle { time = 15 * 60 + 21 }
        compose.waitForIdle()
        assertEquals(15, value(hour))
        assertEquals(21, value(minute))
        assertTrue(committed.isEmpty())
        action(minute, "下一分钟")
        assertEquals(15 * 60 + 22, time)
    }

    @Test fun concurrentHourAndMinuteSettlementNeverLosesAnEdit() {
        render()
        val hourAction = compose.onNodeWithTag(hour).fetchSemanticsNode().config[SemanticsActions.SetProgress]!!.action!!
        val minuteAction = compose.onNodeWithTag(minute).fetchSemanticsNode().config[SemanticsActions.SetProgress]!!.action!!
        compose.runOnIdle { hourAction(10f); minuteAction(40f) }
        compose.waitForIdle()
        assertEquals(10 * 60 + 40, time)
    }

    @Test fun hapticTicksFollowCenterPassagesWithoutIdleOrHalfRowPulses() {
        render()
        val wheel = compose.onNodeWithTag(minute)
        val row = wheel.fetchSemanticsNode().boundsInRoot.height / 4.5f
        wheel.performTouchInput { down(center); moveBy(Offset(0f, -row * .7f), delayMillis = 300) }
        compose.runOnIdle { assertTrue(ticks.isEmpty()) }
        wheel.performTouchInput { moveBy(Offset(0f, -row), delayMillis = 300) }
        compose.runOnIdle {
            assertEquals(1, ticks.size)
            assertEquals(HapticFeedbackType.SegmentFrequentTick, ticks.single())
        }
        wheel.performTouchInput { advanceEventTime(200); up() }
        compose.waitForIdle()
        val count = ticks.size
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals(count, ticks.size) }
    }

    @Test fun shortcutRotatesThroughIntermediateDigitsThenSettlesWithoutPublishingThem() {
        render()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { time = 15 * 60 + 21 }
        compose.mainClock.advanceTimeBy(160)
        compose.runOnIdle { assertTrue(moving); assertTrue(committed.isEmpty()) }
        assertTrue(value(hour) in 9..14)
        compose.mainClock.advanceTimeBy(2000)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(15, value(hour))
        assertEquals(21, value(minute))
        assertTrue(committed.isEmpty())
        assertTrue(ticks.isNotEmpty())
        assertFalse(moving)
    }

    @Test fun aNewShortcutDuringAnimationSettlesAtTheLatestTarget() {
        render()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { time = 15 * 60 + 21 }
        compose.mainClock.advanceTimeBy(160)
        compose.runOnIdle { time = 9 * 60 + 35 }
        compose.mainClock.advanceTimeBy(2000)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(9, value(hour))
        assertEquals(35, value(minute))
        assertEquals(9 * 60 + 35, time)
        assertTrue(committed.isEmpty())
    }

    @Test fun draggingCanTakeOverAProgrammaticRotationAndCommitTheUsersFinalValue() {
        render()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { time = 15 * 60 + 21 }
        compose.mainClock.advanceTimeBy(160)
        val wheel = compose.onNodeWithTag(minute)
        val row = wheel.fetchSemanticsNode().boundsInRoot.height / 4.5f
        wheel.performTouchInput { down(center); moveBy(Offset(0f, row), delayMillis = 300) }
        compose.mainClock.advanceTimeBy(100)
        wheel.performTouchInput { advanceEventTime(200); up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(15, value(hour))
        assertTrue(value(minute) != 21)
        assertEquals(15 * 60 + value(minute), time)
        assertEquals(time, committed.last())
        assertFalse(moving)
    }
}
