package com.example.youroom.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.youroom.domain.allTags
import com.example.youroom.domain.PersonalTags
import com.example.youroom.domain.hasTerm

@Composable
fun PreferencesScreen(state: YouRoomState, onRoom: (String?) -> Unit,
    onClear: (String) -> Unit, onLogout: () -> Unit) {
    var confirmExit by remember { mutableStateOf(false) }
    var clearId by remember { mutableStateOf<String?>(null) }
    val profiles = state.campusProfiles.filter {
        it.favorite || it.excluded || state.tagOrder.any(it::hasTerm) || it.note.isNotBlank()
    }.sortedWith(compareByDescending<com.example.youroom.domain.RoomProfile> { it.favorite }.thenBy { it.name })
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("个人偏好", style = MaterialTheme.typography.headlineMedium)
                Text("${state.campus.label}校区 · 收藏、排除与个人备注",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state.demo) item { StatusNotice("演示标记仅在本次演示中有效，不会修改真实偏好。") }
        if (profiles.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 44.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.StarBorder, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                Text("还没有个人标记", style = MaterialTheme.typography.titleLarge)
                Text("在教室列表左滑，可收藏、修改标签与备注或排除教室。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(profiles, key = { it.roomId }) { profile ->
            Column(Modifier.animateItem().background(if (profile.favorite) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background)
                .clickable { onRoom(profile.roomId) }.padding(horizontal = 24.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(profile.name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleLarge)
                            ExcludedRoomMarker(profile)
                        }
                        Text(listOfNotNull(if (profile.favorite) "已收藏" else null,
                            if (profile.excluded) "已排除" else null).plus(
                            state.tagOrder.filter { it != PersonalTags.favorite && profile.hasTerm(it) }).joinToString(" · ").ifBlank { "个人备注" },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        if (profile.note.isNotBlank()) Text(profile.note, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconAction(onClick = { clearId = profile.roomId }) {
                        Icon(Icons.Outlined.DeleteOutline, "清除 ${profile.name} 的个人标记")
                    }
                }
                HorizontalDivider(Modifier.padding(top = 14.dp))
            }
        }
        item {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("关于数据", style = MaterialTheme.typography.titleMedium)
                Text("插座、桌椅舒适、人少及自定义词条由你标记，不来自学校接口；人少不是实时人数。未标记不代表没有。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("支持西土城与沙河校区，个人标记按校区区分。校外访问取决于校园网络与 VPN 配置。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { confirmExit = true }, shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (state.demo) "结束演示" else if (state.viewingCache) "返回登录" else "退出登录")
                }
                Text("YouRoom 1.8.5 · 非学校官方应用", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (confirmExit) AlertDialog(onDismissRequest = { confirmExit = false },
        title = { Text(if (state.demo) "结束演示？" else "返回登录页？") },
        text = { Text(when {
            state.demo -> "本次演示中的个人标记将丢弃。"
            state.viewingCache -> "本地收藏、备注与教室缓存会保留。"
            else -> "本地收藏和备注会保留，已保存的登录状态将清除。"
        }) },
        confirmButton = { TextButton(onClick = { confirmExit = false; onLogout() },
            shape = MaterialTheme.shapes.small) { Text("确认") } },
        dismissButton = { TextButton(onClick = { confirmExit = false },
            shape = MaterialTheme.shapes.small) { Text("取消") } })
    clearId?.let { id -> AlertDialog(onDismissRequest = { clearId = null },
        title = { Text("清除此教室的个人标记？") },
        text = { Text("收藏、排除、标签和备注都会清除，学校数据不受影响。") },
        confirmButton = { TextButton(onClick = { onClear(id); clearId = null },
            shape = MaterialTheme.shapes.small) { Text("清除") } },
        dismissButton = { TextButton(onClick = { clearId = null },
            shape = MaterialTheme.shapes.small) { Text("取消") } }) }
}
