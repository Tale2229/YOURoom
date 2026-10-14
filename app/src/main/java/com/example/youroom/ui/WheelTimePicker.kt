package com.example.youroom.ui

import androidx.compose.animation.SplineBasedFloatDecayAnimationSpec
import androidx.compose.animation.core.generateDecayAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.snapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import com.example.youroom.domain.planningTime
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.*

// Virtual repeated rows provide native drag/fling/snap physics, without allocating a long list.
internal const val TimeWheelCycles = 2001
internal fun timeWheelValue(index: Int, count: Int) = Math.floorMod(index, count)
internal fun timeWheelInitialIndex(value: Int, count: Int) = (TimeWheelCycles / 2) * count + value
internal fun timeWheelNearestIndex(value: Int, around: Int, count: Int): Int {
    val cycle = around / count
    return (cycle - 1..cycle + 1).map { it * count + value }
        .filter { it in 0 until count * TimeWheelCycles }.minBy { abs(it - around) }
}

// Equal angular steps around a cylinder, not equal screen-space rows. Fractional distance
// changes continuously during dragging, inertia and snapping, including between ticks.
internal const val TimeWheelAngle = .46f
internal data class WheelProjection(val y: Float, val rotation: Float, val scale: Float, val alpha: Float)
internal fun timeWheelProjection(distance: Float): WheelProjection {
    val angle = (distance * TimeWheelAngle).coerceIn(-PI.toFloat() / 2, PI.toFloat() / 2)
    val facing = cos(angle).coerceAtLeast(0f)
    val depth = (1f - facing) / TimeWheelAngle
    val perspective = 8f / (8f + depth)
    return WheelProjection(sin(angle) / TimeWheelAngle * perspective,
        -angle * 180f / PI.toFloat(), (.82f + .18f * facing) * perspective,
        facing.pow(2.2f) * .9f + if (abs(distance) < .5f) .1f * (1f - abs(distance) * 2f) else 0f)
}

internal fun timeWheelTappedOffset(yInRows: Float): Int = (-3..3)
    .minBy { abs(timeWheelProjection(it.toFloat()).y - yInRows) }

private fun LazyListLayoutInfo.centerPosition(fallback: Int): Float {
    val center = (viewportStartOffset + viewportEndOffset) / 2f
    val item = visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2f - center) }
        ?: return fallback.toFloat()
    return item.index - (item.offset + item.size / 2f - center) / item.size
}

@Composable
internal fun WheelTimePicker(title: String, minuteOfDay: Int, tag: String,
    modifier: Modifier = Modifier, onMoving: (Boolean) -> Unit = {}, onTime: (Int) -> Unit) {
    var settledHour by remember { mutableIntStateOf(minuteOfDay / 60) }
    var settledMinute by remember { mutableIntStateOf(minuteOfDay % 60) }
    var lastExternal by remember { mutableIntStateOf(minuteOfDay) }
    var hourMoving by remember { mutableStateOf(false) }
    var minuteMoving by remember { mutableStateOf(false) }
    val currentOnTime by rememberUpdatedState(onTime)
    val currentOnMoving by rememberUpdatedState(onMoving)
    // Shortcuts/restoration update both wheels. Independent settlements combine synchronously
    // against local values, avoiding a stale callback overwriting the other column.
    SideEffect {
        if (lastExternal != minuteOfDay) {
            lastExternal = minuteOfDay
            settledHour = minuteOfDay / 60
            settledMinute = minuteOfDay % 60
        }
    }
    LaunchedEffect(hourMoving, minuteMoving) { currentOnMoving(hourMoving || minuteMoving) }
    DisposableEffect(Unit) { onDispose { currentOnMoving(false) } }
    Column(modifier.testTag(tag).semantics {
        text = AnnotatedString(planningTime(minuteOfDay))
        stateDescription = "$title ${planningTime(minuteOfDay)}"
    }, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TimeWheel("小时", 24, minuteOfDay / 60, "${tag}_hour_wheel", Modifier.weight(1f), {
                settledHour = it
                currentOnTime(settledHour * 60 + settledMinute)
            }, { hourMoving = it })
            Text(":", Modifier.offset { IntOffset(0, -2) }.width(12.dp)
                .testTag("${tag}_colon").clearAndSetSemantics {},
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = MaterialTheme.typography.headlineMedium.fontSize * .8f), color = MaterialTheme.colorScheme.primary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            TimeWheel("分钟", 60, minuteOfDay % 60, "${tag}_minute_wheel", Modifier.weight(1f), {
                settledMinute = it
                currentOnTime(settledHour * 60 + settledMinute)
            }, { minuteMoving = it })
        }
    }
}

@Composable
private fun TimeWheel(unit: String, count: Int, value: Int, tag: String, modifier: Modifier,
    onValue: (Int) -> Unit, onMoving: (Boolean) -> Unit) {
    val initial = remember { timeWheelInitialIndex(value, count) }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = initial)
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val currentHaptic by rememberUpdatedState(haptic)
    var externalAnimation by remember { mutableStateOf(false) }
    var synchronizedValue by remember { mutableIntStateOf(value) }
    val currentValue by rememberUpdatedState(value)
    val currentOnValue by rememberUpdatedState(onValue)
    val currentOnMoving by rememberUpdatedState(onMoving)
    val position by remember { derivedStateOf { list.layoutInfo.centerPosition(initial) } }
    val centered by remember { derivedStateOf { position.roundToInt() } }
    val rowHeight = with(LocalDensity.current) {
        MaterialTheme.typography.headlineMedium.lineHeight.toDp() + 12.dp
    }.coerceAtLeast(48.dp)
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }
    val density = LocalDensity.current
    val fling = remember(list, density) {
        snapFlingBehavior(SnapLayoutInfoProvider(list, SnapPosition.Center),
            TimeWheelDecay(SplineBasedFloatDecayAnimationSpec(density)).generateDecayAnimationSpec(),
            spring(dampingRatio = 1f, stiffness = 400f))
    }
    val colors = MaterialTheme.colorScheme
    LaunchedEffect(value, rowPx) {
        if (timeWheelValue(centered, count) != value) {
            val target = timeWheelNearestIndex(value, centered, count)
            externalAnimation = true
            try {
                // A low-stiffness, lightly damped spring accelerates, coasts and settles with a
                // small inertial tail. New targets or direct dragging cancel this motion safely.
                list.animateScrollBy((target - position) * rowPx,
                    spring(dampingRatio = .88f, stiffness = 85f, visibilityThreshold = .1f))
                list.scrollToItem(target)
            } finally {
                // A cancelled old animation may report idle before the new one starts. Only
                // acknowledge its target if it is still the controlled value; a replacement
                // target remains pending and cannot be overwritten by that old idle event.
                if (currentValue == value) synchronizedValue = value
                externalAnimation = false
            }
        } else synchronizedValue = value
    }
    LaunchedEffect(list) {
        var previous = list.layoutInfo.centerPosition(initial)
        var wasMoving = false
        snapshotFlow { list.layoutInfo.centerPosition(initial) to list.isScrollInProgress }
            .distinctUntilChanged().collect { (next, moving) ->
                if (moving || wasMoving) repeat(timeWheelCenterCrossings(previous, next)) {
                    currentHaptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                }
                previous = next
                wasMoving = moving
            }
    }
    LaunchedEffect(list) {
        var wasMoving = false
        snapshotFlow { list.isScrollInProgress }.distinctUntilChanged().collect { moving ->
            currentOnMoving(moving)
            if (wasMoving && !moving && !externalAnimation && synchronizedValue == currentValue) {
                val settled = timeWheelValue(list.layoutInfo.centerPosition(initial).roundToInt(), count)
                if (settled != currentValue) currentOnValue(settled)
            }
            wasMoving = moving
        }
    }
    fun select(index: Int) {
        scope.launch { list.animateScrollToItem(index.coerceIn(0, count * TimeWheelCycles - 1)) }
    }
    Box(modifier.widthIn(min = 48.dp).height(rowHeight * 4.5f).clipToBounds()) {
        Box(Modifier.align(Alignment.Center).fillMaxWidth().height(rowHeight)
            .background(colors.primary.copy(alpha = .045f), MaterialTheme.shapes.small))
        // Invisible list owns touch/velocity and accessibility. Visible digits are separately
        // projected, so outer rows remain on the curved face throughout the gesture.
        LazyColumn(state = list, flingBehavior = fling,
            contentPadding = PaddingValues(vertical = rowHeight * 1.75f),
            modifier = Modifier.fillMaxSize().testTag(tag).semantics {
                contentDescription = unit
                stateDescription = "%02d".format(java.util.Locale.ROOT, timeWheelValue(centered, count))
                progressBarRangeInfo = ProgressBarRangeInfo(timeWheelValue(centered, count).toFloat(),
                    0f..(count - 1).toFloat(), count - 2)
                setProgress { requested ->
                    select(timeWheelNearestIndex(requested.roundToInt().coerceIn(0, count - 1), centered, count)); true
                }
                customActions = listOf(
                    CustomAccessibilityAction("上一$unit") { select(centered - 1); true },
                    CustomAccessibilityAction("下一$unit") { select(centered + 1); true })
            }.onKeyEvent {
                if (it.type == KeyEventType.KeyDown && (it.key == Key.DirectionUp || it.key == Key.DirectionDown)) {
                    select(centered + if (it.key == Key.DirectionUp) -1 else 1); true
                } else false
            }.focusable().pointerInput(rowPx) {
                detectTapGestures { tap ->
                    select(centered + timeWheelTappedOffset((tap.y - size.height / 2f) / rowPx))
                }
            }) {
            items(count * TimeWheelCycles) { Spacer(Modifier.fillMaxWidth().height(rowHeight)) }
        }
        for (offset in -3..3) {
            val index = centered + offset
            if (index !in 0 until count * TimeWheelCycles) continue
            val distance = index - position
            Text("%02d".format(java.util.Locale.ROOT, timeWheelValue(index, count)),
                Modifier.align(Alignment.Center).clearAndSetSemantics {}.graphicsLayer {
                    val projection = timeWheelProjection(index - position)
                    translationY = projection.y * rowPx
                    rotationX = projection.rotation
                    scaleX = projection.scale
                    scaleY = projection.scale
                    alpha = projection.alpha
                    cameraDistance = rowPx * 12f
                }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium,
                color = lerp(colors.onSurfaceVariant, colors.primary, (1f - abs(distance)).coerceIn(0f, 1f)))
        }
    }
}
