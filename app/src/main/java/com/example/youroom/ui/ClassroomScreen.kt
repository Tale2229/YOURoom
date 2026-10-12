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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
    onRefresh: () -> Unit, onRoom: (String?) -> Unit, onFavorite: (Classroom) -> Unit,
    onCampus: (Campus) -> Unit = {}) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val snapshot = state.campusSnapshot
    val result = state.result
    val slots = snapshot?.slots.orEmpty()
    val westList = androidx.compose.foundation.lazy.rememberLazyListState()
    val shaheList = androidx.compose.foundation.lazy.rememberLazyListState()
    val listState = if (state.campus == Campus.XITUCHENG) westList else shaheList
    val viewportDates = remember { mutableMapOf<Pair<Campus, Boolean>, java.time.LocalDate>() }
    LaunchedEffect(state.campus, snapshot?.queryDate, state.demo) {
        snapshot?.queryDate?.let { date ->
            val previous = viewportDates.put(state.campus to state.demo, date)
            if (previous != null && previous != date) listState.scrollToItem(0)
        }
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        CampusHeader(state, onRefresh, onCampus)
        HorizontalDivider(Modifier.padding(horizontal = 24.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("room_list"), state = listState,
            contentPadding = PaddingValues(bottom = 24.dp)) {
            if (state.demo) item { StatusNotice("演示数据 · 空闲安排为虚构，个人标记仅本次有效。") }
            if (state.stale) item { StatusNotice("缓存已跨日，刷新后才能筛选今天的教室。", isError = true) }
            item {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SlotPicker(state.campus, snapshot?.queryDate, state.demo, state.filters, slots, onFilters)
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
                        state.filters.selectedSlots.isEmpty() -> "请选择自习时段"
                        result.missingSlots -> "所选节次数据不完整"
                        else -> "没有符合条件的教室"
                    }, style = MaterialTheme.typography.titleLarge)
                    Text(if (snapshot == null || state.stale) "检查网络后重试，旧缓存不作为今日数据。"
                        else if (state.filters.selectedSlots.isEmpty()) "点击节次，或按住划过多个格子进行选择。"
                        else "试试缩短时段，或放宽楼宇与个人标记筛选。",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (snapshot == null || state.stale) TextButton(onClick = onRefresh,
                        shape = MaterialTheme.shapes.small, enabled = !state.refreshing && !state.demo) {
                        Text(if (state.viewingCache) "登录并刷新" else "重新获取")
                    } else if (state.filters.selectedSlots.isNotEmpty()) TextButton(onClick = { onFilters(SearchFilters(selectedSlots = state.filters.selectedSlots)) },
                        shape = MaterialTheme.shapes.small) { Text("清除额外筛选") }
                }
            }
            items(result.rooms, key = { it.classroom.id }) { room ->
                RoomRow(room, slots.map { it.number }, Modifier.animateItem(),
                    onClick = { onRoom(room.classroom.id) }, onFavorite = { onFavorite(room.classroom) })
            }
            item {
                HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 20.dp))
                Text("课表空闲不保证现场有座位或教室开放。学校响应没有日期字段，查询日不代表已验证数据更新。",
                    Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
        snapshot = DemoData.snapshot(), filters = SearchFilters(setOf(1, 2))), {}, {}, {}, {}) }
}

@Preview(name = "沙河演示", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ShahePreview() {
    YouRoomTheme(campus = Campus.SHAHE) { ClassroomScreen(YouRoomState(initializing = false,
        campus = Campus.SHAHE, demo = true, snapshot = DemoData.snapshot(Campus.SHAHE),
        filters = SearchFilters(setOf(1, 2))), {}, {}, {}, {}) }
}
