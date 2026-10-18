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
    val selectedOption: Int = 0, val cancelled: Boolean = false,
    val defaultDirection: PlanningDirection = PlanningDirection.BALANCED) {
    val pending get() = result == null && !cancelled
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
    fun generate(state: YOURoomState, draft: PlanningDraft): Boolean {
        val snapshot = state.campusSnapshot?.takeIf { it.isCurrentOn(state.today) } ?: return false
        val goal = PlanningSchedule.confirmedGoal(draft) ?: return false
        val frozen = snapshot.copy(slots = snapshot.slots.map { it.copy(classrooms = it.classrooms.toList()) })
        val profiles = state.profiles.mapValues { (_,profile) -> profile.copy(tags = profile.tags.toSet()) }
        val input = PlanningInput(frozen,state.campus,state.today,profiles,
            goal.copy(savedWindows = goal.savedWindows.toList(),expectedRooms = goal.expectedRooms.toSet()))
        val id = ++generation
        val defaultDirection = state.planningPreferences.forCampus(state.campus).direction
        jobs.remove(state.campus)?.cancel()
        mutableExecutions.update { it + (input.campus to PlanningExecution(id,input, defaultDirection = defaultDirection)) }
        jobs[input.campus] = scope.launch {
            val result = try { withContext(dispatcher) { calculate(input) } }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { PlanningResult(message = "规划未完成，请重新生成。") }
            withContext(Dispatchers.Main.immediate) {
                mutableExecutions.update { current ->
                    if (current[input.campus]?.let { it.id == id && it.pending } == true)
                        current + (input.campus to result.defaultSelection(defaultDirection).let {
                            PlanningExecution(id,input,result, selectedOption = it.index, defaultDirection = it.direction)
                        }) else current
                }
            }
        }
        return true
    }

    /** Stop only this campus's current worker. Draft selections belong to the UI,
     * and neither they nor completed results in another campus are touched. */
    fun cancel(campus: Campus): Boolean {
        val execution = mutableExecutions.value[campus]?.takeIf { it.pending } ?: return false
        mutableExecutions.update { current ->
            if (current[campus]?.let { it.id == execution.id && it.pending } == true)
                current + (campus to execution.copy(cancelled = true)) else current
        }
        jobs.remove(campus)?.cancel()
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
