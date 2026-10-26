package com.example.youroom

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.*
import com.example.youroom.domain.SlotBounds
import com.example.youroom.ui.GridSlotTooltipPositionProvider
import com.example.youroom.ui.SlotTimeTooltipPositionProvider
import org.junit.Assert.*
import org.junit.Test

class GridSlotTooltipPositionTest {
    private val window = IntSize(320, 740)
    private val size = IntSize(120, 34)
    private var face: SlotBounds? = SlotBounds(6, 272f, 250f, 320f, 286f)
    private var origin = Offset(0f, 250f)
    private val provider = GridSlotTooltipPositionProvider({ face }, { origin }, SlotTimeTooltipPositionProvider(28, 8))
    @Test fun oneGridAnchorCanFollowDifferentNumbersAndRows() {
        assertEquals(IntOffset(192, 188), provider.calculatePosition(IntRect(0, 250, 320, 394), window, LayoutDirection.Ltr, size))
        face = SlotBounds(7, 0f, 298f, 48f, 334f)
        assertEquals(IntOffset(8, 236), provider.calculatePosition(IntRect(0, 250, 320, 394), window, LayoutDirection.Ltr, size))
    }
    @Test fun gridRootAndWindowOffsetsAreConvertedExactlyOnce() {
        origin = Offset(20f, 250f); face = SlotBounds(2, 72f, 298f, 120f, 334f)
        assertEquals(IntOffset(86, 246), provider.calculatePosition(IntRect(70, 260, 370, 404), window, LayoutDirection.Ltr, size))
    }
    @Test fun physicalFaceCoordinatesWorkInRtlWithoutMirroringTwice() {
        val anchor = IntRect(0, 250, 320, 394)
        assertEquals(provider.calculatePosition(anchor, window, LayoutDirection.Ltr, size),
            provider.calculatePosition(anchor, window, LayoutDirection.Rtl, size))
    }
    @Test fun missingFaceFallsBackSafelyAndWindowEdgesStayConstrained() {
        face = null
        assertEquals(IntOffset(100, 8), provider.calculatePosition(IntRect(0, 20, 320, 164), window, LayoutDirection.Ltr, size))
        face = SlotBounds(1, 0f, 1000f, 48f, 1036f)
        assertEquals(IntOffset(8, 698), provider.calculatePosition(IntRect(0, 250, 320, 394), window, LayoutDirection.Ltr, size))
    }
}
