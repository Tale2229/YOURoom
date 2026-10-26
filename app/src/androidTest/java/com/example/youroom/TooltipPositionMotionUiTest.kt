package com.example.youroom

import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.PopupPositionProvider
import com.example.youroom.ui.SmoothTooltipPositionProvider
import com.example.youroom.ui.TooltipPositionMotion
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TooltipPositionMotionUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var motion: TooltipPositionMotion
    private lateinit var provider: SmoothTooltipPositionProvider
    private val window = IntSize(300, 600)
    private val size = IntSize(100, 34)
    private fun render() {
        compose.setContent {
            motion = remember { TooltipPositionMotion().also { it.target.value = IntOffset(20, 150) } }
            motion.Run()
            provider = remember { SmoothTooltipPositionProvider(object : PopupPositionProvider {
                override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                    layoutDirection: LayoutDirection, popupContentSize: IntSize) = anchorBounds.topLeft
            }, motion, 8) }
            Text("时间提示位置测试")
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }
    @Test fun movementInterpolatesFromTheDisplayedPositionAndReversalUsesTheLatestTarget() {
        render()
        compose.runOnIdle {
            assertEquals(Offset(20f, 150f), motion.position.value)
            assertEquals(IntOffset(20, 150), provider.calculatePosition(IntRect(150, 200, 160, 210), window, LayoutDirection.Ltr, size))
        }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle { assertTrue(motion.position.value.x > 20f && motion.position.value.x < 150f) }
        compose.runOnIdle { provider.calculatePosition(IntRect(40, 80, 50, 90), window, LayoutDirection.Ltr, size) }
        compose.mainClock.advanceTimeBy(192)
        compose.runOnIdle { assertEquals(Offset(40f, 80f), motion.position.value) }
    }
    @Test fun changingPopupSizeKeepsEvenIntermediatePositionsInsideTheWindow() {
        render()
        compose.runOnIdle {
            val position = provider.calculatePosition(IntRect(40, 80, 50, 90), IntSize(40, 40), LayoutDirection.Rtl, IntSize(80, 60))
            assertEquals(IntOffset.Zero, position)
        }
        compose.mainClock.advanceTimeBy(192)
    }
}
