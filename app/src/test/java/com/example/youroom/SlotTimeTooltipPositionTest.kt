package com.example.youroom

import androidx.compose.ui.unit.*
import com.example.youroom.ui.SlotTimeTooltipPositionProvider
import org.junit.Assert.*
import org.junit.Test

class SlotTimeTooltipPositionTest {
    private val provider = SlotTimeTooltipPositionProvider(gap = 28, margin = 8)
    private val window = IntSize(320, 740)
    private val tooltip = IntSize(120, 34)

    @Test fun centeredAboveTheRectangleWithHalfTheOriginalGap() {
        val anchor = IntRect(130, 250, 178, 286)
        val offset = provider.calculatePosition(anchor, window, LayoutDirection.Ltr, tooltip)
        assertEquals(IntOffset(94, 188), offset)
        assertEquals(28, anchor.top - (offset.y + tooltip.height))
    }

    @Test fun firstAndLastColumnsRemainInsideTheScreen() {
        assertEquals(8, provider.calculatePosition(IntRect(0, 250, 48, 286), window, LayoutDirection.Ltr, tooltip).x)
        assertEquals(192, provider.calculatePosition(IntRect(272, 250, 320, 286), window, LayoutDirection.Ltr, tooltip).x)
    }

    @Test fun topAndBottomConstraintsPreventOffscreenHints() {
        assertEquals(8, provider.calculatePosition(IntRect(0, 30, 48, 66), window, LayoutDirection.Ltr, tooltip).y)
        assertEquals(698, provider.calculatePosition(IntRect(0, 1000, 48, 1036), window, LayoutDirection.Ltr, tooltip).y)
    }

    @Test fun rightToLeftKeepsTheHintAnchoredToTheSameNumber() {
        val anchor = IntRect(130, 250, 178, 286)
        assertEquals(provider.calculatePosition(anchor, window, LayoutDirection.Ltr, tooltip),
            provider.calculatePosition(anchor, window, LayoutDirection.Rtl, tooltip))
    }

    @Test fun oversizedPopupNeverCreatesAnInvalidClampRange() {
        assertEquals(IntOffset.Zero, provider.calculatePosition(IntRect.Zero, IntSize(20, 20),
            LayoutDirection.Ltr, IntSize(100, 40)))
    }
}
