package com.example.youroom.ui

import com.example.youroom.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/** A button-press snapshot. Later edits must not change a running or completed request. */
data class PlanningInput(val snapshot: ClassroomSnapshot, val campus: Campus, val today: LocalDate,
    val profiles: Map<String,RoomProfile>, val goal: PlanningGoal)

data class PlanningExecution(val id: Long, val input: PlanningInput, val result: PlanningResult? = null,
    val selectedOption: Int = 0) {
    val pending get() = result == null
}

/** Activity/ViewModel lifetime, independent of page composition and live school/profile flows. */
class PlanningSession(private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val calculate: suspend (PlanningInput) -> PlanningResult = { input ->
        val context = currentCoroutineContext()
        StudyPlanner.portfolio(input.snapshot,input.campus,input.today,input.profiles,input.goal) { context.ensureActive() }
    }) {
    private val mutableExecutions = MutableStateFlow<Map<Campus,PlanningExecution>>(emptyMap())
    val executions = mutableExecutions.asStateFlow()
    private val jobs = mutableMapOf<Campus,Job>()
    private var generation = 0L

    /** This method is called only by the explicit Generate button, never by a state observer. */
    fun generate(state: YouRoomState, draft: PlanningDraft): Boolean {
        val snapshot = state.campusSnapshot?.takeIf { it.isCurrentOn(state.today) } ?: return false
        val goal = PlanningSchedule.confirmedGoal(draft) ?: return false
        val frozen = snapshot.copy(slots = snapshot.slots.map { it.copy(classrooms = it.classrooms.toList()) })
        val profiles = state.profiles.mapValues { (_,profile) -> profile.copy(tags = profile.tags.toSet()) }
        val input = PlanningInput(frozen,state.campus,state.today,profiles,
            goal.copy(savedWindows = goal.savedWindows.toList(),expectedRooms = goal.expectedRooms.toSet()))
        val id = ++generation
        jobs.remove(state.campus)?.cancel()
        mutableExecutions.update { it + (input.campus to PlanningExecution(id,input)) }
        jobs[input.campus] = scope.launch {
            val result = try { withContext(dispatcher) { calculate(input) } }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { PlanningResult(message = "规划未完成，请重新生成。") }
            withContext(Dispatchers.Main.immediate) {
                mutableExecutions.update { current ->
                    if (current[input.campus]?.id == id) current + (input.campus to PlanningExecution(id,input,result)) else current
                }
            }
        }
        return true
    }

    fun select(campus: Campus, id: Long, index: Int) {
        mutableExecutions.update { current ->
            val execution = current[campus]
            if (execution?.id != id || execution.result?.options?.indices?.contains(index) != true) current
            else current + (campus to execution.copy(selectedOption = index))
        }
    }

    /** Account/data-source isolation only. Ordinary edits and date/campus changes never call this. */
    fun clear() {
        generation++
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        mutableExecutions.value = emptyMap()
    }
}
