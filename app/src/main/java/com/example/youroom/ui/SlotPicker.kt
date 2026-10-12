package com.example.youroom.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.youroom.domain.*
import java.time.LocalDate

private class SlotPointerGeometry {
    var origin: Offset = Offset.Zero
    val bounds = mutableMapOf<Int, SlotBounds>()
}

@Composable
fun SlotPicker(campus: Campus, queryDate: LocalDate?, demo: Boolean, filters: SearchFilters,
    slots: List<LessonSlot>, onFilters: (SearchFilters) -> Unit, enabled: Boolean = true) {
    val context = Triple(campus, queryDate, demo)
    val latestContext by rememberUpdatedState(context)
    val latestFilters by rememberUpdatedState(filters)
    val latestCallback by rememberUpdatedState(onFilters)
    val available = remember(slots) { slots.map { it.number } }
    // Coordinates are read only by pointer events. Writing Compose state here causes
    // a fresh grid composition on every frame as its containing page slides.
    val geometry = remember(context, available) { SlotPointerGeometry() }
    val bounds = geometry.bounds
    val chosen = remember(slots, filters.selectedSlots) { slots.filter { it.number in filters.selectedSlots } }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("自习时段", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { onFilters(filters.copy(selectedSlots = emptySet())) },
                shape = MaterialTheme.shapes.small, enabled = enabled && filters.selectedSlots.isNotEmpty()) { Text("清空") }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().testTag("slot_grid").onGloballyPositioned { geometry.origin = it.positionInRoot() }) {
            val columns = ((maxWidth.value + 4f) / 52f).toInt().coerceIn(1, 7)
            Column(Modifier.fillMaxWidth().pointerInput(context, columns, available, enabled) {
                // Initial-pass consumption owns grid painting, not the surrounding LazyColumn.
                awaitEachGesture {
                    val down = awaitFirstDown(pass = PointerEventPass.Initial)
                    if (!enabled) { down.consume(); return@awaitEachGesture }
                    val firstPosition = down.position + geometry.origin
                    val first = SlotGridGeometry.hit(bounds.values, firstPosition.x, firstPosition.y)
                        ?: return@awaitEachGesture
                    val drag = SlotDragSelection(latestFilters.selectedSlots, first)
                    var previous = firstPosition
                    var completed = false
                    down.consume()
                    latestCallback(latestFilters.copy(selectedSlots = drag.selection))
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
                            val before = drag.selection
                            drag.visit(crossed)
                            if (drag.selection != before) latestCallback(latestFilters.copy(selectedSlots = drag.selection))
                            previous = position
                            change.consume()
                            if (!change.pressed) { completed = true; break }
                        }
                    } finally {
                        // Disposal/campus/date changes must never restore an old selection into a new page.
                        if (!completed && latestContext == context) latestCallback(latestFilters.copy(selectedSlots = drag.cancel()))
                    }
                }
            }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                slots.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { slot ->
                            val isSelected = slot.number in filters.selectedSlots
                            val colors = MaterialTheme.colorScheme
                            val background by animateColorAsState(
                                if (isSelected) colors.primary else colors.surfaceContainer,
                                tween(140), label = "slot_background")
                            Surface(shape = MaterialTheme.shapes.small, color = background,
                                contentColor = if (isSelected) colors.onPrimary else colors.onSurfaceVariant,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                    .testTag("slot_${slot.number}")
                                    .onGloballyPositioned {
                                        val rect = it.boundsInRoot()
                                        bounds[slot.number] = SlotBounds(slot.number, rect.left, rect.top, rect.right, rect.bottom)
                                    }
                                    .toggleable(value = isSelected, enabled = enabled, role = Role.Checkbox, onValueChange = {
                                        onFilters(filters.copy(selectedSlots = SlotSelection.toggle(filters.selectedSlots, slot.number)))
                                    })
                                    .semantics {
                                        selected = isSelected
                                        contentDescription = "第 ${slot.number} 节，${slot.timeLabel}"
                                        stateDescription = if (isSelected) "已选中" else "未选中"
                                    }) {
                                Box(Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                    Text("${slot.number}", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (chosen.isNotEmpty()) Text(SlotSelection.summary(chosen), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("slot_summary"))
    }
}
