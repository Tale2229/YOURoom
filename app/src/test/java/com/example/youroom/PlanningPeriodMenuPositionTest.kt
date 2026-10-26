package com.example.youroom

import androidx.compose.ui.unit.*
import com.example.youroom.ui.PlanningPeriodMenuPosition
import org.junit.Assert.*
import org.junit.Test

class PlanningPeriodMenuPositionTest {
    private val position=PlanningPeriodMenuPosition(8,4)
    private val popup=IntSize(96,48)
    @Test fun belowTheAnchorAlignsToItsLeadingEdgeInEachDirection() {
        val anchor=IntRect(100,100,220,120)
        assertEquals(IntOffset(100,124),position.calculatePosition(anchor,IntSize(360,640),LayoutDirection.Ltr,popup))
        assertEquals(IntOffset(124,124),position.calculatePosition(anchor,IntSize(360,640),LayoutDirection.Rtl,popup))
    }
    @Test fun aBottomAnchorPlacesTheMenuAboveAndSideEdgesStayInsideTheWindow() {
        val anchor=IntRect(340,610,360,630)
        assertEquals(IntOffset(256,558),position.calculatePosition(anchor,IntSize(360,640),LayoutDirection.Ltr,popup))
        assertEquals(IntOffset(8,124),position.calculatePosition(IntRect(0,100,20,120),IntSize(360,640),LayoutDirection.Rtl,popup))
    }
    @Test fun tinyWindowsAndOutOfWindowAnchorsDoNotProduceInvalidRanges() {
        for(window in listOf(IntSize(30,20),IntSize(100,50),IntSize(360,640))) {
            for(direction in LayoutDirection.entries) {
                val p=position.calculatePosition(IntRect(-100,-100,-20,-20),window,direction,popup)
                assertTrue(p.x>=0 && p.y>=0)
                assertTrue(p.x<=(window.width-popup.width).coerceAtLeast(0))
                assertTrue(p.y<=(window.height-popup.height).coerceAtLeast(0))
            }
        }
    }
}
