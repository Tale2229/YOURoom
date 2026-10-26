package com.example.youroom

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.Rect
import com.example.youroom.domain.*
import com.example.youroom.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GuideUtilityRefinementTest {
    private fun check(stage: GuideStage,block: (GuideRuntime)->Unit)=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime=GuideRuntime(mutableIntStateOf(stage.ordinal),AppearancePreferences(),PeriodStripPreferences())
        try { runCurrent();block(runtime) }
        finally { runtime.close();runCurrent();Dispatchers.resetMain() }
    }
    private fun anchor(runtime: GuideRuntime,id: String,rect: Rect) {
        runtime.anchors[id]=GuideAnchor("root",rect,Any())
    }
    @Test fun openingLeisureImmediatelyAdvancesToTheSingleReaderOpening()=check(GuideStage.WAIT_CONTENT) { guide ->
        guide.preparedStage=GuideStage.WAIT_CONTENT
        anchor(guide,"planner_leisure",Rect(250f,400f,300f,448f))
        guide.finishPreparation(guide.stage)
        guide.openedUtility(PlannerPanel.LEISURE)
        assertEquals(GuideStage.READER,guide.stage)
        assertEquals(listOf("planner_utility_panel"),guide.spotlightTargets)
    }
    @Test fun gameAndWaitOpeningsAreSingleRectanglesWithoutHeaderOrFooterProtrusions()=check(GuideStage.DINO) { guide ->
        anchor(guide,"planner_dinosaur",Rect(16f,200f,284f,400f))
        anchor(guide,"planner_utility_footer",Rect(0f,400f,300f,448f))
        anchor(guide,"planner_feedback_slot",Rect(0f,100f,300f,150f))
        guide.choseGame(true)
        assertEquals(listOf("planner_game_area"),guide.spotlightTargets)
        assertEquals(Rect(0f,200f,300f,448f),guide.anchor(guide.target)!!.bounds)
        guide.select(GuideStage.WAIT_RESULT.ordinal)
        guide.preparedStage=guide.stage
        assertEquals(listOf("planner_wait_area"),guide.spotlightTargets)
        assertEquals(Rect(0f,200f,300f,448f),guide.anchor(guide.target)!!.bounds)
        guide.finishPreparation(guide.stage)
        assertTrue(guide.completionOpening)
        assertEquals(Rect(0f,100f,300f,448f),guide.anchor(guide.target)!!.bounds)
    }
    @Test fun historyUsesOneHeaderInclusiveOpeningAndReplayDoesNotIncludeTheFooter()=check(GuideStage.HISTORY) { guide ->
        guide.openedUtility(PlannerPanel.HISTORY)
        assertEquals(listOf("planner_utility_panel"),guide.spotlightTargets)
        guide.select(GuideStage.HISTORY_REPLAY.ordinal)
        anchor(guide,"planner_target_strip",Rect(24f,100f,276f,140f))
        anchor(guide,"planner_workspace",Rect(0f,140f,300f,400f))
        anchor(guide,"planner_room_01:2-201",Rect(0f,200f,300f,280f))
        guide.replayedHistory()
        assertEquals(listOf("planner_history_workspace"),guide.spotlightTargets)
        assertEquals(Rect(0f,100f,300f,280f),guide.anchor(guide.target)!!.bounds)
    }
    @Test fun horizontalSwipesHaveThresholdsAndForwardBackPreserveTheVisitedContent() {
        assertEquals(0,leisureSwipeDirection(20f,300f,48f))
        assertEquals(1,leisureSwipeDirection(-100f,300f,48f))
        assertEquals(-1,leisureSwipeDirection(100f,300f,48f))
        assertEquals(0,leisureSwipeDirection(Float.NaN,300f,48f))
        val utility=PlannerUtilityState()
        val first=LeisureContent.items().first()
        utility.item=first;utility.moveReader(1)
        val second=utility.item!!
        assertNotEquals(first.id,second.id)
        utility.moveReader(-1);assertEquals(first,utility.item)
        utility.moveReader(1);assertEquals(second,utility.item)
    }
}
