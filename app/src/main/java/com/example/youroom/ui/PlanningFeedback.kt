package com.example.youroom.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.example.youroom.domain.*
import java.time.Clock

/** Keep the nullable host mounted so clearing feedback also plays its leftward exit. */
@Composable
internal fun PlanningFeedbackHost(feedback: PlanningResult?, goal: PlanningDraft, clock: Clock,
    onGoal: (PlanningDraft) -> Unit, tagPrefix: String = "planner", horizontalPadding: Dp = 24.dp) {
    var retained by remember { mutableStateOf(feedback) }
    SideEffect { if (feedback!=null) retained=feedback }
    val shown=feedback ?: retained
    AnimatedVisibility(feedback!=null, Modifier.fillMaxWidth().clipToBounds().testTag("${tagPrefix}_feedback_host"),
        enter=slideInHorizontally(tween(240)) { -it } + fadeIn(tween(180)) + expandVertically(tween(240),Alignment.Top),
        exit=slideOutHorizontally(tween(200)) { -it } + fadeOut(tween(140)) + shrinkVertically(tween(200),Alignment.Top),
        label="planning feedback") {
        if (shown!=null) PlanningFeedback(shown,goal,clock,feedback!=null,onGoal,tagPrefix,horizontalPadding)
    }
}

@Composable
private fun PlanningFeedback(feedback: PlanningResult, goal: PlanningDraft, clock: Clock, active: Boolean,
    onGoal: (PlanningDraft) -> Unit, tagPrefix: String, horizontalPadding: Dp) {
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
    val actionsWidth=when(feedback.problem) {
        PlanningProblem.INVALID_TIME, PlanningProblem.TOO_SHORT -> 128.dp
        PlanningProblem.PAST_TIME -> 88.dp
        else -> 0.dp
    }
    val actions: @Composable () -> Unit = {
        when(feedback.problem) {
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
            Text(message,modifier.testTag("${tagPrefix}_message"),style=style,color=MaterialTheme.colorScheme.onSurfaceVariant)
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
