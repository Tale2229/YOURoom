package com.example.youroom.ui

import com.example.youroom.ui.theme.themeRoles

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
import kotlinx.coroutines.delay

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
fun StudyPlanningScreen(state: YOURoomState, goal: PlanningDraft, plans: Map<Campus,PlanningExecution>,
    onGoal: (PlanningDraft) -> Unit, onGenerate: () -> Unit, onCampus: (Campus) -> Unit,
    onRefresh: () -> Unit, onRoom: (String?) -> Unit,
    onOption: (Campus,Long,Int) -> Unit = { _,_,_ -> },
    clock: Clock = Clock.system(SchoolZone), showHeader: Boolean = true,
    sharedHeaderMoving: Boolean = false, interactive: Boolean = true, onCancel: () -> Unit = {},
    onPlanningPreferences: (Campus, CampusPlanningPreferences) -> Unit = { _, _ -> }) {
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
            if (page.campus == Campus.XITUCHENG) westList else shaheList, active,
            { if (active) onPlanningPreferences(page.campus, it) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudyPlanningContent(state: YOURoomState, goal: PlanningDraft, execution: PlanningExecution?,
    onGoal: (PlanningDraft) -> Unit, onGenerate: () -> Unit, onRefresh: () -> Unit,
    onCancel: () -> Unit, onOption: (Long,Int) -> Unit,clock: Clock, listState: LazyListState,
    interactive: Boolean, onPlanningPreferences: (CampusPlanningPreferences) -> Unit) {
    var startMoving by remember { mutableStateOf(false) }
    var endMoving by remember { mutableStateOf(false) }
    var wheelGoal by remember(goal) { mutableStateOf(goal) }
    var expectedDialog by remember(state.campus) { mutableStateOf(false) }
    var importDialog by remember(state.campus) { mutableStateOf(false) }
    var templateManager by remember(state.campus) { mutableStateOf(false) }
    var templateSeed by remember(state.campus) { mutableStateOf<List<StudyWindow>?>(null) }
    var additionError by remember(goal, state.campus) { mutableStateOf<PlanningResult?>(null) }
    // Unlike validation errors, the toggle feedback must survive its own goal update.
    var modeFeedback by remember(state.campus) { mutableStateOf<PlanningResult?>(null) }
    LaunchedEffect(modeFeedback) {
        if(modeFeedback != null) {
            delay(2200)
            modeFeedback=null
        }
    }
    var addAccepted by remember(state.campus) { mutableStateOf(false) }
    var expectedAccepted by remember(state.campus) { mutableStateOf(false) }
    var addRejection by remember(state.campus) { mutableIntStateOf(0) }
    val fullAddCooldown=remember(state.campus) { PlanningTapCooldown() }
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
    val guide=LocalUsageGuide.current
    LaunchedEffect(guide?.preparedStage,guide?.demonstration,guide?.entry,interactive) {
        if(guide==null || !interactive || guide.preparedStage!=guide.stage) return@LaunchedEffect
        expectedDialog=guide.stage==GuideStage.EXPECT && guide.entry>0
        val needsImport=guide.stage in listOf(GuideStage.IMPORT_PICK,GuideStage.REPLACE) ||
            (guide.stage==GuideStage.IMPORT && guide.entry>0)
        if(needsImport) {
            if(!importDialog) importDialog=true
        } else importDialog=false
        templateSeed=if(guide.stage==GuideStage.SAVE_TEMPLATE && guide.entry>0) goal.savedWindows else null
        templateManager=false
        additionError=if((guide.stage==GuideStage.FROM_NOW && guide.entry==0) ||
            (guide.stage==GuideStage.DURATION && guide.entry==1) ||
            (guide.stage==GuideStage.PAST && guide.entry>0)) PlanningSchedule.add(guide.goal,600).error else null
        withFrameNanos { }
        editorScroll.scrollTo(0)
    }

    if (expectedDialog) ExpectedRoomDialog(state, goal.expectedRooms,
        { onGoal(goal.copy(expectedRooms = it)) }, { expectedDialog = false })
    if (templateSeed != null) TimeTemplateEditor(state.campus, null, templateSeed.orEmpty(),
        "组合${state.planningPreferences.forCampus(state.campus).templates.size+1}", { template ->
            val preferences=state.planningPreferences.forCampus(state.campus)
            onPlanningPreferences(preferences.copy(templates=preferences.templates+template));templateSeed=null;guide?.closedTemplate()
        }, { templateSeed=null;guide?.closedTemplate() })
    else if (templateManager) TimeTemplateManager(state.campus, state.planningPreferences.forCampus(state.campus),
        onPlanningPreferences, { templateManager = false })
    else if (importDialog) TimeTemplateImportDialog(state.campus,
        state.planningPreferences.forCampus(state.campus).templates, goal.savedWindows,
        interactive && !pending && !startMoving && !endMoving,
        notBefore = { if (state.demo) null else java.time.LocalTime.now(clock.withZone(SchoolZone))
            .let { (it.toSecondOfDay() + 59) / 60 } }, onImport = { imported ->
            if (interactive && !pending && !startMoving && !endMoving) {
                onGoal(goal.copy(savedWindows = imported.windows)); additionError = null
                modeFeedback = PlanningResult(message = imported.message); importDialog = false; addAccepted = true
            }
        }, onManage = { templateManager = true }, onDismiss = { importDialog = false })

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
                        generateEnabled = interactive && !pending && !startMoving && !endMoving && confirmedGoal != null && snapshot != null && !stale &&
                            (guide==null || guide.stage==GuideStage.GENERATE),
                        expectedCount = expectedCount, pending = pending,
                        onStartMoving = { startMoving = it }, onEndMoving = { endMoving = it },
                        onStart = { wheelGoal = wheelGoal.copy(start = it); onGoal(wheelGoal) },
                        onEnd = { wheelGoal = wheelGoal.copy(end = it); onGoal(wheelGoal) },
                        onAdd = {
                            if (!addAccepted) {
                                modeFeedback=null
                                val now = if (state.demo) null else java.time.LocalTime.now(clock.withZone(SchoolZone))
                                    .let { (it.toSecondOfDay()+59)/60 }
                                val addition = PlanningSchedule.add(goal,now)
                                if (addition.goal != null) {
                                    addAccepted=true; addRejection=0; additionError=null
                                    onGoal(addition.goal)
                                } else {
                                    additionError=addition.error; addRejection++
                                    guide?.rejectedPeriod(addition.error?.problem)
                                }
                            }
                        }, onExpected = { if (!expectedAccepted) { expectedAccepted=true;expectedDialog=true;guide?.openedExpected() } },
                        onFlexible = {
                            val enabled=!goal.flexible
                            additionError=null
                            modeFeedback=PlanningResult(message=if(enabled) "已启用宽松模式" else "已关闭宽松模式")
                            onGoal(goal.copy(flexible=enabled))
                        },
                        onGenerate = { additionError = null; modeFeedback=null; onGenerate() },
                        cancelEnabled = interactive, onCancel = onCancel,
                        addAccepted = addAccepted, addRejection = addRejection, expectedAccepted = expectedAccepted,
                        onFullAdd=if(interactive && !pending && goal.savedWindows.size>=PlanningPolicy.periodLimit) ({
                            if(fullAddCooldown.acquire(android.os.SystemClock.elapsedRealtime())) {
                                modeFeedback=null
                                val feedback=PlanningResult(message="最多添加三个时段",problem=PlanningProblem.TOO_MANY)
                                additionError=feedback;addRejection++
                                scope.launch {
                                    delay(2200)
                                    if(additionError===feedback) additionError=null
                                }
                            }
                        }) else null,
                        onImport = if (interactive && !pending && !startMoving && !endMoving && guide?.stage!=GuideStage.ADD)
                            ({ importDialog = true;guide?.openedImport() }) else null,
                        importing = importDialog || templateManager)
                    if (snapshot == null || stale) TextButton(onClick = onRefresh,
                        enabled = !state.refreshing && !state.demo, shape = MaterialTheme.shapes.small) { Text("获取今日数据") }
                }
            }
        // Preserve the former divider's exact layout height without drawing a line.
        Spacer(Modifier.fillMaxWidth().height(1.dp).testTag("planner_input_spacing"))
        Column(Modifier.weight(1f).fillMaxWidth().testTag("planner_results_area")) {
            PlanningTargetStrip(displaySlots,goal.savedWindows,clock, actions = PlanningRangeActions(
                enabled = interactive && !pending && !startMoving && !endMoving && guide?.stage!=GuideStage.ADD,
                onEdit = { index ->
                    onGoal(PlanningSchedule.remove(goal,index))
                    scope.launch { editorScroll.animateScrollTo(0) }
                }, onDelete = { index -> onGoal(PlanningSchedule.remove(goal,index)) },
                onSave = { templateSeed=goal.savedWindows.toList();guide?.openedTemplate() }))
            // Keep feedback between the target strip and the plan heading, even
            // after a route exists or its details have been scrolled down.
            key(state.campus) {
                PlanningFeedbackHost(additionError ?: modeFeedback ?: result?.takeIf { it.message != null },goal,clock,onGoal)
            }
            if (execution != null && execution.input.today != planningToday) {
                StatusNotice("以下为 ${execution.input.today} 生成的计划，仅供回看。")
            }
            if (pending) {
                Row(Modifier.padding(horizontal = 24.dp, vertical = 12.dp).testTag("planner_loading"),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("比较时间与完整路线…", style = MaterialTheme.typography.bodySmall)
                }
            }
            PlanningWorkspace(execution,state.campus,interactive,{ id,index ->
                onOption(id,index); scope.launch { listState.animateScrollToItem(0) }
            }) {
            LazyColumn(Modifier.fillMaxSize().testTag("planner_list"),state=listState,userScrollEnabled=interactive,
                contentPadding=PaddingValues(bottom=24.dp)) {
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
            if (use.favorite) Icon(Icons.Filled.Star,"已收藏",Modifier.size(16.dp),tint=themeRoles().favorite)
            if (expected) Icon(Icons.Filled.PushPin,"期望教室",Modifier.size(16.dp).graphicsLayer { rotationZ=ExpectedPinTilt },
                tint=MaterialTheme.colorScheme.primary)
        }
        PlanningRoomStrip(use,slots,free,enabled,generation)
        ListDivider(Modifier.padding(horizontal=24.dp))
    }
}
