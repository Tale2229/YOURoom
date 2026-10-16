package com.example.youroom.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.youroom.domain.PlanningDraft
import kotlin.math.roundToInt

/** One measured editor: scale the entire wheels (including titles), not just the digits.
 * Compact 40dp targets stay separate; the last face ends 5 physical pixels below the wheels. */
@Composable
internal fun PlanningTimeControls(goal: PlanningDraft, addEnabled: Boolean, expectedEnabled: Boolean,
    flexibleEnabled: Boolean, generateEnabled: Boolean, expectedCount: Int, pending: Boolean,
    onStartMoving: (Boolean) -> Unit, onEndMoving: (Boolean) -> Unit,
    onStart: (Int) -> Unit, onEnd: (Int) -> Unit,
    onAdd: () -> Unit, onExpected: () -> Unit, onFlexible: () -> Unit, onGenerate: () -> Unit,
    cancelEnabled: Boolean, onCancel: () -> Unit,
    addAccepted: Boolean = false, addRejection: Int = 0, expectedAccepted: Boolean = false) {
    val density = LocalDensity.current
    val shake = remember { Animatable(0f) }
    LaunchedEffect(addRejection) {
        shake.snapTo(0f)
        if (addRejection > 0) shake.animateTo(0f, keyframes {
            durationMillis = 280
            -1f at 35; 1f at 70; -.75f at 110; .65f at 150; -.35f at 195; .15f at 235; 0f at 280
        })
    }
    Layout(modifier = Modifier.fillMaxWidth().testTag("planner_time_controls"), content = {
        CompositionLocalProvider(LocalDensity provides Density(density.density * .85f, density.fontScale)) {
            Row(Modifier.testTag("planner_wheels"), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WheelTimePicker("开始时间", goal.start, "planner_start", Modifier.weight(1f),
                    onMoving = onStartMoving, viewportRows = 3.5f, onTime = onStart)
                WheelTimePicker("结束时间", goal.end, "planner_end", Modifier.weight(1f),
                    onMoving = onEndMoving, viewportRows = 3.5f, onTime = onEnd)
            }
        }
        Column(Modifier.testTag("planner_actions"), verticalArrangement = Arrangement.SpaceBetween) {
            PlanningRailAction("添加时段", "planner_add_period", addEnabled && !addAccepted,
                confirmation = if(addAccepted) "已添加" else null, briefFeedback = true, onClick = onAdd,
                modifier = Modifier.graphicsLayer { translationX = shake.value * 4.dp.toPx() }) {
                Icon(Icons.Outlined.Add, null, Modifier.size(22.dp))
            }
            Box {
                PlanningRailAction("期望教室", "planner_expected", expectedEnabled && !expectedAccepted,
                    confirmation = if(expectedAccepted) "已打开" else null, briefFeedback = true, onClick = onExpected) {
                    Icon(Icons.Outlined.BookmarkBorder, null, Modifier.size(22.dp))
                }
                if (expectedCount > 0) Badge(Modifier.align(Alignment.TopEnd).offset(x = 1.dp, y = 1.dp)
                    .testTag("planner_expected_badge").semantics(mergeDescendants = true) {},
                    containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                    Text(expectedCount.toString())
                }
            }
            PlanningRailAction("宽松模式", "planner_adjust", flexibleEnabled, selected = goal.flexible,
                toggle = true, onClick = onFlexible) { FlexibleTimeIcon(Modifier.size(22.dp)) }
            PlanningRailAction(if (pending) "终止规划" else "生成规划", "planner_generate",
                if (pending) cancelEnabled else generateEnabled, primary = true,
                pending = pending, onClick = if (pending) onCancel else onGenerate) {
                Icon(if (pending) Icons.Filled.Stop else Icons.Filled.PlayArrow, null,
                    Modifier.size(if (pending) 22.dp else 24.dp).testTag("${if(pending) "planner_stop" else "planner_play"}_icon"))
            }
        }
    }) { measurables, constraints ->
        val width = constraints.maxWidth
        val railWidth = 40.dp.roundToPx()
        // The wheels keep their measured size; only this gap and the rail width
        // change, so the four visible controls read as compact rounded squares.
        val gap = 14.dp.roundToPx()
        val wheels = measurables[0].measure(Constraints.fixedWidth((width - railWidth - gap).coerceAtLeast(0)))
        // Keep the visible top 4dp lower and bottom exactly 5 physical px lower.
        val railTop = 2.dp.roundToPx()
        val railHeight = maxOf(160.dp.roundToPx(),(wheels.height+5f+2.dp.toPx()-railTop).roundToInt())
        val height = maxOf(wheels.height,railTop+railHeight)
        val rail = measurables[1].measure(Constraints.fixed(railWidth, railHeight))
        layout(width, height) {
            wheels.placeRelative(0, 0)
            rail.placeRelative(width - railWidth, railTop)
        }
    }
}

@Composable
private fun PlanningRailAction(label: String, tag: String, enabled: Boolean, selected: Boolean = false,
    toggle: Boolean = false, primary: Boolean = false, pending: Boolean = false, confirmation: String? = null,
    briefFeedback: Boolean = false,
    onClick: () -> Unit, modifier: Modifier = Modifier, icon: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(if (primary) colors.primary.copy(alpha = if (enabled || pending) 1f else .38f)
        else if (selected || confirmation!=null) colors.primaryContainer else colors.surface,
        tween(if(briefFeedback) { if(confirmation!=null) 16 else 24 } else 100), label = "planning action state")
    val action = if (toggle) Modifier.toggleable(selected, enabled = enabled, role = Role.Checkbox,
        interactionSource = interaction, indication = null, onValueChange = { onClick() })
    else Modifier.clickable(enabled = enabled, role = Role.Button, interactionSource = interaction,
        indication = null, onClick = onClick)
    Box(modifier.size(40.dp).testTag(tag).then(action).semantics {
        contentDescription = label
        if (confirmation!=null) stateDescription = confirmation else if (pending) stateDescription = "计算中"
    }, contentAlignment = Alignment.Center) {
        Surface(Modifier.size(36.dp)
            .testTag("${tag}_face"), shape = MaterialTheme.shapes.small,
            color = background, contentColor = if (primary) colors.onPrimary else if (enabled || confirmation!=null) colors.primary
                else colors.onSurface.copy(alpha = .38f),
            border = if (primary || selected || confirmation!=null) null else BorderStroke(1.dp, colors.outlineVariant)) {
            Box(contentAlignment = Alignment.Center) { icon() }
        }
    }
}

/** A broken timer rim suggests adjustable time, not faster computation. */
@Composable
private fun FlexibleTimeIcon(modifier: Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier) {
        val unit = size.minDimension / 24f
        fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
        fun line(x: Float, y: Float, x2: Float, y2: Float) =
            drawLine(color, point(x,y), point(x2,y2), 1.55f * unit, StrokeCap.Round)
        line(10f,2.7f,14f,2.7f); line(12f,2.7f,12f,4.9f); line(17.4f,5.8f,18.8f,4.5f)
        // The solid arc ends at 150 degrees; keep each short dash and its
        // rounded caps clear of it and of the next dash (including the wrap).
        listOf(-70f to 220f, 177f to 10f, 217f to 10f, 257f to 10f).forEach { (start, sweep) ->
            drawArc(color,start,sweep,false,point(4.7f,6.2f),Size(14.6f * unit,14.6f * unit),
                style = Stroke(1.55f * unit, cap = StrokeCap.Round))
        }
        line(12f,9.3f,12f,13.5f); line(12f,13.5f,14.9f,15.3f)
    }
}
