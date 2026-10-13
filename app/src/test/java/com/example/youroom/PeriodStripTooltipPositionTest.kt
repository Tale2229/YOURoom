package com.example.youroom

import androidx.compose.ui.unit.*
import com.example.youroom.ui.PeriodStripTooltipPositionProvider
import com.example.youroom.ui.SlotTimeTooltipPositionProvider
import org.junit.Assert.*
import org.junit.Test

class PeriodStripTooltipPositionTest {
    private fun provider(index: Int) = PeriodStripTooltipPositionProvider(index, 14, 24, 3, 16, 4, 5,
        SlotTimeTooltipPositionProvider(28, 8))
    private val strip = IntRect(0, 150, 328, 187)
    private val window = IntSize(328, 600)
    private val label = IntSize(120, 30)

    @Test fun popupIsAboveTheLiftedBarAndUsesTheBarCenterNotTheWholeStrip() {
        val result = provider(5).calculatePosition(strip, window, LayoutDirection.Ltr, label)
        assertEquals(150 + 16 - 4 - 28 - 30, result.y)
        val bar = (328f - 48 - 39) / 14
        assertEquals(24 + kotlin.math.round(5 * (bar + 3)).toInt() + (kotlin.math.round(bar).toInt() - 120) / 2, result.x)
    }

    @Test fun theEndBarsKeepTheLabelInsideTheScreenMargins() {
        assertEquals(8, provider(0).calculatePosition(strip, window, LayoutDirection.Ltr, label).x)
        assertEquals(200, provider(13).calculatePosition(strip, window, LayoutDirection.Ltr, label).x)
        assertEquals(8, provider(0).calculatePosition(strip.copy(top = 10, bottom = 47), window, LayoutDirection.Ltr, label).y)
    }

    @Test fun rightToLeftMirrorsTheAnchorWithoutChangingPeriodIdentity() {
        assertEquals(200, provider(0).calculatePosition(strip, window, LayoutDirection.Rtl, label).x)
        assertEquals(8, provider(13).calculatePosition(strip, window, LayoutDirection.Rtl, label).x)
    }
}
