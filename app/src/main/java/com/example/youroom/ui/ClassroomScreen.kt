package com.example.youroom.ui

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.youroom.data.DemoData
import com.example.youroom.domain.*
import com.example.youroom.ui.theme.YouRoomTheme

@Composable
fun ClassroomScreen(state: YouRoomState, onFilters: (SearchFilters) -> Unit,
    onRefresh: () -> Unit, onRoom: (String?) -> Unit, onFavorite: (Classroom) -> Unit,
    onCampus: (Campus) -> Unit = {}) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val snapshot = state.campusSnapshot
    val result = state.result
    val slots = snapshot?.slots.orEmpty()
    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("room_list"),
        contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("YouRoom", style = MaterialTheme.typography.headlineMedium)
                        Text("今日空闲教室", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (state.refreshing && !state.campusUnavailable) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconAction(onClick = onRefresh, enabled = !state.demo && !state.campusUnavailable) {
                            Icon(if (state.viewingCache) Icons.AutoMirrored.Outlined.Login else Icons.Outlined.Refresh,
                                if (state.viewingCache) "登录并刷新" else "刷新教室", Modifier.size(22.dp))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                CampusTabs(state.campus, onCampus, state.demo)
            }
            HorizontalDivider(Modifier.padding(horizontal = 24.dp))
        }
        if (state.campusUnavailable) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 52.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("沙河校区待接入", style = MaterialTheme.typography.headlineMedium)
                    Text("此校区接口尚未验证，不显示西土城的教室数据。",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { onCampus(Campus.XITUCHENG) },
                        shape = MaterialTheme.shapes.small, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("返回西土城")
                    }
                }
            }
        } else {
            if (state.demo) item { StatusNotice("演示数据 · 空闲安排为虚构，个人标记仅本次有效。") }
            if (state.stale) item { StatusNotice("缓存已跨日，刷新后才能筛选今天的教室。", isError = true) }
            item {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SlotRangePicker(state.campus, state.filters, slots, onFilters)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(state.filters.query, { onFilters(state.filters.copy(query = it)) },
                            Modifier.weight(1f).testTag("room_search"), placeholder = { Text("搜索教室或备注") },
                            singleLine = true, leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(20.dp)) })
                        IconAction(onClick = { expanded = !expanded }, filled = true,
                            modifier = Modifier.testTag("extra_filters")) {
                            Icon(Icons.Outlined.Tune, if (expanded) "收起筛选" else "更多筛选", Modifier.size(22.dp))
                        }
                    }
                    AnimatedVisibility(expanded,
                        enter = fadeIn(tween(140)) + expandVertically(tween(180)),
                        exit = fadeOut(tween(90)) + shrinkVertically(tween(140))) { ExtraFilters(state, onFilters) }
                    if (!expanded && (state.filters.building != null || state.filters.favoritesOnly ||
                            state.filters.quietOnly || state.filters.outletsOnly || state.filters.minimumCapacity > 0)) {
                        TextButton(onClick = { expanded = true }, shape = MaterialTheme.shapes.small,
                            contentPadding = PaddingValues(0.dp)) { Text("已有额外筛选 · 点击修改") }
                    }
                }
            }
            item {
                HorizontalDivider(Modifier.padding(horizontal = 24.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("可选教室", style = MaterialTheme.typography.titleMedium)
                        Text(snapshotLabel(state), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AnimatedContent(result.rooms.size, label = "room_count") {
                        Text("$it", style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (result.hasUncoveredGaps) Text("只确认所选教学节次空闲，课间与午休不作保证。",
                    Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (result.rooms.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(when {
                        state.stale -> "需要刷新今日数据"
                        snapshot == null -> "还没有教室数据"
                        result.missingSlots -> "所选节次数据不完整"
                        else -> "没有符合条件的教室"
                    }, style = MaterialTheme.typography.titleLarge)
                    Text(if (snapshot == null || state.stale) "检查网络后重试，旧缓存不作为今日数据。"
                        else "试试缩短时段，或放宽楼宇与个人标记筛选。",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (snapshot == null || state.stale) TextButton(onClick = onRefresh,
                        shape = MaterialTheme.shapes.small, enabled = !state.refreshing && !state.demo) {
                        Text(if (state.viewingCache) "登录并刷新" else "重新获取")
                    } else TextButton(onClick = { onFilters(SearchFilters(state.filters.firstSlot, state.filters.lastSlot)) },
                        shape = MaterialTheme.shapes.small) { Text("清除额外筛选") }
                }
            }
            items(result.rooms, key = { it.classroom.id }) { room ->
                RoomRow(room, slots.map { it.number }, Modifier.animateItem(),
                    onClick = { onRoom(room.classroom.id) }, onFavorite = { onFavorite(room.classroom) })
            }
        }
        item {
            HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 20.dp))
            Text("课表空闲不保证现场有座位或教室开放。学校响应没有日期字段，查询日不代表已验证数据更新。",
                Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SlotRangePicker(campus: Campus, filters: SearchFilters, slots: List<LessonSlot>,
    onFilters: (SearchFilters) -> Unit) {
    var choosingEnd by rememberSaveable(campus) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("自习时段", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            val first = slots.find { it.number == filters.firstSlot }
            val last = slots.find { it.number == filters.lastSlot }
            Text(if (first != null && last != null) "${first.start}—${last.end}" else "暂无数据",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Every cell retains a 48dp touch target, including small phones and large fonts.
            val columns = ((maxWidth.value + 4f) / 52f).toInt().coerceIn(1, 7)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                slots.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { slot ->
                            val inRange = slot.number in filters.firstSlot..filters.lastSlot
                            val endpoint = inRange && (slot.number == filters.firstSlot || slot.number == filters.lastSlot)
                            val colors = MaterialTheme.colorScheme
                            val background by animateColorAsState(
                                if (endpoint) colors.primary else if (inRange) colors.primaryContainer else colors.surfaceContainer,
                                tween(140), label = "slot_background")
                            val foreground = if (endpoint) colors.onPrimary else if (inRange) colors.onPrimaryContainer else colors.onSurfaceVariant
                            Surface(onClick = {
                                onFilters(SlotSelection.select(filters, slot.number, choosingEnd))
                                choosingEnd = !choosingEnd
                            }, shape = MaterialTheme.shapes.small, color = background, contentColor = foreground,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("slot_${slot.number}")
                                    .semantics {
                                        selected = inRange
                                        contentDescription = "第 ${slot.number} 节，${slot.timeLabel}"
                                        stateDescription = when {
                                            endpoint -> "时段端点"
                                            inRange -> "在所选时段内"
                                            else -> "未选中"
                                        }
                                    }) {
                                Box(Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                    Text("${slot.number}", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        Text(if (choosingEnd) "再点终点，所选节次均需空闲。" else "点选起点，再点终点；再次点选可重新开始。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ExtraFilters(state: YouRoomState, onFilters: (SearchFilters) -> Unit) {
    val filters = state.filters
    val buildings = state.campusSnapshot?.classrooms.orEmpty().map { it.building }.distinct().sorted()
    var capacityText by remember(filters.minimumCapacity) { mutableStateOf(if (filters.minimumCapacity == 0) "" else "${filters.minimumCapacity}") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(filters.building == null, { onFilters(filters.copy(building = null)) },
                shape = MaterialTheme.shapes.small, label = { Text("全部楼宇") })
            buildings.forEach { building ->
                FilterChip(filters.building == building, { onFilters(filters.copy(building = building)) },
                    shape = MaterialTheme.shapes.small,
                    label = { Text(if (building.all(Char::isDigit)) "教学楼 $building" else building) })
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(filters.favoritesOnly, { onFilters(filters.copy(favoritesOnly = !filters.favoritesOnly)) },
                shape = MaterialTheme.shapes.small, label = { Text("仅收藏") })
            FilterChip(filters.quietOnly, { onFilters(filters.copy(quietOnly = !filters.quietOnly)) },
                shape = MaterialTheme.shapes.small, label = { Text("标记安静") })
            FilterChip(filters.outletsOnly, { onFilters(filters.copy(outletsOnly = !filters.outletsOnly)) },
                shape = MaterialTheme.shapes.small, label = { Text("标记有插座") })
        }
        OutlinedTextField(capacityText, { value ->
            capacityText = value.filter(Char::isDigit).take(4)
            onFilters(filters.copy(minimumCapacity = capacityText.toIntOrNull() ?: 0))
        }, label = { Text("最低标注容量（可不填）") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        Text("安静与插座依据个人标记，容量不是实时余座。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RoomRow(match: RoomMatch, slots: List<Int>, modifier: Modifier = Modifier,
    onClick: () -> Unit, onFavorite: () -> Unit) {
    val room = match.classroom
    Column(modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(room.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(room.buildingLabel, room.capacity?.let { "容量 $it" },
                    if (match.profile.quiet) "安静" else null, if (match.profile.outlets) "有插座" else null).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (match.profile.note.isNotBlank()) Text(match.profile.note, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconAction(onClick = onFavorite) {
                AnimatedContent(match.profile.favorite, label = "favorite") { favorite ->
                    Icon(if (favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                        if (favorite) "取消收藏 ${room.name}" else "收藏 ${room.name}",
                        tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        AvailabilityStrip(match.freeSlots, slots, Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp))
        HorizontalDivider()
    }
}

@Preview(name = "西土城", showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "小屏", showBackground = true, widthDp = 320, heightDp = 740)
@Preview(name = "夜间", showBackground = true, widthDp = 390, heightDp = 844, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RoomsPreview() {
    YouRoomTheme { ClassroomScreen(YouRoomState(initializing = false, demo = true,
        snapshot = DemoData.snapshot(), filters = SearchFilters(1, 2)), {}, {}, {}, {}) }
}

@Preview(name = "沙河演示", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ShahePreview() {
    YouRoomTheme(campus = Campus.SHAHE) { ClassroomScreen(YouRoomState(initializing = false,
        campus = Campus.SHAHE, demo = true, snapshot = DemoData.snapshot(Campus.SHAHE),
        filters = SearchFilters(1, 2)), {}, {}, {}, {}) }
}
