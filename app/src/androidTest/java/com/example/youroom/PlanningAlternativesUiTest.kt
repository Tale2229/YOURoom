package com.example.youroom

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.example.youroom.data.DemoData
import com.example.youroom.domain.*
import com.example.youroom.ui.*
import com.example.youroom.ui.theme.YouRoomTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlanningAlternativesUiTest {
    @get:Rule val compose=createComposeRule()
    private val data=DemoData.snapshot()
    private var state by mutableStateOf(YouRoomState(initializing=false,demo=true,snapshot=data,today=data.queryDate))
    private val goal=PlanningDraft(flexible=false,savedWindows=listOf(StudyWindow(480,600)))
    private val options=data.classrooms.take(6).mapIndexed { i,room ->
        PlanningOption("方案$i",StudyPlan(StudyWindow(480,600),listOf(PlanningStep.Study(480,600,room,i==0)),
            120,if (i==0) 120 else 0,if (i==0) 1 else 0,0,0,0,0,0,i))
    }
    private val result=PlanningResult(options=options,groups=PlanningDirection.entries.map {
        PlanningGroup(it,if (it==PlanningDirection.EXPECTED) emptyList() else if (it==PlanningDirection.BALANCED) listOf(0,1,2,3,4) else listOf(1,2,3,4,5),
            if (it==PlanningDirection.EXPECTED) "尚未选择期望教室" else null)
    })
    private var execution by mutableStateOf(PlanningExecution(1,PlanningInput(data,Campus.XITUCHENG,data.queryDate,emptyMap(),
        PlanningSchedule.confirmedGoal(goal)!!),result))
    private var draft by mutableStateOf(goal)
    private var generates=0
    private val shahe=DemoData.snapshot(Campus.SHAHE)
    private var shaheExecution by mutableStateOf(PlanningExecution(1,PlanningInput(shahe,Campus.SHAHE,shahe.queryDate,emptyMap(),
        PlanningSchedule.confirmedGoal(goal)!!),result.copy(options=options.mapIndexed { index,option ->
            option.copy(plan=option.plan.copy(steps=listOf(PlanningStep.Study(480,600,shahe.classrooms[index],false))))
        })))
    private fun render(rtl: Boolean=false)=compose.setContent {
        CompositionLocalProvider(LocalLayoutDirection provides if(rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) { YouRoomTheme {
        StudyPlanningScreen(state,draft,mapOf(Campus.XITUCHENG to execution,Campus.SHAHE to shaheExecution),{draft=it},{generates++},{},{},{},
            { campus,id,index -> if (campus==Campus.XITUCHENG && id==execution.id) execution=execution.copy(selectedOption=index)
                else if (campus==Campus.SHAHE && id==shaheExecution.id) shaheExecution=shaheExecution.copy(selectedOption=index) })
    } } }
    private fun open() {
        compose.onNodeWithTag("planner_alternatives").performClick(); compose.waitForIdle()
    }
    private fun shot(name: String) {
        // Wait for the Android render thread too, not just Compose's test clock.
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(200)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null),"$name.png").outputStream().use {
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                .compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }

    @Test fun onlyTheBandWidthShrinksWhileOriginalAKeyTypographyAndSpacingStayUnchanged() {
        var paletteCampus by mutableStateOf(Campus.XITUCHENG)
        var nightMode by mutableStateOf(false)
        // Test the workspace directly: CampusFrame deliberately follows the
        // system appearance rather than an outer test theme's dark override.
        compose.setContent { YouRoomTheme(campus=paletteCampus,darkTheme=nightMode) { Surface {
            PlanningWorkspace(execution,paletteCampus,true,{_,_->}) { Spacer(Modifier.fillMaxSize()) }
        } } }
        for(campus in Campus.entries) for(night in listOf(false,true)) {
            compose.runOnIdle { paletteCampus=campus;nightMode=night }
            val heading=compose.onNodeWithTag("planner_alternatives")
            val bounds=heading.fetchSemanticsNode().boundsInRoot
            val workspace=compose.onNodeWithTag("planner_workspace").fetchSemanticsNode().boundsInRoot
            assertEquals(workspace.left,bounds.left,.5f);assertEquals(workspace.right,bounds.right,.5f)
            compose.onNodeWithTag("planner_heading_spacing",true).assertHeightIsEqualTo(12.dp)
            assertEquals(with(compose.density) { 12.dp.toPx() },bounds.top-workspace.top,.5f)
            val face=compose.onNodeWithTag("planner_alternatives_face",true)
                .assertHeightIsEqualTo(48.dp).fetchSemanticsNode().boundsInRoot
            assertEquals(with(compose.density) { 16.dp.toPx() },face.left-bounds.left,.5f)
            assertEquals(with(compose.density) { 16.dp.toPx() },bounds.right-face.right,.5f)
            val pixels=compose.onNodeWithTag("planner_alternatives_face",true).captureToImage().toPixelMap()
            val color=com.example.youroom.ui.theme.campusColorScheme(campus,night).surfaceContainerLow
            assertEquals(color,pixels[2,pixels.height/2])
            heading.assertHeightIsAtLeast(48.dp)
            val key=compose.onNodeWithTag("planner_alternatives_key",true)
                .assertWidthIsEqualTo(3.dp).assertHeightIsEqualTo(22.dp).fetchSemanticsNode().boundsInRoot
            assertEquals(bounds.left+with(compose.density) { 24.dp.toPx() },key.left,.5f)
            val title=compose.onNodeWithTag("planner_alternatives_title",true).fetchSemanticsNode().boundsInRoot
            assertEquals(with(compose.density) { 9.dp.toPx() },title.left-key.right,.5f)
            compose.onNodeWithTag("planner_alternatives_separator",true).assertWidthIsEqualTo(1.dp).assertHeightIsEqualTo(12.dp)
            compose.onNodeWithTag("planner_alternatives_count",true).assertTextEquals("01 / 05")
            compose.onNodeWithTag("planner_alternatives_arrow",true).assertWidthIsEqualTo(15.dp).assertHeightIsEqualTo(15.dp)
            for((tag,size) in listOf("title" to 13f,"count" to 10f,"entry" to 11f)) {
                val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                compose.onNodeWithTag("planner_alternatives_$tag",true)
                    .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertEquals(size,layouts.single().layoutInput.style.fontSize.value,0f)
            }
        }
        assertEquals(0,generates)
    }

    @Test fun changingDirectionsOnlyBrowsesAndChoosingAnAlternativeUpdatesTheMainRoute() {
        render();open()
        compose.onNodeWithTag("planner_direction_CHANGES").performClick()
        compose.waitForIdle()
        assertEquals(0,execution.selectedOption)
        compose.onNodeWithTag("planner_alternative_CHANGES_0").performClick()
        compose.waitForIdle()
        assertEquals(1,execution.selectedOption)
        assertEquals(0,generates)
        compose.onNodeWithTag("planner_alternatives").assertTextContains("换教室最少")
        compose.onNodeWithTag("planner_alternatives_count",true).assertTextEquals("01 / 05")
        compose.onNodeWithTag("planner_list").performScrollToNode(hasTestTag("planner_step_0"))
        compose.onNodeWithTag("planner_step_0").assert(hasAnyDescendant(hasText(options[1].plan.steps.filterIsInstance<PlanningStep.Study>().first().classroom.name)))
        open()
        compose.onNodeWithTag("planner_direction_CHANGES").assertIsSelected()
        compose.onNodeWithTag("planner_alternative_CHANGES_0").assertIsSelected()
    }

    @Test fun compactHeadingKeepsLongDirectionsAndPageCountSeparateOnNarrowLargeTextAndRtlLayouts() {
        var width by mutableStateOf(320.dp)
        var scale by mutableFloatStateOf(1f)
        var reverse by mutableStateOf(false)
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,scale),
                LocalLayoutDirection provides if(reverse) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                YouRoomTheme { Surface { Box(Modifier.width(width).height(360.dp)) {
                    PlanningWorkspace(execution,Campus.XITUCHENG,true,{_,index->execution=execution.copy(selectedOption=index)}) {
                        Spacer(Modifier.fillMaxSize())
                    }
                } } }
            }
        }
        open()
        compose.onNodeWithTag("planner_direction_FAVORITES").performScrollTo().performClick()
        compose.onNodeWithTag("planner_alternative_FAVORITES_0").performClick()
        for(size in listOf(280.dp,320.dp)) for(font in listOf(1f,1.5f)) for(rtl in listOf(false,true)) {
            compose.runOnIdle { width=size;scale=font;reverse=rtl }
            compose.onNodeWithTag("planner_alternatives").assertHeightIsAtLeast(48.dp)
            compose.onNodeWithTag("planner_alternatives_title",true).assertTextEquals("收藏教室覆盖最多")
            compose.onNodeWithTag("planner_alternatives_count",true).assertTextEquals("01 / 05")
            val face=compose.onNodeWithTag("planner_alternatives_face",true).fetchSemanticsNode().boundsInRoot
            val tags=listOf("title","count","entry","arrow")
            val bounds=tags.map { compose.onNodeWithTag("planner_alternatives_$it",true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot }
            bounds.forEach { assertTrue(it.left>=face.left && it.right<=face.right && it.top>=face.top && it.bottom<=face.bottom) }
            bounds.forEachIndexed { i,a -> bounds.drop(i+1).forEach { b -> assertFalse("heading fields cannot overlap at $size / $font / RTL=$rtl",a.overlaps(b)) } }
            for(tag in listOf("count","entry")) {
                val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                compose.onNodeWithTag("planner_alternatives_$tag",true)
                    .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val layout=layouts.single()
                // Paragraph layout can retain unused horizontal space when a
                // short label takes its intrinsic width. Check the actual ink
                // positions, not that spare paragraph width.
                assertEquals(1,layout.lineCount)
                assertFalse(layout.isLineEllipsized(0))
                for(char in layout.layoutInput.text.indices) {
                    val glyph=layout.getBoundingBox(char)
                    assertTrue("$tag glyph $char cannot clip at $size / $font / RTL=$rtl: $glyph in ${layout.size}",
                        glyph.left>=-.5f && glyph.right<=layout.size.width+.5f && glyph.top>=-.5f && glyph.bottom<=layout.size.height+.5f)
                }
            }
            if(size==280.dp && font==1.5f && !rtl) shot("heading-a-narrow-large-1.17.4")
        }
        assertEquals(0,generates)
    }

    @Test fun backingOutDoesNotSelectOrGenerateAndHeaderStaysAboveTheDrawer() {
        render()
        val header=compose.onNodeWithTag("campus_header").fetchSemanticsNode().boundsInRoot
        open()
        val after=compose.onNodeWithTag("campus_header").fetchSemanticsNode().boundsInRoot
        val drawer=compose.onNodeWithTag("planner_drawer").fetchSemanticsNode().boundsInRoot
        assertEquals(header,after); assertTrue(drawer.top>=after.bottom)
        compose.onNodeWithTag("planner_direction_FAVORITES").performClick()
        Espresso.pressBack();compose.waitForIdle()
        assertEquals(0,execution.selectedOption);assertEquals(0,generates)
        compose.onNodeWithTag("planner_alternatives").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"已收起"))
    }

    @Test fun expectedDirectionUsesTheGeneratedInputNotLiveEditorChanges() {
        render()
        compose.runOnIdle { draft=draft.copy(expectedRooms=setOf(data.classrooms.first().id)) }
        open()
        compose.onNodeWithTag("planner_direction_EXPECTED").assertIsNotEnabled()
        assertEquals(0,execution.selectedOption);assertEquals(0,generates)
    }

    @Test fun drawerShowsAtMostFiveSummariesAndKeepsTheLastOneReachable() {
        render()
        val header=compose.onNodeWithTag("campus_header").fetchSemanticsNode().boundsInRoot
        open()
        compose.onNodeWithTag("planner_alternative_list").performScrollToIndex(4)
        compose.onNodeWithTag("planner_alternative_BALANCED_4").assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals(4,execution.selectedOption)
        open();shot("planner-alternatives")
        Espresso.pressBack();compose.waitForIdle()
        compose.onNodeWithTag("planner_list").performScrollToNode(hasTestTag("planner_step_0"))
        compose.waitForIdle()
        assertEquals(header,compose.onNodeWithTag("campus_header").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("YouRoom").assertIsDisplayed()
        shot("planner-route")
    }

    @Test fun scrollbarOnlyAppearsForOverflowAndItsThumbFollowsTheSummaryList() {
        render();open()
        val track=compose.onNodeWithTag("planner_alternative_scrollbar",true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("planner_alternative_divider",true).assertDoesNotExist()
        val start=compose.onNodeWithTag("planner_alternative_scroll_thumb",true).fetchSemanticsNode().boundsInRoot
        assertEquals(track.left,start.left,1f)
        assertTrue(start.width<track.width)
        compose.onNodeWithTag("planner_alternative_list").performScrollToIndex(4)
        val end=compose.onNodeWithTag("planner_alternative_scroll_thumb",true).fetchSemanticsNode().boundsInRoot
        assertTrue(end.left>start.left)
        assertEquals(track.right,end.right,1f)
        assertEquals(0,execution.selectedOption);assertEquals(0,generates)
        shot("planner-scrollbar-1.16.4")
        compose.runOnIdle {
            val single=listOf(PlanningGroup(PlanningDirection.BALANCED,listOf(0)))
            execution=execution.copy(result=result.copy(groups=single))
        }
        compose.onNodeWithTag("planner_alternative_scrollbar",true).assertDoesNotExist()
        compose.onNodeWithTag("planner_alternative_divider",true).assertIsDisplayed()
        compose.onNodeWithTag("planner_alternative_BALANCED_0").assertIsDisplayed()
    }

    @Test fun scrollbarMirrorsItsLogicalStartAndEndInRtl() {
        render(rtl=true);open()
        val track=compose.onNodeWithTag("planner_alternative_scrollbar",true).fetchSemanticsNode().boundsInRoot
        val start=compose.onNodeWithTag("planner_alternative_scroll_thumb",true).fetchSemanticsNode().boundsInRoot
        assertEquals(track.right,start.right,1f)
        compose.onNodeWithTag("planner_alternative_list").performScrollToIndex(4)
        val end=compose.onNodeWithTag("planner_alternative_scroll_thumb",true).fetchSemanticsNode().boundsInRoot
        assertTrue(end.left<start.left);assertEquals(track.left,end.left,1f)
        assertEquals(0,execution.selectedOption);assertEquals(0,generates)
    }

    @Test fun disclosureFlipsVerticallyThroughACompressedMidpointAndReversesWithoutSpinning() {
        render()
        val arrow=compose.onNodeWithTag("planner_alternatives_arrow",true)
        val original=arrow.captureToImage().toPixelMap()
        val initial=arrow.fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance=false
        compose.onNodeWithTag("planner_alternatives").performClick()
        compose.mainClock.advanceTimeBy(96)
        val middle=arrow.fetchSemanticsNode().boundsInRoot
        assertEquals(initial.center.x,middle.center.x,1f)
        assertEquals(initial.width,middle.width,1f)
        assertTrue("a flip flattens vertically instead of rotating sideways",middle.height<initial.height*.85f)
        compose.mainClock.advanceTimeBy(700)
        val flipped=arrow.captureToImage().toPixelMap()
        assertEquals(original.width,flipped.width);assertEquals(original.height,flipped.height)
        fun distance(a: androidx.compose.ui.graphics.Color,b: androidx.compose.ui.graphics.Color)=
            kotlin.math.abs(a.red-b.red)+kotlin.math.abs(a.green-b.green)+kotlin.math.abs(a.blue-b.blue)
        for(x in 0 until original.width) for(y in 0 until original.height) {
            assertTrue("expanded arrow mirrors the original without a horizontal turn",
                distance(original[x,y],flipped[x,original.height-1-y])<.025f)
        }
        compose.onNodeWithTag("planner_alternatives").performClick()
        compose.mainClock.advanceTimeBy(48)
        compose.onNodeWithTag("planner_alternatives").performClick()
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("planner_alternatives").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"已展开"))
        val restored=arrow.captureToImage().toPixelMap()
        for(x in 0 until flipped.width) for(y in 0 until flipped.height)
            assertTrue(distance(flipped[x,y],restored[x,y])<.025f)
        assertEquals(0,generates);assertEquals(0,execution.selectedOption)
        compose.mainClock.autoAdvance=true
    }

    @Test fun switchingBetweenScrollableAndSingleOptionDirectionsUsesExactlyOneFooterCue() {
        val groups=listOf(PlanningGroup(PlanningDirection.BALANCED,listOf(0,1,2,3,4)),
            PlanningGroup(PlanningDirection.CHANGES,listOf(1)))
        execution=execution.copy(result=result.copy(groups=groups))
        render();open()
        compose.onNodeWithTag("planner_alternative_scrollbar",true).assertIsDisplayed()
        compose.onNodeWithTag("planner_alternative_divider",true).assertDoesNotExist()
        val top=compose.onNodeWithTag("planner_list").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("planner_direction_CHANGES").performClick()
        compose.onNodeWithTag("planner_alternative_scrollbar",true).assertDoesNotExist()
        compose.onNodeWithTag("planner_alternative_divider",true).assertIsDisplayed()
        assertEquals(top,compose.onNodeWithTag("planner_list").fetchSemanticsNode().boundsInRoot.top,1f)
        compose.onNodeWithTag("planner_direction_BALANCED").performClick()
        compose.onNodeWithTag("planner_alternative_scrollbar",true).assertIsDisplayed()
        compose.onNodeWithTag("planner_alternative_divider",true).assertDoesNotExist()
        shot("planner-footer-and-arrow-1.17.1")
    }

    @Test fun newGenerationResetsComparisonButOrdinaryEditsKeepIt() {
        render();open()
        compose.onNodeWithTag("planner_direction_BUILDINGS").performClick()
        compose.onNodeWithTag("planner_alternative_BUILDINGS_1").performClick()
        compose.waitForIdle()
        compose.runOnIdle { draft=draft.copy(flexible=true) }
        open();compose.onNodeWithTag("planner_direction_BUILDINGS").assertIsSelected()
        Espresso.pressBack();compose.waitForIdle()
        compose.runOnIdle { execution=execution.copy(id=2,selectedOption=0) }
        open();compose.onNodeWithTag("planner_direction_BALANCED").assertIsSelected()
        assertEquals(0,execution.selectedOption); assertEquals(0,generates)
    }

    @Test fun eachCampusKeepsItsOwnDirectionAndSelectedPlan() {
        render();open()
        compose.onNodeWithTag("planner_direction_BUILDINGS").performClick()
        compose.onNodeWithTag("planner_alternative_BUILDINGS_1").performClick()
        compose.waitForIdle()
        compose.runOnIdle { state=state.copy(campus=Campus.SHAHE,snapshot=shahe) }
        compose.waitForIdle();open()
        compose.onNodeWithTag("planner_direction_BALANCED").assertIsSelected()
        compose.onNodeWithTag("planner_direction_FAVORITES").performClick()
        compose.onNodeWithTag("planner_alternative_FAVORITES_0").performClick()
        compose.waitForIdle()
        assertEquals(2,execution.selectedOption);assertEquals(1,shaheExecution.selectedOption)
        compose.runOnIdle { state=state.copy(campus=Campus.XITUCHENG,snapshot=data) }
        compose.waitForIdle();open()
        compose.onNodeWithTag("planner_direction_BUILDINGS").assertIsSelected()
        compose.onNodeWithTag("planner_alternative_BUILDINGS_1").assertIsSelected()
        assertEquals(0,generates)
    }
}
