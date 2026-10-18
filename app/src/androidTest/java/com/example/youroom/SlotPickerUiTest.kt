package com.example.youroom

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import com.example.youroom.data.DemoData
import com.example.youroom.domain.*
import com.example.youroom.ui.SlotPicker
import com.example.youroom.ui.theme.YOURoomTheme
import com.example.youroom.ui.theme.campusColorScheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalTime
import java.io.File
import androidx.test.platform.app.InstrumentationRegistry

class SlotPickerUiTest {
    @get:Rule val compose = createComposeRule()
    private val snapshot = DemoData.snapshot()
    private val changes = mutableListOf<SearchFilters>()
    private var filters by mutableStateOf(SearchFilters(setOf(6)))
    private var campus by mutableStateOf(Campus.XITUCHENG)
    private var slots by mutableStateOf(snapshot.slots)
    private var fontScale by mutableFloatStateOf(1f)
    private var enabled by mutableStateOf(true)
    private var appearanceDark by mutableStateOf(false)

    private fun render(darkTheme: Boolean = false) {
        appearanceDark = darkTheme
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                YOURoomTheme(darkTheme = appearanceDark, campus = campus) {
                    Surface {
                        Column(Modifier.fillMaxWidth().padding(6.dp)) {
                            SlotPicker(campus, snapshot.queryDate, true, filters, slots,
                                { filters = it; changes += it }, enabled = enabled)
                            Spacer(Modifier.fillMaxWidth().height(1.dp).testTag("below_slots"))
                            Text(filters.selectedSlots.sorted().joinToString(), Modifier.testTag("committed_slots"))
                        }
                    }
                }
            }
        }
    }

    private fun point(number: Int): Offset {
        val grid = compose.onNodeWithTag("slot_grid").fetchSemanticsNode().boundsInRoot
        return compose.onNodeWithTag("slot_$number").fetchSemanticsNode().boundsInRoot.center - grid.topLeft
    }

    @Test fun unselectedPeriodsUseCampusSpecificSurfaceColors() {
        render()
        for (target in Campus.entries) {
            compose.runOnIdle { campus = target }
            val pixels = compose.onNodeWithTag("slot_face_1", useUnmergedTree = true).captureToImage().toPixelMap()
            assertEquals(campusColorScheme(target, false).surfaceContainer, pixels[1, 1])
            if (target == Campus.XITUCHENG) {
                assertEquals(androidx.compose.ui.graphics.Color(0xFFF4F0E7), pixels[1, 1])
                val selected = compose.onNodeWithTag("slot_face_6", useUnmergedTree = true).captureToImage().toPixelMap()
                assertEquals(campusColorScheme(target, false).primary, selected[1, 1])
            }
        }
    }

    @Test fun eachNumberedFaceHasInsetDashedStitchesThatInvertWhenSelectedInBothThemes() {
        render()
        for (dark in listOf(false,true)) for (target in Campus.entries) {
            compose.runOnIdle { campus=target;appearanceDark=dark }
            val colors=campusColorScheme(target,dark)
            for (number in 1..14) {
                val pixels=compose.onNodeWithTag("slot_face_$number",true).captureToImage().toPixelMap()
                val fill=if(number==6) colors.primary else colors.surfaceContainer
                assertEquals(fill,pixels[1,1]) // The stitch is inset, not the outside border.
                val stroke=with(compose.density) { .75.dp.toPx() }
                val y=with(compose.density) { 3.dp.roundToPx() }
                val inset=with(compose.density) { 6.dp.roundToPx() }
                val line=(inset until pixels.width-inset).map { x ->
                    val p=pixels[x,y]
                    kotlin.math.abs(p.red-fill.red)+kotlin.math.abs(p.green-fill.green)+kotlin.math.abs(p.blue-fill.blue)>.12f
                }
                assertTrue("period $number restores visible stitches",line.count { it }>2)
                assertTrue("period $number keeps spaces between stitches",line.count { !it }>2)
                val x=with(compose.density) { 8.dp.roundToPx() }
                val topInk=(0 until pixels.height/4).filter { row ->
                    val p=pixels[x,row]
                    kotlin.math.abs(p.red-fill.red)+kotlin.math.abs(p.green-fill.green)+kotlin.math.abs(p.blue-fill.blue)>.12f
                }
                assertEquals("outer blank margin remains 3dp",with(compose.density) { 3.dp.toPx() },topInk.first().toFloat(),1f)
                assertEquals("light stitch width is 0.75dp",stroke,topInk.size.toFloat(),1f)
                val ink=(inset until pixels.width-inset).first { x ->
                    val p=pixels[x,y]
                    kotlin.math.abs(p.red-fill.red)+kotlin.math.abs(p.green-fill.green)+kotlin.math.abs(p.blue-fill.blue)>.12f
                }
                val p=pixels[ink,y]
                val expected=if(number==6) colors.onPrimary else colors.outline
                fun luminance(c: androidx.compose.ui.graphics.Color)=c.red+c.green+c.blue
                assertEquals(luminance(expected)>luminance(fill),luminance(p)>luminance(fill))
                val old=(if(number==6) colors.onPrimary else colors.primary).copy(alpha=.85f).compositeOver(fill)
                fun contrast(c: androidx.compose.ui.graphics.Color)=kotlin.math.abs(c.red-fill.red)+
                    kotlin.math.abs(c.green-fill.green)+kotlin.math.abs(c.blue-fill.blue)
                assertTrue("softer stitch contrast than the former dark thread",contrast(p)<contrast(old))
                val fullInk=expected.copy(alpha=if(number==6) .35f else .36f).compositeOver(fill)
                assertTrue("scheme A limits thread opacity",contrast(p)<=contrast(fullInk)+.015f)
                fun isInk(x: Int,y: Int): Boolean {
                    val p=pixels[x,y]
                    return kotlin.math.abs(p.red-fill.red)+kotlin.math.abs(p.green-fill.green)+kotlin.math.abs(p.blue-fill.blue)>.12f
                }
                fun colorDistance(a: androidx.compose.ui.graphics.Color,b: androidx.compose.ui.graphics.Color)=
                    kotlin.math.abs(a.red-b.red)+kotlin.math.abs(a.green-b.green)+kotlin.math.abs(a.blue-b.blue)
                // Fractional stroke widths and screenshot cropping can put the
                // two antialiased edges one physical pixel apart. Geometry is
                // tested exactly on the host; here compare each mirrored pixel
                // with its immediate neighborhood rather than thresholding it.
                for (xx in 0 until pixels.width) {
                    val best=(-1..1).minOf { delta -> colorDistance(pixels[xx,y],
                        pixels[(pixels.width-1-xx+delta).coerceIn(0,pixels.width-1),y]) }
                    assertTrue("horizontal stitch mirror",best<.06f)
                }
                for (yy in 0 until pixels.height) {
                    val best=(-1..1).minOf { delta -> colorDistance(pixels[y,yy],
                        pixels[y,(pixels.height-1-yy+delta).coerceIn(0,pixels.height-1)]) }
                    assertTrue("vertical stitch mirror",best<.06f)
                }
                for (right in listOf(false,true)) for (bottom in listOf(false,true)) {
                    // Use the center pixel of the fractional-width corner path.
                    val center=with(compose.density) { 3.375.dp.toPx() }.toInt()
                    val cornerX=if(right) pixels.width-1-center else center
                    val cornerY=if(bottom) pixels.height-1-center else center
                    val dx=if(right) -1 else 1;val dy=if(bottom) -1 else 1
                    val horizontal=(0 until pixels.width/3).first { !isInk(cornerX+dx*it,cornerY) }
                    val vertical=(0 until pixels.height/3).first { !isInk(cornerX,cornerY+dy*it) }
                    assertEquals("each L has equal complete arms",horizontal.toFloat(),vertical.toFloat(),1f)
                    assertEquals(with(compose.density) { 6.dp.toPx() },horizontal.toFloat(),1f)
                }
            }
            if(!dark) {
                val instrumentation=InstrumentationRegistry.getInstrumentation()
                instrumentation.waitForIdleSync();android.os.SystemClock.sleep(150)
                File(instrumentation.targetContext.getExternalFilesDir(null),"slot-stitch-${target.dataKey}-1.16.5.png")
                    .outputStream().use {
                        instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
                    }
            }
        }
    }

    private fun hold(number: Int) {
        val position = point(number)
        compose.onNodeWithTag("slot_grid").performTouchInput { down(position) }
        compose.waitUntil(4_000) {
            compose.onAllNodesWithTag("slot_time_tooltip").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("slot_time_tooltip").assertIsDisplayed()
    }

    @Test fun longPressInspectsSelectedAndUnselectedPeriodsWithoutChangingFilters() {
        render()
        for (number in listOf(1, 6)) {
            hold(number)
            val slot = slots.single { it.number == number }
            compose.onNodeWithTag("slot_time_label_$number").assertTextEquals("${slot.number} · ${slot.timeLabel}")
            compose.onNodeWithTag("slot_1").assertIsNotSelected()
            compose.onNodeWithTag("slot_6").assertIsSelected()
            assertTrue(changes.isEmpty())
            compose.onNodeWithTag("slot_grid").performTouchInput { up() }
            compose.onNodeWithTag("slot_time_tooltip").assertDoesNotExist()
            assertEquals(setOf(6), filters.selectedSlots)
            assertTrue(changes.isEmpty())
        }
    }

    @Test fun longPressUsesResponseTimesAndCancellationRemovesTheHint() {
        slots = slots.map { if (it.number == 7) it.copy(start = LocalTime.of(14, 5), end = LocalTime.of(14, 50)) else it }
        render()
        hold(7)
        compose.onNodeWithTag("slot_time_label_7").assertTextEquals("7 · 14:05–14:50")
        compose.onNodeWithTag("slot_grid").performTouchInput { cancel() }
        compose.onNodeWithTag("slot_time_tooltip").assertDoesNotExist()
        assertEquals(setOf(6), filters.selectedSlots)
        assertTrue(changes.isEmpty())
    }

    @Test fun heldInspectionFollowsTheCurrentFaceWithoutSelectingOrSearching() {
        slots = slots.map { if (it.number == 7) it.copy(start = LocalTime.of(14, 5), end = LocalTime.of(14, 50)) else it }
        render()
        hold(1)
        for (number in listOf(6, 7, 14, 1)) {
            val target = point(number)
            compose.onNodeWithTag("slot_grid").performTouchInput { moveTo(target) }
            val slot = slots.single { it.number == number }
            compose.onNodeWithTag("slot_time_label_$number").assertTextEquals("${slot.number} · ${slot.timeLabel}")
            assertEquals(setOf(6), filters.selectedSlots)
            assertTrue(changes.isEmpty())
        }
        compose.onNodeWithTag("slot_grid").performTouchInput { up() }
        compose.onNodeWithTag("slot_time_tooltip").assertDoesNotExist()
        assertTrue(changes.isEmpty())
    }

    @Test fun narrowHorizontalGapsKeepTheHintButLeavingTheGridHidesItAndReturningResumesInspection() {
        render()
        hold(1)
        val popupId = compose.onNodeWithTag("slot_time_tooltip").fetchSemanticsNode().id
        val grid = compose.onNodeWithTag("slot_grid").fetchSemanticsNode().boundsInRoot
        val first = compose.onNodeWithTag("slot_face_1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("slot_face_2", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val gap = Offset((first.right + second.left) / 2, first.center.y) - grid.topLeft
        compose.onNodeWithTag("slot_grid").performTouchInput { moveTo(gap) }
        compose.onNodeWithTag("slot_time_tooltip").assertIsDisplayed()
        val search = compose.onNodeWithTag("room_search_frame", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center - grid.topLeft
        compose.onNodeWithTag("slot_grid").performTouchInput { moveTo(search) }
        compose.onNodeWithTag("slot_time_tooltip").assertDoesNotExist()
        val seventh = point(7)
        compose.onNodeWithTag("slot_grid").performTouchInput { moveTo(seventh) }
        compose.onNodeWithTag("slot_time_label_7").assertExists()
        assertEquals(popupId, compose.onNodeWithTag("slot_time_tooltip").fetchSemanticsNode().id)
        compose.onNodeWithTag("slot_grid").performTouchInput { cancel() }
        compose.onNodeWithTag("slot_time_tooltip").assertDoesNotExist()
        assertEquals(setOf(6), filters.selectedSlots)
        assertTrue(changes.isEmpty())
    }

    @Test fun heldMovementKeepsOnePopupAcrossGuttersAndRowsIncludingFastReversal() {
        render()
        hold(1)
        val id = compose.onNodeWithTag("slot_time_tooltip").fetchSemanticsNode().id
        for (number in listOf(2, 5, 6, 7, 12, 14, 13, 1, 6, 2)) {
            val target = point(number)
            compose.onNodeWithTag("slot_grid").performTouchInput { moveTo(target, delayMillis = 16) }
            compose.onNodeWithTag("slot_time_label_$number").assertExists()
            assertEquals(id, compose.onNodeWithTag("slot_time_tooltip").fetchSemanticsNode().id)
            assertEquals(1, compose.onAllNodesWithTag("slot_time_tooltip").fetchSemanticsNodes().size)
        }
        compose.onNodeWithTag("slot_grid").performTouchInput { up() }
        assertTrue(changes.isEmpty()); assertEquals(setOf(6), filters.selectedSlots)
    }

    @Test fun changingCampusAfterFollowingAnotherFaceEndsInspection() {
        render()
        hold(1)
        val last = point(14)
        compose.onNodeWithTag("slot_grid").performTouchInput { moveTo(last) }
        compose.onNodeWithTag("slot_time_label_14").assertExists()
        compose.runOnIdle { campus = Campus.SHAHE }
        compose.onNodeWithTag("slot_time_tooltip").assertDoesNotExist()
        compose.onNodeWithTag("slot_grid").performTouchInput { up() }
        assertEquals(setOf(6), filters.selectedSlots)
        assertTrue(changes.isEmpty())
    }

    @Test fun clearReplacesTheHeaderAndKeepsPeriodSizeAndSummaryHeight() {
        render()
        compose.onNodeWithTag("slot_header").assertDoesNotExist()
        compose.onNodeWithText("自习时段").assertDoesNotExist()
        compose.onNodeWithText("清空").assertDoesNotExist()
        val grid = compose.onNodeWithTag("slot_grid").fetchSemanticsNode().boundsInRoot
        val target = compose.onNodeWithTag("slot_1").fetchSemanticsNode().boundsInRoot
        val face = compose.onNodeWithTag("slot_face_1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(grid.top, target.top, 1f)
        assertTrue(target.height + 1f >= face.height * 48 / 36)
        val summary = compose.onNodeWithTag("slot_summary_space").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("clear_slots").performClick()
        assertEquals(summary, compose.onNodeWithTag("slot_summary_space").fetchSemanticsNode().boundsInRoot)
    }

    private fun verifyHintColors(dark: Boolean) {
        render(dark)
        for (target in Campus.entries) {
            compose.runOnIdle { campus = target }
            hold(1)
            val colors = campusColorScheme(target, dark)
            val pixels = compose.onNodeWithTag("slot_time_tooltip").captureToImage().toPixelMap()
            // The fill reaches the straight edge: no contrasting border remains.
            assertEquals(colors.background, pixels[pixels.width / 2, pixels.height / 8])
            assertEquals(colors.background, pixels[1, pixels.height / 2])
            val layout = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("slot_time_label_1").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
            assertEquals(colors.primary, layout.single().layoutInput.style.color)
            compose.onNodeWithTag("slot_grid").performTouchInput { up() }
        }
        assertTrue(changes.isEmpty())
    }

    @Test fun hintsUseBorderlessPageColoredFillAndCampusColoredDigits() = verifyHintColors(false)

    @Test fun hintContrastFollowsTheExistingDarkTheme() = verifyHintColors(true)

    @Test fun aLongDragStillPaintsAndDoesNotBecomeInspection() {
        render()
        val first = point(1)
        val third = point(3)
        compose.onNodeWithTag("slot_grid").performTouchInput { down(first); moveTo(third) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("slot_time_tooltip").assertDoesNotExist()
        compose.onNodeWithTag("slot_3").assertIsSelected()
        assertTrue(changes.isEmpty())
        compose.onNodeWithTag("slot_grid").performTouchInput { up() }
        assertEquals(1, changes.size)
        assertEquals(setOf(1, 2, 3, 6), filters.selectedSlots)
    }

    @Test fun changingCampusDismissesAHeldHintWithoutSelectionChanges() {
        render()
        hold(1)
        compose.runOnIdle { campus = Campus.SHAHE }
        compose.onNodeWithTag("slot_time_tooltip").assertDoesNotExist()
        compose.onNodeWithTag("slot_grid").performTouchInput { up() }
        assertEquals(setOf(6), filters.selectedSlots)
        assertTrue(changes.isEmpty())
    }

    @Test fun accessibleInspectionDoesNotToggleThePeriod() {
        render()
        compose.onNodeWithTag("slot_1").performSemanticsAction(SemanticsActions.OnLongClick) { assertTrue(it()) }
        compose.onNodeWithTag("slot_time_label_1").assertTextEquals("1 · 08:00–08:45")
        compose.onNodeWithTag("slot_1").assertIsNotSelected()
        assertTrue(changes.isEmpty())
        compose.mainClock.advanceTimeBy(2_100)
        compose.onNodeWithTag("slot_time_tooltip").assertDoesNotExist()
    }

    @Test fun summaryReservesOneLineAndDisjointTimesScrollWithoutMovingContent() {
        render()
        val initial = compose.onNodeWithTag("slot_summary_space").fetchSemanticsNode().boundsInRoot
        val below = compose.onNodeWithTag("below_slots").fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle { filters = filters.copy(selectedSlots = emptySet()) }
        compose.onNodeWithTag("slot_summary").assertDoesNotExist()
        assertEquals(initial, compose.onNodeWithTag("slot_summary_space").fetchSemanticsNode().boundsInRoot)
        assertEquals(below, compose.onNodeWithTag("below_slots").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.runOnIdle { filters = filters.copy(selectedSlots = (1..14 step 2).toSet()) }
        val summary = compose.onNodeWithTag("slot_summary")
        val layout = mutableListOf<TextLayoutResult>()
        summary.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
        assertEquals(1, layout.single().lineCount)
        assertTrue(summary.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].maxValue() > 0f)
        summary.performTouchInput { swipeLeft() }
        assertTrue(summary.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value() > 0f)
        assertEquals(initial, compose.onNodeWithTag("slot_summary_space").fetchSemanticsNode().boundsInRoot)
        assertEquals(below, compose.onNodeWithTag("below_slots").fetchSemanticsNode().boundsInRoot.top, 1f)
        assertTrue(changes.isEmpty())
    }

    @Test fun reservedSummaryHeightHonorsLargeFontsAndRemainsStableWhenCleared() {
        fontScale = 1.5f
        render()
        val selected = compose.onNodeWithTag("slot_summary_space").fetchSemanticsNode().boundsInRoot
        val text = compose.onNodeWithTag("slot_summary").fetchSemanticsNode().boundsInRoot
        assertTrue(selected.height + 1f >= text.height)
        compose.onNodeWithTag("clear_slots").performClick()
        assertEquals(selected, compose.onNodeWithTag("slot_summary_space").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun dragPreviewsEveryCellButCommitsOnlyOnceOnRelease() {
        render()
        val first = point(1)
        val third = point(3)
        val fifth = point(5)
        compose.onNodeWithTag("slot_grid").performTouchInput { down(first); moveTo(third) }
        compose.onNodeWithTag("slot_1").assertIsSelected()
        compose.onNodeWithTag("slot_2").assertIsSelected()
        compose.onNodeWithTag("slot_3").assertIsSelected()
        compose.onNodeWithTag("committed_slots").assertTextEquals("6")
        assertTrue(changes.isEmpty())
        compose.onNodeWithTag("slot_grid").performTouchInput { moveTo(fifth); moveTo(third) }
        compose.onNodeWithTag("slot_5").assertIsSelected()
        assertTrue(changes.isEmpty())
        compose.onNodeWithTag("slot_grid").performTouchInput { up() }
        assertEquals(1, changes.size)
        assertEquals((1..6).toSet(), changes.single().selectedSlots)
        compose.onNodeWithTag("committed_slots").assertTextEquals("1, 2, 3, 4, 5, 6")
    }

    @Test fun cancelDiscardsThePreviewWithoutAnySearchChange() {
        render()
        val first = point(1)
        val third = point(3)
        compose.onNodeWithTag("slot_grid").performTouchInput { down(first); moveTo(third) }
        compose.onNodeWithTag("slot_3").assertIsSelected()
        compose.onNodeWithTag("slot_grid").performTouchInput { cancel() }
        compose.onNodeWithTag("slot_1").assertIsNotSelected()
        compose.onNodeWithTag("slot_2").assertIsNotSelected()
        compose.onNodeWithTag("slot_3").assertIsNotSelected()
        compose.onNodeWithTag("slot_6").assertIsSelected()
        assertTrue(changes.isEmpty())
    }

    @Test fun dragRemovalCommitsOnceAndSemanticClicksStillCommitImmediately() {
        filters = SearchFilters(setOf(1, 2, 3, 6))
        render()
        val first = point(1)
        val third = point(3)
        compose.onNodeWithTag("slot_grid").performTouchInput { down(first); moveTo(third) }
        compose.onNodeWithTag("slot_1").assertIsNotSelected()
        compose.onNodeWithTag("committed_slots").assertTextEquals("1, 2, 3, 6")
        assertTrue(changes.isEmpty())
        compose.onNodeWithTag("slot_grid").performTouchInput { up() }
        assertEquals(1, changes.size)
        assertEquals(setOf(6), filters.selectedSlots)
        compose.onNodeWithTag("slot_1").performClick()
        assertEquals(2, changes.size)
        assertEquals(setOf(1, 6), filters.selectedSlots)
        compose.onNodeWithTag("slot_grid").performTouchInput { down(first); up() }
        assertEquals(3, changes.size)
        assertEquals(setOf(6), filters.selectedSlots)
    }

    @Test fun changingContextDiscardsAHeldPreviewWithoutCommittingIntoTheNewCampus() {
        render()
        val first = point(1)
        val third = point(3)
        compose.onNodeWithTag("slot_grid").performTouchInput { down(first); moveTo(third) }
        compose.runOnIdle { campus = Campus.SHAHE }
        compose.onNodeWithTag("slot_1").assertIsNotSelected()
        compose.onNodeWithTag("slot_6").assertIsSelected()
        compose.onNodeWithTag("slot_grid").performTouchInput { up() }
        assertTrue(changes.isEmpty())
        assertEquals(setOf(6), filters.selectedSlots)
    }

    @Test fun numericFacesHaveSquareCornersAndTimeMarkersAreAbsent() {
        render()
        for (number in listOf(1, 6, 7, 12, 13, 14)) {
            compose.onNodeWithTag("time_marker_$number").assertDoesNotExist()
            compose.onNodeWithTag("time_marker_line_$number", useUnmergedTree = true).assertDoesNotExist()
            assertSquareCorners(compose.onNodeWithTag("slot_face_$number", useUnmergedTree = true))
        }
    }

    @Test fun inlineClearOnlyChangesTheQueryAndDisappearsWhenEmpty() {
        filters = filters.copy(query = "201", buildings = setOf("2"), favoritesOnly = true, tags = setOf("人少"))
        val original = filters
        render()
        val frame = compose.onNodeWithTag("room_search_frame").fetchSemanticsNode().boundsInRoot
        val clear = compose.onNodeWithTag("clear_room_search")
        val button = clear.fetchSemanticsNode().boundsInRoot
        assertTrue(frame.contains(button.center))
        assertTrue(button.center.x > frame.center.x)
        clear.assertContentDescriptionEquals("清空教室搜索").performTouchInput { click() }
        assertEquals(listOf(original.copy(query = "")), changes)
        assertEquals(original.copy(query = ""), filters)
        compose.onNodeWithTag("clear_room_search").assertDoesNotExist()
        compose.onNodeWithTag("slot_6").assertIsSelected()
    }

    @Test fun trashIsASquareCorneredPeriodSizedRectangleUnderNineBetweenFourteenAndSearch() {
        render()
        val clear = compose.onNodeWithTag("clear_slots").assertContentDescriptionEquals("清空所选时段")
        val hit = clear.fetchSemanticsNode().boundsInRoot
        val face = compose.onNodeWithTag("clear_slots_face", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val ninth = compose.onNodeWithTag("slot_face_9", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val last = compose.onNodeWithTag("slot_face_14", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val search = compose.onNodeWithTag("room_search_frame", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(ninth.center.x, face.center.x, 1f)
        assertEquals(last.top, face.top, 1f)
        assertEquals(last.width, face.width, 1f)
        assertEquals(last.height, face.height, 1f)
        assertTrue(face.left > last.right)
        assertTrue(face.right < search.left)
        assertTrue(hit.height + 1f >= face.height * 48 / 36)
        val pixels = compose.onNodeWithTag("clear_slots_face", useUnmergedTree = true).captureToImage().toPixelMap()
        assertEquals(campusColorScheme(campus,false).surface,pixels[3,3])
    }

    @Test fun trashClearsOnlySelectedSlotsAndIsDisabledWhenEmptyOrPageInactive() {
        filters = filters.copy(query = "201", buildings = setOf("2"), favoritesOnly = true, tags = setOf("人少"))
        val original = filters
        render()
        compose.onNodeWithTag("clear_slots").assertIsEnabled().performTouchInput { click() }
        assertEquals(listOf(original.copy(selectedSlots = emptySet())), changes)
        assertEquals(original.copy(selectedSlots = emptySet()), filters)
        compose.onNodeWithTag("clear_slots").assertIsNotEnabled().performTouchInput { click() }
        assertEquals(1, changes.size)
        compose.runOnIdle { filters = original; enabled = false }
        compose.onNodeWithTag("clear_slots").assertIsNotEnabled().performTouchInput { click() }
        assertEquals(original, filters)
        assertEquals(1, changes.size)
    }

    private fun trashMask(): List<Boolean> {
        val background = campusColorScheme(campus, false).surface
        val pixels = compose.onNodeWithTag("clear_slots_icon", useUnmergedTree = true).captureToImage().toPixelMap()
        return (0 until pixels.height).flatMap { y -> (0 until pixels.width).map { x ->
            val pixel = pixels[x,y]
            kotlin.math.abs(pixel.red - background.red) + kotlin.math.abs(pixel.green - background.green) +
                kotlin.math.abs(pixel.blue - background.blue) > .4f
        } }
    }

    @Test fun lidOpensAndClosesThroughIntermediateGeometryAndRapidReversalUsesLatestSelection() {
        filters = filters.copy(selectedSlots = emptySet())
        render()
        val closed = trashMask()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { filters = filters.copy(selectedSlots = setOf(1)) }
        compose.mainClock.advanceTimeBy(96)
        val opening = trashMask()
        compose.mainClock.advanceTimeBy(240)
        val open = trashMask()
        assertNotEquals(closed, opening)
        assertNotEquals(opening, open)
        assertNotEquals(closed, open)
        // The stationary lower body keeps its density, rather than moving with the lid.
        val lower = closed.size * 13 / 27
        assertEquals(closed.drop(lower).count { it }, open.drop(lower).count { it })
        compose.runOnIdle { filters = filters.copy(selectedSlots = emptySet()) }
        compose.mainClock.advanceTimeBy(96)
        val closing = trashMask()
        assertNotEquals(open, closing)
        assertNotEquals(closed, closing)
        compose.runOnIdle { filters = filters.copy(selectedSlots = setOf(2)) }
        compose.mainClock.advanceTimeBy(48)
        compose.runOnIdle { filters = filters.copy(selectedSlots = emptySet()) }
        compose.mainClock.advanceTimeBy(300)
        assertEquals(closed, trashMask())
        compose.onNodeWithTag("clear_slots").assertIsNotEnabled()
        assertTrue(changes.isEmpty())
    }

    @Test fun trashFollowsUncommittedDragPreviewAndCancellationWithoutLaunchingExtraSearches() {
        filters = filters.copy(selectedSlots = emptySet())
        render()
        compose.onNodeWithTag("clear_slots").assertIsNotEnabled()
        val first = point(1)
        val third = point(3)
        compose.onNodeWithTag("slot_grid").performTouchInput { down(first); moveTo(third) }
        compose.onNodeWithTag("clear_slots").assertIsEnabled()
        assertTrue(changes.isEmpty())
        compose.onNodeWithTag("slot_grid").performTouchInput { cancel() }
        compose.onNodeWithTag("clear_slots").assertIsNotEnabled()
        assertTrue(changes.isEmpty())
        compose.onNodeWithTag("slot_grid").performTouchInput { down(first); moveTo(third); up() }
        assertEquals(1, changes.size)
        val grid = compose.onNodeWithTag("slot_grid").fetchSemanticsNode().boundsInRoot
        val clear = compose.onNodeWithTag("clear_slots").fetchSemanticsNode().boundsInRoot.center - grid.topLeft
        compose.onNodeWithTag("slot_grid").performTouchInput { down(first); moveTo(clear) }
        assertEquals(1, changes.size)
        compose.onNodeWithTag("slot_grid").performTouchInput { up() }
        assertEquals(2, changes.size)
    }

    @Test fun trashRetainsCampusSpecificColorsInLightAndDarkThemes() {
        render()
        for (dark in listOf(false,true)) for (target in Campus.entries) {
            compose.runOnIdle { campus = target; appearanceDark = dark }
            val pixels = compose.onNodeWithTag("clear_slots_face", useUnmergedTree = true).captureToImage().toPixelMap()
            assertEquals(campusColorScheme(target,dark).surface,pixels[3,3])
            val ink = compose.onNodeWithTag("clear_slots_icon", useUnmergedTree = true).captureToImage().toPixelMap()
            assertEquals(campusColorScheme(target,dark).primary,ink[ink.width/2,ink.height*19/27])
            compose.onNodeWithTag("clear_slots").assertIsEnabled()
        }
    }
}
