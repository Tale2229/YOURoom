package com.example.youroom

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.youroom.domain.*
import com.example.youroom.ui.*
import com.example.youroom.ui.theme.YOURoomTheme
import com.example.youroom.ui.theme.campusColorScheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

class RoomActionMotionUiTest {
    @get:Rule val compose=createComposeRule()
    private val flip=RoomActionFlipMotion()
    private val slow=RoomActionFlipMotion()
    private var offset by mutableFloatStateOf(0f)
    private var enabled by mutableStateOf(true)
    private var compositions=0
    private lateinit var motionScope: CoroutineScope
    private fun renderFlip() {
        compose.setContent {
            SideEffect { compositions++ }
            // Pixel assertions below compare with the explicit light palette,
            // independent of the emulator's system night schedule.
            YOURoomTheme(darkTheme=false) {
                motionScope=rememberCoroutineScope()
                flip.Observe({offset},{2200f},{true},enabled)
                slow.Observe({offset},{100f},{true},enabled)
                Surface { RoomActionButtons(Classroom("01:2-201","2-201","2",180).emptyProfile(),{},{},{},{},flip=flip) }
            }
        }
        compose.mainClock.autoAdvance=false
    }
    @Test fun fullButtonThresholdsAndFastCrossingsKeepTheRightToLeftFlipOrder() {
        renderFlip()
        compose.runOnIdle { offset=-51f };compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle { assertTrue(flip.angles.all { it.value==0f }) }
        compose.runOnIdle { offset=-184f };compose.mainClock.advanceTimeBy(120)
        compose.runOnIdle {
            val values=flip.angles.map { it.value }
            assertTrue(values.all { it>0f })
            assertTrue(values.zipWithNext().all { (left,right) -> left<right })
            assertTrue(flip.angles[3].value>slow.angles[3].value)
        }
        compose.mainClock.advanceTimeBy(4000)
        compose.runOnIdle { flip.angles.forEach { assertEquals(360f,it.value,.1f) } }
        compose.mainClock.autoAdvance=true
    }
    @Test fun closingCannotFlipAgainAndReopeningCanRearmTheCoveredButtons() {
        renderFlip()
        compose.runOnIdle { offset=-184f };compose.mainClock.advanceTimeBy(4000)
        compose.runOnIdle { offset=0f };compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle { assertTrue(flip.angles.all { it.value==360f }) }
        compose.runOnIdle { offset=-60f };compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle {
            assertTrue(flip.angles[3].value in .1f..359f)
            assertEquals(360f,flip.angles[2].value,.1f)
        }
        compose.mainClock.autoAdvance=true
    }
    @Test fun deactivationStopsOffscreenFlipsAndAnimationDoesNotRecomposeTheContainer() {
        renderFlip()
        compose.runOnIdle { offset=-184f };compose.mainClock.advanceTimeBy(120)
        val count=compose.runOnIdle { compositions }
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle { assertEquals(count,compositions);enabled=false }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle { assertTrue(flip.angles.all { it.value==0f }) }
        compose.mainClock.autoAdvance=true
    }
    @Test fun theQuarterTurnIsEdgeOnInsteadOfAnInPlaneRotation() {
        renderFlip()
        val button=compose.onNodeWithTag("room_action_01:2-201_3")
        val color=campusColorScheme(Campus.XITUCHENG,false).primary
        fun ink(): Int {
            val pixels=button.captureToImage().toPixelMap()
            return (0 until pixels.width).sumOf { x -> (0 until pixels.height).count { y -> pixels[x,y]==color } }
        }
        val normal=ink()
        assertTrue(normal>0)
        compose.runOnIdle { motionScope.launch(start=CoroutineStart.UNDISPATCHED) { flip.angles[3].snapTo(90f) } }
        compose.mainClock.advanceTimeBy(32)
        assertTrue("Y-axis quarter-turn must be edge-on, not a flat spin",ink()<normal/4)
        compose.mainClock.autoAdvance=true
    }
}
