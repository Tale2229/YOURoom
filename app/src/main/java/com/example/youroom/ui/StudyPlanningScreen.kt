package com.example.youroom.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.unit.dp
import com.example.youroom.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.time.Clock

val PlanningGoalSaver = listSaver<PlanningGoal, Any>(
    save = { listOf(it.start, it.end, it.flexible, it.origin ?: "") },
    // Previously saved dialogs included the removed lunch toggle before origin.
    restore = { PlanningGoal(it[0] as Int, it[1] as Int, it[2] as Boolean,
        (it[if (it.size == 5) 4 else 3] as String).ifEmpty { null }) })

private data class PlanningRequest(val snapshot: ClassroomSnapshot?, val campus: Campus,
    val today: java.time.LocalDate, val profiles: Map<String, RoomProfile>, val goal: PlanningGoal,
    val notBefore: Int?)
private data class FinishedPlanning(val request: PlanningRequest, val result: PlanningResult)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyPlanningScreen(state: YouRoomState, goal: PlanningGoal, requested: Boolean,
    onGoal: (PlanningGoal) -> Unit, onGenerate: () -> Unit, onCampus: (Campus) -> Unit,
    onRefresh: () -> Unit, onRoom: (String?) -> Unit, notBeforeMinute: Int? = null,
    clock: Clock = Clock.system(SchoolZone)) {
    var editingStart by remember { mutableStateOf<Boolean?>(null) }
    var rules by remember { mutableStateOf(false) }
    var originMenu by remember { mutableStateOf(false) }
    var choice by remember(state.campus, goal) { mutableIntStateOf(0) }
    val snapshot = state.campusSnapshot
    val planningToday = if (state.demo) state.today else java.time.LocalDate.now(clock.withZone(SchoolZone))
    val stale = snapshot?.isCurrentOn(planningToday) == false
    val request = remember(snapshot, state.campus, planningToday, state.profiles, goal, requested, notBeforeMinute) {
        if (requested) PlanningRequest(snapshot, state.campus, planningToday, state.profiles, goal,
            if (state.demo) null else notBeforeMinute) else null
    }
    val finished by produceState<FinishedPlanning?>(null, request) {
        val input = request ?: return@produceState
        val result = withContext(Dispatchers.Default) {
            val context = kotlin.coroutines.coroutineContext
            input.snapshot?.let { StudyPlanner.plan(it, input.campus, input.today, input.profiles,
                input.goal, input.notBefore) { context.ensureActive() } } ?: PlanningResult(message = "请先获取当前校区的今日数据。")
        }
        value = FinishedPlanning(input, result)
    }
    val result = finished?.takeIf { it.request == request }?.result
    val pending = request != null && result == null
    LaunchedEffect(result) { choice = 0 }
    val origins = remember(snapshot, state.campus) { snapshot?.classrooms.orEmpty()
        .map { CampusTopology.location(it, state.campus).building }.distinct().sorted() }

    Column(Modifier.fillMaxSize().testTag("planner_screen")) {
        CampusHeader(state, onCampus)
        HorizontalDivider(Modifier.padding(horizontal = 24.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("planner_list"),
            contentPadding = PaddingValues(bottom = 24.dp)) {
            if (state.demo) item { StatusNotice("演示规划 · 空闲安排为虚构，标记不写入真实数据。") }
            if (stale) item { StatusNotice("缓存已跨日，请刷新今日数据后规划。", isError = true) }
            item {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("自习规划", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
                        TextButton(onClick = { rules = true }, shape = MaterialTheme.shapes.small) { Text("规则") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PlanningTimeButton("开始时间", goal.start, Modifier.weight(1f).testTag("planner_start")) { editingStart = true }
                        PlanningTimeButton("结束时间", goal.end, Modifier.weight(1f).testTag("planner_end")) { editingStart = false }
                    }
                    PlanningToggle("允许调整时间", goal.flexible, "planner_adjust") { onGoal(goal.copy(flexible = it)) }
                    Box {
                        OutlinedButton(onClick = { originMenu = true }, shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("planner_origin")) {
                            Text(goal.origin?.let { "从${CampusTopology.label(it)}出发" } ?: "出发教学楼 · 不限")
                        }
                        DropdownMenu(expanded = originMenu, onDismissRequest = { originMenu = false }) {
                            DropdownMenuItem(text = { Text("不限") }, onClick = { originMenu = false; onGoal(goal.copy(origin = null)) })
                            origins.forEach { origin -> DropdownMenuItem(text = { Text(CampusTopology.label(origin)) },
                                onClick = { originMenu = false; onGoal(goal.copy(origin = origin)) }) }
                        }
                    }
                    Button(onClick = onGenerate, enabled = !pending && snapshot != null && !stale,
                        shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .testTag("planner_generate")) { Text(if (pending) "计算中…" else "生成规划") }
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
            result?.message?.let { message -> item {
                Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(message, Modifier.weight(1f).testTag("planner_message"), style = MaterialTheme.typography.bodyMedium)
                    when (result.problem) {
                        PlanningProblem.PAST_TIME -> TextButton(onClick = {
                            PlanningTimeActions.startFromNow(goal, clock)?.let(onGoal)
                        }, enabled = PlanningTimeActions.startFromNow(goal, clock) != null,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("planner_from_now"),
                            contentPadding = PaddingValues(horizontal = 8.dp)) { Text("从现在起") }
                        PlanningProblem.INVALID_TIME -> Row(Modifier.testTag("planner_duration_buttons")) {
                            listOf(1, 2, 4).forEach { hours ->
                                TextButton(onClick = { PlanningTimeActions.endAfterHours(goal, hours)?.let(onGoal) },
                                    enabled = PlanningTimeActions.endAfterHours(goal, hours) != null,
                                    modifier = Modifier.width(48.dp).heightIn(min = 48.dp).testTag("planner_duration_${hours}h"),
                                    contentPadding = PaddingValues(horizontal = 4.dp)) { Text("${hours}h") }
                            }
                        }
                        null -> Unit
                    }
                }
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
                        Text("${planningTime(plan.window.start)}—${planningTime(plan.window.end)}",
                            Modifier.testTag("planner_window"), style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary)
                        Text("自习 ${plan.studyMinutes} 分钟 · 收藏 ${plan.favoriteMinutes} 分钟 · 换教室 ${plan.changes} 次",
                            Modifier.testTag("planner_metrics"), style = MaterialTheme.typography.bodyMedium)
                        Text("相较目标损失 ${plan.lostMinutes} 分钟 · 搬动 ${plan.walkingMinutes} 分钟（估算）",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (plan.window.start != goal.start || plan.window.end != goal.end)
                            Text("原目标 ${planningTime(goal.start)}—${planningTime(goal.end)}，以下是建议区间。",
                                style = MaterialTheme.typography.bodySmall)
                        if (goal.origin != null) Text("从楼入口抵达首间教室约 ${plan.approachMinutes} 分钟，需在开始前预留。",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                itemsIndexed(plan.steps) { index, step ->
                    PlanningTimelineStep(step, Modifier.testTag("planner_step_$index"), onRoom)
                }
            }
        }
    }
    editingStart?.let { start ->
        val minute = if (start) goal.start else goal.end
        key(start) {
            WheelTimePickerDialog(if (start) "开始时间" else "结束时间", minute,
                onDismiss = { editingStart = null }, onConfirm = { selected ->
                    onGoal(if (start) goal.copy(start = selected) else goal.copy(end = selected))
                    editingStart = null
                })
        }
    }
    if (rules) AlertDialog(onDismissRequest = { rules = false }, title = { Text("规划规则") },
        text = { Text("收藏加分，排除教室不可用，自定义词条不评分。\n\n最多换教室 2 次；按相邻节次的时间边界，将全部课间（包括午间）视为可用。上课期间仍须列为空闲，不补造缺失节次。搬动及等候扣除自习时间。\n\n代价：损失每分钟 ×3，起止偏移每分钟 ×1，换房一次 +20，换楼另 +12，步行每分钟 ×2，收藏自习每分钟 −1。额外十分钟收藏不足以抵消一次换房。\n\n时间候选按 5 分钟步进，并补充课表边界及对应整体平移。楼间、走廊和上下楼耗时均为估算，不是地图导航。",
            Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { rules = false }) { Text("关闭") } })
}

@Composable
private fun PlanningTimeButton(label: String, minute: Int, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 72.dp), shape = MaterialTheme.shapes.small) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(planningTime(minute), style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
private fun PlanningToggle(label: String, checked: Boolean, tag: String, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().testTag(tag).toggleable(checked, role = Role.Checkbox, onValueChange = onChecked)
        .heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Checkbox(checked, onCheckedChange = null)
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
        }
        HorizontalDivider()
    }
}
