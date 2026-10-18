package com.example.youroom

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.youroom.data.DemoData
import com.example.youroom.domain.*
import com.example.youroom.ui.PlanningTargetStrip
import com.example.youroom.ui.theme.YOURoomTheme
import com.example.youroom.ui.theme.campusColorScheme
import java.time.*
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.tan
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlanningTimeMarkerUiTest {
    @get:Rule val compose=createComposeRule()
    private val data=DemoData.snapshot()
    private var campus by mutableStateOf(Campus.XITUCHENG)
    private var dark by mutableStateOf(false)
    private var rtl by mutableStateOf(false)
    private var minute by mutableIntStateOf(803)
    private fun render()=compose.setContent {
        CompositionLocalProvider(LocalLayoutDirection provides if(rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            YOURoomTheme(campus=campus,darkTheme=dark) {
                Surface { Column(Modifier.fillMaxWidth().padding(top=40.dp)) {
                    val clock=Clock.fixed(LocalDateTime.of(2026,10,16,minute/60,minute%60).atZone(SchoolZone).toInstant(),SchoolZone)
                    PlanningTargetStrip(data.slots,emptyList(),clock)
                } }
            }
        }
    }
    private fun assertTriangle() {
        val bar=compose.onNodeWithTag("planner_target_bar",true)
        val rect=bar.fetchSemanticsNode().boundsInRoot
        val lesson=compose.onNodeWithTag("planner_target_period_1",true).fetchSemanticsNode().boundsInRoot
        val pixels=bar.captureToImage().toPixelMap()
        val background=campusColorScheme(campus,dark).surface
        fun ink(x: Int,y: Int): Boolean {
            val p=pixels[x,y]
            return abs(p.red-background.red)+abs(p.green-background.green)+abs(p.blue-background.blue)>.2f
        }
        val barTop=(lesson.top-rect.top).roundToInt()
        val points=(0 until barTop).flatMap { y -> (0 until pixels.width).filter { x -> ink(x,y) }.map { x -> x to y } }
        assertTrue(points.isNotEmpty())
        val left=points.minOf { it.first };val right=points.maxOf { it.first }
        val top=points.minOf { it.second };val bottom=points.maxOf { it.second }
        val width=(right-left+1).toFloat();val height=(bottom-top+1).toFloat()
        assertEquals("50-degree apex, allowing the outline",1f/(2f*tan(Math.toRadians(25.0)).toFloat()),height/width,.15f)
        assertEquals("base is 1.5 times the previous marker plus the unchanged outline",with(compose.density) { (5.dp+1.dp).toPx() },width,2f)
        assertFalse("the triangle is hollow",ink(((left+right)/2f).roundToInt(),(top+height*.4f).roundToInt()))
        assertTrue("triangle floats above the bars",barTop-bottom>=with(compose.density) { 2.dp.toPx() }-1)
        val x=PlanningDisplay.xAt(minute.toFloat(),data.slots,
            pixels.width-with(compose.density) { 48.dp.toPx() },with(compose.density) { 3.dp.roundToPx().toFloat() })!!
        val expected=with(compose.density) { 24.dp.toPx() }+if(rtl) pixels.width-with(compose.density) { 48.dp.toPx() }-x else x
        assertEquals("apex still marks the exact minute",expected,(left+right+1)/2f,1f)
        val nearBase=points.count { it.second==top+1 }
        val nearTip=points.count { it.second==bottom-1 }
        assertTrue("downward apex, not an upward triangle",nearBase>nearTip)
        val barBottom=(lesson.bottom-rect.top).roundToInt()
        assertFalse("old vertical stem is gone",(barBottom until pixels.height).any { y ->
            (0 until pixels.width).any { x2 -> ink(x2,y) }
        })
    }
    @Test fun hollowFiftyDegreeTriangleFloatsAboveUnmovedBarsInBothThemesAndDirections() {
        render()
        for(target in Campus.entries) for(night in listOf(false,true)) for(reverse in listOf(false,true)) {
            compose.runOnIdle { campus=target;dark=night;rtl=reverse }
            compose.onNodeWithTag("planner_target_bar",true).assertHeightIsEqualTo(36.dp)
            assertTriangle()
        }
    }
    @Test fun beforeAndAfterTeachingHoursKeepTheFullTriangleAtTheCorrectBarEdge() {
        render()
        for(time in listOf(420,1320)) for(reverse in listOf(false,true)) {
            compose.runOnIdle { minute=time;rtl=reverse }
            assertTriangle()
        }
    }
}
