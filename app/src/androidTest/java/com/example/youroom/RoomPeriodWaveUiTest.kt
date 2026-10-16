package com.example.youroom

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import com.example.youroom.data.DemoData
import com.example.youroom.domain.Campus
import com.example.youroom.domain.PeriodWave
import com.example.youroom.ui.RoomAvailabilityStrip
import com.example.youroom.ui.RoomPeriodWave
import com.example.youroom.ui.theme.YouRoomTheme
import com.example.youroom.ui.theme.campusColorScheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.roundToInt

@OptIn(ExperimentalTestApi::class)
class RoomPeriodWaveUiTest {
    @get:Rule val compose = createComposeRule()
    private val id = "wave-room"
    private val slots = DemoData.snapshot().slots
    private val free = setOf(1, 2, 6)
    private val wave = RoomPeriodWave()
    private var expanded by mutableStateOf(false)
    private var settled by mutableStateOf(false)
    private var enabled by mutableStateOf(true)
    private var dragging by mutableStateOf(false)
    private var revision by mutableIntStateOf(0)
    private var direction by mutableStateOf(LayoutDirection.Ltr)
    private var campus by mutableStateOf(Campus.XITUCHENG)
    private var dark by mutableStateOf(false)
    private var compositions = 0
    private fun render() {
        compose.setContent {
            SideEffect { compositions++ }
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                YouRoomTheme(campus = campus, darkTheme = dark) {
                    wave.Run()
                    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                        RoomAvailabilityStrip(id, free, slots, enabled, wave = wave)
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
    }
    private fun closePulse(elapsed: Long = 160) {
        compose.runOnIdle { expanded = false; wave.play() }
        compose.mainClock.advanceTimeBy(PeriodWave.startDelayMillis+elapsed)
    }
    private fun flat() = compose.runOnIdle {
        assertTrue((0..13).all { wave.amplitude(it, 14) == 0f })
    }
    private fun moving() = compose.runOnIdle {
        assertTrue((0..13).any { wave.amplitude(it, 14) > 0f })
    }

    @Test fun closingFirstWaitsThenPlaysTheSlowerFinitePulseWithoutRestartingItsDelay() {
        render()
        compose.runOnIdle { wave.play() }
        compose.mainClock.advanceTimeBy(PeriodWave.startDelayMillis-48)
        flat()
        compose.runOnIdle { assertTrue(wave.isRunning);wave.play() }
        compose.mainClock.advanceTimeBy(160)
        moving()
        compose.mainClock.advanceTimeBy(450)
        compose.runOnIdle { assertTrue(wave.isRunning) }
        compose.mainClock.advanceTimeBy(200)
        flat()
        compose.runOnIdle { assertFalse(wave.isRunning) }
        compose.mainClock.autoAdvance=true
    }

    private fun waitForLeftPeak() {
        for (frame in 0..40) {
            val atEdge = compose.runOnIdle {
                wave.amplitude(0, 14) > .5f
            }
            if (atEdge) return
            compose.mainClock.advanceTimeBy(16)
        }
        fail("Wave never reached the requested physical edge")
    }

    private fun waitForRightPeak() {
        for (frame in 0..40) {
            if (compose.runOnIdle { wave.amplitude(13,14) > .9f }) return
            compose.mainClock.advanceTimeBy(16)
        }
        fail("Wave never reached the physical right edge")
    }

    @Test fun openingNeverStartsAPulseAndAnExplicitClosingRequestPlaysOnlyOnce() {
        render()
        compose.runOnIdle { expanded = true }
        compose.mainClock.advanceTimeBy(600)
        flat()
        compose.runOnIdle { settled = true }
        compose.mainClock.advanceTimeBy(600)
        flat()
        closePulse()
        moving()
        compose.mainClock.advanceTimeBy(600)
        flat()
        compose.runOnIdle { settled = false }
        compose.mainClock.advanceTimeBy(700)
        flat()
        compose.runOnIdle { settled = true }
        compose.mainClock.advanceTimeBy(64)
        flat()
        compose.runOnIdle { expanded = false }
        compose.mainClock.advanceTimeBy(32)
        closePulse(); moving()
        compose.mainClock.autoAdvance = true
    }

    @Test fun repeatedCloseRequestsAndNewDragsFinishTheCurrentTraversalWithoutRestarting() {
        render(); closePulse(); moving()
        val before = compose.runOnIdle { (0..13).map { wave.amplitude(it, 14) } }
        compose.runOnIdle { wave.play() }
        compose.runOnIdle { assertEquals(before, (0..13).map { wave.amplitude(it, 14) }); assertTrue(wave.isRunning) }
        compose.mainClock.advanceTimeBy(32)
        moving()
        compose.mainClock.advanceTimeBy(700)
        flat()
        compose.runOnIdle { expanded = false }
        compose.mainClock.advanceTimeBy(32)
        closePulse(); moving()
        compose.runOnIdle { dragging = true }
        compose.mainClock.advanceTimeBy(32)
        moving()
        compose.mainClock.advanceTimeBy(700)
        flat()
        compose.runOnIdle { dragging = false }
        compose.mainClock.advanceTimeBy(64)
        flat()
        compose.mainClock.autoAdvance = true
    }

    @Test fun responseChangeAndDisablingBothRetainTheFiniteTailAndAClosedRailCanRearm() {
        render(); closePulse(); moving()
        compose.runOnIdle { revision++ }
        compose.mainClock.advanceTimeBy(32)
        moving()
        compose.mainClock.advanceTimeBy(700)
        flat()
        compose.runOnIdle { expanded = false }
        compose.mainClock.advanceTimeBy(32)
        closePulse(); moving()
        compose.runOnIdle { enabled = false }
        compose.mainClock.advanceTimeBy(32)
        moving()
        compose.mainClock.advanceTimeBy(700)
        flat()
        compose.runOnIdle { enabled = true }
        compose.mainClock.advanceTimeBy(64)
        flat()
        compose.mainClock.autoAdvance = true
    }

    @Test fun pointerKeyboardAndAccessibleTimeInspectionAreBlockedThroughPlaybackAndItsTail() {
        render(); closePulse(64); moving()
        val bar = compose.onNodeWithTag("room_period_${id}_6", useUnmergedTree = true)
        assertFalse(bar.fetchSemanticsNode().config.contains(SemanticsActions.OnLongClick))
        assertFalse(bar.fetchSemanticsNode().config.contains(SemanticsActions.OnClick))
        val strip = compose.onNodeWithTag("room_period_strip_$id")
        strip.performTouchInput { down(center); advanceEventTime(260); up() }
        compose.onNodeWithTag("room_time_${id}_tooltip").assertDoesNotExist()
        compose.runOnIdle { wave.play() }
        compose.mainClock.advanceTimeBy(32)
        assertFalse(bar.fetchSemanticsNode().config.contains(SemanticsActions.OnLongClick))
        compose.mainClock.advanceTimeBy(700)
        bar.performSemanticsAction(SemanticsActions.OnLongClick) { assertTrue(it()) }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("room_time_${id}_label_6").assertTextEquals("6 · 13:00–13:45")
        compose.runOnIdle { assertFalse(expanded) }
        compose.mainClock.autoAdvance = true
    }

    @Test fun rightToLeftWaveLeavesGeometryAndAvailabilityColorsUnchangedInBothThemes() {
        render()
        val strip = compose.onNodeWithTag("room_period_strip_$id")
        for (target in Campus.entries) for (night in listOf(false, true)) for (layout in LayoutDirection.entries) {
            compose.runOnIdle { expanded = false; campus = target; dark = night; direction = layout }
            compose.mainClock.advanceTimeBy(700)
            val before = strip.fetchSemanticsNode().boundsInRoot
            val bars = slots.map { compose.onNodeWithTag("room_period_${id}_${it.number}", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot }
            closePulse(0); waitForRightPeak(); moving()
            assertEquals(before, strip.fetchSemanticsNode().boundsInRoot)
            slots.forEachIndexed { i, slot ->
                assertEquals(bars[i], compose.onNodeWithTag("room_period_${id}_${slot.number}", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot)
            }
            val pixels = strip.captureToImage().toPixelMap()
            val colors = campusColorScheme(target, night)
            slots.forEachIndexed { i, slot ->
                val actual = pixels[(bars[i].center.x - before.left).roundToInt(), (bars[i].center.y - before.top).roundToInt()]
                assertEquals(if (slot.number in free) colors.primary else colors.outlineVariant, actual)
            }
            val rightIndex = bars.indices.maxBy { bars[it].center.x }
            val leftIndex = bars.indices.minBy { bars[it].center.x }
            val raised = pixels[(bars[rightIndex].center.x - before.left).roundToInt(),
                (bars[rightIndex].top - before.top).roundToInt() - 2]
            assertEquals(if (slots[rightIndex].number in free) colors.primary else colors.outlineVariant, raised)
            assertEquals(colors.background, pixels[(bars[leftIndex].center.x - before.left).roundToInt(),
                (bars[leftIndex].top - before.top).roundToInt() - 2])
            compose.mainClock.advanceTimeBy(600)
            flat()
        }
        compose.mainClock.autoAdvance = true
    }

    @Test fun animationFramesOnlyRedrawAndNeverRecomposeTheContainingScreen() {
        render(); closePulse(64); moving()
        val count = compose.runOnIdle { compositions }
        compose.mainClock.advanceTimeBy(256)
        compose.runOnIdle { assertEquals(count, compositions) }
        moving()
        compose.mainClock.autoAdvance = true
    }

    @Test fun thePulseReachesTheLeftThenBecomesFlatAndNeverReturnsOrDwells() {
        render(); closePulse(64)
        waitForLeftPeak()
        compose.mainClock.advanceTimeBy(200)
        flat()
        compose.mainClock.advanceTimeBy(2_000)
        flat()
        compose.mainClock.autoAdvance = true
    }

    @Test fun anEarlyStopRequestStillVisitsTheLeftmostBarBeforeFinishing() {
        render(); closePulse(64)
        compose.runOnIdle { wave.play() }
        waitForLeftPeak()
        compose.runOnIdle { assertTrue(wave.isRunning) }
        compose.mainClock.advanceTimeBy(160)
        flat()
        compose.runOnIdle { assertFalse(wave.isRunning) }
        compose.mainClock.autoAdvance = true
    }

    @Test fun quicklyReopeningDoesNotRestartOrCancelThePreviousClosingPass() {
        render(); closePulse(64)
        compose.runOnIdle { expanded = false }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { expanded = true }
        compose.mainClock.advanceTimeBy(180)
        moving()
        compose.runOnIdle { assertTrue(wave.isRunning) }
        compose.mainClock.advanceTimeBy(700)
        flat()
        compose.mainClock.autoAdvance = true
    }
}
