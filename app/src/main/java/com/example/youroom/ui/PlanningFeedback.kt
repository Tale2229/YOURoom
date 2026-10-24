package com.example.youroom.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import com.example.youroom.domain.*
import java.time.Clock

internal data class PlanningFeedbackButton(val label: String, val tag: String, val onClick: () -> Unit)

/** Keep the nullable host mounted so clearing feedback also plays its leftward exit. */
@Composable
internal fun PlanningFeedbackHost(feedback: PlanningResult?, goal: PlanningDraft, clock: Clock,
    onGoal: (PlanningDraft) -> Unit, tagPrefix: String = "planner", horizontalPadding: Dp = 24.dp,
    reserveSpace: Boolean = false, trailingAction: PlanningFeedbackButton? = null, enabled: Boolean = true) {
    var retained by remember { mutableStateOf(feedback) }
    var retainedAction by remember { mutableStateOf(trailingAction) }
    SideEffect { if (feedback!=null) { retained=feedback; retainedAction=trailingAction } }
    val shown=feedback ?: retained
    val shownAction=if (feedback!=null) trailingAction else retainedAction
    val content: @Composable () -> Unit = {
        if (reserveSpace) {
            // A completed calculation replaces the pending message with its own leftward entrance.
            // Selection / score / enabled-state changes must not replay this animation.
            key(shown?.message,shown?.problem,shownAction?.label) {
                val visibility=remember { MutableTransitionState(false) }
                visibility.targetState=feedback!=null
                AnimatedVisibility(visibility,Modifier.fillMaxWidth().clipToBounds().testTag("${tagPrefix}_feedback_host"),
                    enter=slideInHorizontally(tween(240)) { -it } + fadeIn(tween(180)),
                    exit=slideOutHorizontally(tween(200)) { -it } + fadeOut(tween(140)),label="reserved planning feedback") {
                    if (shown!=null) PlanningFeedback(shown,goal,clock,feedback!=null && enabled,onGoal,
                        tagPrefix,horizontalPadding,shownAction,announce=true)
                }
            }
        } else AnimatedVisibility(feedback!=null,Modifier.fillMaxWidth().clipToBounds().testTag("${tagPrefix}_feedback_host"),
            enter=slideInHorizontally(tween(240)) { -it } + fadeIn(tween(180)) + expandVertically(tween(240),Alignment.Top),
            exit=slideOutHorizontally(tween(200)) { -it } + fadeOut(tween(140)) + shrinkVertically(tween(200),Alignment.Top),
            label="planning feedback") {
            if (shown!=null) PlanningFeedback(shown,goal,clock,feedback!=null && enabled,onGoal,tagPrefix,horizontalPadding,shownAction)
        }
    }
    if (!reserveSpace) { content(); return }

    // Measure the same typography and action layout before anything is shown. These samples
    // are never placed, drawn or exposed to accessibility. No post-layout height state or
    // animation can resize the game when a pending calculation becomes complete.
    SubcomposeLayout(Modifier.fillMaxWidth().clipToBounds().testTag("${tagPrefix}_feedback_slot")) { constraints ->
        val childConstraints=Constraints.fixedWidth(constraints.maxWidth)
        val reserved=subcompose("reservation") {
            PlanningFeedback(PlanningResult(problem=PlanningProblem.INVALID_TIME),goal,clock,false,{},
                "${tagPrefix}_reserve_invalid",horizontalPadding)
            PlanningFeedback(PlanningResult(problem=PlanningProblem.PAST_TIME),goal,clock,false,{},
                "${tagPrefix}_reserve_past",horizontalPadding)
            PlanningFeedback(PlanningResult(message="计算结束，暂无可行方案"),goal,clock,false,{},
                "${tagPrefix}_reserve_complete",horizontalPadding,PlanningFeedbackButton("查看结果","reserve_view_result") {})
            PlanningFeedback(PlanningResult(message="正在计算，可继续浏览或游戏"),goal,clock,false,{},
                "${tagPrefix}_reserve_pending",horizontalPadding)
        }.map { it.measure(childConstraints) }
        val visible=subcompose("visible") { Box(Modifier.fillMaxWidth()) { content() } }.single().measure(childConstraints)
        val height=constraints.constrainHeight(maxOf(reserved.maxOf { it.height },visible.height))
        layout(constraints.maxWidth,height) { visible.place(0,0) }
    }
}

@Composable
private fun PlanningFeedback(feedback: PlanningResult, goal: PlanningDraft, clock: Clock, active: Boolean,
    onGoal: (PlanningDraft) -> Unit, tagPrefix: String, horizontalPadding: Dp,
    trailingAction: PlanningFeedbackButton? = null, announce: Boolean = false) {
    val message=when(feedback.problem) {
        PlanningProblem.INVALID_TIME -> "结束时间必须晚于开始时间\n暂不支持跨日规划"
        PlanningProblem.PAST_TIME -> "目标时段已早于当前时间\n请选择今天尚未开始的时段"
        PlanningProblem.OVERLAP -> feedback.message.orEmpty().removeSuffix("。").removeSuffix(".")
        else -> feedback.message.orEmpty()
    }
    val style=MaterialTheme.typography.bodySmall
    val measurer=rememberTextMeasurer()
    val density=LocalDensity.current
    val textWidth=remember(message,style,density) {
        message.lines().maxOf { measurer.measure(it,style).size.width }
    }
    val trailingWidth=trailingAction?.let {
        with(density) { measurer.measure(it.label,MaterialTheme.typography.labelMedium).size.width.toDp()+20.dp }.coerceAtLeast(88.dp)
    } ?: 0.dp
    val actionsWidth=if (trailingAction!=null) trailingWidth else when(feedback.problem) {
        PlanningProblem.INVALID_TIME, PlanningProblem.TOO_SHORT -> 128.dp
        PlanningProblem.PAST_TIME -> 88.dp
        else -> 0.dp
    }
    val actions: @Composable () -> Unit = {
        if (trailingAction!=null) PlanningFeedbackAction(trailingAction.label,trailingAction.tag,trailingWidth,
            active,trailingAction.onClick)
        else when(feedback.problem) {
            PlanningProblem.PAST_TIME -> PlanningFeedbackAction("从现在起","${tagPrefix}_from_now",88.dp,
                active && LocalUsageGuide.current?.stage!=GuideStage.PAST && PlanningTimeActions.startFromNow(goal,clock)!=null) {
                PlanningTimeActions.startFromNow(goal,clock)?.let(onGoal)
            }
            PlanningProblem.INVALID_TIME, PlanningProblem.TOO_SHORT -> Row(Modifier.guideTag("${tagPrefix}_duration_buttons"),
                horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                listOf(1,2,4).forEach { hours ->
                    PlanningFeedbackAction("${hours}h","${tagPrefix}_duration_${hours}h",40.dp,
                        active && PlanningTimeActions.endAfterHours(goal,hours)!=null) {
                        PlanningTimeActions.endAfterHours(goal,hours)?.let(onGoal)
                    }
                }
            }
            else -> Unit
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal=horizontalPadding,vertical=12.dp).testTag("${tagPrefix}_feedback")) {
        val stacked=actionsWidth>0.dp && with(density) { textWidth+actionsWidth.toPx()+8.dp.toPx()>maxWidth.toPx() }
        val text: @Composable (Modifier) -> Unit = { modifier ->
            Text(message,modifier.testTag("${tagPrefix}_message").then(if (announce) Modifier.semantics {
                liveRegion=LiveRegionMode.Polite
            } else Modifier),style=style,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (stacked) Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            text(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) { actions() }
        } else Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            text(Modifier.weight(1f)); actions()
        }
    }
}

@Composable
private fun PlanningFeedbackAction(label: String, tag: String, width: androidx.compose.ui.unit.Dp,
    enabled: Boolean, onClick: () -> Unit) {
    val faceHeight=with(LocalDensity.current) { (28.dp.toPx()+2f).toDp() }
    // Separate 40dp hit areas; a smaller face doesn't make adjacent shortcuts overlap.
    Box(Modifier.width(width).height(40.dp).guideTag(tag).clickable(enabled=enabled,role=Role.Button,
        interactionSource=remember { MutableInteractionSource() },indication=null,onClick=onClick),
        contentAlignment=Alignment.Center) {
        Surface(Modifier.width(width-4.dp).height(faceHeight).testTag("${tag}_face"),shape=MaterialTheme.shapes.small,
            color=MaterialTheme.colorScheme.primaryContainer.copy(alpha=if(enabled) 1f else .38f),
            contentColor=MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha=if(enabled) 1f else .38f)) {
            Box(contentAlignment=Alignment.Center) { Text(label,style=MaterialTheme.typography.labelMedium) }
        }
    }
}
