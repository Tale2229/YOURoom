package com.example.youroom.ui

import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import com.example.youroom.ui.theme.campusBadgeColor
import com.example.youroom.domain.allTags
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.youroom.data.DemoData
import com.example.youroom.domain.*
import com.example.youroom.ui.theme.YouRoomTheme

@Composable
fun ClassroomScreen(state: YouRoomState, onFilters: (SearchFilters) -> Unit,
    onRefresh: () -> Unit, onRoom: (String?) -> Unit, onFavorite: (Classroom) -> Unit,
    onCampus: (Campus) -> Unit = {}, onAddTag: (String) -> Unit = {}) {
    val westList = androidx.compose.foundation.lazy.rememberLazyListState()
    val shaheList = androidx.compose.foundation.lazy.rememberLazyListState()
    val viewportDates = remember { mutableMapOf<Pair<Campus, Boolean>, java.time.LocalDate>() }
    val pages = remember { mutableMapOf<Campus, YouRoomState>() }
    pages[state.campus] = state
    val listState = if (state.campus == Campus.XITUCHENG) westList else shaheList
    LaunchedEffect(state.campus, state.campusSnapshot?.queryDate, state.demo) {
        state.campusSnapshot?.queryDate?.let { date ->
            val previous = viewportDates.put(state.campus to state.demo, date)
            if (previous != null && previous != date) listState.scrollToItem(0)
        }
    }
    AnimatedContent(state.campus, modifier = Modifier.fillMaxSize().clipToBounds(),
        transitionSpec = {
            val direction = if (targetState == Campus.SHAHE) AnimatedContentTransitionScope.SlideDirection.Left
                else AnimatedContentTransitionScope.SlideDirection.Right
            slideIntoContainer(direction, tween(320)) togetherWith slideOutOfContainer(direction, tween(320))
        }, label = "campus_page") { displayed ->
        val page = if (displayed == state.campus) state else pages.getValue(displayed)
        YouRoomTheme(campus = displayed) {
            Surface(Modifier.fillMaxSize()) {
                ClassroomPage(page, if (displayed == Campus.XITUCHENG) westList else shaheList,
                    { if (displayed == state.campus) onFilters(it) }, onRefresh, onRoom, onFavorite, onCampus, onAddTag)
            }
        }
    }
}

@Composable
private fun ClassroomPage(state: YouRoomState, listState: LazyListState, onFilters: (SearchFilters) -> Unit,
    onRefresh: () -> Unit, onRoom: (String?) -> Unit, onFavorite: (Classroom) -> Unit,
    onCampus: (Campus) -> Unit, onAddTag: (String) -> Unit) {
    var showFilters by remember { mutableStateOf(false) }
    val snapshot = state.campusSnapshot
    val result = state.result
    val slots = snapshot?.slots.orEmpty()
    if (showFilters) FilterDialog(state, { showFilters = false }, onFilters, onAddTag)
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
                        Box {
                            IconAction(onClick = { showFilters = true }, filled = true,
                                modifier = Modifier.testTag("extra_filters").semantics {
                                    contentDescription = "筛选教室，已选 ${state.filters.badgeCount} 个词条"
                                }) {
                                Icon(Icons.Outlined.Tune, "筛选教室", Modifier.size(22.dp))
                            }
                            if (state.filters.badgeCount > 0) Surface(
                                modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp)
                                    .testTag("filter_badge"), shape = androidx.compose.foundation.shape.CircleShape,
                                color = campusBadgeColor(state.campus), contentColor = Color.White) {
                                Box(Modifier.heightIn(min = 20.dp).widthIn(min = 20.dp).padding(horizontal = 4.dp),
                                    contentAlignment = Alignment.Center) {
                                    Text("${state.filters.badgeCount}", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
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
private fun RoomRow(match: RoomMatch, slots: List<Int>, modifier: Modifier = Modifier,
    onClick: () -> Unit, onFavorite: () -> Unit) {
    val room = match.classroom
    Column(modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(room.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(room.buildingLabel, room.capacity?.let { "容量 $it" },
                    if (match.profile.outlets) "有插座" else null).plus(match.profile.allTags.sorted()).joinToString(" · "),
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
