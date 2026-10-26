package com.example.youroom

import com.example.youroom.ui.*
import org.junit.Assert.*
import org.junit.Test

class SlotStitchTest {
    private fun border(width: Float=52f,height: Float=36f,scale: Float=1f)=
        slotStitchGeometry(width*scale,height*scale,.75f*scale,3f*scale,6f*scale,7f*scale,4f*scale)

    @Test fun lightStitchesKeepThreeWhitespaceAndFourCompleteEqualArmCorners() {
        val result=border()
        assertEquals(3.375f,result.inset,0f)
        assertEquals(3f,result.inset-.75f/2,0f)
        assertEquals(4,result.corners.size)
        assertTrue(result.corners.all { it.arm==6f && kotlin.math.abs(it.dx)==1f && kotlin.math.abs(it.dy)==1f })
        assertEquals(setOf(1f to 1f,-1f to 1f,-1f to -1f,1f to -1f),result.corners.map { it.dx to it.dy }.toSet())
    }

    @Test fun edgeDashesAreSymmetricAndSeparatedFromCompleteCorners() {
        for(width in listOf(32f,40f,52f,68f,100f,180f)) {
            val result=border(width)
            for((length,dashes) in listOf(result.width to result.horizontal,result.height to result.vertical)) {
                dashes.zip(dashes.reversed()).forEach { (a,b) ->
                    assertEquals(length-a.start,b.end,.001f)
                    assertEquals(length-a.end,b.start,.001f)
                    assertEquals(7f,a.end-a.start,.001f)
                }
                if(dashes.isNotEmpty()) {
                    val arm=result.corners.first().arm
                    val gaps=listOf(dashes.first().start-arm)+dashes.zipWithNext().map { (a,b) -> b.start-a.end }+
                        (length-arm-dashes.last().end)
                    assertTrue(gaps.all { it>=3.999f })
                    gaps.forEach { assertEquals(gaps.first(),it,.001f) }
                }
            }
        }
    }

    @Test fun normalCellsRestoreTheIndependentHorizontalAndVerticalDashes() {
        val result=border()
        assertEquals(2,result.horizontal.size);assertEquals(1,result.vertical.size)
    }

    @Test fun geometryScalesWithoutChangingItsCornerProportions() {
        val original=border()
        for(scale in listOf(1.25f,1.5f,2f,2.625f,3.5f)) {
            val scaled=border(scale=scale)
            assertEquals(original.horizontal.size,scaled.horizontal.size)
            assertEquals(original.vertical.size,scaled.vertical.size)
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
