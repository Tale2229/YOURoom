package com.example.youroom

import com.example.youroom.ui.themeAvatarPixelSquare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class ThemeAvatarGeometryTest {
    @Test fun frameOpeningAndArtworkShareExactlyTheSamePixelSquare() {
        for (density in listOf(1f,1.5f,2f,2.625f,2.75f,3f,3.5f,4f)) {
            val frame = (95*density).roundToInt()
            val art = (75*density).roundToInt()
            val opening = themeAvatarPixelSquare(frame,frame,frame,art)
            for (width in frame..frame+7) {
                val square = themeAvatarPixelSquare(width,frame,frame,art)
                val frameLeft = ((width-frame)/2f).roundToInt()
                assertEquals(frameLeft+opening.left,square.left)
                assertEquals(opening.top,square.top)
                assertEquals(art,square.edge)
                assertTrue(square.left>=frameLeft)
                assertTrue(square.left+square.edge<=frameLeft+frame)
            }
        }
    }

    @Test fun fractionalDpDoesNotLeaveARightEdgeGap() {
        val square = themeAvatarPixelSquare(843,249,249,197)
        assertEquals(323,square.left)
        assertEquals(26,square.top)
        assertEquals(520,square.left+square.edge)
    }
}
