package com.example.youroom

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import com.example.youroom.domain.*
import com.example.youroom.ui.*
import com.example.youroom.ui.theme.YOURoomTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GuideUtilityRefinementUiTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var guide: GuideRuntime
    private fun render(stage: GuideStage) {
        compose.setContent { YOURoomTheme {
            UsageGuideExperience(GuideRequest("utility-test",stage.ordinal,serial=1),YOURoomState(),
                onRuntime={guide=it}) { _,_ -> }
        } }
        compose.waitUntil(15_000) { ::guide.isInitialized && !guide.preparing && guide.ready }
    }
    private fun visit(stage: GuideStage) {
        compose.runOnIdle { guide.select(stage.ordinal) }
        compose.waitUntil(15_000) { guide.stage==stage && !guide.preparing && guide.ready }
        compose.waitForIdle()
    }
    @Test fun entryAdvancesDirectlyAndReaderSupportsBothSwipeDirectionsWithinOneOpening() {
        render(GuideStage.WAIT_CONTENT)
        compose.onNodeWithTag("planner_leisure").performTouchInput { click() }
        compose.waitUntil(15_000) { guide.stage==GuideStage.READER && !guide.preparing && guide.ready }
        assertEquals(listOf("planner_utility_panel"),guide.spotlightTargets)
        assertEquals("verse-1",guide.utilities.item!!.id)
        compose.onNodeWithTag("planner_poetry_body").assertTextEquals("一年好景君须记，\n最是橙黄橘绿时。")
        compose.onNodeWithTag("planner_poetry_source").assertTextEquals("苏轼《赠刘景文》")
        for (removed in listOf("使用小技巧","主题小札","轻轻放空","诗文一隅","且看眼前","换一则"))
            compose.onNodeWithText(removed).assertDoesNotExist()
        compose.onNodeWithContentDescription("刷新按钮").assertIsDisplayed()
        val image=compose.onNodeWithTag("planner_utility_panel").captureToImage().asAndroidBitmap()
        val cache=InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        File(cache,"poetry-reader.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
        compose.onNodeWithText("想一想").assertDoesNotExist()
        compose.onNodeWithText("看看答案").assertDoesNotExist()
        compose.onNodeWithTag("planner_game_switch").assertIsNotEnabled()
        compose.onNodeWithTag("planner_utility_close").assertIsNotEnabled()
        compose.onNodeWithTag("guide_instruction").assertTextEquals("这里会随机展示诗文，点击刷新按钮或左右滑动来切换")
        val panel=guide.anchor(guide.target)!!.bounds
        val heading=compose.onNodeWithTag("planner_utility_heading").fetchSemanticsNode().boundsInRoot
        val next=compose.onNodeWithTag("planner_leisure_next").fetchSemanticsNode().boundsInRoot
        assertTrue(panel.top<=heading.top && panel.bottom>=next.bottom)
        compose.onNodeWithTag("planner_leisure_next").performClick()
        compose.waitForIdle();val first=guide.utilities.item!!.id
        compose.onNodeWithTag("planner_leisure_reader").performTouchInput { swipeLeft() }
        compose.waitUntil(5_000) { guide.utilities.item!!.id!=first }
        compose.onNodeWithTag("planner_leisure_reader").performTouchInput { swipeRight() }
        compose.waitUntil(5_000) { guide.utilities.item!!.id==first }
        compose.onNodeWithTag("guide_demonstrate").performClick()
        compose.waitUntil(5_000) { guide.done }
        compose.onNodeWithText("看看答案").assertDoesNotExist()
    }
    @Test fun gameHasOneFooterInclusiveOpeningAndCompletionArrivesAfterThePanelMoves() {
        render(GuideStage.DINO)
        compose.onNodeWithTag("planner_game_switch").performTouchInput { click() }
        compose.waitUntil(10_000) { guide.entry==1 && guide.ready }
        assertEquals(listOf("planner_game_area"),guide.spotlightTargets)
        val game=guide.anchor(guide.target)!!.bounds
        val book=compose.onNodeWithTag("planner_reader_switch").fetchSemanticsNode().boundsInRoot
        val footer=compose.onNodeWithTag("planner_utility_footer").fetchSemanticsNode().boundsInRoot
        assertTrue(game.top>=book.bottom && game.left<=footer.left && game.right>=footer.right)
        val phase=guide.utilities.runner.phase
        compose.onNodeWithTag("planner_leisure").assertIsNotEnabled().performTouchInput { click() }
        assertEquals(PlannerPanel.LEISURE,guide.utilities.panel)
        assertEquals(phase,guide.utilities.runner.phase)
        val panelBefore=compose.onNodeWithTag("guide_panel").fetchSemanticsNode().boundsInRoot.top
        compose.mainClock.autoAdvance=false
        compose.runOnIdle { guide.select(GuideStage.WAIT_RESULT.ordinal) }
        repeat(30) { if(guide.preparing) { compose.mainClock.advanceTimeByFrame();compose.waitForIdle() } }
        compose.runOnIdle { assertFalse(guide.preparing);assertTrue(guide.completionOpening) }
        compose.mainClock.advanceTimeBy(320)
        compose.runOnIdle { assertTrue(guide.model.planning.executions.value.getValue(Campus.XITUCHENG).pending) }
        assertTrue(compose.onNodeWithTag("guide_panel").fetchSemanticsNode().boundsInRoot.top<panelBefore)
        compose.mainClock.autoAdvance=true
        compose.waitUntil(10_000) { guide.entry>0 }
        compose.onNodeWithTag("planner_message").assertTextEquals("计算完成")
        assertEquals(listOf("planner_wait_area"),guide.spotlightTargets)
        compose.onNodeWithTag("planner_leisure").assertIsNotEnabled()
        compose.onNodeWithTag("planner_view_result").performTouchInput { click() }
        compose.waitUntil(10_000) { guide.stage==GuideStage.RESULTS && !guide.preparing }
    }
    @Test fun historyAndReplayHaveSingleFullOpeningsAndReplayGuideReturnsToBottom() {
        render(GuideStage.HISTORY)
        compose.onNodeWithTag("planner_history").performTouchInput { click() }
        compose.waitUntil(10_000) { guide.entry>0 && guide.ready }
        assertEquals(listOf("planner_utility_panel"),guide.spotlightTargets)
        val heading=compose.onNodeWithTag("planner_utility_heading").fetchSemanticsNode().boundsInRoot
        assertTrue(guide.anchor(guide.target)!!.bounds.top<=heading.top)
        visit(GuideStage.HISTORY_REPLAY)
        compose.onNodeWithTag("history_guide-current").performTouchInput { click() }
        compose.waitUntil(10_000) { guide.entry>0 && guide.ready }
        compose.waitForIdle()
        assertEquals(listOf("planner_history_workspace"),guide.spotlightTargets)
        val opening=guide.anchor(guide.target)!!.bounds
        val strip=compose.onNodeWithTag("planner_target_strip").fetchSemanticsNode().boundsInRoot
        val room=compose.onNodeWithTag("planner_room_01:2-201").fetchSemanticsNode().boundsInRoot
        assertTrue(opening.top<=strip.top && opening.bottom==room.bottom)
        val panel=compose.onNodeWithTag("guide_panel").fetchSemanticsNode().boundsInRoot
        val scrim=compose.onNodeWithTag("guide_scrim").fetchSemanticsNode().boundsInRoot
        assertTrue(scrim.bottom-panel.bottom<=with(compose.density) { 32.dp.toPx() })
    }
}
