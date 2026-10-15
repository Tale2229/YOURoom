package com.example.youroom

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.youroom.data.DemoData
import com.example.youroom.domain.*
import com.example.youroom.ui.PlanningRoomStrip
import com.example.youroom.ui.theme.YouRoomTheme
import com.example.youroom.ui.theme.campusColorScheme
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlanningRangeUiTest {
    @get:Rule val compose = createComposeRule()
    private val data = DemoData.snapshot()
    private val room = data.classrooms.first()
    private var windows by mutableStateOf(listOf(StudyWindow(530, 575)))
    private var fontScale by mutableFloatStateOf(1f)
    private var rtl by mutableStateOf(false)
    private var campus by mutableStateOf(Campus.XITUCHENG)
    private var dark by mutableStateOf(false)
    private val lines get() = compose.onNodeWithTag("planner_range_lines_${room.id}", true)
    private fun label(index: Int) = compose.onNodeWithTag("planner_range_${room.id}_$index", true)
    private fun bar(number: Int) = compose.onNodeWithTag("room_period_${room.id}_$number", true)
    private fun px(value: Float) = with(compose.density) { value.dp.toPx() }
    private fun render() = compose.setContent {
        val base = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale),
            LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            YouRoomTheme(campus = campus, darkTheme = dark) {
                Surface { Column(Modifier.fillMaxWidth().padding(top = 40.dp)) {
                    PlanningRoomStrip(PlanningRoomUse(room, false, windows), data.slots,
                        setOf(1, 2, 4, 5, 7, 14), true, 1)
                } }
            }
        }
    }
    private fun ink(pixels: PixelMap, x: Float, y: Float): Boolean {
        val background = campusColorScheme(campus, dark).surface
        for (dx in -1..1) for (dy in -1..1) {
            val xx = x.roundToInt() + dx; val yy = y.roundToInt() + dy
            if (xx !in 0 until pixels.width || yy !in 0 until pixels.height) continue
            val p = pixels[xx, yy]
            if (p.alpha > .4f && abs(p.red-background.red)+abs(p.green-background.green)+abs(p.blue-background.blue) > .2f) return true
        }
        return false
    }
    private fun state(index: Int, expected: String) = label(index).assert(
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, expected))
    private fun assertNoOutsideInk(pixels: PixelMap) {
        val margin = px(22f).toInt()
        for (y in 0 until pixels.height) for (x in 0 until margin) {
            assertFalse("outside start ($x,$y)", ink(pixels, x.toFloat(), y.toFloat()))
            assertFalse("outside end ($x,$y)", ink(pixels, (pixels.width-1-x).toFloat(), y.toFloat()))
        }
    }
    private fun shot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(150)
        File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun timesAndDownwardCapsAreAboveTheOriginalFourteenBars() {
        render()
        label(0).assertTextEquals("08:50–09:35")
        state(0, "起点、终点封闭")
        val caption = label(0).fetchSemanticsNode().boundsInRoot
        val face = bar(2).fetchSemanticsNode().boundsInRoot
        val canvas = lines.fetchSemanticsNode().boundsInRoot
        assertTrue(caption.bottom < face.top)
        assertEquals(px(5f), face.height, 1f)
        val pixels = lines.captureToImage().toPixelMap()
        val start = face.left-canvas.left
        val join = face.top-canvas.top-px(10f)
        assertTrue("caps point downward", ink(pixels, start, join+px(3f)))
        assertFalse("no upward caps", ink(pixels, start, join-px(3f)))
        for (n in 1..14) bar(n).assertExists()
    }

    @Test fun crowdedTimesUseDistinctRowsAndKeepCompleteTextAtLargeFontSizes() {
        windows = listOf(StudyWindow(660, 680), StudyWindow(695, 710), StudyWindow(715, 725))
        render()
        for (scale in listOf(1f, 1.5f)) {
            compose.runOnIdle { fontScale = scale }
            val labels = windows.indices.map { label(it).fetchSemanticsNode().boundsInRoot }
            labels.forEachIndexed { i, a -> labels.drop(i+1).forEach { b -> assertFalse(a.overlaps(b)) } }
            val all = compose.onNodeWithTag("planner_room_strip_${room.id}", true).fetchSemanticsNode().boundsInRoot
            labels.forEach { assertTrue(it.left >= all.left+px(24f)-1 && it.right <= all.right-px(24f)+1) }
            label(0).assertTextEquals("11:00–11:20")
            label(1).assertTextEquals("11:35–11:50")
            label(2).assertTextEquals("11:55–12:05")
            assertTrue(labels.map { it.top }.distinct().size > 1)
            assertTrue(labels.all { it.bottom < bar(1).fetchSemanticsNode().boundsInRoot.top })
        }
        shot("planner-range-crowded")
    }

    @Test fun earlyAndLateWindowsHaveNoOutsideDashesArrowsOrBoundaryCaps() {
        windows = listOf(StudyWindow(440, 540), StudyWindow(1190, 1320))
        render()
        state(0, "起点开放"); state(1, "终点开放")
        label(0).assertTextEquals("07:20–09:00"); label(1).assertTextEquals("19:50–22:00")
        val pixels = lines.captureToImage().toPixelMap()
        val canvas = lines.fetchSemanticsNode().boundsInRoot
        val first = bar(1).fetchSemanticsNode().boundsInRoot
        val last = bar(14).fetchSemanticsNode().boundsInRoot
        val capY = first.top-canvas.top-px(7f)
        assertFalse(ink(pixels, first.left-canvas.left, capY))
        assertFalse(ink(pixels, last.right-canvas.left, capY))
        assertNoOutsideInk(pixels)
        shot("planner-range-open")
    }

    @Test fun completelyOutsideWindowsKeepLabelsButDrawNoFakeLessonBracket() {
        windows = listOf(StudyWindow(410, 450), StudyWindow(1270, 1320))
        render()
        state(0, "全部位于课前"); state(1, "全部位于课后")
        label(0).assertTextEquals("06:50–07:30"); label(1).assertTextEquals("21:10–22:00")
        val pixels = lines.captureToImage().toPixelMap()
        val captionBottom = windows.indices.maxOf { label(it).fetchSemanticsNode().boundsInRoot.bottom }
        val from = (captionBottom-lines.fetchSemanticsNode().boundsInRoot.top+px(2f)).roundToInt()
        assertFalse((from until pixels.height-1).any { y -> (0 until pixels.width).any { x -> ink(pixels, x.toFloat(), y.toFloat()) } })
    }

    @Test fun rightToLeftMirrorsTheRangesWithoutChangingTheirTimesOrOpenness() {
        windows = listOf(StudyWindow(440, 540)); rtl = true
        render()
        state(0, "起点开放"); label(0).assertTextEquals("07:20–09:00")
        assertTrue(bar(1).fetchSemanticsNode().boundsInRoot.left > bar(14).fetchSemanticsNode().boundsInRoot.right)
        val caption = label(0).fetchSemanticsNode().boundsInRoot
        assertTrue(caption.center.x > lines.fetchSemanticsNode().boundsInRoot.center.x)
        assertNoOutsideInk(lines.captureToImage().toPixelMap())
    }

    @Test fun bothCampusThemesAndDarkModeRetainTheAvailabilityColorsAndRangeText() {
        render()
        for (target in Campus.entries) for (night in listOf(false, true)) {
            compose.runOnIdle { campus = target; dark = night }
            val colors = campusColorScheme(target, night)
            assertEquals(colors.primary, bar(2).captureToImage().toPixelMap()[2, 2])
            assertEquals(colors.outlineVariant, bar(3).captureToImage().toPixelMap()[2, 2])
            label(0).assertTextEquals("08:50–09:35")
        }
    }

    @Test fun holdingTheActualStripStillInspectsTimeAndDoesNotResizeTheRow() {
        render()
        val strip = compose.onNodeWithTag("room_period_strip_${room.id}", true)
        val before = compose.onNodeWithTag("planner_room_strip_${room.id}", true).fetchSemanticsNode().boundsInRoot
        val face = bar(6).fetchSemanticsNode().boundsInRoot
        val point = face.center-strip.fetchSemanticsNode().boundsInRoot.topLeft
        strip.performTouchInput { down(point) }
        compose.waitUntil(4_000) { compose.onAllNodesWithTag("room_time_${room.id}_tooltip", true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("room_time_${room.id}_label_6", true).assertTextEquals("6 · 13:00–13:45")
        assertEquals(before, compose.onNodeWithTag("planner_room_strip_${room.id}", true).fetchSemanticsNode().boundsInRoot)
        strip.performTouchInput { up() }
    }
}
