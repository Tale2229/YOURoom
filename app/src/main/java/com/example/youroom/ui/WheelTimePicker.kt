package com.example.youroom.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// Repeated values allow both wheels to cross midnight without changing the other column.
// LazyColumn composes only the visible rows, not all the repeated cycles.
internal const val TimeWheelCycles = 2001
internal fun timeWheelValue(index: Int, count: Int) = Math.floorMod(index, count)
internal fun timeWheelInitialIndex(value: Int, count: Int) = (TimeWheelCycles / 2) * count + value
internal fun timeWheelNearestIndex(value: Int, around: Int, count: Int): Int {
    val cycle = around / count
    return (cycle - 1..cycle + 1).map { it * count + value }
        .filter { it in 0 until count * TimeWheelCycles }.minBy { abs(it - around) }
}

private fun LazyListLayoutInfo.centeredIndex(fallback: Int): Int {
    val center = (viewportStartOffset + viewportEndOffset) / 2f
    return visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2f - center) }?.index ?: fallback
}

@Composable
internal fun WheelTimePickerDialog(title: String, initialMinute: Int, onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit) {
    var hour by rememberSaveable(initialMinute) { mutableIntStateOf(initialMinute / 60) }
    var minute by rememberSaveable(initialMinute) { mutableIntStateOf(initialMinute % 60) }
    var hourMoving by remember { mutableStateOf(false) }
    var minuteMoving by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, shape = MaterialTheme.shapes.medium,
        containerColor = MaterialTheme.colorScheme.background,
        title = { Text(title) },
        text = {
            Row(Modifier.fillMaxWidth().testTag("planner_time_input"),
                horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                TimeWheel("时", 24, hour, "planner_hour_wheel", Modifier.weight(1f),
                    { hour = it }, { hourMoving = it })
                TimeWheel("分", 60, minute, "planner_minute_wheel", Modifier.weight(1f),
                    { minute = it }, { minuteMoving = it })
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(hour * 60 + minute) },
            enabled = !hourMoving && !minuteMoving, modifier = Modifier.testTag("planner_time_confirm")) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("planner_time_cancel")) { Text("取消") } })
}

@Composable
private fun TimeWheel(unit: String, count: Int, value: Int, tag: String, modifier: Modifier,
    onValue: (Int) -> Unit, onMoving: (Boolean) -> Unit) {
    val initial = remember { timeWheelInitialIndex(value, count) }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = initial)
    val scope = rememberCoroutineScope()
    val currentOnValue by rememberUpdatedState(onValue)
    val currentOnMoving by rememberUpdatedState(onMoving)
    val centered by remember { derivedStateOf { list.layoutInfo.centeredIndex(initial) } }
    val rowHeight = with(LocalDensity.current) {
        MaterialTheme.typography.headlineMedium.lineHeight.toDp() + 12.dp
    }.coerceAtLeast(48.dp)
    val colors = MaterialTheme.colorScheme
    LaunchedEffect(list) {
        snapshotFlow { list.layoutInfo.centeredIndex(initial) to list.isScrollInProgress }
            .distinctUntilChanged().collect { (index, moving) ->
                currentOnValue(timeWheelValue(index, count))
                currentOnMoving(moving)
            }
    }
    fun select(index: Int) {
        scope.launch { list.animateScrollToItem(index.coerceIn(0, count * TimeWheelCycles - 1)) }
    }
    Box(modifier.height(rowHeight * 5)) {
        // A quiet center guide; the number and unit carry the selection, not a filled card.
        Column(Modifier.align(Alignment.Center).fillMaxWidth().height(rowHeight),
            verticalArrangement = Arrangement.SpaceBetween) {
            HorizontalDivider(color = colors.outlineVariant)
            HorizontalDivider(color = colors.outlineVariant)
        }
        LazyColumn(state = list, flingBehavior = rememberSnapFlingBehavior(list, SnapPosition.Center),
            contentPadding = PaddingValues(vertical = rowHeight * 2),
            modifier = Modifier.fillMaxSize().testTag(tag).semantics {
                contentDescription = if (unit == "时") "小时" else "分钟"
                stateDescription = "%02d%s".format(java.util.Locale.ROOT, value, unit)
                progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), 0f..(count - 1).toFloat(), count - 2)
                setProgress { requested ->
                    select(timeWheelNearestIndex(requested.roundToInt().coerceIn(0, count - 1), centered, count)); true
                }
                customActions = listOf(
                    CustomAccessibilityAction("上一${if (unit == "时") "小时" else "分钟"}") { select(centered - 1); true },
                    CustomAccessibilityAction("下一${if (unit == "时") "小时" else "分钟"}") { select(centered + 1); true })
            }.onKeyEvent {
                if (it.type == KeyEventType.KeyDown && (it.key == Key.DirectionUp || it.key == Key.DirectionDown)) {
                    select(centered + if (it.key == Key.DirectionUp) -1 else 1); true
                } else false
            }.focusable()) {
            items(count * TimeWheelCycles) { index ->
                val distance = abs(index - centered)
                val selected = distance == 0
                Box(Modifier.fillMaxWidth().height(rowHeight).clickable { select(index) }
                    .clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                    Text("%02d".format(java.util.Locale.ROOT, timeWheelValue(index, count)),
                        color = if (selected) colors.primary else colors.onSurface.copy(alpha = if (distance == 1) .65f else .28f),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
        Text(unit, Modifier.align(Alignment.CenterEnd).padding(end = 2.dp).clearAndSetSemantics {},
            style = MaterialTheme.typography.bodySmall, color = colors.primary)
    }
}
