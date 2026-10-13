package com.example.youroom

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.example.youroom.domain.Campus
import com.example.youroom.ui.WheelTimePickerDialog
import com.example.youroom.ui.theme.YouRoomTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TimeWheelUiTest {
    @get:Rule val compose = createComposeRule()
    private val confirmed = mutableListOf<Int>()
    private var dismissed = false

    private fun render(initial: Int = 8 * 60 + 25, fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                YouRoomTheme(campus = Campus.XITUCHENG) {
                    WheelTimePickerDialog("开始时间", initial, { dismissed = true }, { confirmed += it })
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

    @Test fun initialValuesAreCenteredWithoutKeyboardFields() {
        render()
        assertEquals(8, value("planner_hour_wheel"))
        assertEquals(25, value("planner_minute_wheel"))
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithTag("planner_time_confirm").assertIsEnabled().performClick()
        assertEquals(listOf(8 * 60 + 25), confirmed)
    }

    @Test fun draggingEitherWheelChangesOnlyThatColumnAndCommitsOnConfirm() {
        render()
        for (tag in listOf("planner_hour_wheel", "planner_minute_wheel")) {
            val wheel = compose.onNodeWithTag(tag)
            val row = wheel.fetchSemanticsNode().boundsInRoot.height / 5
            wheel.performTouchInput {
                down(center)
                moveBy(Offset(0f, -row), delayMillis = 500)
                advanceEventTime(200)
                up()
            }
            compose.waitForIdle()
        }
        assertEquals(9, value("planner_hour_wheel"))
        assertEquals(26, value("planner_minute_wheel"))
        assertTrue(confirmed.isEmpty())
        compose.onNodeWithTag("planner_time_confirm").performClick()
        assertEquals(listOf(9 * 60 + 26), confirmed)
    }

    @Test fun tappingTheNeighborSnapsItIntoTheCenter() {
        render()
        val wheel = compose.onNodeWithTag("planner_minute_wheel")
        wheel.performTouchInput { click(Offset(centerX, centerY + height / 5f)) }
        compose.waitForIdle()
        assertEquals(26, value("planner_minute_wheel"))
        assertEquals(8, value("planner_hour_wheel"))
    }

    @Test fun midnightWrapAndAccessibleActionsKeepTheOtherColumnUnchanged() {
        render(0)
        action("planner_hour_wheel", "上一小时")
        assertEquals(23, value("planner_hour_wheel"))
        assertEquals(0, value("planner_minute_wheel"))
        action("planner_minute_wheel", "上一分钟")
        assertEquals(59, value("planner_minute_wheel"))
        action("planner_hour_wheel", "下一小时")
        action("planner_minute_wheel", "下一分钟")
        assertEquals(0, value("planner_hour_wheel"))
        assertEquals(0, value("planner_minute_wheel"))
    }

    @Test fun accessibleSetValueSupportsAnyMinuteAndCancelDoesNotConfirm() {
        render()
        compose.onNodeWithTag("planner_hour_wheel").performSemanticsAction(SemanticsActions.SetProgress) { it(23f) }
        compose.onNodeWithTag("planner_minute_wheel").performSemanticsAction(SemanticsActions.SetProgress) { it(59f) }
        assertEquals(23, value("planner_hour_wheel"))
        assertEquals(59, value("planner_minute_wheel"))
        compose.onNodeWithTag("planner_time_cancel").performClick()
        assertTrue(dismissed)
        assertTrue(confirmed.isEmpty())
    }

    @Test fun quickFlingSettlesAtAValidMinuteBeforeConfirming() {
        render()
        compose.onNodeWithTag("planner_minute_wheel").performTouchInput { swipeUp(durationMillis = 120) }
        compose.waitForIdle()
        val selected = value("planner_minute_wheel")
        assertTrue(selected in 0..59 && selected != 25)
        compose.onNodeWithTag("planner_time_confirm").assertIsEnabled().performClick()
        assertEquals(listOf(8 * 60 + selected), confirmed)
    }

    @Test fun heldDragCannotConfirmAnUnsettledValue() {
        render()
        val wheel = compose.onNodeWithTag("planner_minute_wheel")
        val row = wheel.fetchSemanticsNode().boundsInRoot.height / 5
        wheel.performTouchInput { down(center); moveBy(Offset(0f, -row), delayMillis = 300) }
        compose.onNodeWithTag("planner_time_confirm").assertIsNotEnabled()
        assertTrue(confirmed.isEmpty())
        wheel.performTouchInput { advanceEventTime(200); up() }
        compose.waitForIdle()
        compose.onNodeWithTag("planner_time_confirm").assertIsEnabled().performClick()
        assertEquals(listOf(8 * 60 + 26), confirmed)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun directionKeysAdjustTheFocusedWheelWithoutOpeningAnInputField() {
        render()
        val wheel = compose.onNodeWithTag("planner_hour_wheel")
        wheel.performSemanticsAction(SemanticsActions.RequestFocus) { assertTrue(it()) }
        wheel.performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()
        assertEquals(9, value("planner_hour_wheel"))
        wheel.performKeyInput { pressKey(Key.DirectionUp) }
        compose.waitForIdle()
        assertEquals(8, value("planner_hour_wheel"))
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    @Test fun largeFontWheelsStaySideBySideAndLeaveButtonsAccessible() {
        render(fontScale = 1.5f)
        val hour = compose.onNodeWithTag("planner_hour_wheel").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val minute = compose.onNodeWithTag("planner_minute_wheel").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val confirm = compose.onNodeWithTag("planner_time_confirm").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(hour.right < minute.left)
        assertTrue(hour.bottom <= confirm.top && minute.bottom <= confirm.top)
        assertEquals(8, value("planner_hour_wheel"))
        assertEquals(25, value("planner_minute_wheel"))
    }
}
