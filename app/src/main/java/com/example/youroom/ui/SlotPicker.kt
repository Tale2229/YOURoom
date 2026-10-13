package com.example.youroom.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import com.example.youroom.ui.theme.campusBadgeColor
import com.example.youroom.domain.*
import java.time.LocalDate
import kotlinx.coroutines.delay

private class SlotPointerGeometry {
    var origin: Offset = Offset.Zero
    val bounds = mutableMapOf<Int, SlotBounds>()
    val faces = mutableMapOf<Int, SlotBounds>()
}

// Null change means cancellation; a null decision means the stationary press timed out.
private data class SlotPressDecision(val change: PointerInputChange? = null)

@Composable
fun SlotPicker(campus: Campus, queryDate: LocalDate?, demo: Boolean, filters: SearchFilters,
    slots: List<LessonSlot>, onFilters: (SearchFilters) -> Unit, enabled: Boolean = true,
    onShowFilters: () -> Unit = {}) {
    val context = Triple(campus, queryDate, demo)
    val latestContext by rememberUpdatedState(context)
    val latestFilters by rememberUpdatedState(filters)
    val latestCallback by rememberUpdatedState(onFilters)
    val available = remember(slots) { slots.map { it.number } }
    val rows = remember(available) { StudyGridLayout.rows(available) }
    // Coordinates are read only by pointer events. Writing Compose state here causes
    // a fresh grid composition on every frame as its containing page slides.
    val geometry = remember(context, available) { SlotPointerGeometry() }
    val bounds = geometry.bounds
    var previewSelection by remember(context, available) { mutableStateOf<Set<Int>?>(null) }
    var inspectedNumber by remember(context, available) { mutableStateOf<Int?>(null) }
    var pointerHeld by remember(context, available) { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val selection = previewSelection ?: filters.selectedSlots
    val chosen = remember(slots, selection) { slots.filter { it.number in selection } }
    val summaryScroll = rememberScrollState()
    val summaryStyle = MaterialTheme.typography.bodySmall
    val summaryHeight = with(LocalDensity.current) { summaryStyle.lineHeight.toDp() }
    LaunchedEffect(context, filters.selectedSlots) { summaryScroll.scrollTo(0) }
    LaunchedEffect(pointerHeld, inspectedNumber) {
        // Keyboard/accessibility inspection has no pointer-up event to dismiss it.
        if (!pointerHeld && inspectedNumber != null) { delay(2_000); inspectedNumber = null }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().testTag("slot_header"), verticalAlignment = Alignment.CenterVertically) {
            Text("自习时段", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { onFilters(filters.copy(selectedSlots = emptySet())) },
                shape = MaterialTheme.shapes.small, enabled = enabled && filters.selectedSlots.isNotEmpty()) { Text("清空") }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().testTag("slot_grid").onGloballyPositioned { geometry.origin = it.positionInRoot() }) {
            val cellWidth = (maxWidth - 4.dp * (StudyGridLayout.columns - 1)) / StudyGridLayout.columns
            Column(Modifier.fillMaxWidth().pointerInput(context, available, enabled) {
                // Initial-pass consumption owns grid painting, not the surrounding LazyColumn.
                awaitEachGesture {
                    val down = awaitFirstDown(pass = PointerEventPass.Initial)
                    if (!enabled) { down.consume(); return@awaitEachGesture }
                    val firstPosition = down.position + geometry.origin
                    val first = SlotGridGeometry.hit(bounds.values, firstPosition.x, firstPosition.y)
                        ?: return@awaitEachGesture
                    val drag = SlotDragSelection(latestFilters.selectedSlots, first)
                    var previous = firstPosition
                    down.consume()
                    pointerHeld = true
                    inspectedNumber = null
                    try {
                        // Do not toggle on down: a stationary long press only inspects
                        // this period. A normal tap commits on up; movement paints immediately.
                        val decision = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            var intent: SlotPressDecision? = null
                            while (intent == null) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.count { it.pressed } > 1) {
                                    event.changes.forEach { it.consume() }
                                    intent = SlotPressDecision()
                                } else {
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null || change.isConsumed) intent = SlotPressDecision()
                                    else {
                                        val position = change.position + geometry.origin
                                        val hit = SlotGridGeometry.hit(bounds.values, position.x, position.y)
                                        change.consume()
                                        if (!change.pressed || (position - firstPosition).getDistance() > viewConfiguration.touchSlop ||
                                            (hit != null && hit != first)) intent = SlotPressDecision(change)
                                    }
                                }
                            }
                            intent
                        }
                        if (decision == null) {
                            inspectedNumber = first
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            // Inspection owns the rest of the gesture and never turns
                            // into selection or starts a classroom search on release.
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                val cancelled = change == null || change.isConsumed || event.changes.count { it.pressed } > 1
                                event.changes.forEach { it.consume() }
                                if (cancelled || change?.pressed != true) break
                                val position = change.position + geometry.origin
                                // Follow the current face, not every cell crossed along the way.
                                // Gaps / search / filters hide the hint without leaving inspection mode.
                                inspectedNumber = SlotGridGeometry.hit(geometry.faces.values, position.x, position.y)
                            }
                            return@awaitEachGesture
                        }
                        var change = decision.change ?: return@awaitEachGesture
                        while (true) {
                            val position = change.position + geometry.origin
                            val crossed = SlotGridGeometry.crossed(bounds.values, previous.x, previous.y, position.x, position.y)
                            drag.visit(crossed)
                            // Preview stays inside the grid; commit only once, on up.
                            previewSelection = drag.selection
                            previous = position
                            change.consume()
                            if (!change.pressed) {
                                if (latestContext == context && latestFilters.selectedSlots == drag.initial) {
                                    latestCallback(latestFilters.copy(selectedSlots = drag.selection))
                                }
                                break
                            }
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.count { it.pressed } > 1) {
                                event.changes.forEach { it.consume() }
                                break
                            }
                            change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed) break
                        }
                    } finally {
                        // Cancellation/disposal needs no rollback of committed filters:
                        // only the local preview has changed, so no search is launched.
                        previewSelection = null
                        inspectedNumber = null
                        pointerHeld = false
                    }
                }
            }) {
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEachIndexed { cellIndex, cell -> key(cell.kind, cell.number, cellIndex) {
                            val modifier = Modifier.width(cellWidth * cell.span + 4.dp * (cell.span - 1))
                            when (cell.kind) {
                                StudyGridCellKind.SLOT -> slots.firstOrNull { it.number == cell.number }?.let { slot ->
                                    val isSelected = slot.number in selection
                                    val colors = MaterialTheme.colorScheme
                                    val background by animateColorAsState(
                                        if (isSelected) colors.primary else colors.surfaceContainer,
                                        tween(140), label = "slot_background")
                                    Box(modifier.height(48.dp).testTag("slot_${slot.number}")
                                        .onGloballyPositioned {
                                            val rect = it.boundsInRoot()
                                            bounds[slot.number] = SlotBounds(slot.number, rect.left, rect.top, rect.right, rect.bottom)
                                        }.toggleable(value = isSelected, enabled = enabled, role = Role.Checkbox, onValueChange = {
                                            onFilters(filters.copy(selectedSlots = SlotSelection.toggle(filters.selectedSlots, slot.number)))
                                        }).semantics {
                                            selected = isSelected
                                            contentDescription = "第 ${slot.number} 节，${slot.timeLabel}"
                                            stateDescription = if (isSelected) "已选中" else "未选中"
                                            if (enabled) onLongClick("查看节次时间") { inspectedNumber = slot.number; true }
                                        }, contentAlignment = Alignment.Center) {
                                        Surface(Modifier.fillMaxWidth().height(36.dp).testTag("slot_face_${slot.number}")
                                            .onGloballyPositioned {
                                                val rect = it.boundsInRoot()
                                                geometry.faces[slot.number] = SlotBounds(slot.number, rect.left, rect.top, rect.right, rect.bottom)
                                            },
                                            shape = RectangleShape, color = background,
                                            contentColor = if (isSelected) colors.onPrimary else colors.onSurfaceVariant) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("${slot.number}", style = MaterialTheme.typography.labelLarge)
                                                if (enabled && inspectedNumber == slot.number) SlotTimeTooltip(slot)
                                            }
                                        }
                                    }
                                }
                                StudyGridCellKind.SPACER -> Spacer(modifier.height(48.dp))
                                StudyGridCellKind.SEARCH -> CompactRoomSearch(filters.query,
                                    { onFilters(filters.copy(query = it)) }, modifier, enabled)
                                StudyGridCellKind.FILTER -> CompactFilterAction(campus, filters.badgeCount,
                                    onShowFilters, modifier, enabled)
                            }
                        } }
                    }
                }
            }
        }
        // Reserve one font-scaled line even when empty. Disjoint selections scroll
        // horizontally rather than adding rows and shifting the classrooms below.
        Box(Modifier.fillMaxWidth().height(summaryHeight).testTag("slot_summary_space")) {
            if (chosen.isNotEmpty()) Text(SlotSelection.summary(chosen), style = summaryStyle,
                color = MaterialTheme.colorScheme.primary, maxLines = 1, softWrap = false,
                modifier = Modifier.fillMaxWidth().horizontalScroll(summaryScroll).testTag("slot_summary"))
        }
    }
}

@Composable
private fun CompactRoomSearch(query: String, onQuery: (String) -> Unit, modifier: Modifier, enabled: Boolean) {
    val keyboard = LocalSoftwareKeyboardController.current
    val colors = MaterialTheme.colorScheme
    Box(modifier.height(48.dp), contentAlignment = Alignment.Center) {
        Surface(Modifier.fillMaxWidth().height(36.dp).testTag("room_search_frame"), shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, colors.outlineVariant)) {
            BasicTextField(query, onQuery, Modifier.fillMaxSize().testTag("room_search")
                .semantics { contentDescription = "搜索教室" }.padding(horizontal = 6.dp),
                enabled = enabled, singleLine = true,
                textStyle = MaterialTheme.typography.labelLarge.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                decorationBox = { inner ->
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Outlined.Search, null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
                        Box(Modifier.weight(1f)) { inner() }
                        if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }, enabled = enabled,
                            modifier = Modifier.width(24.dp).fillMaxHeight().testTag("clear_room_search")) {
                            Icon(Icons.Outlined.Close, "清空教室搜索", Modifier.size(16.dp), tint = colors.onSurfaceVariant)
                        }
                    }
                })
        }
    }
}

@Composable
private fun CompactFilterAction(campus: Campus, count: Int, onClick: () -> Unit, modifier: Modifier, enabled: Boolean) {
    Box(modifier.height(48.dp)) {
        Surface(onClick = onClick, enabled = enabled, color = Color.Transparent,
            modifier = Modifier.fillMaxSize().testTag("extra_filters").semantics {
                contentDescription = "筛选教室，已选 $count 个词条"
            }) {
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxWidth().height(36.dp).background(MaterialTheme.colorScheme.surfaceContainer,
                    MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Tune, "筛选教室", Modifier.size(20.dp))
                }
            }
        }
        if (count > 0) Surface(Modifier.align(Alignment.TopEnd).offset(x = 4.dp)
            .testTag("filter_badge").semantics(mergeDescendants = true) {}, shape = androidx.compose.foundation.shape.CircleShape,
            color = campusBadgeColor(campus), contentColor = Color.White) {
            Box(Modifier.heightIn(min = 20.dp).widthIn(min = 20.dp).padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center) {
                Text("$count", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
