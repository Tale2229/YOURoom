package com.example.youroom.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.youroom.domain.*

@Composable
fun FilterDialog(state: YOURoomState, onDismiss: () -> Unit, onFilters: (SearchFilters) -> Unit,
    onAddTag: (String) -> Unit, onRemoveTag: (String) -> Unit, onTagOrder: (List<String>) -> Unit) {
    val buildings = remember(state.campusSnapshot) {
        state.campusSnapshot?.classrooms.orEmpty().map { it.building }.distinct().sorted()
    }
    var editingTags by remember { mutableStateOf(false) }
    val guide=LocalUsageGuide.current
    LaunchedEffect(guide?.stage,guide?.entry) {
        if(guide!=null) editingTags=guide.stage==GuideStage.FILTER_TAGS && guide.entry>0
    }
    GuideDialog("filter",onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
            DialogPanel(DialogDesign.B, Modifier.widthIn(max = 480.dp).fillMaxWidth().testTag("filter_dialog")) {
                Column {
                    DialogHeading {
                        Text("筛选教室", Modifier.weight(1f))
                        Text(state.campus.label, Modifier.testTag("filter_campus"), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(Modifier.weight(1f, fill = false).padding(horizontal = 20.dp, vertical = 12.dp)
                        .fillMaxWidth().heightIn(max = 480.dp).guideTag("filter_choices")
                        .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        BuildingFilterRows(state, buildings, onFilters)
                        ThemeDivider()
                        TagEditor(state, onFilters, onAddTag, onRemoveTag, onTagOrder,
                            editing = editingTags, onCancelEdit = { editingTags=false;guide?.editingFilterTags(false) })
                    }
                    DialogFooter {
                    DialogFooterActions(state.filters.badgeCount > 0, "reset_filters", "close_filters",
                        { onFilters(SearchFilters(selectedSlots = state.filters.selectedSlots, query = state.filters.query)) }, onDismiss,
                        middleTag = "edit_tags", middleDescription = if (editingTags) "结束词条编辑" else "添加和整理词条",
                        middleSelected = editingTags, onMiddle = { editingTags = !editingTags;guide?.editingFilterTags(editingTags) },
                        resetIconOffsetY = (-1).dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun BuildingFilterRows(state: YOURoomState, buildings: List<String>, onFilters: (SearchFilters) -> Unit) {
    val names = listOf<String?>(null) + buildings
    val rows = if (state.campus == Campus.SHAHE) 1 else minOf(2, names.size)
    val perRow = (names.size + rows - 1) / rows
    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).guideTag("building_rows")) {
        names.chunked(perRow).forEachIndexed { index, row ->
            Row(Modifier.testTag("building_row_$index"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { building ->
                    CompactFilterChip(
                        if (building == null) "全部教学楼" else if (building.all(Char::isDigit)) "教学楼 $building" else building,
                        if (building == null) state.filters.buildings.isEmpty() else building in state.filters.buildings,
                        { onFilters(if (building == null) state.filters.copy(buildings = emptySet()) else state.filters.toggleBuilding(building)) },
                        Modifier.guideTag(if (building == null) "building_all" else "building_$building"))
                }
            }
        }
    }
}
