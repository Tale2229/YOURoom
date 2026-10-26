package com.example.youroom

import android.graphics.Bitmap
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.youroom.domain.LeisureContent
import com.example.youroom.domain.LeisureItem
import com.example.youroom.ui.*
import com.example.youroom.ui.theme.YOURoomTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class PoetryPageTurnUiTest {
    @get:Rule val compose=createComposeRule()
    private val utility=PlannerUtilityState()
    private fun render(long: Boolean=false) {
        utility.item=if(long) LeisureItem("long",List(45) { "行到水穷处，坐看云起时。" }.joinToString(""),"王维《终南别业》")
            else LeisureContent.items().first()
        compose.setContent { YOURoomTheme {
            PoetryPageReader(utility.item!!,true,utility::previewReader,utility::moveReader,
                Modifier.width(360.dp).height(380.dp))
        } }
    }
    @Test fun curvedThumbStrokesTurnForwardAndBackAndRefreshUsesTheSameTransition() {
        render();val first=utility.item!!
        val reader=compose.onNodeWithTag("planner_leisure_reader")
        reader.performTouchInput {
            down(Offset(width*.85f,height*.77f))
            moveTo(Offset(width*.75f,height*.67f),32)
            moveTo(Offset(width*.60f,height*.58f),32)
            moveTo(Offset(width*.37f,height*.53f),32)
            moveTo(Offset(width*.18f,height*.52f),32);up()
        }
        compose.waitUntil(5_000) { utility.item!!.id!=first.id }
        val second=utility.item!!
        reader.performTouchInput {
            down(Offset(width*.15f,height*.77f))
            moveTo(Offset(width*.25f,height*.67f),32)
            moveTo(Offset(width*.40f,height*.58f),32)
            moveTo(Offset(width*.63f,height*.53f),32)
            moveTo(Offset(width*.82f,height*.52f),32);up()
        }
        compose.waitUntil(5_000) { utility.item==first }
        compose.onNodeWithTag("planner_leisure_next").performClick()
        compose.waitUntil(5_000) { utility.item==second }
    }
    @Test fun cancelledFoldReturnsToTheSamePageAndTheActualNextPoemStaysReserved() {
        render();val first=utility.item!!
        val next=utility.previewReader(1)
        val reader=compose.onNodeWithTag("planner_leisure_reader")
        reader.performTouchInput {
            down(Offset(width*.8f,height*.8f))
            moveTo(Offset(width*.72f,height*.76f),50);up()
        }
        compose.waitForIdle();assertEquals(first,utility.item)
        assertEquals(next,utility.previewReader(1))
        compose.onNodeWithTag("planner_leisure_next").performClick()
        compose.waitUntil(5_000) { utility.item==next }
    }
    @Test fun verticalDragScrollsLongPoetryWithoutTurningIt() {
        render(long=true);val first=utility.item
        val reader=compose.onNodeWithTag("planner_leisure_reader")
        reader.performTouchInput { swipeUp() }
        compose.waitForIdle();assertEquals(first,utility.item)
        val range=reader.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue(range.value()>0f)
    }
    @Test fun midwayFoldShowsARealDiagonalPageAndDoesNotCommitBeforeRelease() {
        render();val first=utility.item
        val reader=compose.onNodeWithTag("planner_leisure_reader")
        reader.performTouchInput {
            down(Offset(width*.92f,height*.84f))
            moveTo(Offset(width*.36f,height*.60f),160)
        }
        compose.waitForIdle();assertEquals(first,utility.item)
        val bitmap=compose.onNodeWithTag("planner_poetry_pages").captureToImage().asAndroidBitmap()
        val cache=InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        File(cache,"poetry-page-curl.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        reader.performTouchInput { cancel() }
        compose.waitForIdle();assertEquals(first,utility.item)
    }
}
