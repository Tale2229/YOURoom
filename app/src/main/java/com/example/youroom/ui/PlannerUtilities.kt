package com.example.youroom.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.example.youroom.domain.*
import java.time.format.DateTimeFormatter

internal enum class PlannerPanel { RESULT, HISTORY, LEISURE }
@Stable
class PlannerUtilityState {
    internal var panel by mutableStateOf(PlannerPanel.RESULT)
    internal var recordId by mutableStateOf<String?>(null)
    internal var recordOption by mutableIntStateOf(0)
    internal var game by mutableStateOf(false)
    internal var item by mutableStateOf<LeisureItem?>(null)
    internal val runner = DinosaurRun()

    internal fun openRecord(record: PlanningHistoryRecord) {
        runner.pause()
        recordId=record.id
        recordOption=record.selectedOption
        panel=PlannerPanel.RESULT
    }

    internal fun clearRecord() { recordId=null; recordOption=0 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlannerUtilityBar(state: PlannerUtilityState, count: Int, enabled: Boolean) {
    Row(Modifier.fillMaxWidth().background(plannerUtilityBackground(MaterialTheme.colorScheme,state.panel))
        .height(48.dp).padding(horizontal=8.dp).testTag("planner_utilities"),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        for ((panel, icon, title) in listOf(Triple(PlannerPanel.HISTORY, Icons.Outlined.History, "历史记录"),
            Triple(PlannerPanel.LEISURE, Icons.Outlined.AutoAwesome, "随手一翻与 Google Dino"))) {
            val selected=state.panel==panel ||
                (panel==PlannerPanel.HISTORY && state.panel==PlannerPanel.RESULT && state.recordId!=null)
            TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text(if(panel == PlannerPanel.HISTORY) "历史记录 · $count 条" else title) } },
                state = rememberTooltipState()) {
                IconButton(onClick = {
                    state.runner.pause()
                    state.panel = if (state.panel == panel) PlannerPanel.RESULT else panel
                }, enabled = enabled, modifier = Modifier.testTag(if (panel == PlannerPanel.HISTORY) "planner_history" else "planner_leisure")
                    .semantics { this.selected = selected }) {
                    BadgedBox(badge = {
                        if (panel == PlannerPanel.HISTORY && count > 0) Badge(
                            containerColor = if(selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                            contentColor = if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary) {
                            Text(count.coerceAtMost(10).toString())
                        }
                    }) {
                        Box(Modifier.size(36.dp).then(if(selected) Modifier.background(MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(11.dp)) else Modifier),contentAlignment=Alignment.Center) {
                            Icon(if(selected) {
                                if(panel==PlannerPanel.HISTORY) Icons.Filled.History else Icons.Filled.AutoAwesome
                            } else icon,title,Modifier.size(24.dp),
                                tint=if(selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PlannerUtilityPanel(utility: PlannerUtilityState, history: List<PlanningHistoryRecord>, notice: String?,
    today: java.time.LocalDate, tone: CampusTone, active: Boolean,
    onHistory: (PlanningHistoryRecord) -> Unit, modifier: Modifier = Modifier) {
    BackHandler(active) {
        utility.runner.pause(); utility.panel = PlannerPanel.RESULT
    }
    val background=plannerUtilityBackground(MaterialTheme.colorScheme,utility.panel)
    Column(modifier.fillMaxSize().background(background).testTag("planner_utility_panel")) {
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).height(48.dp)
            .padding(start = 24.dp, end = 12.dp).testTag("planner_utility_heading"), verticalAlignment = Alignment.CenterVertically) {
            Text(if (utility.panel == PlannerPanel.HISTORY) "历史记录" else if (utility.game) "Google Dino" else "随手一翻",
                Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if (utility.panel == PlannerPanel.LEISURE) {
                IconButton(onClick = { utility.runner.pause(); utility.game = false }, enabled = active) { Icon(Icons.AutoMirrored.Outlined.MenuBook, "看看静态内容") }
                IconButton(onClick = { utility.game = true }, enabled = active) { Icon(Icons.Outlined.SportsEsports, "玩 Google Dino") }
            }
            IconButton(onClick = { utility.runner.pause(); utility.panel = PlannerPanel.RESULT }, enabled = active) { Icon(Icons.Outlined.Close, "收起面板") }
        }
        if (utility.panel == PlannerPanel.HISTORY) {
            PlanningHistoryPanel(history.filter { it.date == today },utility.recordId,notice,active,onHistory,Modifier.weight(1f))
        } else if (utility.game) {
            DinosaurRunner(utility.runner, active, Modifier.weight(1f),backgroundColor=background)
        } else {
            LeisureReader(utility, tone, active, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PlanningHistoryPanel(records: List<PlanningHistoryRecord>, selectedId: String?, notice: String?,
    active: Boolean, onHistory: (PlanningHistoryRecord) -> Unit, modifier: Modifier) {
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)) {
        item {
            if (notice != null) Text(notice, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
            if (records.isEmpty()) Text("今日暂无历史记录", Modifier.padding(vertical = 28.dp), style = MaterialTheme.typography.bodyMedium)
        }
        items(records, key = { it.id }) { saved ->
            val line=MaterialTheme.colorScheme.primary.copy(alpha=if(saved.id==selectedId) .85f else .28f)
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("history_${saved.id}")
                .clickable(enabled=active,role=Role.Button,
                    interactionSource=remember { MutableInteractionSource() },indication=null) { onHistory(saved) }
                .padding(vertical=14.dp),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f).drawBehind {
                    val stroke=1.5.dp.toPx()
                    drawLine(line,Offset(stroke/2,0f),Offset(stroke/2,size.height),stroke)
                }.padding(start=12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${saved.completedAt.atZone(SchoolZone).format(DateTimeFormatter.ofPattern("HH:mm"))}  ${saved.campus.label}",
                        style = MaterialTheme.typography.titleSmall)
                    Text(saved.goal.windows.joinToString("、") { "${planningTime(it.start)}–${planningTime(it.end)}" }, style = MaterialTheme.typography.bodySmall)
                    Text(if (saved.result.options.isEmpty()) "未找到可行方案" else "${saved.result.options.map { it.plan.signature }.distinct().size} 个不同方案",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun LeisureReader(utility: PlannerUtilityState, tone: CampusTone, active: Boolean, modifier: Modifier) {
    LaunchedEffect(tone) {
        if (utility.item == null || utility.item?.theme != null) utility.item = LeisureContent.next(tone, utility.item?.id)
    }
    val item = utility.item ?: return
    var revealed by remember(item.id) { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(item.title,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                Text(item.kind.title,style=MaterialTheme.typography.labelSmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
            }
            Text(item.body, style = MaterialTheme.typography.bodyMedium)
            if (item.kind == LeisureKind.CALM) CalmRipples(active, Modifier.fillMaxWidth().height(140.dp))
            if (item.answer != null) {
                if (revealed) Text(item.answer, style = MaterialTheme.typography.bodyMedium)
                else TextButton(onClick = { revealed = true }, enabled = active) { Text("看看答案") }
            }
            // Keep real quotation credits; omit the repeated application signature only.
            if (item.source.isNotBlank() && item.source!="YOURoom") Text(item.source,
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = { utility.item = LeisureContent.next(tone, item.id) }, enabled = active,
            modifier = Modifier.align(Alignment.End).padding(end = 16.dp)) { Text("换一则") }
    }
}

@Composable
private fun CalmRipples(active: Boolean, modifier: Modifier) {
    var touch by remember { mutableStateOf(Offset(.5f, .5f)) }
    var pulse by remember { mutableIntStateOf(0) }
    val progress = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(pulse, active) {
        if (pulse > 0 && active) { progress.snapTo(0f); progress.animateTo(1f, androidx.compose.animation.core.tween(1700)) }
    }
    val ink = MaterialTheme.colorScheme.primary
    Canvas(modifier.clipToBounds().pointerInput(active) {
        detectTapGestures { point -> if (active) { touch = Offset(point.x / size.width, point.y / size.height); pulse++ } }
    }.semantics {
        contentDescription = "轻触水面，泛起涟漪"
        onClick { if (active) pulse++; active }
    }) {
        if (pulse == 0) drawCircle(ink.copy(alpha = .2f), 3.dp.toPx(), Offset(size.width / 2, size.height / 2))
        else repeat(3) { index ->
            val p = (progress.value - index * .12f).coerceIn(0f, 1f)
            if (p > 0f) drawCircle(ink.copy(alpha = (1f - p) * .4f), 4.dp.toPx() + size.minDimension * p,
                Offset(touch.x * size.width, touch.y * size.height), style = Stroke(1.dp.toPx()))
        }
    }
}
