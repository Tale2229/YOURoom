package com.example.youroom.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.example.youroom.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.time.Clock

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

private data class PlanningRequest(val snapshot: ClassroomSnapshot?, val campus: Campus,
    val today: java.time.LocalDate, val profiles: Map<String, RoomProfile>, val goal: PlanningGoal)
private data class FinishedPlanning(val request: PlanningRequest, val result: PlanningResult)

@Composable
fun StudyPlanningScreen(state: YouRoomState, goal: PlanningDraft, requested: Boolean,
    onGoal: (PlanningDraft) -> Unit, onGenerate: () -> Unit, onCampus: (Campus) -> Unit,
    onRefresh: () -> Unit, onRoom: (String?) -> Unit,
    clock: Clock = Clock.system(SchoolZone), showHeader: Boolean = true,
    sharedHeaderMoving: Boolean = false, interactive: Boolean = true) {
    val westList = rememberLazyListState()
    val shaheList = rememberLazyListState()
    var heldGoal by remember { mutableStateOf(goal) }
    var heldRequested by remember { mutableStateOf(requested) }
    CampusFrame(state, onCampus, showHeader, sharedHeaderMoving, interactive,
        bodyTag = "planner_campus_body") { page, active, _, ready ->
        val displayedGoal = if (ready) goal else heldGoal
        val displayedRequested = if (ready) requested else heldRequested
        SideEffect { if (ready) { heldGoal = goal; heldRequested = requested } }
        StudyPlanningContent(page, displayedGoal, displayedRequested,
            { if (active) onGoal(it) }, { if (active) onGenerate() },
            { if (active) onRefresh() }, { if (active) onRoom(it) }, clock,
            if (page.campus == Campus.XITUCHENG) westList else shaheList, active)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudyPlanningContent(state: YouRoomState, goal: PlanningDraft, requested: Boolean,
    onGoal: (PlanningDraft) -> Unit, onGenerate: () -> Unit, onRefresh: () -> Unit,
    onRoom: (String?) -> Unit, clock: Clock, listState: LazyListState,
    interactive: Boolean) {
    var startMoving by remember { mutableStateOf(false) }
    var endMoving by remember { mutableStateOf(false) }
    var wheelGoal by remember(goal) { mutableStateOf(goal) }
    var expectedDialog by remember(state.campus) { mutableStateOf(false) }
    var additionError by remember(goal, state.campus) { mutableStateOf<PlanningResult?>(null) }
    var choice by remember(state.campus,goal.savedWindows,goal.flexible,goal.expectedRooms) { mutableIntStateOf(0) }
    val snapshot = state.campusSnapshot
    val planningToday = if (state.demo) state.today else java.time.LocalDate.now(clock.withZone(SchoolZone))
    val stale = snapshot?.isCurrentOn(planningToday) == false
    val confirmedGoal = remember(goal.savedWindows,goal.flexible,goal.expectedRooms) { PlanningSchedule.confirmedGoal(goal) }
    val request = remember(snapshot, state.campus, planningToday, state.profiles, confirmedGoal, requested) {
        if (requested && confirmedGoal != null) PlanningRequest(snapshot, state.campus, planningToday, state.profiles, confirmedGoal) else null
    }
    val finished by produceState<FinishedPlanning?>(null, request) {
        val input = request ?: return@produceState
        val result = withContext(Dispatchers.Default) {
            val context = kotlin.coroutines.coroutineContext
            input.snapshot?.let { StudyPlanner.planConfirmed(it, input.campus, input.today, input.profiles,
                input.goal) { context.ensureActive() } } ?: PlanningResult(message = "请先获取当前校区的今日数据。")
        }
        // Marshal the completion explicitly: also prevents test frame-clock dispatchers
        // from applying a worker snapshot in the middle of UI measurement.
        withContext(Dispatchers.Main.immediate) { value = FinishedPlanning(input, result) }
    }
    val result = finished?.takeIf { it.request == request }?.result
    val pending = request != null && result == null
    LaunchedEffect(result) { choice = 0 }
    val expectedCount = goal.expectedRooms.count { it.startsWith("${state.campus.dataKey}:") }

    if (expectedDialog) ExpectedRoomDialog(state, goal.expectedRooms,
        { onGoal(goal.copy(expectedRooms = it)) }, { expectedDialog = false })

    Column(Modifier.fillMaxSize().testTag("planner_screen")) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("planner_list"),
            state = listState, userScrollEnabled = interactive, contentPadding = PaddingValues(bottom = 24.dp)) {
            if (state.demo) item { StatusNotice("演示规划 · 空闲安排为虚构，标记不写入真实数据。") }
            if (stale) item { StatusNotice("缓存已跨日，请刷新今日数据后规划。", isError = true) }
            item {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        WheelTimePicker("开始时间", goal.start, "planner_start", Modifier.weight(1f),
                            onMoving = { startMoving = it }, viewportRows = 3.5f) {
                            wheelGoal = wheelGoal.copy(start = it); onGoal(wheelGoal)
                        }
                        WheelTimePicker("结束时间", goal.end, "planner_end", Modifier.weight(1f),
                            onMoving = { endMoving = it }, viewportRows = 3.5f) {
                            wheelGoal = wheelGoal.copy(end = it); onGoal(wheelGoal)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        PlanningPeriods(goal, interactive && !pending && !startMoving && !endMoving, Modifier.weight(1f)) { index ->
                            onGoal(PlanningSchedule.remove(goal,index))
                        }
                        IconButton(onClick = {
                            val now = if (state.demo) null else java.time.LocalTime.now(clock.withZone(SchoolZone))
                                .let { (it.toSecondOfDay()+59)/60 }
                            val addition = PlanningSchedule.add(goal,now)
                            if (addition.goal != null) onGoal(addition.goal)
                            else additionError = addition.error
                        }, enabled = interactive && !startMoving && !endMoving && !pending &&
                            goal.savedWindows.size < PlanningPolicy.periodLimit,
                            modifier = Modifier.size(48.dp).testTag("planner_add_period")) {
                            Icon(Icons.Outlined.Add,"添加时段",Modifier.size(20.dp))
                        }
                    }
                    Row(Modifier.fillMaxWidth().testTag("planner_actions"), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            PlanningAction("期望教室", false, interactive && snapshot != null && !stale,
                                Modifier.fillMaxWidth().testTag("planner_expected")) { expectedDialog = true }
                            if (expectedCount > 0) Badge(Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-3).dp)
                                .testTag("planner_expected_badge").semantics(mergeDescendants = true) {}, containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary) { Text(expectedCount.toString()) }
                        }
                        PlanningAction("宽松模式", goal.flexible, interactive && !pending,
                            Modifier.weight(1f).testTag("planner_adjust"), toggle = true) { onGoal(goal.copy(flexible = !goal.flexible)) }
                        PlanningAction(if (pending) "计算中…" else "生成规划", false,
                            interactive && !pending && !startMoving && !endMoving && confirmedGoal != null && snapshot != null && !stale,
                            Modifier.weight(1f).testTag("planner_generate"), primary = true,
                            onClick = { additionError = null; onGenerate() })
                    }
                    if (snapshot == null || stale) TextButton(onClick = onRefresh,
                        enabled = !state.refreshing && !state.demo, shape = MaterialTheme.shapes.small) { Text("获取今日数据") }
                }
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
                item {
                    Column(Modifier.padding(horizontal = 24.dp).testTag("planner_results"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HorizontalDivider()
                        result.options.forEachIndexed { index, option ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("planner_option_$index")
                                .clickable { choice = index }, verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = choice == index, onClick = { choice = index })
                                Text(option.label, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
                val plan = result.options[choice.coerceIn(result.options.indices)].plan
                item {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (plan.windows.size == 1) Text("${planningTime(plan.window.start)}—${planningTime(plan.window.end)}",
                            Modifier.testTag("planner_window"), style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary)
                        else Column(Modifier.testTag("planner_window").semantics(mergeDescendants = true) {}) {
                            plan.windows.forEachIndexed { index,window ->
                                Text("${index+1} · ${planningTime(window.start)}—${planningTime(window.end)}",
                                    Modifier.testTag("planner_window_$index"), style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Text("自习 ${plan.studyMinutes} 分钟 · 收藏 ${plan.favoriteMinutes} 分钟 · 换教室 ${plan.changes} 次",
                            Modifier.testTag("planner_metrics"), style = MaterialTheme.typography.bodyMedium)
                        Text("相较目标损失 ${plan.lostMinutes} 分钟 · 搬动 ${plan.walkingMinutes} 分钟（估算）",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (plan.windows != goal.windows)
                            Text("原目标 ${goal.windows.joinToString("、") { "${planningTime(it.start)}—${planningTime(it.end)}" }}，以下是建议区间。",
                                style = MaterialTheme.typography.bodySmall)
                    }
                }
                itemsIndexed(plan.steps) { index, step ->
                    PlanningTimelineStep(step, Modifier.testTag("planner_step_$index"), onRoom)
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
private fun PlanningAction(label: String, selected: Boolean, enabled: Boolean, modifier: Modifier,
    toggle: Boolean = false, primary: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val action = if (toggle) Modifier.toggleable(selected, enabled = enabled, role = Role.Checkbox,
        interactionSource = interaction, indication = null, onValueChange = { onClick() })
    else Modifier.clickable(enabled = enabled, role = Role.Button, interactionSource = interaction, indication = null, onClick = onClick)
    Box(modifier.heightIn(min = 48.dp).then(action), contentAlignment = Alignment.Center) {
        Surface(Modifier.fillMaxWidth().height(36.dp), shape = MaterialTheme.shapes.small,
            color = if (primary) MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else .38f)
                else if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            contentColor = if (primary) MaterialTheme.colorScheme.onPrimary else if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f),
            border = if (primary) null else BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center) {
                if (selected) Icon(Icons.Outlined.Check,null,Modifier.size(12.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PlanningTimelineStep(step: PlanningStep, modifier: Modifier, onRoom: (String?) -> Unit) {
    Column(modifier.fillMaxWidth().then(if (step is PlanningStep.Study)
        Modifier.clickable { onRoom(step.classroom.id) } else Modifier)
        .padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${planningTime(step.start)}—${planningTime(step.end)}", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary)
        when (step) {
            is PlanningStep.Study -> {
                Text(step.classroom.name, style = MaterialTheme.typography.titleLarge)
                Text(if (step.favorite) "已收藏" else step.classroom.buildingLabel, style = MaterialTheme.typography.bodySmall)
            }
            is PlanningStep.Transfer -> {
                Text(if (step.from.id == step.to.id) "暂离教室，等候下一空闲时段" else "搬动至 ${step.to.name}",
                    style = MaterialTheme.typography.titleMedium)
                Text("步行约 ${step.walkingMinutes} 分钟" + if (step.waitingMinutes > 0) " · 等候 ${step.waitingMinutes} 分钟" else "",
                    style = MaterialTheme.typography.bodySmall)
            }
            is PlanningStep.Break -> Text("间隔", style = MaterialTheme.typography.titleMedium)
        }
        HorizontalDivider()
    }
}
