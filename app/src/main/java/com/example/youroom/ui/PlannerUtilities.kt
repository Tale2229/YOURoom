package com.example.youroom.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.example.youroom.R
import com.example.youroom.domain.*
import com.example.youroom.data.OfflinePoetry
import kotlinx.coroutines.CancellationException
import java.time.format.DateTimeFormatter

internal enum class PlannerPanel { RESULT, HISTORY, LEISURE }
@Stable
class PlannerUtilityState(initialBestScore: Int = 0,onBestScore: (Int) -> Unit = {}) {
    internal var panel by mutableStateOf(PlannerPanel.RESULT)
    internal var recordId by mutableStateOf<String?>(null)
    internal var recordOption by mutableIntStateOf(0)
    internal var game by mutableStateOf(false)
    internal var item by mutableStateOf<LeisureItem?>(null)
    private val readerBack=ArrayDeque<LeisureItem>()
    private val readerForward=ArrayDeque<LeisureItem>()
    // A cancelled curl keeps this unvisited quotation reserved for the next turn.
    // Merely revealing it must not append history or skip it in the shuffled deck.
    private var readerReserved: LeisureItem? = null
    private var poetry: PoetryShuffle? = null
    internal fun installPoetry(items: List<LeisureItem>) {
        if(poetry==null) poetry=PoetryShuffle(items)
    }
    internal fun moveReader(direction: Int) {
        require(direction==1 || direction==-1)
        val current=item
        val destination=if(direction>0) readerForward.removeLastOrNull() else readerBack.removeLastOrNull()
        if(current!=null) {
            val stack=if(direction>0) readerBack else readerForward
            stack.addLast(current)
            if(stack.size>20) stack.removeFirst()
        }
        item=destination ?: reservedReaderItem().also { readerReserved=null }
    }
    internal fun previewReader(direction: Int): LeisureItem {
        require(direction==1 || direction==-1)
        return (if(direction>0) readerForward.lastOrNull() else readerBack.lastOrNull()) ?: reservedReaderItem()
    }
    private fun reservedReaderItem(): LeisureItem {
        if(readerReserved==null || readerReserved?.id==item?.id) readerReserved=nextItem(item?.id)
        return requireNotNull(readerReserved)
    }
    internal fun nextItem(previous: String? = null) = LeisureContent.next(previous,poetry=poetry)
    internal val runner = DinosaurRun(onBestScore=onBestScore).apply { restoreBestScore(initialBestScore) }
    private val acknowledgedCompletions=mutableStateMapOf<Campus,Long>()
    internal var observedCompletion: Pair<Campus,Long>? = null

    internal fun completionUnseen(campus: Campus,id: Long) = acknowledgedCompletions[campus]!=id
    internal fun observeCompletion(campus: Campus,id: Long?,resultsVisible: Boolean) {
        observedCompletion=id?.let { campus to it }
        // An already visible result needs no later "come back and view it" reminder.
        if(id!=null && resultsVisible) acknowledgedCompletions[campus]=id
    }
    internal fun closePanel() {
        observedCompletion?.let { (campus,id) -> acknowledgedCompletions[campus]=id }
        runner.pause();panel=PlannerPanel.RESULT
    }

    internal fun openRecord(record: PlanningHistoryRecord) {
        closePanel()
        recordId=record.id
        recordOption=record.selectedOption
    }

    internal fun clearRecord() { recordId=null; recordOption=0 }
}

@Composable
internal fun PlannerUtilityWorkspace(utility: PlannerUtilityState, history: List<PlanningHistoryRecord>, notice: String?,
    today: java.time.LocalDate, tone: CampusTone, active: Boolean, showUtilities: Boolean,
    onHistory: (PlanningHistoryRecord) -> Unit, onSurfaceVisibleChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    feedback: @Composable (Boolean) -> Unit = {},
    content: @Composable BoxScope.() -> Unit) {
    val guide=LocalUsageGuide.current
    val open=showUtilities && utility.panel!=PlannerPanel.RESULT
    // Keep the outgoing panel's content and origin until its reverse reveal has finished.
    var retainedPanel by remember(utility) { mutableStateOf(PlannerPanel.HISTORY) }
    val panel=if(open) utility.panel else retainedPanel
    SideEffect { if(open) retainedPanel=panel }
    val transition=updateTransition(open,label="planner_utility_reveal")
    val progress by transition.animateFloat(transitionSpec={
        tween(if(targetState) 380 else 280,easing=FastOutSlowInEasing)
    },label="corner_extent") { if(it) 1f else 0f }
    val covering=transition.currentState || transition.targetState
    // Surface lifetime gates input/semantics only. Its feedback reservation belongs to
    // the overlay and must never remeasure or translate the workspace underneath.
    SideEffect {
        onSurfaceVisibleChanged(showUtilities && covering)
        if(guide?.utilityLesson==true)
            guide.utilityLayoutReady=transition.currentState==transition.targetState && !transition.isRunning
    }
    Box(modifier) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()
                .then(if(covering) Modifier.clearAndSetSemantics {} else Modifier),content=content)
            // Keep both original buttons underneath the reveal. The opposite one stays
            // visible until the boundary reaches it, but cannot be used while covered.
            if(showUtilities) PlannerUtilityBar(utility,history.size,active && !covering,
                hiddenFromAccessibility=covering)
        }
        if(showUtilities && covering) {
            Column(Modifier.matchParentSize().graphicsLayer {
                shape=PlannerCornerRevealShape(progress,panel==PlannerPanel.HISTORY)
                clip=true
            }.background(MaterialTheme.colorScheme.surface).pointerInput(Unit) {
                // Consume otherwise empty surface taps rather than hitting the workspace below.
                detectTapGestures(onTap={})
            }.testTag("planner_utility_reveal")) {
                Column(Modifier.weight(1f).fillMaxWidth()) {
                    feedback(active && open)
                    PlannerUtilityPanel(utility,history,notice,today,tone,active && open,onHistory,
                        Modifier.weight(1f),panel)
                }
                PlannerUtilityBar(utility,history.size,active && open,overlayPanel=panel)
            }
        }
    }
}

/** Reveal the surface itself, not a scaled copy of its text or game. Physical corners
 * are intentional: the history / leisure controls remain at left / right in this app. */
private class PlannerCornerRevealShape(private val progress: Float,private val fromLeft: Boolean): Shape {
    override fun createOutline(size: Size,layoutDirection: LayoutDirection,density: Density): Outline {
        val p=progress.coerceIn(0f,1f)
        val width=size.width*p
        val height=size.height*p
        val left=if(fromLeft) 0f else size.width-width
        val radius=CornerRadius(minOf(with(density) { 24.dp.toPx() },width/2,height/2))
        return Outline.Generic(Path().apply {
            addRoundRect(RoundRect(Rect(left,size.height-height,left+width,size.height),
                topLeft=radius,topRight=radius,
                bottomRight=if(fromLeft) radius else CornerRadius.Zero,
                bottomLeft=if(fromLeft) CornerRadius.Zero else radius))
        })
    }
}

@Composable
private fun PlannerUtilityBar(state: PlannerUtilityState,count: Int,enabled: Boolean,
    overlayPanel: PlannerPanel? = null,hiddenFromAccessibility: Boolean = false) {
    val canJump=enabled && overlayPanel==PlannerPanel.LEISURE && state.game
    val guide=LocalUsageGuide.current
    // The overlay's empty left corner replaces (rather than activates) the history entry.
    // Its entire footer, including both outer gutters, is a jump pad except the close control.
    Box(Modifier.fillMaxWidth().height(48.dp)
        .then(if(overlayPanel!=null) Modifier.background(plannerUtilityBackground(MaterialTheme.colorScheme,overlayPanel)) else Modifier)
        .then(if(canJump) Modifier.clickable(role=Role.Button,
            interactionSource=remember { MutableInteractionSource() },indication=null,
            onClickLabel="跳跃或开始") { state.runner.jump();guide?.jumped() }
            .semantics { contentDescription="Google Dino 跳跃区域" } else Modifier)
        .then(if(hiddenFromAccessibility) Modifier.clearAndSetSemantics {} else Modifier)
        .then(if(overlayPanel==null) Modifier.testTag("planner_utilities") else Modifier.guideTag("planner_utility_footer"))) {
        Row(Modifier.fillMaxSize().padding(horizontal=8.dp),
            verticalAlignment=Alignment.CenterVertically) {
            if(overlayPanel==null || overlayPanel==PlannerPanel.HISTORY)
                PlannerUtilityButton(state,PlannerPanel.HISTORY,count,enabled,overlayPanel!=null)
            Spacer(Modifier.weight(1f).fillMaxHeight().then(if(overlayPanel!=null)
                Modifier.guideTag("planner_dinosaur_jump_pad") else Modifier.testTag("planner_dinosaur_jump_pad")))
            if(overlayPanel==null || overlayPanel==PlannerPanel.LEISURE)
                PlannerUtilityButton(state,PlannerPanel.LEISURE,count,enabled,overlayPanel!=null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlannerUtilityButton(state: PlannerUtilityState,panel: PlannerPanel,count: Int,
    enabled: Boolean,overlay: Boolean) {
    val guide=LocalUsageGuide.current
    val title=if(panel==PlannerPanel.HISTORY) "历史记录" else "随手一翻与 Google Dino"
    val blocked=overlay && panel==PlannerPanel.LEISURE && guide?.stage in setOf(GuideStage.DINO,GuideStage.WAIT_RESULT)
    // A replay remains selected underneath the opposite corner's growing surface.
    // Keep its dark state until that surface actually covers it (and on reveal back).
    val selected=overlay || (panel==PlannerPanel.HISTORY && state.recordId!=null)
    TooltipBox(positionProvider=TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip={ PlainTooltip { Text(if(panel==PlannerPanel.HISTORY) "历史记录 · $count 条" else title) } },
        state=rememberTooltipState(),enableUserInput=enabled) {
        IconButton(onClick={
            if(overlay) { state.closePanel();guide?.closedUtility() }
            else { state.runner.pause();state.panel=panel;guide?.openedUtility(panel) }
        },enabled=enabled && !blocked,modifier=Modifier.guideTag(if(panel==PlannerPanel.HISTORY) "planner_history" else "planner_leisure")
            .then(if(blocked) Modifier.pointerInput(Unit) { detectTapGestures(onTap={}) } else Modifier)
            .semantics { this.selected=selected }) {
            Box(Modifier.size(36.dp).then(if(selected) Modifier.background(MaterialTheme.colorScheme.primary,
                RoundedCornerShape(11.dp)) else Modifier),contentAlignment=Alignment.Center) {
                Icon(if(panel==PlannerPanel.HISTORY) {
                    if(selected) Icons.Filled.History else Icons.Outlined.History
                } else if(selected) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome,
                    title,Modifier.size(24.dp),
                    tint=if(selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PlannerUtilityPanel(utility: PlannerUtilityState, history: List<PlanningHistoryRecord>, notice: String?,
    today: java.time.LocalDate, tone: CampusTone, active: Boolean,
    onHistory: (PlanningHistoryRecord) -> Unit, modifier: Modifier, panel: PlannerPanel) {
    val guide=LocalUsageGuide.current
    // During a lesson Back belongs to the guide directory, not to a hidden
    // utility state change that would remove the lesson's current target.
    BackHandler(active && guide==null) {
        utility.closePanel();guide?.closedUtility()
    }
    val background=plannerUtilityBackground(MaterialTheme.colorScheme,panel)
    // The notice reservation above is page-colored; round the visible panel itself,
    // rather than just the otherwise invisible top of the outer reveal mask.
    Column(modifier.fillMaxSize().clip(RoundedCornerShape(topStart=24.dp,topEnd=24.dp))
        .background(background).guideTag("planner_utility_panel")) {
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).height(48.dp)
            .padding(start = 24.dp, end = 12.dp).testTag("planner_utility_heading"), verticalAlignment = Alignment.CenterVertically) {
            Text(if (panel == PlannerPanel.HISTORY) "历史记录" else if (utility.game) "Google Dino" else "随手一翻",
                Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if (panel == PlannerPanel.LEISURE) {
                IconButton(onClick = { utility.runner.pause(); utility.game = false;guide?.choseGame(false) },
                    enabled = active && guide?.stage !in setOf(GuideStage.DINO,GuideStage.WAIT_RESULT),
                    modifier=Modifier.guideTag("planner_reader_switch")) {
                    Icon(painterResource(R.drawable.ic_poetry_book),"阅读诗文",Modifier.size(24.dp))
                }
                IconButton(onClick = { utility.game = true;guide?.choseGame(true) },
                    enabled = active && guide?.stage !in setOf(GuideStage.READER,GuideStage.WAIT_RESULT),
                    modifier=Modifier.guideTag("planner_game_switch")) { Icon(Icons.Outlined.SportsEsports, "玩 Google Dino") }
            }
            IconButton(onClick = { utility.closePanel();guide?.closedUtility() },
                enabled = active && guide?.stage !in setOf(GuideStage.READER,GuideStage.WAIT_RESULT),
                modifier=Modifier.guideTag("planner_utility_close")) { Icon(Icons.Outlined.Close, "收起面板") }
        }
        if (panel == PlannerPanel.HISTORY) {
            PlanningHistoryPanel(history.filter { it.date == today },utility.recordId,notice,
                active && guide?.stage!=GuideStage.HISTORY,onHistory,Modifier.weight(1f))
        } else if (utility.game) {
            DinosaurRunner(utility.runner, active, Modifier.weight(1f),backgroundColor=background)
        } else {
            LeisureReader(utility, active, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PlanningHistoryPanel(records: List<PlanningHistoryRecord>, selectedId: String?, notice: String?,
    active: Boolean, onHistory: (PlanningHistoryRecord) -> Unit, modifier: Modifier) {
    LazyColumn(modifier.fillMaxWidth().guideTag("planner_history_list"), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp)) {
        item {
            if (notice != null) Text(notice, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
            if (records.isEmpty()) Text("今日暂无历史记录", Modifier.padding(vertical = 28.dp), style = MaterialTheme.typography.bodyMedium)
        }
        items(records, key = { it.id }) { saved ->
            val line=MaterialTheme.colorScheme.primary.copy(alpha=if(saved.id==selectedId) .85f else .28f)
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp).guideTag("history_${saved.id}")
                .clickable(enabled=active,role=Role.Button,
                    interactionSource=remember { MutableInteractionSource() },indication=null) { onHistory(saved) }
                .padding(vertical=9.dp),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f).drawBehind {
                    val stroke=1.5.dp.toPx()
                    drawLine(line,Offset(stroke/2,0f),Offset(stroke/2,size.height),stroke)
                }.padding(start=12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
private fun LeisureReader(utility: PlannerUtilityState, active: Boolean, modifier: Modifier) {
    val guide=LocalUsageGuide.current
    val context=LocalContext.current.applicationContext
    LaunchedEffect(utility,guide) {
        if(utility.item==null) utility.item=utility.nextItem()
        if(guide==null) {
            try { utility.installPoetry(OfflinePoetry.load(context)) }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(_: java.io.IOException) { /* Keep offline seed content if an asset cannot be read. */ }
        }
    }
    val item = utility.item ?: return
    PoetryPageReader(item,active,
        onPreview={ direction -> if(guide?.stage==GuideStage.READER) guide.previewReaderItem(direction) else utility.previewReader(direction) },
        onNavigate={ direction -> if(guide?.stage==GuideStage.READER) guide.nextReaderItem(direction) else utility.moveReader(direction) },
        modifier=modifier)
}
