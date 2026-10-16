package com.example.youroom

import com.example.youroom.ui.*
import org.junit.Assert.*
import org.junit.Test

class SlotStitchTest {
    private fun border(width: Float=52f,height: Float=36f,scale: Float=1f)=
        slotStitchGeometry(width*scale,height*scale,2f*scale,3f*scale,6f*scale,7f*scale,4f*scale)

    @Test fun twoWideStitchesKeepThreeWhitespaceAndFourCompleteEqualArmCorners() {
        val result=border()
        assertEquals(4f,result.inset,0f)
        assertEquals(3f,result.inset-2f/2,0f)
        assertEquals(4,result.corners.size)
        assertTrue(result.corners.all { it.arm==6f && kotlin.math.abs(it.dx)==1f && kotlin.math.abs(it.dy)==1f })
        assertEquals(setOf(1f to 1f,-1f to 1f,-1f to -1f,1f to -1f),result.corners.map { it.dx to it.dy }.toSet())
    }

    @Test fun edgeDashesAreRemovedWhileCornersRemainIndependent() {
        for(width in listOf(32f,40f,52f,68f,100f,180f)) {
            val result=border(width)
            assertTrue(result.horizontal.isEmpty())
            assertTrue(result.vertical.isEmpty())
        }
    }

    @Test fun normalCellsUseOnlyTheFourCornerLs() {
        val result=border()
        assertTrue(result.horizontal.isEmpty());assertTrue(result.vertical.isEmpty())
    }

    @Test fun geometryScalesWithoutChangingItsCornerProportions() {
        val original=border()
        for(scale in listOf(1.25f,1.5f,2f,2.625f,3.5f)) {
            val scaled=border(scale=scale)
            assertTrue(scaled.horizontal.isEmpty());assertTrue(scaled.vertical.isEmpty())
            original.corners.zip(scaled.corners).forEach { (a,b) ->
                assertEquals(a.x*scale,b.x,.001f);assertEquals(a.y*scale,b.y,.001f);assertEquals(a.arm*scale,b.arm,.001f)
            }
        }
    }

    @Test fun insufficientSpaceDoesNotCreateOverlappingCornersOrNegativeDashes() {
        for(width in listOf(0f,5f,9f,10f,16f,24f)) {
            val result=border(width)
            assertTrue(result.horizontal.all { it.start>=0 && it.end>=it.start && it.end<=result.width })
            assertTrue(result.corners.all { it.arm*2<=minOf(result.width,result.height) })
        }
    }
}
