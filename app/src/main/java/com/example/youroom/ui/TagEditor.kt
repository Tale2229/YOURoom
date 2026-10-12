package com.example.youroom.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.example.youroom.domain.*
import kotlin.math.roundToInt

private class TagGeometry {
    var origin = Offset.Zero
    val bounds = mutableMapOf<String, Rect>()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagEditor(state: YouRoomState, onFilters: (SearchFilters) -> Unit, onAdd: (String) -> Unit,
    onRemove: (String) -> Unit, onOrder: (List<String>) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var removal by remember { mutableStateOf<String?>(null) }
    var order by remember { mutableStateOf(state.tagOrder) }
    var dragged by remember { mutableStateOf<String?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    val geometry = remember { TagGeometry() }
    val latestOrderCallback by rememberUpdatedState(onOrder)
    LaunchedEffect(state.tagOrder, dragged) { if (dragged == null) order = state.tagOrder }
    val angle = if (editing) rememberInfiniteTransition(label = "tag_editing").animateFloat(-1.3f, 1.3f,
        infiniteRepeatable(tween(110, easing = LinearEasing), RepeatMode.Reverse), label = "tag_wiggle") else null
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().onGloballyPositioned { geometry.origin = it.positionInRoot() }) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                order.forEach { tag -> key(tag) {
                    val movable = tag != PersonalTags.favorite
                    CompactFilterChip(tag, state.filters.hasTerm(tag), { onFilters(state.filters.toggleTerm(tag)) },
                        modifier = Modifier.testTag(when (tag) {
                            PersonalTags.favorite -> "filter_favorite"
                            PersonalTags.outlets -> "filter_outlets"
                            else -> "filter_tag_$tag"
                        }).onGloballyPositioned { coordinates ->
                            geometry.bounds[tag] = Rect(coordinates.positionInRoot(), coordinates.size.toSize())
                        }.semantics {
                            if (editing && movable) customActions = listOf(
                                CustomAccessibilityAction("向前移动") {
                                    val previous = order.getOrNull(order.indexOf(tag) - 1)
                                    if (previous != null && previous != PersonalTags.favorite) {
                                        order = TagOrder.move(order, tag, previous); latestOrderCallback(order); true
                                    } else false
                                }, CustomAccessibilityAction("向后移动") {
                                    order.getOrNull(order.indexOf(tag) + 1)?.let {
                                        order = TagOrder.move(order, tag, it); latestOrderCallback(order); true
                                    } ?: false
                                })
                        }.pointerInput(tag, editing) {
                            if (editing && movable) {
                                var before = emptyList<String>()
                                var lastTarget: String? = null
                                detectDragGestures(onDragStart = { position ->
                                    before = order.toList(); lastTarget = null
                                    val bounds = geometry.bounds[tag]
                                    grab = Offset(position.x.coerceIn(0f, bounds?.width ?: 0f),
                                        position.y.coerceIn(0f, bounds?.height ?: 0f))
                                    pointer = bounds?.topLeft?.plus(position) ?: Offset.Zero
                                    dragged = tag
                                }, onDragCancel = { order = before; dragged = null }, onDragEnd = {
                                    if (order != before) latestOrderCallback(order)
                                    dragged = null
                                }) { change, _ ->
                                    change.consume()
                                    pointer = (geometry.bounds[tag]?.topLeft ?: Offset.Zero) + change.position
                                    val target = order.firstOrNull { candidate -> candidate != tag &&
                                        candidate != PersonalTags.favorite && geometry.bounds[candidate]?.contains(pointer) == true }
                                    if (target != null && target != lastTarget) {
                                        lastTarget = target; order = TagOrder.move(order, tag, target)
                                    }
                                }
                            }
                        },
                        onDelete = if (editing && movable) ({ removal = tag }) else null,
                        angle = if (movable && dragged != tag) angle else null, faded = dragged == tag)
                } }
                CompactFilterChip("＋", editing, { editing = !editing },
                    Modifier.testTag("edit_tags").semantics { contentDescription = if (editing) "结束词条编辑" else "添加和整理词条" })
            }
            dragged?.let { tag ->
                CompactFilterChip(tag, state.filters.hasTerm(tag), {},
                    Modifier.offset { IntOffset((pointer.x - geometry.origin.x - grab.x).roundToInt(),
                        (pointer.y - geometry.origin.y - grab.y).roundToInt()) }.clearAndSetSemantics {})
            }
        }
        if (editing) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(name, { name = it; error = null }, singleLine = true, label = { Text("词条名称") },
                    modifier = Modifier.weight(1f).testTag("custom_tag_name"), isError = error != null,
                    supportingText = if (error != null) ({ Text(error.orEmpty()) }) else null)
                TextButton(onClick = {
                    error = PersonalTags.validate(name, state.tagOrder.toSet())
                    if (error == null) { onAdd(name.trim()); name = "" }
                }, modifier = Modifier.testTag("add_tag")) { Text("添加") }
            }
        }
    }
    removal?.let { tag -> AlertDialog(onDismissRequest = { removal = null },
        title = { Text("删除“$tag”？") },
        text = { Text("取消该词条的筛选并隐藏标记。教室原标记保留，重新添加可恢复。") },
        confirmButton = { TextButton(onClick = { onRemove(tag); removal = null },
            modifier = Modifier.testTag("confirm_delete_tag")) { Text("删除") } },
        dismissButton = { TextButton(onClick = { removal = null }) { Text("取消") } }) }
}
