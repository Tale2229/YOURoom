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
                    wave.Run(expanded, enabled, dragging, settled, revision to direction)
                    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                        RoomAvailabilityStrip(id, free, slots, enabled, wave = wave, onInspection = wave::interrupt)
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
    }
    private fun open() {
        compose.runOnIdle { expanded = true; settled = true }
        compose.mainClock.advanceTimeBy(64)
    }
    private fun flat() = compose.runOnIdle {
        assertTrue((0..13).all { wave.amplitude(it, 14) == 0f })
    }
    private fun moving() = compose.runOnIdle {
        assertTrue((0..13).any { wave.amplitude(it, 14) > 0f })
    }

    @Test fun startsOnlyAfterFullSettlementThenPlaysOncePerOpening() {
        render()
        compose.runOnIdle { expanded = true }
        compose.mainClock.advanceTimeBy(600)
        flat()
        compose.runOnIdle { settled = true }
        compose.mainClock.advanceTimeBy(64)
        moving()
        compose.mainClock.advanceTimeBy(PeriodWave.durationMillis + 64L)
        flat()
        compose.runOnIdle { settled = false }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { settled = true }
        compose.mainClock.advanceTimeBy(64)
        flat()
        compose.runOnIdle { expanded = false }
        compose.mainClock.advanceTimeBy(32)
        open(); moving()
        compose.mainClock.autoAdvance = true
    }

    @Test fun clicksAndNewDragsStopImmediatelyAndDoNotResumeOnTheSameOpenRail() {
        render(); open(); moving()
        compose.runOnIdle { wave.interrupt() }
        flat()
        compose.mainClock.advanceTimeBy(96)
        flat()
        compose.runOnIdle { expanded = false }
        compose.mainClock.advanceTimeBy(32)
        open(); moving()
        compose.runOnIdle { dragging = true }
        compose.mainClock.advanceTimeBy(32)
        flat()
        compose.runOnIdle { dragging = false }
        compose.mainClock.advanceTimeBy(64)
        flat()
        compose.mainClock.autoAdvance = true
    }

    @Test fun responseChangeOrDisablingCancelsAndAClosedRailCanRearm() {
        render(); open(); moving()
        compose.runOnIdle { revision++ }
        compose.mainClock.advanceTimeBy(32)
        flat()
        compose.mainClock.advanceTimeBy(96)
        flat()
        compose.runOnIdle { expanded = false }
        compose.mainClock.advanceTimeBy(32)
        open(); moving()
        compose.runOnIdle { enabled = false }
        compose.mainClock.advanceTimeBy(32)
        flat()
        compose.runOnIdle { enabled = true }
        compose.mainClock.advanceTimeBy(64)
        flat()
        compose.mainClock.autoAdvance = true
    }

    @Test fun inspectingTimeInterruptsTheWaveButDoesNotCloseTheActions() {
        render(); open(); moving()
        compose.onNodeWithTag("room_period_${id}_6", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnLongClick) { assertTrue(it()) }
        compose.mainClock.advanceTimeBy(32)
        flat()
        compose.onNodeWithTag("room_time_${id}_label_6").assertTextEquals("6 · 13:00–13:45")
        compose.runOnIdle { assertTrue(expanded) }
        compose.mainClock.autoAdvance = true
    }

    @Test fun rightToLeftWaveLeavesGeometryAndAvailabilityColorsUnchangedInBothThemes() {
        render()
        val strip = compose.onNodeWithTag("room_period_strip_$id")
        for (target in Campus.entries) for (night in listOf(false, true)) for (layout in LayoutDirection.entries) {
            compose.runOnIdle { expanded = false; campus = target; dark = night; direction = layout }
            compose.mainClock.advanceTimeBy(32)
            val before = strip.fetchSemanticsNode().boundsInRoot
            val bars = slots.map { compose.onNodeWithTag("room_period_${id}_${it.number}", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot }
            open(); moving()
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
        render(); open(); moving()
        val count = compose.runOnIdle { compositions }
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle { assertEquals(count, compositions) }
        flat()
        compose.mainClock.autoAdvance = true
    }
}
