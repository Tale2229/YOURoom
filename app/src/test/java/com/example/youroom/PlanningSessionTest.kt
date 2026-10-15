package com.example.youroom

import com.example.youroom.data.DemoData
import com.example.youroom.domain.*
import com.example.youroom.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlanningSessionTest {
    private val dispatcher = StandardTestDispatcher()
    private val data = DemoData.snapshot()
    private val state = YouRoomState(initializing=false,demo=true,snapshot=data,today=data.queryDate)
    private val draft = PlanningDraft(savedWindows=listOf(StudyWindow(480,720)))
    @Before fun setMain() = Dispatchers.setMain(dispatcher)
    @After fun resetMain() = Dispatchers.resetMain()

    private fun result(input: PlanningInput, message: String = "done") = PlanningResult(message=message,
        options=listOf(PlanningOption("推荐",StudyPlan(StudyWindow(input.goal.start,input.goal.end),emptyList(),
            240,0,0,0,0,0,0,0,0)),PlanningOption("原时段",StudyPlan(StudyWindow(input.goal.start,input.goal.end),
            emptyList(),240,60,0,0,0,0,0,0,-60))))

    @Test fun noRequestExistsUntilGenerateAndIdenticalExplicitClicksStillRunTwice() = runTest {
        var calls=0
        val session=PlanningSession(backgroundScope,dispatcher) { calls++;result(it) }
        runCurrent()
        assertTrue(session.executions.value.isEmpty())
        assertTrue(session.generate(state,draft))
        assertTrue(session.executions.value.getValue(state.campus).pending)
        runCurrent()
        val first=session.executions.value.getValue(state.campus)
        assertEquals(1,calls)
        assertTrue(session.generate(state,draft))
        assertNull(session.executions.value.getValue(state.campus).result)
        runCurrent()
        assertEquals(2,calls)
        assertTrue(session.executions.value.getValue(state.campus).id>first.id)
    }

    @Test fun theRequestCopiesProfilesTagsConfirmedWindowsAndSnapshotCollections() = runTest {
        val room=data.classrooms.first()
        val tags=mutableSetOf("原标记")
        val profiles=mutableMapOf(room.id to room.emptyProfile().copy(favorite=true,tags=tags))
        val windows=mutableListOf(StudyWindow(480,720))
        val expected=mutableSetOf(room.id)
        val rooms=data.slots.first().classrooms.toMutableList()
        val slots=data.slots.toMutableList().apply { set(0,first().copy(classrooms=rooms)) }
        val session=PlanningSession(backgroundScope,dispatcher) { result(it) }
        session.generate(state.copy(profiles=profiles,snapshot=data.copy(slots=slots)),draft.copy(savedWindows=windows,expectedRooms=expected))
        profiles.clear();tags.clear();windows.clear();expected.clear();rooms.clear();slots.clear()
        runCurrent()
        val frozen=session.executions.value.getValue(state.campus).input
        assertTrue(frozen.profiles.getValue(room.id).favorite)
        assertEquals(setOf("原标记"),frozen.profiles.getValue(room.id).tags)
        assertEquals(listOf(StudyWindow(480,720)),frozen.goal.windows)
        assertEquals(setOf(room.id),frozen.goal.expectedRooms)
        assertEquals(data.slots.size,frozen.snapshot.slots.size)
        assertTrue(frozen.snapshot.slots.first().classrooms.isNotEmpty())
    }

    @Test fun editsDuringCalculationCannotChangeTheInputOrRestartTheWorker() = runTest {
        var calls=0
        val gate=CompletableDeferred<Unit>()
        val session=PlanningSession(backgroundScope,dispatcher) { input -> calls++;gate.await();result(input) }
        session.generate(state,draft)
        runCurrent()
        val pending=session.executions.value.getValue(state.campus)
        val newState=state.copy(profiles=mapOf(data.classrooms.first().id to data.classrooms.first().emptyProfile().copy(excluded=true)))
        val newDraft=draft.copy(savedWindows=emptyList(),expectedRooms=setOf("01:2-240"),flexible=false)
        assertTrue(newState.profiles.isNotEmpty()&&newDraft.savedWindows.isEmpty())
        runCurrent()
        assertSame(pending,session.executions.value.getValue(state.campus))
        gate.complete(Unit);runCurrent()
        assertEquals(1,calls)
        assertEquals(draft.windows,session.executions.value.getValue(state.campus).input.goal.windows)
    }

    @Test fun campusSwitchingDoesNotCreateARequestAndResultsRemainIndependent() = runTest {
        var calls=0
        val session=PlanningSession(backgroundScope,dispatcher) { calls++;result(it) }
        session.generate(state,draft);runCurrent()
        val west=session.executions.value.getValue(Campus.XITUCHENG)
        val shahe=state.copy(campus=Campus.SHAHE,snapshot=DemoData.snapshot(Campus.SHAHE))
        assertNull(session.executions.value[shahe.campus])
        assertSame(west,session.executions.value[Campus.XITUCHENG])
        session.generate(shahe,draft);runCurrent()
        assertEquals(2,calls)
        assertEquals(2,session.executions.value.size)
        assertSame(west,session.executions.value[Campus.XITUCHENG])
        assertEquals("04",session.executions.value.getValue(Campus.SHAHE).input.snapshot.campusId)
    }

    @Test fun choosingAnOptionPersistsWithoutRecalculatingAndStaleClicksAreIgnored() = runTest {
        var calls=0
        val session=PlanningSession(backgroundScope,dispatcher) { calls++;result(it) }
        session.generate(state,draft);runCurrent()
        val first=session.executions.value.getValue(state.campus)
        session.select(state.campus,first.id,1);runCurrent()
        assertEquals(1,session.executions.value.getValue(state.campus).selectedOption)
        assertEquals(1,calls)
        assertSame(first.result,session.executions.value.getValue(state.campus).result)
        session.generate(state,draft);runCurrent()
        session.select(state.campus,first.id,1)
        session.select(state.campus,session.executions.value.getValue(state.campus).id,99)
        assertEquals(0,session.executions.value.getValue(state.campus).selectedOption)
    }

    @Test fun aSupersededWorkerCannotOverwriteTheLatestExplicitRequest() = runTest {
        val gates=listOf(CompletableDeferred<Unit>(),CompletableDeferred<Unit>())
        var calls=0
        val session=PlanningSession(backgroundScope,dispatcher) { input ->
            val index=calls++
            withContext(NonCancellable) { gates[index].await() }
            result(input,index.toString())
        }
        session.generate(state,draft);runCurrent()
        session.generate(state,draft.copy(savedWindows=listOf(StudyWindow(600,720))));runCurrent()
        gates[1].complete(Unit);runCurrent()
        val latest=session.executions.value.getValue(state.campus)
        assertEquals("1",latest.result!!.message)
        gates[0].complete(Unit);runCurrent()
        assertSame(latest,session.executions.value.getValue(state.campus))
    }

    @Test fun accountIsolationClearsAndCancelsWithoutResurrectingAnOldResult() = runTest {
        val gate=CompletableDeferred<Unit>()
        val session=PlanningSession(backgroundScope,dispatcher) { input ->
            withContext(NonCancellable) { gate.await() };result(input)
        }
        session.generate(state,draft);runCurrent()
        session.clear()
        assertTrue(session.executions.value.isEmpty())
        gate.complete(Unit);runCurrent()
        assertTrue(session.executions.value.isEmpty())
    }

    @Test fun invalidNewInputsDoNotEraseTheExistingPlan() = runTest {
        var calls=0
        val session=PlanningSession(backgroundScope,dispatcher) { calls++;result(it) }
        session.generate(state,draft);runCurrent()
        val old=session.executions.value.getValue(state.campus)
        assertFalse(session.generate(state,draft.copy(savedWindows=emptyList())))
        assertFalse(session.generate(state.copy(today=state.today.plusDays(1)),draft))
        assertFalse(session.generate(state.copy(snapshot=null),draft))
        runCurrent()
        assertEquals(1,calls)
        assertSame(old,session.executions.value.getValue(state.campus))
    }

    @Test fun failuresBecomeAStableMessageAndRetryRequiresAnotherClick() = runTest {
        var calls=0
        val session=PlanningSession(backgroundScope,dispatcher) { calls++;error("failed") }
        session.generate(state,draft);runCurrent()
        val failed=session.executions.value.getValue(state.campus)
        assertFalse(failed.pending)
        assertNotNull(failed.result!!.message)
        runCurrent()
        assertEquals(1,calls)
        session.generate(state,draft);runCurrent()
        assertEquals(2,calls)
    }
}
