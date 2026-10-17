package com.example.youroom.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.example.youroom.domain.PeriodWave
import com.example.youroom.domain.PeriodStripLayout
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private class PeriodStripSize { var width = 0f }
private val PeriodStripTopPadding = 8.dp

/** A single popup stays attached while its anchor moves between the strip's original bars. */
internal class PeriodStripTooltipPositionProvider(private val index: Int, private val count: Int,
    private val gutter: Int, private val gap: Int, private val top: Int, private val lift: Int,
    private val barHeight: Int, private val delegate: PopupPositionProvider,
    private val slots: List<LessonSlot> = emptyList(),
    private val mode: PeriodStripLayout = PeriodStripLayout.EQUAL) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
        layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val width = (anchorBounds.width - 2 * gutter - gap * (count - 1)).toFloat() / count
        val visual = if (layoutDirection == LayoutDirection.Rtl) count - 1 - index else index
        val timeBar=if(mode==PeriodStripLayout.TIME_PROPORTIONAL)
            PeriodStripGeometry.bars(slots,(anchorBounds.width-2*gutter).toFloat(),gap.toFloat(),mode).getOrNull(index) else null
        val left = anchorBounds.left + gutter + if(timeBar==null) (visual * (width + gap)).roundToInt()
            else (if(layoutDirection==LayoutDirection.Rtl) anchorBounds.width-2*gutter-timeBar.end else timeBar.start).roundToInt()
        val actualWidth=timeBar?.width ?: width
        val y = anchorBounds.top + top - lift
        return delegate.calculatePosition(IntRect(left, y, left + actualWidth.roundToInt(), y + barHeight),
            windowSize, layoutDirection, popupContentSize)
    }
}

@Composable
internal fun RoomAvailabilityStrip(roomId: String, freeSlots: Set<Int>, slots: List<LessonSlot>,
    enabled: Boolean, modifier: Modifier = Modifier, inspectionKey: Any? = null,
    wave: RoomPeriodWave? = null, onInspection: () -> Unit = {}) {
    val inspectionEnabled = enabled && wave?.isRunning != true
    val preferences=LocalPeriodStripPreferences.current
    // Reset transient inspection when the room, response/date, or real period times change.
    var inspected by remember(roomId, slots, inspectionKey) { mutableStateOf<Int?>(null) }
    var pointerHeld by remember(roomId, slots, inspectionKey) { mutableStateOf(false) }
    val geometry = remember { PeriodStripSize() }
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val haptic = LocalHapticFeedback.current
    val latestInspection by rememberUpdatedState(onInspection)
    val gutter = with(density) { 24.dp.roundToPx().toFloat() }
    val gap = with(density) { 3.dp.roundToPx().toFloat() }
    fun numberAt(x: Float): Int? {
        val width=geometry.width-2*gutter
        val bars=PeriodStripGeometry.bars(slots,width,gap,preferences.layout)
        return PeriodStripGeometry.nearestIndex(x-gutter,width,bars,direction==LayoutDirection.Rtl)?.let { slots[it].number }
    }
    LaunchedEffect(inspectionEnabled) { if (!inspectionEnabled) { inspected = null; pointerHeld = false } }
    LaunchedEffect(pointerHeld, inspected) {
        // Accessibility/keyboard inspection has no pointer release to dismiss it.
        if (!pointerHeld && inspected != null) { delay(2_000); inspected = null }
    }
    fun inspectAccessibly(number: Int): Boolean {
        if (!inspectionEnabled) return false
        latestInspection(); inspected = number; return true
    }
    Box(modifier.fillMaxWidth().testTag("room_period_strip_$roomId")
        .onSizeChanged { geometry.width = it.width.toFloat() }
        .pointerInput(roomId, slots, inspectionKey, inspectionEnabled, direction, gutter, gap, preferences.layout) {
            awaitEachGesture {
                val down = awaitFirstDown(pass = PointerEventPass.Initial)
                if (!inspectionEnabled || slots.isEmpty()) return@awaitEachGesture
                inspected = null
                var x = down.position.x
                try {
                    // Early movement yields to vertical list scrolling. Horizontal
                    // motion here never opens the upper rail; only a hold owns inspection.
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
                    latestInspection()
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
        PeriodBarsRow(slots,preferences.layout,
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = PeriodStripTopPadding, bottom = 16.dp)) {
            slots.forEachIndexed { index, slot -> key(slot.number) {
                val shown = inspectionEnabled && inspected == slot.number
                val color = availabilityColor(slot.number in freeSlots)
                val visual = if (direction == LayoutDirection.Rtl) slots.lastIndex - index else index
                val lift by animateDpAsState(if (shown) (-4).dp else 0.dp, tween(90), label = "period_inspection_lift")
                Column {
                Box(Modifier.fillMaxWidth().height(5.dp).offset { IntOffset(0, lift.roundToPx()) }
                    .testTag("room_period_${roomId}_${slot.number}")
                    .onKeyEvent { event ->
                        if (inspectionEnabled && event.type == KeyEventType.KeyUp && event.key in listOf(Key.Enter, Key.Spacebar)) {
                            inspectAccessibly(slot.number)
                        } else false
                    }.focusable(inspectionEnabled).semantics {
                        contentDescription = "第 ${slot.number} 节，${slot.timeLabel}"
                        stateDescription = if (slot.number in freeSlots) "课表空闲" else "未列为空闲"
                        selected = shown
                        if (inspectionEnabled) {
                            onLongClick("查看节次时间") { inspectAccessibly(slot.number) }
                            onClick("查看节次时间") { inspectAccessibly(slot.number) }
                        }
                    }.drawBehind {
                        val first=slots.first().let { (it.start.toSecondOfDay()+it.end.toSecondOfDay())/2f }
                        val last=slots.last().let { (it.start.toSecondOfDay()+it.end.toSecondOfDay())/2f }
                        val center=(slot.start.toSecondOfDay()+slot.end.toSecondOfDay())/2f
                        val position=if(last==first) .5f else (center-first)/(last-first)
                        val amplitude=if(preferences.layout==PeriodStripLayout.TIME_PROPORTIONAL)
                            wave?.spatialAmplitude(if(direction==LayoutDirection.Rtl) 1-position else position,slots.size)
                            else wave?.amplitude(visual,slots.size)
                        val extension = (amplitude ?: 0f) *
                            PeriodWave.maximumExtensionDp.dp.toPx()
                        // Extend upwards into existing padding. The baseline, hit
                        // boxes, colors and row/list measurements never animate.
                        drawRect(color, Offset(0f, -extension), Size(size.width, size.height + extension))
                    })
                if(preferences.showNumbers) Text(slot.number.toString(),Modifier.fillMaxWidth().padding(top=3.dp)
                    .testTag("room_period_number_${roomId}_${slot.number}").clearAndSetSemantics {},
                    fontSize=8.sp,lineHeight=10.sp,textAlign=TextAlign.Center,maxLines=1,softWrap=false,
                    color=MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.65f))
                }
            } }
        }
        slots.firstOrNull { inspectionEnabled && it.number == inspected }?.let { slot ->
            val position = remember(slot.number, slots, density, preferences.layout) { with(density) {
                PeriodStripTooltipPositionProvider(slots.indexOf(slot), slots.size, 24.dp.roundToPx(), 3.dp.roundToPx(),
                    PeriodStripTopPadding.roundToPx(), 4.dp.roundToPx(), 5.dp.roundToPx(),
                    SlotTimeTooltipPositionProvider(28.dp.roundToPx(), 8.dp.roundToPx()),slots,preferences.layout)
            } }
            // Updating number/anchor does not remove and recreate an Android popup window.
            SlotTimeTooltip(slot, tagPrefix = "room_time_$roomId", positionOverride = position)
        }
    }
}
