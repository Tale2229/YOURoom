package com.example.youroom.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.youroom.domain.PersonalTags
import com.example.youroom.domain.hasTerm
import com.example.youroom.domain.Campus

@Composable
fun SettingsScreen(state: YouRoomState, onRoom: (String?) -> Unit,
    onClear: (String) -> Unit, onLogout: () -> Unit, onCampus: (Campus) -> Unit = {},
    showHeader: Boolean = true, sharedHeaderMoving: Boolean = false, interactive: Boolean = true) {
    val westList = rememberLazyListState()
    val shaheList = rememberLazyListState()
    CampusFrame(state, onCampus, showHeader, sharedHeaderMoving, interactive,
        bodyTag = "settings_campus_body") { page, active, _, _ ->
        SettingsContent(page, { if (active) onRoom(it) }, { if (active) onClear(it) },
            { if (active) onLogout() }, if (page.campus == Campus.XITUCHENG) westList else shaheList, active)
    }
}

@Composable
private fun SettingsContent(state: YouRoomState, onRoom: (String?) -> Unit,
    onClear: (String) -> Unit, onLogout: () -> Unit, listState: LazyListState, interactive: Boolean) {
    var confirmExit by remember { mutableStateOf(false) }
    var clearId by remember { mutableStateOf<String?>(null) }
    var marksExpanded by rememberSaveable { mutableStateOf(false) }
    val context=LocalContext.current
    val version=remember(context) { runCatching {
        context.packageManager.getPackageInfo(context.packageName,0).versionName
    }.getOrNull().orEmpty() }
    val profiles = state.campusProfiles.filter {
        it.favorite || it.excluded || state.tagOrder.any(it::hasTerm) || it.note.isNotBlank()
    }.sortedWith(compareByDescending<com.example.youroom.domain.RoomProfile> { it.favorite }.thenBy { it.name })
    LazyColumn(Modifier.fillMaxSize().testTag("settings_list"), state = listState,
        userScrollEnabled = interactive, contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("设置", style = MaterialTheme.typography.headlineMedium)
            }
        }
        if (state.demo) item { StatusNotice("演示标记仅在本次演示中有效，不会修改真实偏好。") }
        item {
            SettingsItem("教室标记","收藏、屏蔽、标签与备注",Icons.Outlined.StarBorder,"settings_room_marks",
                interactive,{ marksExpanded=!marksExpanded }) {
                Text(profiles.size.toString(),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
                Icon(if(marksExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,null,Modifier.size(22.dp))
            }
        }
        if (marksExpanded) {
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
                    .clickable(enabled=interactive) { onRoom(profile.roomId) }.padding(horizontal = 24.dp, vertical = 12.dp)) {
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
                        IconAction(onClick = { clearId = profile.roomId },enabled=interactive) {
                            Icon(Icons.Outlined.DeleteOutline, "清除 ${profile.name} 的个人标记")
                        }
                    }
                    HorizontalDivider(Modifier.padding(top = 14.dp))
                }
            }
        }
        item { HorizontalDivider(Modifier.padding(horizontal=24.dp)) }
        item {
            SettingsItem("色调","校区主题与深浅外观",Icons.Outlined.Palette,"settings_tone")
        }
        item {
            SettingsItem("默认校区","启动时优先展示的校区",Icons.Outlined.LocationOn,"settings_default_campus")
        }
        item {
            SettingsItem("规划偏好","规划质量与换教室偏好",Icons.Outlined.Tune,"settings_planning")
        }
        item {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("关于数据", style = MaterialTheme.typography.titleMedium)
                Text("插座、桌椅舒适、人少及自定义词条由你标记，不来自学校接口；人少不是实时人数。未标记不代表没有。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("支持西土城与沙河校区，个人标记按校区区分。校外访问取决于校园网络与 VPN 配置。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { confirmExit = true },enabled=interactive, shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (state.demo) "结束演示" else if (state.viewingCache) "返回登录" else "退出登录")
                }
                Text("YouRoom $version · 非学校官方应用", style = MaterialTheme.typography.labelSmall,
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

@Composable
private fun SettingsItem(title: String,detail: String,icon: ImageVector,tag: String,
    enabled: Boolean=false,onClick: (() -> Unit)?=null,trailing: (@Composable RowScope.() -> Unit)?=null) {
    Row(Modifier.fillMaxWidth().heightIn(min=72.dp).testTag(tag)
        .clickable(enabled=enabled && onClick!=null,role=Role.Button,
            interactionSource=remember { MutableInteractionSource() },indication=null,onClick=onClick ?: {})
        .semantics { if(onClick==null) stateDescription="待开发" }
        .padding(horizontal=24.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        Icon(icon,null,Modifier.size(23.dp),tint=MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(title,style=MaterialTheme.typography.titleMedium)
            Text(detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(trailing!=null) trailing() else Text("待开发",style=MaterialTheme.typography.labelSmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
