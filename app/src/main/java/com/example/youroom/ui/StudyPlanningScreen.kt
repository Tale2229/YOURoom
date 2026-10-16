package com.example.youroom.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animate
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import com.example.youroom.domain.*
import java.time.Clock
import kotlinx.coroutines.launch

val PlanningGoalSaver = listSaver<PlanningDraft, Any>(
    save = { listOf("v3", it.start, it.end, it.flexible, it.origin ?: "", it.savedWindows.size) +
        it.savedWindows.flatMap { window -> listOf(window.start,window.end) } + it.expectedRooms.sorted() },
    // Previously saved dialogs included the removed lunch toggle before origin.
    restore = { PlanningSchedule.restore(if (it.firstOrNull() in listOf("v2","v3")) {
        val count = it[5] as Int
        PlanningDraft(it[1] as Int, it[2] as Int, it[3] as Boolean, (it[4] as String).ifEmpty { null },
            it.subList(6,6+count*2).chunked(2).map { pair -> StudyWindow(pair[0] as Int,pair[1] as Int) },
            it.drop(6+count*2).map { id -> id as String }.toSet())
    } else PlanningDraft(it[0] as Int, it[1] as Int, it[2] as Boolean,
        (it[if (it.size == 5) 4 else 3] as String).ifEmpty { null },
        if (it.size >= 6) it.drop(4).chunked(2).map { pair -> StudyWindow(pair[0] as Int,pair[1] as Int) }
        else emptyList())) })

@Composable
fun StudyPlanningScreen(state: YouRoomState, goal: PlanningDraft, plans: Map<Campus,PlanningExecution>,
    onGoal: (PlanningDraft) -> Unit, onGenerate: () -> Unit, onCampus: (Campus) -> Unit,
    onRefresh: () -> Unit, onRoom: (String?) -> Unit,
    onOption: (Campus,Long,Int) -> Unit = { _,_,_ -> },
    clock: Clock = Clock.system(SchoolZone), showHeader: Boolean = true,
    sharedHeaderMoving: Boolean = false, interactive: Boolean = true, onCancel: () -> Unit = {}) {
    val westList = rememberLazyListState()
    val shaheList = rememberLazyListState()
    var heldGoal by remember { mutableStateOf(goal) }
    CampusFrame(state, onCampus, showHeader, sharedHeaderMoving, interactive,
        bodyTag = "planner_campus_body") { page, active, _, ready ->
        val displayedGoal = if (ready) goal else heldGoal
        SideEffect { if (ready) heldGoal = goal }
        StudyPlanningContent(page, displayedGoal, plans[page.campus],
            { if (active) onGoal(it) }, { if (active) onGenerate() },
            { if (active) onRefresh() }, { if (active) onCancel() },
            { id,index -> if (active) onOption(page.campus,id,index) },clock,
            if (page.campus == Campus.XITUCHENG) westList else shaheList, active)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudyPlanningContent(state: YouRoomState, goal: PlanningDraft, execution: PlanningExecution?,
    onGoal: (PlanningDraft) -> Unit, onGenerate: () -> Unit, onRefresh: () -> Unit,
    onCancel: () -> Unit, onOption: (Long,Int) -> Unit,clock: Clock, listState: LazyListState,
    interactive: Boolean) {
    var startMoving by remember { mutableStateOf(false) }
    var endMoving by remember { mutableStateOf(false) }
    var wheelGoal by remember(goal) { mutableStateOf(goal) }
    var expectedDialog by remember(state.campus) { mutableStateOf(false) }
    var additionError by remember(goal, state.campus) { mutableStateOf<PlanningResult?>(null) }
    var addAccepted by remember(state.campus) { mutableStateOf(false) }
    var expectedAccepted by remember(state.campus) { mutableStateOf(false) }
    var addRejection by remember(state.campus) { mutableIntStateOf(0) }
    LaunchedEffect(addAccepted) {
        if (addAccepted) {
            animate(0f,1f,animationSpec=tween(64)) { _,_ -> }
            addAccepted=false
        }
    }
    LaunchedEffect(expectedAccepted) {
        if (expectedAccepted) {
            animate(0f,1f,animationSpec=tween(64)) { _,_ -> }
            expectedAccepted=false
        }
    }
    val snapshot = state.campusSnapshot
    val planningToday = if (state.demo) state.today else java.time.LocalDate.now(clock.withZone(SchoolZone))
    val stale = snapshot?.isCurrentOn(planningToday) == false
    val confirmedGoal = remember(goal.savedWindows,goal.flexible,goal.expectedRooms) { PlanningSchedule.confirmedGoal(goal) }
    val result = execution?.result
    val pending = execution?.pending == true
    val choice = execution?.selectedOption ?: 0
    val expectedCount = goal.expectedRooms.count { it.startsWith("${state.campus.dataKey}:") }
    val editorScroll=rememberScrollState()
    val scope=rememberCoroutineScope()
    val displaySlots=remember(snapshot) { snapshot?.slots.orEmpty().sortedBy { it.start } }
    val displayedPlan=result?.options?.takeIf { it.isNotEmpty() }?.let { it[choice.coerceIn(it.indices)].plan }
    val displayedUses=remember(displayedPlan,execution?.input?.snapshot) {
        displayedPlan?.let { PlanningDisplay.rooms(it,execution?.input?.snapshot) }.orEmpty()
    }

    if (expectedDialog) ExpectedRoomDialog(state, goal.expectedRooms,
        { onGoal(goal.copy(expectedRooms = it)) }, { expectedDialog = false })

    BoxWithConstraints(Modifier.fillMaxSize()) {
    // Reserve a real detail viewport, including the target strip and comparison header.
    // On short screens the input area can scroll independently instead of starving it.
    val editorMaximum=minOf(maxHeight*.6f,maxHeight-104.dp).coerceAtLeast(0.dp)
    Column(Modifier.fillMaxSize().testTag("planner_screen")) {
        Column(Modifier.fillMaxWidth().heightIn(max=editorMaximum).verticalScroll(editorScroll).testTag("planner_editor")) {
            if (state.demo) StatusNotice("演示规划 · 空闲安排为虚构，标记不写入真实数据。")
            if (stale) StatusNotice("缓存已跨日，请刷新今日数据后规划。", isError = true)
                Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlanningTimeControls(goal,
                        addEnabled = interactive && !startMoving && !endMoving && !pending && goal.savedWindows.size < PlanningPolicy.periodLimit,
                        expectedEnabled = interactive && snapshot != null && !stale,
                        flexibleEnabled = interactive && !pending,
                        generateEnabled = interactive && !pending && !startMoving && !endMoving && confirmedGoal != null && snapshot != null && !stale,
                        expectedCount = expectedCount, pending = pending,
                        onStartMoving = { startMoving = it }, onEndMoving = { endMoving = it },
                        onStart = { wheelGoal = wheelGoal.copy(start = it); onGoal(wheelGoal) },
                        onEnd = { wheelGoal = wheelGoal.copy(end = it); onGoal(wheelGoal) },
                        onAdd = {
                            if (!addAccepted) {
                                val now = if (state.demo) null else java.time.LocalTime.now(clock.withZone(SchoolZone))
                                    .let { (it.toSecondOfDay()+59)/60 }
                                val addition = PlanningSchedule.add(goal,now)
                                if (addition.goal != null) {
                                    addAccepted=true; addRejection=0; additionError=null
                                    onGoal(addition.goal)
                                } else {
                                    additionError=addition.error; addRejection++
                                    scope.launch { listState.animateScrollToItem(0) }
                                }
                            }
                        }, onExpected = { if (!expectedAccepted) { expectedAccepted=true;expectedDialog=true } },
                        onFlexible = { onGoal(goal.copy(flexible = !goal.flexible)) },
                        onGenerate = { additionError = null; onGenerate() },
                        cancelEnabled = interactive, onCancel = onCancel,
                        addAccepted = addAccepted, addRejection = addRejection, expectedAccepted = expectedAccepted)
                    if (snapshot == null || stale) TextButton(onClick = onRefresh,
                        enabled = !state.refreshing && !state.demo, shape = MaterialTheme.shapes.small) { Text("获取今日数据") }
                }
            }
        HorizontalDivider(Modifier.padding(horizontal=24.dp).testTag("planner_input_divider"))
        Column(Modifier.weight(1f).fillMaxWidth().testTag("planner_results_area")) {
            PlanningTargetStrip(displaySlots,goal.savedWindows,clock, actions = PlanningRangeActions(
                enabled = interactive && !pending && !startMoving && !endMoving,
                onEdit = { index ->
                    onGoal(PlanningSchedule.remove(goal,index))
                    scope.launch { editorScroll.animateScrollTo(0) }
                }, onDelete = { index -> onGoal(PlanningSchedule.remove(goal,index)) }))
            PlanningWorkspace(execution,state.campus,interactive,{ id,index ->
                onOption(id,index); scope.launch { listState.animateScrollToItem(0) }
            }) {
            LazyColumn(Modifier.fillMaxSize().testTag("planner_list"),state=listState,userScrollEnabled=interactive,
                contentPadding=PaddingValues(bottom=24.dp)) {
            if (execution != null && execution.input.today != planningToday) item {
                StatusNotice("以下为 ${execution.input.today} 生成的计划，仅供回看。")
            }
            if (pending) item {
                Row(Modifier.padding(horizontal = 24.dp, vertical = 12.dp).testTag("planner_loading"),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("比较时间与完整路线…", style = MaterialTheme.typography.bodySmall)
                }
            }
            item(key="planning_feedback") {
                key(state.campus) {
                    PlanningFeedbackHost(additionError ?: result?.takeIf { it.message != null },goal,clock,onGoal)
                }
            }
            if (result?.options?.isNotEmpty() == true) {
                val frozen=execution!!.input.snapshot
                val frozenSlots=frozen.slots.sortedBy { it.start }
                itemsIndexed(displayedUses,key={ _,use -> use.classroom.id }) { index, use ->
                    Crossfade(use,animationSpec=tween(160),label="plan room use") { shown ->
                        PlanningRoomRow(shown,frozenSlots,frozen.index.freeSlotsByRoom[shown.classroom.id].orEmpty(),
                            shown.classroom.id in execution.input.goal.expectedRooms,interactive,
                            execution.id,Modifier.testTag("planner_step_$index"))
                    }
                }
            }
        }
    }
    }
    }
    }
}

@Composable
private fun PlanningRoomRow(use: PlanningRoomUse, slots: List<LessonSlot>, free: Set<Int>, expected: Boolean,
    enabled: Boolean, generation: Long, modifier: Modifier) {
    // Group traversal without inheriting the first period's accessibility click.
    Column(modifier.fillMaxWidth().semantics { isTraversalGroup=true }.padding(top=12.dp,bottom=6.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal=24.dp),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(use.classroom.name,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
            if (use.favorite) Icon(Icons.Filled.Star,"已收藏",Modifier.size(16.dp),tint=MaterialTheme.colorScheme.primary)
            if (expected) Icon(Icons.Filled.PushPin,"期望教室",Modifier.size(16.dp).graphicsLayer { rotationZ=ExpectedPinTilt },
                tint=MaterialTheme.colorScheme.primary)
        }
        PlanningRoomStrip(use,slots,free,enabled,generation)
        HorizontalDivider(Modifier.padding(horizontal=24.dp))
    }
}
