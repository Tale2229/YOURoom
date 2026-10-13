package com.example.youroom.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import com.example.youroom.domain.LessonSlot
import com.example.youroom.domain.PeriodStripGeometry
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private class PeriodStripSize { var width = 0f }

/** A single popup stays attached while its anchor moves between the strip's original bars. */
internal class PeriodStripTooltipPositionProvider(private val index: Int, private val count: Int,
    private val gutter: Int, private val gap: Int, private val top: Int, private val lift: Int,
    private val barHeight: Int, private val delegate: PopupPositionProvider) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
        layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val width = (anchorBounds.width - 2 * gutter - gap * (count - 1)).toFloat() / count
        val visual = if (layoutDirection == LayoutDirection.Rtl) count - 1 - index else index
        val left = anchorBounds.left + gutter + (visual * (width + gap)).roundToInt()
        val y = anchorBounds.top + top - lift
        return delegate.calculatePosition(IntRect(left, y, left + width.roundToInt(), y + barHeight),
            windowSize, layoutDirection, popupContentSize)
    }
}

@Composable
internal fun RoomAvailabilityStrip(roomId: String, freeSlots: Set<Int>, slots: List<LessonSlot>,
    enabled: Boolean, modifier: Modifier = Modifier, inspectionKey: Any? = null) {
    // Reset transient inspection when the room, response/date, or real period times change.
    var inspected by remember(roomId, slots, inspectionKey) { mutableStateOf<Int?>(null) }
    var pointerHeld by remember(roomId, slots, inspectionKey) { mutableStateOf(false) }
    val geometry = remember { PeriodStripSize() }
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val haptic = LocalHapticFeedback.current
    val gutter = with(density) { 24.dp.roundToPx().toFloat() }
    val gap = with(density) { 3.dp.roundToPx().toFloat() }
    fun numberAt(x: Float): Int? = PeriodStripGeometry.nearestIndex(x - gutter,
        geometry.width - 2 * gutter, slots.size, gap, direction == LayoutDirection.Rtl)?.let { slots[it].number }
    LaunchedEffect(enabled) { if (!enabled) { inspected = null; pointerHeld = false } }
    LaunchedEffect(pointerHeld, inspected) {
        // Accessibility/keyboard inspection has no pointer release to dismiss it.
        if (!pointerHeld && inspected != null) { delay(2_000); inspected = null }
    }
    fun inspectAccessibly(number: Int): Boolean { inspected = number; return true }
    Box(modifier.fillMaxWidth().testTag("room_period_strip_$roomId")
        .onSizeChanged { geometry.width = it.width.toFloat() }
        .pointerInput(roomId, slots, inspectionKey, enabled, direction, gutter, gap) {
            awaitEachGesture {
                val down = awaitFirstDown(pass = PointerEventPass.Initial)
                if (!enabled || slots.isEmpty()) return@awaitEachGesture
                inspected = null
                var x = down.position.x
                try {
                    // Observe without consuming during the dwell. Any real movement, up,
                    // second finger, or a parent's claim cancels; scrolling stays ordinary.
                    val interrupted = withTimeoutOrNull(250L) {
                        var done = false
                        while (!done) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            done = change == null || change.isConsumed || !change.pressed ||
                                event.changes.count { it.pressed } > 1 ||
                                (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                            if (!done) {
                                x = change!!.position.x
                                val final = awaitPointerEvent(PointerEventPass.Final)
                                done = final.changes.firstOrNull { it.id == down.id }?.isConsumed != false
                            }
                        }
                        true
                    }
                    if (interrupted != null) return@awaitEachGesture
                    pointerHeld = true
                    inspected = numberAt(x)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    // Own the rest of this pointer sequence, even outside the original row.
                    // Vertical movement never scrolls or changes rooms after activation.
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        val cancelled = change == null || change.isConsumed || event.changes.count { it.pressed } > 1
                        event.changes.forEach { it.consume() }
                        if (cancelled || change?.pressed != true) break
                        inspected = numberAt(change.position.x)
                    }
                } finally { inspected = null; pointerHeld = false }
            }
        }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            slots.forEach { slot -> key(slot.number) {
                val shown = enabled && inspected == slot.number
                val lift by animateDpAsState(if (shown) (-4).dp else 0.dp, tween(90), label = "period_inspection_lift")
                Box(Modifier.weight(1f).height(5.dp).offset { IntOffset(0, lift.roundToPx()) }
                    .testTag("room_period_${roomId}_${slot.number}")
                    .onKeyEvent { event ->
                        if (enabled && event.type == KeyEventType.KeyUp && event.key in listOf(Key.Enter, Key.Spacebar)) {
                            inspectAccessibly(slot.number)
                        } else false
                    }.focusable(enabled).semantics {
                        contentDescription = "第 ${slot.number} 节，${slot.timeLabel}"
                        stateDescription = if (slot.number in freeSlots) "课表空闲" else "未列为空闲"
                        selected = shown
                        if (enabled) {
                            onLongClick("查看节次时间") { inspectAccessibly(slot.number) }
                            onClick("查看节次时间") { inspectAccessibly(slot.number) }
                        }
                    }.background(availabilityColor(slot.number in freeSlots), RectangleShape))
            } }
        }
        slots.firstOrNull { enabled && it.number == inspected }?.let { slot ->
            val position = remember(slot.number, slots, density) { with(density) {
                PeriodStripTooltipPositionProvider(slots.indexOf(slot), slots.size, 24.dp.roundToPx(), 3.dp.roundToPx(),
                    16.dp.roundToPx(), 4.dp.roundToPx(), 5.dp.roundToPx(),
                    SlotTimeTooltipPositionProvider(28.dp.roundToPx(), 8.dp.roundToPx()))
            } }
            // Updating number/anchor does not remove and recreate an Android popup window.
            SlotTimeTooltip(slot, tagPrefix = "room_time_$roomId", positionOverride = position)
        }
    }
}
