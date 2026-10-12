package com.example.youroom.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.youroom.data.DemoData
import com.example.youroom.domain.*
import com.example.youroom.ui.theme.YouRoomTheme

@Composable
fun ClassroomScreen(state: YouRoomState, onFilters: (SearchFilters) -> Unit,
    onRefresh: () -> Unit, onRoom: (String?) -> Unit, onFavorite: (Classroom) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val snapshot = state.snapshot
    val result = state.result
    val slots = snapshot?.slots.orEmpty()
    LazyColumn(Modifier.fillMaxSize().testTag("room_list"), contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("YouRoom", style = MaterialTheme.typography.headlineMedium)
                    Text("西土城校区 · 今日空闲教室", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.refreshing) CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                else IconButton(onClick = onRefresh, enabled = !state.demo) {
                    Icon(if (state.viewingCache) Icons.AutoMirrored.Outlined.Login else Icons.Outlined.Refresh,
                        if (state.viewingCache) "登录并刷新" else "刷新教室")
                }
            }
        }
        if (state.demo) item { StatusNotice("演示模式：空闲安排为虚构，标记不会写入真实收藏。") }
        if (state.stale) item { StatusNotice("缓存已跨日。刷新后才能筛选今天的教室。", isError = true) }
        item {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("选择自习时段", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SlotPicker("开始", state.filters.firstSlot, slots, Modifier.weight(1f)) { number ->
                        onFilters(state.filters.copy(firstSlot = number, lastSlot = maxOf(number, state.filters.lastSlot)))
                    }
                    SlotPicker("结束", state.filters.lastSlot, slots.filter { it.number >= state.filters.firstSlot }, Modifier.weight(1f)) { number ->
                        onFilters(state.filters.copy(lastSlot = number))
                    }
                }
                if (result.selectedSlots.isNotEmpty()) {
                    Text("${result.selectedSlots.first().start}–${result.selectedSlots.last().end} · ${result.selectedSlots.size} 节课均需空闲",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(state.filters.query, { onFilters(state.filters.copy(query = it)) },
                        Modifier.weight(1f).testTag("room_search"), placeholder = { Text("教室编号或个人备注") },
                        singleLine = true, leadingIcon = { Icon(Icons.Outlined.Search, null) })
                    FilledTonalIconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.Tune, if (expanded) "收起筛选" else "更多筛选")
                    }
                }
                AnimatedVisibility(expanded) {
                    ExtraFilters(state, onFilters)
                }
                if (!expanded && (state.filters.building != null || state.filters.favoritesOnly || state.filters.quietOnly ||
                        state.filters.outletsOnly || state.filters.minimumCapacity > 0)) {
                    TextButton(onClick = { expanded = true }, contentPadding = PaddingValues(0.dp)) {
                        Text("已有额外筛选 · 点击修改")
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom) {
                Text("可选教室", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(8.dp))
                AnimatedContent(result.rooms.size, label = "room_count") {
                    Text("$it", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.weight(1f))
                Text("收藏优先", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(snapshotLabel(state), Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (result.hasUncoveredGaps) item {
            Text("课间及午休未单独提供数据；这里只确认所选教学节次空闲。",
                Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (result.rooms.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Search, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(when {
                    state.stale -> "需要刷新今日数据"
                    snapshot == null -> "还没有教室数据"
                    result.missingSlots -> "所选节次数据不完整"
                    else -> "没有符合条件的教室"
                }, style = MaterialTheme.typography.titleMedium)
                Text(if (snapshot == null || state.stale) "检查网络后重试，旧缓存不会被当作今日数据。"
                    else "试试缩短时段、切换楼宇或放宽个人标记筛选。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (snapshot == null || state.stale) TextButton(onClick = onRefresh, enabled = !state.refreshing && !state.demo) {
                    Text(if (state.viewingCache) "登录并刷新" else "重新获取")
                }
                else TextButton(onClick = { onFilters(SearchFilters(state.filters.firstSlot, state.filters.lastSlot)) }) {
                    Text("清除额外筛选")
                }
            }
        }
        items(result.rooms, key = { it.classroom.id }) { room ->
            RoomRow(room, slots.map { it.number }, Modifier.animateItem(),
                onClick = { onRoom(room.classroom.id) }, onFavorite = { onFavorite(room.classroom) })
        }
        item {
            HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
            Text("按教务课表筛选，不保证现场有座位或教室开放。学校响应没有日期字段，查询日期不代表已验证数据更新。",
                Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SlotPicker(label: String, selected: Int, slots: List<LessonSlot>, modifier: Modifier,
    onSelected: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val slot = slots.find { it.number == selected }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, enabled = slots.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(slot?.let { "第 ${it.number} 节 · ${if (label == "开始") it.start else it.end}" } ?: "暂无数据",
                    style = MaterialTheme.typography.labelLarge)
            }
            Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
        }
        DropdownMenu(open, { open = false }, modifier = Modifier.heightIn(max = 320.dp)) {
            slots.forEach { option -> DropdownMenuItem(
                text = { Text("第 ${option.number} 节   ${option.timeLabel}") },
                onClick = { onSelected(option.number); open = false }) }
        }
    }
}

@Composable
private fun ExtraFilters(state: YouRoomState, onFilters: (SearchFilters) -> Unit) {
    val filters = state.filters
    val buildings = state.snapshot?.classrooms.orEmpty().map { it.building }.distinct().sorted()
    var capacityText by remember(filters.minimumCapacity) { mutableStateOf(if (filters.minimumCapacity == 0) "" else "${filters.minimumCapacity}") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(filters.building == null, { onFilters(filters.copy(building = null)) }, label = { Text("全部楼宇") })
            buildings.forEach { building ->
                FilterChip(filters.building == building, { onFilters(filters.copy(building = building)) },
                    label = { Text(if (building.all(Char::isDigit)) "教学楼 $building" else building) })
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(filters.favoritesOnly, { onFilters(filters.copy(favoritesOnly = !filters.favoritesOnly)) }, label = { Text("仅收藏") })
            FilterChip(filters.quietOnly, { onFilters(filters.copy(quietOnly = !filters.quietOnly)) }, label = { Text("标记安静") })
            FilterChip(filters.outletsOnly, { onFilters(filters.copy(outletsOnly = !filters.outletsOnly)) }, label = { Text("标记有插座") })
        }
        OutlinedTextField(capacityText, { value ->
            capacityText = value.filter(Char::isDigit).take(4)
            onFilters(filters.copy(minimumCapacity = capacityText.toIntOrNull() ?: 0))
        }, label = { Text("最低标注容量（可不填）") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        Text("安静与插座只依据你的个人标记；容量不是实时余座。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RoomRow(match: RoomMatch, slots: List<Int>, modifier: Modifier = Modifier,
    onClick: () -> Unit, onFavorite: () -> Unit) {
    val room = match.classroom
    Column(modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(room.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(room.buildingLabel, room.capacity?.let { "标注容量 $it" },
                    if (match.profile.quiet) "安静" else null, if (match.profile.outlets) "有插座" else null).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (match.profile.note.isNotBlank()) Text(match.profile.note, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onFavorite) {
                AnimatedContent(match.profile.favorite, label = "favorite") { favorite ->
                    Icon(if (favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                        if (favorite) "取消收藏 ${room.name}" else "收藏 ${room.name}",
                        tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        AvailabilityStrip(match.freeSlots, slots, Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp))
        HorizontalDivider()
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun RoomsPreview() {
    YouRoomTheme { ClassroomScreen(YouRoomState(initializing = false, demo = true,
        snapshot = DemoData.snapshot(), filters = SearchFilters(1, 2)), {}, {}, {}, {}) }
}
