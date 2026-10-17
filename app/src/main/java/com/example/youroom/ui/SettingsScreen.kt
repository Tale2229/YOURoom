package com.example.youroom.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.KeyboardReturn
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.youroom.data.DemoData
import com.example.youroom.domain.*

@Composable
fun SettingsScreen(state: YouRoomState, onRoom: (String?) -> Unit,
    onClear: (String) -> Unit, onLogout: () -> Unit, onCampus: (Campus) -> Unit = {},
    showHeader: Boolean = true, sharedHeaderMoving: Boolean = false, interactive: Boolean = true,
    onPeriodStrip: (PeriodStripPreferences) -> Unit = {},
    onTone: (Campus, CampusTone) -> Unit = { _, _ -> }, onDefaultCampus: (Campus) -> Unit = {}) {
    val westList = rememberLazyListState()
    val shaheList = rememberLazyListState()
    CampusFrame(state, onCampus, showHeader, sharedHeaderMoving, interactive,
        bodyTag = "settings_campus_body") { page, active, _, _ ->
        SettingsContent(page, { if (active) onRoom(it) }, { if (active) onClear(it) },
            { if (active) onLogout() }, if (page.campus == Campus.XITUCHENG) westList else shaheList, active,
            { if (active) onPeriodStrip(it) }, { campus, tone -> if (active) onTone(campus, tone) },
            { if (active) onDefaultCampus(it) })
    }
}

@Composable
private fun SettingsContent(state: YouRoomState, onRoom: (String?) -> Unit,
    onClear: (String) -> Unit, onLogout: () -> Unit, listState: LazyListState, interactive: Boolean,
    onPeriodStrip: (PeriodStripPreferences) -> Unit, onTone: (Campus, CampusTone) -> Unit,
    onDefaultCampus: (Campus) -> Unit) {
    var sheet by remember(state.campus) { mutableStateOf<String?>(null) }
    var confirmExit by remember(state.campus) { mutableStateOf(false) }
    val context = LocalContext.current
    val version = remember(context) { runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty() }
    val profiles = remember(state.campusProfiles, state.tagOrder) { state.campusProfiles.filter {
        it.favorite || it.excluded || state.tagOrder.any(it::hasTerm) || it.note.isNotBlank()
    }.sortedWith(compareByDescending<RoomProfile> { it.favorite }.thenBy { it.name }) }
    LazyColumn(Modifier.fillMaxSize().testTag("settings_list"), state = listState,
        userScrollEnabled = interactive, contentPadding = PaddingValues(bottom = 20.dp)) {
        item { ThemeImpressionPicker(state.campus, state.appearance.tone(state.campus), interactive, onTone) }
        if (state.demo) item { Text("演示模式", Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { StripAppearanceControls(state, interactive, onPeriodStrip) }
        item { HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) }
        item { SettingsEntry("教室标记", Icons.Outlined.StarBorder, "settings_room_marks", interactive,
            value = profiles.size.toString()) { sheet = "marks" } }
        item {
            Row(Modifier.fillMaxWidth().heightIn(min = 62.dp).padding(horizontal = 24.dp)
                .testTag("settings_default_campus"), verticalAlignment = Alignment.CenterVertically) {
                Text("默认校区", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Campus.entries.forEach { campus ->
                        SettingChoice(campus.label, state.appearance.defaultCampus == campus, interactive,
                            "settings_default_${campus.dataKey}") { onDefaultCampus(campus) }
                    }
                }
            }
        }
        item { SettingsEntry("规划偏好", Icons.Outlined.Tune, "settings_planning", interactive) { sheet = "planning" } }
        item { SettingsEntry("关于", Icons.Outlined.Info, "settings_about", interactive) { sheet = "about" } }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HorizontalDivider()
                TextButton(onClick = { confirmExit = true }, enabled = interactive,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("settings_logout")) {
                    Text(if (state.demo) "结束演示" else if (state.viewingCache) "返回登录" else "退出登录")
                }
                Text("YouRoom $version", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    when (sheet) {
        "marks" -> RoomMarksSheet(state, profiles, onRoom, onClear) { sheet = null }
        "planning" -> SettingsSheet("规划偏好", state.campus, { sheet = null }) {
            Text("生成后可在方案页切换规划导向", style = MaterialTheme.typography.bodyMedium)
            Text("更多默认偏好待开放", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        "about" -> SettingsSheet("关于", state.campus, { sheet = null }) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("YouRoom $version · 非学校官方应用", style = MaterialTheme.typography.bodyMedium)
                Text("空闲信息来自学校课表，不代表实时空座。收藏、排除、标签与备注仅保存在本机。",
                    style = MaterialTheme.typography.bodySmall)
                Text("查询需要网络；时段条样式与配色可离线调整。账号密码仅在勾选记住密码后加密保存。",
                    style = MaterialTheme.typography.bodySmall)
                Text("退出登录会清除登录状态和账号密码，不清除教室标记与外观设置。",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (confirmExit) AlertDialog(onDismissRequest = { confirmExit = false },
        title = { Text(if (state.demo) "结束演示？" else "返回登录页？") },
        text = { Text(when {
            state.demo -> "本次演示中的个人标记将丢弃。"
            state.viewingCache -> "本地收藏、备注与教室缓存会保留。"
            else -> "本地收藏和备注会保留，已保存的登录状态和账号密码将清除。"
        }) },
        confirmButton = { TextButton(onClick = { confirmExit = false; onLogout() }) { Text("确认") } },
        dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("取消") } })
}

@Composable
private fun StripAppearanceControls(state: YouRoomState, enabled: Boolean, onChange: (PeriodStripPreferences) -> Unit) {
    val sample = remember { DemoData.snapshot().slots.map { it.copy(classrooms = emptyList()) } }
    val slots = state.snapshot?.slots?.takeIf { it.size == 14 } ?: sample
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).testTag("settings_strip_layout"),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("时段条", style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PeriodStripLayout.entries.forEach { layout ->
                val selected = state.periodStrip.layout == layout
                Column(Modifier.weight(1f).heightIn(min = 66.dp)
                    .background(if (selected) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface,
                        MaterialTheme.shapes.small)
                    .testTag("settings_strip_${layout.name}")
                    .clickable(enabled = enabled, role = Role.RadioButton,
                        interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        onChange(state.periodStrip.copy(layout = layout))
                    }.semantics { this.selected = selected; contentDescription = layout.title }
                    .padding(horizontal = 10.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Canvas(Modifier.fillMaxWidth().height(5.dp)) {
                        PeriodStripGeometry.bars(slots, size.width, if (layout == PeriodStripLayout.EQUAL) 2.dp.toPx() else 0f, layout)
                            .forEachIndexed { i, bar -> drawRect(if (i in setOf(1, 2, 3, 6, 7, 10, 11)) primary else muted,
                                Offset(bar.start, 0f), Size(bar.end - bar.start, size.height)) }
                    }
                    Text(layout.title, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("settings_strip_numbers")
            .clickable(enabled = enabled, role = Role.Switch,
                interactionSource = remember { MutableInteractionSource() }, indication = null) {
                onChange(state.periodStrip.copy(showNumbers = !state.periodStrip.showNumbers))
            }, verticalAlignment = Alignment.CenterVertically) {
            Text("节次编号", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            CompactSettingsSwitch(state.periodStrip.showNumbers, enabled, onCheckedChange = {
                if (enabled) onChange(state.periodStrip.copy(showNumbers = it))
            }, modifier = Modifier.testTag("settings_strip_numbers_switch").semantics { contentDescription = "显示节次编号" })
        }
    }
}

@Composable
private fun SettingChoice(title: String, selected: Boolean, enabled: Boolean, tag: String, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 48.dp).widthIn(min = 62.dp)
        .background(if (selected) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface,
            MaterialTheme.shapes.small).testTag(tag)
        .clickable(enabled = enabled, role = Role.RadioButton,
            interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
        .semantics { this.selected = selected }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
        Text(title, style = MaterialTheme.typography.bodySmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsEntry(title: String, icon: ImageVector, tag: String, enabled: Boolean,
    value: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).testTag(tag)
        .clickable(enabled = enabled, role = Role.Button, interactionSource = remember { MutableInteractionSource() },
            indication = null, onClick = onClick).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (value != null) Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsSheet(title: String, campus: Campus, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 420.dp).fillMaxWidth(.9f).heightIn(max = 560.dp), shape = MaterialTheme.shapes.small) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp))
                    Text(campus.label, style = MaterialTheme.typography.bodySmall)
                }
                content()
                IconAction(onClick = onDismiss, modifier = Modifier.align(Alignment.End).testTag("settings_sheet_close")) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardReturn, "关闭")
                }
            }
        }
    }
}

@Composable
private fun RoomMarksSheet(state: YouRoomState, profiles: List<RoomProfile>, onRoom: (String?) -> Unit,
    onClear: (String) -> Unit, onDismiss: () -> Unit) {
    var clearId by remember { mutableStateOf<String?>(null) }
    SettingsSheet("教室标记", state.campus, onDismiss) {
        if (profiles.isEmpty()) Text("还没有个人标记", Modifier.padding(vertical = 24.dp), style = MaterialTheme.typography.bodyMedium)
        else LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 360.dp).testTag("settings_marks_list")) {
            items(profiles, key = { it.roomId }) { profile ->
                Column {
                    Row(Modifier.fillMaxWidth().clickable(role = Role.Button,
                        interactionSource = remember { MutableInteractionSource() }, indication = null) { onRoom(profile.roomId) }
                        .padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(profile.name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium)
                                ExcludedRoomMarker(profile)
                            }
                            val terms = listOfNotNull(if (profile.favorite) "已收藏" else null, if (profile.excluded) "已排除" else null) +
                                state.tagOrder.filter { it != PersonalTags.favorite && profile.hasTerm(it) }
                            if (terms.isNotEmpty()) Text(terms.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (profile.note.isNotBlank()) Text(profile.note, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        IconAction(onClick = { clearId = profile.roomId }) {
                            Icon(Icons.Outlined.DeleteOutline, "清除 ${profile.name} 的个人标记")
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
    clearId?.let { id -> AlertDialog(onDismissRequest = { clearId = null },
        title = { Text("清除此教室的个人标记？") },
        text = { Text("收藏、排除、标签和备注都会清除，学校数据不受影响。") },
        confirmButton = { TextButton(onClick = { onClear(id); clearId = null }) { Text("清除") } },
        dismissButton = { TextButton(onClick = { clearId = null }) { Text("取消") } }) }
}
