package com.example.youroom.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.youroom.domain.PlanningDraft

/** One measured editor: scale the entire wheels (including titles), not just the digits.
 * The rail always keeps four separate 48dp targets; its last face may sit slightly lower. */
@Composable
internal fun PlanningTimeControls(goal: PlanningDraft, addEnabled: Boolean, expectedEnabled: Boolean,
    flexibleEnabled: Boolean, generateEnabled: Boolean, expectedCount: Int, pending: Boolean,
    onStartMoving: (Boolean) -> Unit, onEndMoving: (Boolean) -> Unit,
    onStart: (Int) -> Unit, onEnd: (Int) -> Unit,
    onAdd: () -> Unit, onExpected: () -> Unit, onFlexible: () -> Unit, onGenerate: () -> Unit) {
    val density = LocalDensity.current
    Layout(modifier = Modifier.fillMaxWidth().testTag("planner_time_controls"), content = {
        CompositionLocalProvider(LocalDensity provides Density(density.density * .9f, density.fontScale)) {
            Row(Modifier.testTag("planner_wheels"), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WheelTimePicker("开始时间", goal.start, "planner_start", Modifier.weight(1f),
                    onMoving = onStartMoving, viewportRows = 3.5f, onTime = onStart)
                WheelTimePicker("结束时间", goal.end, "planner_end", Modifier.weight(1f),
                    onMoving = onEndMoving, viewportRows = 3.5f, onTime = onEnd)
            }
        }
        Column(Modifier.testTag("planner_actions"), verticalArrangement = Arrangement.SpaceBetween) {
            PlanningRailAction("添加时段", "planner_add_period", addEnabled, onClick = onAdd) {
                Icon(Icons.Outlined.Add, null, Modifier.size(22.dp))
            }
            Box {
                PlanningRailAction("期望教室", "planner_expected", expectedEnabled,
                    selected = expectedCount > 0, onClick = onExpected) {
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
            PlanningRailAction("生成规划", "planner_generate", generateEnabled, primary = true,
                pending = pending, onClick = onGenerate) {
                if (pending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 1.5.dp,
                    color = MaterialTheme.colorScheme.onPrimary)
                else Icon(Icons.Filled.PlayArrow, null, Modifier.size(24.dp))
            }
        }
    }) { measurables, constraints ->
        val width = constraints.maxWidth
        val railWidth = 48.dp.roundToPx()
        val gap = 12.dp.roundToPx()
        val wheels = measurables[0].measure(Constraints.fixedWidth((width - railWidth - gap).coerceAtLeast(0)))
        val height = maxOf(wheels.height, 192.dp.roundToPx())
        val rail = measurables[1].measure(Constraints.fixed(railWidth, height))
        layout(width, height) {
            wheels.placeRelative(0, 0)
            rail.placeRelative(width - railWidth, 0)
        }
    }
}

@Composable
private fun PlanningRailAction(label: String, tag: String, enabled: Boolean, selected: Boolean = false,
    toggle: Boolean = false, primary: Boolean = false, pending: Boolean = false,
    onClick: () -> Unit, icon: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(if (primary) colors.primary.copy(alpha = if (enabled || pending) 1f else .38f)
        else if (selected) colors.primaryContainer else colors.surface, tween(140), label = "planning action state")
    val action = if (toggle) Modifier.toggleable(selected, enabled = enabled, role = Role.Checkbox,
        interactionSource = interaction, indication = null, onValueChange = { onClick() })
    else Modifier.clickable(enabled = enabled, role = Role.Button, interactionSource = interaction,
        indication = null, onClick = onClick)
    Box(Modifier.size(48.dp).testTag(tag).then(action).semantics {
        contentDescription = label
        if (pending) stateDescription = "计算中"
    }, contentAlignment = Alignment.Center) {
        Surface(Modifier.size(38.dp).testTag("${tag}_face"), shape = MaterialTheme.shapes.small,
            color = background, contentColor = if (primary) colors.onPrimary else if (enabled) colors.primary
                else colors.onSurface.copy(alpha = .38f),
            border = if (primary || selected) null else BorderStroke(1.dp, colors.outlineVariant)) {
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
        listOf(-70f to 240f, 181f to 15f, 210f to 15f, 239f to 15f).forEach { (start, sweep) ->
            drawArc(color,start,sweep,false,point(4.7f,6.2f),Size(14.6f * unit,14.6f * unit),
                style = Stroke(1.55f * unit, cap = StrokeCap.Round))
        }
        line(12f,9.3f,12f,13.5f); line(12f,13.5f,14.9f,15.3f)
    }
}
