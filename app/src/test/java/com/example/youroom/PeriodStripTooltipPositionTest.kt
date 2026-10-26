package com.example.youroom

import androidx.compose.ui.unit.*
import com.example.youroom.ui.PeriodStripTooltipPositionProvider
import com.example.youroom.ui.SlotTimeTooltipPositionProvider
import org.junit.Assert.*
import org.junit.Test
import com.example.youroom.data.DemoData
import com.example.youroom.domain.*

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

    @Test fun proportionalPopupUsesTheRealBarCenterIncludingLunchAndRtl() {
        val slots=DemoData.snapshot().slots
        val anchor=IntRect(0,150,360,200)
        val popup=IntSize(100,30)
        for(index in listOf(4,5,8)) for(direction in listOf(LayoutDirection.Ltr,LayoutDirection.Rtl)) {
            val provider=PeriodStripTooltipPositionProvider(index,14,24,3,8,4,5,
                SlotTimeTooltipPositionProvider(28,8),slots,PeriodStripLayout.TIME_PROPORTIONAL)
            val result=provider.calculatePosition(anchor,IntSize(360,600),direction,popup)
            val bar=PeriodStripGeometry.bars(slots,312f,3f,PeriodStripLayout.TIME_PROPORTIONAL)[index]
            val expected=24+if(direction==LayoutDirection.Ltr) bar.center else 312-bar.center
            assertEquals(expected,result.x+popup.width/2f,1f)
            assertEquals(150+8-4-28-30,result.y)
        }
    }
}
