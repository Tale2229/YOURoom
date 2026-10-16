package com.example.youroom

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.youroom.data.DemoData
import com.example.youroom.domain.*
import com.example.youroom.ui.*
import com.example.youroom.ui.theme.YouRoomTheme
import java.time.Clock
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlanningRangeOrderingUiTest {
    @get:Rule val compose=createComposeRule()
    private val data=DemoData.snapshot()
    private val room=data.classrooms.first()
    private var windows by mutableStateOf(listOf(StudyWindow(840,900),StudyWindow(900,960),StudyWindow(960,1020)))
    private var fontScale by mutableFloatStateOf(1f)
    private var campus by mutableStateOf(Campus.XITUCHENG)
    private var dark by mutableStateOf(false)
    private var rtl by mutableStateOf(false)
    private var edited=-1
    private fun label(index: Int,target: Boolean)=compose.onNodeWithTag(
        if(target) "planner_target_range_$index" else "planner_range_${room.id}_$index",true)
    private fun render()=compose.setContent {
        val density=LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density,fontScale),
            LocalLayoutDirection provides if(rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            YouRoomTheme(campus=campus,darkTheme=dark) {
                Surface { Column(Modifier.fillMaxWidth().padding(top=40.dp)) {
                    PlanningTargetStrip(data.slots,windows,Clock.system(SchoolZone),
                        actions=PlanningRangeActions(true,{edited=it},{}))
                    Spacer(Modifier.height(30.dp))
                    // Room use merges touching intervals by design; keep one-minute gaps here.
                    PlanningRoomStrip(PlanningRoomUse(room,false,windows.map { it.copy(end=it.end-1) }),
                        data.slots,setOf(1,2,6,7,9,14),true,1)
                } }
            }
        }
    }
    private fun shot(name: String) {
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync();android.os.SystemClock.sleep(150)
        File(instrumentation.targetContext.getExternalFilesDir(null),"$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }
    @Test fun consecutiveAfternoonTargetsUseFirstRowForTheFirstAndThirdAndKeepTimeOrder() {
        render()
        for(target in listOf(true,false)) {
            val labels=windows.indices.map { label(it,target).fetchSemanticsNode().boundsInRoot }
            assertEquals(labels[0].top,labels[2].top,1f)
            assertTrue(if(target) labels[1].top>labels[0].bottom else labels[1].bottom<labels[0].top)
            assertTrue(labels.zipWithNext().all { (a,b) -> a.center.x<b.center.x })
            labels.forEachIndexed { i,a -> labels.drop(i+1).forEach { b -> assertFalse(a.overlaps(b)) } }
        }
        shot("planner-range-consecutive-1.15.2")
    }
    @Test fun additionOrderDoesNotChangeGeometryOrWhichPeriodTheMenuEdits() {
        windows=listOf(windows[2],windows[0],windows[1]);render()
        assertTrue(label(1,true).fetchSemanticsNode().boundsInRoot.center.x<label(2,true).fetchSemanticsNode().boundsInRoot.center.x)
        assertTrue(label(2,true).fetchSemanticsNode().boundsInRoot.center.x<label(0,true).fetchSemanticsNode().boundsInRoot.center.x)
        label(0,true).assertTextEquals("16:00–17:00").performClick()
        compose.onNodeWithTag("planner_edit_period_0").performClick()
        assertEquals(0,edited);assertEquals(StudyWindow(960,1020),windows[edited])
    }
    @Test fun consecutiveLunchPeriodsKeepCompactConnectorsInBothDirections() {
        windows=listOf(StudyWindow(733,793),StudyWindow(793,853),StudyWindow(853,913));render()
        for(target in listOf(true,false)) {
            val labels=windows.indices.map { label(it,target).fetchSemanticsNode().boundsInRoot }
            assertTrue(labels.zipWithNext().all { (a,b) -> a.center.x<=b.center.x })
            labels.forEachIndexed { i,a -> labels.drop(i+1).forEach { b -> assertFalse(a.overlaps(b)) } }
            label(0,target).assertTextEquals(if(target) "12:13–13:13" else "12:13–13:12")
        }
        shot("planner-range-centered-lunch-1.15.5")
    }
    @Test fun adjacentHalfHoursShiftTheSecondTimeToItsVerticalColumnInBothStrips() {
        windows=listOf(StudyWindow(840,870),StudyWindow(870,900));render()
        for(target in listOf(true,false)) {
            val first=label(0,target).fetchSemanticsNode().boundsInRoot
            val second=label(1,target).fetchSemanticsNode().boundsInRoot
            assertTrue(second.center.x>=first.right+with(compose.density) { 4.dp.toPx() }-1f)
            assertTrue(if(target) second.top>first.bottom else second.bottom<first.top)
            label(1,target).assertTextEquals(if(target) "14:30–15:00" else "14:30–14:59")
        }
        shot("planner-half-hours-no-return-1.15.5")
    }
    @Test fun largerFontsThemesAndRtlKeepLabelsOrderedInBoundsAndOnTheirOwnSideOfTheBars() {
        render()
        for(scale in listOf(1f,1.5f)) for(reverse in listOf(false,true)) for(targetCampus in Campus.entries) {
            compose.runOnIdle { fontScale=scale;rtl=reverse;campus=targetCampus;dark=reverse }
            for(target in listOf(true,false)) {
                val all=compose.onNodeWithTag(if(target) "planner_target_strip" else "planner_room_strip_${room.id}",true)
                    .fetchSemanticsNode().boundsInRoot
                val labels=windows.indices.map { label(it,target).fetchSemanticsNode().boundsInRoot }
                labels.forEach { assertTrue(it.left>=all.left+with(compose.density){24.dp.toPx()}-1f)
                    assertTrue(it.right<=all.right-with(compose.density){24.dp.toPx()}+1f) }
                assertTrue(labels.zipWithNext().all { (a,b) -> if(reverse) a.center.x>=b.center.x else a.center.x<=b.center.x })
                labels.forEachIndexed { i,a -> labels.drop(i+1).forEach { b -> assertFalse(a.overlaps(b)) } }
                label(0,target).assertTextEquals(if(target) "14:00–15:00" else "14:00–14:59")
                label(1,target).assertTextEquals(if(target) "15:00–16:00" else "15:00–15:59")
                label(2,target).assertTextEquals(if(target) "16:00–17:00" else "16:00–16:59")
            }
        }
        shot("planner-range-ordered-large-rtl-1.15.2")
    }
}
