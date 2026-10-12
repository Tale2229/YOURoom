package com.example.youroom.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.ImeAction
import com.example.youroom.ui.theme.campusBadgeColor
import com.example.youroom.domain.*
import java.time.LocalDate

private class SlotPointerGeometry {
    var origin: Offset = Offset.Zero
    val bounds = mutableMapOf<Int, SlotBounds>()
}

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
    val markers = remember(rows, slots) { StudyGridLayout.markers(rows, slots) }
    // Coordinates are read only by pointer events. Writing Compose state here causes
    // a fresh grid composition on every frame as its containing page slides.
    val geometry = remember(context, available) { SlotPointerGeometry() }
    val bounds = geometry.bounds
    var previewSelection by remember(context, available) { mutableStateOf<Set<Int>?>(null) }
    val selection = previewSelection ?: filters.selectedSlots
    val chosen = remember(slots, selection) { slots.filter { it.number in selection } }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                    // Preview stays inside the grid. Do not change the search request
                    // or animate classroom rows for each cell crossed by the finger.
                    previewSelection = drag.selection
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.count { it.pressed } > 1) {
                                event.changes.forEach { it.consume() }
                                break
                            }
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed) break
                            val position = change.position + geometry.origin
                            val crossed = SlotGridGeometry.crossed(bounds.values, previous.x, previous.y, position.x, position.y)
                            drag.visit(crossed)
                            previewSelection = drag.selection
                            previous = position
                            change.consume()
                            if (!change.pressed) {
                                if (latestContext == context && latestFilters.selectedSlots == drag.initial) {
                                    latestCallback(latestFilters.copy(selectedSlots = drag.selection))
                                }
                                break
                            }
                        }
                    } finally {
                        // Cancellation/disposal needs no rollback of committed filters:
                        // only the local preview has changed, so no search is launched.
                        previewSelection = null
                    }
                }
            }) {
                rows.forEachIndexed { rowIndex, row ->
                    TimeMarkerBand(markers[rowIndex], cellWidth)
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
                                        }, contentAlignment = Alignment.Center) {
                                        Surface(Modifier.fillMaxWidth().height(36.dp).testTag("slot_face_${slot.number}"),
                                            shape = MaterialTheme.shapes.small, color = background,
                                            contentColor = if (isSelected) colors.onPrimary else colors.onSurfaceVariant) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("${slot.number}", style = MaterialTheme.typography.labelLarge)
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
                TimeMarkerBand(markers.last(), cellWidth)
            }
        }
        if (chosen.isNotEmpty()) Text(SlotSelection.summary(chosen), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("slot_summary"))
    }
}

@Composable
private fun TimeMarkerBand(markers: List<StudyTimeMarker?>, cellWidth: Dp) {
    if (markers.none { it != null }) return
    val bandHeight = with(LocalDensity.current) { maxOf(12.dp, 12.sp.toDp()) }
    Row(Modifier.fillMaxWidth().height(bandHeight), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        markers.forEach { marker ->
            Box(Modifier.width(cellWidth).fillMaxHeight(), contentAlignment =
                if (marker?.start == false) Alignment.CenterEnd else Alignment.CenterStart) {
                if (marker != null) Row(Modifier.testTag("time_marker_${marker.number}").semantics(mergeDescendants = true) {
                    contentDescription = "第 ${marker.number} 节${if (marker.start) "开始" else "结束"}，${marker.label}"
                }.height(bandHeight).padding(start = if (marker.start) 4.dp else 0.dp,
                    end = if (marker.start) 0.dp else 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (marker.start) TimeMarkerLine(marker)
                    Text(marker.label, Modifier.offset(y = if (marker.start) 2.dp else (-2).dp)
                        .testTag("time_marker_label_${marker.number}"),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp), maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!marker.start) TimeMarkerLine(marker)
                }
            }
        }
    }
}

@Composable
private fun TimeMarkerLine(marker: StudyTimeMarker) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.width(1.dp).fillMaxHeight().testTag("time_marker_line_${marker.number}")) {
        val range = timeMarkerLineRange(marker.start, size.height, 48.dp.roundToPx(), 36.dp.roundToPx(), 2.dp.toPx())
        // Draw outside the label band into the 48dp target's transparent inset.
        // Pixel-rounded insets keep the endpoint flush with the 36dp rectangle.
        drawLine(color, Offset(size.width / 2, range.start), Offset(size.width / 2, range.endInclusive),
            strokeWidth = size.width)
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
