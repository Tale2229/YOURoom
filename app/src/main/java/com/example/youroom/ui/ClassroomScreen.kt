package com.example.youroom.ui

import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.clipToBounds
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
    onCampus: (Campus) -> Unit = {}, onAddTag: (String) -> Unit = {},
    onRemoveTag: (String) -> Unit = {}, onTagOrder: (List<String>) -> Unit = {}) {
    val westList = androidx.compose.foundation.lazy.rememberLazyListState()
    val shaheList = androidx.compose.foundation.lazy.rememberLazyListState()
    val viewportDates = remember { mutableMapOf<Pair<Campus, Boolean>, java.time.LocalDate>() }
    val pages = remember { mutableMapOf<Campus, YouRoomState>() }
    pages[state.campus] = state
    val listState = if (state.campus == Campus.XITUCHENG) westList else shaheList
    val pager = rememberPagerState(initialPage = state.campus.ordinal) { Campus.entries.size }
    val desiredPage by rememberUpdatedState(state.campus.ordinal)
    val moving by remember(pager) { derivedStateOf {
        pager.isScrollInProgress || pager.settledPage != desiredPage
    } }
    LaunchedEffect(state.campus, state.campusSnapshot?.queryDate, state.demo, moving) {
        if (!moving) state.campusSnapshot?.queryDate?.let { date ->
            val previous = viewportDates.put(state.campus to state.demo, date)
            if (previous != null && previous != date) listState.scrollToItem(0)
        }
    }
    LaunchedEffect(state.campus) {
        if (pager.currentPage != state.campus.ordinal || pager.currentPageOffsetFraction != 0f) {
            pager.animateScrollToPage(state.campus.ordinal, animationSpec = tween(320))
        }
    }
    HorizontalPager(state = pager, beyondViewportPageCount = 1, userScrollEnabled = false,
        modifier = Modifier.fillMaxSize().clipToBounds().testTag("campus_pager"),
        key = { Campus.entries[it].dataKey }) { index ->
        val displayed = Campus.entries[index]
        val observed = if (displayed == state.campus) state else pages[displayed] ?: state.copy(
            campus = displayed, snapshot = state.campusSnapshots[displayed],
            filters = state.filters.copy(buildings = emptySet()), fromCache = !state.demo,
            refreshing = false, error = null, selectedRoomId = null)
        // Keep the warmed offscreen page stable; editing the visible grid must not
        // run a second set of selection animations on the hidden campus.
        pages.putIfAbsent(displayed, observed)
        // A response arriving mid-slide must not rebuild rows and start item animations
        // at the same time as page movement. Apply it as soon as the movement settles.
        var held by remember(displayed) { mutableStateOf(observed) }
        val page = if (moving) held else observed
        SideEffect { if (!moving) held = observed }
        val active = displayed == state.campus && !moving
        val pageModifier = if (pager.currentPage == index) Modifier else Modifier.clearAndSetSemantics {}
        YouRoomTheme(campus = displayed) {
            Surface(Modifier.fillMaxSize().then(pageModifier).graphicsLayer()) {
                ClassroomPage(page, if (displayed == Campus.XITUCHENG) westList else shaheList, active, moving,
                    { if (active) onFilters(it) }, { if (active) onRefresh() },
                    { if (active) onRoom(it) }, { if (active) onFavorite(it) }, onCampus,
                    { if (active) onAddTag(it) }, { if (active) onRemoveTag(it) },
                    { if (active) onTagOrder(it) })
            }
        }
    }
}

@Composable
private fun ClassroomPage(state: YouRoomState, listState: LazyListState, active: Boolean, moving: Boolean,
    onFilters: (SearchFilters) -> Unit,
    onRefresh: () -> Unit, onRoom: (String?) -> Unit, onFavorite: (Classroom) -> Unit,
    onCampus: (Campus) -> Unit, onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit, onTagOrder: (List<String>) -> Unit) {
    var showFilters by remember { mutableStateOf(false) }
    val snapshot = state.campusSnapshot
    val search = rememberClassroomSearch(state, enabled = active)
    val result = search.result
    val waiting = search.pending || (snapshot == null && (state.refreshing || moving))
    val slots = snapshot?.slots.orEmpty()
    val slotNumbers = remember(slots) { slots.map { it.number } }
    LaunchedEffect(active) { if (!active) showFilters = false }
    if (showFilters && active) FilterDialog(state, { showFilters = false }, onFilters, onAddTag, onRemoveTag, onTagOrder)
    Column(Modifier.fillMaxSize().imePadding()) {
        CampusHeader(state, onRefresh, onCampus)
        HorizontalDivider(Modifier.padding(horizontal = 24.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("room_list"), state = listState,
            contentPadding = PaddingValues(bottom = 24.dp)) {
            if (state.demo) item { StatusNotice("演示数据 · 空闲安排为虚构，个人标记仅本次有效。") }
            if (state.stale) item { StatusNotice("缓存已跨日，刷新后才能筛选今天的教室。", isError = true) }
            item {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    // Keep six columns and 48dp targets even on a 320dp-wide phone.
                    val gutter = ((maxWidth - 308.dp) / 2).coerceIn(0.dp, 24.dp)
                    Box(Modifier.padding(horizontal = gutter, vertical = 14.dp)) {
                        SlotPicker(state.campus, snapshot?.queryDate, state.demo, state.filters, slots, onFilters,
                            enabled = active, onShowFilters = { showFilters = true })
                    }
                }
            }
            item {
                HorizontalDivider(Modifier.padding(horizontal = 24.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("可选教室", style = MaterialTheme.typography.titleMedium)
                    }
                    if (!waiting || result.rooms.isNotEmpty()) Text("${result.rooms.size}", style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            if (!waiting && result.rooms.isEmpty()) item {
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
                    } else TextButton(onClick = { onFilters(SearchFilters(selectedSlots = state.filters.selectedSlots)) },
                        shape = MaterialTheme.shapes.small) { Text("清除额外筛选") }
                }
            }
            items(result.rooms, key = { it.classroom.id }) { room ->
                RoomRow(room, slotNumbers, if (moving) Modifier else Modifier.animateItem(), terms = state.tagOrder,
                    onClick = { onRoom(room.classroom.id) }, onFavorite = { onFavorite(room.classroom) })
            }
        }
    }
}

@Composable
private fun RoomRow(match: RoomMatch, slots: List<Int>, modifier: Modifier = Modifier, terms: List<String>,
    onClick: () -> Unit, onFavorite: () -> Unit) {
    val room = match.classroom
    val labels = remember(match.profile, terms) { terms.filter { it != PersonalTags.favorite && match.profile.hasTerm(it) } }
    Column(modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(room.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(room.buildingLabel, room.capacity?.let { "容量 $it" }).plus(labels).joinToString(" · "),
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
