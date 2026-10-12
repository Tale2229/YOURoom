package com.example.youroom.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.youroom.domain.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterDialog(state: YouRoomState, onDismiss: () -> Unit, onApply: (SearchFilters) -> Unit,
    onAddTag: (String) -> Unit) {
    var draft by remember { mutableStateOf(state.filters) }
    var editing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var tagError by remember { mutableStateOf<String?>(null) }
    var capacity by remember { mutableStateOf(if (draft.minimumCapacity > 0) "${draft.minimumCapacity}" else "") }
    val buildings = state.campusSnapshot?.classrooms.orEmpty().map { it.building }.distinct().sorted()
    AlertDialog(onDismissRequest = onDismiss, modifier = Modifier.testTag("filter_dialog"),
        shape = MaterialTheme.shapes.small,
        title = { Text("筛选教室", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${state.campus.label} · 教学楼", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(draft.buildings.isEmpty(), { draft = draft.copy(buildings = emptySet()) },
                        modifier = Modifier.testTag("building_all"), shape = MaterialTheme.shapes.small,
                        label = { Text("全部楼宇") })
                    buildings.forEach { building ->
                        FilterChip(building in draft.buildings, { draft = draft.toggleBuilding(building) },
                            modifier = Modifier.testTag("building_$building"), shape = MaterialTheme.shapes.small,
                            label = { Text(if (building.all(Char::isDigit)) "教学楼 $building" else building) })
                    }
                }
                Text("楼宇可多选；切换校区后恢复全部楼宇。", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("个人词条", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    IconAction(onClick = { editing = !editing }, modifier = Modifier.testTag("edit_tags")) {
                        Icon(Icons.Outlined.Edit, "修改词条", Modifier.size(20.dp))
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(draft.favoritesOnly, { draft = draft.copy(favoritesOnly = !draft.favoritesOnly) },
                        shape = MaterialTheme.shapes.small, modifier = Modifier.testTag("filter_favorite"), label = { Text("已收藏") })
                    FilterChip(draft.outletsOnly, { draft = draft.copy(outletsOnly = !draft.outletsOnly) },
                        shape = MaterialTheme.shapes.small, modifier = Modifier.testTag("filter_outlets"), label = { Text("有插座") })
                    (listOf(PersonalTags.comfortable, PersonalTags.uncrowded) + state.customTags.sorted()).distinct().forEach { tag ->
                        FilterChip(tag in draft.tags, { draft = draft.copy(tags =
                            if (tag in draft.tags) draft.tags - tag else draft.tags + tag) },
                            shape = MaterialTheme.shapes.small, modifier = Modifier.testTag("filter_tag_$tag"), label = { Text(tag) })
                    }
                }
                if (editing) {
                    OutlinedTextField(name, { name = it; tagError = null }, singleLine = true,
                        label = { Text("自定义词条") }, supportingText = { Text(tagError ?: "最多 20 个字，例如：靠窗") },
                        isError = tagError != null, modifier = Modifier.fillMaxWidth().testTag("custom_tag_name"))
                    TextButton(onClick = {
                        tagError = PersonalTags.validate(name, state.customTags)
                        if (tagError == null) { onAddTag(name.trim()); name = "" }
                    }, modifier = Modifier.testTag("add_tag")) { Text("添加词条") }
                }
                Text("多个词条需同时满足。请在教室详情中添加个人标记；人少不是实时人数。",
                    style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                OutlinedTextField(capacity, { value ->
                    capacity = value.filter(Char::isDigit).take(4)
                    draft = draft.copy(minimumCapacity = capacity.toIntOrNull() ?: 0)
                }, label = { Text("最低标注容量") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                TextButton(onClick = {
                    draft = SearchFilters(selectedSlots = draft.selectedSlots, query = draft.query)
                    capacity = ""
                }, modifier = Modifier.testTag("reset_filters")) { Text("重置筛选") }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(draft); onDismiss() },
            modifier = Modifier.testTag("apply_filters")) { Text("应用") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
