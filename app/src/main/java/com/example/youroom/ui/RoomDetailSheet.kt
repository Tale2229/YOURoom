package com.example.youroom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
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
import com.example.youroom.domain.PersonalTags
import com.example.youroom.domain.allTags

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomDetailSheet(room: RoomMatch, state: YouRoomState, onDismiss: () -> Unit,
    onChange: (RoomProfile) -> Unit) {
    var draft by remember(room.classroom.id) { mutableStateOf(room.profile.copy(tags = room.profile.allTags, quiet = false)) }
    LaunchedEffect(room.profile) { draft = room.profile.copy(tags = room.profile.allTags, quiet = false) }
    fun update(profile: RoomProfile) { draft = profile; onChange(profile) }
    val snapshot = state.campusSnapshot
    ModalBottomSheet(onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
        dragHandle = { Box(Modifier.padding(vertical = 12.dp).width(36.dp).height(2.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().testTag("room_detail").imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(room.classroom.name, Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
                IconAction(onDismiss, Modifier.testTag("close_room_detail")) {
                    Icon(Icons.Outlined.Close, "关闭教室详情", Modifier.size(20.dp))
                }
            }
            Text(listOfNotNull(room.classroom.buildingLabel, room.classroom.capacity?.let { "标注容量 $it" }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${state.campus.label} · 今日教学节次", style = MaterialTheme.typography.titleMedium)
            if (state.stale || snapshot == null) {
                Text("暂无可用于今天判断的数据，请先刷新。", style = MaterialTheme.typography.bodySmall)
            } else {
                snapshot.slots.chunked(7).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { slot ->
                            val free = slot.number in room.freeSlots
                            Box(Modifier.weight(1f).height(36.dp).testTag("detail_slot_${slot.number}").semantics {
                                contentDescription = "第 ${slot.number} 节，${if (free) "课表空闲" else "未列为空闲"}"
                            }.background(
                                availabilityColor(free),
                                MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
                                Text("${slot.number}", style = MaterialTheme.typography.labelLarge,
                                    color = if (free) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
            Text("我的标记", style = MaterialTheme.typography.titleMedium)
            ProfileToggle("已收藏", draft.favorite) { update(draft.copy(favorite = it)) }
            ProfileToggle("从筛选结果中排除", draft.excluded) { update(draft.copy(excluded = it)) }
            state.tagOrder.filterNot { it == PersonalTags.favorite }.forEach { tag ->
                ProfileToggle(tag, if (tag == PersonalTags.outlets) draft.outlets else tag in draft.tags) { selected ->
                    update(if (tag == PersonalTags.outlets) draft.copy(outlets = selected) else
                        draft.copy(tags = if (selected) draft.tags + tag else draft.tags - tag))
                }
            }
            Text("标签仅依据个人经验；未勾选表示未标记，不代表没有。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(draft.note, { update(draft.copy(note = it.take(500))) },
                modifier = Modifier.fillMaxWidth().testTag("room_note"), label = { Text("个人备注") },
                placeholder = { Text("例如：靠窗、后排插座、周末可能不开门") }, minLines = 2, maxLines = 4)
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
