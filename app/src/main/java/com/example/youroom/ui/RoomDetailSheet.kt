package com.example.youroom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.youroom.domain.RoomMatch
import com.example.youroom.domain.RoomProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomDetailSheet(room: RoomMatch, state: YouRoomState, onDismiss: () -> Unit,
    onSave: (RoomProfile) -> Unit) {
    var draft by remember(room.classroom.id) { mutableStateOf(room.profile) }
    val snapshot = state.campusSnapshot
    ModalBottomSheet(onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
        dragHandle = { Box(Modifier.padding(vertical = 12.dp).width(36.dp).height(2.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(room.classroom.name, style = MaterialTheme.typography.headlineMedium)
            Text(listOfNotNull(room.classroom.buildingLabel, room.classroom.capacity?.let { "标注容量 $it（非余座）" }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${state.campus.label} · 今日教学节次", style = MaterialTheme.typography.titleMedium)
            if (state.stale || snapshot == null) {
                Text("暂无可用于今天判断的数据，请先刷新。", style = MaterialTheme.typography.bodySmall)
            } else {
                snapshot.slots.chunked(7).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { slot ->
                            val free = slot.number in room.freeSlots
                            Box(Modifier.weight(1f).height(36.dp).semantics {
                                contentDescription = "第 ${slot.number} 节，${if (free) "课表空闲" else "未列为空闲"}"
                            }.background(
                                if (free) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
                                Text("${slot.number}", style = MaterialTheme.typography.labelLarge,
                                    color = if (free) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Text("主题色：课表空闲；灰色：未列为空闲。课间与午休不作保证。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
            Text("我的标记", style = MaterialTheme.typography.titleMedium)
            ProfileToggle("收藏此教室", draft.favorite) { draft = draft.copy(favorite = it) }
            ProfileToggle("从筛选结果中排除", draft.excluded) { draft = draft.copy(excluded = it) }
            ProfileToggle("我知道这里比较安静", draft.quiet) { draft = draft.copy(quiet = it) }
            ProfileToggle("我知道这里有插座", draft.outlets) { draft = draft.copy(outlets = it) }
            Text("标签仅依据个人经验；未勾选表示未标记，不代表没有。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(draft.note, { draft = draft.copy(note = it.take(500)) },
                modifier = Modifier.fillMaxWidth().testTag("room_note"), label = { Text("个人备注") },
                placeholder = { Text("例如：靠窗、后排插座、周末可能不开门") }, minLines = 2, maxLines = 4)
            Button(onClick = { onSave(draft.copy(note = draft.note.trim())) },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("save_profile")) { Text("保存个人标记") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProfileToggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .toggleable(value = value, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Checkbox(checked = value, onCheckedChange = null)
    }
}
