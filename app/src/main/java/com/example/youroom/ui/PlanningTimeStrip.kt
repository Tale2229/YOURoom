package com.example.youroom.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.roundToInt
import kotlin.math.tan
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.youroom.domain.*
import kotlinx.coroutines.delay
import java.time.Clock
import java.time.LocalTime

@Composable
internal fun PlanningTargetStrip(slots: List<LessonSlot>, windows: List<StudyWindow>, clock: Clock,
    modifier: Modifier=Modifier, actions: PlanningRangeActions? = null) {
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var now by remember(clock) { mutableStateOf(LocalTime.now(clock.withZone(SchoolZone))) }
    LaunchedEffect(clock,lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        while (true) { now=LocalTime.now(clock.withZone(SchoolZone)); delay(60_000L-now.second*1000L-now.nano/1_000_000L) }
    } }
    val dark=MaterialTheme.colorScheme.primary
    val light=MaterialTheme.colorScheme.outlineVariant
    val rtl=LocalLayoutDirection.current==LayoutDirection.Rtl
    val preferences=LocalPeriodStripPreferences.current
    val numberHeight=with(LocalDensity.current) { 10.sp.toDp() }
    PlanningRangeStrip(windows,slots,"planner_target_range","planner_target_range_lines",
        modifier.fillMaxWidth().testTag("planner_target_strip")
        .semantics(mergeDescendants=true) {
            contentDescription="已添加时段："+windows.joinToString("、") { "${planningTime(it.start)}–${planningTime(it.end)}" }
            stateDescription="当前时间 ${now.hour.toString().padStart(2,'0')}:${now.minute.toString().padStart(2,'0')}"
        }, below = true, actions = actions) {
        Box(Modifier.fillMaxWidth().height(36.dp+if(preferences.showNumbers) numberHeight+3.dp else 0.dp).testTag("planner_target_bar")) {
        if(slots.isEmpty()) Row(Modifier.fillMaxWidth().padding(horizontal=24.dp).padding(top=14.dp),
            horizontalArrangement=Arrangement.spacedBy(3.dp)) {
            repeat(14) { Box(Modifier.weight(1f).height(5.dp).testTag("planner_target_period_${it+1}").background(light)) }
        } else PeriodBarsRow(slots,preferences.layout,Modifier.fillMaxWidth().padding(horizontal=24.dp).padding(top=14.dp)) {
            // Keep the initial visual strip even before a school response is available.
            val count=slots.size.takeIf { it>0 } ?: 14
            repeat(count) { index ->
                val slot=slots.getOrNull(index)
                val portions=slot?.let { PlanningDisplay.portions(it,windows) }.orEmpty()
                Column {
                Box(Modifier.fillMaxWidth().height(5.dp).testTag("planner_target_period_${slot?.number ?: index+1}")
                    .semantics { stateDescription=portions.joinToString { "${it.start}–${it.end}" }.ifEmpty { "未选中" } }
                    .drawBehind {
                        drawRect(light)
                        portions.forEach { range -> drawRect(dark,
                            Offset((if (rtl) 1-range.end else range.start)*size.width,0f),
                            Size((range.end-range.start)*size.width,size.height)) }
                    })
                if(preferences.showNumbers) Text((slot?.number ?: index+1).toString(),Modifier.fillMaxWidth().padding(top=3.dp)
                    .testTag("planner_target_number_${slot?.number ?: index+1}").clearAndSetSemantics {},
                    fontSize=8.sp,lineHeight=10.sp,textAlign=TextAlign.Center,maxLines=1,softWrap=false,
                    color=MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.65f))
                }
            }
        }
        Canvas(Modifier.fillMaxSize().padding(horizontal=24.dp).testTag("planner_now_marker")) {
            val x=PlanningDisplay.xAt(now.toSecondOfDay()/60f,slots,size.width,3.dp.roundToPx().toFloat(),preferences.layout) ?: return@Canvas
            val visual=if (rtl) size.width-x else x
            // Hollow isosceles triangle with a 50° downward apex (65° base angles).
            // Scale the previous 10/3dp marker by 1.5, preserving its apex angle.
            // Keep the 1dp outline and include it in the 2dp air gap.
            val base=5.dp.toPx()
            val height=base/(2f*tan(Math.toRadians(25.0)).toFloat())
            val tip=11.5.dp.toPx()
            drawPath(Path().apply {
                moveTo(visual-base/2,tip-height)
                lineTo(visual+base/2,tip-height)
                lineTo(visual,tip)
                close()
            },dark,style=Stroke(1.dp.toPx(),join=StrokeJoin.Round))
        }
        }
    }
}

internal data class PlanningRangeActions(val enabled: Boolean, val onEdit: (Int) -> Unit, val onDelete: (Int) -> Unit)

@Composable
internal fun PlanningRoomStrip(use: PlanningRoomUse, slots: List<LessonSlot>, free: Set<Int>, enabled: Boolean,
    inspectionKey: Any, modifier: Modifier=Modifier) {
    val windows=remember(use.windows) { PlanningDisplay.merge(use.windows) }
    PlanningRangeStrip(windows,slots,"planner_range_${use.classroom.id}","planner_range_lines_${use.classroom.id}",
        modifier.fillMaxWidth().testTag("planner_room_strip_${use.classroom.id}")
            .semantics { contentDescription="计划使用："+windows.joinToString("、") { "${planningTime(it.start)}–${planningTime(it.end)}" } }) {
        RoomAvailabilityStrip(use.classroom.id,free,slots,enabled,inspectionKey=inspectionKey)
    }
}

/** Shared geometry for room use above the bars and editable target periods below them.
 * Keep target windows separate, including touching intervals, so each remains editable. */
@Composable
private fun PlanningRangeStrip(windows: List<StudyWindow>, slots: List<LessonSlot>, labelPrefix: String,
    linesTag: String, modifier: Modifier, below: Boolean = false, actions: PlanningRangeActions? = null,
    bars: @Composable () -> Unit) {
    val color=MaterialTheme.colorScheme.primary
    val paper=MaterialTheme.colorScheme.surface
    val textStyle=MaterialTheme.typography.bodySmall
    val rtl=LocalLayoutDirection.current==LayoutDirection.Rtl
    val preferences=LocalPeriodStripPreferences.current
    SubcomposeLayout(modifier) { constraints ->
        val width=constraints.maxWidth
        val strip=subcompose("availability",bars).single().measure(Constraints.fixedWidth(width))
        if (windows.isEmpty()) {
            return@SubcomposeLayout layout(width,strip.height) { strip.place(0,0) }
        }
        val gutter=24.dp.roundToPx().toFloat()
        val anchors=planningRangeAnchors(windows,slots,width.toFloat(),gutter,3.dp.roundToPx().toFloat(),preferences.layout)
        val labelConstraints=Constraints(maxWidth=(width-2*gutter.roundToInt()).coerceAtLeast(0))
        val labels=subcompose("labels") {
            windows.forEachIndexed { index,window ->
                val anchor=anchors[index]
                var menu by remember(window) { mutableStateOf(false) }
                LaunchedEffect(actions?.enabled) { if (actions?.enabled != true) menu=false }
                val action = if (actions != null) Modifier.clickable(enabled = actions.enabled, role = Role.Button,
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                    onClickLabel = "编辑或删除时段") { menu=true } else Modifier
                Box(Modifier.background(paper)) {
                    Text("${planningTime(window.start)}–${planningTime(window.end)}",
                        Modifier.width(IntrinsicSize.Max).testTag("${labelPrefix}_$index").then(action).semantics {
                        stateDescription=when {
                            !anchor.known -> "暂无节次数据"
                            anchor.outside && anchor.openStart -> "全部位于课前"
                            anchor.outside -> "全部位于课后"
                            anchor.openStart && anchor.openEnd -> "起点、终点开放"
                            anchor.openStart -> "起点开放"
                            anchor.openEnd -> "终点开放"
                            else -> "起点、终点封闭"
                        }
                        if (actions != null) customActions=listOf(
                            CustomAccessibilityAction("编辑时段") { if (actions.enabled) { actions.onEdit(index); true } else false },
                            CustomAccessibilityAction("删除时段") { if (actions.enabled) { actions.onDelete(index); true } else false })
                    }, style=textStyle,color=color,maxLines=1,softWrap=false)
                    if (actions != null) PlanningPeriodMenu(menu,actions.enabled,index,{ menu=false },
                        { menu=false;actions.onEdit(index) },{ menu=false;actions.onDelete(index) })
                }
            }
        }.map { it.measure(labelConstraints) }
        val arrangement=arrangePlanningRanges(anchors,labels.map { PlanningRangeLabel(it.width,it.height) },
            width.toFloat(),gutter,8.dp.toPx(),4.dp.toPx(),5.dp.toPx(),
            windows.indices.sortedWith(compareBy({ windows[it].start },{ windows[it].end },{ it })))
        val annotationHeight=arrangement.annotationHeight
        val bracketY=annotationHeight-2.dp.toPx()
        val capEnd=bracketY+6.dp.toPx()
        val lines=subcompose("lines") {
            Canvas(Modifier.testTag(linesTag)) {
                fun x(value: Float)=if (rtl) size.width-value else value
                fun point(value: Float, y: Float)=Offset(x(value),if (below) size.height-y else y)
                val dash=PathEffect.dashPathEffect(floatArrayOf(1.5.dp.toPx(),2.5.dp.toPx()))
                anchors.forEachIndexed { index,anchor ->
                    if (!anchor.known) return@forEachIndexed
                    val label=arrangement.labels[index]
                    // Joint routing assigns heights by crossing constraints, not
                    // just chronology; keep those heights when mirroring below/RTL.
                    val corridor=arrangement.labelBottom+4.dp.toPx()+label.routeTrack*3.dp.toPx()
                    fun leader(from: Offset,to: Offset) {
                        if (from!=to) drawLine(color.copy(alpha=.65f),from,to,.75.dp.toPx(),pathEffect=dash)
                    }
                    // Wholly outside teaching hours: attach to the corresponding
                    // bar edge, without a fake bracket or an extra outside cap.
                    arrangement.leaders[index].points(label.bottom+2.dp.toPx(),corridor,
                        if (anchor.outside) size.height else bracketY).zipWithNext().forEach { (a,b) ->
                        leader(point(a.x,a.y),point(b.x,b.y))
                    }
                    if (anchor.outside) return@forEachIndexed
                    // Above: caps point down. Below: the whole annotation mirrors,
                    // so caps point up, toward the target bars. Text stays upright.
                    drawLine(color.copy(alpha=.85f),point(anchor.start,bracketY),point(anchor.end,bracketY),.85.dp.toPx())
                    if (!anchor.openStart) drawLine(color.copy(alpha=.85f),point(anchor.start,bracketY),point(anchor.start,capEnd),.85.dp.toPx())
                    if (!anchor.openEnd) drawLine(color.copy(alpha=.85f),point(anchor.end,bracketY),point(anchor.end,capEnd),.85.dp.toPx())
                }
            }
        }.single().measure(Constraints.fixed(width,annotationHeight+8.dp.roundToPx()))
        val height=annotationHeight+strip.height-(if (below) 8.dp.roundToPx() else 0)
        layout(width,height) {
            lines.place(0,if (below) strip.height-16.dp.roundToPx() else 0)
            labels.forEachIndexed { index,label ->
                val position=arrangement.labels[index]
                val y=if (below) strip.height-8.dp.roundToPx()+annotationHeight-position.top-label.height else position.top
                label.place((if (rtl) width-position.left-label.width else position.left).roundToInt(),y.roundToInt())
            }
            strip.place(0,if (below) 0 else annotationHeight)
        }
    }
}
