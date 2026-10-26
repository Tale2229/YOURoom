package com.example.youroom

import androidx.compose.ui.geometry.Offset
import com.example.youroom.domain.LeisureContent
import com.example.youroom.ui.*
import org.junit.Assert.*
import org.junit.Test

class PoetryPageFoldTest {
    @Test fun thumbArcsWorkInBothDirectionsWhileVerticalScrollingKeepsItsGesture() {
        assertEquals(1,poetrySwipeDirection(-44f,-35f,300f,48f))
        assertEquals(-1,poetrySwipeDirection(80f,-130f,300f,48f))
        assertEquals(0,poetrySwipeDirection(12f,-130f,300f,48f))
        assertEquals(0,poetrySwipeDirection(20f,0f,300f,48f))
        assertEquals(0,poetrySwipeDirection(100f,Float.NaN,300f,48f))
        assertEquals(PoetryDragIntent.PAGE,poetryDragIntent(-30f,-40f,12f))
        assertEquals(PoetryDragIntent.SCROLL,poetryDragIntent(4f,-40f,12f))
        assertEquals(PoetryDragIntent.UNDECIDED,poetryDragIntent(3f,4f,12f))
    }

    @Test fun foldStartsClosedAndEndsCompletelyOutsideEveryTestViewport() {
        for((width,height) in listOf(320f to 140f,390f to 380f,600f to 900f))
            for(direction in listOf(-1,1)) for(slope in listOf(-.65f,-.2f,.25f,.65f)) {
                val start=poetryPageFold(width,height,0f,slope,direction)
                assertEquals(4,start.front.size)
                val end=poetryPageFold(width,height,1f,slope,direction)
                assertTrue(end.front.isEmpty())
                val min=listOf(Offset.Zero,Offset(width,0f),Offset(width,height),Offset(0f,height))
                    .minOf { end.normal.x*it.x+end.normal.y*it.y }
                assertTrue(end.back.all { end.normal.x*it.x+end.normal.y*it.y<min })
            }
    }

    @Test fun creaseIsSharedAndReflectionDoesNotStretchThePageOrCreateInvalidVertices() {
        for(p in listOf(.01f,.2f,.5f,.8f,.99f)) for(direction in listOf(-1,1)) {
            val fold=poetryPageFold(390f,380f,p,.31f,direction)
            val a=Offset(40f,35f);val b=Offset(215f,120f)
            val aa=fold.reflect(fold.reflect(a))
            assertEquals(a.x,aa.x,.001f);assertEquals(a.y,aa.y,.001f)
            assertEquals((a-b).getDistance(),(fold.reflect(a)-fold.reflect(b)).getDistance(),.001f)
            for(point in fold.crease) {
                assertTrue((fold.reflect(point)-point).getDistance()<.01f)
                assertTrue(fold.back.any { (it-point).getDistance()<.01f })
            }
            assertTrue((fold.front+fold.back).all { it.x.isFinite() && it.y.isFinite() })
        }
    }

    @Test fun cancelledPreviewsDoNotAppendHistoryOrDiscardTheUnvisitedPoem() {
        val utility=PlannerUtilityState()
        val first=LeisureContent.items().first();utility.item=first
        val next=utility.previewReader(1)
        repeat(10) { assertEquals(next,utility.previewReader(1));assertEquals(first,utility.item) }
        utility.moveReader(1);assertEquals(next,utility.item)
        val future=utility.previewReader(1)
        assertEquals(first,utility.previewReader(-1))
        utility.moveReader(-1);assertEquals(first,utility.item)
        utility.moveReader(1);assertEquals(next,utility.item)
        utility.moveReader(1);assertEquals(future,utility.item)
    }
}
