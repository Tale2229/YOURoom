package com.example.youroom.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.stateDescription
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
    sharedHeaderMoving: Boolean = false, interactive: Boolean = true) {
    val westList = rememberLazyListState()
    val shaheList = rememberLazyListState()
    var heldGoal by remember { mutableStateOf(goal) }
    CampusFrame(state, onCampus, showHeader, sharedHeaderMoving, interactive,
        bodyTag = "planner_campus_body") { page, active, _, ready ->
        val displayedGoal = if (ready) goal else heldGoal
        SideEffect { if (ready) heldGoal = goal }
        StudyPlanningContent(page, displayedGoal, plans[page.campus],
            { if (active) onGoal(it) }, { if (active) onGenerate() },
            { if (active) onRefresh() }, { if (active) onRoom(it) },
            { id,index -> if (active) onOption(page.campus,id,index) },clock,
            if (page.campus == Campus.XITUCHENG) westList else shaheList, active)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudyPlanningContent(state: YouRoomState, goal: PlanningDraft, execution: PlanningExecution?,
    onGoal: (PlanningDraft) -> Unit, onGenerate: () -> Unit, onRefresh: () -> Unit,
    onRoom: (String?) -> Unit, onOption: (Long,Int) -> Unit,clock: Clock, listState: LazyListState,
    interactive: Boolean) {
    var startMoving by remember { mutableStateOf(false) }
    var endMoving by remember { mutableStateOf(false) }
    var wheelGoal by remember(goal) { mutableStateOf(goal) }
    var expectedDialog by remember(state.campus) { mutableStateOf(false) }
    var additionError by remember(goal, state.campus) { mutableStateOf<PlanningResult?>(null) }
    val snapshot = state.campusSnapshot
    val planningToday = if (state.demo) state.today else java.time.LocalDate.now(clock.withZone(SchoolZone))
    val stale = snapshot?.isCurrentOn(planningToday) == false
    val confirmedGoal = remember(goal.savedWindows,goal.flexible,goal.expectedRooms) { PlanningSchedule.confirmedGoal(goal) }
    val result = execution?.result
    val pending = execution?.pending == true
    val choice = execution?.selectedOption ?: 0
    val expectedCount = goal.expectedRooms.count { it.startsWith("${state.campus.dataKey}:") }
    var westClosed by rememberSaveable(state.demo) { mutableStateOf(false) }
    var shaheClosed by rememberSaveable(state.demo) { mutableStateOf(false) }
    val collapsed=if (state.campus==Campus.XITUCHENG) westClosed else shaheClosed
    fun closePanel(value: Boolean) { if (state.campus==Campus.XITUCHENG) westClosed=value else shaheClosed=value }
    val editorScroll=rememberScrollState()
    val scope=rememberCoroutineScope()
    val displaySlots=remember(snapshot) { snapshot?.slots.orEmpty().sortedBy { it.start } }
    LaunchedEffect(collapsed) { if (collapsed) { startMoving=false; endMoving=false } }

    if (expectedDialog) ExpectedRoomDialog(state, goal.expectedRooms,
        { onGoal(goal.copy(expectedRooms = it)) }, { expectedDialog = false })

    BoxWithConstraints(Modifier.fillMaxSize()) {
    // Reserve a real detail viewport, including the target strip and comparison header.
    // On short screens the input area can scroll independently instead of starving it.
    val editorMaximum=minOf(maxHeight*.6f,maxHeight-104.dp).coerceAtLeast(0.dp)
    Column(Modifier.fillMaxSize().testTag("planner_screen")) {
        AnimatedVisibility(!collapsed,enter=expandVertically(tween(220),expandFrom=Alignment.Top)+fadeIn(tween(140)),
            exit=shrinkVertically(tween(220),shrinkTowards=Alignment.Top)+fadeOut(tween(120)),label="planning input panel") {
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
                            val now = if (state.demo) null else java.time.LocalTime.now(clock.withZone(SchoolZone))
                                .let { (it.toSecondOfDay()+59)/60 }
                            val addition = PlanningSchedule.add(goal,now)
                            if (addition.goal != null) onGoal(addition.goal)
                            else additionError = addition.error
                        }, onExpected = { expectedDialog = true },
                        onFlexible = { onGoal(goal.copy(flexible = !goal.flexible)) },
                        onGenerate = { additionError = null; onGenerate() })
                    if (snapshot == null || stale) TextButton(onClick = onRefresh,
                        enabled = !state.refreshing && !state.demo, shape = MaterialTheme.shapes.small) { Text("获取今日数据") }
                }
            }
        }
        if (!collapsed) HorizontalDivider(Modifier.padding(horizontal=24.dp).testTag("planner_input_divider"))
        Column(Modifier.weight(1f).fillMaxWidth().testTag("planner_results_area")
            .semantics {
                stateDescription=if (collapsed) "规划输入已收起" else "规划输入已展开"
                customActions=listOf(CustomAccessibilityAction(if (collapsed) "展开规划输入" else "收起规划输入") {
                    if (interactive) { closePanel(!collapsed); true } else false
                })
            }.planningPanelGestures(collapsed,interactive,{ !listState.canScrollBackward },::closePanel)) {
            if (collapsed) Box(Modifier.fillMaxWidth().height(28.dp).testTag("planner_expand_handle")
                .clickable(enabled=interactive,role=Role.Button,interactionSource=remember { MutableInteractionSource() },
                    indication=null,onClickLabel="展开规划输入") { closePanel(false) },contentAlignment=Alignment.Center) {
                Box(Modifier.width(28.dp).height(2.dp).background(MaterialTheme.colorScheme.outlineVariant))
            }
            PlanningTargetStrip(displaySlots,goal.savedWindows,clock, actions = PlanningRangeActions(
                enabled = interactive && !pending && !startMoving && !endMoving,
                onEdit = { index ->
                    onGoal(PlanningSchedule.remove(goal,index)); closePanel(false)
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
            (additionError ?: result?.takeIf { it.message != null })?.let { feedback -> item {
                PlanningFeedback(feedback,goal,clock,onGoal)
            } }
            if (result?.options?.isNotEmpty() == true) {
                val plan = result.options[choice.coerceIn(result.options.indices)].plan
                val uses=PlanningDisplay.rooms(plan)
                val frozen=execution!!.input.snapshot
                val frozenSlots=frozen.slots.sortedBy { it.start }
                itemsIndexed(uses,key={ _,use -> use.classroom.id }) { index, use ->
                    Crossfade(use,animationSpec=tween(160),label="plan room use") { shown ->
                        PlanningRoomRow(shown,frozenSlots,frozen.index.freeSlotsByRoom[shown.classroom.id].orEmpty(),
                            shown.classroom.id in execution.input.goal.expectedRooms,interactive,
                            execution.id,Modifier.testTag("planner_step_$index"),onRoom)
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
private fun PlanningFeedback(feedback: PlanningResult, goal: PlanningDraft, clock: Clock,
    onGoal: (PlanningDraft) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(feedback.message.orEmpty(), Modifier.weight(1f).testTag("planner_message"), style = MaterialTheme.typography.bodyMedium)
        when (feedback.problem) {
            PlanningProblem.PAST_TIME -> FilledTonalButton(onClick = {
                PlanningTimeActions.startFromNow(goal, clock)?.let(onGoal)
            }, enabled = PlanningTimeActions.startFromNow(goal, clock) != null,
                modifier = Modifier.heightIn(min = 48.dp).testTag("planner_from_now"),
                shape = MaterialTheme.shapes.small,
                contentPadding = PaddingValues(horizontal = 8.dp)) { Text("从现在起") }
            PlanningProblem.INVALID_TIME -> Row(Modifier.testTag("planner_duration_buttons"),
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(1, 2, 4).forEach { hours ->
                    FilledTonalButton(onClick = { PlanningTimeActions.endAfterHours(goal, hours)?.let(onGoal) },
                        enabled = PlanningTimeActions.endAfterHours(goal, hours) != null,
                        modifier = Modifier.width(48.dp).heightIn(min = 48.dp).testTag("planner_duration_${hours}h"),
                        shape = MaterialTheme.shapes.small,
                        contentPadding = PaddingValues(horizontal = 4.dp)) { Text("${hours}h") }
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun PlanningRoomRow(use: PlanningRoomUse, slots: List<LessonSlot>, free: Set<Int>, expected: Boolean,
    enabled: Boolean, generation: Long, modifier: Modifier, onRoom: (String?) -> Unit) {
    Column(modifier.fillMaxWidth().clickable(enabled=enabled,interactionSource=remember { MutableInteractionSource() },
        indication=null) { onRoom(use.classroom.id) }.padding(top=12.dp,bottom=6.dp)) {
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
