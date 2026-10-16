package com.example.youroom

import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import com.example.youroom.data.DemoData
import com.example.youroom.domain.*
import com.example.youroom.ui.*
import com.example.youroom.ui.theme.YouRoomTheme
import java.io.File
import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlanningControlUiTest {
    @get:Rule val compose=createComposeRule()
    private val clock=Clock.fixed(Instant.parse("2026-10-15T23:00:00Z"),SchoolZone)
    private val data=DemoData.snapshot(clock=clock).copy(isDemo=false)
    private var campus by mutableStateOf(Campus.XITUCHENG)
    private var goal by mutableStateOf(PlanningDraft(840,900,savedWindows=listOf(StudyWindow(480,600),StudyWindow(780,840)),
        expectedRooms=data.classrooms.take(2).map { it.id }.toSet()))
    private val gate=CompletableDeferred<Unit>()
    private val calls=AtomicInteger(0)
    private lateinit var session: PlanningSession
    private fun render()=compose.setContent {
        val scope=rememberCoroutineScope()
        val holder=remember { PlanningSession(scope) { input ->
            calls.incrementAndGet();gate.await()
            PlanningResult(options=listOf(PlanningOption("综合",StudyPlan(StudyWindow(480,600),
                listOf(PlanningStep.Study(480,600,input.snapshot.classrooms.first(),false)),120,0,0,0,0,0,0,0,0))))
        } }
        SideEffect { session=holder }
        val executions by holder.executions.collectAsState()
        val snapshot=if (campus==Campus.XITUCHENG) data else DemoData.snapshot(campus,clock).copy(isDemo=false)
        val state=YouRoomState(initializing=false,loggedIn=true,campus=campus,snapshot=snapshot,today=data.queryDate)
        YouRoomTheme(campus=campus) { StudyPlanningScreen(state,goal,executions,{goal=it},{holder.generate(state,goal)},
            {campus=it},{},{},clock=clock,onCancel={ holder.cancel(campus) }) }
    }
    private fun shot(name: String) {
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync();android.os.SystemClock.sleep(150)
        File(instrumentation.targetContext.getExternalFilesDir(null),"$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }
    @Test fun generateBecomesAnEnabledStopAndCancellationPreservesAllSelectionsForRetry() {
        render()
        val original=goal
        compose.onNodeWithTag("planner_generate").assertContentDescriptionEquals("生成规划").performClick()
        compose.waitUntil(10_000) { calls.get()==1 }
        compose.onNodeWithTag("planner_generate").assertContentDescriptionEquals("终止规划").assertIsEnabled()
        shot("planner-stop-1.15")
        compose.onNodeWithTag("planner_generate").performClick()
        compose.onNodeWithTag("planner_generate").assertContentDescriptionEquals("生成规划").assertIsEnabled()
        assertEquals(original,goal)
        assertTrue(session.executions.value.getValue(campus).cancelled)
        compose.onNodeWithTag("planner_expected_badge").assertTextEquals("2")
        compose.onNodeWithTag("planner_target_range_0",true).assertTextEquals("08:00–10:00")
        compose.onNodeWithTag("planner_target_range_1",true).assertTextEquals("13:00–14:00")
        gate.complete(Unit)
        compose.onNodeWithTag("planner_generate").performClick()
        compose.waitUntil(10_000) { session.executions.value[campus]?.pending==false }
        assertEquals(2,calls.get())
        assertNotNull(session.executions.value.getValue(campus).result)
        assertEquals(original,goal)
        shot("planner-fixed-1.15")
    }
    @Test fun switchingCampusesNeverCancelsOrGeneratesForTheOtherCampus() {
        render()
        compose.onNodeWithTag("planner_generate").performClick()
        compose.waitUntil(10_000) { calls.get()==1 }
        compose.onNodeWithTag("campus_swipe_region").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("planner_generate").assertContentDescriptionEquals("生成规划")
        assertTrue(session.executions.value.getValue(Campus.XITUCHENG).pending)
        compose.onNodeWithTag("planner_generate").performClick()
        compose.waitUntil(10_000) { calls.get()==2 }
        compose.onNodeWithTag("planner_generate").assertContentDescriptionEquals("终止规划").performClick()
        assertTrue(session.executions.value.getValue(Campus.SHAHE).cancelled)
        assertTrue(session.executions.value.getValue(Campus.XITUCHENG).pending)
        compose.onNodeWithTag("campus_swipe_region").performTouchInput { swipeRight() }
        compose.onNodeWithTag("planner_generate").assertContentDescriptionEquals("终止规划").performClick()
        assertTrue(session.executions.value.getValue(Campus.XITUCHENG).cancelled)
    }
    @Test fun allFourDialogHeadingsAndTheirCampusLabelsUseTheSmallerTypeSizes() {
        var dialog by mutableIntStateOf(0)
        val room=RoomMatch(data.classrooms.first(),data.classrooms.first().emptyProfile(),emptySet())
        val state=YouRoomState(initializing=false,snapshot=data,today=data.queryDate)
        compose.setContent { YouRoomTheme {
            when (dialog) {
                0 -> FilterDialog(state,{},{},{},{},{})
                1 -> RoomTagsDialog(room,state,{},{},{},{},{})
                2 -> RoomNoteDialog(room,{},{})
                else -> ExpectedRoomDialog(state,emptySet(),{},{})
            }
        } }
        val titles=listOf("筛选教室","教室标签","${room.classroom.name} · 备注","期望教室")
        fun font(text: String): Float {
            val layouts=mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            return layouts.single().layoutInput.style.fontSize.value
        }
        titles.forEachIndexed { index,title ->
            compose.runOnIdle { dialog=index }
            assertEquals(16f,font(title),.01f)
            if (index!=2) assertEquals(12f,font("西土城"),.01f)
        }
        shot("planner-dialog-title-1.15")
    }
}
