package com.example.youroom.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.youroom.domain.*

@Composable
fun FilterDialog(state: YouRoomState, onDismiss: () -> Unit, onFilters: (SearchFilters) -> Unit,
    onAddTag: (String) -> Unit, onRemoveTag: (String) -> Unit, onTagOrder: (List<String>) -> Unit) {
    var capacity by remember(state.filters.minimumCapacity) { mutableStateOf(
        if (state.filters.minimumCapacity > 0) "${state.filters.minimumCapacity}" else "") }
    val buildings = remember(state.campusSnapshot) {
        state.campusSnapshot?.classrooms.orEmpty().map { it.building }.distinct().sorted()
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 480.dp).fillMaxWidth().testTag("filter_dialog"),
                shape = MaterialTheme.shapes.small, tonalElevation = 0.dp) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("筛选教室", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        Text(state.campus.label, Modifier.testTag("filter_campus"), style = MaterialTheme.typography.titleSmall)
                        IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_filters")) {
                            Icon(Icons.Outlined.Close, "关闭筛选", Modifier.size(20.dp))
                        }
                    }
                    Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        BuildingFilterRows(state, buildings, onFilters)
                        HorizontalDivider()
                        TagEditor(state, onFilters, onAddTag, onRemoveTag, onTagOrder)
                        HorizontalDivider()
                        OutlinedTextField(capacity, { value ->
                            capacity = value.filter(Char::isDigit).take(4)
                            onFilters(state.filters.copy(minimumCapacity = capacity.toIntOrNull() ?: 0))
                        }, label = { Text("最低标注容量") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                        TextButton(onClick = { onFilters(SearchFilters(selectedSlots = state.filters.selectedSlots, query = state.filters.query)) },
                            modifier = Modifier.testTag("reset_filters")) { Text("重置筛选") }
                    }
                }
            }
        }
    }
}

@Composable
private fun BuildingFilterRows(state: YouRoomState, buildings: List<String>, onFilters: (SearchFilters) -> Unit) {
    val names = listOf<String?>(null) + buildings
    val rows = if (state.campus == Campus.SHAHE) 1 else minOf(2, names.size)
    val perRow = (names.size + rows - 1) / rows
    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("building_rows")) {
        names.chunked(perRow).forEachIndexed { index, row ->
            Row(Modifier.testTag("building_row_$index"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { building ->
                    CompactFilterChip(
                        if (building == null) "全部教学楼" else if (building.all(Char::isDigit)) "教学楼 $building" else building,
                        if (building == null) state.filters.buildings.isEmpty() else building in state.filters.buildings,
                        { onFilters(if (building == null) state.filters.copy(buildings = emptySet()) else state.filters.toggleBuilding(building)) },
                        Modifier.testTag(if (building == null) "building_all" else "building_$building"))
                }
            }
        }
    }
}
